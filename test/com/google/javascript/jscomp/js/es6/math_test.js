/*
 * Copyright 2016 The Closure Compiler Authors.
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

goog.module('$jscomp_math_test');
goog.setTestOnly();

const jsunit = goog.require('goog.testing.jsunit');
const testSuite = goog.require('goog.testing.testSuite');


// Note: jsunit's assertNaN doesn't ensure it's a number.
/** @param {*} x Number to assert is exactly NaN. */
function assertNaN(x) {
  assertTrue('Expected NaN: ' + x, typeof x === 'number' && isNaN(x));
}


/** @param {*} x Number to assert is exactly positive zero. */
function assertPositiveZero(x) {
  assertTrue('Expected +0: ' + x, x === 0 && 1 / x == Infinity);
}


/** @param {*} x Number to assert is exactly negative zero. */
function assertNegativeZero(x) {
  assertTrue('Expected -0: ' + x, x === 0 && 1 / x == -Infinity);
}


testSuite({
  testClz32() {
    assertEquals(32, Math.clz32(0));
    let x = 1;
    for (let i = 31; i >= 0; i--) {
      assertEquals(i, Math.clz32(x));
      assertEquals(i, Math.clz32(2 * x - 1));
      x *= 2;
    }
    assertEquals(26, Math.clz32('52'));
    assertEquals(26, Math.clz32([52]));
    assertEquals(32, Math.clz32([52, 53]));

    // Overflow cases
    assertEquals(32, Math.clz32(0x100000000));
    assertEquals(31, Math.clz32(0x100000001));

    // NaN -> 0
    assertEquals(32, Math.clz32(NaN));
    assertEquals(32, Math.clz32('foo'));
    assertEquals(32, Math.clz32(Infinity));
  },


  testImul() {
    // This is a sampling of test cases from v8's unit tests
    assertEquals(8, Math.imul(2, 4));
    assertEquals(-8, Math.imul(-1, 8));
    assertEquals(4, Math.imul(-2, -2));
    assertEquals(-5, Math.imul(0xffffffff, 5));
    assertEquals(-10, Math.imul(0xfffffffe, 5));
    assertPositiveZero(Math.imul(7, -0));
    assertEquals(7, Math.imul(7, 1.9));
    assertEquals(7, Math.imul(1.9, 7));
    assertEquals(-7, Math.imul(7, -1.9));
    assertEquals(-7, Math.imul(-1.9, 7));

    const two16 = 1 << 16;
    const two30 = 1 << 30;
    const two31 = two30 * 2;
    const max = two31 - 1;

    assertEquals(-two30, Math.imul(two30, 7));
    assertEquals(two30, Math.imul(7, -two30));
    assertPositiveZero(Math.imul(two30, two30));

    assertEquals(-two31, Math.imul(-two31, 7));
    assertEquals(-two31, Math.imul(7, two31));
    assertPositiveZero(Math.imul(-two31, two31));

    assertEquals(two31 - 7, Math.imul(max, 7));
    assertEquals(1, Math.imul(max, max));

    assertPositiveZero(Math.imul(two16, two16));
    assertEquals(-two16, Math.imul(two16 - 1, two16));
    assertEquals(-two16, Math.imul(two16, two16 - 1));
    assertEquals(-2 * two16 + 1, Math.imul(two16 - 1, two16 - 1));

    // And some other edge cases, too
    assertEquals(20, Math.imul([5], '4'));
    assertPositiveZero(Math.imul([5], [4, 6]));
  },


  testSign() {
    assertEquals(1, Math.sign(1.2));
    assertEquals(1, Math.sign('42'));
    assertEquals(1, Math.sign(Infinity));
    assertEquals(1, Math.sign([2]));

    assertEquals(-1, Math.sign(-42));
    assertEquals(-1, Math.sign('-Infinity'));
    assertEquals(-1, Math.sign(-Infinity));
    assertEquals(-1, Math.sign([-2]));

    assertPositiveZero(Math.sign(0));
    assertNegativeZero(Math.sign(-0));
    assertPositiveZero(Math.sign(null));
    assertPositiveZero(Math.sign([]));

    assertNaN(Math.sign(NaN));
    assertNaN(Math.sign({}));
    assertNaN(Math.sign('foo'));
  },


  testLog10() {
    assertRoughlyEquals(2, Math.log10(100), 1e-6);
    assertRoughlyEquals(300, Math.log10(1e300), 1e-6);
    assertRoughlyEquals(0, Math.log10(1), 1e-6);
    assertRoughlyEquals(-2, Math.log10(1e-2), 1e-6);

    assertEquals(-Infinity, Math.log10(0));
    assertEquals(Infinity, Math.log10(Infinity));
    assertNaN(Math.log10(-1));

    assertRoughlyEquals(2, Math.log10('100'), 1e-6);
    assertEquals(-Infinity, Math.log10(null));

    assertRoughlyEquals(543, Math.pow(10, Math.log10(543)), 1e-6);
  },


  testLog2() {
    assertRoughlyEquals(2, Math.log2(4), 1e-6);
    assertRoughlyEquals(1000, Math.log2(Math.pow(2, 1000)), 1e-6);
    assertRoughlyEquals(0, Math.log2(1), 1e-6);
    assertRoughlyEquals(-2, Math.log2(0.25), 1e-6);

    assertEquals(-Infinity, Math.log2(0));
    assertEquals(Infinity, Math.log2(Infinity));
    assertNaN(Math.log2(-1));

    assertRoughlyEquals(2, Math.log2('4'), 1e-6);
    assertEquals(-Infinity, Math.log2(null));

    assertRoughlyEquals(543, Math.pow(2, Math.log2(543)), 1e-6);
  },


  testLog1p() {
    assertPositiveZero(Math.log1p(0));
    assertPositiveZero(Math.log1p([]));
    assertNegativeZero(Math.log1p(-0));
    assertRoughlyEquals(Math.log(10), Math.log1p(9), 1e-6);
    assertEquals(-Infinity, Math.log1p(-1));
    assertEquals(Infinity, Math.log1p(Infinity));
    assertNaN(Math.log1p(-2));
    assertNaN(Math.log1p({}));

    assertRoughlyEquals(1e-10, Math.log1p(1e-10), 1e-20);
    assertRoughlyEquals(-1e-20, Math.log1p(-1e-20), 1e-30);
    assertRoughlyEquals(1e-100, Math.log1p(1e-100), 1e-110);
    assertRoughlyEquals('-1e-100', Math.log1p('-1e-100'), 1e-110);

    for (let x = 1e-7; x < 1; x *= 1.2) {
      assertRoughlyEquals(x, Math.log1p(Math.expm1(x)), x * 1e-7);
      assertRoughlyEquals(-x, Math.log1p(Math.expm1(-x)), x * 1e-7);
    }
  },


  testExpm1() {
    assertPositiveZero(Math.expm1(0));
    assertPositiveZero(Math.expm1([]));
    assertNegativeZero(Math.expm1(-0));
    assertEquals(-1, Math.expm1(-Infinity));
    assertEquals(Infinity, Math.expm1(Infinity));
    assertRoughlyEquals(Math.exp(10) - 1, Math.expm1(10), 1e-6);
    assertNaN(Math.expm1('foo'));

    assertRoughlyEquals(1e-10, Math.expm1(1e-10), 1e-20);
    assertRoughlyEquals(-1e-20, Math.expm1(-1e-20), 1e-30);
    assertRoughlyEquals(1e-100, Math.expm1(1e-100), 1e-110);
    assertRoughlyEquals(-1e-10, Math.expm1('-1e-10'), 1e-20);

    for (let x = 1e-7; x < 1; x *= 1.2) {
      assertRoughlyEquals(x, Math.expm1(Math.log1p(x)), x * 1e-7);
      assertRoughlyEquals(-x, Math.expm1(Math.log1p(-x)), x * 1e-7);
    }
  },


  testCosh() {
    assertEquals(1, Math.cosh(0));
    assertEquals(1, Math.cosh(null));
    assertEquals(Infinity, Math.cosh(Infinity));
    assertEquals(Infinity, Math.cosh(1e20));
    assertEquals(Infinity, Math.cosh(-1e20));
    assertEquals(Infinity, Math.cosh(-Infinity));
    assertNaN(Math.cosh(NaN));
    assertNaN(Math.cosh('foo'));

    // Note: we get the last couple digits wrong, possibly due to rounding?
    assertRoughlyEquals(1.5430806348153, Math.cosh(1), 1e-13);
    assertRoughlyEquals(1.5430806348153, Math.cosh(-1), 1e-13);
  },


  testSinh() {
    assertPositiveZero(Math.sinh(0));
    assertPositiveZero(Math.sinh([]));
    assertNegativeZero(Math.sinh(-0));
    assertEquals(Infinity, Math.sinh(Infinity));
    assertEquals(Infinity, Math.sinh(1e20));
    assertEquals(-Infinity, Math.sinh(-1e20));
    assertEquals(-Infinity, Math.sinh(-Infinity));
    assertNaN(Math.sinh(NaN));
    assertNaN(Math.sinh('foo'));

    assertRoughlyEquals(1.17520119364380, Math.sinh(1), 1e-13);
    assertRoughlyEquals(-1.17520119364380, Math.sinh(-1), 1e-13);
  },


  testTanh() {
    assertPositiveZero(Math.tanh(0));
    assertPositiveZero(Math.tanh([-0]));
    assertNegativeZero(Math.tanh(-0));
    assertEquals(1, Math.tanh(Infinity));
    assertEquals(1, Math.tanh(1e20));
    assertEquals(-1, Math.tanh(-1e20));
    assertEquals(-1, Math.tanh(-Infinity));
    assertNaN(Math.tanh(NaN));
    assertNaN(Math.tanh({}));

    assertRoughlyEquals(0.761594155955765, Math.tanh(1), 1e-15);
    assertRoughlyEquals(-0.761594155955765, Math.tanh(-1), 1e-15);
  },


  testAcosh() {
    assertPositiveZero(Math.acosh(1));
    assertNaN(Math.acosh(0));
    assertNaN(Math.acosh({}));
    assertEquals(Infinity, Math.acosh(Infinity));

    assertRoughlyEquals(1, Math.acosh(Math.cosh(1)), 1e-10);
    assertRoughlyEquals(2, Math.acosh(Math.cosh(2)), 1e-10);
  },


  testAsinh() {
    assertPositiveZero(Math.asinh(0));
    assertPositiveZero(Math.asinh(null));
    assertNegativeZero(Math.asinh(-0));
    assertNaN(Math.asinh('foo'));
    assertEquals(Infinity, Math.asinh(Infinity));
    assertEquals(-Infinity, Math.asinh(-Infinity));

    assertRoughlyEquals(1, Math.asinh(Math.sinh(1)), 1e-10);
    assertRoughlyEquals(-1, Math.asinh(Math.sinh(-1)), 1e-10);
    assertRoughlyEquals(2, Math.asinh(Math.sinh(2)), 1e-10);
  },


  testAtanh() {
    assertPositiveZero(Math.atanh(0));
    assertPositiveZero(Math.atanh(null));
    assertNegativeZero(Math.atanh(-0));
    assertNaN(Math.atanh('foo'));
    assertEquals(Infinity, Math.atanh(1));
    assertEquals(-Infinity, Math.atanh(-1));

    assertRoughlyEquals(1, Math.atanh(Math.tanh(1)), 1e-10);
    assertRoughlyEquals(-1, Math.atanh(Math.tanh(-1)), 1e-10);
    assertRoughlyEquals(2, Math.atanh(Math.tanh(2)), 1e-10);
  },


  testHypot() {
    assertRoughlyEquals(5, Math.hypot(3, 4), 1e-10);
    assertRoughlyEquals(5, Math.hypot(-3, 4), 1e-10);
    assertRoughlyEquals(13, Math.hypot(5, 12), 1e-10);
    assertRoughlyEquals(13, Math.hypot(5, -12), 1e-10);
    assertRoughlyEquals(Math.sqrt(2), Math.hypot(-1, -1), 1e-10);

    assertRoughlyEquals(13, Math.hypot(3, 4, 12), 1e-10);

    // Test overflow and underflow
    assertRoughlyEquals(5e300, Math.hypot(3e300, 4e300), 1e290);
    assertRoughlyEquals(5e300, Math.hypot(-3e300, -4e300), 1e290);

    assertRoughlyEquals(5e-300, Math.hypot(3e-300, 4e-300), 1e290);
    assertRoughlyEquals(5e-300, Math.hypot(-3e-300, -4e-300), 1e290);
  },


  testTrunc() {
    assertPositiveZero(Math.trunc(0));
    assertPositiveZero(Math.trunc(0.2));
    assertNegativeZero(Math.trunc(-0));
    assertNegativeZero(Math.trunc(-0.2));
    assertEquals(1e12, Math.trunc(1e12 + 0.99));

    assertEquals(Infinity, Math.trunc(Infinity));
    assertEquals(-Infinity, Math.trunc(-Infinity));
  },


  testCbrt() {
    assertPositiveZero(Math.cbrt(0));
    assertNegativeZero(Math.cbrt(-0));

    assertEquals(Infinity, Math.cbrt(Infinity));
    assertEquals(-Infinity, Math.cbrt(-Infinity));

    assertRoughlyEquals(2, Math.cbrt(8), 1e-10);
    assertRoughlyEquals(-2, Math.cbrt(-8), 1e-10);
    assertRoughlyEquals(3, Math.cbrt(27), 1e-10);
    assertRoughlyEquals(-3, Math.cbrt(-27), 1e-10);

    assertRoughlyEquals(6, Math.cbrt(216), 1e-10);
    assertRoughlyEquals(-5, Math.cbrt(-125), 1e-10);

    assertRoughlyEquals(1.44224957030741, Math.cbrt(3), 1e-14);
  }
});
