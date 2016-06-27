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

'require util/checkstringargs util/polyfill';

$jscomp.polyfill('String.prototype.repeat', function(orig) {
  if (orig) return orig;

  /**
   * Returns a new string repeated the given number of times.
   *
   * <p>Polyfills the instance method String.prototype.repeat().
   *
   * @this {string}
   * @param {number} copies
   * @return {string}
   */
  var polyfill = function(copies) {
    'use strict';
    var string = $jscomp.checkStringArgs(this, null, 'repeat');
    if (copies < 0 || copies > 0x4FFFFFFF) { // impose a 1GB limit
      throw new RangeError('Invalid count value');
    }
    copies = copies | 0; // cast to a signed integer.
    var result = '';
    while (copies) {
      if (copies & 1) result += string;
      if ((copies >>>= 1)) string += string;
    }
    return result;
  };

  return polyfill;
}, 'es6-impl', 'es3');
