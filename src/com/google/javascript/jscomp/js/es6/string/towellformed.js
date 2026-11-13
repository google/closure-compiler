/*
 * Copyright 2026 The Closure Compiler Authors.
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

/**
 * @fileoverview String.prototype.toWellFormed polyfill.
 * @suppress {uselessCode}
 */
'require util/checkstringargs';
'require util/polyfill';

$jscomp.polyfill('String.prototype.toWellFormed', function(orig) {
  if (orig) return orig;

  /**
   * Polyfills String.prototype.toWellFormed.
   *
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/toWellFormed
   *
   * @this {string}
   * @return {string}
   */
  var toWellFormed = function() {
    'use strict';
    var str = $jscomp.checkStringArgs(this, null, 'toWellFormed');
    var len = str.length;
    var result = '';
    var lastIdx = 0;
    // Terminology:
    //   "Unicode code point": a number representing a Unicode character.
    //   "Code unit": a 16-bit value representing part of a Unicode code
    //     point.
    //   "Surrogate pair": two code units that represent a single Unicode code
    //     point.
    //   "Leading surrogate": a high-surrogate code unit. Values between
    //     0xD800 and 0xDBFF, inclusive.
    //   "Trailing surrogate": a low-surrogate code unit. Values between
    //     0xDC00 and 0xDFFF, inclusive.
    //   "Surrogate": either a leading or trailing surrogate, thus in the
    //     range 0xD800 to 0xDFFF, inclusive.
    //   "Lone surrogate": either a leading surrogate that is not followed by a
    //     trailing surrogate; or a trailing surrogate that is not preceded by a
    //     leading surrogate.
    //   "Replacement character": U+FFFD, special character used to replace
    //     unrepresentable code points (https://unicode-explorer.com/c/FFFD).
    // See
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String#utf-16_characters_unicode_code_points_and_grapheme_clusters.
    for (var i = 0; i < len; i++) {
      var code = str.charCodeAt(i);
      // Is it a surrogate? (i.e. is it in the range 0xD800 to 0xDFFF?)
      if ((code & 0xF800) !== 0xD800) {
        // Not a surrogate!
        continue;
      }
      // Is it a leading surrogate? If so, is it followed by a trailing
      // surrogate at `i + 1`?
      if (code <= 0xDBFF && i + 1 < len &&
          (str.charCodeAt(i + 1) & 0xFC00) === 0xDC00) {
        // Valid surrogate pair.
        // Skip the trailing surrogate as well. (Otherwise when we visit it
        // next it will look like a lone surrogate.)
        i++;
        continue;
      }
      // We found a lone surrogate.
      // Replace it with the special 'replacement character'.
      result += str.slice(lastIdx, i) + '\uFFFD';
      lastIdx = i + 1;
    }
    return lastIdx === 0 ? str : result + str.slice(lastIdx);
  };

  return toWellFormed;
}, 'es_next', 'es3');
