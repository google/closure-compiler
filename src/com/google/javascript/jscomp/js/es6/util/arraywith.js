/*
 * Copyright 2025 The Closure Compiler Authors.
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
 * @fileoverview Helper for Array.prototype.with and TypedArray.prototype.with.
 * @suppress {uselessCode}
 */

'require base';
'require util/tointegerorinfinity';

/**
 * @param {!A} arr Should be a copy of the original array.
 * @param {number} index
 * @param {*} value
 * @return {!A} The passed-in arr now modified.
 * @template A
 */
$jscomp.arrayWith = function(arr, index, value) {
  var actualIndex = $jscomp.toIntegerOrInfinity(index);
  if (actualIndex < 0) {
    actualIndex += arr.length;
  }

  // with is stricter than bracket assignment so we need a range check.
  if (actualIndex < 0 || actualIndex >= arr.length) {
    throw new RangeError('Invalid index : ' + index);
  }

  arr[actualIndex] = value;
  return arr;
};
