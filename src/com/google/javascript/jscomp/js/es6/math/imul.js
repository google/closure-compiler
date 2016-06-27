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

$jscomp.polyfill('Math.imul', function(orig) {
  if (orig) return orig;

  /**
   * Performs C-like 32-bit signed integer multiplication.
   *
   * <p>Polyfills the static function Math.imul().
   *
   * @param {number} a Any number, or value that can be coerced to a number.
   * @param {number} b Any number, or value that can be coerced to a number.
   * @return {number} The 32-bit integer product of a and b.
   */
  var polyfill = function(a, b) {
    // This algorithm is taken from v8.
    // Note: If multiplication overflows 32 bits, then we risk losing
    // precision.  We must therefore break the inputs into 16-bit
    // words and multiply separately.
    a = Number(a);
    b = Number(b);
    var ah = (a >>> 16) & 0xFFFF;  // Treat individual words as unsigned
    var al = a & 0xFFFF;
    var bh = (b >>> 16) & 0xFFFF;
    var bl = b & 0xFFFF;
    var lh = ((ah * bl + al * bh) << 16) >>> 0;  // >>> 0 casts to uint
    return (al * bl + lh) | 0;  // | 0 casts back to signed
  };

  return polyfill;
}, 'es6-impl', 'es3');
