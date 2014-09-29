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

import java.util.AbstractSet;

/** A persistent set with non-destructive additions and removals */
public abstract class PersistentSet<K> extends AbstractSet<K> {

  private static PersistentSet EMPTY;

  static {
      setupEmpty();
  }

  @SuppressWarnings("unchecked") //we cannot cast to 'Class<PersistentHashSet>' because it is not in scope at compile time
  private static void setupEmpty(){
    try {
      Class c = Class.forName("clojure.lang.PersistentHashSet");
      EMPTY = ClojurePersistentHashSet.create(c);
    } catch (ClassNotFoundException e) {
      EMPTY = NaivePersistentSet.create();
    }
  }

  public abstract PersistentSet<K> with(K key);

  public abstract PersistentSet<K> without(K key);

  @SuppressWarnings("unchecked")
  public static <K> PersistentSet<K> create() {
    return EMPTY;
  }

}
