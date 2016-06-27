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

$jscomp.polyfill('String.fromCodePoint', function(orig) {
  if (orig) return orig;

  /**
   * Creates a new string from the given codepoints.
   *
   * <p>Polyfills the static function String.fromCodePoint().
   *
   * @param {...number} var_args
   * @return {string}
   */
  var polyfill = function(var_args) {
    // Note: this is taken from v8's harmony-string.js StringFromCodePoint.
    var result = '';
    for (var i = 0; i < arguments.length; i++) {
      var code = Number(arguments[i]);
      if (code < 0 || code > 0x10FFFF || code !== Math.floor(code)) {
        throw new RangeError('invalid_code_point ' + code);
      }
      if (code <= 0xFFFF) {
        result += String.fromCharCode(code);
      } else {
        code -= 0x10000;
        result += String.fromCharCode((code >>> 10) & 0x3FF | 0xD800);
        result += String.fromCharCode(code & 0x3FF | 0xDC00);
      }
    }
    return result;
  };

  return polyfill;
}, 'es6-impl', 'es3');
