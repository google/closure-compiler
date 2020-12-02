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

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A utility class for doing fixed-point computations. We traverse
 * the edges over the given directed graph until the graph reaches
 * a steady state.
 *
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 */
public final class FixedPointGraphTraversal<N, E> {
  // TODO(nicksantos): Generalize the algorithm for undirected graphs, if we
  // need it.

  private final EdgeCallback<N, E> callback;

  private enum TraversalDirection {
    INWARDS, // from a node to its incoming edges
    OUTWARDS // from a node to its outgoing edges
  }

  private final TraversalDirection traversalDirection;

  public static final String NON_HALTING_ERROR_MSG =
    "Fixed point computation not halting";

  /**
   * Create a new traversal.
   *
   * @param callback A callback for updating the state of the graph each time an edge is traversed.
   */
  private FixedPointGraphTraversal(
      EdgeCallback<N, E> callback, TraversalDirection traversalDirection) {
    this.callback = callback;
    this.traversalDirection = traversalDirection;
  }

  /** Helper method for creating new traversals that traverse from parent to child. */
  public static <NodeT, EdgeT> FixedPointGraphTraversal<NodeT, EdgeT> newTraversal(
      EdgeCallback<NodeT, EdgeT> callback) {
    return new FixedPointGraphTraversal<>(callback, TraversalDirection.OUTWARDS);
  }

  /** Helper method for creating new traversals that traverse from child to parent. */
  public static <NodeT, EdgeT> FixedPointGraphTraversal<NodeT, EdgeT> newReverseTraversal(
      EdgeCallback<NodeT, EdgeT> callback) {
    return new FixedPointGraphTraversal<>(callback, TraversalDirection.INWARDS);
  }

  /**
   * Compute a fixed point for the given graph.
   *
   * @param graph The graph to traverse.
   */
  public void computeFixedPoint(DiGraph<N, E> graph) {
    Set<N> nodes = new LinkedHashSet<>();
    for (DiGraphNode<N, E> node : graph.getNodes()) {
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
    Set<N> entrySet = new LinkedHashSet<>();
    entrySet.add(entry);
    computeFixedPoint(graph, entrySet);
  }

  // We cube the number of nodes to estimate the largest number of iterations we should allow before
  // deciding that we aren't reaching a fixed point.
  // We have to make sure that we don't hit integer overflow when calculating this value.
  // We also don't want to wait longer than a full minute for any fixed point calculation.
  // We'll be generous and assume each iteration takes only a nanosecond.
  // That's 60*10^9 iterations, so we must cap the node count we use for calculation at the
  // cube root of that number.
  private static final long MAX_NODE_COUNT_FOR_ITERATION_LIMIT = (long) Math.floor(Math.cbrt(60e9));

  /**
   * Compute a fixed point for the given graph, entering from the given nodes.
   * @param graph The graph to traverse.
   * @param entrySet The nodes to begin traversing from.
   */
  public void computeFixedPoint(DiGraph<N, E> graph, Set<N> entrySet) {
    long cycleCount = 0;
    long nodeCount = min(graph.getNodeCount(), MAX_NODE_COUNT_FOR_ITERATION_LIMIT);

    // Choose a bail-out heuristically in case the computation
    // doesn't converge.
    long maxIterations = max(nodeCount * nodeCount * nodeCount, 100L);

    // Use a LinkedHashSet, so that the traversal is deterministic.
    LinkedHashSet<DiGraphNode<N, E>> workSet = new LinkedHashSet<>();
    for (N n : entrySet) {
      workSet.add(graph.getNode(n));
    }
    for (; !workSet.isEmpty() && cycleCount < maxIterations; cycleCount++) {
      visitNode(workSet.iterator().next(), workSet);
    }

    checkState(cycleCount != maxIterations, NON_HALTING_ERROR_MSG);
  }

  private void visitNode(DiGraphNode<N, E> node, LinkedHashSet<DiGraphNode<N, E>> workSet) {
    // For every out edge in the workSet, traverse that edge. If that
    // edge updates the state of the graph, then add the destination
    // node to the resultSet, so that we can update all of its out edges
    // on the next iteration.
    workSet.remove(node);
    switch (traversalDirection) {
      case OUTWARDS:
        N sourceValue = node.getValue();
        for (DiGraphEdge<N, E> edge : node.getOutEdges()) {
          N destValue = edge.getDestination().getValue();
          if (callback.traverseEdge(sourceValue, edge.getValue(), destValue)) {
            workSet.add(edge.getDestination());
          }
        }
        return;
      case INWARDS:
        N revSourceValue = node.getValue();
        for (DiGraphEdge<N, E> edge : node.getInEdges()) {
          N revDestValue = edge.getSource().getValue();
          if (callback.traverseEdge(revSourceValue, edge.getValue(), revDestValue)) {
            workSet.add(edge.getSource());
          }
        }
        return;
    }
    throw new AssertionError("Unrecognized direction " + traversalDirection);
  }

  /** Edge callback */
  public interface EdgeCallback<Node, Edge> {
    /**
     * Update the state of the destination node when the given edge is traversed.
     *
     * <p>Recall that depending on the direction of the traversal, {@code source} and {@code
     * destination} may be swapped compared to the orientation of the edge in the graph. In either
     * case, only the {@code destination} parameter may be mutated.
     *
     * @param source The start node.
     * @param e The edge.
     * @param destination The end node.
     * @return Whether the state of the destination node changed.
     */
    boolean traverseEdge(Node source, Edge e, Node destination);
  }
}
