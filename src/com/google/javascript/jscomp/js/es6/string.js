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

/**
 * @fileoverview Polyfills for ES6 String functions.
 */

$jscomp.string = $jscomp.string || {};


/**
 * Throws if the argument is a RegExp, or if thisArg is undefined.
 * @param {?} thisArg The 'this' arg, which must be defined.
 * @param {*} arg The first argument of the function, which mustn't be a RegExp.
 * @param {string} func Name of the function, for reporting.
 * @return {string} The thisArg, coerced to a string.
 */
$jscomp.checkStringArgs = function(thisArg, arg, func) {
  if (thisArg == null) {
    throw new TypeError(
        "The 'this' value for String.prototype." + func +
        ' must not be null or undefined');
  }
  if (arg instanceof RegExp) {
    throw new TypeError(
        'First argument to String.prototype.' + func +
        ' must not be a regular expression');
  }
  return thisArg + '';
};


  /**
   * Creates a new string from the given codepoints.
   *
   * <p>Polyfills the static function String.fromCodePoint().
   *
   * @param {...number} var_args
   * @return {string}
   */
  $jscomp.string.fromCodePoint = function(var_args) {
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


  /**
   * Returns a new string repeated the given number of times.
   *
   * <p>Polyfills the instance method String.prototype.repeat().
   *
   * @this {string}
   * @param {number} copies
   * @return {string}
   */
  $jscomp.string.repeat = function(copies) {
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


  /**
   * Installs the String.prototype.repeat polyfill.
   * @const @suppress {const,checkTypes}
   */
  $jscomp.string.repeat$install = function() {
    if (!String.prototype.repeat) {
      String.prototype.repeat = $jscomp.string.repeat;
    }
  };


  /**
   * Returns the UTF-16 codepoint at the given index.
   *
   * <p>Polyfills the instance method String.prototype.codePointAt().
   *
   * @this {string}
   * @param {number} position
   * @return {number|undefined} The codepoint.
   */
  $jscomp.string.codePointAt = function(position) {
    // NOTE: this is taken from v8's harmony-string.js StringCodePointAt
    'use strict';
    var string = $jscomp.checkStringArgs(this, null, 'codePointAt');
    var size = string.length;
    // Make 'position' a number.  Non-numbers turn into NaN and then or to zero.
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


  /**
   * Installs the String.prototype.codePointAt polyfill.
   * @suppress {const,checkTypes}
   */
  $jscomp.string.codePointAt$install = function() {
    if (!String.prototype.codePointAt) {
      String.prototype.codePointAt = $jscomp.string.codePointAt;
    }
  };


  /**
   * Searches for a substring, starting at the given position.
   *
   * <p>Polyfills the instance method String.prototype.includes().
   *
   * @this {string}
   * @param {string} searchString
   * @param {number=} opt_position
   * @return {boolean}
   */
  $jscomp.string.includes = function(searchString, opt_position) {
    'use strict';
    var string = $jscomp.checkStringArgs(this, searchString, 'includes');
    return string.indexOf(searchString, opt_position || 0) !== -1;
  };


  /**
   * Installs the String.prototype.includes polyfill.
   * @suppress {const,checkTypes}
   */
  $jscomp.string.includes$install = function() {
    if (!String.prototype.includes) {
      String.prototype.includes = $jscomp.string.includes;
    }
  };


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
  $jscomp.string.startsWith = function(searchString, opt_position) {
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


  /**
   * Installs the String.prototype.startsWith polyfill.
   * @suppress {const,checkTypes}
   */
  $jscomp.string.startsWith$install = function() {
    if (!String.prototype.startsWith) {
      String.prototype.startsWith = $jscomp.string.startsWith;
    }
  };


  /**
   * Tests whether the string ends with a given substring.
   *
   * <p>Polyfills the instance method String.prototype.endsWith().
   *
   * @this {string}
   * @param {string} searchString
   * @param {number=} opt_position
   * @return {boolean}
   */
  $jscomp.string.endsWith = function(searchString, opt_position) {
    'use strict';
    var string = $jscomp.checkStringArgs(this, searchString, 'endsWith');
    searchString = searchString + '';
    if (opt_position === void 0) opt_position = string.length;
    var i = Math.max(0, Math.min(opt_position | 0, string.length));
    var j = searchString.length;
    while (j > 0 && i > 0) {
      if (string[--i] != searchString[--j]) return false;
    }
    return j <= 0;
  };


  /**
   * Installs the String.prototype.endsWith polyfill.
   * @suppress {const,checkTypes}
   */
  $jscomp.string.endsWith$install = function() {
    if (!String.prototype.endsWith) {
      String.prototype.endsWith = $jscomp.string.endsWith;
    }
  };


// TODO(sdh): String.prototype.normalize?
