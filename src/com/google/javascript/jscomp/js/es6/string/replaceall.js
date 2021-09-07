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
 * @fileoverview Creates polyfill for `String.prototype.replaceAll` being
 * added with ES2021. https://tc39.es/ecma262/#sec-string.prototype.replaceall
 * @suppress {uselessCode}
 */
'require util/polyfill';

$jscomp.polyfill('String.prototype.replaceAll', function(orig) {
  if (orig) return orig;

  /**
   * Escapes characters in the string that are not safe to use in a RegExp.
   * @param {string|null} s
   * @return {string}
   */
  function regExpEscape(s) {
    return String(s)
        .replace(/([-()\[\]{}+?*.$\^|,:#<!\\])/g, '\\$1')
        .replace(/\x08/g, '\\x08');
  }

  /**
   * Returns a new string in which all occurrences of searchValue are replaced
   * with the given replacement.
   *
   * Polyfills the instance method String.prototype.replaceAll().
   *
   * @this {string}
   * @param {!RegExp|string} searchValue to replace.
   * @param {?string|function(string, ...?):*} replacement string or replacer
   *     function
   * @return {string} new string with searchValue substituted with replacement.
   */
  var polyfill = function(searchValue, replacement) {
    if (searchValue instanceof RegExp && !searchValue.global) {
      throw new TypeError(
          'String.prototype.replaceAll called with a non-global RegExp argument.');
    }

    if (searchValue instanceof RegExp) {
      // Must behave exactly like String.prototype.replace if the searchValue is
      // a global regular expression.
      return this.replace(searchValue, replacement);
    }

    // regExpEscape() sticks a '\' character in front of all the RegExp
    // special characters.
    return this.replace(
        new RegExp(regExpEscape(searchValue), 'g'), replacement);
  };
  return polyfill;
}, 'es_2021', 'es3');
