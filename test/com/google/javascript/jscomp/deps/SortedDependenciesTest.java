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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.deps.SortedDependencies.CircularDependencyException;

import junit.framework.TestCase;

import java.util.List;

/**
 * Tests for {@link SortedDependencies}
 * @author nicksantos@google.com (Nick Santos)
 */
public class SortedDependenciesTest extends TestCase {

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

    try {
      new SortedDependencies<>(
          Lists.newArrayList(a, b, c));
      fail("expected exception");
    } catch (CircularDependencyException e) {
      assertEquals("a -> a", e.getMessage());
    }
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

  private void assertSortedInputs(
      List<SimpleDependencyInfo> expected,
      List<SimpleDependencyInfo> shuffled) throws Exception {
    SortedDependencies<SimpleDependencyInfo> sorted =
        new SortedDependencies<>(shuffled);
    assertEquals(expected, sorted.getSortedList());
  }

  private void assertSortedDeps(
      List<SimpleDependencyInfo> expected,
      List<SimpleDependencyInfo> shuffled,
      List<SimpleDependencyInfo> roots) throws Exception {
    SortedDependencies<SimpleDependencyInfo> sorted =
        new SortedDependencies<>(shuffled);
    assertEquals(expected, sorted.getSortedDependenciesOf(roots));
  }

  private List<String> requires(String ... strings) {
    return Lists.newArrayList(strings);
  }

  private List<String> provides(String ... strings) {
    return Lists.newArrayList(strings);
  }
}
