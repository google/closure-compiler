/*
 * Copyright 2009 Google Inc.
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
 * @fileoverview Definitions for Ecmascript 5.
 * @see http://www.ecma-international.org/publications/files/drafts/tc39-2009-025.pdf
 * @externs
 */


/**
 * @param {Object} selfObj Specifies the object to which |this| should point
 *     when the function is run. If the value is null or undefined, it will
 *     default to the global object.
 * @param {...*} var_args Additional arguments that are partially
 *     applied to fn.
 * @return {!Function} A partially-applied form of the Function on which
 *     bind() was invoked as a method.
 * @see https://developer.mozilla.org/en/JavaScript/Reference/Global_Objects/Function/bind
 */
Function.prototype.bind = function(selfObj, var_args) {};


/*
 * JSON api.
 */

/**
 * @see https://developer.mozilla.org/En/Using_native_JSON
 */
Window.prototype.JSON = {};


/**
 * @param {string} text
 * @param {(function(string, *) : *)=} opt_reviver
 * @return {*}
 * @see http://ejohn.org/blog/ecmascript-5-strict-mode-json-and-more/
 */
Window.prototype.JSON.parse = function(text, opt_reviver) {};


/**
 * @param {*} value
 * @param {(Array.<string>|(function(string, *) : *)|null)=} opt_replacer
 * @param {(number|string)=} opt_space
 * @return {string}
 * @see http://ejohn.org/blog/ecmascript-5-strict-mode-json-and-more/
 */
Window.prototype.JSON.stringify =
    function(value, opt_replacer, opt_space) {};


/**
 * @param {*=} opt_ignoredKey
 * @return {string}
 */
Date.prototype.toJSON = function(opt_ignoredKey) {};


/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en/JavaScript/Reference/Global_Objects/String/Trim
 */
String.prototype.trim = function() {};


/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en/JavaScript/Reference/Global_Objects/String/TrimLeft
 */
String.prototype.trimLeft = function() {};


/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en/JavaScript/Reference/Global_Objects/String/TrimRight
 */
String.prototype.trimRight = function() {};
