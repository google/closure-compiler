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

$jscomp.polyfill('Math.hypot', function(orig) {
  if (orig) return orig;

  /**
   * Returns the sum of its arguments in quadrature.
   *
   * <p>Polyfills the static function Math.hypot().
   *
   * @param {number} x Any number, or value that can be coerced to a number.
   * @param {number} y Any number, or value that can be coerced to a number.
   * @param {...*} var_args More numbers.
   * @return {number} The square root of the sum of the squares.
   */
  var polyfill = function(x, y, var_args) {
    // Make the type checker happy.
    x = Number(x);
    y = Number(y);
    var i, z, sum;
    // Note: we need to normalize the numbers in case of over/underflow.
    var max = Math.max(Math.abs(x), Math.abs(y));
    for (i = 2; i < arguments.length; i++) {
      max = Math.max(max, Math.abs(arguments[i]));
    }
    if (max > 1e100 || max < 1e-100) {
      x = x / max;
      y = y / max;
      sum = x * x + y * y;
      for (i = 2; i < arguments.length; i++) {
        z = Number(arguments[i]) / max;
        sum += z * z;
      }
      return Math.sqrt(sum) * max;
    } else {
      sum = x * x + y * y;
      for (i = 2; i < arguments.length; i++) {
        z = Number(arguments[i]);
        sum += z * z;
      }
      return Math.sqrt(sum);
    }
  };

  return polyfill;
}, 'es6-impl', 'es3');
