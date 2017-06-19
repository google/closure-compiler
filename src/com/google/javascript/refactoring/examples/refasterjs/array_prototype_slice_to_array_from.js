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
 * Array.prototype.slice to convert an array-like object to an array with
 * Array.from.
 *
 * Array.prototype.slice can be executed on the following types:
 * - IArrayLike - Converts the array-like object to an Array.
 * - Array - Creates a shallow copy of the array.
 * - string - Creates an Array where each element is a character.
 *   'abc' -> ['a', 'b', 'c']
 *
 * 'Array.from(arrLike)' is equivalent to:
 * - Array.prototype.slice(arrLike)
 * - Array.prototype.slice(arrLike, 0)
 *
 * Available in:
 * - Chrome 45+
 * - Firefox 32+
 * - Edge (Yes)
 * - Opera (Yes)
 * - Safari 9.0+
 * - IE n/a
 *
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/from
 */

/**
 * @param {!(Array|IArrayLike|string)} arrLike
 */
function before_sliceNoArguments(arrLike) {
  Array.prototype.slice.call(arrLike);
}

/**
 * @param {!(Array|IArrayLike|string)} arrLike
 */
function after_sliceNoArguments(arrLike) {
  Array.from(arrLike);
}

/**
 * @param {!(Array|IArrayLike|string)} arrLike
 */
function before_sliceZero(arrLike) {
  Array.prototype.slice.call(arrLike, 0);
}

/**
 * @param {!(Array|IArrayLike|string)} arrLike
 */
function after_sliceZero(arrLike) {
  Array.from(arrLike);
}
