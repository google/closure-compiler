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

goog.module('jscomp.runtime_tests.polyfill_tests.math_log2_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertExactlyNaN,
  noCheck,
} = testing;

testSuite({
  testLog2() {
    assertRoughlyEquals(2, Math.log2(4), 1e-6);
    assertRoughlyEquals(1000, Math.log2(Math.pow(2, 1000)), 1e-6);
    assertRoughlyEquals(0, Math.log2(1), 1e-6);
    assertRoughlyEquals(-2, Math.log2(0.25), 1e-6);

    assertEquals(-Infinity, Math.log2(0));
    assertEquals(Infinity, Math.log2(Infinity));
    assertExactlyNaN(Math.log2(-1));

    assertRoughlyEquals(2, Math.log2(noCheck('4')), 1e-6);
    assertEquals(-Infinity, Math.log2(noCheck(null)));

    assertRoughlyEquals(543, Math.pow(2, Math.log2(543)), 1e-6);
  },
});
