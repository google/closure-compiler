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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Iterator;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit test for the {@link StandardUnionFind} data structure.
 *
 */
@RunWith(JUnit4.class)
public final class StandardUnionFindTest {
  private StandardUnionFind<String> union;

  @Before
  public void setUp() {
    union = new StandardUnionFind<>();
  }

  @Test
  public void testEmpty() {
    assertThat(union.allEquivalenceClasses()).isEmpty();
  }

  @Test
  public void testAdd() {
    union.add("foo");
    union.add("bar");
    assertThat(union.find("foo")).isNotNull();
    assertThat(union.allEquivalenceClasses()).hasSize(2);
  }

  @Test
  public void testUnion() {
    union.union("A", "B");
    union.union("C", "D");
    assertThat(union.find("B")).isEqualTo(union.find("A"));
    assertThat(union.find("D")).isEqualTo(union.find("C"));
    assertThat(union.find("A").equals(union.find("D"))).isFalse();
  }

  @Test
  public void testSetSize() {
    union.union("A", "B");
    union.union("B", "C");
    union.union("D", "E");
    union.union("F", "F");

    assertThat(union.findAll("A")).hasSize(3);
    assertThat(union.findAll("B")).hasSize(3);
    assertThat(union.findAll("C")).hasSize(3);
    assertThat(union.findAll("D")).hasSize(2);
    assertThat(union.findAll("F")).hasSize(1);
  }

  @Test
  public void testFind() {
    union.add("A");
    union.add("B");
    assertThat(union.find("A")).isEqualTo("A");
    assertThat(union.find("B")).isEqualTo("B");

    union.union("A", "B");
    assertThat(union.find("B")).isEqualTo(union.find("A"));

    try {
      union.find("Z");
      assertWithMessage("find() on unknown element should not be allowed.").fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testAllEquivalenceClasses() {
    union.union("A", "B");
    union.union("A", "B");
    union.union("B", "A");
    union.union("B", "C");
    union.union("D", "E");
    union.union("F", "F");

    ImmutableList<ImmutableSet<String>> classes = union.allEquivalenceClasses();
    assertThat(classes).containsExactly(
        ImmutableSet.of("A", "B", "C"), ImmutableSet.of("D", "E"), ImmutableSet.of("F"));
  }

  @Test
  public void testFindAll() {
    union.union("A", "B");
    union.union("A", "B");
    union.union("B", "A");
    union.union("D", "E");
    union.union("F", "F");

    Set<String> aSet = union.findAll("A");
    assertThat(aSet).containsExactly("A", "B");

    union.union("B", "C");
    assertThat(aSet).contains("C");
    assertThat(aSet).hasSize(3);

    try {
      union.findAll("Z");
      assertWithMessage("findAll() on unknown element should not be allowed.").fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testFindAllIterator() {
    union.union("A", "B");
    union.union("B", "C");
    union.union("A", "B");
    union.union("D", "E");

    Set<String> aSet = union.findAll("A");
    Iterator<String> aIter = aSet.iterator();
    assertThat(aIter.hasNext()).isTrue();
    assertThat(aIter.next()).isEqualTo("A");
    assertThat(aIter.next()).isEqualTo("B");
    assertThat(aIter.next()).isEqualTo("C");
    assertThat(aIter.hasNext()).isFalse();

    Set<String> dSet = union.findAll("D");
    Iterator<String> dIter = dSet.iterator();
    assertThat(dIter.hasNext()).isTrue();
    assertThat(dIter.next()).isEqualTo("D");
    assertThat(dIter.next()).isEqualTo("E");
    assertThat(dIter.hasNext()).isFalse();
  }

  @Test
  public void testFindAllSize() {
    union.union("A", "B");
    union.union("B", "C");
    assertThat(union.findAll("A")).hasSize(3);
    assertThat(union.findAll("B")).hasSize(3);
    assertThat(union.findAll("C")).hasSize(3);
    union.union("D", "E");
    assertThat(union.findAll("C")).hasSize(3);
    assertThat(union.findAll("D")).hasSize(2);
    union.union("B", "E");
    assertThat(union.findAll("C")).hasSize(5);
    assertThat(union.findAll("D")).hasSize(5);
  }

  @Test
  public void testElements() {
    union.union("A", "B");
    union.union("B", "C");
    union.union("A", "B");
    union.union("D", "E");

    Set<String> elements = union.elements();
    assertThat(elements).isEqualTo(ImmutableSet.of("A", "B", "C", "D", "E"));
    assertThat(elements).doesNotContain("F");
  }

  @Test
  public void testCopy() {
    union.union("A", "B");
    union.union("B", "Z");
    union.union("X", "Y");
    UnionFind<String> copy = new StandardUnionFind<>(union);
    assertThat(copy.findAll("Z")).containsExactly("A", "B", "Z");
    assertThat(copy.findAll("X")).containsExactly("X", "Y");
  }

  @Test
  public void testChangesToCopyDontAffectOriginal() {
    union.union("A", "B");
    union.union("X", "Y");
    union.union("A", "C");
    UnionFind<String> copy = new StandardUnionFind<>(union);
    copy.union("A", "D");
    assertThat(copy.findAll("D")).containsExactly("A", "B", "C", "D");
    assertThat(union.findAll("A")).containsExactly("A", "B", "C");
    assertThat(copy.findAll("X")).containsExactly("X", "Y");
    try {
      union.findAll("D");
      assertWithMessage("D has been inserted to the original collection").fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void testCheckEquivalent() {
    union.union("A", "B");
    union.add("C");
    assertThat(union.areEquivalent("A", "B")).isTrue();
    assertThat(union.areEquivalent("C", "A")).isFalse();
    assertThat(union.areEquivalent("C", "B")).isFalse();
    try {
      union.areEquivalent("A", "F");
      throw new AssertionError();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }
}
