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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** A naive persistent set that does too many copies */
public class NaivePersistentSet<K> extends PersistentSet<K> {
  private final Set<K> set;

  private NaivePersistentSet(Set<K> s) {
    this.set = s;
  }

  public static <K> PersistentSet<K> create()  {
    return new NaivePersistentSet<K>(new HashSet<K>());
  }

  public PersistentSet<K> with(K key) {
    Set<K> newSet = new HashSet<>(this.set);
    newSet.add(key);
    return new NaivePersistentSet<>(newSet);
  }

  public PersistentSet<K> without(K key) {
    Set<K> newSet = new HashSet<>(this.set);
    newSet.remove(key);
    return new NaivePersistentSet<>(newSet);
  }

  @Override
  public int size() {
    return set.size();
  }

  @Override
  public Iterator<K> iterator() {
    return set.iterator();
  }
}
