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

package com.google.javascript.jscomp.disambiguate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DefaultNameGenerator;
import com.google.javascript.jscomp.DotFormatter;
import com.google.javascript.jscomp.GatherGetterAndSetterProperties;
import com.google.javascript.jscomp.NameGenerator;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.jscomp.graph.AdjacencyGraph;
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.GraphColoring;
import com.google.javascript.jscomp.graph.GraphColoring.GreedyGraphColoring;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.jscomp.graph.LowestCommonAncestorFinder;
import com.google.javascript.jscomp.graph.SubGraph;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.nullness.Nullable;

/**
 * Renames unrelated properties to the same name, using {@link Color}s provided by the typechecker.
 * This allows better compression as more properties can be given short names.
 *
 * <p>Properties are considered unrelated if they are never referenced from the same color or from a
 * subtype of each others' colors, thus this pass is only effective if type checking is enabled.
 *
 * <p>Example: <code>
 *   Foo.fooprop = 0;
 *   Foo.fooprop2 = 0;
 *   Bar.barprop = 0;
 * </code> becomes: <code>
 *   Foo.a = 0;
 *   Foo.b = 0;
 *   Bar.a = 0;
 * </code>
 */
public class AmbiguateProperties implements CompilerPass {
  private static final Logger logger = Logger.getLogger(AmbiguateProperties.class.getName());

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

  private final ColorRegistry colorRegistry;

  /** Map from original property name to new name. Only used by tests. */
  private @Nullable Map<String, String> renamingMap = null;

  private @Nullable ColorGraphNodeFactory graphNodeFactory = null;

  /**
   * Sorts Property objects by their count, breaking ties alphabetically to ensure a deterministic
   * total ordering.
   */
  private static final Comparator<Property> FREQUENCY_COMPARATOR =
      (Property p1, Property p2) -> {
        if (p1.numOccurrences != p2.numOccurrences) {
          return p2.numOccurrences - p1.numOccurrences;
        }
        return p1.oldName.compareTo(p2.oldName);
      };

  public AmbiguateProperties(
      AbstractCompiler compiler,
      char[] reservedFirstCharacters,
      char[] reservedNonFirstCharacters,
      Set<String> externProperties) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    this.compiler = compiler;
    this.reservedFirstCharacters = reservedFirstCharacters;
    this.reservedNonFirstCharacters = reservedNonFirstCharacters;

    this.externedNames =
        ImmutableSet.<String>builder().add("prototype").addAll(externProperties).build();
    this.colorRegistry = compiler.getColorRegistry();
  }

  static AmbiguateProperties makePassForTesting(
      AbstractCompiler compiler,
      char[] reservedFirstCharacters,
      char[] reservedNonFirstCharacters,
      Set<String> externProperties) {
    AmbiguateProperties ap =
        new AmbiguateProperties(
            compiler, reservedFirstCharacters, reservedNonFirstCharacters, externProperties);
    ap.renamingMap = new HashMap<>();
    return ap;
  }

  Map<String, String> getRenamingMap() {
    checkNotNull(renamingMap);
    return renamingMap;
  }

  @Override
  public void process(Node externs, Node root) {
    this.graphNodeFactory = ColorGraphNodeFactory.createFactory(this.colorRegistry);

    // Find all property references and record the types on which they occur.
    // Populate stringNodesToRename, propertyMap, quotedNames.
    NodeTraversal.traverse(compiler, root, new ProcessPropertiesAndConstructors());

    ColorGraphBuilder graphBuilder =
        new ColorGraphBuilder(
            graphNodeFactory, LowestCommonAncestorFinder::new, this.colorRegistry);
    graphBuilder.addAll(graphNodeFactory.getAllKnownTypes());
    LinkedDirectedGraph<ColorGraphNode, Object> colorGraph = graphBuilder.build();
    try (LogFile chunkGraphLog = compiler.createOrReopenLog(this.getClass(), "color_graph.dot")) {
      chunkGraphLog.log(DotFormatter.toDot(colorGraph));
    }
    for (ColorGraphNode node : graphNodeFactory.getAllKnownTypes()) {
      node.getSubtypeIndices().set(node.getIndex()); // Init subtyping as reflexive.
    }

    FixedPointGraphTraversal.<ColorGraphNode, Object>newReverseTraversal(
            (subtype, e, supertype) -> {
              /*
               * Cheap path for when we're sure there's going to be a change.
               *
               * <p>Since bits only ever turn on, using more bits means there are definitely more
               * elements. This prevents of from needing to check cardinality or equality, which
               * would otherwise dominate the cost of computing the fixed point.
               *
               * <p>We're guaranteed to converge because the sizes will be euqal after the OR
               * operation.
               */
              if (subtype.getSubtypeIndices().size() > supertype.getSubtypeIndices().size()) {
                supertype.getSubtypeIndices().or(subtype.getSubtypeIndices());
                return true;
              }

              int startSize = supertype.getSubtypeIndices().cardinality();
              supertype.getSubtypeIndices().or(subtype.getSubtypeIndices());
              return supertype.getSubtypeIndices().cardinality() > startSize;
            })
        .computeFixedPoint(colorGraph);

    // Fill in all transitive edges in subtyping graph per property
    for (Property prop : propertyMap.values()) {
      if (prop.relatedColorsSeeds == null) {
        continue;
      }
      for (ColorGraphNode color : prop.relatedColorsSeeds.keySet()) {
        prop.relatedColors.or(color.getSubtypeIndices());
      }
      prop.relatedColorsSeeds = null;
    }

    ImmutableSet.Builder<String> reservedNames =
        ImmutableSet.<String>builder().addAll(externedNames).addAll(quotedNames);
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
    final int finalNumRenamedPropertyNames = numRenamedPropertyNames;
    final int finalNumSkippedPropertyNames = numSkippedPropertyNames;

    PropertyGraph propertyGraph = new PropertyGraph(nodes);
    GraphColoring<Property, Void> coloring =
        new GreedyGraphColoring<>(propertyGraph, FREQUENCY_COMPARATOR);
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
    for (PropertyGraphNode node : propertyGraph.getNodes()) {
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
    // TODO(b/161947315): this shouldn't be the responsibility of AmbiguateProperties
    GatherGetterAndSetterProperties.update(compiler, externs, root);

    Supplier<String> summarySupplier =
        Suppliers.memoize(
            () ->
                "Collapsed "
                    + finalNumRenamedPropertyNames
                    + " properties into "
                    + numNewPropertyNames
                    + " and skipped renaming "
                    + finalNumSkippedPropertyNames
                    + " properties.");
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(summarySupplier.get());
    }
    compiler.reportAmbiguatePropertiesSummary(summarySupplier);
  }

  static class PropertyGraph implements AdjacencyGraph<Property, Void> {
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
   * A {@link SubGraph} that represents properties. The related types of the properties are used to
   * efficiently calculate adjacency information.
   */
  static class PropertySubGraph implements SubGraph<Property, Void> {
    /** Types related to properties referenced in this subgraph. */
    final BitSet relatedTypes = new BitSet();

    /**
     * Returns true if prop is in an independent set from all properties in this sub graph. That is,
     * if none of its related types intersects with the related types for this sub graph.
     */
    @Override
    public boolean isIndependentOf(Property prop) {
      return !this.relatedTypes.intersects(prop.relatedColors);
    }

    /**
     * Adds the node to the sub graph, adding all its related types to the related types for the sub
     * graph.
     */
    @Override
    public void addNode(Property prop) {
      this.relatedTypes.or(prop.relatedColors);
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
    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getAnnotation() {
      return (A) annotation;
    }

    @Override
    public void setAnnotation(@Nullable Annotation data) {
      annotation = data;
    }
  }

  /**
   * Finds all property references, recording the types on which they occur, and records all
   * constructors and their instance types in the {@link ColorGraphNodeFactory}.
   */
  private class ProcessPropertiesAndConstructors extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case GETPROP:
        case OPTCHAIN_GETPROP:
          processGetProp(n);
          return;

        case CALL:
          processCall(n);
          return;

        case NAME:
          // handle ES5-style classes
          if (NodeUtil.isNameDeclaration(parent) || parent.isFunction()) {
            graphNodeFactory.createNode(getColor(n));
          }
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
      Color type = getColor(getProp.getFirstChild());
      maybeMarkCandidate(getProp, type);
      if (NodeUtil.isLhsOfAssign(getProp) || NodeUtil.isStatement(getProp.getParent())) {
        graphNodeFactory.createNode(type);
      }
    }

    private void processCall(Node call) {
      Node target = call.getFirstChild();
      if (!target.isQualifiedName()) {
        return;
      }

      if (compiler.getCodingConvention().isPropertyRenameFunction(target)) {
        Node propName = call.getSecondChild();
        if (propName == null || !propName.isStringLit()) {
          return;
        }

        // Skip ambiguation for properties in renaming calls
        // NOTE (lharker@) - I'm not sure if this behavior is necessary, or if we could safely
        // ambiguate the property as long as we also updated the property renaming call
        Property p = getProperty(propName.getString());
        p.skipAmbiguating = true;
      } else if (NodeUtil.isObjectDefinePropertiesDefinition(call)) {
        Node typeObj = call.getSecondChild();
        Color type = getColor(typeObj);
        Node objectLiteral = typeObj.getNext();

        if (!objectLiteral.isObjectLit()) {
          return;
        }

        for (Node key = objectLiteral.getFirstChild(); key != null; key = key.getNext()) {
          processObjectProperty(objectLiteral, key, type);
        }
      }
    }

    private void processObjectProperty(Node objectLit, Node key, Color type) {
      checkArgument(objectLit.isObjectLit() || objectLit.isObjectPattern(), objectLit);
      switch (key.getToken()) {
        case COMPUTED_PROP:
          if (key.getFirstChild().isStringLit()) {
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
          if (key.isQuotedStringKey()) {
            // If this quoted prop name is statically determinable, ensure we don't rename some
            // other property in a way that could conflict with it
            quotedNames.add(key.getString());
          } else {
            maybeMarkCandidate(key, type);
          }
          break;

        case OBJECT_REST:
        case OBJECT_SPREAD:
          break; // Nothing to do.

        default:
          throw new IllegalStateException(
              "Unexpected child of " + objectLit.getToken() + ": " + key.toStringTree());
      }
    }

    private void processObjectLitOrPattern(Node objectLit) {
      // Object.defineProperties literals are handled at the CALL node, as we determine the type
      // differently than for regular object literals.
      if (objectLit.getParent().isCall()
          && NodeUtil.isObjectDefinePropertiesDefinition(objectLit.getParent())) {
        return;
      }

      // The children of an OBJECTLIT node are keys, where the values
      // are the children of the keys.
      Color type = getColor(objectLit);
      for (Node key = objectLit.getFirstChild(); key != null; key = key.getNext()) {
        processObjectProperty(objectLit, key, type);
      }
    }

    private void processGetElem(Node n) {
      // If this is a quoted property access (e.g. x['myprop']), we need to
      // ensure that we never rename some other property in a way that
      // could conflict with this quoted name.
      Node child = n.getLastChild();
      if (child.isStringLit()) {
        quotedNames.add(child.getString());
      }
    }

    private void processClass(Node classNode) {
      Color classConstructorType = getColor(classNode);
      graphNodeFactory.createNode(classConstructorType);
      // In theory all CLASS colors should be a function with a known prototype, but in
      // practice typecasts mean that this is not always the case.

      ImmutableSet<Color> possiblePrototypes = classConstructorType.getPrototypes();
      Color classPrototype =
          possiblePrototypes.isEmpty()
              ? StandardColors.UNKNOWN
              : Color.createUnion(possiblePrototypes);
      for (Node member = NodeUtil.getClassMembers(classNode).getFirstChild();
          member != null;
          member = member.getNext()) {
        if (member.isQuotedStringKey()) {
          // ignore get 'foo'() {} and prevent property name collisions
          // Note that only getters/setters are represented as quoted strings, not 'foo'() {}
          // see https://github.com/google/closure-compiler/issues/3071
          quotedNames.add(member.getString());
          continue;
        } else if (member.isComputedProp() || member.isComputedFieldDef()) {
          // ignore ['foo']() {}
          // for simple cases, we also prevent renaming collisions
          if (member.getFirstChild().isStringLit()) {
            quotedNames.add(member.getFirstChild().getString());
          }
          continue;
        } else if (NodeUtil.isEs6ConstructorMemberFunctionDef(member)) {
          // don't rename `class C { constructor() {} }` !
          // This only applies for ES6 classes, not generic properties called 'constructor', which
          // is why it's handled in this method specifically.
          continue;
        }

        Color memberOwnerColor;
        if (member.isStaticMember()) {
          memberOwnerColor = classConstructorType;
        } else if (member.isMemberFieldDef()) {
          ImmutableSet<Color> possibleInstances = classConstructorType.getInstanceColors();
          memberOwnerColor =
              possibleInstances.isEmpty()
                  ? StandardColors.UNKNOWN
                  : Color.createUnion(possibleInstances);
        } else {
          checkState(member.isMemberFunctionDef() || member.isGetterDef() || member.isSetterDef());
          memberOwnerColor = classPrototype;
        }
        // member could be a MEMBER_FUNCTION_DEF, MEMBER_FIELD_DEF, GETTER_DEF, or SETTER_DEF
        maybeMarkCandidate(member, memberOwnerColor);
      }
    }

    /**
     * If a property node is eligible for renaming, stashes a reference to it and increments the
     * property name's access count.
     *
     * @param n The STRING node for a property
     */
    private void maybeMarkCandidate(Node n, Color type) {
      String name = n.getString();
      if (!externedNames.contains(name)) {
        stringNodesToRename.add(n);
        recordProperty(name, type);
      }
    }

    private Property recordProperty(String name, Color color) {
      Property prop = getProperty(name);
      prop.addRelatedColor(color);
      return prop;
    }
  }

  private Property getProperty(String name) {
    return propertyMap.computeIfAbsent(name, Property::new);
  }

  /** This method gets the Color from the Node argument or UNKNOWN if not present. */
  private Color getColor(Node n) {
    Color type = n.getColor();
    if (type == null) {
      // TODO(bradfordcsmith): This branch indicates a compiler bug. It should throw an exception.
      return StandardColors.UNKNOWN;
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
    // All colors upon which this property was directly accessed. For "a.b" this includes "a"'s type
    @Nullable IdentityHashMap<ColorGraphNode, Integer> relatedColorsSeeds = null;
    // includes relatedTypesSeeds + all subtypes of those seed colors. For example if this property
    // was accessed off of Iterable, then this bitset will include Array as well.
    final BitSet relatedColors = new BitSet();

    Property(String name) {
      this.oldName = name;
    }

    /** Marks this color as related to this property */
    void addRelatedColor(Color color) {
      if (skipAmbiguating) {
        return;
      }

      ++numOccurrences;

      if (color.isInvalidating() || color.getPropertiesKeepOriginalName()) {
        skipAmbiguating = true;
        return;
      }

      if (relatedColorsSeeds == null) {
        this.relatedColorsSeeds = new IdentityHashMap<>();
      }

      ColorGraphNode newColorGraphNode = graphNodeFactory.createNode(color);
      relatedColorsSeeds.put(newColorGraphNode, 0);
    }
  }
}
