/*
 * Copyright 2020 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.base.JSCompObjects.identical;

/**
 * A reference to another object that uses the identity semantics of that object for equality.
 *
 * <p>The intended purpose of these references is to make give identity semantics to {@code Map}
 * keys. For example, {@code LinkedHashMap<IdentityRef<Foo>, Bar>}, behaves as an {@code
 * IdentityHashMap} but with deterministic iteration order.
 */
public final class IdentityRef<T> {

  private final T value;

  public static <U> IdentityRef<U> of(U value) {
    return new IdentityRef<>(value);
  }

  private IdentityRef(T value) {
    this.value = value;
  }

  public T get() {
    return this.value;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof IdentityRef) && identical(((IdentityRef) o).value, this.value);
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this.value);
  }

  @Override
  public String toString() {
    return "IdentityRef<" + this.value + ">";
  }
}
