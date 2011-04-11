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

import java.util.List;

/**
 * A generic directed graph.
 *
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 */
public abstract class DiGraph<N, E> extends Graph<N, E> {

  /**
   * Gets an immutable iterable over all the nodes in the graph.
   */
  public abstract Iterable<DiGraphNode<N, E>> getDirectedGraphNodes();

  /**
   * Gets an immutable list of out edges of the given node.
   */
  public abstract List<DiGraphEdge<N, E>> getOutEdges(N nodeValue);

  /**
   * Gets an immutable list of in edges of the given node.
   */
  public abstract List<DiGraphEdge<N, E>> getInEdges(N nodeValue);

  public abstract List<DiGraphNode<N, E>> getDirectedPredNodes(
      DiGraphNode<N, E> n);

  public abstract List<DiGraphNode<N, E>> getDirectedSuccNodes(
      DiGraphNode<N, E> n);

  public abstract List<DiGraphNode<N, E>>
      getDirectedPredNodes(N nodeValue);

  public abstract List<DiGraphNode<N, E>>
      getDirectedSuccNodes(N nodeValue);

  public abstract DiGraphNode<N, E> createDirectedGraphNode(N nodeValue);

  public abstract DiGraphNode<N, E> getDirectedGraphNode(N nodeValue);

  public abstract List<DiGraphEdge<N, E>>
      getDirectedGraphEdges(N n1, N n2);

  /**
   * Disconnects all edges from n1 to n2.
   *
   * @param n1 Source node.
   * @param n2 Destination node.
   */
  public abstract void disconnectInDirection(N n1, N n2);

  /**
   * Checks whether two nodes in the graph are connected via a directed edge.
   *
   * @param n1 Node 1.
   * @param n2 Node 2.
   * @return <code>true</code> if the graph contains edge from n1 to n2.
   */
  public abstract boolean isConnectedInDirection(N n1, N n2);

  /**
   * Checks whether two nodes in the graph are connected via a directed edge
   * with the given value.
   *
   * @param n1 Node 1.
   * @param edgeValue edge value tag
   * @param n2 Node 2.
   * @return <code>true</code> if the edge exists.
   */
  public abstract boolean isConnectedInDirection(N n1, E edgeValue, N n2);

  @Override
  public boolean isConnected(N n1, N n2) {
    return isConnectedInDirection(n1, n2) || isConnectedInDirection(n2, n1);
  }

  @Override
  public boolean isConnected(N n1, E e, N n2) {
    return isConnectedInDirection(n1, e, n2) ||
        isConnectedInDirection(n2, e, n1);
  }

  /**
   * A generic directed graph node.
   *
   * @param <N> Value type that the graph node stores.
   * @param <E> Value type that the graph edge stores.
   */
  public static interface DiGraphNode<N, E> extends GraphNode<N, E> {

    public List<DiGraphEdge<N, E>> getOutEdges();

    public List<DiGraphEdge<N, E>> getInEdges();
  }

  /**
   * A generic directed graph edge.
   *
   * @param <N> Value type that the graph node stores.
   * @param <E> Value type that the graph edge stores.
   */
  public static interface DiGraphEdge<N, E> extends GraphEdge<N, E> {

    public DiGraphNode<N, E> getSource();

    public DiGraphNode<N, E> getDestination();

    public void setSource(DiGraphNode<N, E> node);

    public void setDestination(DiGraphNode<N, E> node);
  }
}
