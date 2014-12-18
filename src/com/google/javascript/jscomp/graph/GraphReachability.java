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
import com.google.common.base.Predicate;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;

/**
 * Computes all the reachable nodes. Upon execution of {@link #compute(Object)},
 * the graph nodes will be annotated with {@link #REACHABLE} if it is reachable
 * from the specified entry node.
 *
 * @param <N> The type of data that the graph node holds.
 * @param <E> The type of data that the graph edge holds.
 *
 * @see GraphNode#getAnnotation()
 */
public class GraphReachability<N, E> implements EdgeCallback<N, E> {

  // TODO(user): This should work for undirected graphs when
  // FixedPointGraphTraversal accepts them.
  private final DiGraph<N, E> graph;

  private final Predicate<EdgeTuple<N, E>> edgePredicate;

  public GraphReachability(DiGraph<N, E> graph) {
    this(graph, null);
  }

  /**
   * @param graph The graph.
   * @param edgePredicate Given the predecessor P of the a node S and the edge
   *      coming from P to S, this predicate should return true if S is
   *      reachable from P using the edge.
   */
  public GraphReachability(DiGraph<N, E> graph,
                           Predicate<EdgeTuple<N, E>> edgePredicate) {
    this.graph = graph;
    this.edgePredicate = edgePredicate;
  }

  public void compute(N entry) {
    graph.clearNodeAnnotations();
    graph.getNode(entry).setAnnotation(REACHABLE);
    FixedPointGraphTraversal.newTraversal(this)
        .computeFixedPoint(graph, entry);
  }

  public void recompute(N reachableNode) {
    GraphNode<N, E> newReachable = graph.getNode(reachableNode);
    Preconditions.checkState(newReachable.getAnnotation() != REACHABLE);
    newReachable.setAnnotation(REACHABLE);
    FixedPointGraphTraversal.newTraversal(this)
        .computeFixedPoint(graph, reachableNode);
  }

  @Override
  public boolean traverseEdge(N source, E e, N destination) {
    if (graph.getNode(source).getAnnotation() == REACHABLE &&
        (edgePredicate == null ||
            edgePredicate.apply(new EdgeTuple<>(source, e, destination)))) {
      GraphNode<N, E> destNode = graph.getNode(destination);
      if (destNode.getAnnotation() != REACHABLE) {
        destNode.setAnnotation(REACHABLE);
        return true;
      }
    }
    return false;
  }

  public static final Annotation REACHABLE = new Annotation() {};

  /**
   * Represents a Source Node and an Edge.
   */
  public static final class EdgeTuple<N, E> {
    public final N sourceNode;
    public final E edge;
    public EdgeTuple(N sourceNode, E edge, N destNode) {
      this.sourceNode = sourceNode;
      this.edge = edge;
    }
  }
}
