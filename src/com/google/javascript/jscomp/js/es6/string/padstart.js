/*
 * Copyright 2017 The Closure Compiler Authors.
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

'require util/checkstringargs';
'require util/polyfill';
'require util/stringpadding';

$jscomp.polyfill('String.prototype.padStart', function(orig) {
  if (orig) return orig;

  /**
   * Polyfills String.prototype.padStart.
   *
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/padStart
   *
   * @this {string}
   * @param {number} targetLength
   * @param {string=} opt_padString
   * @return {string}
   */
  var padStart = function(targetLength, opt_padString) {
    var string = $jscomp.checkStringArgs(this, null, 'padStart');
    var padLength = targetLength - string.length;
    return $jscomp.stringPadding(opt_padString, padLength) + string;
  };

  return padStart;
}, 'es8', 'es3');
