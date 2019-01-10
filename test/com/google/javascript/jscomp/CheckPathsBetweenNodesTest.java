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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CheckPathsBetweenNodes}.
 *
 */
@RunWith(JUnit4.class)
public final class CheckPathsBetweenNodesTest {

  /**
   * Predicate satisfied by strings with a given prefix.
   */
  private static class PrefixPredicate implements Predicate<String> {
    String prefix;

    PrefixPredicate(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public boolean apply(String input) {
      return input.startsWith(prefix);
    }
  }

  private static final Predicate<String> FALSE = Predicates.alwaysFalse();

  private static final Predicate<DiGraphEdge<String, String>> ALL_EDGE =
      Predicates.alwaysTrue();

  private static final Predicate<DiGraphEdge<String, String>> NO_EDGE =
    Predicates.alwaysFalse();

  /** Tests straight-line graphs. */
  @Test
  public void testSimple() {
    DiGraph<String, String> g = LinkedDirectedGraph.create();
    g.createDirectedGraphNode("a");
    g.createDirectedGraphNode("b");
    g.createDirectedGraphNode("c");
    g.createDirectedGraphNode("d");

    g.connect("a", "-", "b");
    g.connect("b", "-", "c");
    g.connect("c", "-", "d");
    g.connect("a", "x", "d");

    // Simple case: the sole path from a to d has a matching node.
    assertGood(createTest(g, "a", "d", Predicates.equalTo("b"), edgeIs("-")));
    //Test two edge cases where satisfying node is the first and last node on
    // the path.
    assertGood(createTest(g, "a", "d", Predicates.equalTo("a"), edgeIs("-")));
    assertGood(createTest(g, "a", "d", Predicates.equalTo("d"), edgeIs("-")));

    // Traverse no edges, so no paths.
    assertGood(createTest(g, "a", "d", FALSE, NO_EDGE));

    // No path with matching edges contains b.
    assertBad(createTest(g, "a", "d", Predicates.equalTo("b"), edgeIs("x")));
  }

  /** Tests a graph where some paths between the nodes are valid and others are invalid. */
  @Test
  public void testSomeValidPaths() {
    DiGraph<String, String> g = LinkedDirectedGraph.create();
    g.createDirectedGraphNode("a");
    g.createDirectedGraphNode("b");
    g.createDirectedGraphNode("c");
    g.createDirectedGraphNode("d");
    g.createDirectedGraphNode("e");

    g.connect("a", "1", "b");
    g.connect("b", "2", "c");
    g.connect("b", "3", "e");
    g.connect("e", "4", "d");
    g.connect("c", "5", "d");

    assertBad(createTest(g, "a", "d", Predicates.equalTo("c"), ALL_EDGE));
    assertBad(createTest(g, "a", "d", Predicates.equalTo("z"), ALL_EDGE));
  }

  /** Tests a graph with many valid paths. */
  @Test
  public void testManyValidPaths() {
    DiGraph<String, String> g = LinkedDirectedGraph.create();
    g.createDirectedGraphNode("a");
    g.createDirectedGraphNode("b");
    g.createDirectedGraphNode("c1");
    g.createDirectedGraphNode("c2");
    g.createDirectedGraphNode("c3");
    g.createDirectedGraphNode("d");

    g.connect("a",  "-", "b");
    g.connect("b",  "-", "c1");
    g.connect("b",  "-", "c2");
    g.connect("c2", "-", "d");
    g.connect("c1", "-", "d");
    g.connect("a",  "-", "c3");
    g.connect("c3", "-", "d");

    assertGood(createTest(g, "a", "d", new PrefixPredicate("c"), ALL_EDGE));
  }

  /** Tests a graph with some cycles. */
  @Test
  public void testCycles1() {
    DiGraph<String, String> g = LinkedDirectedGraph.create();
    g.createDirectedGraphNode("a");
    g.createDirectedGraphNode("b");
    g.createDirectedGraphNode("c");
    g.createDirectedGraphNode("d");
    g.createDirectedGraphNode("e");
    g.createDirectedGraphNode("f");

    g.connect("a", "-", "b");
    g.connect("b", "-", "c");
    g.connect("c", "-", "d");
    g.connect("d", "-", "e");
    g.connect("e", "-", "f");
    g.connect("f", "-", "b");

    assertGood(createTest(g, "a", "e", Predicates.equalTo("b"), ALL_EDGE));
    assertGood(createTest(g, "a", "e", Predicates.equalTo("c"), ALL_EDGE));
    assertGood(createTest(g, "a", "e", Predicates.equalTo("d"), ALL_EDGE));
    assertGood(createTest(g, "a", "e", Predicates.equalTo("e"), ALL_EDGE));
    assertBad(createTest(g, "a", "e", Predicates.equalTo("f"), ALL_EDGE));
  }

  /**
   * Tests another graph with cycles. The topology of this graph was inspired by a control flow
   * graph that was being incorrectly analyzed by an early version of CheckPathsBetweenNodes.
   */
  @Test
  public void testCycles2() {
    DiGraph<String, String> g = LinkedDirectedGraph.create();
    g.createDirectedGraphNode("a");
    g.createDirectedGraphNode("b");
    g.createDirectedGraphNode("c");
    g.createDirectedGraphNode("d");

    g.connect("a", "-", "b");
    g.connect("b", "-", "c");
    g.connect("c", "-", "b");
    g.connect("b", "-", "d");

    assertGood(createTest(g, "a", "d", Predicates.equalTo("a"), ALL_EDGE));
    assertBad(createTest(g, "a", "d", Predicates.equalTo("z"), ALL_EDGE));
  }

  /**
   * Tests another graph with cycles. The topology of this graph was inspired by a control flow
   * graph that was being incorrectly analyzed by an early version of CheckPathsBetweenNodes.
   */
  @Test
  public void testCycles3() {
    DiGraph<String, String> g = LinkedDirectedGraph.create();
    g.createDirectedGraphNode("a");
    g.createDirectedGraphNode("b");
    g.createDirectedGraphNode("c");
    g.createDirectedGraphNode("d");

    g.connect("a", "-", "b");
    g.connect("b", "-", "c");
    g.connect("c", "-", "b");
    g.connect("b", "-", "d");
    g.connect("c", "-", "d");

    assertGood(createTest(g, "a", "d", Predicates.equalTo("a"), ALL_EDGE));
    assertBad(createTest(g, "a", "d", Predicates.equalTo("z"), ALL_EDGE));
  }

  /**
   * Much of the tests are done by testing all paths. We quickly verified that some paths are indeed
   * correct for the some path case.
   */
  @Test
  public void testSomePath1() {
    DiGraph<String, String> g = LinkedDirectedGraph.create();
    g.createDirectedGraphNode("a");
    g.createDirectedGraphNode("b");
    g.createDirectedGraphNode("c");
    g.createDirectedGraphNode("d");

    g.connect("a", "-", "b");
    g.connect("a", "-", "c");
    g.connect("b", "-", "d");
    g.connect("c", "-", "d");

    assertThat(
            createTest(g, "a", "d", Predicates.equalTo("b"), ALL_EDGE).somePathsSatisfyPredicate())
        .isTrue();
    assertThat(
            createTest(g, "a", "d", Predicates.equalTo("c"), ALL_EDGE).somePathsSatisfyPredicate())
        .isTrue();
    assertThat(
            createTest(g, "a", "d", Predicates.equalTo("a"), ALL_EDGE).somePathsSatisfyPredicate())
        .isTrue();
    assertThat(
            createTest(g, "a", "d", Predicates.equalTo("d"), ALL_EDGE).somePathsSatisfyPredicate())
        .isTrue();
    assertThat(
            createTest(g, "a", "d", Predicates.equalTo("NONE"), ALL_EDGE)
                .somePathsSatisfyPredicate())
        .isFalse();
  }

  @Test
  public void testSomePath2() {
    // No Paths between nodes, by definition, always false.
    DiGraph<String, String> g = LinkedDirectedGraph.create();
    g.createDirectedGraphNode("a");
    g.createDirectedGraphNode("b");

    assertThat(
            createTest(g, "a", "b", Predicates.equalTo("b"), ALL_EDGE).somePathsSatisfyPredicate())
        .isFalse();
    assertThat(
            createTest(g, "a", "b", Predicates.equalTo("d"), ALL_EDGE).somePathsSatisfyPredicate())
        .isFalse();
    assertThat(
            createTest(g, "a", "b", Predicates.equalTo("a"), ALL_EDGE).somePathsSatisfyPredicate())
        .isTrue();
  }

  @Test
  public void testSomePathRevisiting() {
    DiGraph<String, String> g = LinkedDirectedGraph.create();
    g.createDirectedGraphNode("1");
    g.createDirectedGraphNode("2a");
    g.createDirectedGraphNode("2b");
    g.createDirectedGraphNode("3");
    g.createDirectedGraphNode("4a");
    g.createDirectedGraphNode("4b");
    g.createDirectedGraphNode("5");
    g.connect("1", "-", "2a");
    g.connect("1", "-", "2b");
    g.connect("2a", "-", "3");
    g.connect("2b", "-", "3");
    g.connect("3", "-", "4a");
    g.connect("3", "-", "4b");
    g.connect("4a", "-", "5");
    g.connect("4b", "-", "5");

    CountingPredicate<String> p =
      new CountingPredicate<>(Predicates.equalTo("4a"));

    assertThat(createTest(g, "1", "5", p, ALL_EDGE).somePathsSatisfyPredicate()).isTrue();

    // Make sure we are not doing more traversals than we have to.
    assertThat(p.count).isEqualTo(4);
  }

  @Test
  public void testNonInclusive() {
    // No Paths between nodes, by definition, always false.
    DiGraph<String, String> g = LinkedDirectedGraph.create();
    g.createDirectedGraphNode("a");
    g.createDirectedGraphNode("b");
    g.createDirectedGraphNode("c");
    g.connect("a", "-", "b");
    g.connect("b", "-", "c");
    assertThat(
            createNonInclusiveTest(g, "a", "b", Predicates.equalTo("a"), ALL_EDGE)
                .somePathsSatisfyPredicate())
        .isFalse();
    assertThat(
            createNonInclusiveTest(g, "a", "b", Predicates.equalTo("b"), ALL_EDGE)
                .somePathsSatisfyPredicate())
        .isFalse();
    assertThat(
            createNonInclusiveTest(g, "a", "c", Predicates.equalTo("b"), ALL_EDGE)
                .somePathsSatisfyPredicate())
        .isTrue();
  }

  private static <N, E> void assertGood(CheckPathsBetweenNodes<N, E> test) {
    assertThat(test.allPathsSatisfyPredicate()).isTrue();
  }

  private static <N, E> void assertBad(CheckPathsBetweenNodes<N, E> test) {
    assertThat(test.allPathsSatisfyPredicate()).isFalse();
  }

  private static CheckPathsBetweenNodes<String, String> createTest(
      DiGraph<String, String> graph,
      String entry,
      String exit,
      Predicate<String> nodePredicate,
      Predicate<DiGraphEdge<String, String>> edgePredicate) {
    return new CheckPathsBetweenNodes<>(graph,
        graph.getDirectedGraphNode(entry), graph.getDirectedGraphNode(exit),
        nodePredicate, edgePredicate);
  }

  private static CheckPathsBetweenNodes<String, String>
      createNonInclusiveTest(
        DiGraph<String, String> graph,
        String entry,
        String exit,
        Predicate<String> nodePredicate,
        Predicate<DiGraphEdge<String, String>> edgePredicate) {
    return new CheckPathsBetweenNodes<>(graph,
        graph.getDirectedGraphNode(entry), graph.getDirectedGraphNode(exit),
        nodePredicate, edgePredicate, false);
  }

  private static Predicate<DiGraphEdge<String, String>> edgeIs(final Object val) {
    return input -> input.getValue().equals(val);
  }

  private static class CountingPredicate<T> implements Predicate<T> {

    private int count = 0;
    private final Predicate<T> delegate;

    private CountingPredicate(Predicate<T> delegate) {
      this.delegate = delegate;
    }
    @Override
    public boolean apply(T input) {
      count++;
      return delegate.apply(input);
    }
  }
}
