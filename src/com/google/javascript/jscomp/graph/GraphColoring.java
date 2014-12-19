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

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Annotates the graph with a color in a way that no connected node will have
 * the same color. Nodes of the same color can then be partitioned together and
 * be represented by a super node. This class will merely annotate the nodes
 * with a color using {@link GraphNode#setAnnotation(Annotation)} and provide
 * a node to super node mapping with {@link #getPartitionSuperNode(Object)}. The
 * given graph itself will not be modified.
 *
 * <p>This algorithm is <b>NOT</b> deterministic by default. Passes that
 * requires deterministic output should provide a {@code Comparator} in the
 * constructor as a tie-breaker. This tie-break will be used when deciding
 * which node should be colored first when multiple nodes have the same degree.
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 *
 */
public abstract class GraphColoring<N, E> {
  // Maps a color (represented by an integer) to a variable. If, for example,
  // the color 5 is mapped to "foo". Then any other variables colored with the
  // color 5 will now use the name "foo".
  protected N[] colorToNodeMap;
  protected final AdjacencyGraph<N, E> graph;

  public GraphColoring(AdjacencyGraph<N, E> graph) {
    this.graph = graph;
  }

  /**
   * Annotates the graph with {@link Color} objects using
   * {@link GraphNode#setAnnotation(Annotation)}.
   *
   * @return The number of unique colors need.
   */
  public abstract int color();

  /**
   * Using the coloring as partitions, finds the node that represents that
   * partition as the super node. The first to retrieve its partition will
   * become the super node.
   */
  public N getPartitionSuperNode(N node) {
    Preconditions.checkNotNull(colorToNodeMap,
        "No coloring founded. color() should be called first.");
    Color color = graph.getNode(node).getAnnotation();
    N headNode = colorToNodeMap[color.value];
    if (headNode == null) {
      colorToNodeMap[color.value] = node;
      return node;
    } else {
      return headNode;
    }
  }

  public AdjacencyGraph<N, E> getGraph() {
    return graph;
  }

  /** The color of a node */
  public static class Color implements Annotation {
    int value = 0;

    Color(int value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Color)) {
        return false;
      } else {
        return value == ((Color) other).value;
      }
    }

    @Override
    public int hashCode() {
      return value;
    }
  }

  /**
   * Greedily assign nodes with high degree unique colors.
   */
  public static class GreedyGraphColoring<N, E> extends GraphColoring<N, E> {

    private final Comparator<N> tieBreaker;
    public GreedyGraphColoring(AdjacencyGraph<N, E> graph) {
      this(graph, null);
    }

    /**
     * @param tieBreaker In case of a tie between two nodes of the same degree,
     *     this comparator will determine which node should be colored first.
     */
    public GreedyGraphColoring(
        AdjacencyGraph<N, E> graph, Comparator<N> tieBreaker) {
      super(graph);
      this.tieBreaker = tieBreaker;
    }

    @Override
    public int color() {
      List<GraphNode<N, E>> worklist = new ArrayList<>(graph.getNodes());

      // Sort nodes by degree.
      Collections.sort(worklist, new Comparator<GraphNode<N, E>>() {
        @Override
        public int compare(GraphNode<N, E> o1, GraphNode<N, E> o2) {
          int result = graph.getWeight(o2.getValue())
              - graph.getWeight(o1.getValue());
          return result == 0 && tieBreaker != null ?
              tieBreaker.compare(o1.getValue(), o2.getValue()) : result;
        }
      });

      // Idea: From the highest to lowest degree, assign any uncolored node with
      // a unique color if none of its neighbors has been assigned that color.
      int count = 0;
      do {
        Color color = new Color(count);
        SubGraph<N, E> subgraph = graph.newSubGraph();
        for (Iterator<GraphNode<N, E>> i = worklist.iterator(); i.hasNext();) {
          GraphNode<N, E> node = i.next();
          if (subgraph.isIndependentOf(node.getValue())) {
            subgraph.addNode(node.getValue());
            node.setAnnotation(color);
            i.remove();
          }
        }
        count++;
      } while (!worklist.isEmpty());
      @SuppressWarnings("unchecked")
      N[] map = (N[]) new Object[count];
      colorToNodeMap = map;
      return count;
    }
  }
}
