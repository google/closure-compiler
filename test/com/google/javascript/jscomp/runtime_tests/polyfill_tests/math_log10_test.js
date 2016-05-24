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

goog.module('jscomp.runtime_tests.polyfill_tests.math_log10_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertExactlyNaN,
  noCheck,
} = testing;

testSuite({
  testLog10() {
    assertRoughlyEquals(2, Math.log10(100), 1e-6);
    assertRoughlyEquals(300, Math.log10(1e300), 1e-6);
    assertRoughlyEquals(0, Math.log10(1), 1e-6);
    assertRoughlyEquals(-2, Math.log10(1e-2), 1e-6);

    assertEquals(-Infinity, Math.log10(0));
    assertEquals(Infinity, Math.log10(Infinity));
    assertExactlyNaN(Math.log10(-1));

    assertRoughlyEquals(2, Math.log10(noCheck('100')), 1e-6);
    assertEquals(-Infinity, Math.log10(noCheck(null)));

    assertRoughlyEquals(543, Math.pow(10, Math.log10(543)), 1e-6);
  },
});
