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
*
 */


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
