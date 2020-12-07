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

import static com.google.common.base.Preconditions.checkState;

import com.google.errorprone.annotations.Immutable;

/**
 * All color representing native JavaScript objects that have special behavior during optimizations.
 */
@Immutable
public enum NativeColorId {

  // Boxed primitive types
  BIGINT_OBJECT(/* isPrimitive= */ false, /* boxed= */ null),
  BOOLEAN_OBJECT(/* isPrimitive= */ false, /* boxed= */ null),
  NUMBER_OBJECT(/* isPrimitive= */ false, /* boxed= */ null),
  STRING_OBJECT(/* isPrimitive= */ false, /* boxed= */ null),
  SYMBOL_OBJECT(/* isPrimitive= */ false, /* boxed= */ null),

  // JS primitive types
  BIGINT(/* isPrimitive= */ true, BIGINT_OBJECT),
  BOOLEAN(/* isPrimitive= */ true, BOOLEAN_OBJECT),
  NUMBER(/* isPrimitive= */ true, NUMBER_OBJECT),
  STRING(/* isPrimitive= */ true, STRING_OBJECT),
  SYMBOL(/* isPrimitive= */ true, SYMBOL_OBJECT),
  NULL_OR_VOID(/* isPrimitive= */ true, /* boxed= */ null),

  // Equivalent to Closure '*'/'?' and TS unknown/any
  UNKNOWN(/* isPrimitive= */ false, /* boxed= */ null),
  // The supertype of all objects but not primitives. Separate from UNKNOWN because some
  // optimizations back off on any non-object primitives + unknown but operate on the top object +
  // all other objects.
  TOP_OBJECT(/* isPrimitive= */ false, /* boxed= */ null);

  private final boolean isPrimitive;
  private final NativeColorId boxed;

  NativeColorId(boolean isPrimitive, NativeColorId boxed) {
    this.isPrimitive = isPrimitive;
    this.boxed = boxed;
  }

  final boolean isPrimitive() {
    return this.isPrimitive;
  }

  public final NativeColorId box() {
    checkState(this.boxed != null, "box() can only be called on primitives besides null/undefined");
    return this.boxed;
  }
}
