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

goog.module('jscomp.runtime_tests.polyfill_tests.math_sign_test');
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
  testSign() {
    assertEquals(1, Math.sign(1.2));
    assertEquals(1, Math.sign(noCheck('42')));
    assertEquals(1, Math.sign(Infinity));
    assertEquals(1, Math.sign(noCheck([2])));

    assertEquals(-1, Math.sign(-42));
    assertEquals(-1, Math.sign(noCheck('-Infinity')));
    assertEquals(-1, Math.sign(-Infinity));
    assertEquals(-1, Math.sign(noCheck([-2])));

    assertPositiveZero(Math.sign(0));
    assertNegativeZero(Math.sign(-0));
    assertPositiveZero(Math.sign(noCheck(null)));
    assertPositiveZero(Math.sign(noCheck([])));

    assertExactlyNaN(Math.sign(NaN));
    assertExactlyNaN(Math.sign(noCheck({})));
    assertExactlyNaN(Math.sign(noCheck('foo')));
  },
});
