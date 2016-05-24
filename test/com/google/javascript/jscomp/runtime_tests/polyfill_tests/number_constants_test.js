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

goog.module('jscomp.runtime_tests.polyfill_tests.number_constants_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testEpsilon() {
    assertEquals(1, 1 + (Number.EPSILON / 2));
    assertNotEquals(1, 1 + (Number.EPSILON * 0.5000001));
    assertEquals(Number.EPSILON, (1 + (Number.EPSILON * 0.5000001)) - 1);
  },


  testMaxSafeInteger() {
    assertEquals(Number.MAX_SAFE_INTEGER + 1, Number.MAX_SAFE_INTEGER + 2);
    assertNotEquals(Number.MAX_SAFE_INTEGER, Number.MAX_SAFE_INTEGER + 1);
    assertNotEquals(Number.MAX_SAFE_INTEGER, Number.MAX_SAFE_INTEGER - 1);
  },


  testMinSafeInteger() {
    assertEquals(Number.MIN_SAFE_INTEGER - 1, Number.MIN_SAFE_INTEGER - 2);
    assertNotEquals(Number.MIN_SAFE_INTEGER, Number.MIN_SAFE_INTEGER - 1);
    assertNotEquals(Number.MIN_SAFE_INTEGER, Number.MIN_SAFE_INTEGER + 1);
  },
});
