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
 * String.prototype.indexOf to determine whether one string may be found within
 * another string or not with String.prototype.includes.
 *
 * Note that String.prototype.includes is case sensitive.
 *
 * Available in:
 * - Chrome 41+
 * - Firefox 40+
 * - Edge (Yes)
 * - Opera n/a
 * - Safari 9+
 * - IE n/a
 *
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/includes
 *
 * This refactoring covers the most common cases in which this can be expressed:
 *
 * 'str.includes(subStr)' is equivalent to:
 * - str.indexOf(subStr) != -1
 * - str.indexOf(subStr) !== -1
 * - str.indexOf(subStr) > -1
 * - str.indexOf(subStr) >= 0
 *
 * '!str.includes(subStr)' is equivalent to:
 * - str.indexOf(subStr) == -1
 * - str.indexOf(subStr) === -1
 * - str.indexOf(subStr) < 0
 */

// subStr found in the string.

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function before_indexOfNotEqualsMinusOne(str, subStr) {
  str.indexOf(subStr) != -1;
}

/**
 * @param {string} str
 * @param {string} subStr
 */
function after_indexOfNotEqualsMinusOne(str, subStr) {
  str.includes(subStr);
}

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function before_indexOfStronglyNotEqualsMinusOne(str, subStr) {
  str.indexOf(subStr) !== -1;
}

/**
 * @param {string} str
 * @param {string} subStr
 */
function after_indexOfStronglyNotEqualsMinusOne(str, subStr) {
  str.includes(subStr);
}

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function before_indexOfGreaterThanMinusOne(str, subStr) {
  str.indexOf(subStr) > -1;
}

/**
 * @param {string} str
 * @param {string} subStr
 */
function after_indexOfGreaterThanMinusOne(str, subStr) {
  str.includes(subStr);
}

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function before_indexOfGreaterThanOrEqualsZero(str, subStr) {
  str.indexOf(subStr) >= 0;
}

/**
 * @param {string} str
 * @param {string} subStr
 */
function after_indexOfGreaterThanOrEqualsZero(str, subStr) {
  str.includes(subStr);
}

// subStr NOT found in the string.

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function before_indexOfEqualsMinusOne(str, subStr) {
  str.indexOf(subStr) == -1;
}

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function after_indexOfEqualsMinusOne(str, subStr) {
  !str.includes(subStr);
}

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function before_indexOfStronglyEqualsMinusOne(str, subStr) {
  str.indexOf(subStr) === -1;
}

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function after_indexOfStronglyEqualsMinusOne(str, subStr) {
  !str.includes(subStr);
}

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function before_indexOfLessThanZero(str, subStr) {
  str.indexOf(subStr) < 0;
}

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function after_indexOfLessThanZero(str, subStr) {
  !str.includes(subStr);
}

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function before_bitwiseNot(str, subStr) {
  ~str.indexOf(subStr);
}

/**
 * @param {string} str
 * @param {string} subStr
 * @suppress {uselessCode}
 */
function after_bitwiseNot(str, subStr) {
  str.includes(subStr);
}
