/*
 * Copyright 2008 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.TypeValidator.TypeMismatch;
import com.google.javascript.jscomp.graph.AdjacencyGraph;
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.jscomp.graph.GraphColoring;
import com.google.javascript.jscomp.graph.GraphColoring.GreedyGraphColoring;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.SubGraph;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Renames unrelated properties to the same name, using type information.
 * This allows better compression as more properties can be given short names.
 *
 * <p>Properties are considered unrelated if they are never referenced from the
 * same type or from a subtype of each others' types, thus this pass is only
 * effective if type checking is enabled.
 *
 * Example:
 * <code>
 *   Foo.fooprop = 0;
 *   Foo.fooprop2 = 0;
 *   Bar.barprop = 0;
 * </code>
 *
 * becomes:
 *
 * <code>
 *   Foo.a = 0;
 *   Foo.b = 0;
 *   Bar.a = 0;
 * </code>
 *
 */
class AmbiguateProperties implements CompilerPass {
  private static final Logger logger = Logger.getLogger(
      AmbiguateProperties.class.getName());

  private final AbstractCompiler compiler;

  private final List<Node> stringNodesToRename = Lists.newArrayList();
  private final char[] reservedCharacters;

  /** Map from property name to Property object */
  private final Map<String, Property> propertyMap = Maps.newHashMap();

  /** Property names that don't get renamed */
  private final Set<String> externedNames = Sets.newHashSet();

  /** Names to which properties shouldn't be renamed, to avoid name conflicts */
  private final Set<String> quotedNames = Sets.newHashSet();

  /** Map from original property name to new name. */
  private final Map<String, String> renamingMap = Maps.newHashMap();

  /**
   * Sorts Property objects by their count, breaking ties alphabetically to
   * ensure a deterministic total ordering.
   */
  private static final Comparator<Property> FREQUENCY_COMPARATOR =
      new Comparator<Property>() {
        @Override
        public int compare(Property p1, Property p2) {
          if (p1.numOccurrences != p2.numOccurrences) {
            return p2.numOccurrences - p1.numOccurrences;
          }
          return p1.oldName.compareTo(p2.oldName);
        }
      };

  /** A map from JSType to a unique representative Integer. */
  private BiMap<JSType, Integer> intForType = HashBiMap.create();

  /**
   * A map from JSType to JSTypeBitSet representing the types related
   * to the type.
   */
  private Map<JSType, JSTypeBitSet> relatedBitsets = Maps.newHashMap();

  /** A set of types that invalidate properties from ambiguation. */
  private final Set<JSType> invalidatingTypes;

  /**
   * Prefix of properties to skip renaming.  These should be renamed in the
   * RenameProperties pass.
   */
  static final String SKIP_PREFIX = "JSAbstractCompiler";

  AmbiguateProperties(AbstractCompiler compiler,
      char[] reservedCharacters) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());
    this.compiler = compiler;
    this.reservedCharacters = reservedCharacters;

    JSTypeRegistry r = compiler.getTypeRegistry();
    invalidatingTypes = Sets.newHashSet(
        r.getNativeType(JSTypeNative.ALL_TYPE),
        r.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        r.getNativeType(JSTypeNative.NO_TYPE),
        r.getNativeType(JSTypeNative.NULL_TYPE),
        r.getNativeType(JSTypeNative.VOID_TYPE),
        r.getNativeType(JSTypeNative.FUNCTION_FUNCTION_TYPE),
        r.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
        r.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
        r.getNativeType(JSTypeNative.GLOBAL_THIS),
        r.getNativeType(JSTypeNative.OBJECT_TYPE),
        r.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
        r.getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE),
        r.getNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE),
        r.getNativeType(JSTypeNative.UNKNOWN_TYPE));

    for (TypeMismatch mis : compiler.getTypeValidator().getMismatches()) {
      addInvalidatingType(mis.typeA);
      addInvalidatingType(mis.typeB);
    }
  }

  /**
   * Invalidates the given type, so that no properties on it will be renamed.
   */
  private void addInvalidatingType(JSType type) {
    type = type.restrictByNotNullOrUndefined();
    if (type.isUnionType()) {
      for (JSType alt : type.toMaybeUnionType().getAlternates()) {
        addInvalidatingType(alt);
      }
    }

    invalidatingTypes.add(type);
    ObjectType objType = ObjectType.cast(type);
    if (objType != null && objType.isInstanceType()) {
      invalidatingTypes.add(objType.getImplicitPrototype());
    }
  }

  Map<String, String> getRenamingMap() {
    return renamingMap;
  }

  /** Returns an integer that uniquely identifies a JSType. */
  private int getIntForType(JSType type) {
    if (intForType.containsKey(type)) {
      return intForType.get(type).intValue();
    }
    int newInt = intForType.size() + 1;
    intForType.put(type, newInt);
    return newInt;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, new ProcessExterns());
    NodeTraversal.traverse(compiler, root, new ProcessProperties());

    Set<String> reservedNames =
        new HashSet<String>(externedNames.size() + quotedNames.size());
    reservedNames.addAll(externedNames);
    reservedNames.addAll(quotedNames);

    int numRenamedPropertyNames = 0;
    int numSkippedPropertyNames = 0;
    Set<Property> propsByFreq = new TreeSet<Property>(FREQUENCY_COMPARATOR);
    for (Property p : propertyMap.values()) {
      if (!p.skipAmbiguating) {
        ++numRenamedPropertyNames;
        propsByFreq.add(p);
      } else {
        ++numSkippedPropertyNames;
        reservedNames.add(p.oldName);
      }
    }

    PropertyGraph graph = new PropertyGraph(Lists.newLinkedList(propsByFreq));
    GraphColoring<Property, Void> coloring =
        new GreedyGraphColoring<Property, Void>(graph, FREQUENCY_COMPARATOR);
    int numNewPropertyNames = coloring.color();

    NameGenerator nameGen = new NameGenerator(
        reservedNames, "", reservedCharacters);
    Map<Integer, String> colorMap = Maps.newHashMap();
    for (int i = 0; i < numNewPropertyNames; ++i) {
      colorMap.put(i, nameGen.generateNextName());
    }
    for (GraphNode<Property, Void> node : graph.getNodes()) {
      node.getValue().newName = colorMap.get(node.getAnnotation().hashCode());
      renamingMap.put(node.getValue().oldName, node.getValue().newName);
    }

    // Update the string nodes.
    for (Node n : stringNodesToRename) {
      String oldName = n.getString();
      Property p = propertyMap.get(oldName);
      if (p != null && p.newName != null) {
        Preconditions.checkState(oldName.equals(p.oldName));
        if (!p.newName.equals(oldName)) {
          n.setString(p.newName);
          compiler.reportCodeChange();
        }
      }
    }

    logger.fine("Collapsed " + numRenamedPropertyNames + " properties into "
                + numNewPropertyNames + " and skipped renaming "
                + numSkippedPropertyNames + " properties.");
  }

  private BitSet getRelatedTypesOnNonUnion(JSType type) {
    // All of the types we encounter should have been added to the
    // relatedBitsets via computeRelatedTypes.
    if (relatedBitsets.containsKey(type)) {
      return relatedBitsets.get(type);
    } else {
      throw new RuntimeException("Related types should have been computed for"
                                 + " type: " + type + " but have not been.");
    }
  }

  /**
   * Adds subtypes - and implementors, in the case of interfaces - of the type
   * to its JSTypeBitSet of related types. Union types are decomposed into their
   * alternative types.
   *
   * <p>The 'is related to' relationship is best understood graphically. Draw an
   * arrow from each instance type to the prototype of each of its
   * subclass. Draw an arrow from each prototype to its instance type. Draw an
   * arrow from each interface to its implementors. A type is related to another
   * if there is a directed path in the graph from the type to other. Thus, the
   * 'is related to' relationship is reflexive and transitive.
   *
   * <p>Example with Foo extends Bar which extends Baz and Bar implements I:
   * <pre>
   * Foo -> Bar.prototype -> Bar -> Baz.prototype -> Baz
   *                          ^
   *                          |
   *                          I
   * </pre>
   *
   * <p>Note that we don't need to correctly handle the relationships between
   * functions, because the function type is invalidating (i.e. its properties
   * won't be ambiguated).
   */
  private void computeRelatedTypes(JSType type) {
    if (type.isUnionType()) {
      type = type.restrictByNotNullOrUndefined();
      if (type.isUnionType()) {
        for (JSType alt : type.toMaybeUnionType().getAlternates()) {
          computeRelatedTypes(alt);
        }
        return;
      }
    }

    if (relatedBitsets.containsKey(type)) {
      // We only need to generate the bit set once.
      return;
    }

    JSTypeBitSet related = new JSTypeBitSet(intForType.size());
    relatedBitsets.put(type, related);
    related.set(getIntForType(type));

    // A prototype is related to its instance.
    if (type.isFunctionPrototypeType()) {
      addRelatedInstance(((ObjectType) type).getOwnerFunction(), related);
      return;
    }

    // An instance is related to its subclasses.
    FunctionType constructor = type.toObjectType().getConstructor();
    if (constructor != null && constructor.getSubTypes() != null) {
      for (FunctionType subType : constructor.getSubTypes()) {
        addRelatedInstance(subType, related);
      }
    }

    // An interface is related to its implementors.
    for (FunctionType implementor : compiler.getTypeRegistry()
        .getDirectImplementors(type.toObjectType())) {
      addRelatedInstance(implementor, related);
    }
  }

  /**
   * Adds the instance of the given constructor, its implicit prototype and all
   * its related types to the given bit set.
   */
  private void addRelatedInstance(
      FunctionType constructor, JSTypeBitSet related) {
    // TODO(user): A constructor which doesn't have an instance type
    // (e.g. it's missing the @constructor annotation) should be an invalidating
    // type which doesn't reach this code path.
    if (constructor.hasInstanceType()) {
      ObjectType instanceType = constructor.getInstanceType();
      related.set(getIntForType(instanceType.getImplicitPrototype()));
      computeRelatedTypes(instanceType);
      related.or(relatedBitsets.get(instanceType));
    }
  }

  class PropertyGraph implements AdjacencyGraph<Property, Void> {
    protected final Map<Property, PropertyGraphNode> nodes = Maps.newHashMap();

    PropertyGraph(Collection<Property> props) {
      for (Property prop : props) {
        nodes.put(prop, new PropertyGraphNode(prop));
      }
    }

    @Override
    public List<GraphNode<Property, Void>> getNodes() {
      return Lists.<GraphNode<Property, Void>>newArrayList(nodes.values());
    }

    @Override
    public GraphNode<Property, Void> getNode(Property property) {
      return nodes.get(property);
    }

    @Override
    public SubGraph<Property, Void> newSubGraph() {
      return new PropertySubGraph();
    }

    @Override
    public void clearNodeAnnotations() {
      for (PropertyGraphNode node : nodes.values()) {
        node.setAnnotation(null);
      }
    }

    @Override
    public int getWeight(Property value) {
      return value.numOccurrences;
    }
  }

  /**
   * A {@link SubGraph} that represents properties. The related types of
   * the properties are used to efficiently calculate adjacency information.
   */
  class PropertySubGraph implements SubGraph<Property, Void> {
    /** Types related to properties referenced in this subgraph. */
    JSTypeBitSet relatedTypes = new JSTypeBitSet(intForType.size());

    /**
     * Returns true if prop is in an independent set from all properties in this
     * sub graph.  That is, if none of its related types intersects with the
     * related types for this sub graph.
     */
    @Override
    public boolean isIndependentOf(Property prop) {
      return !relatedTypes.intersects(prop.relatedTypes);
    }

    /**
     * Adds the node to the sub graph, adding all its related types to the
     * related types for the sub graph.
     */
    @Override
    public void addNode(Property prop) {
      relatedTypes.or(prop.relatedTypes);
    }
  }

  class PropertyGraphNode implements GraphNode<Property, Void> {
    Property property;
    protected Annotation annotation;

    PropertyGraphNode(Property property) {
      this.property = property;
    }

    @Override
    public Property getValue() {
      return property;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getAnnotation() {
      return (A) annotation;
    }

    @Override
    public void setAnnotation(Annotation data) {
      annotation = data;
    }
  }

  /** A traversal callback that collects externed property names. */
  private class ProcessExterns extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
          Node dest = n.getFirstChild().getNext();
          externedNames.add(dest.getString());
          break;
        case Token.OBJECTLIT:
          for (Node child = n.getFirstChild();
               child != null;
               child = child.getNext()) {
            // names: STRING, GET, SET
            externedNames.add(child.getString());
          }
          break;
      }
    }
  }

  /** Finds all property references, recording the types on which they occur. */
  private class ProcessProperties extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP: {
          Node propNode = n.getFirstChild().getNext();
          JSType jstype = getJSType(n.getFirstChild());
          maybeMarkCandidate(propNode, jstype, t);
          break;
        }
        case Token.OBJECTLIT:
          // The children of an OBJECTLIT node are keys, where the values
          // are the children of the keys.
          for (Node key = n.getFirstChild(); key != null;
               key = key.getNext()) {
            // We only want keys that were unquoted.
            // Keys are STRING, GET, SET
            if (!key.isQuotedString()) {
              JSType jstype = getJSType(n.getFirstChild());
              maybeMarkCandidate(key, jstype, t);
            } else {
              // Ensure that we never rename some other property in a way
              // that could conflict with this quoted key.
              quotedNames.add(key.getString());
            }
          }
          break;
        case Token.GETELEM:
          // If this is a quoted property access (e.g. x['myprop']), we need to
          // ensure that we never rename some other property in a way that
          // could conflict with this quoted name.
          Node child = n.getLastChild();
          if (child.isString()) {
            quotedNames.add(child.getString());
          }
          break;
      }
    }

    /**
     * If a property node is eligible for renaming, stashes a reference to it
     * and increments the property name's access count.
     *
     * @param n The STRING node for a property
     * @param t The traversal
     */
    private void maybeMarkCandidate(Node n, JSType type, NodeTraversal t) {
      String name = n.getString();
      if (!externedNames.contains(name)) {
        stringNodesToRename.add(n);
        recordProperty(name, type);
      }
    }

    private Property recordProperty(String name, JSType type) {
      Property prop = getProperty(name);
      prop.addType(type);
      return prop;
    }
  }

  /** Returns true if properties on this type should not be renamed. */
  private boolean isInvalidatingType(JSType type) {
    if (type.isUnionType()) {
      type = type.restrictByNotNullOrUndefined();
      if (type.isUnionType()) {
        for (JSType alt : type.toMaybeUnionType().getAlternates()) {
          if (isInvalidatingType(alt)) {
            return true;
          }
        }
        return false;
      }
    }
    ObjectType objType = ObjectType.cast(type);
    return objType == null
        || invalidatingTypes.contains(objType)
        || !objType.hasReferenceName()
        || objType.isUnknownType()
        || objType.isEmptyType() /* unresolved types */
        || objType.isEnumType()
        || objType.autoboxesTo() != null;
  }

  private Property getProperty(String name) {
    Property prop = propertyMap.get(name);
    if (prop == null) {
      prop = new Property(name);
      propertyMap.put(name, prop);
    }
    return prop;
  }

  /**
   * This method gets the JSType from the Node argument and verifies that it is
   * present.
   */
  private JSType getJSType(Node n) {
    JSType jsType = n.getJSType();
    if (jsType == null) {
      // TODO(user): This branch indicates a compiler bug, not worthy of
      // halting the compilation but we should log this and analyze to track
      // down why it happens. This is not critical and will be resolved over
      // time as the type checker is extended.
      return compiler.getTypeRegistry().getNativeType(
          JSTypeNative.UNKNOWN_TYPE);
    } else {
      return jsType;
    }
  }

  /** Encapsulates the information needed for renaming a property. */
  private class Property {
    final String oldName;
    String newName;
    int numOccurrences;
    boolean skipAmbiguating;
    JSTypeBitSet relatedTypes = new JSTypeBitSet(intForType.size());

    Property(String name) {
      this.oldName = name;

      // Properties with this suffix are handled in RenameProperties.
      if (name.startsWith(SKIP_PREFIX)) {
        skipAmbiguating = true;
      }
    }

    /** Add this type to this property, calculating */
    void addType(JSType newType) {
      if (skipAmbiguating) {
        return;
      }

      ++numOccurrences;

      if (newType.isUnionType()) {
        newType = newType.restrictByNotNullOrUndefined();
        if (newType.isUnionType()) {
          for (JSType alt : newType.toMaybeUnionType().getAlternates()) {
            addNonUnionType(alt);
          }
          return;
        }
      }
      addNonUnionType(newType);
    }

    private void addNonUnionType(JSType newType) {
      if (skipAmbiguating || isInvalidatingType(newType)) {
        skipAmbiguating = true;
        return;
      }

      if (!relatedTypes.get(getIntForType(newType))) {
        computeRelatedTypes(newType);
        relatedTypes.or(getRelatedTypesOnNonUnion(newType));
      }
    }
  }

  // A BitSet that stores type info. Adds pretty-print routines.
  private class JSTypeBitSet extends BitSet {
    private static final long serialVersionUID = 1L;

    private JSTypeBitSet(int size) {
      super(size);
    }

    private JSTypeBitSet() {
      super();
    }

    /**
     * Pretty-printing, for diagnostic purposes.
     */
    @Override
    public String toString() {
      int from = 0;
      int current = 0;
      List<String> types = Lists.newArrayList();
      while (-1 != (current = nextSetBit(from))) {
        types.add(intForType.inverse().get(current).toString());
        from = current + 1;
      }
      return Joiner.on(" && ").join(types);
    }
  }
}
