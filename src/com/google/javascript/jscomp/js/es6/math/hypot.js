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
   * @param {...number} var_args Any number, or value that can be coerced to a
   *     number.
   * @return {number} The square root of the sum of the squares.
   */
  var polyfill = function(var_args) {
    if (arguments.length < 2) {
      return arguments.length ? Math.abs(arguments[0]) : 0;
    }

    var i, z, sum, max;
    // Note: we need to normalize the numbers in case of over/underflow.
    for (max = 0, i = 0; i < arguments.length; i++) {
      max = Math.max(max, Math.abs(arguments[i]));
    }
    // TODO(sdh): Document where these constants come from.
    if (max > 1e100 || max < 1e-100) {
      if (!max) return max; // Handle 0 and NaN before trying to divide.
      sum = 0;
      for (i = 0; i < arguments.length; i++) {
        z = Number(arguments[i]) / max;
        sum += z * z;
      }
      return Math.sqrt(sum) * max;
    } else {
      sum = 0;
      for (i = 0; i < arguments.length; i++) {
        z = Number(arguments[i]);
        sum += z * z;
      }
      return Math.sqrt(sum);
    }
  };

  return polyfill;
}, 'es6', 'es3');
