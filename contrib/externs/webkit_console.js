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
 * @fileoverview Definitions for the Webkit console specification.
 * @see http://trac.webkit.org/browser/trunk/WebCore/page/Console.idl
 * @see http://trac.webkit.org/browser/trunk/WebCore/page/Console.cpp
 * @externs
 *
 */

var console = {};

/**
 * @param {...*} var_args
 */
console.debug = function(var_args) {};

/**
 * @param {...*} var_args
 */
console.error = function(var_args) {};

/**
 * @param {...*} var_args
 */
console.info = function(var_args) {};

/**
 * @param {...*} var_args
 */
console.log = function(var_args) {};

/**
 * @param {...*} var_args
 */
console.warn = function(var_args) {};

/**
 * @param {*} value
 */
console.dir = function(value) {};

/**
 * @param {...*} var_args
 */
console.dirxml = function(var_args) {};

/**
 * @param {*} value
 */
console.trace = function(value) {};

/**
 * @param {*} condition
 * @param {...*} var_args
 */
console.assert = function(condition, var_args) {};

/**
 * @param {*} value
 */
console.count = function(value) {};

/**
 * @param {string=} opt_title
 */
console.profile = function(opt_title) {};

console.profileEnd = function() {};

/**
 * @param {string} name
 */
console.time = function(name) {};

/**
 * @param {string} name
 */
console.timeEnd = function(name) {};

console.group = function() {};
console.groupEnd = function() {};
