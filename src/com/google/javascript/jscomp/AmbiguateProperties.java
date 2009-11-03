/*
 * Copyright 2008 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.TypeValidator.TypeMismatch;
import com.google.javascript.jscomp.graph.AdjacencyGraph;
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.SubGraph;
import com.google.javascript.jscomp.graph.GraphColoring;
import com.google.javascript.jscomp.graph.GraphColoring.GreedyGraphColoring;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionPrototypeType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.InstanceObjectType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.UnionType;

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

  /** Map from color assigned by GraphColoring to new name. */
  private final Map<Integer, String> colorMap = Maps.newHashMap();

  /**
   * Sorts Property objects by their count, breaking ties alphabetically to
   * ensure a deterministic total ordering.
   */
  private static final Comparator<Property> FREQUENCY_COMPARATOR =
      new Comparator<Property>() {
        public int compare(Property p1, Property p2) {
          if (p1.numOccurrences != p2.numOccurrences) {
            return p2.numOccurrences - p1.numOccurrences;
          }
          return p1.oldName.compareTo(p2.oldName);
        }
      };

  /** A map from JSType to a unique representative Integer. */
  private Map<JSType, Integer> intForType = Maps.newHashMap();

  /** A map from JSType to BitSet representing the types related to the type. */
  private Map<JSType, BitSet> relatedBitsets = Maps.newHashMap();

  /** A set of types that invalidate properties from ambiguation. */
  private final Set<JSType> invalidatingTypes;

  /**
   * Prefix of properties to skip renaming.  These should be renamed in the
   * RenameProperties pass.
   */
  static final String SKIP_PREFIX = "JSAbstractCompiler";

  AmbiguateProperties(AbstractCompiler compiler,
      char[] reservedCharacters) {
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
        r.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
        r.getNativeType(JSTypeNative.GLOBAL_THIS),
        r.getNativeType(JSTypeNative.OBJECT_TYPE),
        r.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
        r.getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE),
        r.getNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE),
        r.getNativeType(JSTypeNative.UNKNOWN_TYPE));

    for (TypeMismatch mis : compiler.getTypeValidator().getMismatches()) {
      addInvalidatingType(mis.first);
      addInvalidatingType(mis.second);
    }
  }

  /**
   * Invalidates the given type, so that no properties on it will be renamed.
   */
  private void addInvalidatingType(JSType type) {
    type = type.restrictByNotNullOrUndefined();
    if (type instanceof UnionType) {
      for (JSType alt : ((UnionType) type).getAlternates()) {
        addInvalidatingType(alt);
      }
    }

    invalidatingTypes.add(type);
    if (type instanceof InstanceObjectType) {
      invalidatingTypes.add(((ObjectType) type).getImplicitPrototype());
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
        computeRelatedTypes(p.type);
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

    logger.info("Collapsed " + numRenamedPropertyNames + " properties into "
                + numNewPropertyNames + " and skipped renaming "
                + numSkippedPropertyNames + " properties.");
  }

  /** Add supertypes of the type to its BitSet of related types. */
  private void computeRelatedTypes(JSType type) {
    if (type instanceof UnionType) {
      type = type.restrictByNotNullOrUndefined();
      if (type instanceof UnionType) {
        for (JSType alt : ((UnionType) type).getAlternates()) {
          computeRelatedTypes(alt);
        }
        return;
      }
    }

    BitSet related = relatedBitsets.get(type);
    if (related == null) {
      related = new BitSet(intForType.size());
      relatedBitsets.put(type, related);
    }

    ObjectType parentType = (ObjectType) type;
    while (parentType != null) {
      related.set(getIntForType(parentType));
      parentType = parentType.getImplicitPrototype();
    }

    FunctionType constructor = null;
    if (type instanceof FunctionType) {
      constructor = (FunctionType) type;
    } else if (type instanceof FunctionPrototypeType) {
      constructor = ((FunctionPrototypeType) type).getOwnerFunction();
    } else {
      constructor = ((ObjectType) type).getConstructor();
    }
    if (constructor != null) {
      Set<ObjectType> interfaces = constructor.getAllImplementedInterfaces();
      for (ObjectType itype : interfaces) {
        related.set(getIntForType(itype));
      }
    }
  }

  class PropertyGraph implements AdjacencyGraph<Property, Void> {
    protected final Map<Property, PropertyGraphNode> nodes = Maps.newHashMap();

    PropertyGraph(Collection<Property> props) {
      for (Property prop : props) {
        nodes.put(prop, new PropertyGraphNode(prop));
      }
    }

    public List<GraphNode<Property, Void>> getNodes() {
      return Lists.<GraphNode<Property, Void>>newArrayList(nodes.values());
    }

    public GraphNode<Property, Void> getNode(Property property) {
      return nodes.get(property);
    }

    public SubGraph<Property, Void> newSubGraph() {
      return new PropertySubGraph();
    }

    public void clearNodeAnnotations() {
      for (PropertyGraphNode node : nodes.values()) {
        node.setAnnotation(null);
      }
    }

    public int getWeight(Property value) {
      return value.numOccurrences;
    }
  }

  /**
   * A {@link SubGraph} that represents properties. The types of the properties
   * are used to efficiently calculate adjacency information.
   */
  class PropertySubGraph implements SubGraph<Property, Void> {
    /** Types from which properties in this subgraph are referenced. */
    BitSet typesInSet = new BitSet(intForType.size());

    /** Types related to types in {@code typesInSet}. */
    BitSet typesRelatedToSet = new BitSet(intForType.size());

    /**
     * Returns true if prop is in an independent set from all properties in
     * this sub graph.  That is, if none of its types is contained in the
     * related types for this sub graph and if none if its related types is one
     * of the types in the sub graph.
     */
    public boolean isIndependentOf(Property prop) {
      if (typesRelatedToSet.intersects(prop.typesSet)) {
        return false;
      }
      return !getRelated(prop.type).intersects(typesInSet);
    }

    /**
     * Adds the node to the sub graph, adding all of its types to the set of
     * types in the sub graph and all of its related types to the related types
     * for the sub graph.
     */
    public void addNode(Property prop) {
      typesInSet.or(prop.typesSet);
      typesRelatedToSet.or(getRelated(prop.type));
    }

    /**
     * Finds all types related to the provided type and returns a BitSet with
     * their bits to true.
     */
    private BitSet getRelated(JSType type) {
      BitSet relatedTypes = new BitSet(intForType.size());
      if (type instanceof UnionType) {
        for (JSType alt : ((UnionType) type).getAlternates()) {
          getRelatedTypesOnNonUnion(alt, relatedTypes);
        }
      } else {
        getRelatedTypesOnNonUnion(type, relatedTypes);
      }
      return relatedTypes;
    }

    /**
     * Finds all types related to the provided type and returns a BitSet with
     * their bits to true.  Expects a non-union type.
     */
    private void getRelatedTypesOnNonUnion(JSType type, BitSet relatedTypes) {
      // All of the types we encounter should have been added to the
      // relatedBitsets via computeRelatedTypes.
      if (relatedBitsets.containsKey(type)) {
        relatedTypes.or(relatedBitsets.get(type));
      } else {
        throw new RuntimeException("Related types should have been computed for"
                                   + "type: " + type + " but have not been.");
      }
    }
  }

  class PropertyGraphNode implements GraphNode<Property, Void> {
    Property property;
    protected Annotation annotation;

    PropertyGraphNode(Property property) {
      this.property = property;
    }

    public Property getValue() {
      return property;
    }

    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getAnnotation() {
      return (A) annotation;
    }

    public void setAnnotation(Annotation data) {
      annotation = data;
    }
  }

  /** A traversal callback that collects externed property names. */
  private class ProcessExterns extends AbstractPostOrderCallback {
    /** {@inheritDoc} */
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
          Node dest = n.getFirstChild().getNext();
          externedNames.add(dest.getString());
          break;
        case Token.OBJECTLIT:
          for (Node child = n.getFirstChild();
               child != null;
               child = child.getNext().getNext()) {
            if (child.getType() == Token.STRING) {
              externedNames.add(child.getString());
            }
          }
          break;
      }
    }
  }

  /** Finds all property references, recording the types on which they occur. */
  private class ProcessProperties extends AbstractPostOrderCallback {
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP: {
          Node propNode = n.getFirstChild().getNext();
          JSType jstype = getJSType(n.getFirstChild());
          maybeMarkCandidate(propNode, jstype, t);
          break;
        }
        case Token.OBJECTLIT:
          // The children of an OBJECTLIT node are alternating key/value pairs.
          // We skip the values.
          for (Node key = n.getFirstChild(); key != null;
               key = key.getNext().getNext()) {
            // We only want keys that are strings (not numbers), and only keys
            // that were unquoted.
            if (key.getType() == Token.STRING) {
              if (!key.isQuotedString()) {
                JSType jstype = getJSType(n.getFirstChild());
                maybeMarkCandidate(key, jstype, t);
              } else {
                // Ensure that we never rename some other property in a way
                // that could conflict with this quoted key.
                quotedNames.add(key.getString());
              }
            }
          }
          break;
        case Token.GETELEM:
          // If this is a quoted property access (e.g. x['myprop']), we need to
          // ensure that we never rename some other property in a way that
          // could conflict with this quoted name.
          Node child = n.getLastChild();
          if (child.getType() == Token.STRING) {
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
    if (type instanceof UnionType) {
      type = type.restrictByNotNullOrUndefined();
      if (type instanceof UnionType) {
        for (JSType alt : ((UnionType) type).getAlternates()) {
          if (isInvalidatingType(alt)) {
            return true;
          }
        }
        return false;
      }
    }
    return type == null || !(type instanceof ObjectType)
        || invalidatingTypes.contains(type)
        || !((ObjectType) type).hasName()
        || (type.isNamedType() && type.isUnknownType())
        || type.isEnumType() || type.autoboxesTo() != null;
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
    JSType type;
    String newName;
    int numOccurrences;
    boolean skipAmbiguating;
    BitSet typesSet = new BitSet(intForType.size());

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

      if (newType instanceof UnionType) {
        newType = newType.restrictByNotNullOrUndefined();
        if (newType instanceof UnionType) {
          for (JSType alt : ((UnionType) newType).getAlternates()) {
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

      if (type == null) {
        type = newType;
      } else {
        type = type.getLeastSupertype(newType);
      }

      typesSet.set(getIntForType(newType));
    }
  }
}
