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

$jscomp.polyfill('Math.trunc', function(orig) {
  if (orig) return orig;

  /**
   * Truncates any fractional digits from its argument (towards zero).
   *
   * <p>Polyfills the static function Math.trunc().
   *
   * @param {number} x Any number, or value that can be coerced to a number.
   * @return {number}
   */
  var polyfill = function(x) {
    x = Number(x);
    if (isNaN(x) || x === Infinity || x === -Infinity || x === 0) return x;
    var y = Math.floor(Math.abs(x));
    return x < 0 ? -y : y;
  };

  return polyfill;
}, 'es6-impl', 'es3');
