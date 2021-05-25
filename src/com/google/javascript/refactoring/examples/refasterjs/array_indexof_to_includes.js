/*
 * Copyright 2017 The Closure Compiler Authors.
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

/**
 * @fileoverview RefasterJS templates for replacing the usage of
 * Array.prototype.indexOf to determine whether an element is in the array or
 * not with Array.prototype.includes.
 *
 * Note that Array.prototype.includes handles NaN in a different way:
 * [1, 2, NaN].includes(NaN); // true
 * [1, 2, NaN].indexOf(NaN); // -1
 *
 * Available in:
 * - Chrome 47+
 * - Firefox 43+
 * - Edge 14+
 * - Opera 34+
 * - Safari 9+
 * - IE n/a
 *
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/includes
 *
 * This refactoring covers the most common cases in which this can be expressed:
 *
 * 'arr.includes(elem)' is equivalent to:
 * - arr.indexOf(elem) != -1
 * - arr.indexOf(elem) !== -1
 * - arr.indexOf(elem) > -1
 * - arr.indexOf(elem) >= 0
 *
 * '!arr.includes(elem)' is equivalent to:
 * - arr.indexOf(elem) == -1
 * - arr.indexOf(elem) === -1
 * - arr.indexOf(elem) < 0
 */

// elem found in the array.

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function before_indexOfNotEqualsMinusOne(arr, elem) {
  arr.indexOf(elem) != -1;
}

/**
 * @param {!Array} arr
 * @param {*} elem
 */
function after_indexOfNotEqualsMinusOne(arr, elem) {
  arr.includes(elem);
}

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function before_indexOfStronglyNotEqualsMinusOne(arr, elem) {
  arr.indexOf(elem) !== -1;
}

/**
 * @param {!Array} arr
 * @param {*} elem
 */
function after_indexOfStronglyNotEqualsMinusOne(arr, elem) {
  arr.includes(elem);
}

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function before_indexOfGreaterThanMinusOne(arr, elem) {
  arr.indexOf(elem) > -1;
}

/**
 * @param {!Array} arr
 * @param {*} elem
 */
function after_indexOfGreaterThanMinusOne(arr, elem) {
  arr.includes(elem);
}

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function before_indexOfGreaterThanOrEqualsZero(arr, elem) {
  arr.indexOf(elem) >= 0;
}

/**
 * @param {!Array} arr
 * @param {*} elem
 */
function after_indexOfGreaterThanOrEqualsZero(arr, elem) {
  arr.includes(elem);
}

// elem NOT found in the array.

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function before_indexOfEqualsMinusOne(arr, elem) {
  arr.indexOf(elem) == -1;
}

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function after_indexOfEqualsMinusOne(arr, elem) {
  !arr.includes(elem);
}

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function before_indexOfStronglyEqualsMinusOne(arr, elem) {
  arr.indexOf(elem) === -1;
}

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function after_indexOfStronglyEqualsMinusOne(arr, elem) {
  !arr.includes(elem);
}

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function before_indexOfLessThanZero(arr, elem) {
  arr.indexOf(elem) < 0;
}

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function after_indexOfLessThanZero(arr, elem) {
  !arr.includes(elem);
}

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function before_bitwiseNot(arr, elem) {
  ~arr.indexOf(elem);
}

/**
 * @param {!Array} arr
 * @param {*} elem
 * @suppress {uselessCode}
 */
function after_bitwiseNot(arr, elem) {
  arr.includes(elem);
}
