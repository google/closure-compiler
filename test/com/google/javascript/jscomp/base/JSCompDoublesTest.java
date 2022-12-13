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
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.base.JSCompDoubles.ecmascriptToInt32;
import static com.google.javascript.jscomp.base.JSCompDoubles.isAtLeastIntegerPrecision;
import static com.google.javascript.jscomp.base.JSCompDoubles.isExactInt32;
import static com.google.javascript.jscomp.base.JSCompDoubles.isExactInt64;
import static com.google.javascript.jscomp.base.JSCompDoubles.isMathematicalInteger;
import static com.google.javascript.jscomp.base.JSCompDoubles.isNegative;
import static com.google.javascript.jscomp.base.JSCompDoubles.isPositive;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JSCompDoublesTest {

  @Test
  public void testIsExactInt32() {
    assertThat(isExactInt32(0.0)).isTrue();
    assertThat(isExactInt32(-0.0)).isTrue();
    assertThat(isExactInt32(1.0)).isTrue();
    assertThat(isExactInt32(-1.0)).isTrue();
    assertThat(isExactInt32(3.1e5)).isTrue();
    assertThat(isExactInt32(-3.1e5)).isTrue();
    assertThat(isExactInt32(Integer.MIN_VALUE)).isTrue();
    assertThat(isExactInt32(Integer.MAX_VALUE)).isTrue();

    assertThat(isExactInt32(3.1e-5)).isFalse();
    assertThat(isExactInt32(-3.1e-5)).isFalse();
    assertThat(isExactInt32(3.1e12)).isFalse();
    assertThat(isExactInt32(-3.1e12)).isFalse();
    assertThat(isExactInt32(Double.POSITIVE_INFINITY)).isFalse();
    assertThat(isExactInt32(Double.NEGATIVE_INFINITY)).isFalse();
    assertThat(isExactInt32(Double.NaN)).isFalse();
    assertThat(isExactInt32(Integer.MIN_VALUE - 1.0)).isFalse();
    assertThat(isExactInt32(Integer.MAX_VALUE + 1.0)).isFalse();
  }

  @Test
  public void testIsExactInt64() {
    assertThat(isExactInt64(0.0)).isTrue();
    assertThat(isExactInt64(-0.0)).isTrue();
    assertThat(isExactInt64(1.0)).isTrue();
    assertThat(isExactInt64(-1.0)).isTrue();
    assertThat(isExactInt64(3.1e5)).isTrue();
    assertThat(isExactInt64(-3.1e5)).isTrue();
    assertThat(isExactInt64(3.1e12)).isTrue();
    assertThat(isExactInt64(-3.1e12)).isTrue();
    assertThat(isExactInt64(Long.MIN_VALUE)).isTrue();
    assertThat(isExactInt64(Long.MAX_VALUE)).isTrue();

    assertThat(isExactInt64(3.1e-5)).isFalse();
    assertThat(isExactInt64(-3.1e-5)).isFalse();
    assertThat(isExactInt64(3.1e24)).isFalse();
    assertThat(isExactInt64(-3.1e24)).isFalse();
    assertThat(isExactInt64(Double.POSITIVE_INFINITY)).isFalse();
    assertThat(isExactInt64(Double.NEGATIVE_INFINITY)).isFalse();
    assertThat(isExactInt64(Double.NaN)).isFalse();
    assertThat(isExactInt64(POW_2_200)).isFalse();
    assertThat(isExactInt64(-POW_2_200)).isFalse();
  }

  @Test
  public void testIsAtLeastIntegerPrecision() {
    assertThat(isAtLeastIntegerPrecision(Double.POSITIVE_INFINITY)).isFalse();
    assertThat(isAtLeastIntegerPrecision(Double.NEGATIVE_INFINITY)).isFalse();
    assertThat(isAtLeastIntegerPrecision(Double.NaN)).isFalse();
    assertThat(isAtLeastIntegerPrecision(POW_2_53)).isFalse();
    assertThat(isAtLeastIntegerPrecision(-POW_2_53)).isFalse();

    assertThat(isAtLeastIntegerPrecision(0.0)).isTrue();
    assertThat(isAtLeastIntegerPrecision(-0.0)).isTrue();
    assertThat(isAtLeastIntegerPrecision(1.0)).isTrue();
    assertThat(isAtLeastIntegerPrecision(-1.0)).isTrue();
    assertThat(isAtLeastIntegerPrecision(3.1e5)).isTrue();
    assertThat(isAtLeastIntegerPrecision(-3.1e5)).isTrue();
    assertThat(isAtLeastIntegerPrecision(POW_2_53 - 1.0)).isTrue();
    assertThat(isAtLeastIntegerPrecision(-(POW_2_53 - 1.0))).isTrue();
    assertThat(isAtLeastIntegerPrecision(3.1e-5)).isTrue();
    assertThat(isAtLeastIntegerPrecision(-3.1e-5)).isTrue();
  }

  @Test
  public void testIsMathematicalInteger() {
    assertThat(isMathematicalInteger(0.1)).isFalse();
    assertThat(isMathematicalInteger(3.1e-5)).isFalse();
    assertThat(isMathematicalInteger(Double.POSITIVE_INFINITY)).isFalse();
    assertThat(isMathematicalInteger(Double.NaN)).isFalse();

    assertIsMathematicalIntegerWithDecimalPrecision(0.0);
    assertIsMathematicalIntegerWithDecimalPrecision(1.0);
    assertIsMathematicalIntegerWithDecimalPrecision(3.1e5);
    assertIsMathematicalIntegerWithDecimalPrecision(3.1e12);
    assertIsMathematicalIntegerWithDecimalPrecision(Integer.MIN_VALUE);
    assertIsMathematicalIntegerWithDecimalPrecision(Integer.MAX_VALUE);
    assertIsMathematicalIntegerWithDecimalPrecision(Integer.MIN_VALUE - 1.0);
    assertIsMathematicalIntegerWithDecimalPrecision(Integer.MAX_VALUE + 1.0);

    assertIsMathematicalIntegerWithoutDecimalPrecision(POW_2_53 - 1.0);
    assertIsMathematicalIntegerWithoutDecimalPrecision(POW_2_53);
    assertIsMathematicalIntegerWithoutDecimalPrecision(POW_2_53 + 2.0);
    assertIsMathematicalIntegerWithoutDecimalPrecision(POW_2_64);
    assertIsMathematicalIntegerWithoutDecimalPrecision(POW_2_200);
  }

  private static void assertIsMathematicalIntegerWithDecimalPrecision(double x) {
    for (int sign : new int[] {1, -1}) {
      double value = sign * x;
      assertThat(isMathematicalInteger(value)).isTrue();
      assertThat(isMathematicalInteger(value + 0.1)).isFalse();
      assertThat(isMathematicalInteger(value - 0.1)).isFalse();
    }
  }

  private static void assertIsMathematicalIntegerWithoutDecimalPrecision(double x) {
    for (int sign : new int[] {1, -1}) {
      double value = sign * x;
      assertThat(isMathematicalInteger(value)).isTrue();
      assertThat(value).isEqualTo(value + 0.1);
      assertThat(value).isEqualTo(value - 0.1);
    }
  }

  @Test
  public void testIsNegative() {
    assertThat(isNegative(-0.0)).isTrue();
    assertThat(isNegative(-1.0)).isTrue();
    assertThat(isNegative(-3.1e-5)).isTrue();
    assertThat(isNegative(-3.1e5)).isTrue();
    assertThat(isNegative(Double.NEGATIVE_INFINITY)).isTrue();
    assertThat(isNegative(Integer.MIN_VALUE)).isTrue();

    assertThat(isNegative(0.0)).isFalse();
    assertThat(isNegative(1.0)).isFalse();
    assertThat(isNegative(3.1e-5)).isFalse();
    assertThat(isNegative(3.1e12)).isFalse();
    assertThat(isNegative(3.1e5)).isFalse();
    assertThat(isNegative(Double.POSITIVE_INFINITY)).isFalse();
    assertThat(isNegative(Integer.MAX_VALUE)).isFalse();

    assertThrows(Exception.class, () -> isNegative(Double.NaN));
  }

  @Test
  public void testEcmascriptToInt32() {
    // Step 1 special cases
    assertThat(ecmascriptToInt32(0.0)).isEqualTo(0);
    assertThat(ecmascriptToInt32(-0.0)).isEqualTo(0);
    assertThat(ecmascriptToInt32(Double.NaN)).isEqualTo(0);
    assertThat(ecmascriptToInt32(Double.POSITIVE_INFINITY)).isEqualTo(0);
    assertThat(ecmascriptToInt32(Double.NEGATIVE_INFINITY)).isEqualTo(0);

    assertEcmascriptToInt32Equals(0.3, 0);
    assertEcmascriptToInt32Equals(0.0, 0);
    assertEcmascriptToInt32Equals(1.0, 1);
    assertEcmascriptToInt32Equals(2.0, 2);
    assertEcmascriptToInt32Equals(726832.0, 726832);
    assertEcmascriptToInt32Equals(POW_2_31, (1 << 31));
    assertEcmascriptToInt32Equals(POW_2_31 + 1, (1 << 31) + 1);
    assertEcmascriptToInt32Equals(POW_2_64, 0);
    assertEcmascriptToInt32Equals(POW_2_64 + 1.0, 0);
  }

  private static void assertEcmascriptToInt32Equals(double input, int expected) {
    checkState(isPositive(input));

    for (double bias : new double[] {0.0, POW_2_32, POW_2_52}) {
      for (double frac : new double[] {0.0, 0.1}) {
        for (int sign : new int[] {1, -1}) {
          double value = sign * (input + bias + frac);
          assertWithMessage(Double.toString(value))
              .that(ecmascriptToInt32(value))
              .isEqualTo(sign * expected);
        }
      }
    }
  }

  private static final double POW_2_31 = Math.pow(2, 31);
  private static final double POW_2_32 = Math.pow(2, 32);
  private static final double POW_2_52 = Math.pow(2, 52);
  private static final double POW_2_53 = Math.pow(2, 53);
  private static final double POW_2_64 = Math.pow(2, 64);
  private static final double POW_2_200 = Math.pow(2, 200);
}
