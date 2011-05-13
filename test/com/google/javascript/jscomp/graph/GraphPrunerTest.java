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

import com.google.common.base.Predicates;
import com.google.common.collect.Lists;

import junit.framework.TestCase;

/**
 * @author nicksantos@google.com (Nick Santos)
 */
public class GraphPrunerTest extends TestCase {

  public void testThreeNodesConnected() {
    DiGraph<String, String> graph = LinkedDirectedGraph.create();
    graph.createNode("A");
    graph.createNode("B");
    graph.createNode("C");
    graph.connect("A", "--", "B");
    graph.connect("B", "--", "C");

    DiGraph<String, String> pruned =
        new GraphPruner<String, String>(graph).prune(
            Predicates.in(Lists.newArrayList("A", "C")));
    assertEquals(2, pruned.getNodes().size());
    assertTrue(pruned.isConnectedInDirection("A", "C"));
  }

  public void testThreeNodesDisconnected() {
    DiGraph<String, String> graph = LinkedDirectedGraph.create();
    graph.createNode("A");
    graph.createNode("B");
    graph.createNode("C");
    graph.connect("A", "--", "B");
    graph.connect("C", "--", "B");

    DiGraph<String, String> pruned =
        new GraphPruner<String, String>(graph).prune(
            Predicates.in(Lists.newArrayList("A", "C")));
    assertEquals(2, pruned.getNodes().size());
    assertFalse(pruned.isConnectedInDirection("A", "C"));
  }

  public void testFourNodesConnected1() {
    DiGraph<String, String> graph = LinkedDirectedGraph.create();
    graph.createNode("A");
    graph.createNode("B");
    graph.createNode("C");
    graph.createNode("D");
    graph.connect("A", "--", "C");
    graph.connect("B", "--", "C");
    graph.connect("C", "--", "D");
    graph.connect("A", "--", "D");

    DiGraph<String, String> pruned =
        new GraphPruner<String, String>(graph).prune(
            Predicates.not(Predicates.equalTo("C")));
    assertEquals(3, pruned.getNodes().size());
    assertTrue(pruned.isConnectedInDirection("A", "D"));
    assertTrue(pruned.isConnectedInDirection("B", "D"));
    assertFalse(pruned.isConnectedInDirection("A", "B"));
  }

  public void testFourNodesConnected2() {
    DiGraph<String, String> graph = LinkedDirectedGraph.create();
    graph.createNode("A");
    graph.createNode("B");
    graph.createNode("C");
    graph.createNode("D");
    graph.connect("A", "--", "B");
    graph.connect("B", "--", "C");
    graph.connect("C", "--", "D");

    DiGraph<String, String> pruned =
        new GraphPruner<String, String>(graph).prune(
            Predicates.not(Predicates.in(Lists.newArrayList("B", "C"))));
    assertEquals(2, pruned.getNodes().size());
    assertTrue(pruned.isConnectedInDirection("A", "D"));
  }

  public void testFiveNodesConnected() {
    DiGraph<String, String> graph = LinkedDirectedGraph.create();
    graph.createNode("A");
    graph.createNode("B");
    graph.createNode("C");
    graph.createNode("D");
    graph.createNode("E");
    graph.connect("A", "--", "B");
    graph.connect("B", "--", "C");
    graph.connect("C", "--", "D");
    graph.connect("D", "--", "E");
    graph.connect("D", "--", "B");

    DiGraph<String, String> pruned =
        new GraphPruner<String, String>(graph).prune(
            Predicates.not(Predicates.in(Lists.newArrayList("B", "C", "D"))));
    assertEquals(2, pruned.getNodes().size());
    assertTrue(pruned.isConnectedInDirection("A", "E"));
  }
}
