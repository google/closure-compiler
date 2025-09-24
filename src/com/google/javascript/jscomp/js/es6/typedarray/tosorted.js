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

'require util/polyfill';

/**
 * @param {*} orig
 * @return {*}
 */
$jscomp.typedArrayToSorted = function(orig) {
  if (orig) return orig;

  /**
   * Returns a new typed array with all elements sorted.
   *
   * Note: This polyfill purposefully is NOT spec compliant in order to minimize
   * code size. See skipped unit tests for specific gaps.
   *
   * @see https://tc39.es/ecma262/multipage/indexed-collections.html#sec-%typedarray%.prototype.tosorted
   *
   * @param {function(number, number): number=} compareFn A function tha
   *     defines the sort order.
   * @return {THIS}
   * @this {THIS}
   * @template THIS
   */
  var polyfill = function(compareFn) {
    return this.slice().sort(compareFn);
  };

  return polyfill;
};

$jscomp.polyfillTypedArrayMethod(
    'toSorted', $jscomp.typedArrayToSorted, 'es_next', 'es5');
