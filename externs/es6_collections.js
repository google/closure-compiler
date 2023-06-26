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
 * @see http://wiki.ecmascript.org/doku.php?id=harmony:specification_drafts
 * @externs
 */

// TODO(johnlenz): Use Tuples for the Map and Set iterators where appropriate.
/**
 * @interface
 * @extends {Iterable<!Array<KEY|VALUE>>}
 * @template KEY, VALUE
 */
function ReadonlyMap() {}

/**
 * @return {!IteratorIterable<!Array<KEY|VALUE>>}
 * @nosideeffects
 */
ReadonlyMap.prototype.entries = function() {};

/**
 * @param {function(this:THIS, VALUE, KEY, MAP): ?} callback
 * @param {THIS=} opt_thisArg
 * @this {MAP}
 * @template MAP, THIS
 * @return {undefined}
 */
ReadonlyMap.prototype.forEach = function(callback, opt_thisArg) {};

/**
 * @param {KEY} key
 * @return {VALUE|undefined}
 * @nosideeffects
 */
ReadonlyMap.prototype.get = function(key) {};

/**
 * @param {KEY} key
 * @return {boolean}
 * @nosideeffects
 */
ReadonlyMap.prototype.has = function(key) {};

/**
 * @return {!IteratorIterable<KEY>}
 * @nosideeffects
 */
ReadonlyMap.prototype.keys = function() {};

/**
 * @const {number}
 * @nosideeffects
 */
ReadonlyMap.prototype.size;

/**
 * @return {!IteratorIterable<VALUE>}
 * @nosideeffects
 */
ReadonlyMap.prototype.values = function() {};

/**
 * @return {!Iterator<!Array<KEY|VALUE>>}
 * @nosideeffects
 */
ReadonlyMap.prototype[Symbol.iterator] = function() {};

/**
 * @constructor @struct
 * @param {Iterable<!Array<KEY|VALUE>>|!Array<!Array<KEY|VALUE>>=} opt_iterable
 * @implements {ReadonlyMap<KEY, VALUE>}
 * @template KEY, VALUE
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
 */
function Map(opt_iterable) {}

/** @return {void} */
Map.prototype.clear = function() {};

/**
 * @param {KEY} key
 * @return {boolean}
 */
Map.prototype.delete = function(key) {};

/**
 * @override
 * @return {!IteratorIterable<!Array<KEY|VALUE>>}
 * @nosideeffects
 */
Map.prototype.entries = function() {};

/**
 * @override
 * @param {function(this:THIS, VALUE, KEY, MAP): ?} callback
 * @param {THIS=} opt_thisArg
 * @this {MAP}
 * @template MAP,THIS
 * @return {undefined}
 */
Map.prototype.forEach = function(callback, opt_thisArg) {};

/**
 * @override
 * @param {KEY} key
 * @return {VALUE}
 * @nosideeffects
 */
Map.prototype.get = function(key) {};

/**
 * @override
 * @param {KEY} key
 * @return {boolean}
 * @nosideeffects
 */
Map.prototype.has = function(key) {};

/**
 * @override
 * @return {!IteratorIterable<KEY>}
 * @nosideeffects
 */
Map.prototype.keys = function() {};

/**
 * @param {KEY} key
 * @param {VALUE} value
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 */
Map.prototype.set = function(key, value) {};

/**
 * @override
 * @type {number}
 * @nosideeffects
 */
Map.prototype.size;

/**
 * @override
 * @return {!IteratorIterable<VALUE>}
 * @nosideeffects
 */
Map.prototype.values = function() {};

/**
 * @override
 * @return {!Iterator<!Array<KEY|VALUE>>}
 * @nosideeffects
 */
Map.prototype[Symbol.iterator] = function() {};


/**
 * @constructor @struct
 * @param {Iterable<!Array<KEY|VALUE>>|!Array<!Array<KEY|VALUE>>=} opt_iterable
 * @template KEY, VALUE
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WeakMap
 */
function WeakMap(opt_iterable) {}

/** @return {void} */
WeakMap.prototype.clear = function() {};

/**
 * @param {KEY} key
 * @return {boolean}
 */
WeakMap.prototype.delete = function(key) {};

/**
 * @param {KEY} key
 * @return {VALUE}
 * @nosideeffects
 */
WeakMap.prototype.get = function(key) {};

/**
 * @param {KEY} key
 * @return {boolean}
 * @nosideeffects
 */
WeakMap.prototype.has = function(key) {};

/**
 * @param {KEY} key
 * @param {VALUE} value
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 */
WeakMap.prototype.set = function(key, value) {};

/**
 * @constructor @struct
 * @param {Iterable<VALUE>|Array<VALUE>=} opt_iterable
 * @implements {Iterable<VALUE>}
 * @template VALUE
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set
 */
function Set(opt_iterable) {}

/**
 * @param {VALUE} value
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 */
Set.prototype.add = function(value) {};

/**
 * @return {void}
 */
Set.prototype.clear = function() {};

/**
 * @param {VALUE} value
 * @return {boolean}
 */
Set.prototype.delete = function(value) {};

/**
 * @return {!IteratorIterable<!Array<VALUE>>} Where each array has two entries:
 *     [value, value]
 * @nosideeffects
 */
Set.prototype.entries = function() {};

/**
 * @param {function(this: THIS, VALUE, VALUE, SET)} callback
 * @param {THIS=} opt_thisArg
 * @this {SET}
 * @template SET,THIS
 */
Set.prototype.forEach = function(callback, opt_thisArg) {};

/**
 * @param {VALUE} value
 * @return {boolean}
 * @nosideeffects
 */
Set.prototype.has = function(value) {};

/**
 * @type {number} (readonly)
 */
Set.prototype.size;

/**
 * @return {!IteratorIterable<VALUE>}
 * @nosideeffects
 */
Set.prototype.keys = function() {};

/**
 * @return {!IteratorIterable<VALUE>}
 * @nosideeffects
 */
Set.prototype.values = function() {};

/**
 * @return {!Iterator<VALUE>}
 */
Set.prototype[Symbol.iterator] = function() {};



/**
 * @constructor @struct
 * @param {Iterable<VALUE>|Array<VALUE>=} opt_iterable
 * @template VALUE
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set
 */
function WeakSet(opt_iterable) {}

/**
 * @param {VALUE} value
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 */
WeakSet.prototype.add = function(value) {};

/**
 * @return {void}
 */
WeakSet.prototype.clear = function() {};

/**
 * @param {VALUE} value
 * @return {boolean}
 */
WeakSet.prototype.delete = function(value) {};

/**
 * @param {VALUE} value
 * @return {boolean}
 * @nosideeffects
 */
WeakSet.prototype.has = function(value) {};
