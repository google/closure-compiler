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

goog.module('jscomp.runtime_tests.polyfill_tests.math_expm1_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertExactlyNaN,
  assertNegativeZero,
  assertPositiveZero,
  noCheck,
} = testing;

testSuite({
  testExpm1() {
    assertPositiveZero(Math.expm1(0));
    assertPositiveZero(Math.expm1(noCheck([])));
    assertNegativeZero(Math.expm1(-0));
    assertEquals(-1, Math.expm1(-Infinity));
    assertEquals(Infinity, Math.expm1(Infinity));
    assertRoughlyEquals(Math.exp(10) - 1, Math.expm1(10), 1e-6);
    assertExactlyNaN(Math.expm1(noCheck('foo')));

    assertRoughlyEquals(1e-10, Math.expm1(1e-10), 1e-20);
    assertRoughlyEquals(-1e-20, Math.expm1(-1e-20), 1e-30);
    assertRoughlyEquals(1e-100, Math.expm1(1e-100), 1e-110);
    assertRoughlyEquals(-1e-10, Math.expm1(noCheck('-1e-10')), 1e-20);
  },
});
