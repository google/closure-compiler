/*
 * Copyright 2022 The Closure Compiler Authors.
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
 * @fileoverview Array.prototype.at polyfill.
 * @suppress {uselessCode}
 */
'require util/polyfill';

$jscomp.polyfill('Array.prototype.at', function(orig) {
  if (orig) return orig;

  /**
   * Polyfills Array.prototype.at.
   *
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/at
   *
   * @this {!IArrayLike<T>}
   * @param {number} index
   * @return {T}
   * @template T
   */
  var at = function(index) {
    var array = this;

    var indexValue = Math.trunc(index) || 0;
    // Allow negative indexing from the end
    if (indexValue < 0) indexValue += array.length;
    // OOB access is guaranteed to return undefined
    if (indexValue < 0 || indexValue >= array.length) return undefined;
    // Otherwise, this is just normal property access
    return array[indexValue];
  };

  return at;
}, 'es_next', 'es3');
