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
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A utility class for doing fixed-point computations. We traverse
 * the edges over the given directed graph until the graph reaches
 * a steady state.
 *
 * @author nicksantos@google.com (Nick Santos)
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 */
public final class FixedPointGraphTraversal<N, E> {
  // TODO(nicksantos): Generalize the algorithm for undirected graphs, if we
  // need it.

  private final EdgeCallback<N, E> callback;

  public static final String NON_HALTING_ERROR_MSG =
    "Fixed point computation not halting";

  /**
   * Create a new traversal.
   * @param callback A callback for updating the state of the graph each
   *     time an edge is traversed.
   */
  public FixedPointGraphTraversal(EdgeCallback<N, E> callback) {
    this.callback = callback;
  }

  /**
   * Helper method for creating new traversals.
   */
  public static <NODE, EDGE> FixedPointGraphTraversal<NODE, EDGE> newTraversal(
      EdgeCallback<NODE, EDGE> callback) {
    return new FixedPointGraphTraversal<>(callback);
  }

  /**
   * Compute a fixed point for the given graph.
   * @param graph The graph to traverse.
   */
  public void computeFixedPoint(DiGraph<N, E> graph) {
    Set<N> nodes = new HashSet<>();
    for (DiGraphNode<N, E> node : graph.getDirectedGraphNodes()) {
      nodes.add(node.getValue());
    }
    computeFixedPoint(graph, nodes);
  }

  /**
   * Compute a fixed point for the given graph, entering from the given node.
   * @param graph The graph to traverse.
   * @param entry The node to begin traversing from.
   */
  public void computeFixedPoint(DiGraph<N, E> graph, N entry) {
    Set<N> entrySet = new HashSet<>();
    entrySet.add(entry);
    computeFixedPoint(graph, entrySet);
  }

  /**
   * Compute a fixed point for the given graph, entering from the given nodes.
   * @param graph The graph to traverse.
   * @param entrySet The nodes to begin traversing from.
   */
  public void computeFixedPoint(DiGraph<N, E> graph, Set<N> entrySet) {
    int cycleCount = 0;
    long nodeCount = graph.getNodes().size();

    // Choose a bail-out heuristically in case the computation
    // doesn't converge.
    long maxIterations = Math.max(nodeCount * nodeCount * nodeCount, 100);

    // Use a LinkedHashSet, so that the traversal is deterministic.
    LinkedHashSet<DiGraphNode<N, E>> workSet =
         new LinkedHashSet<>();
    for (N n : entrySet) {
      workSet.add(graph.getDirectedGraphNode(n));
    }
    for (; !workSet.isEmpty() && cycleCount < maxIterations; cycleCount++) {
      // For every out edge in the workSet, traverse that edge. If that
      // edge updates the state of the graph, then add the destination
      // node to the resultSet, so that we can update all of its out edges
      // on the next iteration.
      DiGraphNode<N, E> source = workSet.iterator().next();
      N sourceValue = source.getValue();

      workSet.remove(source);

      List<DiGraphEdge<N, E>> outEdges = source.getOutEdges();
      for (DiGraphEdge<N, E> edge : outEdges) {
        N destNode = edge.getDestination().getValue();
        if (callback.traverseEdge(sourceValue, edge.getValue(), destNode)) {
          workSet.add(edge.getDestination());
        }
      }
    }

    Preconditions.checkState(cycleCount != maxIterations,
        NON_HALTING_ERROR_MSG);
  }

  /** Edge callback */
  public static interface EdgeCallback<Node, Edge> {
    /**
     * Update the state of the destination node when the given edge
     * is traversed. For the fixed-point computation to work, only the
     * destination node may be modified. The source node and the edge must
     * not be modified.
     *
     * @param source The start node.
     * @param e The edge.
     * @param destination The end node.
     * @return Whether the state of the destination node changed.
     */
    boolean traverseEdge(Node source, Edge e, Node destination);
  }
}
