/*
 * Copyright 2008 The Closure Compiler Authors
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
 * @fileoverview JavaScript Built-Ins that are not part of any specifications
 * but are still needed in some project's build.
 * @externs
 */
var opera = {};

opera.postError;

/** @nosideeffects */
opera.version = function() {};

/** @constructor */
function XSLTProcessor() {}

/**
 * @constructor
 * @extends {HTMLOptionElement}
 * @param {*=} opt_text
 * @param {*=} opt_value
 * @param {*=} opt_defaultSelected
 * @param {*=} opt_selected
 */
function Option(opt_text, opt_value, opt_defaultSelected, opt_selected) {}


// The "methods" object is a place to hang arbitrary external
// properties. It is a throwback to pre-typed days, and should
// not be used for any new definitions; it exists only to bridge
// the gap between the old way and the new way.
var methods = {};

/**
 * FF 1.5+ only
 * @param {IArrayLike<T>} arr
 * @param {?function(this:S, T, number, ?) : ?} callback
 * @param {S=} opt_context
 * @return {boolean}
 * @template T,S
 * @deprecated
 */
Array.every = function(arr, callback, opt_context) {};

/**
 * @param {IArrayLike<T>} arr
 * @param {?function(this:S, T, number, ?) : ?} callback
 * @param {S=} opt_context
 * @return {!Array<T>}
 * @template T,S
 * @deprecated
 */
Array.filter = function(arr, callback, opt_context) {};

/**
 * @param {IArrayLike<T>} arr
 * @param {?function(this:S, T, number, ?) : ?} callback
 * @param {S=} opt_context
 * @template T,S
 * @return {undefined}
 * @deprecated
 */
Array.forEach = function(arr, callback, opt_context) {};

/**
 * Mozilla 1.6+ only.
 * @param {IArrayLike<T>} arr
 * @param {T} obj
 * @param {number=} opt_fromIndex
 * @return {number}
 * @template T
 * @nosideeffects
 * @deprecated
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/indexOf
 */
Array.indexOf = function(arr, obj, opt_fromIndex) {};

/**
 * Mozilla 1.6+ only.
 * @param {IArrayLike<T>} arr
 * @param {T} obj
 * @param {number=} opt_fromIndex
 * @return {number}
 * @template T
 * @nosideeffects
 * @deprecated
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/lastIndexOf
 */
Array.lastIndexOf = function(arr, obj, opt_fromIndex) {};

/**
 * @param {IArrayLike<T>} arr
 * @param {?function(this:S, T, number, !Array<T>): R} callback
 * @param {S=} opt_context
 * @return {!Array<R>}
 * @deprecated
 * @template T,S,R
 */
Array.map = function(arr, callback, opt_context) {};

/**
 * @param {IArrayLike<T>} arr
 * @param {?function(this:S, T, number, ?) : ?} callback
 * @param {S=} opt_context
 * @return {boolean}
 * @deprecated
 * @template T,S
 */
Array.some = function(arr, callback, opt_context) {};
