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



/**
 * @param {string} sym
 * @return {symbol|undefined}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-symbol.for
 */
Symbol.for;


/**
 * @param {symbol} sym
 * @return {string|undefined}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-symbol.keyfor
 */
Symbol.keyFor;


// Well known symbols

/** @const {symbol} */
Symbol.create;

/** @const {symbol} */
Symbol.hasInstance;

/** @const {symbol} */
Symbol.isConcatSpreadable;

/** @const {symbol} */
Symbol.isRegExp;

/** @const {symbol} */
Symbol.toPrimitive;

/** @const {symbol} */
Symbol.toStringTag;

/** @const {symbol} */
Symbol.unscopables;



/** @const */
var Reflect = {};

/**
 * @param {Function} target
 * @param {?} thisArg
 * @param {{length:number}} args
 */
Reflect.apply;


/**
 * @param {Function} target
 * @param {{length:number}} args
 */
Reflect.construct;


Reflect.defineProperty;

Reflect.deleteProperty;

Reflect.enumerate;

Reflect.get;

Reflect.getOwnPropertyDescriptor;

Reflect.getPrototypeOf;

Reflect.has;

Reflect.isExtensible;

Reflect.ownKeys;

Reflect.preventExtensions;

Reflect.set;

Reflect.setPrototypeOf;







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
Reflect.Loader;

/** @return {Iterator.<[string, Module]>}*/
Reflect.Loader.prototype.entries;


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
Reflect.Loader.prototype.define;

/**
 * @param {string} name
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.delete
 */
Reflect.Loader.prototype.delete;


/**
 * @return {Iterator.<[string, Module]>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.entries
 */
Reflect.Loader.prototype.entries;

/**
 * @param {string} name
 * @return {*}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.eval
 */
Reflect.Loader.prototype.eval;

/**
 * @param {string} name
 * @return {Module|undefined}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.get
 */
Reflect.Loader.prototype.get;


/**
 * @const {Object}
 * This is really an instance constant
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-get-loader.prototype.global
 */
Reflect.Loader.prototype.global;

/**
 * @param {string} name
 * @return {boolean}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.has
 */
Reflect.Loader.prototype.has;


/**
 * @param {string} name
 * @param {{
 *     address:string
 * }=} opt_options
 * @return {Promise.<Module>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.import
 */
Reflect.Loader.prototype.import;

/**
 * @return {Iterator.<string>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.keys
 */
Reflect.Loader.prototype.keys;

/**
 * @param {string} name
 * @param {{
 *     address:string
 * }=} opt_options
 * @return {Promise.<Module>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.load
 */
Reflect.Loader.prototype.load;

/**
 * @param {{
 *     address:string
 * }=} opt_options
 * @return {Promise.<Module>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.module
 */
Reflect.Loader.prototype.module;

/**
 * @param {!Object=} opt_options
 * @return {Module}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.module
 */
Reflect.Loader.prototype.newModule;

/**
 * @const {Realm}
 * TODO: This is really an instance constant, define this as an ES6
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-get-loader.prototype.realm
 */
Reflect.Loader.prototype.realm;


/**
 * @param {string} name
 * @param {Module} module
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-get-loader.prototype.set
 */
Reflect.Loader.prototype.set;

/**
 * @return {Iterator.<string>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.values
 */
Reflect.Loader.prototype.keys;

/**
 * @return {Iterator.<Module>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.values
 */
Reflect.Loader.prototype.values;

/**
 * @param {string} name
 * @param {string} refererName
 * @param {string} refererAddress
 * @return {Promise.<string>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.normalize
 */
Reflect.Loader.prototype.normalize;

/**
 * @param {{name: string}} loadRequest
 * @return {Promise.<string>} The URL for the request
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.locate
 */
Reflect.Loader.prototype.locate;

/**
 * @param {{address: string}} loadRequest
 * @return {Promise.<string>} The requested module source.
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.fetch
 */
Reflect.Loader.prototype.fetch;

/**
 * @param {{source: string}} load
 * @return {Promise.<string>} The translated source.
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.translate
 */
Reflect.Loader.prototype.translate;

/**
 * @param {{address: string, source: string}} load
 * @return {Promise.<(undefined|{
 *   deps:Array.<string>,
 *   execute:(function(...Module):Promise.<Module>)
 *   })>} The requested module.
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.instantiate
 */
Reflect.Loader.prototype.instantiate;


/**
 * @return {Iterator.<Array.<string|Module>>}
 */
Reflect.Loader.prototype[Symbol.iterator] = function() {};


/** @const {Reflect.Loader} */
var System;

// TODO(johnlenz): Array.from should have different result values
// depending on whether a map function is provide.

/**
 * @param {{length:number}|!Array.<VALUE>} arrLike
 * @param {function(this:MAP_FN_THIS, VALUE, number):RESULT=} mapFn
 * @param {MAP_FN_THIS=} thisArg
 * @return {!Array.<RESULT>}
 * @template MAP_FN_THIS, VALUE
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-array.from
 */
Array.from;

/**
 * @param {...VALUES} var_args
 * @return {Array.<VALUES>}
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-array.of
 */
Array.of;

/**
 * @param {number} target
 * @param {number} start
 * @param {number=} end
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-array.prototype.copywithin
 */
Array.prototype.copyWithin;


Array.prototype.find;

Array.prototype.findIndex;

Array.prototype.fill;

Array.prototype.keys;

Array.prototype.values;

Array.prototype.entries;

/**
 * @return {Iterator.<Array.<number|T>>}
 */
Array.prototype[Symbol.iterator] = function() {};



/**
 * @param {Object} target
 * @param {Object} source
 * @return {Object}
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-object.assign
 */
Object.assign;

Object.getOwnPropertySymbols;

Object.setPrototypeOf;



Function.prototype.toMethod;



/**
 * @const {number}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/EPSILON
 */
Number.EPSILON;

/**
 * @const {number}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MIN_SAFE_INTEGER
 */
Number.MIN_SAFE_INTEGER;

/**
 * @const {number}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
 */
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


RegExp.prototype.match;

RegExp.prototype.replace;

RegExp.prototype.search;

RegExp.prototype.split;


String.raw;

String.fromCodePoint;

String.prototype.codePointAt;

String.prototype.normalize;


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
