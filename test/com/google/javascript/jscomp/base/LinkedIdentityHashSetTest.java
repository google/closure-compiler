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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LinkedIdentityHashSetTest {

  private static final Key KEY_0 = new Key();
  private static final Key KEY_1 = new Key();

  @BeforeClass
  public static void validateKeys() {
    assertThat(KEY_0).isEqualTo(KEY_1);
    assertThat(KEY_0).isNotSameInstanceAs(KEY_1);

    assertThat(KEY_0.hashCode()).isEqualTo(0);
    assertThat(KEY_1.hashCode()).isEqualTo(0);
  }

  @Test
  public void entries_keyedByIdentity() {
    LinkedIdentityHashSet<Key> set = new LinkedIdentityHashSet<>();
    assertThat(set.contains(KEY_0)).isFalse();
    assertThat(set.contains(KEY_1)).isFalse();

    set.add(KEY_0);
    assertThat(set.contains(KEY_0)).isTrue();
    assertThat(set.contains(KEY_1)).isFalse();

    set.add(KEY_1);
    assertThat(set.contains(KEY_0)).isTrue();
    assertThat(set.contains(KEY_1)).isTrue();
  }

  @Test
  public void add_returnsPrevioulyPresent() {
    LinkedIdentityHashSet<Key> set = new LinkedIdentityHashSet<>();
    assertThat(set.contains(KEY_0)).isFalse();

    assertThat(set.add(KEY_0)).isTrue();
    assertThat(set.add(KEY_0)).isFalse();
  }

  @Test
  public void remove_returnsPrevioulyPresent() {
    LinkedIdentityHashSet<Key> set = new LinkedIdentityHashSet<>();
    assertThat(set.contains(KEY_0)).isFalse();

    set.add(KEY_0);
    assertThat(set.contains(KEY_0)).isTrue();

    assertThat(set.remove(KEY_0)).isTrue();
    assertThat(set.remove(KEY_0)).isFalse();
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
