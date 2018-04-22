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

goog.module('jscomp.runtime_tests.polyfill_tests.array_copywithin_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const getKeys = testing.getKeys;
const noCheck = testing.noCheck;

testSuite({
  shouldRunTests() {
    // Disable tests for IE8 and below.
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testCopyWithin() {
    let arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(0, 3, 5));
    assertObjectEquals([2, 1, 3, 2, 1, 0], arr);

    arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(0, 3));
    assertObjectEquals([2, 1, 0, 2, 1, 0], arr);

    arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(3, 1, 3));
    assertObjectEquals([5, 4, 3, 4, 3, 0], arr);

    arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(3, 1));
    assertObjectEquals([5, 4, 3, 4, 3, 2], arr);

    arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(1, 0));
    assertObjectEquals([5, 5, 4, 3, 2, 1], arr);

    arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(0, 1));
    assertObjectEquals([4, 3, 2, 1, 0, 0], arr);

    arr = [];
    arr[4] = 42;
    arr[2] = 21;
    assertEquals(arr, arr.copyWithin(0, 1));
    assertObjectEquals(['1', '3', '4'], getKeys(arr));

    arr = {length: 3, 1: 4, 3: 'unused'};
    assertEquals(arr, Array.prototype.copyWithin.call(noCheck(arr), 0, 1));
    assertObjectEquals({length: 3, 0: 4, 3: 'unused'}, arr);

    arr = {length: 3, 1: 4, 3: 'unused'};
    assertEquals(arr, Array.prototype.copyWithin.call(noCheck(arr), 1, 0));
    assertObjectEquals({length: 3, 2: 4, 3: 'unused'}, arr);
  },

  testCopyWithin_coercingArgs() {
    assertObjectEquals([1, 2, 3, 3], [0, 1, 2, 3].copyWithin(NaN, 1));
    assertObjectEquals([1, 2, 3, 3], [0, 1, 2, 3].copyWithin(0.5, 1));
    assertObjectEquals([0, 0, 1, 2], [0, 1, 2, 3].copyWithin(1.5, 0));

    assertObjectEquals([0, 0, 1, 2], [0, 1, 2, 3].copyWithin(1, NaN));
    assertObjectEquals([0, 0, 1, 2], [0, 1, 2, 3].copyWithin(1, 0.5));
    assertObjectEquals([1, 2, 3, 3], [0, 1, 2, 3].copyWithin(0, 1.5));

    assertObjectEquals([0, 1, 2, 3], [0, 1, 2, 3].copyWithin(1, 0, NaN));
    assertObjectEquals([0, 0, 2, 3], [0, 1, 2, 3].copyWithin(1, 0, 1.5));
    assertObjectEquals([0, 0, 1, 3], [0, 1, 2, 3].copyWithin(1, 0, -2.5));
    assertObjectEquals([0, 0, 1, 2], [0, 1, 2, 3].copyWithin(1, 0, undefined));
  },

  testCopyWithin_negativeArgs() {
    assertObjectEquals([1, 2, 3, 1, 2], [1, 2, 3, 4, 5].copyWithin(-2, 0));
    assertObjectEquals([1, 3, 4, 5, 5], [1, 2, 3, 4, 5].copyWithin(1, -3));
    assertObjectEquals([1, 3, 4, 4, 5], [1, 2, 3, 4, 5].copyWithin(1, 2, -1));
  },

  testCopyWithin_throwsIfNullish() {
    assertThrows(function() {
      Array.prototype.copyWithin.call(null);
    });
    assertThrows(function() {
      Array.prototype.copyWithin.call(undefined);
    });
  },

  testCopyWithin_throwIfFailToDeleteProperty() {
    var obj = {
      length: 5
    };

    Object.defineProperty(obj, 4, {
      value: 'a',
      configurable: false,
      writable: true
    });

    assertThrows(function() {
      Array.prototype.copyWithin.call(noCheck(obj), 4, 0);
    });
  },
});
