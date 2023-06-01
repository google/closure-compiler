/*
 * Copyright 2019 The Closure Compiler Authors.
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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSortedMap.toImmutableSortedMap;
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.naturalOrder;

import com.google.common.base.Supplier;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.errorprone.annotations.Keep;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.GatherGetterAndSetterProperties;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.jscomp.disambiguate.ColorGraphNode.PropAssociation;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.LowestCommonAncestorFinder;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** Assembles the various parts of the diambiguator to execute them as a compiler pass. */
public final class DisambiguateProperties implements CompilerPass {

  public static final DiagnosticType PROPERTY_INVALIDATION =
      DiagnosticType.error(
          "JSC_DISAMBIGUATE2_PROPERTY_INVALIDATION",
          "Property ''{0}'' was required to be disambiguated but was invalidated."
              + "{1}");

  private static final Gson GSON = new Gson();

  private final AbstractCompiler compiler;
  private final ImmutableSet<String> propertiesThatMustDisambiguate;
  private final ColorRegistry registry;

  public DisambiguateProperties(
      AbstractCompiler compiler, ImmutableSet<String> propertiesThatMustDisambiguate) {
    this.compiler = compiler;
    this.propertiesThatMustDisambiguate = propertiesThatMustDisambiguate;
    this.registry = this.compiler.getColorRegistry();
  }

  @Override
  public void process(Node externs, Node root) {
    checkArgument(externs.getParent() == root.getParent());

    ColorGraphNodeFactory flattener = ColorGraphNodeFactory.createFactory(this.registry);
    ColorFindPropertyReferences findRefs =
        new ColorFindPropertyReferences(
            flattener, this.compiler.getCodingConvention()::isPropertyRenameFunction);
    ColorGraphBuilder graphBuilder =
        new ColorGraphBuilder(flattener, LowestCommonAncestorFinder::new, this.registry);
    ClusterPropagator propagator = new ClusterPropagator();
    UseSiteRenamer renamer =
        new UseSiteRenamer(
            /* mutationCb= */ this.compiler::reportChangeToEnclosingScope);

    NodeTraversal.traverse(this.compiler, externs.getParent(), findRefs);
    LinkedHashMap<String, PropertyClustering> propIndex = findRefs.getPropertyIndex();

    invalidateWellKnownProperties(propIndex);
    this.logForDiagnostics(
        "prop_refs",
        // use a StreamedJsonProducer instead of building up the entire json string at once to
        // prevent OOMs for very large projects.
        new LogFile.StreamedJsonProducer() {
          @Override
          public void writeJson(JsonWriter writer) throws IOException {
            ImmutableSortedSet<PropertyReferenceIndexJson> propRefsJson =
                propIndex.values().stream()
                    .map(PropertyReferenceIndexJson::new)
                    .collect(toImmutableSortedSet(naturalOrder()));

            writer.beginObject();
            for (PropertyReferenceIndexJson propRef : propRefsJson) {
              propRef.writeJson(writer);
            }
            writer.endObject();
          }
        });

    graphBuilder.addAll(flattener.getAllKnownTypes());
    DiGraph<ColorGraphNode, Object> graph = graphBuilder.build();

    // Model legacy behavior from the old (pre-January 2021) disambiguator.
    // TODO(b/177695515): delete this section.
    for (ColorGraphNode colorGraphNode : flattener.getAllKnownTypes()) {
      if (graph.getOutEdges(colorGraphNode).isEmpty()) {
        // Skipping leaf types improves code size, especially as "namespace" types are all leaf
        // types and will often have their declared properties collapsed into variables.
        continue;
      }
      if (colorGraphNode.getColor().isUnion()) {
        // Only need this step for SINGLE ColorGraphNodes because
        // and we will add properties of each alternate elsewhere in this loop.
        continue;
      }
      registerOwnDeclaredProperties(colorGraphNode, propIndex);
    }

    this.logForDiagnostics(
        "graph",
        () ->
            graph.getNodes().stream()
                .map(TypeNodeJson::new)
                .sorted(comparingInt((x) -> x.index))
                .collect(toImmutableList()));

    invalidateBasedOnType(flattener);

    FixedPointGraphTraversal.newTraversal(propagator).computeFixedPoint(graph);

    for (PropertyClustering prop : propIndex.values()) {
      renamer.renameUses(prop);
      if (prop.isInvalidated() && this.propertiesThatMustDisambiguate.contains(prop.getName())) {
        this.compiler.report(this.createInvalidationError(prop));
      }
    }

    this.logForDiagnostics("renaming_index", () -> buildRenamingIndex(propIndex, renamer));
    this.logForDiagnostics("mismatches", this.registry.getMismatchLocationsForDebugging()::inverse);

    GatherGetterAndSetterProperties.update(this.compiler, externs, root);
  }

  private JSError createInvalidationError(PropertyClustering prop) {
    String additionalContext = "";

    return JSError.make(
        null,
        -1,
        -1,
        DisambiguateProperties.PROPERTY_INVALIDATION,
        prop.getName(),
        additionalContext);
  }

  private static ImmutableMap<String, Object> buildRenamingIndex(
      LinkedHashMap<String, PropertyClustering> props, UseSiteRenamer renamer) {
    ImmutableSetMultimap<String, String> newNames = renamer.getRenamingIndex();
    return props.values().stream()
        .collect(
            toImmutableSortedMap(
                naturalOrder(),
                PropertyClustering::getName,
                prop ->
                    prop.isInvalidated()
                        ? prop.getLastInvalidation()
                        : ImmutableSortedSet.copyOf(newNames.get(prop.getName()))));
  }

  private static void invalidateWellKnownProperties(
      LinkedHashMap<String, PropertyClustering> propIndex) {
    /*
     * Expand this list as needed; it wasn't created exhaustively.
     *
     * <p>Good candidates are: props accessed by builtin functions, props accessed by syntax sugar,
     * props used in strange ways by the language spec, etc.
     *
     * <p>TODO(b/169899789): consider instead omitting these properties entirely from the serialized
     * colors format.
     */
    ImmutableList<String> names = ImmutableList.of("prototype", "constructor", "then");
    for (String name : names) {
      propIndex
          .computeIfAbsent(name, PropertyClustering::new)
          .invalidate(Invalidation.wellKnownProperty());
    }
  }

  private void invalidateBasedOnType(ColorGraphNodeFactory flattener) {
    for (ColorGraphNode type : flattener.getAllKnownTypes()) {
      if (type.getColor().isInvalidating()) {
        for (PropertyClustering prop : type.getAssociatedProps().keySet()) {
          prop.invalidate(Invalidation.invalidatingType(type.getIndex()));
        }
      } else {
        invalidateNonDeclaredPropertyAccesses(type);
      }
    }
  }

  /**
   * Invalidate all property accesses that cause "missing property" warnings.
   *
   * <p>This behavior is not inherently necessary for correctness. It only exists because the older
   * version of the disambiguator invalidated these properties and some projects began to rely on
   * this.
   *
   * <p>TODO(b/177695515): delete this method
   */
  private void invalidateNonDeclaredPropertyAccesses(ColorGraphNode colorGraphNode) {
    Color color = colorGraphNode.getColor();
    checkArgument(
        !color.isInvalidating(),
        "Not applicable to invalidating types. All their properties are invalidated");

    for (PropertyClustering prop : colorGraphNode.getAssociatedProps().keySet()) {
      if (prop.isInvalidated()) {
        continue; // Skip unnecessary `hasProperty` lookups which can be expensive.
      }

      if (!mayHaveProperty(color, prop.getName())) {
        prop.invalidate(Invalidation.undeclaredAccess(colorGraphNode.getIndex()));
      }
    }
  }

  /**
   * Returns true if the color or any of its ancestors has the given property
   *
   * <p>If this is a union, returns true if /any/ union alternate has the property.
   *
   * <p>TODO(b/177695515): delete this method
   */
  private boolean mayHaveProperty(Color color, String propertyName) {
    try {
      if (!this.mayHavePropertySeenSet.add(color)) {
        return false;
      }

      if (color.isUnion()) {
        return color.getUnionElements().stream()
            .anyMatch(element -> mayHaveProperty(element, propertyName));
      }

      if (color.getOwnProperties().contains(propertyName)) {
        return true;
      }
      return this.registry.getDisambiguationSupertypes(color).stream()
          .anyMatch(element -> mayHaveProperty(element, propertyName));

    } finally {
      this.mayHavePropertySeenSet.remove(color);
    }
  }

  private final LinkedHashSet<Color> mayHavePropertySeenSet = new LinkedHashSet<>();

  private void registerOwnDeclaredProperties(
      ColorGraphNode colorGraphNode, Map<String, PropertyClustering> propIndex) {
    checkArgument(!colorGraphNode.getColor().isUnion());

    // For each type, get the list of its "own properties" and add them to its clustering. This
    // is only to mimic the behavior of the old disambiguator and could be removed if we were
    // confident we could update all existing code to be compatible.
    for (String propName : colorGraphNode.getColor().getOwnProperties()) {
      PropertyClustering prop = propIndex.get(propName);
      if (prop == null) {
        // Ignore declared properties without any visible references to rename.
        continue;
      } else if (prop.isInvalidated()) {
        continue;
      }
      prop.getClusters().add(colorGraphNode);
      colorGraphNode.getAssociatedProps().put(prop, PropAssociation.TYPE_SYSTEM);
    }
  }

  private void logForDiagnostics(String name, Supplier<Object> data) {
    try (LogFile log = this.compiler.createOrReopenLog(this.getClass(), name + ".log")) {
      log.logJson(data);
    }
  }

  private void logForDiagnostics(String name, LogFile.StreamedJsonProducer data) {
    try (LogFile log = this.compiler.createOrReopenLog(this.getClass(), name + ".log")) {
      log.logJson(data);
    }
  }

  private static final class PropertyReferenceIndexJson
      implements Comparable<PropertyReferenceIndexJson> {
    final String name;
    final ImmutableSortedSet<PropertyReferenceJson> refs;

    PropertyReferenceIndexJson(PropertyClustering prop) {
      this.name = prop.getName();
      if (prop.isInvalidated()) {
        this.refs = ImmutableSortedSet.of();
      } else {
        this.refs =
            prop.getUseSites().entrySet().stream()
                .map((e) -> new PropertyReferenceJson(e.getKey(), e.getValue()))
                .collect(toImmutableSortedSet(naturalOrder()));
      }
    }

    @Override
    public int compareTo(PropertyReferenceIndexJson x) {
      return this.name.compareTo(x.name);
    }

    private void writeJson(JsonWriter writer) throws IOException {
      // creates an entry such as:
      //   foo: {name: 'foo', refs: [{location: 'bar.js:3:4', receiverIndex: 2}]}
      writer.name(this.name);

      writer.beginObject();
      writer.name("name").value(this.name);
      writer.name("refs").beginArray();
      for (PropertyReferenceJson ref : this.refs) {
        GSON.toJson(ref, PropertyReferenceJson.class, writer);
      }
      writer.endArray();
      writer.endObject();
    }
  }

  private static final class PropertyReferenceJson implements Comparable<PropertyReferenceJson> {
    final String location;
    final int receiverIndex;

    PropertyReferenceJson(Node location, ColorGraphNode receiver) {
      this.location = location.getLocation();
      this.receiverIndex = receiver.getIndex();
    }

    @Override
    public int compareTo(PropertyReferenceJson x) {
      return ComparisonChain.start()
          .compare(this.receiverIndex, x.receiverIndex)
          .compare(this.location, x.location)
          .result();
    }
  }

  private static final class TypeNodeJson {
    final int index;
    // These fields are used reflectively via GSON.
    @Keep final boolean invalidating;
    @Keep final String colorId;
    @Keep final ImmutableSortedSet<TypeEdgeJson> edges;
    @Keep final ImmutableSortedMap<String, PropAssociation> props;

    TypeNodeJson(DiGraphNode<ColorGraphNode, Object> n) {
      ColorGraphNode t = n.getValue();

      this.index = t.getIndex();
      this.colorId = t.getColor().getId().toString();
      this.invalidating = t.getColor().isInvalidating();
      this.edges =
          n.getOutEdges().stream()
              .map(TypeEdgeJson::new)
              .collect(toImmutableSortedSet(naturalOrder()));
      this.props =
          t.getAssociatedProps().entrySet().stream()
              .collect(
                  toImmutableSortedMap(
                      naturalOrder(), e -> e.getKey().getName(), Map.Entry::getValue));
    }
  }

  private static final class TypeEdgeJson implements Comparable<TypeEdgeJson> {
    final int dest;
    // This field is used reflectively via GSON.
    @Keep final Object value;

    TypeEdgeJson(DiGraphEdge<ColorGraphNode, Object> e) {
      this.dest = e.getDestination().getValue().getIndex();
      this.value = e.getValue();
    }

    @Override
    public int compareTo(TypeEdgeJson x) {
      checkArgument(this.dest != x.dest);
      return this.dest - x.dest;
    }
  }

}
