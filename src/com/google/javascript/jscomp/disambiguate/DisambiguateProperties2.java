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
import static com.google.common.base.Preconditions.checkState;
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
import com.google.common.collect.ImmutableSortedSet;
import com.google.gson.Gson;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.InvalidatingTypes;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.TypeMismatch;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.jscomp.disambiguate.FlatType.Arity;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.LowestCommonAncestorFinder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.LinkedHashMap;
import java.util.Map;

/** Assembles the various parts of the diambiguator to execute them as a compiler pass. */
public final class DisambiguateProperties2 implements CompilerPass {

  private static final Gson GSON = new Gson();

  private final AbstractCompiler compiler;
  private final ImmutableMap<String, CheckLevel> invalidationReportingLevelByProp;
  private final ImmutableSet<TypeMismatch> mismatches;
  private final JSTypeRegistry registry;
  private final InvalidatingTypes invalidations;

  public DisambiguateProperties2(
      AbstractCompiler compiler,
      ImmutableMap<String, CheckLevel> invalidationReportingLevelByProp) {
    this.compiler = compiler;
    this.invalidationReportingLevelByProp = invalidationReportingLevelByProp;
    this.registry = this.compiler.getTypeRegistry();

    this.mismatches =
        ImmutableSet.<TypeMismatch>builder()
            .addAll(compiler.getTypeMismatches())
            .build();
    this.invalidations =
        new InvalidatingTypes.Builder(this.registry).addAllTypeMismatches(this.mismatches).build();
  }

  @Override
  public void process(Node externs, Node root) {
    checkArgument(externs.getParent() == root.getParent());

    TypeFlattener flattener = new TypeFlattener(this.registry, this.invalidations::isInvalidating);
    FindPropertyReferences findRefs =
        new FindPropertyReferences(
            flattener,
            /* errorCb= */ this.compiler::report,
            this.compiler.getCodingConvention()::isPropertyRenameFunction);
    TypeGraphBuilder graphBuilder =
        new TypeGraphBuilder(flattener, LowestCommonAncestorFinder::new);
    ClusterPropagator propagator = new ClusterPropagator();
    UseSiteRenamer renamer =
        new UseSiteRenamer(
            this.invalidationReportingLevelByProp,
            /* errorCb= */ this.compiler::report,
            /* mutationCb= */ this.compiler::reportChangeToEnclosingScope);

    NodeTraversal.traverse(this.compiler, externs.getParent(), findRefs);
    LinkedHashMap<String, PropertyClustering> propIndex = findRefs.getPropertyIndex();

    invalidateWellKnownProperties(propIndex);
    this.logForDiagnostics(
        "prop_refs",
        () ->
            propIndex.values().stream()
                .map(PropertyReferenceIndexJson::new)
                .collect(toImmutableSortedMap(naturalOrder(), (x) -> x.name, (x) -> x)));

    graphBuilder.addAll(flattener.getAllKnownTypes());
    DiGraph<FlatType, Object> graph = graphBuilder.build();

    // Model legacy behavior from the old (pre-December 2020) disambiguator.
    for (FlatType flatType : flattener.getAllKnownTypes()) {
      if (flatType.getArity().equals(Arity.SINGLE)) {
        // Only need this step for SINGLE FlatTypes because union types don't have "own" properties
        // and we will add properties of each alternate elsewhere in this loop.
        registerOwnDeclaredProperties(flatType, propIndex);
      }
    }

    this.logForDiagnostics(
        "graph",
        () ->
            graph.getNodes().stream()
                .map(TypeNodeJson::new)
                .sorted(comparingInt((x) -> x.id))
                .collect(toImmutableList()));

    // Ensure this step happens after logging PropertyReferenceIndexJson. Invalidating a property
    // destroys its list of use sites, which we need to log.
    invalidateBasedOnType(flattener);

    FixedPointGraphTraversal.newTraversal(propagator).computeFixedPoint(graph);
    propIndex.values().forEach(renamer::renameUses);

    this.logForDiagnostics("renaming_index", () -> buildRenamingIndex(propIndex, renamer));

    this.logForDiagnostics(
        "mismatches",
        () ->
            this.mismatches.stream()
                .map((m) -> new TypeMismatchJson(m, flattener))
                .collect(toImmutableSortedSet(naturalOrder())));
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
    /**
     * Expand this list as needed; it wasn't created exhaustively.
     *
     * <p>Good candidates are: props accessed by builtin functions, props accessed by syntax sugar,
     * props used in strange ways by the language spec, etc.
     */
    ImmutableList<String> names = ImmutableList.of("prototype", "constructor", "then");
    for (String name : names) {
      propIndex
          .computeIfAbsent(name, PropertyClustering::new)
          .invalidate(Invalidation.wellKnownProperty());
    }
  }

  private static void invalidateBasedOnType(TypeFlattener flattener) {
    for (FlatType type : flattener.getAllKnownTypes()) {
      if (type.isInvalidating()) {
        for (PropertyClustering prop : type.getAssociatedProps()) {
          prop.invalidate(Invalidation.invalidatingType(type.getId()));
        }
      } else {
        invalidateNonDeclaredPropertyAccesses(type);
      }
    }
  }

  private static void invalidateNonDeclaredPropertyAccesses(FlatType type) {
    checkArgument(
        !type.isInvalidating(),
        "Not applicable to invalidating types. All their properties are invalidated");
    // Invalidate all property accesses that cause "missing property" warnings. This behavior is not
    // inherently necessary for correctness. It only exists because the older version of the
    // disambiguator invalidated these properties and some projects began to rely on this.
    for (PropertyClustering prop : type.getAssociatedProps()) {
      if (prop.isInvalidated()) {
        continue; // Skip unnecessary `hasProperty` lookups which can be expensive.
      }

      boolean mayHaveProperty =
          type.hasArity(Arity.SINGLE)
              ? type.getTypeSingle().hasProperty(prop.getName())
              : type.getTypeUnion().stream()
                  .anyMatch(flatType -> flatType.getTypeSingle().hasProperty(prop.getName()));

      if (!mayHaveProperty) {
        prop.invalidate(Invalidation.undeclaredAccess(type.getId()));
      }
    }
  }

  private void registerOwnDeclaredProperties(
      FlatType flatType, Map<String, PropertyClustering> propIndex) {
    checkArgument(flatType.hasArity(Arity.SINGLE));
    JSType single = flatType.getTypeSingle();
    checkState(!single.isBoxableScalar(), single);
    ObjectType obj = single.toMaybeObjectType();
    if (obj == null) {
      return;
    }

    // For each type, get the list of its "own properties" and add them to its clustering. This
    // is only to mimic the behavior of the old disambiguator and could be removed if we were
    // confident we could update all existing code to be compatible.
    for (String propName : obj.getOwnPropertyNames()) {
      PropertyClustering prop = propIndex.get(propName);
      if (prop == null) {
        // Ignore declared properties without any visible references to rename.
        continue;
      } else if (prop.isInvalidated()) {
        continue;
      }
      prop.getClusters().add(flatType);
      flatType.getAssociatedProps().add(prop);
    }
  }

  private void logForDiagnostics(String name, Supplier<Object> data) {
    try (LogFile log = this.compiler.createOrReopenLog(this.getClass(), name + ".log")) {
      log.log(() -> GSON.toJson(data.get()));
    }
  }

  private static final class PropertyReferenceIndexJson {
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
  }

  private static final class PropertyReferenceJson implements Comparable<PropertyReferenceJson> {
    final String location;
    final int receiver;

    PropertyReferenceJson(Node location, FlatType receiver) {
      this.location =
          location.getSourceFileName() + ":" + location.getLineno() + ":" + location.getCharno();
      this.receiver = receiver.getId();
    }

    @Override
    public int compareTo(PropertyReferenceJson x) {
      return ComparisonChain.start()
          .compare(this.receiver, x.receiver)
          .compare(this.location, x.location)
          .result();
    }
  }

  private static final class TypeNodeJson {
    final int id;
    final boolean invalidating;
    final String name;
    final ImmutableSortedSet<TypeEdgeJson> edges;
    final ImmutableSortedSet<String> props;

    TypeNodeJson(DiGraphNode<FlatType, Object> n) {
      FlatType t = n.getValue();

      this.id = t.getId();
      this.name =
          (t.hasArity(FlatType.Arity.SINGLE) ? t.getTypeSingle() : t.getTypeUnion()).toString();
      this.invalidating = t.isInvalidating();
      this.edges =
          n.getOutEdges().stream()
              .map(TypeEdgeJson::new)
              .collect(toImmutableSortedSet(naturalOrder()));
      this.props =
          t.getAssociatedProps().stream()
              .map(PropertyClustering::getName)
              .collect(toImmutableSortedSet(naturalOrder()));
    }
  }

  private static final class TypeEdgeJson implements Comparable<TypeEdgeJson> {
    final int dest;
    final Object value;

    TypeEdgeJson(DiGraphEdge<FlatType, Object> e) {
      this.dest = e.getDestination().getValue().getId();
      this.value = e.getValue();
    }

    @Override
    public int compareTo(TypeEdgeJson x) {
      checkArgument(this.dest != x.dest);
      return this.dest - x.dest;
    }
  }

  private static final class TypeMismatchJson implements Comparable<TypeMismatchJson> {
    final int found;
    final int required;
    final String location;

    TypeMismatchJson(TypeMismatch x, TypeFlattener flattener) {
      this.found = flattener.flatten(x.getFound()).getId();
      this.required = flattener.flatten(x.getRequired()).getId();
      this.location = x.getLocation().getLocation();
    }

    @Override
    public int compareTo(TypeMismatchJson x) {
      return ComparisonChain.start()
          .compare(this.found, x.found)
          .compare(this.required, x.required)
          .compare(this.location, x.location)
          .result();
    }
  }
}
