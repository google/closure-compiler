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

package com.google.javascript.jscomp.graph;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Implements a lowest common ancestor search algorithm.
 *
 * <p>The LCA of a set of nodes is the node that is an ancestor to all of them but is not an
 * ancestor of any other common ancestor. In a non-tree, There may be multiple LCAs for a given set
 * of search nodes.
 *
 * <p>In a cyclic graph, the LCAs may not be well defined. Within a cycle, all elements are both
 * above and below one another, so there is no uniquely lowest element. If the set of common
 * ancestors is rooted on a cycle, this implementation returns one or more elements of that cycle.
 * Those elements are chosen arbitrarily but deterministically (as long as the underlying graph has
 * deterministic iteration).
 */
public class LowestCommonAncestorFinder<N, E> {

  /**
   * An abstraction for {@code LowestCommonAncestorFinder::new}.
   *
   * <p>This interface allows injection in tests.
   */
  @FunctionalInterface
  public interface Factory<N, E> {
    LowestCommonAncestorFinder<N, E> create(DiGraph<N, E> graph);
  }

  /** A "color" for a node, encoded as a combination of other colors using a one-hot scheme. */
  @Immutable
  private static final class Color {

    /**
     * A set of commonly used colors to minimize allocations.
     *
     * <p>An array is used to prevent bounds checking overhead.
     */
    private static final Color[] COMMON_COLOR_CACHE = new Color[2 << 5];

    static {
      Arrays.setAll(COMMON_COLOR_CACHE, Color::new);
    }

    /**
     * A color that when mixed with any other color {@code x} returns {@code x}.
     *
     * <p>Mixing this color is idempotent.
     */
    static final Color BLANK = checkNotNull(COMMON_COLOR_CACHE[0]);

    /**
     * A color for common ancestors that indicates that there are lower common ancestors.
     *
     * <p>Because this color sets its MSB high, it can never equal any other color. Also notice that
     * mixing this {@link Color} with any other produces this {@link Color} again; mixing it is
     * nullipotent.
     */
    static final Color NOT_LOWEST = new Color(-1);

    static Color create(int bitset) {
      if (bitset < 0) {
        checkArgument(bitset == -1);
        return NOT_LOWEST;
      } else if (bitset < COMMON_COLOR_CACHE.length) {
        return COMMON_COLOR_CACHE[bitset];
      }
      return new Color(bitset);
    }

    final int bitset;

    private Color(int bitset) {
      this.bitset = bitset;
    }

    Color mix(Color other) {
      if (this.bitset == other.bitset) {
        return this;
      }
      return create(this.bitset | other.bitset);
    }

    public boolean contains(Color other) {
      return (this.bitset & other.bitset) == other.bitset;
    }

    @Override
    @SuppressWarnings({"EqualsUnsafeCast"})
    public boolean equals(Object other) {
      return this.bitset == ((Color) other).bitset;
    }

    @Override
    public int hashCode() {
      return this.bitset;
    }
  }

  private final DiGraph<N, E> graph;

  private final LinkedHashMap<DiGraphNode<N, E>, Color> searchColoring = new LinkedHashMap<>();
  private final ArrayDeque<DiGraphNode<N, E>> searchQueue = new ArrayDeque<>();

  public LowestCommonAncestorFinder(DiGraph<N, E> graph) {
    this.graph = graph;
  }

  /**
   * Execute a search on all the elements of {@code roots}.
   *
   * <p>This is a general-purpose, bare-bones implementation. There are lots of special case
   * optimizations that could be applied.
   */
  public ImmutableSet<N> findAll(Set<N> roots) {
    // We reserved the MSB of each Color for bookkeeping.
    checkArgument(roots.size() <= Integer.SIZE - 1, "Too many roots.");
    checkState(this.searchColoring.isEmpty());

    // In two's-complement, (2^n - 1) sets the lowest n bits high.
    Color allColor = Color.create((1 << roots.size()) - 1);

    /*
     * Paint up from each root using the color associated with that root.
     *
     * <p>When done, the set of common ancestors is the set of nodes painted `allColor`.
     */
    int bitForRoot = 1;
    for (N root : roots) {
      DiGraphNode<N, E> rootNode = this.graph.getNode(root);
      checkNotNull(rootNode, "Root not present in graph: %s", root);

      Color color = Color.create(bitForRoot);

      this.searchColoring.merge(rootNode, color, Color::mix); // Preserve any existing colors.
      this.paintAncestors(rootNode, color);
      bitForRoot <<= 1;
    }

    /*
     * For every common ancestor, paint all of its ancestors with a color indicating it is not the
     * lowest.
     */
    this.searchColoring.forEach(
        (node, color) -> {
          if (color.equals(allColor)) {
            this.paintAncestors(node, Color.NOT_LOWEST);
          }
        });

    ImmutableSet.Builder<N> results = ImmutableSet.builder();
    this.searchColoring.forEach(
        (node, color) -> {
          if (color.equals(allColor)) {
            results.add(node.getValue());
          }
        });

    this.searchColoring.clear();
    this.searchQueue.clear();
    return results.build();
  }

  /**
   * Paint all nodes above {@code root} with {@code color}.
   *
   * <p>{@code root} itself will not have its color changed. {@code color} will be mixed with all
   * existing colors on ancestor nodes.
   */
  private void paintAncestors(DiGraphNode<N, E> root, Color color) {
    checkState(this.searchQueue.isEmpty());

    this.searchQueue.addLast(root);

    while (!this.searchQueue.isEmpty()) {
      DiGraphNode<N, E> curr = this.searchQueue.removeFirst();
      List<? extends DiGraphEdge<N, E>> parentEdges = curr.getInEdges();

      for (DiGraphEdge<N, E> parentEdge : parentEdges) {
        DiGraphNode<N, E> parent = parentEdge.getSource();
        if (parent.equals(root)) {
          continue; // Don't paint `root`.
        }

        Color oldColor = this.searchColoring.getOrDefault(parent, Color.BLANK);
        if (!oldColor.contains(color)) {
          // Only explore in directions that have not yet been painted.
          this.searchQueue.addLast(parent);
          this.searchColoring.put(parent, oldColor.mix(color));
        }
      }
    }
  }
}
