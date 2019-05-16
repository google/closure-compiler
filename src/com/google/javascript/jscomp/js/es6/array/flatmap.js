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

/** @fileoverview @suppress {uselessCode} */
'require util/polyfill';

$jscomp.polyfill('Array.prototype.flatMap', function(orig) {
  if (orig) return orig;

  /**
   * Polyfills Array.prototype.flatMap.
   *
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/flatMap
   *
   * @param {function(this: THIS, T, number, !IArrayLike<T>): !Array<S>} callback
   * @param {THIS=} thisArg
   * @return {!Array<S>}
   * @this {!IArrayLike<T>}
   * @template T, THIS, S
   * @suppress {reportUnknownTypes}
   */
  var flatMap = function(callback, thisArg) {
    var mapped = [];
    for (var i = 0; i < this.length; i++) {
      var result = callback.call(thisArg, this[i], i, this);
      if (Array.isArray(result)) {
        mapped.push.apply(mapped, result);
      } else {
        // NOTE: The specification says the callback can return a non-Array.
        // We intentionally don't include that in the type information on
        // this function or the corresponding extern in order to encourage
        // more readable code and avoid complex TTL in the type annotations,
        // but we still want to behave correctly if the callback gives us a
        // non-Array.
        mapped.push(result);
      }
    }
    return mapped;
  };

  return flatMap;
}, 'es9', 'es5');
