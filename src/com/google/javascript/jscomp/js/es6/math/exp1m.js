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

$jscomp.polyfill('Math.expm1', function(orig) {
  if (orig) return orig;

  /**
   * Exponentiates x and then subtracts one.  This is implemented in a
   * way that is accurate for numbers close to zero.
   *
   * <p>Polyfills the static function Math.expm1().
   *
   * @param {number} x Any number, or value that can be coerced to a number.
   * @return {number} The exponential of x, less 1.
   */
  var polyfill = function(x) {
    // This implementation is based on the Taylor expansion
    //   exp(x) ~ 1 + x + x^2/2 + x^3/6 + x^4/24 + ...
    x = Number(x);
    if (x < .25 && x > -.25) {
      var y = x;
      var d = 1;
      var z = x;
      var zPrev = 0;
      while (zPrev != z) {
        y *= x / (++d);
        z = (zPrev = z) + y;
      }
      return z;
    }
    return Math.exp(x) - 1;
  };

  return polyfill;
}, 'es6-impl', 'es3');
