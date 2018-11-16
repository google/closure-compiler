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
'require util/defines';

$jscomp.polyfill('Math.fround', function(orig) {
  if (orig) return orig;

  if ($jscomp.SIMPLE_FROUND_POLYFILL || typeof Float32Array !== 'function') {
    // Explicitly requested a no-op polyfill, or Float32Array not available.
    return /** @return {number} */ function(/** number */ arg) {
      return arg;
    };
  }

  var arr = new Float32Array(1);
  /**
   * Rounds the given double-precision number to single-precision (float32).
   *
   * Polyfills the static function Math.fround().
   *
   * This polyfill is slightly incorrect for IE8 and IE9, where it performs no
   * rounding at all. This is generally not a problem, since Math.fround is
   * primarily used for optimization (to force faster 32-bit operations rather
   * than 64-bit), but in cases where (a) the logic actually depends on a
   * correct fround implementation and (b) the application targets very old
   * browsers, this polyfill will be insufficient.  For that case, see
   * https://gist.github.com/shicks/7a97ec6b3f10212e60a89a7f6d2d097d for a
   * more correct polyfill that does not depend on Float32Array.
   *
   * @param {number} arg A 64-bit double-precision number.
   * @return {number} The closest float32 to the argument.
   */
  var polyfill = function(arg) {
    arr[0] = arg;
    return arr[0];
  };
  return polyfill;
}, 'es6', 'es3');
