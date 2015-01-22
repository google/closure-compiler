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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.graph.Annotatable;
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.Graph;
import com.google.javascript.jscomp.graph.Graph.GraphEdge;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.jscomp.graph.LinkedUndirectedGraph;
import com.google.javascript.jscomp.graph.SubGraph;
import com.google.javascript.jscomp.graph.UndiGraph;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for the graph data structure.
 *
 */
public class GraphTest extends TestCase {

  public void testDirectedSimple() {
    DiGraph<String, String> graph =
        LinkedDirectedGraph.create();
    graph.createNode("a");
    graph.createNode("b");
    graph.createNode("c");
    graph.connect("a", "->", "b");
    assertThat(graph.hasNode("a")).isTrue();
    assertThat(graph.hasNode("b")).isTrue();
    assertThat(graph.hasNode("c")).isTrue();
    assertThat(graph.hasNode("d")).isFalse();
    assertThat(graph.isConnected("a", "b")).isTrue();
    assertThat(graph.isConnected("b", "a")).isTrue();
    assertThat(graph.isConnected("a", "c")).isFalse();
    assertThat(graph.isConnected("b", "c")).isFalse();
    assertThat(graph.isConnected("c", "a")).isFalse();
    assertThat(graph.isConnected("c", "b")).isFalse();
    assertThat(graph.isConnected("a", "a")).isFalse();
    assertThat(graph.isConnected("b", "b")).isFalse();
    assertThat(graph.isConnected("b", "c")).isFalse();
    assertThat(graph.isConnectedInDirection("a", "b")).isTrue();
    assertThat(graph.isConnectedInDirection("b", "a")).isFalse();
    assertThat(graph.isConnectedInDirection("a", "c")).isFalse();
    assertThat(graph.isConnectedInDirection("b", "c")).isFalse();
    assertThat(graph.isConnectedInDirection("c", "a")).isFalse();
    assertThat(graph.isConnectedInDirection("c", "b")).isFalse();

    // Removal.
    graph.disconnect("a", "b");
    assertThat(graph.isConnected("a", "b")).isFalse();
    assertThat(graph.isConnected("b", "a")).isFalse();

    // Disconnect both ways.
    graph.connect("a", "->", "b");
    graph.connect("b", "->", "a");
    graph.disconnect("a", "b");
    assertThat(graph.isConnected("a", "b")).isFalse();
    assertThat(graph.isConnected("b", "a")).isFalse();

    // Disconnect one way.
    graph.connect("a", "->", "b");
    graph.connect("b", "->", "a");
    graph.disconnectInDirection("a", "b");
    assertThat(graph.isConnected("b", "a")).isTrue();
    assertThat(graph.isConnected("a", "b")).isTrue();
    assertThat(graph.isConnectedInDirection("a", "b")).isFalse();
    assertThat(graph.isConnectedInDirection("b", "a")).isTrue();
  }

  public void testUndirectedSimple() {
    UndiGraph<String, String> graph =
        LinkedUndirectedGraph.create();
    graph.createNode("a");
    graph.createNode("b");
    graph.createNode("c");
    graph.connect("a", "--", "b");
    assertThat(graph.hasNode("a")).isTrue();
    assertThat(graph.hasNode("b")).isTrue();
    assertThat(graph.hasNode("c")).isTrue();
    assertThat(graph.hasNode("d")).isFalse();
    assertThat(graph.isConnected("a", "b")).isTrue();
    assertThat(graph.isConnected("b", "a")).isTrue();
    assertThat(graph.isConnected("a", "c")).isFalse();
    assertThat(graph.isConnected("b", "c")).isFalse();
    assertThat(graph.isConnected("c", "a")).isFalse();
    assertThat(graph.isConnected("c", "b")).isFalse();
    assertThat(graph.isConnected("a", "a")).isFalse();
    assertThat(graph.isConnected("b", "b")).isFalse();
    assertThat(graph.isConnected("b", "c")).isFalse();

    // Removal.
    graph.disconnect("a", "b");
    assertThat(graph.isConnected("a", "b")).isFalse();
    assertThat(graph.isConnected("b", "a")).isFalse();
  }

  public void testDirectedSelfLoop() {
    DiGraph<String, String> graph =
        LinkedDirectedGraph.create();
    graph.createNode("a");
    graph.createNode("b");
    graph.connect("a", "->", "a");
    assertThat(graph.isConnected("a", "a")).isTrue();
    assertThat(graph.isConnected("a", "b")).isFalse();
    assertThat(graph.isConnected("b", "a")).isFalse();
    assertThat(graph.isConnectedInDirection("a", "a")).isTrue();
    assertThat(graph.isConnectedInDirection("a", "b")).isFalse();
    assertThat(graph.isConnectedInDirection("b", "a")).isFalse();

    // Removal.
    graph.disconnect("a", "a");
    assertThat(graph.isConnected("a", "a")).isFalse();

    // Disconnect both ways.
    graph.connect("a", "->", "a");
    graph.disconnect("a", "a");
    assertThat(graph.isConnected("a", "a")).isFalse();
    assertThat(graph.isConnected("a", "a")).isFalse();

    // Disconnect one way.
    graph.connect("a", "->", "a");
    graph.disconnectInDirection("a", "a");
    assertThat(graph.isConnected("a", "a")).isFalse();
  }

  public void testUndirectedSelfLoop() {
    UndiGraph<String, String> graph =
        LinkedUndirectedGraph.create();
    graph.createNode("a");
    graph.createNode("b");
    graph.connect("a", "--", "a");
    assertThat(graph.isConnected("a", "a")).isTrue();
    assertThat(graph.isConnected("a", "b")).isFalse();
    assertThat(graph.isConnected("b", "a")).isFalse();

    // Removal.
    graph.disconnect("a", "a");
    assertThat(graph.isConnected("a", "a")).isFalse();
  }

  public void testDirectedInAndOutEdges() {
    DiGraph<String, String> graph =
        LinkedDirectedGraph.create();
    graph.createNode("a");
    graph.createNode("b");
    graph.createNode("c");
    graph.createNode("d");
    graph.connect("a", "->", "b");
    graph.connect("a", "-->", "b");
    graph.connect("a", "--->", "b");
    graph.connect("a", "->", "c");
    graph.connect("c", "->", "d");
    assertSetEquals(graph.getDirectedSuccNodes("a"), "b", "c");
    assertSetEquals(graph.getDirectedPredNodes("b"), "a");
    assertSetEquals(graph.getDirectedPredNodes("c"), "a");
    assertListCount(graph.getDirectedSuccNodes("a"), "b", 3);

    // Removal.
    graph.disconnect("a", "b");
    assertThat(graph.isConnected("a", "b")).isFalse();
  }

  public void testUndirectedNeighbors() {
    UndiGraph<String, String> graph =
        LinkedUndirectedGraph.create();
    graph.createNode("a");
    graph.createNode("b");
    graph.createNode("c");
    graph.createNode("d");
    graph.connect("a", "-", "b");
    graph.connect("a", "--", "b");
    graph.connect("a", "---", "b");
    graph.connect("a", "-", "c");
    graph.connect("c", "-", "d");
    assertSetEquals(graph.getNeighborNodes("a"), "b", "c");
    assertSetEquals(graph.getNeighborNodes("b"), "a");
    assertSetEquals(graph.getNeighborNodes("c"), "a", "d");
    assertListCount(graph.getNeighborNodes("a"), "b", 3);

    // Removal.
    graph.disconnect("a", "b");
    assertThat(graph.isConnected("a", "b")).isFalse();
  }

  public void testDirectedGetFirstEdge() {
    DiGraph<String, String> graph =
      LinkedDirectedGraph.create();
    graph.createNode("a");
    graph.createNode("b");
    graph.createNode("c");
    graph.connect("a", "-", "b");
    assertThat("-").isEqualTo(graph.getFirstEdge("a", "b").getValue());
    assertThat("-").isEqualTo(graph.getFirstEdge("b", "a").getValue());
    assertNull(graph.getFirstEdge("a", "c"));
  }

  public void testUndirectedGetFirstEdge() {
    UndiGraph<String, String> graph =
      LinkedUndirectedGraph.create();
    graph.createNode("a");
    graph.createNode("b");
    graph.createNode("c");
    graph.connect("a", "-", "b");
    assertThat("-").isEqualTo(graph.getFirstEdge("a", "b").getValue());
    assertThat("-").isEqualTo(graph.getFirstEdge("b", "a").getValue());
    assertNull(graph.getFirstEdge("a", "c"));
  }

  public void testNodeAnnotations() {
    Graph<String, String> graph = LinkedUndirectedGraph.create();
    GraphNode<String, String> a = graph.createNode("a");
    GraphNode<String, String> b = graph.createNode("b");
    checkAnnotations(graph, a, b);
  }

  public void testEdgeAnnotations() {
    Graph<String, String> graph = LinkedUndirectedGraph.create();
    graph.createNode("1");
    graph.createNode("2");
    graph.createNode("3");
    graph.connect("1", "a", "2");
    graph.connect("2", "b", "3");
    GraphEdge<String, String> a = graph.getEdges("1", "2").get(0);
    GraphEdge<String, String> b = graph.getEdges("2", "3").get(0);
    checkAnnotations(graph, a, b);
  }

  private static void checkAnnotations(
      Graph<String, String> graph, Annotatable a, Annotatable b) {
    final Annotation A = new Annotation() {};
    final Annotation B = new Annotation() {};

    // Initially null.
    assertNull(a.getAnnotation());
    assertNull(b.getAnnotation());

    // Test basic setting.
    a.setAnnotation(A);
    b.setAnnotation(B);
    assertSame(A, a.getAnnotation());
    assertSame(B, b.getAnnotation());

    // Test clearing.
    graph.clearEdgeAnnotations();
    graph.clearNodeAnnotations();
    assertNull(a.getAnnotation());
    assertNull(b.getAnnotation());

    a.setAnnotation(A);
    b.setAnnotation(B);
    // Pushing clears.
    graph.pushEdgeAnnotations();
    graph.pushNodeAnnotations();
    assertNull(a.getAnnotation());
    assertNull(b.getAnnotation());
    a.setAnnotation(B);
    b.setAnnotation(B);
    graph.pushEdgeAnnotations();
    graph.pushNodeAnnotations();
    a.setAnnotation(B);
    b.setAnnotation(A);

    // Test restoring then restoring old values with pop.
    assertSame(B, a.getAnnotation());
    assertSame(A, b.getAnnotation());
    graph.popEdgeAnnotations();
    graph.popNodeAnnotations();
    assertSame(B, a.getAnnotation());
    assertSame(B, b.getAnnotation());
    graph.popEdgeAnnotations();
    graph.popNodeAnnotations();
    assertSame(A, a.getAnnotation());
    assertSame(B, b.getAnnotation());
  }

  public void testDegree() {
    testDirectedDegree(LinkedDirectedGraph.<String, String>create());
    testDirectedDegree(LinkedUndirectedGraph.<String, String>create());
  }

  public void testDirectedDegree(Graph<String, String> graph) {
    graph.createNode("a");
    graph.createNode("b");
    graph.createNode("c");
    graph.createNode("d");
    assertThat(graph.getNodeDegree("a")).isEqualTo(0);
    graph.connect("a", "-", "b");
    assertThat(graph.getNodeDegree("a")).isEqualTo(1);
    graph.connect("b", "-", "c");
    assertThat(graph.getNodeDegree("a")).isEqualTo(1);
    graph.connect("a", "-", "c");
    assertThat(graph.getNodeDegree("a")).isEqualTo(2);
    graph.connect("d", "-", "a");
    assertThat(graph.getNodeDegree("a")).isEqualTo(3);
  }

  public void testDirectedConnectIfNotFound() {
    testDirectedConnectIfNotFound(
        LinkedDirectedGraph.<String, String>create());
    testDirectedConnectIfNotFound(
        LinkedUndirectedGraph.<String, String>create());
  }

  public void testDirectedConnectIfNotFound(Graph<String, String> graph) {
    graph.createNode("a");
    graph.createNode("b");
    graph.connectIfNotFound("a", "-", "b");
    assertThat(graph.getNodeDegree("a")).isEqualTo(1);
    graph.connectIfNotFound("a", "-", "b");
    assertThat(graph.getNodeDegree("a")).isEqualTo(1);
    graph.connectIfNotFound("a", null, "b");
    assertThat(graph.getNodeDegree("a")).isEqualTo(2);
    graph.connectIfNotFound("a", null, "b");
    assertThat(graph.getNodeDegree("a")).isEqualTo(2);
  }

  public void testSimpleSubGraph() {
    UndiGraph<String, String> graph =
        LinkedUndirectedGraph.create();
    graph.createNode("a");
    graph.createNode("b");
    graph.createNode("c");
    graph.connect("a", "--", "b");

    SubGraph<String, String> subGraph = graph.newSubGraph();
    subGraph.addNode("a");
    subGraph.addNode("b");

    try {
      subGraph.addNode("d");
      fail("SubGraph should not allow add for node that is not in graph.");
    } catch (IllegalArgumentException e) {
      // exception expected
    }

    assertThat(subGraph.isIndependentOf("a")).isFalse();
    assertThat(subGraph.isIndependentOf("b")).isFalse();
    assertThat(subGraph.isIndependentOf("c")).isTrue();
  }

  private <T extends GraphNode<String, String>> void assertListCount(
      List<T> list, String target, int count) {
    for (GraphNode<String, String> node : list) {
      if (node.getValue().equals(target)) {
        count--;
      }
    }
    assertThat(count).isEqualTo(0);
  }

  private <T extends GraphNode<String, String>> void assertSetEquals(
      List<T> list, String ... targets) {
    Set<String> set = new HashSet<>();
    for (GraphNode<String, String> node : list) {
      set.add(node.getValue());
    }
    Set<String> otherSet = new HashSet<>();
    Collections.addAll(otherSet, targets);
    assertThat(otherSet).isEqualTo(set);
  }
}
