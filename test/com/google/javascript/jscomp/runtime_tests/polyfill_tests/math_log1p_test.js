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

goog.module('jscomp.runtime_tests.polyfill_tests.math_log1p_test');
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
  testLog1p() {
    assertPositiveZero(Math.log1p(0));
    assertPositiveZero(Math.log1p(noCheck([])));
    assertNegativeZero(Math.log1p(-0));
    assertRoughlyEquals(Math.log(10), Math.log1p(9), 1e-6);
    assertEquals(-Infinity, Math.log1p(-1));
    assertEquals(Infinity, Math.log1p(Infinity));
    assertExactlyNaN(Math.log1p(-2));
    assertExactlyNaN(Math.log1p(noCheck({})));

    assertRoughlyEquals(1e-10, Math.log1p(1e-10), 1e-20);
    assertRoughlyEquals(-1e-20, Math.log1p(-1e-20), 1e-30);
    assertRoughlyEquals(1e-100, Math.log1p(1e-100), 1e-110);
    assertRoughlyEquals('-1e-100', Math.log1p(noCheck('-1e-100')), 1e-110);
  },
});
