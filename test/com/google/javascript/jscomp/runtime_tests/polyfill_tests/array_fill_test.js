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
  assertDeepEquals,
  noCheck,
} = testing;

testSuite({
  shouldRunTests() {
    // Disable tests for IE8 and below.
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testFill() {
    let arr = [];
    arr[6] = 42;
    assertEquals(arr, arr.fill('x', 2, 5));
    assertDeepEquals([,, 'x', 'x', 'x',, 42], arr);

    assertEquals(arr, arr.fill('y', 4));
    assertDeepEquals([,, 'x', 'x', 'y', 'y', 'y'], arr);

    assertEquals(arr, arr.fill('z'));
    assertDeepEquals(['z', 'z', 'z', 'z', 'z', 'z', 'z'], arr);

    arr = {length: 3, 1: 'x', 3: 'safe'};
    assertEquals(arr, Array.prototype.fill.call(arr, 'y'));
    assertDeepEquals({0: 'y', 1: 'y', 2: 'y', 3: 'safe', length: 3}, arr);

    assertEquals(arr, Array.prototype.fill.call(arr, 'z', void 0, 2));
    assertDeepEquals({0: 'z', 1: 'z', 2: 'y', 3: 'safe', length: 3}, arr);

    arr = {2: 'x'}; // does nothing if no length
    assertEquals(arr, Array.prototype.fill.call(noCheck(arr), 'y', 0, 4));
  },
});
