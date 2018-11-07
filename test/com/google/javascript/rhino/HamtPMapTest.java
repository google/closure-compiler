/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link HamtPMap}. */
@RunWith(JUnit4.class)
public class HamtPMapTest {
  @Test
  public void testEmpty() {
    PMap<String, Integer> map = HamtPMap.empty();
    assertThat(map.get("foo")).isNull();
    assertThat(map.values()).isEmpty();
    assertThat(map.isEmpty()).isTrue();
  }

  @Test
  public void testPlus() {
    PMap<String, Integer> empty = HamtPMap.empty();
    PMap<String, Integer> map = empty.plus("foo", 42);
    assertThat(map.get("foo")).isEqualTo(42);
    assertThat(map.values()).containsExactly(42);
    assertThat(map.isEmpty()).isFalse();
  }

  @Test
  public void testPlus_alreadyExistsReturnsSame() {
    PMap<String, Integer> empty = HamtPMap.empty();
    PMap<String, Integer> map = empty.plus("foo", 42);
    assertThat(map.plus("foo", 42)).isSameAs(map);
  }

  @Test
  public void testPlus_updatesExistingKey() {
    PMap<String, Integer> empty = HamtPMap.empty();
    PMap<String, Integer> map = empty.plus("foo", 42).plus("foo", 23);
    assertThat(map.get("foo")).isEqualTo(23);
    assertThat(map.values()).containsExactly(23);
    assertThat(map.isEmpty()).isFalse();
  }

  @Test
  public void testMinus() {
    PMap<String, Integer> map = HamtPMap.<String, Integer>empty().plus("foo", 42);
    PMap<String, Integer> empty = map.minus("foo");
    assertThat(empty.get("foo")).isNull();
    assertThat(empty.values()).isEmpty();
    assertThat(empty.isEmpty()).isTrue();
  }

  @Test
  public void testMinus_nonexistentKeyReturnsSame() {
    PMap<String, Integer> empty = HamtPMap.empty();
    PMap<String, Integer> map = empty.plus("foo", 42);
    assertThat(map.minus("bar")).isSameAs(map);
  }

  @Test
  public void testPlusAndMinus() {
    PMap<String, Integer> empty = HamtPMap.empty();
    PMap<String, Integer> map = empty.plus("foo", 42).plus("bar", 19).plus("baz", 12);
    assertThat(map.get("foo")).isEqualTo(42);
    assertThat(map.get("bar")).isEqualTo(19);
    assertThat(map.get("baz")).isEqualTo(12);
    assertThat(map.values()).containsExactly(12, 19, 42);
    assertThat(map.isEmpty()).isFalse();

    map = map.minus("bar");
    assertThat(map.get("foo")).isEqualTo(42);
    assertThat(map.get("bar")).isNull();
    assertThat(map.get("baz")).isEqualTo(12);
    assertThat(map.values()).containsExactly(12, 42);
    assertThat(map.isEmpty()).isFalse();

    map = map.minus("baz");
    assertThat(map.get("foo")).isEqualTo(42);
    assertThat(map.get("baz")).isNull();
    assertThat(map.values()).containsExactly(42);
    assertThat(map.isEmpty()).isFalse();

    map = map.minus("foo");
    assertThat(map.get("foo")).isNull();
    assertThat(map.values()).isEmpty();
    assertThat(map.isEmpty()).isTrue();
  }

  @Test
  public void testReconcile_customJoiner() {
    PMap<Integer, String> empty = HamtPMap.<Integer, String>empty();
    PMap<Integer, String> left =
        empty.plus(1, "1").plus(3, "3").plus(5, "5").plus(7, "7").plus(9, "9");
    PMap<Integer, String> right =
        empty.plus(3, "C").plus(4, "D").plus(5, "5").plus(6, "F").plus(7, "G");

    JoinExpectations<String, String> expectations =
        new JoinExpectations<String, String>()
            .expect("1", null, "1")
            .expect("3", "C", "c")
            .expect(null, "D", "D")
            .expect(null, "F", "F")
            .expect("7", "G", "g")
            .expect("9", null, "9");
    PMap<Integer, String> joined = left.reconcile(right, expectations);
    assertThat(joined.values()).containsExactly("1", "c", "D", "5", "F", "g", "9");
    expectations.verify();
  }

  @Test
  public void testReconcile_emptyMaps() {
    PMap<Integer, String> empty = HamtPMap.<Integer, String>empty();
    PMap<Integer, String> map =
        empty.plus(1, "1").plus(3, "3").plus(5, "5").plus(7, "7").plus(9, "9");

    PMap<Integer, String> joined =
        map.reconcile(
            empty,
            (a, b) -> {
              assertThat(b).isNull();
              return a;
            });
    assertThat(joined.equivalent(map, Objects::equals)).isTrue();

    joined =
        empty.reconcile(
            map,
            (a, b) -> {
              assertThat(a).isNull();
              return b;
            });
    assertThat(joined.equivalent(map, Objects::equals)).isTrue();
  }

  @Test
  public void testReconcile_differentSizes() {
    PMap<Integer, Integer> left = build(1, 6, 2, 19, 4, 23, 5, 8, 42, 12, 18, 33);
    PMap<Integer, Integer> right =
        build().plus(3, 1).plus(4, 1).plus(8, 1).plus(19, 1).plus(25, 1).plus(42, 1);
    PMap<Integer, Integer> expected =
        left.plus(3, 1).plus(4, 5).plus(8, 9).plus(19, 20).plus(25, 1).plus(42, 43);

    PMap<Integer, Integer> joined =
        left.reconcile(right, (a, b) -> a == null ? b : b == null ? a : a + b);
    assertThat(joined.equivalent(expected, Objects::equals)).isTrue();
  }

  @Test
  public void testReconcile_delete() {
    PMap<Integer, Integer> left = build(1, 3, 5, 7);
    PMap<Integer, Integer> right = build(2, 4, 6, 8);

    PMap<Integer, Integer> joined = left.reconcile(right, (a, b) -> null);
    assertThat(joined.equivalent(build(), Objects::equals)).isTrue();
    assertThat(joined.isEmpty()).isTrue();
  }

  @Test
  public void testEquivalent_equivalent() {
    PMap<Integer, Integer> left = build(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024);
    PMap<Integer, Integer> right = build(2, 512, 32, 8, 1024, 1, 64, 4, 16, 256, 128);

    assertThat(left.equivalent(right, Objects::equals)).isTrue();
    assertThat(right.equivalent(left, Objects::equals)).isTrue();
  }

  @Test
  public void testEquivalent_oneDifferentValue() {
    // Insertion order doesn't matter
    PMap<Integer, Integer> left1 = build(1, 3, 5, 7);
    PMap<Integer, Integer> left2 = build(7, 3, 5, 1);
    PMap<Integer, Integer> right1 = build().plus(1, 2).plus(3, 3).plus(5, 5).plus(7, 7);
    PMap<Integer, Integer> right2 = build(1, 3, 5).plus(7, 8);

    assertThat(left1.equivalent(right1, Objects::equals)).isFalse();
    assertThat(right1.equivalent(left1, Objects::equals)).isFalse();
    assertThat(left2.equivalent(right1, Objects::equals)).isFalse();
    assertThat(right1.equivalent(left2, Objects::equals)).isFalse();
    assertThat(left1.equivalent(right2, Objects::equals)).isFalse();
    assertThat(right2.equivalent(left1, Objects::equals)).isFalse();
    assertThat(left2.equivalent(right2, Objects::equals)).isFalse();
    assertThat(right2.equivalent(left2, Objects::equals)).isFalse();
  }

  @Test
  public void testEquivalent_oneDifferentKey() {
    PMap<Integer, Integer> left1 = build().plus(2, 1).plus(3, 3).plus(5, 5).plus(7, 7);
    PMap<Integer, Integer> left2 = build(1, 3, 5).plus(7, 8);
    PMap<Integer, Integer> right = build(1, 3, 5, 7);

    assertThat(left1.equivalent(right, Objects::equals)).isFalse();
    assertThat(right.equivalent(left1, Objects::equals)).isFalse();
    assertThat(left2.equivalent(right, Objects::equals)).isFalse();
    assertThat(right.equivalent(left2, Objects::equals)).isFalse();
  }

  @Test
  public void testEquivalent_oneMissingKey() {
    PMap<Integer, Integer> left = build(1, 5, 4, 3, 9);
    PMap<Integer, Integer> right = build(1, 5, 3, 9);

    assertThat(left.equivalent(right, Objects::equals)).isFalse();
    assertThat(right.equivalent(left, Objects::equals)).isFalse();
  }

  @Test
  public void testEquivalent_equalsWithCustomEquivalence() {
    PMap<String, String> empty = HamtPMap.empty();
    PMap<String, String> left = empty.plus("abc", "AAa").plus("def", "Ddd").plus("ghi", "ggG");
    PMap<String, String> right = empty.plus("abc", "aAA").plus("def", "ddD").plus("ghi", "GGG");

    assertThat(
            left.equivalent(
                right,
                new PMap.BiPredicate<String, String>() {
                  @Override
                  public boolean test(String left, String right) {
                    return left.toLowerCase().equals(right.toLowerCase());
                  }
                }))
        .isTrue();
  }

  @Test
  public void testEquivalent_notEquivalentShortCircuits() {
    PMap<Integer, String> empty = HamtPMap.empty();
    PMap<Integer, String> left = empty.plus(1, "1").plus(2, "2");
    PMap<Integer, String> right = empty.plus(1, "3").plus(2, "4");

    int[] calls = new int[] { 0 };
    assertThat(
            left.equivalent(
                right,
                new PMap.BiPredicate<String, String>() {
                  @Override
                  public boolean test(String left, String right) {
                    calls[0]++;
                    return false;
                  }
                }))
        .isFalse();
    assertThat(calls[0]).isEqualTo(1);
  }

  private static class JoinExpectations<T, V> implements PMap.BiFunction<T, T, V> {
    private final Table<Optional<T>, Optional<T>, Optional<V>> table = HashBasedTable.create();

    @Override
    public V apply(T left, T right) {
      if (!table.contains(Optional.ofNullable(left), Optional.ofNullable(right))) {
        assertWithMessage(
                "Unexpected invocation of JoinExpectation for (" + left + ", " + right + ")")
            .fail();
      }
      return table.remove(Optional.ofNullable(left), Optional.ofNullable(right)).orElse(null);
    }

    JoinExpectations<T, V> expect(T left, T right, V result) {
      table.put(Optional.ofNullable(left), Optional.ofNullable(right), Optional.ofNullable(result));
      return this;
    }

    void verify() {
      if (!table.isEmpty()) {
        assertWithMessage("Missing invocations of JoinExpectation: " + table).fail();
      }
    }
  }

  @Test
  public void testIntegration() {
    TreeSet<Integer> ref = new TreeSet<>();
    PMap<Integer, String> map = HamtPMap.empty();

    // Note: multiplying by 43 produces every nonzero integer mod 127.
    for (int i = 17; ref.add(i); i = (i * 43) % 127) {
      map = map.plus(i, String.valueOf(i));
      ((HamtPMap<?, ?>) map).assertCorrectStructure();
      assertThat(map.isEmpty()).isEqualTo(ref.isEmpty());
      for (int j = 1; j < 127; j++) {
        assertThat(map.get(j)).isEqualTo(ref.contains(j) ? String.valueOf(j) : null);
        if (ref.contains(j)) {
          assertThat(map.plus(j, String.valueOf(j))).isSameAs(map);
        } else {
          assertThat(map.minus(j)).isSameAs(map);
        }
      }
      assertThat(map.values()).containsExactlyElementsIn(Iterables.transform(ref, String::valueOf));
    }
    assertThat(ref).hasSize(126); // (quick check that we actually did add everything)

    // Note: multiplying by 39 produces every nonzero integer mod 127 in a different order.
    for (int i = 12; ref.remove(i); i = (i * 39) % 127) {
      map = map.minus(i);
      ((HamtPMap<?, ?>) map).assertCorrectStructure();
      assertThat(map.isEmpty()).isEqualTo(ref.isEmpty());
      for (int j = 1; j < 127; j++) {
        assertThat(map.get(j)).isEqualTo(ref.contains(j) ? String.valueOf(j) : null);
        if (ref.contains(j)) {
          assertThat(map.plus(j, String.valueOf(j))).isSameAs(map);
        } else {
          assertThat(map.minus(j)).isSameAs(map);
        }
      }
      assertThat(map.values()).containsExactlyElementsIn(Iterables.transform(ref, String::valueOf));
    }
    assertThat(ref).isEmpty();
    assertThat(map).isSameAs(HamtPMap.empty());
  }

  private PMap<Integer, Integer> build(int... values) {
    PMap<Integer, Integer> map = HamtPMap.<Integer, Integer>empty();
    for (int value : values) {
      map = map.plus(value, value);
      ((HamtPMap<?, ?>) map).assertCorrectStructure();
    }
    return map;
  }
}
