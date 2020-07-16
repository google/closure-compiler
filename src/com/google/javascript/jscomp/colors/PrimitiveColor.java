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

package com.google.javascript.jscomp.colors;

import com.google.common.collect.ImmutableCollection;

/** Value types that are a) not user-defined and b) do not need individual classes */
public enum PrimitiveColor implements Color {
  // JS primitive types
  NUMBER,
  STRING,
  SYMBOL,
  NULL_OR_VOID,
  BIGINT,
  BOOLEAN,
  // Equivalent to Closure '*'/'?' and TS unknown/any
  UNKNOWN;

  @Override
  public boolean isPrimitive() {
    return true;
  }

  @Override
  public boolean isUnion() {
    return false;
  }

  @Override
  public ImmutableCollection<Color> getAlternates() {
    // In theory we could consider a primitive a union of a single value. It's not clear whether
    // that would be simpler or not.
    throw new UnsupportedOperationException("Can only call getAlternates() on unions");
  }
}
