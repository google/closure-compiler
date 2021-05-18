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

import static com.google.common.base.Preconditions.checkState;

/** Basic double functions used by JSComp. */
public final class JSCompDoubles {

  /**
   * Can a 32 bit int exactly represent `x`?
   *
   * <p>Many double values are not exact integers. Many that are integers are too large to fit into
   * a Java int.
   *
   * <p>This function does not guarantee that a value can be round-tripped from double to int to
   * double and have an identical bit pattern. Notably, 0.0 and -0.0 both represent exactly 0.
   */
  public static boolean isExactInt32(double x) {
    return !Double.isNaN(x) && ((double) ((int) x)) == x;
  }

  /**
   * Can a 64 bit int exactly represent `x`?
   *
   * <p>Many double values are not exact integers. Many that are integers are too large to fit into
   * a Java long.
   *
   * <p>This function does not guarantee that a value can be round-tripped from double to long to
   * double and have an identical bit pattern. Notably, 0.0 and -0.0 both represent exactly 0.
   */
  public static boolean isExactInt64(double x) {
    return !Double.isNaN(x) && ((double) ((long) x)) == x;
  }

  /**
   * Does `x` exactly represent an value with no fractional part?
   *
   * <p>The value may be too large to fit in a primitive integral type, such as long.
   *
   * <p>Returns false for NaN and Infinity.
   *
   * <p>This should behave identically to Guava {@code DoubleMath.isMathematicalInteger} but is J2CL
   * compatible.
   */
  public static boolean isMathematicalInteger(double x) {
    return x % 1.0 == 0.0;
  }

  /**
   * Does `x` have precision down to the "ones column"?
   *
   * <p>A double can hold exact integer values that are very large, but to do so it may loose
   * precision at the scale of "ones". That is "largeDouble + 1.0 == largeDouble" may be true.
   *
   * <p>Returns false for NaN and Infinity.
   */
  public static boolean isAtLeastIntegerPrecision(double x) {
    // It's possible for a JVM to use floating-point values with more than 53 bits of precision,
    // but we have to be conservative.
    return Math.abs(x) < POW_2_53;
  }

  /**
   * Does `x` carry a negative sign?
   *
   * <p>Because -0.0 == 0.0, it is not enough to check `x < 0.0` to determine if a double is
   * negative. This function identifies the -0.0 case.
   *
   * @throws if passed NaN since NaNs are neither positive nor negative.
   */
  public static boolean isNegative(double x) {
    checkState(!Double.isNaN(x));
    return Double.compare(0.0, x) > 0;
  }

  /**
   * Does `x` not carry a negative sign?
   *
   * <p>Because -0.0 == 0.0, it is not enough to check `x < 0.0` to determine if a double is
   * negative. This function identifies the -0.0 case.
   *
   * @throws if passed NaN since NaNs are neither positive nor negative.
   */
  public static boolean isPositive(double x) {
    return !isNegative(x);
  }

  /** Is `x` positive or negative zero? */
  public static boolean isEitherZero(double x) {
    return x == 0.0;
  }

  /**
   * The ECMAScript ToInt32 abstract operation.
   *
   * <p>See https://262.ecma-international.org/5.1/#sec-9.5
   */
  public static int ecmascriptToInt32(double number) {
    // Fast path for most common case. Also covers -0.0
    if (isExactInt32(number)) {
      return (int) number;
    }

    // Step 2
    if (Double.isNaN(number) || Double.isInfinite(number)) {
      return 0;
    }

    // Step 3
    double posInt = (number >= 0) ? Math.floor(number) : Math.ceil(number);

    /**
     * Step 4
     *
     * <p>Afer modding, the value must fit into a long, but not necessarily an int.
     *
     * <p>If value produced by modding is negative, the spec says to add 2^32 so that `int32bit` is
     * positive. However, that addition doesn't affect the trailing 32 bits of the `int32bit`, which
     * are the only ones that matter.
     */
    long int32bit = (long) (posInt % POW_2_32);

    /**
     * Step 5
     *
     * <p>The instructions in the spec are equivalent to interpreting the last 32 bits of `int32bit`
     * as a 2s-compliment integer, which is exactly the representation Java uses for int.
     */
    return (int) int32bit;
  }

  /**
   * The ECMAScript ToUint32 abstract operation.
   *
   * <p>Java has no uint types, so the caller must remember to treat the returned bits as a uint.
   *
   * <p>See https://262.ecma-international.org/5.1/#sec-9.6
   */
  public static int ecmascriptToUint32(double number) {
    return ecmascriptToInt32(number);
  }

  private static final double POW_2_32 = Math.pow(2, 32);
  private static final double POW_2_53 = Math.pow(2, 53);

  private JSCompDoubles() {
    throw new AssertionError();
  }
}
