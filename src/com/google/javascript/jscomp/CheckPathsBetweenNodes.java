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

package com.google.javascript.jscomp;

import com.google.common.base.Predicate;
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;

/**
 * See constructor, {@link #CheckPathsBetweenNodes(DiGraph,
 * DiGraphNode, DiGraphNode, Predicate, Predicate)}, for a
 * description of this algorithm.
 *
*
 *
 * @param <N> The node type.
 * @param <E> The edge type.
 */
class CheckPathsBetweenNodes<N, E> {

  private Predicate<N> nodePredicate;
  private Predicate<DiGraphEdge<N, E>> edgePredicate;
  private boolean result;

  // This algorithm works in two stages. First, the depth-first search (DFS)
  // tree is calculated with A as the root. During when constructing the DFS
  // tree, back edges are recorded. A back edge is a non-tree edge (X -> Y)
  // where X is an descendant of Y in the DFS tree. The second step does a
  // recursive traversal of the graph. Back edges are ignored during the
  // recursive traversal, thus no cycles are encountered. Any recursive branch
  // that encounters B without yet satisfying the predicate represents a path
  // from the entry node to the exit without any nodes that satisfy the
  // predicate.
  //
  // The implementation of discoverBackEdges follows the DFS-Visit algorithm in
  // "Introduction to Algorithms" by Cormen, Leiseron, Rivest, and Stein, 2nd
  // ed., on page 541. The calculation of back edges is described on page 546.

  // A non-tree edge in the DFS that connects a node to one of its ancestors.
  private static final Annotation BACK_EDGE = new Annotation() {};
  // Not yet visited.
  private static final Annotation WHITE = null;
  // Being visited.
  private static final Annotation GRAY = new Annotation() {};
  // Finished visiting.
  private static final Annotation BLACK = new Annotation() {};

  /**
   * Given a graph G with nodes A and B, this algorithm determines if all paths
   * from A to B contain at least one node satisfying a given predicate.
   *
   * Note that nodePredicate is not necessarily called for all nodes in G nor is
   * edgePredicate called for all edges in G.
   *
   * @param graph Graph G to analyze.
   * @param a The node A.
   * @param b The node B.
   * @param nodePredicate Predicate which at least one node on each path from an
   *     A node to B (inclusive) must match.
   * @param edgePredicate Edges to consider as part of the graph. Edges in
   *     graph that don't match edgePredicate will be ignored.
   */
  CheckPathsBetweenNodes(DiGraph<N, E> graph, DiGraphNode<N, E> a,
      DiGraphNode<N, E> b, Predicate<N> nodePredicate,
      Predicate<DiGraphEdge<N, E>> edgePredicate) {
    this.nodePredicate = nodePredicate;
    this.edgePredicate = edgePredicate;

    graph.pushNodeAnnotations();
    graph.pushEdgeAnnotations();

    discoverBackEdges(a);
    result = checkAllPathsWithoutBackEdges(a, b);

    graph.popNodeAnnotations();
    graph.popEdgeAnnotations();
  }

  /**
   * @return true iff all paths contain at least one node that satisfy the
   *     predicate
   */
  public boolean allPathsSatisfyPredicate() {
    return result;
  }

  private void discoverBackEdges(DiGraphNode<N, E> u) {
    u.setAnnotation(GRAY);
    for (DiGraphEdge<N, E> e : u.getOutEdges()) {
      if (ignoreEdge(e)) {
        continue;
      }
      DiGraphNode<N, E> v = e.getDestination();
      if (v.getAnnotation() == WHITE) {
        discoverBackEdges(v);
      } else if (v.getAnnotation() == GRAY) {
        e.setAnnotation(BACK_EDGE);
      }
    }
    u.setAnnotation(BLACK);
  }

  private boolean ignoreEdge(DiGraphEdge<N, E> e) {
    return !edgePredicate.apply(e);
  }

  /**
   * Verify that all non-looping paths from {@code a} to {@code b} pass
   * through at least one node where {@code nodePredicate} is true.
   */
  private boolean checkAllPathsWithoutBackEdges(DiGraphNode<N, E> a,
      DiGraphNode<N, E> b) {
    if (nodePredicate.apply(a.getValue())) {
      return true;
    }
    if (a == b) {
      return false;
    }
    for (DiGraphEdge<N, E> e : a.getOutEdges()) {
      if (ignoreEdge(e)) {
        continue;
      }
      if (e.getAnnotation() == BACK_EDGE) {
        continue;
      }
      DiGraphNode<N, E> next = e.getDestination();
      if (!checkAllPathsWithoutBackEdges(next, b)) {
        return false;
      }
    }
    return true;
  }
}
