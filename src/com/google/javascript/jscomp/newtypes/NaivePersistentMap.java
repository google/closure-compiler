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

import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Set;

/** A naive persistent map that does too many copies */
public class NaivePersistentMap<K, V> extends PersistentMap<K, V> {
  private Map map;

  private NaivePersistentMap(Map<K, V> m) {
    this.map = m;
  }

  public static <K, V> PersistentMap<K, V> create()  {
    return new NaivePersistentMap<>(Maps.<K, V>newHashMap());
  }

  public PersistentMap<K, V> with(K key, V value) {
    Map<K, V> newMap = Maps.newHashMap(this);
    newMap.put(key, value);
    return new NaivePersistentMap<>(newMap);
  }

  public PersistentMap<K, V> without(K key) {
    Map<K, V> newMap = Maps.newHashMap(this);
    newMap.remove(key);
    return new NaivePersistentMap<>(newMap);
  }

  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    return this.map.entrySet();
  }
}
