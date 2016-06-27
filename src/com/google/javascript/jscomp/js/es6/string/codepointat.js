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

$jscomp.polyfill('String.prototype.codePointAt', function(orig) {
  if (orig) return orig;

  /**
   * Returns the UTF-16 codepoint at the given index.
   *
   * <p>Polyfills the instance method String.prototype.codePointAt().
   *
   * @this {string}
   * @param {number} position
   * @return {number|undefined} The codepoint.
   */
  var polyfill = function(position) {
    // NOTE: this is taken from v8's harmony-string.js StringCodePointAt
    'use strict';
    var string = $jscomp.checkStringArgs(this, null, 'codePointAt');
    var size = string.length;
    // Make 'position' a number (non-number coerced to NaN and then or to zero).
    position = Number(position) || 0;
    if (!(position >= 0 && position < size)) {
      return void 0;
    }
    // Truncate 'position' to an integer.
    position = position | 0;
    var first = string.charCodeAt(position);
    if (first < 0xD800 || first > 0xDBFF || position + 1 === size) {
      return first;
    }
    var second = string.charCodeAt(position + 1);
    if (second < 0xDC00 || second > 0xDFFF) {
      return first;
    }
    return (first - 0xD800) * 0x400 + second + 0x2400;
  };

  return polyfill;
}, 'es6-impl', 'es3');
