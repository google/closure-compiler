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
 * @extends {Iterable<!Array<K|V>>}
 * @template K, V
 */
function ReadonlyMap() {}

/**
 * @return {!IteratorIterable<!Array<K|V>>}
 * @nosideeffects
 */
ReadonlyMap.prototype.entries = function() {};

/**
 * @param {function(this:THIS, V, K, MAP): ?} callback
 * @param {THIS=} opt_thisArg
 * @this {MAP}
 * @template MAP, THIS
 * @return {undefined}
 */
ReadonlyMap.prototype.forEach = function(callback, opt_thisArg) {};

/**
 * @param {K} key
 * @return {V|undefined}
 * @nosideeffects
 */
ReadonlyMap.prototype.get = function(key) {};

/**
 * @param {K} key
 * @return {boolean}
 * @nosideeffects
 */
ReadonlyMap.prototype.has = function(key) {};

/**
 * @return {!IteratorIterable<K>}
 * @nosideeffects
 */
ReadonlyMap.prototype.keys = function() {};

/**
 * @const {number}
 * @nosideeffects
 */
ReadonlyMap.prototype.size;

/**
 * @return {!IteratorIterable<V>}
 * @nosideeffects
 */
ReadonlyMap.prototype.values = function() {};

/**
 * @return {!IteratorIterable<!Array<K|V>>}
 * @nosideeffects
 */
ReadonlyMap.prototype[Symbol.iterator] = function() {};

/**
 * @constructor @struct
 * @param {Iterable<!Array<K|V>>|!Array<!Array<K|V>>=} opt_iterable
 * @implements {ReadonlyMap<K, V>}
 * @template K, V
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
 */
function Map(opt_iterable) {}

/**
 * @param {!Iterable<T>} items
 * @param {function(T, number): K} callbackFn
 * @return {!Map<K, !Array<T>>}
 * @template T, K
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map/groupBy
 */
Map.groupBy = function(items, callbackFn) {};

/** @return {void} */
Map.prototype.clear = function() {};

/**
 * @param {K} key
 * @return {boolean}
 */
Map.prototype.delete = function(key) {};

/**
 * @override
 * @return {!IteratorIterable<!Array<K|V>>}
 * @nosideeffects
 */
Map.prototype.entries = function() {};

/**
 * @override
 * @param {function(this:THIS, V, K, MAP): ?} callback
 * @param {THIS=} opt_thisArg
 * @this {MAP}
 * @template MAP,THIS
 * @return {undefined}
 */
Map.prototype.forEach = function(callback, opt_thisArg) {};

/**
 * @override
 * @param {K} key
 * @return {V}
 * @nosideeffects
 */
Map.prototype.get = function(key) {};

/**
 * @override
 * @param {K} key
 * @return {boolean}
 * @nosideeffects
 */
Map.prototype.has = function(key) {};

/**
 * @override
 * @return {!IteratorIterable<K>}
 * @nosideeffects
 */
Map.prototype.keys = function() {};

/**
 * @param {K} key
 * @param {V} value
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
 * @return {!IteratorIterable<V>}
 * @nosideeffects
 */
Map.prototype.values = function() {};

/**
 * @override
 * @return {!IteratorIterable<!Array<K|V>>}
 * @nosideeffects
 */
Map.prototype[Symbol.iterator] = function() {};


/**
 * @constructor @struct
 * @param {Iterable<!Array<K|V>>|!Array<!Array<K|V>>=} opt_iterable
 * @template K, V
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WeakMap
 */
function WeakMap(opt_iterable) {}

/** @return {void} */
WeakMap.prototype.clear = function() {};

/**
 * @param {K} key
 * @return {boolean}
 */
WeakMap.prototype.delete = function(key) {};

/**
 * @param {K} key
 * @return {V}
 * @nosideeffects
 */
WeakMap.prototype.get = function(key) {};

/**
 * @param {K} key
 * @return {boolean}
 * @nosideeffects
 */
WeakMap.prototype.has = function(key) {};

/**
 * @param {K} key
 * @param {V} value
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 */
WeakMap.prototype.set = function(key, value) {};

/**
 * @record
 * @template VALUE
 */
function SetLike() {}

/**
 * @param {VALUE} element
 * @return {boolean}
 */
SetLike.prototype.has = function(element) {};

/**
 * @return {!IteratorIterable<VALUE>}
 */
SetLike.prototype.keys = function() {};

/**
 * @type {number}
 */
SetLike.prototype.size;

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
 * @modifies {this}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/add
 */
Set.prototype.add = function(value) {};

/**
 * @return {void}
 * @modifies {this}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/clear
 */
Set.prototype.clear = function() {};

/**
 * @param {VALUE} value
 * @return {boolean}
 * @modifies {this}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/delete
 */
Set.prototype.delete = function(value) {};

/**
 * @return {!IteratorIterable<!Array<VALUE>>} Where each array has two entries:
 *     [value, value]
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/entries
 */
Set.prototype.entries = function() {};

/**
 * @param {function(this: THIS, VALUE, VALUE, SET)} callback
 * @param {THIS=} opt_thisArg
 * @this {SET}
 * @template SET,THIS
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/forEach
 */
Set.prototype.forEach = function(callback, opt_thisArg) {};

/**
 * @param {VALUE} value
 * @return {boolean}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/has
 */
Set.prototype.has = function(value) {};

/**
 * @return {!IteratorIterable<VALUE>}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/keys
 */
Set.prototype.keys = function() {};

/**
 * @return {!IteratorIterable<VALUE>}
 * @nosideeffects
 * @see https://tc39.es/ecma262/multipage/keyed-collections.html#sec-set.prototype.values
 */
Set.prototype.values = function() {};

/**
 * @template OTHER_VALUE
 * @param {!SetLike<OTHER_VALUE>} other
 * @return {!Set<OTHER_VALUE|VALUE>}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/union
 */
Set.prototype.union = function(other) {};

/**
 * @template OTHER_VALUE
 * @param {!SetLike<OTHER_VALUE>} other
 * @return {!Set<VALUE>}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/intersection
 */
Set.prototype.intersection = function(other) {};

/**
 * @template OTHER_VALUE
 * @param {!SetLike<OTHER_VALUE>} other
 * @return {!Set<VALUE>}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/difference
 */
Set.prototype.difference = function(other) {};

/**
 * @template OTHER_VALUE
 * @param {!SetLike<OTHER_VALUE>} other
 * @return {!Set<OTHER_VALUE|VALUE>}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/symmetricDifference
 */
Set.prototype.symmetricDifference = function(other) {};

/**
 * @template OTHER_VALUE
 * @param {!SetLike<OTHER_VALUE>} other
 * @return {boolean}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/isSupersetOf
 */
Set.prototype.isSupersetOf = function(other) {};

/**
 * @template OTHER_VALUE
 * @param {!SetLike<OTHER_VALUE>} other
 * @return {boolean}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/isSubsetOf
 */
Set.prototype.isSubsetOf = function(other) {};

/**
 * @template OTHER_VALUE
 * @param {!SetLike<OTHER_VALUE>} other
 * @return {boolean}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/isDisjointFrom
 */
Set.prototype.isDisjointFrom = function(other) {};

/**
 * @return {!IteratorIterable<VALUE>}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/Symbol.iterator
 * @override
 */
Set.prototype[Symbol.iterator] = function() {};

/**
 * @type {number} (readonly)
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set/size
 */
Set.prototype.size;

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
