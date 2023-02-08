/*
 * Copyright 2018 The Closure Compiler Authors.
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

goog.module('jscomp.runtime_tests.polyfill_tests.array_flatmap_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');
// const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

testSuite({
  shouldRunTests() {
    // Disable tests for IE8 and below.
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testFlatMap_callbackParameters() {
    let arr = [1, 2, 3, 4];
    let repeated = arr.flatMap(element => {
      let mapped = [];
      for (let i = 0; i < element; i++) {
        mapped.push(element * 10 + i);
      }
      return mapped;
    });
    assertObjectEquals([10, 20, 21, 30, 31, 32, 40, 41, 42, 43], repeated);

    let summed = arr.flatMap((element, index) => [element + index]);
    assertObjectEquals([1, 3, 5, 7], summed);

    let nested =
        arr.flatMap((element, index, array) => [...arr, index, element]);
    assertObjectEquals(
        [...arr, 0, 1, ...arr, 1, 2, ...arr, 2, 3, ...arr, 3, 4], nested);
  },

  testFlatMap_onEmptyArray() {
    let result = [].flatMap(() => [1]);
    assertObjectEquals([], result);
  },

  testFlatMap_sparseArray() {
    const arr = [1, 2, , 3, 4];
    assertObjectEquals([1, 2, 3, 4], arr.flatMap(x => x));
  },

  testFlatMap_arrayWithNullAndUndefined() {
    const arr = [1, 2, undefined, null, 4];
    assertObjectEquals([1, 2, undefined, null, 4], arr.flatMap(x => x));
  },

  testFlatMap_thisArg() {
    let result = [1].flatMap(function() {
      return [this.x];
    }, {x: 0});
    assertObjectEquals([0], result);
  },

  testFlatMap_mixedCallbackReturnTypes() {
    // Confirm that flatMap() handles array and non-array returns from the
    // callback as required by the spec.
    let mixedArray = [
      0, 'string1', false, null, undefined, {key: 2}, [],
      [3, 'string4', false, null, undefined, {key: 5}, [6]]
    ];
    let result = mixedArray.flatMap(a => a);
    assertObjectEquals(
        [
          0, 'string1', false, null, undefined, {key: 2}, 3, 'string4', false,
          null, undefined, {key: 5}, [6]
        ],
        result);
  },

  /** @suppress {missingProperties} */
  testFlatMap_isConcatSpreadable() {
    var concatTrue =
        {[Symbol.isConcatSpreadable]: true, length: 2, 0: 10, 1: 11};
    var concatFalse =
        {[Symbol.isConcatSpreadable]: false, length: 2, 0: 12, 1: 13};
    var arrayConcatTrue = [1, 2, 3];
    var arrayConcatFalse = [4, 5, 6];
    arrayConcatFalse[Symbol.isConcatSpreadable] = false;

    let result = [
      concatTrue, concatFalse, arrayConcatTrue, arrayConcatFalse
    ].flatMap(a => a);
    assertObjectEquals([concatTrue, concatFalse, 1, 2, 3, 4, 5, 6], result);
  },
});
