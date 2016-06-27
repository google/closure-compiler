/*
 * Copyright 2016 The Closure Compiler Authors.
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

'require util/findinternal util/polyfill';

$jscomp.polyfill('Array.prototype.find', function(orig) {
  if (orig) return orig;

  /**
   * Finds and returns an element that satisfies the given predicate.
   *
   * @this {!IArrayLike<VALUE>}
   * @param {function(this: THIS, VALUE, number, !IArrayLike<VALUE>): *}
   *     callback
   * @param {THIS=} opt_thisArg
   * @return {VALUE|undefined} The found value, or undefined.
   * @template VALUE, THIS
   */
  var polyfill = function(callback, opt_thisArg) {
    return $jscomp.findInternal(this, callback, opt_thisArg).v;
  };

  return polyfill;
}, 'es6-impl', 'es3');
