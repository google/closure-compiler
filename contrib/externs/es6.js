/*
 * Copyright 2014 The Closure Compiler Authors
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
 * @fileoverview Definitions for ECMAScript 6.
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html
 * @externs
 * @author johnlenz@google.com (johnlenz)
 */

// TODO(johnlenz): flesh out the type information in this file.

/** @typedef {?} */
var symbol;

/**
 * @param {string} description
 * @return {symbol}
 */
function Symbol(description) {}




/**
 * @constructor
 * // @extends {null} // A non-Object, property container
 */
var Module;

/**
 * @constructor
 */
var Realm;

/**
 * @constructor
 * @param {{
 *     realm:(Realm|undefined),
 *     normalize:Function,
 *     locate:Function,
 *     fetch:Function,
 *     translate:Function,
 *     instantiate:Function}=} opt_options
 */
function Loader(opt_options) {}

/** @return {Iterator.<[string, Module]>}*/
Loader.prototype.entries;


/**
 * @param {string} name
 * @param {string} source
 * @param {{
 *     address:string,
 *     metadata:string
 * }=} opt_options
 * @return {Promise.<undefined>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-%loader%.prototype.define
 */
Loader.prototype.define;

/**
 * @param {string} name
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.delete
 */
Loader.prototype.delete;


/**
 * @return {Iterator.<[string, Module]>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.entries
 */
Loader.prototype.entries;

/**
 * @param {string} name
 * @return {*}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.eval
 */
Loader.prototype.eval;

/**
 * @param {string} name
 * @return {Module|undefined}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.get
 */
Loader.prototype.get;


/**
 * @const {Object}
 * This is really an instance constant
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-get-loader.prototype.global
 */
Loader.prototype.global;

/**
 * @param {string} name
 * @return {boolean}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.has
 */
Loader.prototype.has;


/**
 * @param {string} name
 * @param {{
 *     address:string
 * }=} opt_options
 * @return {Promise.<Module>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.import
 */
Loader.prototype.import;

/**
 * @return {Iterator.<string>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.keys
 */
Loader.prototype.keys;

/**
 * @param {string} name
 * @param {{
 *     address:string
 * }=} opt_options
 * @return {Promise.<Module>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.load
 */
Loader.prototype.load;

/**
 * @param {{
 *     address:string
 * }=} opt_options
 * @return {Promise.<Module>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.module
 */
Loader.prototype.module;

/**
 * @param {!Object=} opt_options
 * @return {Module}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.module
 */
Loader.prototype.newModule;

/**
 * @const {Realm}
 * TODO: This is really an instance constant, define this as an ES6
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-get-loader.prototype.realm
 */
Loader.prototype.realm;


/**
 * @return {Iterator.<Module>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.values
 */
Loader.prototype.keys;

/**
 * @param {string} name
 * @param {string} refererName
 * @param {string} refererAddress
 * @return {Promise.<string>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.normalize
 */
Loader.prototype.normalize;

/**
 * @param {{name: string}} loadRequest
 * @return {Promise.<string>} The URL for the request
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.locate
 */
Loader.prototype.locate;

/**
 * @param {{address: string}} loadRequest
 * @return {Promise.<string>} The requested module source.
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.fetch
 */
Loader.prototype.fetch;

/**
 * @param {{source: string}} load
 * @return {Promise.<string>} The translated source.
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.translate
 */
Loader.prototype.translate;

/**
 * @param {{address: string, source: string}} load
 * @return {Promise.<(undefined|{
 *   deps:Array.<string>,
 *   execute:(function(...[Module]):Promise.<Module>)
 *   })>} The requested module.
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.instantiate
 */
Loader.prototype.instantiate;





/** @const {Loader} */
var System;




// TODO(johnlenz): add externs for std:iteration, GeneratorFunction etc.
// http://people.mozilla.org/~jorendorff/es6-draft.html#sec-generatorfunction

/**
 * @interface
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-iterator-interface
 */
var Iterator;
Iterator.prototype.next;

/**
 * @interface
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-generator-objects
 * @extends {Iterator}
 */
var Generator;
Generator.prototype.next;
Generator.prototype.throw;

/** @constructor */
function Map(opt_iterable) {}
Map.prototype.clear;
Map.prototype.delete;
Map.prototype.entries;
Map.prototype.forEach;
Map.prototype.get;
Map.prototype.has;
Map.prototype.keys;
Map.prototype.set;
Map.prototype.size;
Map.prototype.values;


/** @constructor */
function WeakMap(opt_iterable) {}
WeakMap.prototype.clear;
WeakMap.prototype.delete;
WeakMap.prototype.get;
WeakMap.prototype.has;
WeakMap.prototype.set;

/** @constructor */
function Set(opt_iterable) {}
Set.prototype.add;
Set.prototype.clear;
Set.prototype.delete;
Set.prototype.entries;
Set.prototype.forEach;
Set.prototype.has;
Set.prototype.keys;
Set.prototype.size;
Set.prototype.values;

/** @constructor */
function WeakSet(opt_iterable) {}
WeakSet.prototype.add;
WeakSet.prototype.clear;
WeakSet.prototype.delete;
WeakSet.prototype.has;


/**
 * @param {*} a
 * @param {*} b
 * @return {boolean}
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-object.is
 */
Object.is;


/**
 * @param {Object} target
 * @param {Object} source
 * @return {Object}
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-object.assign
 */
Object.assign;

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.log10 = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.log2 = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.log1p = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.expm1 = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.cosh = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.sinh = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.tanh = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.acosh = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.asinh = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.atanh = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.trunc = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.sign = function(value) {};

/**
 * @param {number} value
 * @return {number}
 * @nosideeffects
 */
Math.cbrt = function(value) {};

/**
 * @param {number} value1
 * @param {...number} var_args
 * @return {number}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-math.hypot
 */
Math.hypot = function(value1, var_args) {};


/** @const {number} */
Number.EPSILON;

/** @const {number} */
Number.MAX_SAFE_INTEGER;

/**
 * Parse an integer. Use of {@code parseInt} without {@code base} is strictly
 * banned in Google. If you really want to parse octal or hex based on the
 * leader, then pass {@code undefined} as the base.
 *
 * @param {string} string
 * @param {number|undefined} radix
 * @return {number}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-number.parseint
 */
Number.parseInt = function(string, radix) {};

/**
 * @param {string} string
 * @return {number}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-number.parsefloat
 */
Number.parseFloat = function(string) {};

/**
 * @param {number} value
 * @return {boolean}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-number.isnan
 */
Number.isNaN = function(value) {};

/**
 * @param {number} value
 * @return {boolean}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-number.isfinite
 */
Number.isFinite = function(value) {};

/**
 * @param {number} value
 * @return {boolean}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-number.isinteger
 */
Number.isInteger = function(value) {};

/**
 * @param {number} value
 * @return {boolean}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-number.issafeinteger
 */
Number.isSafeInteger = function(value) {};

/**
 * @param {number} count
 * @return {string}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-string.prototype.repeat
 */
String.prototype.repeat = function(count) {};

/**
 * @param {string} searchString
 * @param {number=} opt_position
 * @return {boolean}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-string.prototype.startswith
 */
String.prototype.startsWith = function(searchString, opt_position) {};

/**
 * @param {string} searchString
 * @param {number=} opt_position
 * @return {boolean}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-string.prototype.endswith
 */
String.prototype.endsWith = function(searchString, opt_position) {};

/**
 * @param {string} searchString
 * @param {number=} opt_position
 * @return {boolean}
 * @nosideeffects
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-string.prototype.contains
 */
String.prototype.contains = function(searchString, opt_position) {};
