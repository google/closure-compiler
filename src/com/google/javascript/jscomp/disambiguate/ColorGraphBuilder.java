/*
 * Copyright 2020 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph.LinkedDiGraphNode;
import com.google.javascript.jscomp.graph.LowestCommonAncestorFinder;
import java.util.Collection;
import org.jspecify.nullness.Nullable;

/** Builds a graph of the {@link Color}s on the AST from a specified set of seed colors. */
final class ColorGraphBuilder {

  /**
   * The relationship that caused an edge to be created.
   *
   * <p>This information is only retained for diagnostics, not correctness.
   */
  enum EdgeReason {
    ALGEBRAIC,
    CAN_HOLD
  }

  private final ColorRegistry registry;
  private final ColorGraphNodeFactory nodeFactory;
  private final LowestCommonAncestorFinder<ColorGraphNode, Object> lcaFinder;
  private final LinkedDiGraphNode<ColorGraphNode, Object> topNode;

  /**
   * The graph of colors as defined by `holdsInstanceOf`.
   *
   * <p>We use `holdsInstanceOf` rather than `isSupertypeOf` because we only actually care about
   * edges that were "used" in the program. If instances never flow over an edge at runtime, then
   * properties also don't need to be tracked across that edge either. Of course, since we don't
   * track the types of all assignments in a program, many of the edges are `isSupertypeOf` edges
   * that we include to be conservative.
   *
   * <p>This graph, when fully constructed, is still only an approximation. This is the due to both
   * memory and time constraints. The following quirks are expected:
   *
   * <ul>
   *   <li>Some colors, such as primitives, will not have nodes.
   *   <li>Transitive edges (shortcut edges for which there exist alternate paths) are kept minimal.
   * </ul>
   *
   * <p>In practice, this could be declared as taking an {@link EdgeReason} instead of an Object,
   * but Object is used to indicate that EdgeReasons are only meant for debugging and not any actual
   * logic in (dis)ambiguation.
   */
  private @Nullable LinkedDirectedGraph<ColorGraphNode, Object> colorHoldsInstanceGraph =
      LinkedDirectedGraph.createWithoutAnnotations();

  ColorGraphBuilder(
      ColorGraphNodeFactory nodeFactory,
      LowestCommonAncestorFinder.Factory<ColorGraphNode, Object> lcaFinderFactory,
      ColorRegistry registry) {
    this.registry = registry;
    this.nodeFactory = nodeFactory;
    this.lcaFinder = lcaFinderFactory.create(this.colorHoldsInstanceGraph);

    this.topNode =
        this.colorHoldsInstanceGraph.createNode(
            this.nodeFactory.createNode(StandardColors.UNKNOWN));
  }

  public void add(ColorGraphNode flat) {
    this.addInternal(flat);
  }

  public void addAll(Collection<ColorGraphNode> flats) {
    flats.forEach(this::add);
  }

  public LinkedDirectedGraph<ColorGraphNode, Object> build() {
    this.colorHoldsInstanceGraph.getNodes().forEach(this::connectUnionWithAncestors);

    LinkedDirectedGraph<ColorGraphNode, Object> temp = this.colorHoldsInstanceGraph;
    this.colorHoldsInstanceGraph = null;
    return temp;
  }

  /**
   * During initial lattice construction unions were only given outbound edges. Here we add any
   * necessary inbound ones.
   *
   * <p>We defer this operation because adding union-to-union and common-supertype-to-union edges,
   * is a hard problem. Solving it after all other colors are in place makes it easier.
   */
  private void connectUnionWithAncestors(LinkedDiGraphNode<ColorGraphNode, Object> unionNode) {
    ColorGraphNode flatUnion = unionNode.getValue();
    if (!flatUnion.getColor().isUnion()) {
      return;
    }

    /**
     * Connect the LCAs to the union.
     *
     * <p>The union itself will be found in most cases, but since we don't add self-edges, that
     * won't matter.
     *
     * <p>Some of these edges may pollute the "lattice-ness" of the graph, but all the invariants we
     * actually care about will be maintained. If disambiguation is too slow and stricter invariants
     * would help, we could be more careful.
     */
    checkState(!unionNode.getOutEdges().isEmpty());
    ImmutableSet<ColorGraphNode> graphNodes =
        flatUnion.getColor().getUnionElements().stream()
            .map(this.nodeFactory::createNode)
            .collect(toImmutableSet());
    for (ColorGraphNode lca : this.lcaFinder.findAll(graphNodes)) {
      this.connectSourceToDest(
          checkNotNull(this.colorHoldsInstanceGraph.getNode(lca)), EdgeReason.ALGEBRAIC, unionNode);
    }
  }

  /** Insert {@code color} and all necessary related colors into the datastructures of this pass. */
  private LinkedDiGraphNode<ColorGraphNode, Object> addInternal(Color color) {
    return this.addInternal(this.nodeFactory.createNode(color));
  }

  /** Insert {@code node} and all necessary related colors into the datastructures of this pass. */
  private LinkedDiGraphNode<ColorGraphNode, Object> addInternal(ColorGraphNode node) {
    LinkedDiGraphNode<ColorGraphNode, Object> flatNode = this.colorHoldsInstanceGraph.getNode(node);
    if (flatNode != null) {
      return flatNode;
    }
    flatNode = this.colorHoldsInstanceGraph.createNode(node);

    if (node.getColor().isUnion()) {
      for (Color alt : node.getColor().getUnionElements()) {
        this.connectSourceToDest(flatNode, EdgeReason.ALGEBRAIC, this.addInternal(alt));
      }
      return flatNode;
    }

    Color color = node.getColor();
    ImmutableSet<Color> supertypes = this.registry.getDisambiguationSupertypes(color);
    if (supertypes.isEmpty()) {
      this.connectSourceToDest(topNode, EdgeReason.ALGEBRAIC, flatNode);
    } else {
      for (Color supertype : supertypes) {
        this.connectSourceToDest(this.addInternal(supertype), EdgeReason.CAN_HOLD, flatNode);
      }
    }

    /**
     * Add all instance and prototype colors when visiting a constructor. We won't necessarily see
     * all possible instance colors that exist at runtime during an AST traversal.
     *
     * <p>For example, a subclass constructor may never be explicitly initialized but instead passed
     * to some function expecting `function(new:Parent)`. See {@link
     * AmbiguatePropertiesTest#testImplementsAndExtends_respectsUndeclaredProperties()}
     */
    for (Color prototype : color.getPrototypes()) {
      this.addInternal(prototype);
    }
    for (Color instanceColor : color.getInstanceColors()) {
      this.addInternal(instanceColor);
    }
    return flatNode;
  }

  private void connectSourceToDest(
      LinkedDiGraphNode<ColorGraphNode, Object> source,
      EdgeReason reason,
      LinkedDiGraphNode<ColorGraphNode, Object> dest) {
    if (source.equals(dest)
        || this.colorHoldsInstanceGraph.isConnectedInDirection(source, (t) -> true, dest)) {
      return;
    }

    this.colorHoldsInstanceGraph.connect(source, reason, dest);
  }
}
