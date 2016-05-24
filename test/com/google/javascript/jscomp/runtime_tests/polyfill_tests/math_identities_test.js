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

goog.module('jscomp.runtime_tests.polyfill_tests.math_identities_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testExpm1Log1p() {
    for (let x = 1e-7; x < 1; x *= 1.2) {
      assertRoughlyEquals(x, Math.expm1(Math.log1p(x)), x * 1e-7);
      assertRoughlyEquals(-x, Math.expm1(Math.log1p(-x)), x * 1e-7);
    }
  },

  testLog1pExpm1() {
    for (let x = 1e-7; x < 1; x *= 1.2) {
      assertRoughlyEquals(x, Math.log1p(Math.expm1(x)), x * 1e-7);
      assertRoughlyEquals(-x, Math.log1p(Math.expm1(-x)), x * 1e-7);
    }
  },

  testAcoshCosh() {
    assertRoughlyEquals(1, Math.acosh(Math.cosh(1)), 1e-10);
    assertRoughlyEquals(2, Math.acosh(Math.cosh(2)), 1e-10);
  },

  testAsinhSinh() {
    assertRoughlyEquals(1, Math.asinh(Math.sinh(1)), 1e-10);
    assertRoughlyEquals(-1, Math.asinh(Math.sinh(-1)), 1e-10);
    assertRoughlyEquals(2, Math.asinh(Math.sinh(2)), 1e-10);
  },

  testAtanhTanh() {
    assertRoughlyEquals(1, Math.atanh(Math.tanh(1)), 1e-10);
    assertRoughlyEquals(-1, Math.atanh(Math.tanh(-1)), 1e-10);
    assertRoughlyEquals(2, Math.atanh(Math.tanh(2)), 1e-10);
  },
});
