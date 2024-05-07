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

package com.google.javascript.jscomp.base;

import static com.google.common.truth.Truth.assertThat;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LinkedIdentityHashMapTest {

  private static final Key KEY_0 = new Key();
  private static final Key KEY_1 = new Key();
  private static final Key KEY_2 = new Key();

  @BeforeClass
  public static void validateKeys() {
    assertThat(KEY_0).isEqualTo(KEY_1);
    assertThat(KEY_0).isEqualTo(KEY_2);
    assertThat(KEY_1).isEqualTo(KEY_2);

    assertThat(KEY_0.hashCode()).isEqualTo(0);
    assertThat(KEY_1.hashCode()).isEqualTo(0);
    assertThat(KEY_2.hashCode()).isEqualTo(0);

    assertThat(KEY_0).isNotSameInstanceAs(KEY_1);
    assertThat(KEY_0).isNotSameInstanceAs(KEY_2);
    assertThat(KEY_1).isNotSameInstanceAs(KEY_2);
  }

  @Test
  public void entries_keyedByIdentity() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();
    assertThat(map.get(KEY_1)).isNull();

    map.put(KEY_0, "hello");
    assertThat(map.get(KEY_0)).isEqualTo("hello");
    assertThat(map.get(KEY_1)).isNull();

    map.put(KEY_1, "world");
    assertThat(map.get(KEY_0)).isEqualTo("hello");
    assertThat(map.get(KEY_1)).isEqualTo("world");
  }

  @Test
  public void entries_allowNullValues() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();

    map.put(KEY_0, null);
    assertThat(map.get(KEY_0)).isNull();
  }

  @Test
  public void entries_canBeOverwritten() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();

    map.put(KEY_0, "hello");
    assertThat(map.get(KEY_0)).isEqualTo("hello");

    map.put(KEY_0, "world");
    assertThat(map.get(KEY_0)).isEqualTo("world");

    map.put(KEY_0, null);
    assertThat(map.get(KEY_0)).isEqualTo(null);
  }

  @Test
  public void put_returnsPreviousValue() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();

    assertThat(map.put(KEY_0, "hello")).isNull();
    assertThat(map.put(KEY_0, "world")).isEqualTo("hello");
    assertThat(map.put(KEY_0, null)).isEqualTo("world");
    assertThat(map.put(KEY_0, "hello")).isNull();
  }

  @Test
  public void computeIfAbsent_setsWhenNoEntry_returnsCurrentValue() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();

    assertThat(map.computeIfAbsent(KEY_0, (k) -> "hello")).isEqualTo("hello");
    assertThat(map.get(KEY_0)).isEqualTo("hello");
  }

  @Test
  public void computeIfAbsent_setsWhenNullEntry_returnsCurrentValue() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();

    map.put(KEY_0, "world");
    map.put(KEY_0, null);

    assertThat(map.computeIfAbsent(KEY_0, (k) -> "hello")).isEqualTo("hello");
    assertThat(map.get(KEY_0)).isEqualTo("hello");
  }

  @Test
  public void computeIfAbsent_noopWhenEntry_returnsCurrentValue() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();

    map.put(KEY_0, "world");

    assertThat(map.computeIfAbsent(KEY_0, this::throwOnCall)).isEqualTo("world");
    assertThat(map.get(KEY_0)).isEqualTo("world");
  }

  @Test
  public void computeIfAbsent_passesKey() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();

    map.computeIfAbsent(
        KEY_0,
        (k) -> {
          assertThat(k).isSameInstanceAs(KEY_0);
          return "hello";
        });
    assertThat(map.get(KEY_0)).isEqualTo("hello");
  }

  @Test
  public void merge_setsWhenNoEntry_returnsCurrentValue() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();

    assertThat(map.merge(KEY_0, "hello", this::throwOnCall)).isEqualTo("hello");
    assertThat(map.get(KEY_0)).isEqualTo("hello");
  }

  @Test
  public void merge_setsWhenNullEntry_returnsCurrentValue() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();

    map.put(KEY_0, "world");
    map.put(KEY_0, null);

    assertThat(map.merge(KEY_0, "hello", this::throwOnCall)).isEqualTo("hello");
    assertThat(map.get(KEY_0)).isEqualTo("hello");
  }

  @Test
  public void merge_mergesExistingValue_returnsCurrentValue() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();

    map.put(KEY_0, "hello");

    assertThat(map.merge(KEY_0, "world", (v0, v1) -> v0 + " " + v1)).isEqualTo("hello world");
    assertThat(map.get(KEY_0)).isEqualTo("hello world");
  }

  @Test
  public void forEach_noopOnEmpty() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();

    map.forEach(this::throwOnCall);
  }

  @Test
  public void forEach_iteratesInInsertionOrder() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();
    assertThat(map.get(KEY_1)).isNull();
    assertThat(map.get(KEY_2)).isNull();

    map.put(KEY_1, "hello");
    map.put(KEY_0, "world");
    map.put(KEY_2, "great");

    ArrayList<Key> keys = new ArrayList<>();
    ArrayList<String> values = new ArrayList<>();
    map.forEach(
        (k, v) -> {
          keys.add(k);
          values.add(v);
        });

    assertThat(keys).containsExactly(KEY_1, KEY_0, KEY_2).inOrder();
    assertThat(values).containsExactly("hello", "world", "great").inOrder();
  }

  @Test
  public void forEach_iteratesInInsertionOrder_afterOverwrite() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();
    assertThat(map.get(KEY_1)).isNull();
    assertThat(map.get(KEY_2)).isNull();

    map.put(KEY_1, "hello");
    map.put(KEY_0, "world");
    map.put(KEY_2, "great");

    map.put(KEY_2, "hello");
    map.put(KEY_0, "great");
    map.put(KEY_1, "world");

    ArrayList<Key> keys = new ArrayList<>();
    ArrayList<String> values = new ArrayList<>();
    map.forEach(
        (k, v) -> {
          keys.add(k);
          values.add(v);
        });

    assertThat(keys).containsExactly(KEY_1, KEY_0, KEY_2).inOrder();
    assertThat(values).containsExactly("world", "great", "hello").inOrder();
  }

  @Test
  public void forEach_includesNullValues() {
    LinkedIdentityHashMap<Key, String> map = new LinkedIdentityHashMap<>();
    assertThat(map.get(KEY_0)).isNull();
    assertThat(map.get(KEY_1)).isNull();

    map.put(KEY_0, null);

    map.put(KEY_1, "hello");
    map.put(KEY_1, null);

    ArrayList<Key> keys = new ArrayList<>();
    ArrayList<String> values = new ArrayList<>();
    map.forEach(
        (k, v) -> {
          keys.add(k);
          values.add(v);
        });

    assertThat(keys).containsExactly(KEY_0, KEY_1).inOrder();
    assertThat(values).containsExactly((String) null, (String) null).inOrder();
  }

  @CanIgnoreReturnValue
  @SuppressWarnings("TypeParameterUnusedInFormals")
  private <R, P> R throwOnCall(P arg) {
    throw new AssertionError(String.format("Unexpected call with arg '%s'", arg));
  }

  @CanIgnoreReturnValue
  @SuppressWarnings("TypeParameterUnusedInFormals")
  private <R, P0, P1> R throwOnCall(P0 arg0, P1 arg1) {
    throw new AssertionError(String.format("Unexpected call with args '%s', '%s'", arg0, arg1));
  }

  private static final class Key {
    @Override
    public boolean equals(Object o) {
      return o instanceof Key;
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }
}
