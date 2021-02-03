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

/**
 * All colors that either exist a priori in the type system, including JS primitives and
 * type-system-only types, or that otherwise have special behavior during optimizations.
 */
public enum NativeColorId {

  // Boxed primitive types
  BIGINT_OBJECT(
      /* isJsPrimitive= */ false,
      /* boxed= */ null,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ false),
  BOOLEAN_OBJECT(
      /* isJsPrimitive= */ false,
      /* boxed= */ null,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ false),
  NUMBER_OBJECT(
      /* isJsPrimitive= */ false,
      /* boxed= */ null,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ false),
  STRING_OBJECT(
      /* isJsPrimitive= */ false,
      /* boxed= */ null,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ false),
  SYMBOL_OBJECT(
      /* isJsPrimitive= */ false,
      /* boxed= */ null,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ false),

  // JS primitive types
  BIGINT(
      /* isJsPrimitive= */ true,
      BIGINT_OBJECT,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ true),
  BOOLEAN(
      /* isJsPrimitive= */ true,
      BOOLEAN_OBJECT,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ true),
  NUMBER(
      /* isJsPrimitive= */ true,
      NUMBER_OBJECT,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ true),
  STRING(
      /* isJsPrimitive= */ true,
      STRING_OBJECT,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ true),
  SYMBOL(
      /* isJsPrimitive= */ true,
      SYMBOL_OBJECT,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ true),
  NULL_OR_VOID(
      /* isJsPrimitive= */ true,
      /* boxed= */ null,
      /* alwaysInvalidating= */ false,
      /* isTypesystemPrimitive= */ true),

  // Equivalent to Closure '*'/'?' and TS unknown/any
  UNKNOWN(
      /* isJsPrimitive= */ false,
      /* boxed= */ null,
      /* alwaysInvalidating= */ true,
      /* isTypesystemPrimitive= */ true),
  // The supertype of all objects but not primitives. Separate from UNKNOWN because some
  // optimizations back off on any non-object primitives + unknown but operate on the top object +
  // all other objects.
  TOP_OBJECT(
      /* isJsPrimitive= */ false,
      /* boxed= */ null,
      /* alwaysInvalidating= */ true,
      /* isTypesystemPrimitive= */ true);

  private final boolean isJsPrimitive;
  private final NativeColorId boxed;
  private final boolean alwaysInvalidating;
  private final boolean isTypesystemPrimitive;

  NativeColorId(
      boolean isJsPrimitive,
      NativeColorId boxed,
      boolean alwaysInvalidating,
      boolean isTypesystemPrimitive) {
    this.isJsPrimitive = isJsPrimitive;
    this.boxed = boxed;
    this.alwaysInvalidating = alwaysInvalidating;
    this.isTypesystemPrimitive = isTypesystemPrimitive;
  }

  /** Whether this is some JavaScript primitive type like number or string */
  final boolean isPrimitive() {
    return this.isJsPrimitive;
  }

  /**
   * Whether this is some primitive type-system type that exists a priori in the colors, as opposed
   * to the nativd object colors like Number whose properites may vary between compilations.
   */
  final boolean isTypesystemPrimitive() {
    return this.isTypesystemPrimitive;
  }

  /**
   * Whether this type is invalidating in every possible compiler invocation
   *
   * <p>Only call this from the ColorRegistry. It's possible that in a given compiler invocation,
   * more native types (like NUMBER_OBJECT) are invalidating, which will be reflected in the actual
   * {@link Color} corresponding to NUMBER_OBJECT.
   */
  final boolean alwaysInvalidating() {
    return this.alwaysInvalidating;
  }

  public final NativeColorId box() {
    checkState(this.boxed != null, "box() can only be called on primitives besides null/undefined");
    return this.boxed;
  }
}
