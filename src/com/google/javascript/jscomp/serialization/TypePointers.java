/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Comparator.comparingInt;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import java.util.Comparator;

/** TypePointer utilities. */
final class TypePointers {

  /**
   * The first N TypePointer pool offsets correspond to axiomatic colors in this order.
   *
   * <p>These colors are never serialized because all their information is constant.
   */
  static final ImmutableList<Color> OFFSET_TO_AXIOMATIC_COLOR =
      ImmutableList.of(
          StandardColors.UNKNOWN,
          StandardColors.BOOLEAN,
          StandardColors.STRING,
          StandardColors.NUMBER,
          StandardColors.NULL_OR_VOID,
          StandardColors.SYMBOL,
          StandardColors.BIGINT,
          StandardColors.TOP_OBJECT);

  static final int AXIOMATIC_COLOR_COUNT = OFFSET_TO_AXIOMATIC_COLOR.size();

  static final Comparator<TypePointer> OFFSET_ASCENDING = comparingInt(TypePointer::getPoolOffset);

  static int trimOffset(TypePointer x) {
    return trimOffset(x.getPoolOffset());
  }

  static int trimOffset(int x) {
    checkState(x >= AXIOMATIC_COLOR_COUNT, x);
    return x - AXIOMATIC_COLOR_COUNT;
  }

  static int untrimOffset(int x) {
    checkState(x >= 0, x);
    return x + AXIOMATIC_COLOR_COUNT;
  }

  static boolean isAxiomatic(TypePointer x) {
    return isAxiomatic(x.getPoolOffset());
  }

  static boolean isAxiomatic(int x) {
    return x < AXIOMATIC_COLOR_COUNT;
  }

  private TypePointers() {
    throw new AssertionError();
  }
}
