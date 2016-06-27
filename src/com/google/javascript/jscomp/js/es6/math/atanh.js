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

'require util/polyfill es6/math/log1p';

$jscomp.polyfill('Math.atanh', function(orig) {
  if (orig) return orig;
  var log1p = Math.log1p;

  /**
   * Computes the inverse hyperbolic tangent.
   *
   * <p>Polyfills the static function Math.atanh().
   *
   * @param {number} x Any number, or value that can be coerced to a number.
   * @return {number} The inverse hyperbolic tangent +x.
   */
  var polyfill = function(x) {
    x = Number(x);
    return (log1p(x) - log1p(-x)) / 2;
  };

  return polyfill;
}, 'es6-impl', 'es3');
