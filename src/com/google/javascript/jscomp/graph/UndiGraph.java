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
import java.util.Iterator;
import java.util.List;

/**
 * A generic undirected graph.
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 */
public abstract class UndiGraph<N, E> extends Graph<N, E> {

  /**
   * Gets an immutable collection of all the nodes in this graph.
   */
  abstract Collection<UndiGraphNode<N, E>> getUndirectedGraphNodes();

  abstract UndiGraphNode<N, E> createUndirectedGraphNode(N nodeValue);

  public abstract UndiGraphNode<N, E> getUndirectedGraphNode(N nodeValue);

  abstract List<UndiGraphEdge<N, E>> getUndirectedGraphEdges(N n1, N n2);

  /**
   * A generic undirected graph node.
   *
   * @param <N> Value type that the graph node stores.
   * @param <E> Value type that the graph edge stores.
   */
  public static interface UndiGraphNode<N, E> extends GraphNode<N, E> {
    public List<UndiGraphEdge<N, E>> getNeighborEdges();
    public Iterator<UndiGraphEdge<N, E>> getNeighborEdgesIterator();
  }

  /**
   * A generic undirected graph edge.
   *
   * @param <N> Value type that the graph node stores.
   * @param <E> Value type that the graph edge stores.
   */
  public static interface UndiGraphEdge<N, E> extends GraphEdge<N, E> {
  }
}
