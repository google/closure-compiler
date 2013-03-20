/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.common.base.Predicate;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;

/**
 * Prunes a graph, creating a new graph with nodes removed.
 *
 * If a node is removed from the graph, any paths through that node
 * will be replaced with edges. In other words, if A and B are nodes
 * in the original graph and the pruned graph, then there exists a path
 * from A -> B in the original graph iff there's a path from A -> B
 * in the pruned graph.
 *
 * We do not make any guarantees about what edges are in the pruned graph.
 *
 * @param <N> The type of data that the graph node holds.
 * @param <E> The type of data that the graph edge holds.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class GraphPruner<N, E> {
  private final DiGraph<N, E> graph;

  public GraphPruner(DiGraph<N, E> graph) {
    this.graph = graph;
  }

  public LinkedDirectedGraph<N, E> prune(Predicate<N> keep) {
    LinkedDirectedGraph<N, E> workGraph = cloneGraph(graph);

    // Create a work graph where all nodes with a path between them have
    // an edge.
    for (DiGraphNode<N, E> node : workGraph.getDirectedGraphNodes()) {
      for (DiGraphEdge<N, E> inEdge : node.getInEdges()) {
        for (DiGraphEdge<N, E> outEdge : node.getOutEdges()) {
          N source = inEdge.getSource().getValue();
          N dest = outEdge.getDestination().getValue();
          if (!workGraph.isConnectedInDirection(source, dest)) {
            workGraph.connect(source, outEdge.getValue(), dest);
          }
        }
      }
    }


    // Build a complete subgraph of workGraph.
    LinkedDirectedGraph<N, E> resultGraph =
        LinkedDirectedGraph.create();
    for (DiGraphNode<N, E> node : workGraph.getDirectedGraphNodes()) {
      if (keep.apply(node.getValue())) {
        resultGraph.createNode(node.getValue());

        for (DiGraphEdge<N, E> outEdge : node.getOutEdges()) {
          N source = node.getValue();
          N dest = outEdge.getDestination().getValue();
          if (keep.apply(dest)) {
            resultGraph.createNode(dest);
            if (source != dest &&
                !resultGraph.isConnectedInDirection(source, dest)) {
              resultGraph.connect(source, outEdge.getValue(), dest);
            }
          }
        }
      }
    }

    return resultGraph;
  }

  private static <N, E> LinkedDirectedGraph<N, E> cloneGraph(
      DiGraph<N, E> graph) {
    LinkedDirectedGraph<N, E> newGraph = LinkedDirectedGraph.create();
    for (DiGraphNode<N, E> node : graph.getDirectedGraphNodes()) {
      newGraph.createNode(node.getValue());

      for (DiGraphEdge<N, E> outEdge : node.getOutEdges()) {
        N dest = outEdge.getDestination().getValue();
        newGraph.createNode(dest);
        newGraph.connect(node.getValue(), outEdge.getValue(), dest);
      }
    }

    return newGraph;
  }
}
