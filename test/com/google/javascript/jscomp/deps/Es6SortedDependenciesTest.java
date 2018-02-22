/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.deps;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests for {@link Es6SortedDependencies}
 * @author nicksantos@google.com (Nick Santos)
 * @author stalcup@google.com (John Stalcup)
 */
public class Es6SortedDependenciesTest extends TestCase {
  private static SortedDependencies<SimpleDependencyInfo> createSortedDependencies(
      List<SimpleDependencyInfo> shuffled) {
    return new Es6SortedDependencies<>(shuffled);
  }

  public void testSort() throws Exception {
    SimpleDependencyInfo a = SimpleDependencyInfo.builder("a", "a")
        .setRequires("b", "c")
        .build();
    SimpleDependencyInfo b = SimpleDependencyInfo.builder("b", "b")
        .setProvides("b")
        .setRequires("d")
        .build();
    SimpleDependencyInfo c = SimpleDependencyInfo.builder("c", "c")
        .setProvides("c")
        .setRequires("d")
        .build();
    SimpleDependencyInfo d = SimpleDependencyInfo.builder("d", "d")
        .setProvides("d")
        .build();
    SimpleDependencyInfo e = SimpleDependencyInfo.builder("e", "e")
        .setProvides("e")
        .build();

    assertSortedInputs(
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(a, b, c, d));
    assertSortedInputs(
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(d, b, c, a));
    assertSortedInputs(
        ImmutableList.of(d, c, b, a),
        ImmutableList.of(d, c, b, a));
    assertSortedInputs(
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(d, a, b, c));

    assertSortedDeps(
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(a));
    assertSortedDeps(
        ImmutableList.of(d, c),
        ImmutableList.of(d, c, b, a),
        ImmutableList.of(c));
    assertSortedDeps(
        ImmutableList.of(d),
        ImmutableList.of(d, c, b, a),
        ImmutableList.of(d));

    try {
      assertSortedDeps(
          ImmutableList.<SimpleDependencyInfo>of(),
          ImmutableList.of(a, b, c, d),
          ImmutableList.of(e));
      fail("Expected an exception");
    } catch (IllegalArgumentException expected) {}
  }

  public void testSort2() throws Exception {
    SimpleDependencyInfo ab = SimpleDependencyInfo.builder("ab", "ab")
        .setProvides("a", "b")
        .setRequires("d", "f")
        .build();
    SimpleDependencyInfo c = SimpleDependencyInfo.builder("c", "c")
        .setProvides("c")
        .setRequires("h")
        .build();
    SimpleDependencyInfo d = SimpleDependencyInfo.builder("d", "d")
        .setProvides("d")
        .setRequires("e", "f")
        .build();
    SimpleDependencyInfo ef = SimpleDependencyInfo.builder("ef", "ef")
        .setProvides("e", "f")
        .setRequires("g", "c")
        .build();
    SimpleDependencyInfo g = SimpleDependencyInfo.builder("g", "g")
        .setProvides("g")
        .build();
    SimpleDependencyInfo hi = SimpleDependencyInfo.builder("hi", "hi")
        .setProvides("h", "i")
        .build();

    assertSortedInputs(
        ImmutableList.of(g, hi, c, ef, d, ab),
        ImmutableList.of(ab, c, d, ef, g, hi));

    assertSortedDeps(
        ImmutableList.of(g),
        ImmutableList.of(ab, c, d, ef, g, hi),
        ImmutableList.of(g));
    assertSortedDeps(
        ImmutableList.of(g, hi, c, ef, d),
        ImmutableList.of(ab, c, d, ef, g, hi),
        ImmutableList.of(d, hi));
  }

  public void testSort3() {
    SimpleDependencyInfo a = SimpleDependencyInfo.builder("a", "a")
        .setProvides("a")
        .setRequires("c")
        .build();
    SimpleDependencyInfo b = SimpleDependencyInfo.builder("b", "b")
        .setProvides("b")
        .setRequires("a")
        .build();
    SimpleDependencyInfo c = SimpleDependencyInfo.builder("c", "c")
        .setProvides("c")
        .setRequires("b")
        .build();

    assertOrder(
        ImmutableList.of(a, b, c), ImmutableList.of(b, c, a));
  }

  public void testSort4() throws Exception {
    // Check the degenerate case.
    SimpleDependencyInfo a = SimpleDependencyInfo.builder("a", "a")
        .setProvides("a")
        .setRequires("a")
        .build();
    assertSortedDeps(
        ImmutableList.of(a),
        ImmutableList.of(a),
        ImmutableList.of(a));
  }

  public void testSort5() throws Exception {
    SimpleDependencyInfo a = SimpleDependencyInfo.builder("a", "a")
        .setProvides("a")
        .build();
    SimpleDependencyInfo b = SimpleDependencyInfo.builder("b", "b")
        .setProvides("b")
        .build();
    SimpleDependencyInfo c = SimpleDependencyInfo.builder("c", "c")
        .setProvides("c")
        .build();

    assertSortedInputs(
        ImmutableList.of(a, b, c),
        ImmutableList.of(a, b, c));
    assertSortedInputs(
        ImmutableList.of(c, b, a),
        ImmutableList.of(c, b, a));
  }

  public void testSort6() {
    SimpleDependencyInfo a = SimpleDependencyInfo.builder("gin", "gin")
        .setProvides("gin")
        .setRequires("tonic")
        .build();
    SimpleDependencyInfo b = SimpleDependencyInfo.builder("tonic", "tonic")
        .setProvides("tonic")
        .setRequires("gin2")
        .build();
    SimpleDependencyInfo c = SimpleDependencyInfo.builder("gin2", "gin2")
        .setProvides("gin2")
        .setRequires("gin")
        .build();
    SimpleDependencyInfo d = SimpleDependencyInfo.builder("gin3", "gin3")
        .setProvides("gin3")
        .setRequires("gin")
        .build();

    assertOrder(ImmutableList.of(a, b, c, d), ImmutableList.of(c, b, a, d));
  }

  public void testSort7() {
    SimpleDependencyInfo a = SimpleDependencyInfo.builder("gin", "gin")
        .setProvides("gin")
        .setRequires("tonic")
        .build();
    SimpleDependencyInfo b = SimpleDependencyInfo.builder("tonic", "tonic")
        .setProvides("tonic")
        .setRequires("gin")
        .build();
    SimpleDependencyInfo c = SimpleDependencyInfo.builder("gin2", "gin2")
        .setProvides("gin2")
        .setRequires("gin")
        .build();
    SimpleDependencyInfo d = SimpleDependencyInfo.builder("gin3", "gin3")
        .setProvides("gin3")
        .setRequires("gin")
        .build();

    assertOrder(
        ImmutableList.of(a, b, c, d), ImmutableList.of(b, a, c, d));
  }

  public void testSort8() {
    SimpleDependencyInfo a = SimpleDependencyInfo.builder("A", "A")
        .setProvides("A")
        .setRequires("B")
        .build();
    SimpleDependencyInfo b = SimpleDependencyInfo.builder("B", "B")
        .setProvides("B")
        .setRequires("C")
        .build();
    SimpleDependencyInfo c = SimpleDependencyInfo.builder("C", "C")
        .setProvides("C")
        .setRequires("D")
        .build();
    SimpleDependencyInfo d = SimpleDependencyInfo.builder("D", "D")
        .setProvides("D")
        .setRequires("A")
        .build();

    assertOrder(
        ImmutableList.of(a, b, c, d), ImmutableList.of(d, c, b, a));
  }

  public void testSort9() {
    SimpleDependencyInfo a = SimpleDependencyInfo.builder("A", "A")
        .setProvides("A")
        .setRequires("B")
        .build();
    SimpleDependencyInfo a2 = SimpleDependencyInfo.builder("A", "A")
        .setProvides("A")
        .setRequires("B1")
        .build();
    SimpleDependencyInfo b = SimpleDependencyInfo.builder("B", "B")
        .setProvides("B")
        .setRequires("C")
        .build();
    SimpleDependencyInfo c = SimpleDependencyInfo.builder("C", "C")
        .setProvides("C")
        .setRequires("E")
        .build();
    SimpleDependencyInfo d = SimpleDependencyInfo.builder("D", "D")
        .setProvides("D")
        .setRequires("A")
        .build();
    SimpleDependencyInfo e = SimpleDependencyInfo.builder("B1", "B1")
        .setProvides("B1")
        .setRequires("C1")
        .build();
    SimpleDependencyInfo f = SimpleDependencyInfo.builder("C1", "C1")
        .setProvides("C1")
        .setRequires("D1")
        .build();
    SimpleDependencyInfo g = SimpleDependencyInfo.builder("D1", "D1")
        .setProvides("D1")
        .setRequires("A")
        .build();

    assertOrder(ImmutableList.of(a, a2, b, c, d, e, f, g),
        ImmutableList.of(c, b, a, g, f, e, a2, d));
  }

  public void testSort10() throws Exception {
    SimpleDependencyInfo a =
        SimpleDependencyInfo.builder("A", "A")
            .setProvides("A")
            .setRequires("C")
            .build();
    SimpleDependencyInfo b = SimpleDependencyInfo.builder("B", "B")
        .setProvides("B")
        .build();
    SimpleDependencyInfo c = SimpleDependencyInfo.builder("C", "C")
        .setProvides("C")
        .build();

    SortedDependencies<SimpleDependencyInfo> sorted =
        createSortedDependencies(ImmutableList.of(a, b, c));

    assertThat(sorted.getSortedList()).containsExactly(c, a, b).inOrder();
  }

  private static void assertSortedInputs(
      List<SimpleDependencyInfo> expected, List<SimpleDependencyInfo> shuffled) throws Exception {
    SortedDependencies<SimpleDependencyInfo> sorted = createSortedDependencies(shuffled);
    assertThat(sorted.getSortedList()).isEqualTo(expected);
  }

  private static void assertSortedDeps(
      List<SimpleDependencyInfo> expected,
      List<SimpleDependencyInfo> shuffled,
      List<SimpleDependencyInfo> roots)
      throws Exception {
    SortedDependencies<SimpleDependencyInfo> sorted = createSortedDependencies(shuffled);
    assertThat(sorted.getSortedDependenciesOf(roots)).isEqualTo(expected);
  }

  private static void assertOrder(
      ImmutableList<SimpleDependencyInfo> shuffle, ImmutableList<SimpleDependencyInfo> expected) {
    SortedDependencies<SimpleDependencyInfo> sorted = createSortedDependencies(shuffle);
    assertThat(sorted.getSortedList()).isEqualTo(expected);
  }

  private static ImmutableList<String> requires(String... strings) {
    return ImmutableList.copyOf(strings);
  }

  private static ImmutableList<String> provides(String... strings) {
    return ImmutableList.copyOf(strings);
  }
}
