/*
 * Copyright 2008 Google Inc.
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
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.jscomp.graph.GraphNode;

/**
 * Computes all the reachable nodes. Upon execution of {@link #compute(Object)},
 * the graph nodes will be annotated with {@link #REACHABLE} if it is reachable
 * from the specified entry node.
 *
 * @see GraphNode#getAnnotation()
*
 */
public class GraphReachability<N, E> implements EdgeCallback<N, E> {

  // TODO(user): This should work for undirected graphs when
  // FixedPointGraphTraversal accepts them.
  private final DiGraph<N, E> graph;

  public GraphReachability(DiGraph<N, E> graph) {
    this.graph = graph;
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
    if (graph.getNode(source).getAnnotation() == REACHABLE) {
      GraphNode<N, E> destNode = graph.getNode(destination);
      if (destNode.getAnnotation() != REACHABLE) {
        destNode.setAnnotation(REACHABLE);
        return true;
      }
    }
    return false;
  }

  public static final Annotation REACHABLE = new Annotation() {};
}
