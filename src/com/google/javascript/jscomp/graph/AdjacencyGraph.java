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

import java.util.Collection;

/**
 * A minimal graph interface.  Provided is add nodes to the graph, adjacency
 * calculation between a SubGraph and a GraphNode, and adding node annotations.
 *
 * <p>For a more extensive interface, see {@link Graph}.
 *
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 * @see Graph
 */
public interface AdjacencyGraph<N, E> {
  /** Gets an immutable list of all nodes. */
  Collection<GraphNode<N, E>> getNodes();

  /**
   * Gets a node from the graph given a value. Values equality are compared
   * using <code>Object.equals</code>.
   *
   * @param value The node's value.
   * @return The corresponding node in the graph, null if there value has no
   *         corresponding node.
   */
  GraphNode<N, E> getNode(N value);

  /** Returns an empty SubGraph for this Graph. */
  SubGraph<N, E> newSubGraph();

  /** Makes each node's annotation null. */
  void clearNodeAnnotations();

  /**
   * Returns a weight for the given value to be used in ordering nodes, e.g.
   * in {@link GraphColoring}.
   */
  int getWeight(N value);
}
