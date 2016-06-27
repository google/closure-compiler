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

$jscomp.polyfill('String.prototype.startsWith', function(orig) {
  if (orig) return orig;

  /**
   * Tests whether the string starts with a given substring.
   *
   * <p>Polyfills the instance method String.prototype.startsWith().
   *
   * @this {string}
   * @param {string} searchString
   * @param {number=} opt_position
   * @return {boolean}
   */
  var polyfill = function(searchString, opt_position) {
    'use strict';
    var string = $jscomp.checkStringArgs(this, searchString, 'startsWith');
    searchString = searchString + '';
    var strLen = string.length;
    var searchLen = searchString.length;
    var i = Math.max(
        0,
        Math.min(/** @type {number} */ (opt_position) | 0, string.length));
    var j = 0;
    while (j < searchLen && i < strLen) {
      if (string[i++] != searchString[j++]) return false;
    }
    return j >= searchLen;
  };

  return polyfill;
}, 'es6-impl', 'es3');
