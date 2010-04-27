/*
 * Copyright 2010 Google Inc.
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

import junit.framework.TestCase;

import java.util.List;

/**
 * Tests for {@link SortedDependencies}
 * @author nicksantos@google.com (Nick Santos)
 */
public class SortedDependenciesTest extends TestCase {

  public void testSort() {
    SimpleDependencyInfo a = new SimpleDependencyInfo(
        "a", "a", symbols(), symbols("b", "c"));
    SimpleDependencyInfo b = new SimpleDependencyInfo(
        "b", "b", symbols("b"), symbols("d"));
    SimpleDependencyInfo c = new SimpleDependencyInfo(
        "c", "c", symbols("c"), symbols("d"));
    SimpleDependencyInfo d = new SimpleDependencyInfo(
        "d", "d", symbols("d"), symbols());
    SimpleDependencyInfo e = new SimpleDependencyInfo(
        "e", "e", symbols("e"), symbols());
    SimpleDependencyInfo f = new SimpleDependencyInfo(
        "f", "f", symbols("f"), symbols());

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

  public void testSort2() {
    SimpleDependencyInfo ab = new SimpleDependencyInfo(
        "ab", "ab", symbols("a", "b"), symbols("d", "f"));
    SimpleDependencyInfo c = new SimpleDependencyInfo(
        "c", "c", symbols("c"), symbols("h"));
    SimpleDependencyInfo d = new SimpleDependencyInfo(
        "d", "d", symbols("d"), symbols("e", "f"));
    SimpleDependencyInfo ef = new SimpleDependencyInfo(
        "ef", "ef", symbols("e", "f"), symbols("g", "c"));
    SimpleDependencyInfo g = new SimpleDependencyInfo(
        "g", "g", symbols("g"), symbols());
    SimpleDependencyInfo hi = new SimpleDependencyInfo(
        "hi", "hi", symbols("h", "i"), symbols());

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

  private void assertSortedInputs(
      List<SimpleDependencyInfo> expected,
      List<SimpleDependencyInfo> shuffled) {
    SortedDependencies<SimpleDependencyInfo> sorted =
        new SortedDependencies<SimpleDependencyInfo>(shuffled);
    assertEquals(expected, sorted.getSortedList());
  }

  private void assertSortedDeps(
      List<SimpleDependencyInfo> expected,
      List<SimpleDependencyInfo> shuffled,
      List<SimpleDependencyInfo> roots) {
    SortedDependencies<SimpleDependencyInfo> sorted =
        new SortedDependencies<SimpleDependencyInfo>(shuffled);
    assertEquals(expected, sorted.getSortedDependenciesOf(roots));
  }

  private List<String> symbols(String ... strings) {
    return Lists.newArrayList(strings);
  }
}
