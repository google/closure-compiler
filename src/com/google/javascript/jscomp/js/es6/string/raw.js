/*
 * Copyright 2021 The Closure Compiler Authors.
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
 * @fileoverview String.raw polyfill.
 * @suppress {uselessCode}
 */

'require util/polyfill';

$jscomp.polyfill('String.raw', function(orig) {
  if (orig) return orig;

  /**
   * Polyfills String.raw.
   *
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/raw
   *
   * @param {!ITemplateArray} strings List of string fragments to concatenate.
   * @param {...*} var_args Values to go between string fragments.
   * @return {string}
   */
  var stringRaw = function(strings, var_args) {
    if (strings == null) {
      throw new TypeError('Cannot convert undefined or null to object');
    }
    var raw = strings.raw;
    var rawlen = raw.length;
    var result = "";
    for (var i = 0; i < rawlen; ++i) {
      result += raw[i];
      if ((i + 1) < rawlen && (i + 1) < arguments.length) {
        result += String(arguments[i+1]);
      }
    }
    return result;
  };

  return stringRaw;
}, 'es6', 'es3');
