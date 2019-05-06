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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
  // Can't use these to start property names.
  private final char[] reservedFirstCharacters;
  // Can't use these at all in property names.
  private final char[] reservedNonFirstCharacters;

  /** Map from property name to Property object */
  private final Map<String, Property> propertyMap = new LinkedHashMap<>();

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

  /** A map from JSType to a unique representative Integer. */
  private final BiMap<JSType, Integer> intForType = HashBiMap.create();

  /**
   * A map from JSType to JSTypeBitSet representing the types related to the type.
   *
   * <p>A type is always related to itself.
   */
  private final Map<JSType, JSTypeBitSet> relatedBitsets = new HashMap<>();

  /** A set of types that invalidate properties from ambiguation. */
  private final InvalidatingTypes invalidatingTypes;

  AmbiguateProperties(
      AbstractCompiler compiler,
      char[] reservedFirstCharacters,
      char[] reservedNonFirstCharacters) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    this.compiler = compiler;
    this.reservedFirstCharacters = reservedFirstCharacters;
    this.reservedNonFirstCharacters = reservedNonFirstCharacters;

    this.invalidatingTypes = new InvalidatingTypes.Builder(compiler.getTypeRegistry())
        .addTypesInvalidForPropertyRenaming()
        .addAllTypeMismatches(compiler.getTypeMismatches())
        .addAllTypeMismatches(compiler.getImplicitInterfaceUses())
        .build();

    this.externedNames =
        ImmutableSet.<String>builder()
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
  private int getIntForType(JSType type) {
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

    // We may have renamed getter / setter properties.
    GatherGettersAndSetterProperties.update(compiler, externs, root);

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Collapsed " + numRenamedPropertyNames + " properties into "
                  + numNewPropertyNames + " and skipped renaming "
                  + numSkippedPropertyNames + " properties.");
    }
  }

  private BitSet getRelatedTypesOnNonUnion(JSType type) {
    // All of the types we encounter should have been added to the
    // relatedBitsets via computeRelatedTypesForNonUnionType.
    if (relatedBitsets.containsKey(type)) {
      return relatedBitsets.get(type);
    } else {
      throw new RuntimeException("Related types should have been computed for"
                                 + " type: " + type + " but have not been.");
    }
  }

  /**
   * Adds subtypes - and implementors, in the case of interfaces - of the type to its JSTypeBitSet
   * of related types.
   *
   * <p>The 'is related to' relationship is best understood graphically. Draw an arrow from each
   * instance type to the prototype of each of its subclass. Draw an arrow from each prototype to
   * its instance type. Draw an arrow from each interface to its implementors. A type is related to
   * another if there is a directed path in the graph from the type to other. Thus, the 'is related
   * to' relationship is reflexive and transitive.
   *
   * <p>Example with Foo extends Bar which extends Baz and Bar implements I:
   *
   * <pre>{@code
   * Foo -> Bar.prototype -> Bar -> Baz.prototype -> Baz
   *                          ^
   *                          |
   *                          I
   * }</pre>
   *
   * <p>We also need to handle relationships between functions because of ES6 class-side inheritance
   * although the top Function type itself is invalidating.
   */
  @SuppressWarnings("ReferenceEquality")
  private void computeRelatedTypesForNonUnionType(JSType type) {
    // This method could be expanded to handle union types if necessary, but currently no union
    // types are ever passed as input so the method doesn't have logic for union types
    checkState(!type.isUnionType(), type);

    if (relatedBitsets.containsKey(type)) {
      // We only need to generate the bit set once.
      return;
    }

    JSTypeBitSet related = new JSTypeBitSet(intForType.size());
    relatedBitsets.put(type, related);
    related.set(getIntForType(type));

    // A prototype is related to its instance.
    if (type.isFunctionPrototypeType()) {
      FunctionType maybeCtor = type.toMaybeObjectType().getOwnerFunction();
      if (maybeCtor.isConstructor() || maybeCtor.isInterface()) {
        addRelatedInstance(maybeCtor, related);
      }
      return;
    }

    // A class/interface is related to its subclasses/implementors.
    FunctionType constructor = type.toMaybeObjectType().getConstructor();
    if (constructor != null) {
      for (FunctionType subType : constructor.getDirectSubTypes()) {
        addRelatedInstance(subType, related);
      }
    }

    // We only specifically handle implicit prototypes of functions in the case of ES6 classes
    // For regular functions, the implicit prototype being Function.prototype does not matter
    // because the type `Function` is invalidating.
    // This may cause unexpected behavior for code that manually sets a prototype, e.g.
    //   Object.setPrototypeOf(myFunction, prototypeObj);
    // but code like that should not be used with --ambiguate_properties or type-based optimizations
    FunctionType fnType = type.toMaybeFunctionType();
    if (fnType != null) {
      for (FunctionType subType : fnType.getDirectSubTypes()) {
        // We record all subtypes of constructors, but don't care about old 'ES5-style' subtyping,
        // just ES6-style. This is equivalent to saying that the subtype constructor's implicit
        // prototype is the given type
        if (fnType == subType.getImplicitPrototype()) {
          addRelatedType(subType, related);
        }
      }
    }
  }

  /**
   * Adds the instance of the given constructor, its implicit prototype and all
   * its related types to the given bit set.
   */
  private void addRelatedInstance(FunctionType constructor, JSTypeBitSet related) {
    checkArgument(constructor.hasInstanceType(),
        "Constructor %s without instance type.", constructor);
    ObjectType instanceType = constructor.getInstanceType();
    addRelatedType(instanceType, related);
  }

  /** Adds the given type and all its related types to the given bit set. */
  private void addRelatedType(JSType type, JSTypeBitSet related) {
    computeRelatedTypesForNonUnionType(type);
    related.or(relatedBitsets.get(type));
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
            n, PropertyRenamingDiagnostics.INVALID_RENAME_FUNCTION, functionName, message));
  }
  private static final String WRONG_ARGUMENT_COUNT = " Must be called with 1 or 2 arguments.";
  private static final String WANT_STRING_LITERAL = " The first argument must be a string literal.";
  private static final String DO_NOT_WANT_PATH = " The first argument must not be a property path.";

  /** Finds all property references, recording the types on which they occur. */
  private class ProcessProperties extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case GETPROP:
          processGetProp(n);
          return;

        case CALL:
          processCall(n);
          return;

        case OBJECTLIT:
        case OBJECT_PATTERN:
          processObjectLitOrPattern(n);
          return;

        case GETELEM:
          processGetElem(n);
          return;

        case CLASS:
          processClass(n);
          return;

        default:
          // Nothing to do.
      }
    }

    private void processGetProp(Node getProp) {
      Node propNode = getProp.getSecondChild();
      JSType type = getJSType(getProp.getFirstChild());
      maybeMarkCandidate(propNode, type);
    }

    private void processCall(Node call) {
      Node target = call.getFirstChild();
      if (!target.isQualifiedName()) {
        return;
      }

      String renameFunctionName = target.getOriginalQualifiedName();
      if (renameFunctionName != null
          && compiler.getCodingConvention().isPropertyRenameFunction(renameFunctionName)) {
        int childCount = call.getChildCount();
        if (childCount != 2 && childCount != 3) {
          reportInvalidRenameFunction(call, renameFunctionName, WRONG_ARGUMENT_COUNT);
          return;
        }

        Node propName = call.getSecondChild();
        if (!propName.isString()) {
          reportInvalidRenameFunction(call, renameFunctionName, WANT_STRING_LITERAL);
          return;
        }

        if (propName.getString().contains(".")) {
          reportInvalidRenameFunction(call, renameFunctionName, DO_NOT_WANT_PATH);
          return;
        }

        // Skip ambiguation for properties in renaming calls
        // NOTE (lharker@) - I'm not sure if this behavior is necessary, or if we could safely
        // ambiguate the property as long as we also updated the property renaming call
        Property p = getProperty(propName.getString());
        p.skipAmbiguating = true;
      } else if (NodeUtil.isObjectDefinePropertiesDefinition(call)) {
        Node typeObj = call.getSecondChild();
        JSType type = getJSType(typeObj);
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
    }

    private void processObjectLitOrPattern(Node objectLit) {
      // Object.defineProperties literals are handled at the CALL node.
      if (objectLit.getParent().isCall()
          && NodeUtil.isObjectDefinePropertiesDefinition(objectLit.getParent())) {
        return;
      }

      // The children of an OBJECTLIT node are keys, where the values
      // are the children of the keys.
      JSType type = getJSType(objectLit);
      for (Node key = objectLit.getFirstChild(); key != null; key = key.getNext()) {
        switch (key.getToken()) {
          case COMPUTED_PROP:
            if (key.getFirstChild().isString()) {
              // If this quoted prop name is statically determinable, ensure we don't rename some
              // other property in a way that could conflict with it.
              //
              // This is largely because we store quoted member functions as computed properties and
              // want to be consistent with how other quoted properties invalidate property names.
              quotedNames.add(key.getFirstChild().getString());
            }
            break;

          case MEMBER_FUNCTION_DEF:
          case GETTER_DEF:
          case SETTER_DEF:
          case STRING_KEY:
            if (key.isQuotedString()) {
              // If this quoted prop name is statically determinable, ensure we don't rename some
              // other property in a way that could conflict with it
              quotedNames.add(key.getString());
            } else {
              maybeMarkCandidate(key, type);
            }
            break;

          case REST:
          case SPREAD:
            break; // Nothing to do.

          default:
            throw new IllegalStateException(
                "Unexpected child of " + objectLit.getToken() + ": " + key.toStringTree());
        }
      }
    }

    private void processGetElem(Node n) {
      // If this is a quoted property access (e.g. x['myprop']), we need to
      // ensure that we never rename some other property in a way that
      // could conflict with this quoted name.
      Node child = n.getLastChild();
      if (child.isString()) {
        quotedNames.add(child.getString());
      }
    }

    private void processClass(Node classNode) {
      JSType classConstructorType = getJSType(classNode);
      JSType classPrototype =
          classConstructorType.isFunctionType()
              ? classConstructorType.toMaybeFunctionType().getPrototype()
              : compiler.getTypeRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE);
      for (Node member : NodeUtil.getClassMembers(classNode).children()) {
        if (member.isQuotedString()) {
          // ignore get 'foo'() {} and prevent property name collisions
          // Note that only getters/setters are represented as quoted strings, not 'foo'() {}
          // see https://github.com/google/closure-compiler/issues/3071
          quotedNames.add(member.getString());
          continue;
        } else if (member.isComputedProp()) {
          // ignore ['foo']() {}
          // for simple cases, we also prevent renaming collisions
          if (member.getFirstChild().isString()) {
            quotedNames.add(member.getFirstChild().getString());
          }
          continue;
        } else if (NodeUtil.isEs6ConstructorMemberFunctionDef(member)) {
          // don't rename `class C { constructor() {} }` !
          // This only applies for ES6 classes, not generic properties called 'constructor', which
          // is why it's handled in this method specifically.
          continue;
        }

        JSType memberOwnerType = member.isStaticMember() ? classConstructorType : classPrototype;

        // member could be a MEMBER_FUNCTION_DEF, GETTER_DEF, or SETTER_DEF
        maybeMarkCandidate(member, memberOwnerType);
      }
    }

    /**
     * If a property node is eligible for renaming, stashes a reference to it
     * and increments the property name's access count.
     *
     * @param n The STRING node for a property
     */
    private void maybeMarkCandidate(Node n, JSType type) {
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
    JSType type = n.getJSType();
    if (type == null) {
      // TODO(bradfordcsmith): This branch indicates a compiler bug. It should throw an exception.
      return compiler.getTypeRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE);
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
          for (JSType alt : newType.getUnionMembers()) {
            addNonUnionType(alt);
          }
          return;
        }
      }
      addNonUnionType(newType);
    }

    private void addNonUnionType(JSType newType) {
      if (skipAmbiguating || invalidatingTypes.isInvalidating(newType)) {
        skipAmbiguating = true;
        return;
      }
      if (!relatedTypes.get(getIntForType(newType))) {
        computeRelatedTypesForNonUnionType(newType);
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
