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

import com.google.javascript.jscomp.graph.Graph;
import com.google.javascript.jscomp.graph.Graph.GraphEdge;
import com.google.javascript.jscomp.graph.GraphColoring;
import com.google.javascript.jscomp.graph.GraphColoring.Color;
import com.google.javascript.jscomp.graph.GraphColoring.GreedyGraphColoring;
import com.google.javascript.jscomp.graph.LinkedUndirectedGraph;

import junit.framework.TestCase;

import java.util.Comparator;

/**
 * Tests for {@link GraphColoring}.
 *
 */
public class GraphColoringTest extends TestCase {

  public void testNoEdge() {
    Graph<String, String> graph = LinkedUndirectedGraph.create();
    for (int i = 0; i < 5; i++) {
      graph.createNode("Node " + i);
      // All node with same color.
      GraphColoring<String, String> coloring =
          new GreedyGraphColoring<>(graph);
      assertEquals(1, coloring.color());
      validateColoring(graph);
      for (int j = 0; j < i; j++) {
        assertEquals("Node 0", coloring.getPartitionSuperNode("Node 0"));
      }
    }
  }

  public void testTwoNodesConnected() {
    Graph<String, String> graph = LinkedUndirectedGraph.create();
    graph.createNode("A");
    graph.createNode("B");
    graph.connect("A", "--", "B");
    GraphColoring<String, String> coloring =
        new GreedyGraphColoring<>(graph);
    assertEquals(2, coloring.color());
    validateColoring(graph);
    assertEquals("A", coloring.getPartitionSuperNode("A"));
    assertEquals("B", coloring.getPartitionSuperNode("B"));
  }

  public void testGreedy() {
    Graph<String, String> graph = LinkedUndirectedGraph.create();
    graph.createNode("A");
    graph.createNode("B");
    graph.createNode("C");
    graph.createNode("D");
    graph.connect("A", "--", "C");
    graph.connect("B", "--", "C");
    graph.connect("B", "--", "D");
    GraphColoring<String, String> coloring =
        new GreedyGraphColoring<>(graph);
    assertEquals(2, coloring.color());
    validateColoring(graph);
    assertEquals("A", coloring.getPartitionSuperNode("A"));
    assertEquals("A", coloring.getPartitionSuperNode("B"));
    assertEquals("C", coloring.getPartitionSuperNode("C"));
  }

  public void testFullyConnected() {
    final int count = 100;
    Graph<String, String> graph = LinkedUndirectedGraph.create();
    for (int i = 0; i < count; i++) {
      graph.createNode("Node " + i);
      for (int j = 0; j < count; j++) {
        graph.createNode("Node " + j);
        if (i != j) {
          graph.connect("Node " + i, null, "Node " + j);
        }
      }
    }
    GraphColoring<String, String> coloring =
        new GreedyGraphColoring<>(graph);
    assertEquals(count, coloring.color());
    validateColoring(graph);
    for (int i = 0; i < count; i++) {
      assertEquals("Node " + i, coloring.getPartitionSuperNode("Node " + i));
    }
  }

  public void testAllConnectedToOneNode() {
    final int count = 10;
    Graph<String, String> graph = LinkedUndirectedGraph.create();
    graph.createNode("Center");
    for (int i = 0; i < count; i++) {
      graph.createNode("Node " + i);
      graph.connect("Center", null, "Node " + i);
    }
    GraphColoring<String, String> coloring =
        new GreedyGraphColoring<>(graph);
    assertEquals(2, coloring.color());
    validateColoring(graph);
    assertEquals("Center", coloring.getPartitionSuperNode("Center"));
    for (int i = 0; i < count; i++) {
      assertEquals("Node 0", coloring.getPartitionSuperNode("Node " + i));
    }
  }

  public void testTwoFullyConnected() {
    final int count = 100;
    // A graph with two disconnected disjunct cliques.
    Graph<String, String> graph = LinkedUndirectedGraph.create();
    for (int i = 0; i < count; i++) {
      graph.createNode("Node Left " + i);
      graph.createNode("Node Right " + i);
      for (int j = 0; j < count; j++) {
        graph.createNode("Node Left " + j);
        graph.createNode("Node Right " + j);
        if (i != j) {
          graph.connect("Node Left " + i, null, "Node Left " + j);
          graph.connect("Node Right " + i, null, "Node Right " + j);
        }
      }
    }
    assertEquals(count, new GreedyGraphColoring<>(graph).color());
    validateColoring(graph);

    // Connect the two cliques.
    for (int i = 0; i < count; i++) {
      graph.connect("Node Left " + i, null, "Node Right " + i);
    }
    // Think of two exactly same graph with the same coloring side by side.
    // If we circularly shift the colors of one of the graph by 1, we can
    // connect the isomorphic nodes and still have a valid coloring in the
    // resulting graph.
    assertEquals(count, new GreedyGraphColoring<>(graph).color());
    validateColoring(graph);
  }

  public void testDeterministic() {
    // A pentagon.
    Graph<String, String> graph = LinkedUndirectedGraph.create();
    graph.createNode("A");
    graph.createNode("B");
    graph.createNode("C");
    graph.createNode("D");
    graph.createNode("E");
    graph.connect("A", "-->", "B");
    graph.connect("B", "-->", "C");
    graph.connect("C", "-->", "D");
    graph.connect("D", "-->", "E");
    graph.connect("E", "-->", "A");

    Comparator<String> lexicographic = new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return o1.toString().compareTo(o2.toString());
      }
    };
    GraphColoring<String, String> coloring =
        new GreedyGraphColoring<>(graph, lexicographic);
    assertEquals(3, coloring.color());
    validateColoring(graph);
    assertEquals("A", coloring.getPartitionSuperNode("A"));
    assertEquals("A", coloring.getPartitionSuperNode("C"));

    Comparator<String> biasD = new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return o1.replaceAll("D", "@").compareTo(o2.replaceAll("D", "@"));
      }
    };

    coloring = new GreedyGraphColoring<>(graph, biasD);
    assertEquals(3, coloring.color());
    validateColoring(graph);
    assertEquals("A", coloring.getPartitionSuperNode("A"));
    assertFalse("A".equals(coloring.getPartitionSuperNode("C")));
  }

  /**
   * Validate that each node has been colored and connected nodes have different
   * coloring.
   */
  private static <N, E> void validateColoring(Graph<N, E> graph) {
    for (GraphNode<N, E> node : graph.getNodes()) {
      assertNotNull(node.getAnnotation());
    }
    for (GraphEdge<N, E> edge : graph.getEdges()) {
      Color c1 = edge.getNodeA().getAnnotation();
      Color c2 = edge.getNodeB().getAnnotation();
      assertNotNull(c1);
      assertNotNull(c2);
      assertFalse(c1.equals(c2));
    }
  }
}
