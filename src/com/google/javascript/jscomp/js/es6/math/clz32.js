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

$jscomp.polyfill('Math.clz32', function(orig) {
  if (orig) return orig;

  /**
   * Counts the leading zeros in the 32-bit binary representation.
   *
   * <p>Polyfills the static function Math.clz32().
   *
   * @param {number} x Any number, or value that can be coerced to a number.
   * @return {number} The number of leading zero bits.
   */
  var polyfill = function(x) {
    // This binary search algorithm is taken from v8.
    x = Number(x) >>> 0;  // first ensure we have a 32-bit unsigned integer.
    if (x === 0) return 32;
    var result = 0;
    if ((x & 0xFFFF0000) === 0) {
      x <<= 16;
      result += 16;
    }
    if ((x & 0xFF000000) === 0) {
      x <<= 8;
      result += 8;
    }
    if ((x & 0xF0000000) === 0) {
      x <<= 4;
      result += 4;
    }
    if ((x & 0xC0000000) === 0) {
      x <<= 2;
      result += 2;
    }
    if ((x & 0x80000000) === 0) result++;
    return result;
  };

  return polyfill;
}, 'es6-impl', 'es3');
