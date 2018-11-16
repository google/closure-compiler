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

goog.module('jscomp.runtime_tests.polyfill_tests.math_fround_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const assertExactlyNaN = testing.assertExactlyNaN;
const assertNegativeZero = testing.assertNegativeZero;
const assertPositiveZero = testing.assertPositiveZero;
const noCheck = testing.noCheck;
const userAgent = goog.require('goog.userAgent');

const EPSILON = Number.EPSILON;

testSuite({
  shouldRunTests() {
    // Not polyfilled correctly to IE9.
    return !userAgent.IE || userAgent.isVersionOrHigher(10);
  },

  testFround_terminatingNumbers() {
    // These cases all have terminating binary representations (i.e. integers
    // or rationals with powers of two in the denominator).
    assertPositiveZero(Math.fround(0));
    assertNegativeZero(Math.fround(-0));

    assertEquals(1, Math.fround(1));
    assertEquals(-1, Math.fround(-1));

    assertEquals(0.5, Math.fround(0.5));
    assertEquals(0.25, Math.fround(0.25));
    assertEquals(-0.75, Math.fround(-0.75));
    assertEquals(3, Math.fround(3));
    assertEquals(-20.375, Math.fround(-20.375));
    assertEquals(101.3125, Math.fround(101.3125));

    assertEquals(1 << 22, Math.fround(1 << 22));
  },

  testFround_largeNumbers() {
    assertEquals(1 << 30, Math.fround(1 << 30));
    assertEquals(2 ** 127, Math.fround(2 ** 127));
    assertEquals(-(2 ** 127), Math.fround(-(2 ** 127)));
    assertEquals(1.875 * (2 ** 127), Math.fround(1.875 * (2 ** 127)));
    assertEquals(-1.9375 * (2 ** 127), Math.fround(-1.9375 * (2 ** 127)));
    assertEquals('a', Infinity, Math.fround(2 ** 128));
    assertEquals(-Infinity, Math.fround(-(2 ** 128)));

    const maxFloat = 3.4028234663852886e38;
    assertEquals(maxFloat, Math.fround(3.4028235e38));
    assertEquals(Infinity, Math.fround(3.4028236e38));
    assertEquals('b', Infinity, Math.fround(Infinity));
    assertEquals(-maxFloat, Math.fround(-3.4028235e38));
    assertEquals(-Infinity, Math.fround(-3.4028236e38));
    assertEquals(-Infinity, Math.fround(-Infinity));
  },

  testFround_smallNumbers() {
    // Smallest normal float32
    assertEquals(1.015625 * 2 ** -126, Math.fround(1.015625 * 2 ** -126));
    assertEquals(-1.015625 * 2 ** -126, Math.fround(-1.015625 * 2 ** -126));

    // Subnormal numbers
    assertEquals(1.015625 * 2 ** -127, Math.fround(1.015625 * 2 ** -127));
    assertEquals(1.875 * 2 ** -128, Math.fround(1.875 * 2 ** -128));

    // Numbers exactly between two floats round toward zero
    const minFloat = 2 ** -149;
    assertEquals(12 * minFloat, Math.fround(12.5 * minFloat));
    assertEquals(13 * minFloat, Math.fround(12.5 * (1 + EPSILON) * minFloat));
    assertEquals(-12 * minFloat, Math.fround(-12.5 * minFloat));
    assertEquals(-13 * minFloat, Math.fround(-12.5 * (1 + EPSILON) * minFloat));

    // Smallest non-zero float32
    assertEquals(minFloat, Math.fround(minFloat));
    assertEquals(-minFloat, Math.fround(-minFloat));
    assertEquals(minFloat, Math.fround((1 + EPSILON) / 2 * minFloat));
    assertEquals(-minFloat, Math.fround(-(1 + EPSILON) / 2 * minFloat));
    assertPositiveZero(Math.fround(minFloat / 2));
    assertNegativeZero(Math.fround(-minFloat / 2));
  },

  testFround_nonNumbers() {
    assertEquals(1, Math.fround(noCheck('1')));
    assertExactlyNaN(Math.fround(noCheck([1, 2])));
    assertExactlyNaN(Math.fround(noCheck('a')));
    assertExactlyNaN(Math.fround(NaN));
  },
});
