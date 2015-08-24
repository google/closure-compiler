/*
 * Copyright 2010 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.deps.SortedDependencies.CircularDependencyException;

import junit.framework.TestCase;

import java.util.List;

/**
 * Tests for {@link SortedDependencies}
 * @author nicksantos@google.com (Nick Santos)
 */
public abstract class SortedDependenciesTestHelper extends TestCase {

  public void testSort() throws Exception {
    SimpleDependencyInfo a = new SimpleDependencyInfo(
        "a", "a", provides(), requires("b", "c"), false);
    SimpleDependencyInfo b = new SimpleDependencyInfo(
        "b", "b", provides("b"), requires("d"), false);
    SimpleDependencyInfo c = new SimpleDependencyInfo(
        "c", "c", provides("c"), requires("d"), false);
    SimpleDependencyInfo d = new SimpleDependencyInfo(
        "d", "d", provides("d"), requires(), false);
    SimpleDependencyInfo e = new SimpleDependencyInfo(
        "e", "e", provides("e"), requires(), false);

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
    SimpleDependencyInfo ab = new SimpleDependencyInfo(
        "ab", "ab", provides("a", "b"), requires("d", "f"), false);
    SimpleDependencyInfo c = new SimpleDependencyInfo(
        "c", "c", provides("c"), requires("h"), false);
    SimpleDependencyInfo d = new SimpleDependencyInfo(
        "d", "d", provides("d"), requires("e", "f"), false);
    SimpleDependencyInfo ef = new SimpleDependencyInfo(
        "ef", "ef", provides("e", "f"), requires("g", "c"), false);
    SimpleDependencyInfo g = new SimpleDependencyInfo(
        "g", "g", provides("g"), requires(), false);
    SimpleDependencyInfo hi = new SimpleDependencyInfo(
        "hi", "hi", provides("h", "i"), requires(), false);

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
    SimpleDependencyInfo a = new SimpleDependencyInfo(
        "a", "a", provides("a"), requires("c"), false);
    SimpleDependencyInfo b = new SimpleDependencyInfo(
        "b", "b", provides("b"), requires("a"), false);
    SimpleDependencyInfo c = new SimpleDependencyInfo(
        "c", "c", provides("c"), requires("b"), false);

    assertOrderOrException(
        ImmutableList.of(a, b, c), ImmutableList.of(b, c, a), "c -> b -> a -> c");
  }

  public void testSort4() throws Exception {
    // Check the degenerate case.
    SimpleDependencyInfo a = new SimpleDependencyInfo(
        "a", "a", provides("a"), requires("a"), false);
    assertSortedDeps(
        ImmutableList.of(a),
        ImmutableList.of(a),
        ImmutableList.of(a));
  }

  public void testSort5() throws Exception {
    SimpleDependencyInfo a = new SimpleDependencyInfo(
        "a", "a", provides("a"), requires(), false);
    SimpleDependencyInfo b = new SimpleDependencyInfo(
        "b", "b", provides("b"), requires(), false);
    SimpleDependencyInfo c = new SimpleDependencyInfo(
        "c", "c", provides("c"), requires(), false);

    assertSortedInputs(
        ImmutableList.of(a, b, c),
        ImmutableList.of(a, b, c));
    assertSortedInputs(
        ImmutableList.of(c, b, a),
        ImmutableList.of(c, b, a));
  }

  public void testSort6() {
    SimpleDependencyInfo a = new SimpleDependencyInfo(
        "gin", "gin", provides("gin"), requires("tonic"), false);
    SimpleDependencyInfo b = new SimpleDependencyInfo(
        "tonic", "tonic", provides("tonic"), requires("gin2"), false);
    SimpleDependencyInfo c = new SimpleDependencyInfo(
        "gin2", "gin2", provides("gin2"), requires("gin"), false);
    SimpleDependencyInfo d = new SimpleDependencyInfo(
        "gin3", "gin3", provides("gin3"), requires("gin"), false);

    assertOrderOrException(ImmutableList.of(a, b, c, d), ImmutableList.of(c, b, a, d),
        "tonic -> gin2 -> gin -> tonic");
  }

  public void testSort7() {
    SimpleDependencyInfo a = new SimpleDependencyInfo(
        "gin", "gin", provides("gin"), requires("tonic"), false);
    SimpleDependencyInfo b = new SimpleDependencyInfo(
        "tonic", "tonic", provides("tonic"), requires("gin"), false);
    SimpleDependencyInfo c = new SimpleDependencyInfo(
        "gin2", "gin2", provides("gin2"), requires("gin"), false);
    SimpleDependencyInfo d = new SimpleDependencyInfo(
        "gin3", "gin3", provides("gin3"), requires("gin"), false);

    assertOrderOrException(
        ImmutableList.of(a, b, c, d), ImmutableList.of(b, a, c, d), "tonic -> gin -> tonic");
  }

  public void testSort8() {
    SimpleDependencyInfo a = new SimpleDependencyInfo(
        "A", "A", provides("A"), requires("B"), false);
    SimpleDependencyInfo b = new SimpleDependencyInfo(
        "B", "B", provides("B"), requires("C"), false);
    SimpleDependencyInfo c = new SimpleDependencyInfo(
        "C", "C", provides("C"), requires("D"), false);
    SimpleDependencyInfo d = new SimpleDependencyInfo(
        "D", "D", provides("D"), requires("A"), false);

    assertOrderOrException(
        ImmutableList.of(a, b, c, d), ImmutableList.of(d, c, b, a), "B -> C -> D -> A -> B");
  }

  public void testSort9() {
    SimpleDependencyInfo a = new SimpleDependencyInfo(
        "A", "A", provides("A"), requires("B"), false);
    SimpleDependencyInfo a2 = new SimpleDependencyInfo(
        "A", "A", provides("A"), requires("B1"), false);
    SimpleDependencyInfo b = new SimpleDependencyInfo(
        "B", "B", provides("B"), requires("C"), false);
    SimpleDependencyInfo c = new SimpleDependencyInfo(
        "C", "C", provides("C"), requires("E"), false);
    SimpleDependencyInfo d = new SimpleDependencyInfo(
        "D", "D", provides("D"), requires("A"), false);
    SimpleDependencyInfo e = new SimpleDependencyInfo(
        "B1", "B1", provides("B1"), requires("C1"), false);
    SimpleDependencyInfo f = new SimpleDependencyInfo(
        "C1", "C1", provides("C1"), requires("D1"), false);
    SimpleDependencyInfo g = new SimpleDependencyInfo(
        "D1", "D1", provides("D1"), requires("A"), false);

    assertOrderOrException(ImmutableList.of(a, a2, b, c, d, e, f, g),
        ImmutableList.of(c, b, a, g, f, e, a2, d), "B1 -> C1 -> D1 -> A -> B1");
  }

  public void testSort10() throws Exception {
    SimpleDependencyInfo a =
        new SimpleDependencyInfo("A", "A", provides("A"), requires("C"), false);
    SimpleDependencyInfo b = new SimpleDependencyInfo("B", "B", provides("B"), requires(), false);
    SimpleDependencyInfo c = new SimpleDependencyInfo("C", "C", provides("C"), requires(), false);

    SortedDependencies<SimpleDependencyInfo> sorted =
        createSortedDependencies(ImmutableList.of(a, b, c));

    // The dependency sorting implementations have different results for this input.
    if (handlesCycles()) {
      assertThat(sorted.getSortedList()).isEqualTo(ImmutableList.of(c, a, b));
    } else {
      assertThat(sorted.getSortedList()).isEqualTo(ImmutableList.of(b, c, a));
    }
  }

  private void assertSortedInputs(
      List<SimpleDependencyInfo> expected,
      List<SimpleDependencyInfo> shuffled) throws Exception {
    SortedDependencies<SimpleDependencyInfo> sorted = createSortedDependencies(shuffled);
    assertThat(sorted.getSortedList()).isEqualTo(expected);
  }

  private void assertSortedDeps(
      List<SimpleDependencyInfo> expected,
      List<SimpleDependencyInfo> shuffled,
      List<SimpleDependencyInfo> roots) throws Exception {
    SortedDependencies<SimpleDependencyInfo> sorted = createSortedDependencies(shuffled);
    assertThat(sorted.getSortedDependenciesOf(roots)).isEqualTo(expected);
  }

  private void assertOrderOrException(ImmutableList<SimpleDependencyInfo> shuffle,
      ImmutableList<SimpleDependencyInfo> expected, String failMessage) {
    try {
      SortedDependencies<SimpleDependencyInfo> sorted = createSortedDependencies(shuffle);
      if (handlesCycles()) {
        assertThat(sorted.getSortedList()).isEqualTo(expected);
      } else {
        fail("expected exception");
      }
    } catch (CircularDependencyException e) {
      if (handlesCycles()) {
        fail("expected no exception");
      } else {
        assertThat(e.getMessage()).isEqualTo(failMessage);
      }
    }
  }

  private List<String> requires(String ... strings) {
    return ImmutableList.copyOf(strings);
  }

  private List<String> provides(String ... strings) {
    return ImmutableList.copyOf(strings);
  }

  public abstract SortedDependencies<SimpleDependencyInfo> createSortedDependencies(
      List<SimpleDependencyInfo> shuffled) throws CircularDependencyException;

  public abstract boolean handlesCycles();
}
