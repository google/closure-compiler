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

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link com.google.javascript.jscomp.graph.GraphReachability}.
 *
 */
@RunWith(JUnit4.class)
public final class GraphReachabilityTest {
  GraphReachability<String, String> reachability = null;
  DiGraph<String, String> graph = null;

  @Test
  public void testSimple() {
    graph = LinkedDirectedGraph.create();
    graph.createNode("A");
    reachability = new GraphReachability<>(graph);
    reachability.compute("A");
    assertReachable("A");

    graph.createNode("B");
    reachability = new GraphReachability<>(graph);
    reachability.compute("A");
    assertReachable("A");
    assertNotReachable("B");

    graph.connect("A", "--->", "B");
    reachability = new GraphReachability<>(graph);
    reachability.compute("B");
    assertNotReachable("A");
    assertReachable("B");

    graph.connect("B", "--->", "A");
    reachability = new GraphReachability<>(graph);
    reachability.compute("B");
    assertReachable("A");
    assertReachable("B");

    graph.createNode("C");
    reachability = new GraphReachability<>(graph);
    reachability.compute("A");
    assertReachable("A");
    assertReachable("B");
    assertNotReachable("C");

    graph.createNode("D");
    graph.connect("C", "--->", "D");
    reachability = new GraphReachability<>(graph);
    reachability.compute("A");
    assertReachable("A");
    assertReachable("B");
    assertNotReachable("C");
    assertNotReachable("D");
    reachability.recompute("C");
    assertReachable("C");
    assertReachable("D");
  }

  public void assertReachable(String s) {
    assertWithMessage(s + " should be reachable")
        .that(GraphReachability.REACHABLE)
        .isSameAs(graph.getNode(s).getAnnotation());
  }

  public void assertNotReachable(String s) {
    assertWithMessage(s + " should not be reachable")
        .that(GraphReachability.REACHABLE)
        .isNotSameAs(graph.getNode(s).getAnnotation());
  }
}
