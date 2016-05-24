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

goog.module('jscomp.runtime_tests.polyfill_tests.number_issafeinteger_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  noCheck,
} = testing;

testSuite({
  testIsSafeInteger() {
    assertTrue(Number.isSafeInteger(0));
    assertTrue(Number.isSafeInteger(1));
    assertTrue(Number.isSafeInteger(-1));
    assertTrue(Number.isSafeInteger(100));

    const overflow = Math.pow(2, 53);
    assertTrue(Number.isSafeInteger(overflow - 1));
    assertTrue(Number.isSafeInteger(-overflow + 1));
    assertFalse(Number.isSafeInteger(overflow + 1));
    assertFalse(Number.isSafeInteger(-overflow));

    assertFalse(Number.isSafeInteger(Infinity));
    assertFalse(Number.isSafeInteger(-Infinity));
    assertFalse(Number.isSafeInteger(NaN));
    assertFalse(Number.isSafeInteger(1e300));
    assertFalse(Number.isSafeInteger(1.2));
    assertFalse(Number.isSafeInteger(noCheck('42')));
  },
});
