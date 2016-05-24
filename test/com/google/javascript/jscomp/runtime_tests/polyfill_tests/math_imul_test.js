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

goog.module('jscomp.runtime_tests.polyfill_tests.math_imul_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertPositiveZero,
  noCheck,
} = testing;

testSuite({
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
    assertEquals(20, Math.imul(noCheck([5]), noCheck('4')));
    assertPositiveZero(Math.imul(noCheck([5]), noCheck([4, 6])));
  },
});
