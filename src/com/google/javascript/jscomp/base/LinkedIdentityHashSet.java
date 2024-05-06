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

/**
 * A deterministic drop-in replacement for {@link Sets.newIdentityHashSet}.
 *
 * <p>This class is actually "set-like" in that it doesn't fully implement the {@link Set}
 * interface. Only the methods needed to eliminate {@link Sets.newIdentityHashSet} were defined. The
 * entire interface could be satisfied in the future if necessary.
 */
public final class LinkedIdentityHashSet<E> implements Serializable {

  private final LinkedIdentityHashMap<E, Sentinel> innerMap = new LinkedIdentityHashMap<>();

  public boolean contains(E element) {
    return innerMap.get(element) == SENTINEL;
  }

  @CanIgnoreReturnValue
  public boolean add(E element) {
    return innerMap.put(element, SENTINEL) == null;
  }

  @CanIgnoreReturnValue
  public boolean remove(E element) {
    return innerMap.put(element, null) == SENTINEL;
  }

  private static class Sentinel {}

  private static final Sentinel SENTINEL = new Sentinel();
}
