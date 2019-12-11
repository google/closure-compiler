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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Implements a lowest common ancestor search algorithm.
 *
 * <p>The LCA of a set of nodes is the node that is an ancestor to all of them but has the smallest
 * value of {@code heightFn}. In a non-tree, There may be multiple LCAs for a given set of search
 * nodes. In a cyclic graph, the LCAs may not be well defined.
 */
public class LowestCommonAncestorFinder<N, E> {

  /**
   * A function that maps each node in a {@code DiGraph} into an {@code int} defining a topological
   * sort of all nodes.
   *
   * <p>Specifically, {@code forall nodes (x, y) in G, (x above y) -> heightFn(x) > heightFn(y) &&
   * heightFn(x) == heightFn(y) -> (x incomparable y)}.
   *
   * <p>The result of this function must be consitent for each node during each search.
   */
  @FunctionalInterface
  public interface HeightFunction<N> {
    int measure(N node);
  }

  /** A "color" for a node, encoded as a combination of other colors using a one-hot scheme. */
  @Immutable
  private static final class Color {
    /**
     * A color for common ansecstors that indicates that there are lower common ansectors.
     *
     * <p>Because this color sets its MSB high, it can never equal any other color. Also notice that
     * mixing this {@link Color} with any other produces this {@link Color} again; mixing it is
     * nullipotent.
     */
    static final Color NOT_LOWEST = new Color(-1);

    /**
     * A set of commonly used colors to minimize allocations.
     *
     * <p>An array is used to prevent bounds checking overhead. The 0th element is unused.
     */
    private static final Color[] COMMON_COLOR_CACHE = new Color[2 << 5];

    static {
      Arrays.setAll(COMMON_COLOR_CACHE, Color::new);
    }

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

  /** Sorts graph nodes in order of height from lowest to highest. */
  private final Comparator<DiGraphNode<N, E>> prioritization;

  private final DiGraph<N, E> graph;

  public LowestCommonAncestorFinder(DiGraph<N, E> graph, HeightFunction<N> heightFn) {
    this.graph = graph;
    this.prioritization = Comparator.comparingInt((d) -> heightFn.measure(d.getValue()));
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

    PriorityQueue<DiGraphNode<N, E>> searchQueue = new PriorityQueue<>(this.prioritization);
    LinkedHashMap<DiGraphNode<N, E>, Color> searchColoring = new LinkedHashMap<>();
    Color allColor = Color.create((1 << roots.size()) - 1);

    // Assign the starting colors.
    int bitForRoot = 1;
    for (N root : roots) {
      DiGraphNode<N, E> rootNode = this.graph.getNode(root);
      checkNotNull(rootNode, "Root not present in graph: %s", root);

      searchQueue.add(rootNode);
      searchColoring.put(rootNode, Color.create(bitForRoot));
      bitForRoot <<= 1;
    }

    ImmutableSet.Builder<N> results = ImmutableSet.builder();
    while (!searchQueue.isEmpty()) {
      DiGraphNode<N, E> curr = searchQueue.poll();
      Color currColor = searchColoring.get(curr);

      if (currColor.equals(allColor)) {
        results.add(curr.getValue());
        /**
         * Mark this result with a special color.
         *
         * <p>None of the ancestors of this node can be LCAs. However, there may be alternative
         * paths to them. Therefore, we need to keep exploring, marking them all as having an LCA
         * descendant.
         */
        currColor = Color.NOT_LOWEST;
        searchColoring.put(curr, Color.NOT_LOWEST);
      }

      List<? extends DiGraphEdge<N, E>> parentEdges = curr.getInEdges();
      for (DiGraphEdge<N, E> parentEdge : parentEdges) {
        DiGraphNode<N, E> parent = parentEdge.getSource();
        @Nullable Color oldColor = searchColoring.get(parent);

        if (oldColor == null) {
          searchQueue.add(parent);
          searchColoring.put(parent, currColor);
        } else {
          searchColoring.put(parent, oldColor.mix(currColor));
        }
      }
    }

    return results.build();
  }
}
