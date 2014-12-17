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

/** @const {symbol} */
Symbol.iterator;



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
 *   execute:(function(...Module):Promise.<Module>)
 *   })>} The requested module.
 * @see https://people.mozilla.org/~jorendorff/es6-draft.html#sec-loader.prototype.instantiate
 */
Loader.prototype.instantiate;





/** @const {Loader} */
var System;


/**
 * @interface
 * @template VALUE
 */
var Iterable;

// TODO(johnlenz): remove this when the compiler understands "symbol" natively
/**
 * @return {Iterator.<VALUE>}
 * @suppress {externsValidation}
 */
Iterable[Symbol.iterator] = function() {};



// TODO(johnlenz): Iterator should be a templated record type.
/**
 * @interface
 * @template VALUE
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/The_Iterator_protocol
 */
var Iterator;

/**
 * @return {{value:VALUE, done:boolean}}
 */
Iterator.prototype.next;




/**
 * @constructor
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-generator-objects
 * @implements {Iterator<VALUE>}
 * @template VALUE
 */
var Generator;

/**
 * @param {?=} value
 * @return {{value:VALUE, done:boolean}}
 */
Generator.prototype.next;

/**
 * @param {VALUE} value
 * @return {{value:VALUE, done:boolean}}
 */
Generator.prototype.return;

/**
 * @param {?} exception
 * @return {{value:VALUE, done:boolean}}
 */
Generator.prototype.throw;

// TODO(johnlenz): cleanup the Iterator|

/**
 * @constructor
 * @param {Iterable<Array<KEY|VALUE>>|Array<Array<KEY|VALUE>>} opt_iterable
 * @implements Iterable.<Array.<KEY|VALUE>>
 * @template KEY, VALUE
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
 */
function Map(opt_iterable) {}

/** @return {void} */
Map.prototype.clear;

/**
 * @param {KEY} key
 * @return {boolean}
 */
Map.prototype.delete;

/**
 * @return {Iterator<Array<KEY|VALUE>>}
 * @nosideeffects
 */
Map.prototype.entries;

/**
 * @param {function(this:THIS, VALUE, KEY, MAP):void} callback
 * @param {THIS} thisArg
 * @this {MAP}
 * @template MAP,THIS
 */
Map.prototype.forEach;

/**
 * @param {KEY} key
 * @return {VALUE}
 * @nosideeffects
 */
Map.prototype.get;

/**
 * @param {KEY} key
 * @return {boolean}
 * @nosideeffects
 */
Map.prototype.has;

/**
 * @return {Iterator<KEY>}
 */
Map.prototype.keys;

/**
 * @param {KEY} key
 * @param {VALUE} value
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 */
Map.prototype.set;

/**
 * @type {number}
 * (readonly)
 */
Map.prototype.size;

/**
 * @return {Iterator<VALUE>}
 * @nosideeffects
 */
Map.prototype.values;

/**
 * @return {Iterator.<Array.<KEY|VALUE>>}
 */
Map.prototype[Symbol.iterator] = function() {};


/**
 * @constructor
 * @param {Iterable<Array<KEY|VALUE>>|Array<Array<KEY|VALUE>>} opt_iterable
 * @template KEY, VALUE
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WeakMap
 */
function WeakMap(opt_iterable) {}

/** @return {void} */
WeakMap.prototype.clear;

/**
 * @param {KEY} key
 * @return {boolean}
 */
WeakMap.prototype.delete;

/**
 * @param {KEY} key
 * @return {VALUE}
 * @nosideeffects
 */
WeakMap.prototype.get;

/**
 * @param {KEY} key
 * @return {boolean}
 * @nosideeffects
 */
WeakMap.prototype.has;

/**
 * @param {KEY} key
 * @param {VALUE} value
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 */
WeakMap.prototype.set;



/**
 * @constructor
 * @param {Iterable<VALUE>|Array<VALUE>=} opt_iterable
 * @implements Iterable.<VALUE>
 * @template VALUE
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set
 */
function Set(opt_iterable) {}

/**
 * @type {number}
 */
Set.prototype.size;

/**
 * @param {VALUE} value
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 */
Set.prototype.add;

/**
 * @return {void}
 */
Set.prototype.clear;

/**
 * @param {VALUE} value
 * @return {boolean}
 */
Set.prototype.delete;

/**
 * @return {Iterator<Array<VALUE>>} Where each array is two entries value, value
 * @nosideeffects
 */
Set.prototype.entries;

/**
 * @param {function(VALUE, VALUE, SET)} callback
 * @param {THIS} thisArg
 * @this {SET}
 * @template SET,THIS
 */
Set.prototype.forEach;

/**
 * @param {VALUE} value
 * @return {boolean}
 * @nosideeffects
 */
Set.prototype.has;

/**
 * @type {number} (readonly)
 */
Set.prototype.size;

/**
 * @return {Iterator<VALUE>}
 * @nosideeffects
 */
Set.prototype.keys;

/**
 * @return {Iterator<VALUE>}
 * @nosideeffects
 */
Set.prototype.values;

/**
 * @return {Iterator.<VALUE>}
 */
Set.prototype[Symbol.iterator] = function() {};



/**
 * @constructor
 * @param {Iterable<VALUE>|Array<VALUE>=} opt_iterable
 * @template VALUE
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set
 */
function WeakSet(opt_iterable) {}

/**
 * @param {VALUE} value
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 */
WeakSet.prototype.add;

/**
 * @return {void}
 */
WeakSet.prototype.clear;

/**
 * @param {VALUE} value
 * @return {boolean}
 */
WeakSet.prototype.delete;

/**
 * @param {VALUE} value
 * @return {boolean}
 * @nosideeffects
 */
WeakSet.prototype.has;



/**
 * @param {Object} target
 * @param {Object} source
 * @return {Object}
 * @see http://people.mozilla.org/~jorendorff/es6-draft.html#sec-object.assign
 */
Object.assign;


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
