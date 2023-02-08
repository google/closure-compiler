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

goog.module('jscomp.runtime_tests.polyfill_tests.array_flat_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');

testSuite({
  shouldRunTests() {
    // Disable tests for IE8 and below.
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testFlat_depth1() {
    var arr = [1, 2, [3, 4, [5, 6]]];
    assertObjectEquals([1, 2, 3, 4, [5, 6]], arr.flat());
    assertObjectEquals([1, 2, 3, 4, [5, 6]], arr.flat(1));
  },

  test_sparseArray() {
    const arr = [1, 2, , 3, 4];
    assertObjectEquals([1, 2, 3, 4], arr.flat());
  },

  test_arrayWithNullAndUndefined() {
    const arr = [1, 2, 3, undefined, null, 4];
    assertObjectEquals([1, 2, 3, undefined, null, 4], arr.flat());
  },

  testFlat_emptyArray() {
    var arr = [];
    assertObjectEquals([], arr.flat());
  },

  testFlat_mixedTypes() {
    var arr = [[1], ['2'], [true, null, undefined, {'three': 4}]];
    assertObjectEquals(
        [1, '2', true, null, undefined, {'three': 4}], arr.flat());
  },

  testFlat_depth0OrLess() {
    var arr = [[1], ['2'], [true, null, undefined, {'three': 4}]];
    assertObjectEquals(
        [[1], ['2'], [true, null, undefined, {'three': 4}]], arr.flat(0));
    assertObjectEquals(
        [[1], ['2'], [true, null, undefined, {'three': 4}]], arr.flat(-1));
    assertObjectEquals(
        [[1], ['2'], [true, null, undefined, {'three': 4}]], arr.flat(-2));
  },

  testFlat_depth2OrGreater() {
    var arr = [1, 2, [3, 4, [5, 6, [7, 8]]]];
    assertObjectEquals([1, 2, 3, 4, 5, 6, [7, 8]], arr.flat(2));
    assertObjectEquals([1, 2, 3, 4, 5, 6, 7, 8], arr.flat(3));
    assertObjectEquals([1, 2, 3, 4, 5, 6, 7, 8], arr.flat(4));
  },

  testFlat_makesShalowCopies() {
    var arr = [1, 2, [3, 4]];
    var copy = arr.flat(0);

    arr[0] = 10;
    copy[1] = 20;
    arr[2][0] = 30;
    copy[2][1] = 40;

    assertObjectEquals([10, 2, [30, 40]], arr);
    assertObjectEquals([1, 20, [30, 40]], copy);
  },

  /** @suppress {missingProperties} */
  testFlat_isConcatSpreadable() {
    var concatTrue =
        {[Symbol.isConcatSpreadable]: true, length: 2, 0: 10, 1: 11};
    var concatFalse =
        {[Symbol.isConcatSpreadable]: false, length: 2, 0: 12, 1: 13};
    var arrayConcatTrue = [1, 2, 3];
    var arrayConcatFalse = [4, 5, 6];
    arrayConcatFalse[Symbol.isConcatSpreadable] = false;

    var result =
        [concatTrue, concatFalse, arrayConcatTrue, arrayConcatFalse].flat();
    assertObjectEquals([concatTrue, concatFalse, 1, 2, 3, 4, 5, 6], result);
  },

  testFlat_arraySubclassWithOverriddenPush() {
    /**
     * @param {T} element
     * @return {T}
     * @this {!IArrayLike<T>}
     * @template T
     */
    var customPush = function(element) {
      Array.prototype.push.call(this, element);
      return Array.prototype.push.call(this, element);
    };

    var inner = [2];
    var outer = [3, inner];

    inner.push = customPush.bind(inner);
    outer.push = customPush.bind(outer);

    assertObjectEquals([3, 2], outer.flat());
  },

  /** @suppress {checkTypes} */
  testFlat_arraySubclassWithOverriddenFlat() {
    /**
     * @return {!Array<T>}
     * @this {!Array<?>}
     * @template T
     */
    var customFlat = function() {
      return Array.prototype.concat.call(this, 'very flat');
    };

    var inner = [2];
    var outer = [3, inner];

    inner.flat = customFlat.bind(inner);

    assertObjectEquals([3, 2], outer.flat());
  },
});
