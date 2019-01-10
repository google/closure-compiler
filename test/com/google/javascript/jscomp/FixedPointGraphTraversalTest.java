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

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for FixedPointGraphTraversal.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class FixedPointGraphTraversalTest {

  // The maximum value of a counter that counts as a "change"
  // to the state of the graph, for the purposes of fixed-point
  // computation.
  private int maxChange = 0;

  private static class Counter {
    int value = 0;
  }

  private class CounterIncrementer implements EdgeCallback<Counter, String> {
    @Override
    public boolean traverseEdge(Counter source, String e, Counter dest) {
      dest.value++;
      return dest.value <= maxChange;
    }
  }

  private DiGraph<Counter, String> graph;

  private Counter A, B, C, D, E;
  private final CounterIncrementer callback = new CounterIncrementer();
  private FixedPointGraphTraversal<Counter, String> traversal =
      new FixedPointGraphTraversal<>(callback);

  // Create a new graph of the following form:
  //
  //     A
  //    / \
  //   |   B
  //  / \ /
  // C   D
  //  \ /
  //   E
  //
  // with all edges pointing downwards, and an "up-edge" from E to D.
  @Before
  public void setUp() {
    A = new Counter();
    B = new Counter();
    C = new Counter();
    D = new Counter();
    E = new Counter();

    graph = LinkedDirectedGraph.create();
    graph.createDirectedGraphNode(A);
    graph.createDirectedGraphNode(B);
    graph.createDirectedGraphNode(C);
    graph.createDirectedGraphNode(D);
    graph.createDirectedGraphNode(E);

    graph.connect(A, "->", B);
    graph.connect(A, "->", C);
    graph.connect(A, "->", D);
    graph.connect(B, "->", D);
    graph.connect(C, "->", E);
    graph.connect(D, "->", E);
    graph.connect(E, "->", D);
  }

  @Test
  public void testGraph1() {
    maxChange = 0;
    traversal.computeFixedPoint(graph, A);

    assertThat(A.value).isEqualTo(0);
    assertThat(B.value).isEqualTo(1);
    assertThat(C.value).isEqualTo(1);
    assertThat(D.value).isEqualTo(1);
    assertThat(E.value).isEqualTo(0);
  }

  @Test
  public void testGraph2() {
    maxChange = 0;
    traversal.computeFixedPoint(graph, D);

    assertThat(A.value).isEqualTo(0);
    assertThat(B.value).isEqualTo(0);
    assertThat(C.value).isEqualTo(0);
    assertThat(D.value).isEqualTo(0);
    assertThat(E.value).isEqualTo(1);
  }

  @Test
  public void testGraph3() {
    maxChange = 1;
    traversal.computeFixedPoint(graph, A);

    assertThat(A.value).isEqualTo(0);
    assertThat(B.value).isEqualTo(1);
    assertThat(C.value).isEqualTo(1);
    assertThat(D.value).isEqualTo(3);
    assertThat(E.value).isEqualTo(2);
  }

  @Test
  public void testGraph4() {
    maxChange = 1;
    traversal.computeFixedPoint(graph, D);

    assertThat(A.value).isEqualTo(0);
    assertThat(B.value).isEqualTo(0);
    assertThat(C.value).isEqualTo(0);
    assertThat(D.value).isEqualTo(1);
    assertThat(E.value).isEqualTo(2);
  }

  @Test
  public void testGraph5() {
    maxChange = 5;
    traversal.computeFixedPoint(graph, A);

    assertThat(A.value).isEqualTo(0);
    assertThat(B.value).isEqualTo(1);
    assertThat(C.value).isEqualTo(1);
    assertThat(D.value).isEqualTo(6);
    assertThat(E.value).isEqualTo(5);
  }

  @Test
  public void testGraph6() {
    maxChange = 5;
    traversal.computeFixedPoint(graph, B);

    assertThat(A.value).isEqualTo(0);
    assertThat(B.value).isEqualTo(0);
    assertThat(C.value).isEqualTo(0);
    assertThat(D.value).isEqualTo(6);
    assertThat(E.value).isEqualTo(5);
  }

  @Test
  public void testGraph8() {
    maxChange = 2;
    traversal.computeFixedPoint(graph, A);

    try {
      traversal = new FixedPointGraphTraversal<>(
        new EdgeCallback<Counter, String>() {
          @Override
          public boolean traverseEdge(Counter source, String e, Counter dest) {
            return true;
          }
        });
      traversal.computeFixedPoint(graph, A);
      assertWithMessage("Expecting Error: " + FixedPointGraphTraversal.NON_HALTING_ERROR_MSG)
          .fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().isEqualTo(FixedPointGraphTraversal.NON_HALTING_ERROR_MSG);
    }
  }

  @Test
  public void testGraph9() {
    maxChange = 0;

    // when the graph traversal is done for the whole graph, we're actually
    // counting the number of "in" edges for each node.
    traversal.computeFixedPoint(graph);

    assertThat(A.value).isEqualTo(0);
    assertThat(B.value).isEqualTo(1);
    assertThat(C.value).isEqualTo(1);
    assertThat(D.value).isEqualTo(3);
    assertThat(E.value).isEqualTo(2);
  }

  @Test
  public void testGraph10() {
    // Test a graph with self-edges.
    maxChange = 5;

    A = new Counter();
    B = new Counter();

    graph = LinkedDirectedGraph.create();
    graph.createDirectedGraphNode(A);
    graph.createDirectedGraphNode(B);

    graph.connect(A, "->", A);
    graph.connect(A, "->", B);

    traversal.computeFixedPoint(graph);

    assertThat(A.value).isEqualTo(6);
    assertThat(B.value).isEqualTo(6);
  }
}
