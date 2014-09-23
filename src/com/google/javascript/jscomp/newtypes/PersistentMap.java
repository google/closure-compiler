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

import java.util.AbstractMap;

/** A persistent map with non-destructive additions and removals  */
public abstract class PersistentMap<K, V> extends AbstractMap<K, V> {

  private static PersistentMap EMPTY;
  static {
    setupEmpty();
  }

  @SuppressWarnings("unchecked") //we cannot cast to 'Class<PersistentHashMap>' because it is not in scope at compile time
  private static void setupEmpty(){
      try {
        Class c = Class.forName("clojure.lang.PersistentHashMap");
        EMPTY = ClojurePersistentHashMap.create(c);
      } catch (ClassNotFoundException e) {
        EMPTY = NaivePersistentMap.create();
      }
  }

  public abstract PersistentMap<K, V> with(K key, V value);

  public abstract PersistentMap<K, V> without(K key);

  @SuppressWarnings("unchecked")
  public static <K, V> PersistentMap<K, V> create() {
    return EMPTY;
  }

  public static <K, V> PersistentMap<K, V> of(K key, V value) {
    return PersistentMap.<K, V>create().with(key, value);
  }

}
