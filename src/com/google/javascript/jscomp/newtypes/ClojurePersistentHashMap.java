/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.newtypes;

import com.google.common.annotations.GwtIncompatible;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/** A persistent map that simply wraps Clojure's implementation */
@GwtIncompatible("java.lang.reflect")
final class ClojurePersistentHashMap<K, V> extends PersistentMap<K, V> {
  private static Method assoc;
  private static Method without;
  private final Map map;

  private ClojurePersistentHashMap(Map m) {
    this.map = m;
  }

  public static <K, V> PersistentMap<K, V> create(Class<? extends Map> cls)  {
    try {
      assoc = cls.getDeclaredMethod("assoc", Object.class, Object.class);
      without = cls.getDeclaredMethod("without", Object.class);
      Map m = (Map) cls.getDeclaredField("EMPTY").get(null);
      return new ClojurePersistentHashMap<>(m);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  public PersistentMap<K, V> with(K key, V value) {
    try {
      Map m = (Map) assoc.invoke(this.map, key, value);
      return new ClojurePersistentHashMap<>(m);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  public PersistentMap<K, V> without(K key) {
    try {
      Map m = (Map) without.invoke(this.map, key);
      return new ClojurePersistentHashMap<>(m);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public V get(Object key) {
    return (V) this.map.get(key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<K> keySet() {
    return this.map.keySet();
  }

  @Override
  public boolean containsKey(Object key) {
    return this.map.containsKey(key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<Map.Entry<K, V>> entrySet() {
    return this.map.entrySet();
  }

  @Override
  public int size() {
    return this.map.size();
  }

  @Override
  public boolean isEmpty() {
    return this.map.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ClojurePersistentHashMap) {
      ClojurePersistentHashMap pm = (ClojurePersistentHashMap) o;
      return this.map.equals(pm.map);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return this.map.hashCode();
  }
}
