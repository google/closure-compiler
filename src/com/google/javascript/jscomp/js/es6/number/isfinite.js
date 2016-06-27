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

$jscomp.polyfill('Number.isFinite', function(orig) {
  if (orig) return orig;

  /**
   * Returns whether the given argument is a finite number.
   *
   * <p>Polyfills the static function Number.isFinite().
   *
   * @param {number} x Any value.
   * @return {boolean} True if x is a number and not NaN or infinite.
   */
  var polyfill = function(x) {
    if (typeof x !== 'number') return false;
    return !isNaN(x) && x !== Infinity && x !== -Infinity;
  };

  return polyfill;
}, 'es6-impl', 'es3');
