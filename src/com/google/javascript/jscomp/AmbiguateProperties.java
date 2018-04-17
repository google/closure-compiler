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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.graph.AdjacencyGraph;
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.jscomp.graph.GraphColoring;
import com.google.javascript.jscomp.graph.GraphColoring.GreedyGraphColoring;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.SubGraph;
import com.google.javascript.rhino.FunctionTypeI;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
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

  private final List<Node> stringNodesToRename = new ArrayList<>();
  // Can't use these as property names.
  private final char[] reservedFirstCharacters;
  // Can't use these as property names.
  private final char[] reservedNonFirstCharacters;

  /** Map from property name to Property object */
  private final Map<String, Property> propertyMap = new HashMap<>();

  /** Property names that don't get renamed */
  private final ImmutableSet<String> externedNames;

  /** Names to which properties shouldn't be renamed, to avoid name conflicts */
  private final Set<String> quotedNames = new HashSet<>();

  /** Map from original property name to new name. Only used by tests. */
  private Map<String, String> renamingMap = null;

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

  /** A map from TypeI to a unique representative Integer. */
  private final BiMap<TypeI, Integer> intForType = HashBiMap.create();

  /** A map from TypeI to JSTypeBitSet representing the types related to the type. */
  private final Map<TypeI, JSTypeBitSet> relatedBitsets = new HashMap<>();

  /** A set of types that invalidate properties from ambiguation. */
  private final InvalidatingTypes invalidatingTypes;

  /**
   * Prefix of properties to skip renaming.  These should be renamed in the
   * RenameProperties pass.
   */
  static final String SKIP_PREFIX = "JSAbstractCompiler";

  AmbiguateProperties(
      AbstractCompiler compiler,
      char[] reservedFirstCharacters,
      char[] reservedNonFirstCharacters) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    this.compiler = compiler;
    this.reservedFirstCharacters = reservedFirstCharacters;
    this.reservedNonFirstCharacters = reservedNonFirstCharacters;

    this.invalidatingTypes = new InvalidatingTypes.Builder(compiler.getTypeIRegistry())
        .addTypesInvalidForPropertyRenaming()
        .addAllTypeMismatches(compiler.getTypeMismatches())
        .addAllTypeMismatches(compiler.getImplicitInterfaceUses())
        .build();

    this.externedNames = ImmutableSet.<String>builder()
        .add("prototype")
        .addAll(compiler.getExternProperties())
        .build();
  }

  static AmbiguateProperties makePassForTesting(
      AbstractCompiler compiler,
      char[] reservedFirstCharacters,
      char[] reservedNonFirstCharacters) {
    AmbiguateProperties ap =
        new AmbiguateProperties(compiler, reservedFirstCharacters, reservedNonFirstCharacters);
    ap.renamingMap = new HashMap<>();
    return ap;
  }

  Map<String, String> getRenamingMap() {
    checkNotNull(renamingMap);
    return renamingMap;
  }

  /** Returns an integer that uniquely identifies a JSType. */
  private int getIntForType(TypeI type) {
    // Templatized types don't exist at runtime, so collapse to raw type
    if (type != null && type.isGenericObjectType()) {
      type = type.toMaybeObjectType().getRawType();
    }
    if (intForType.containsKey(type)) {
      return intForType.get(type).intValue();
    }
    int newInt = intForType.size() + 1;
    intForType.put(type, newInt);
    return newInt;
  }

  @Override
  public void process(Node externs, Node root) {
    // Find all property references and record the types on which they occur.
    // Populate stringNodesToRename, propertyMap, quotedNames.
    NodeTraversal.traverse(compiler, root, new ProcessProperties());

    ImmutableSet.Builder<String> reservedNames = ImmutableSet.<String>builder()
        .addAll(externedNames)
        .addAll(quotedNames);

    int numRenamedPropertyNames = 0;
    int numSkippedPropertyNames = 0;
    ArrayList<PropertyGraphNode> nodes = new ArrayList<>(propertyMap.size());
    for (Property prop : propertyMap.values()) {
      if (prop.skipAmbiguating) {
        ++numSkippedPropertyNames;
        reservedNames.add(prop.oldName);
      } else {
        ++numRenamedPropertyNames;
        nodes.add(new PropertyGraphNode(prop));
      }
    }

    PropertyGraph graph = new PropertyGraph(nodes);
    GraphColoring<Property, Void> coloring =
        new GreedyGraphColoring<>(graph, FREQUENCY_COMPARATOR);
    int numNewPropertyNames = coloring.color();

    // Generate new names for the properties that will be renamed.
    NameGenerator nameGen =
        new DefaultNameGenerator(
            reservedNames.build(), "", reservedFirstCharacters, reservedNonFirstCharacters);
    String[] colorMap = new String[numNewPropertyNames];
    for (int i = 0; i < numNewPropertyNames; ++i) {
      colorMap[i] = nameGen.generateNextName();
    }

    // Translate the color of each Property instance to a name.
    for (PropertyGraphNode node : graph.getNodes()) {
      node.getValue().newName = colorMap[node.getAnnotation().hashCode()];
      if (renamingMap != null) {
        renamingMap.put(node.getValue().oldName, node.getValue().newName);
      }
    }

    // Actually assign the new names to the relevant STRING nodes in the AST.
    for (Node n : stringNodesToRename) {
      String oldName = n.getString();
      Property p = propertyMap.get(oldName);
      if (p != null && p.newName != null) {
        checkState(oldName.equals(p.oldName));
        if (!p.newName.equals(oldName)) {
          n.setString(p.newName);
          compiler.reportChangeToEnclosingScope(n);
        }
      }
    }
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Collapsed " + numRenamedPropertyNames + " properties into "
                  + numNewPropertyNames + " and skipped renaming "
                  + numSkippedPropertyNames + " properties.");
    }
  }

  private BitSet getRelatedTypesOnNonUnion(TypeI type) {
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
   * <pre>{@code
   * Foo -> Bar.prototype -> Bar -> Baz.prototype -> Baz
   *                          ^
   *                          |
   *                          I
   * }</pre>
   *
   * <p>Note that we don't need to correctly handle the relationships between
   * functions, because the function type is invalidating (i.e. its properties
   * won't be ambiguated).
   */
  private void computeRelatedTypes(TypeI type) {
    if (type.isUnionType()) {
      type = type.restrictByNotNullOrUndefined();
      if (type.isUnionType()) {
        for (TypeI alt : type.getUnionMembers()) {
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
    if (type.isPrototypeObject()) {
      FunctionTypeI maybeCtor = type.toMaybeObjectType().getOwnerFunction();
      if (maybeCtor.isConstructor() || maybeCtor.isInterface()) {
        addRelatedInstance(maybeCtor, related);
      }
      return;
    }

    // A class/interface is related to its subclasses/implementors.
    FunctionTypeI constructor = type.toMaybeObjectType().getConstructor();
    if (constructor != null) {
      for (FunctionTypeI subType : constructor.getDirectSubTypes()) {
        addRelatedInstance(subType, related);
      }
    }
  }

  /**
   * Adds the instance of the given constructor, its implicit prototype and all
   * its related types to the given bit set.
   */
  private void addRelatedInstance(FunctionTypeI constructor, JSTypeBitSet related) {
    checkArgument(constructor.hasInstanceType(),
        "Constructor %s without instance type.", constructor);
    ObjectTypeI instanceType = constructor.getInstanceType();
    related.set(getIntForType(instanceType.getPrototypeObject()));
    computeRelatedTypes(instanceType);
    related.or(relatedBitsets.get(instanceType));
  }

  class PropertyGraph implements AdjacencyGraph<Property, Void> {
    private final ArrayList<PropertyGraphNode> nodes;

    PropertyGraph(ArrayList<PropertyGraphNode> nodes) {
      this.nodes = nodes;
    }

    @Override
    public List<PropertyGraphNode> getNodes() {
      return nodes;
    }

    @Override
    public int getNodeCount() {
      return nodes.size();
    }

    @Override
    public GraphNode<Property, Void> getNode(Property property) {
      throw new RuntimeException("PropertyGraph#getNode is never called.");
    }

    @Override
    public SubGraph<Property, Void> newSubGraph() {
      return new PropertySubGraph();
    }

    @Override
    public void clearNodeAnnotations() {
      for (PropertyGraphNode node : nodes) {
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

  static class PropertyGraphNode implements GraphNode<Property, Void> {
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
    public Annotation getAnnotation() {
      return annotation;
    }

    @Override
    public void setAnnotation(Annotation data) {
      annotation = data;
    }
  }

  private void reportInvalidRenameFunction(Node n, String functionName, String message) {
    compiler.report(
        JSError.make(
            n, DisambiguateProperties.Warnings.INVALID_RENAME_FUNCTION, functionName, message));
  }
  private static final String WRONG_ARGUMENT_COUNT = " Must be called with 1 or 2 arguments.";
  private static final String WANT_STRING_LITERAL = " The first argument must be a string literal.";
  private static final String DO_NOT_WANT_PATH = " The first argument must not be a property path.";

  /** Finds all property references, recording the types on which they occur. */
  private class ProcessProperties extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case GETPROP: {
          Node propNode = n.getSecondChild();
          TypeI type = getTypeI(n.getFirstChild());
          maybeMarkCandidate(propNode, type);
          return;
        }
        case CALL: {
          Node target = n.getFirstChild();
          if (!target.isQualifiedName()) {
            return;
          }

          String renameFunctionName = target.getOriginalQualifiedName();
          if (renameFunctionName != null
              && compiler.getCodingConvention().isPropertyRenameFunction(renameFunctionName)) {
            int childCount = n.getChildCount();
            if (childCount != 2 && childCount != 3) {
              reportInvalidRenameFunction(n, renameFunctionName, WRONG_ARGUMENT_COUNT);
              return;
            }

            Node propName = n.getSecondChild();
            if (!propName.isString()) {
              reportInvalidRenameFunction(n, renameFunctionName, WANT_STRING_LITERAL);
              return;
            }

            if (propName.getString().contains(".")) {
              reportInvalidRenameFunction(n, renameFunctionName, DO_NOT_WANT_PATH);
              return;
            }

            maybeMarkCandidate(propName, getTypeI(n.getSecondChild()));
          } else if (NodeUtil.isObjectDefinePropertiesDefinition(n)) {
            Node typeObj = n.getSecondChild();
            TypeI type = getTypeI(typeObj);
            Node objectLiteral = typeObj.getNext();

            if (!objectLiteral.isObjectLit()) {
              return;
            }

            for (Node key : objectLiteral.children()) {
              if (key.isQuotedString()) {
                quotedNames.add(key.getString());
              } else {
                maybeMarkCandidate(key, type);
              }
            }
          }
          return;
        }
        case OBJECTLIT:
          // Object.defineProperties literals are handled at the CALL node.
          if (n.getParent().isCall()
              && NodeUtil.isObjectDefinePropertiesDefinition(n.getParent())) {
            return;
          }

          // The children of an OBJECTLIT node are keys, where the values
          // are the children of the keys.
          TypeI type = getTypeI(n);
          for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
            // We only want keys that were unquoted.
            // Keys are STRING, GET, SET
            if (key.isQuotedString()) {
              // Ensure that we never rename some other property in a way
              // that could conflict with this quoted key.
              quotedNames.add(key.getString());
            } else {
              maybeMarkCandidate(key, type);
            }
          }
          return;
        case GETELEM:
          // If this is a quoted property access (e.g. x['myprop']), we need to
          // ensure that we never rename some other property in a way that
          // could conflict with this quoted name.
          Node child = n.getLastChild();
          if (child.isString()) {
            quotedNames.add(child.getString());
          }
          return;
        default:
          // Nothing to do.
      }
    }

    /**
     * If a property node is eligible for renaming, stashes a reference to it
     * and increments the property name's access count.
     *
     * @param n The STRING node for a property
     */
    private void maybeMarkCandidate(Node n, TypeI type) {
      String name = n.getString();
      if (!externedNames.contains(name)) {
        stringNodesToRename.add(n);
        recordProperty(name, type);
      }
    }

    private Property recordProperty(String name, TypeI type) {
      Property prop = getProperty(name);
      prop.addType(type);
      return prop;
    }
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
  private TypeI getTypeI(Node n) {
    if (n == null) {
      return compiler.getTypeIRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }

    TypeI type = n.getTypeI();
    if (type == null) {
      // TODO(user): This branch indicates a compiler bug, not worthy of
      // halting the compilation but we should log this and analyze to track
      // down why it happens. This is not critical and will be resolved over
      // time as the type checker is extended.
      return compiler.getTypeIRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE);
    } else {
      return type;
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
    void addType(TypeI newType) {
      if (skipAmbiguating) {
        return;
      }

      ++numOccurrences;

      if (newType.isUnionType()) {
        newType = newType.restrictByNotNullOrUndefined();
        if (newType.isUnionType()) {
          for (TypeI alt : newType.getUnionMembers()) {
            addNonUnionType(alt);
          }
          return;
        }
      }
      addNonUnionType(newType);
    }

    private void addNonUnionType(TypeI newType) {
      if (skipAmbiguating || invalidatingTypes.isInvalidating(newType)) {
        skipAmbiguating = true;
        return;
      }
      ObjectTypeI maybeObj = newType.toMaybeObjectType();
      if (maybeObj != null) {
        newType = maybeObj.withoutStrayProperties();
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
      List<String> types = new ArrayList<>();
      while (-1 != (current = nextSetBit(from))) {
        types.add(intForType.inverse().get(current).toString());
        from = current + 1;
      }
      return Joiner.on(" && ").join(types);
    }
  }
}
