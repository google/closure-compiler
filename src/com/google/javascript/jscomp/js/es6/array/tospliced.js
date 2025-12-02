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

'require es6/array/from';
'require util/polyfill';

$jscomp.polyfill('Array.prototype.toSpliced', function(orig) {
  if (orig) return orig;

  /**
   * Returns a new array with some elements removed and/or replaced at a given
   * index.
   *
   * Note: This polyfill purposefully is NOT spec compliant in order to minimize
   * code size. See skipped unit tests for specific gaps.
   *
   * @see https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.tospliced
   *
   * @param {number} start
   * @param {number=} skipCount
   * @param {...T} var_toAdd
   * @return {!Array<T>}
   * @this {!IArrayLike<T>}
   * @template T
   */
  var polyfill = function(start, skipCount, var_toAdd) {
    var newArray = Array.from(this);
    // We call apply here to simplify passing on the arguments.
    Array.prototype.splice.apply(newArray, arguments);
    return newArray;
  };

  return polyfill;
}, 'es_next', 'es3');
