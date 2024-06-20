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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A deterministic drop-in replacement for {@link IdentityHashMap}.
 *
 * <p>This class is actually "map-like" in that it doesn't fully implement the {@link Map}
 * interface. Only the methods needed to eliminate {@link IdentityHashMap} were defined. The entire
 * interface could be satisfied in the future if necessary.
 */
public final class LinkedIdentityHashMap<K, V> implements Serializable {

  @SuppressWarnings("DeterministicDatastructure")
  private final IdentityHashMap<K, Entry<K, V>> innerMap = new IdentityHashMap<>();

  private final ArrayList<Entry<K, V>> iterationOrder = new ArrayList<>();

  public @Nullable V get(K key) {
    Entry<K, V> entry = innerMap.get(key);
    return (entry == null) ? null : entry.value;
  }

  @CanIgnoreReturnValue
  public @Nullable V put(K key, V value) {
    Entry<K, V> entry = innerMap.get(key);
    if (entry == null) {
      entry = new Entry<>(key, value);
      innerMap.put(key, entry);
      iterationOrder.add(entry);
      return null;
    } else {
      V oldValue = entry.value;
      entry.value = value;
      return oldValue;
    }
  }

  @CanIgnoreReturnValue
  public @Nullable V computeIfAbsent(K key, Function<? super K, ? extends V> fn) {
    V oldValue = this.get(key);
    if (oldValue != null) {
      return oldValue;
    }

    V newValue = fn.apply(key);
    this.put(key, newValue);
    return newValue;
  }

  @CanIgnoreReturnValue
  public @Nullable V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> fn) {
    V oldValue = this.get(key);
    if (oldValue == null) {
      this.put(key, value);
      return value;
    }

    V mergeValue = fn.apply(oldValue, value);
    this.put(key, mergeValue);
    return mergeValue;
  }

  public void forEach(BiConsumer<? super K, ? super V> fn) {
    for (Entry<K, V> entry : iterationOrder) {
      fn.accept(entry.key, entry.value);
    }
  }

  private static class Entry<K, V> {
    private final K key;
    private V value;

    Entry(K key, V value) {
      this.key = key;
      this.value = value;
    }
  }
}
