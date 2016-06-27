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

$jscomp.polyfill('Number.isNaN', function(orig) {
  if (orig) return orig;

  /**
   * Returns whether the given argument is the value NaN,
   * guaranteeing not to coerce to a number first.
   *
   * <p>Polyfills the static function Number.isNaN().
   *
   * @param {number} x Any value.
   * @return {boolean} True if x is exactly NaN.
   */
  var polyfill = function(x) {
    return typeof x === 'number' && isNaN(x);
  };

  return polyfill;
}, 'es6-impl', 'es3');
