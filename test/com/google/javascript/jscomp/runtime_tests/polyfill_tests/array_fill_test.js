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

goog.module('jscomp.runtime_tests.polyfill_tests.array_fill_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  noCheck,
} = testing;

testSuite({
  shouldRunTests() {
    // Disable tests for IE8 and below.
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testFill() {
    function fill(arr, ...args) {
      assertEquals(arr, arr.fill(...args));
      return arr;
    }

    assertObjectEquals([9, 9, 9], fill([1, 2, 3], 9));
    assertObjectEquals([9, 9, 9, 9], fill(new Array(4), 9));
    assertObjectEquals([,, 9, 9], fill(new Array(4), 9, 2));
    assertObjectEquals([9, 9,,,], fill(new Array(4), 9, 0, 2));

    assertObjectEquals([1, 2, 9, 9, 9], fill([1, 2, 3, 4, 5], 9, 2));
    assertObjectEquals([1, 9, 9, 9, 5], fill([1, 2, 3, 4, 5], 9, 1, 4));
    assertObjectEquals([9, 9, 3, 4, 5], fill([1, 2, 3, 4, 5], 9, 0, 2));
    assertObjectEquals([1, 2, 3, 9, 9], fill([1, 2, 3, 4, 5], 9, -2));
    assertObjectEquals([1, 9, 9, 9, 9], fill([1, 2, 3, 4, 5], 9, -4));
    assertObjectEquals([1, 2, 9, 4, 5], fill([1, 2, 3, 4, 5], 9, -3, 3));
    assertObjectEquals([1, 9, 9, 9, 5], fill([1, 2, 3, 4, 5], 9, -4, -1));
    assertObjectEquals([1, 2, 9, 4, 5], fill([1, 2, 3, 4, 5], 9, 2, -2));

    assertObjectEquals([1, 2, 3], fill([1, 2, 3], 9, 5));
    assertObjectEquals([1, 2, 3], fill([1, 2, 3], 9, 5, 1));
    assertObjectEquals([1, 2, 3], fill([1, 2, 3], 9, 0, 0));
    assertObjectEquals([1, 2, 3], fill([1, 2, 3], 9, 1, 1));
    assertObjectEquals([1, 2, 9], fill([1, 2, 3], 9, 2, 5));
    assertObjectEquals([1, 2, 3], fill([1, 2, 3], 9, -6, -4));
    assertObjectEquals([9, 2, 3], fill([1, 2, 3], 9, -6, -2));

    assertObjectEquals([1, 2, 3], fill([1, 2, 3], 9, NaN, NaN));
    assertObjectEquals([1, 2, 3], fill([1, 2, 3], 9, 1, NaN));
    assertObjectEquals([9, 2, 3], fill([1, 2, 3], 9, NaN, 1));
  },

  testFill_arrayLike() {
    const arr = {length: 3, 1: 'x', 3: 'safe'};
    assertEquals(arr, Array.prototype.fill.call(arr, 'y'));
    assertObjectEquals({0: 'y', 1: 'y', 2: 'y', 3: 'safe', length: 3}, arr);

    assertEquals(arr, Array.prototype.fill.call(arr, 'z', undefined, 2));
    assertObjectEquals({0: 'z', 1: 'z', 2: 'y', 3: 'safe', length: 3}, arr);
  },

  testFill_notArrayLike() {
    const arr = {2: 'x'}; // does nothing if no length
    assertEquals(arr, Array.prototype.fill.call(noCheck(arr), 'y', 0, 4));
  },
});
