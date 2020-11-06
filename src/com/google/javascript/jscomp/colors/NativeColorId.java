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

import com.google.errorprone.annotations.Immutable;

/**
 * All color representing native JavaScript objects that have special behavior during optimizations.
 */
@Immutable
public enum NativeColorId {

  // JS primitive types
  NUMBER(/* isPrimitive= */ true),
  STRING(/* isPrimitive= */ true),
  SYMBOL(/* isPrimitive= */ true),
  NULL_OR_VOID(/* isPrimitive= */ true),
  BIGINT(/* isPrimitive= */ true),
  BOOLEAN(/* isPrimitive= */ true),
  // Equivalent to Closure '*'/'?' and TS unknown/any
  UNKNOWN(/* isPrimitive= */ false),
  // The supertype of all objects but not primitives. Separate from UNKNOWN because some
  // optimizations back off on any non-object primitives + unknown but operate on the top object +
  // all other objects.
  TOP_OBJECT(/* isPrimitive= */ false);

  private final boolean isPrimitive;

  NativeColorId(boolean isPrimitive) {
    this.isPrimitive = isPrimitive;
  }

  final boolean isPrimitive() {
    return this.isPrimitive;
  }
}
