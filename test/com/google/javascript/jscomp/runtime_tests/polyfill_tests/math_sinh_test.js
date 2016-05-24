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

goog.module('jscomp.runtime_tests.polyfill_tests.math_sinh_test');
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
  testSinh() {
    assertPositiveZero(Math.sinh(0));
    assertPositiveZero(Math.sinh(noCheck([])));
    assertNegativeZero(Math.sinh(-0));
    assertEquals(Infinity, Math.sinh(Infinity));
    assertEquals(Infinity, Math.sinh(1e20));
    assertEquals(-Infinity, Math.sinh(-1e20));
    assertEquals(-Infinity, Math.sinh(-Infinity));
    assertExactlyNaN(Math.sinh(NaN));
    assertExactlyNaN(Math.sinh(noCheck('foo')));

    assertRoughlyEquals(1.17520119364380, Math.sinh(1), 1e-13);
    assertRoughlyEquals(-1.17520119364380, Math.sinh(-1), 1e-13);
  },
});
