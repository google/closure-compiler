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

'require util/polyfill';

$jscomp.polyfill('Math.sign', function(orig) {
  if (orig) return orig;

  /**
   * Returns the sign of the number, indicating whether it is
   * positive, negative, or zero.
   *
   * <p>Polyfills the static function Math.sign().
   *
   * @param {number} x Any number, or value that can be coerced to a number.
   * @return {number} The sign, +1 if x is positive, -1 if x is
   *     negative, or 0 if x is zero.
   */
  var polyfill = function(x) {
    x = Number(x);
    return x === 0 || isNaN(x) ? x : x > 0 ? 1 : -1;
  };

  return polyfill;
}, 'es6-impl', 'es3');
