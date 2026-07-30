/*
 * Copyright 2026 The Closure Compiler Authors
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
 * @fileoverview Definitions for ECMAScript TypedArray externs.
 * @see https://www.khronos.org/registry/typedarray/specs/latest/
 * @externs
 */

/**
 * @constructor
 * @implements {IArrayLike<number>}
 * @implements {Iterable<number>}
 * @extends {ArrayBufferView}
 * @template TArrayBuffer (unused)
 */
function TypedArray() {};

/** @const {number} */
TypedArray.prototype.BYTES_PER_ELEMENT;

/**
 * NOTE: this is an ES2022 extern.
 * @param {number} index
 * @return {(number|undefined)}
 * @this {THIS}
 * @template THIS
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/at
 */
TypedArray.prototype.at = function(index) {};

/**
 * @param {number} target
 * @param {number} start
 * @param {number=} opt_end
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/copyWithin
 */
TypedArray.prototype.copyWithin = function(target, start, opt_end) {};

/**
 * @return {!IteratorIterable<!Array<number>>}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/entries
 */
TypedArray.prototype.entries = function() {};

/**
 * @param {function(this:S, number, number, !TypedArray) : *} callback
 * @param {S=} opt_thisArg
 * @return {boolean}
 * @template S
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/every
 */
TypedArray.prototype.every = function(callback, opt_thisArg) {};

/**
 * @param {number} value
 * @param {number=} opt_begin
 * @param {number=} opt_end
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/fill
 */
TypedArray.prototype.fill = function(value, opt_begin, opt_end) {};

/**
 * @param {function(this:S, number, number, !TypedArray) : *} callback
 * @param {S=} opt_thisArg
 * @return {THIS}
 * @this {THIS}
 * @template THIS,S
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/filter
 */
TypedArray.prototype.filter = function(callback, opt_thisArg) {};

/**
 * @param {function(this:S, number, number, !TypedArray) : *} callback
 * @param {S=} opt_thisArg
 * @return {(number|undefined)}
 * @template S
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/find
 */
TypedArray.prototype.find = function(callback, opt_thisArg) {};

/**
 * @param {function(this:S, number, number, !TypedArray) : *} callback
 * @param {S=} opt_thisArg
 * @return {number}
 * @template S
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/findIndex
 */
TypedArray.prototype.findIndex = function(callback, opt_thisArg) {};

/**
 * @param {function(this:S, number, number, !TypedArray) : boolean} callback
 * @param {S=} opt_thisArg
 * @return {(number|undefined)}
 * @template S
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/findLast
 */
TypedArray.prototype.findLast = function(callback, opt_thisArg) {};

/**
 * @param {function(this:S, number, number, !TypedArray) : boolean} callback
 * @param {S=} opt_thisArg
 * @return {number}
 * @template S
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/findLastIndex
 */
TypedArray.prototype.findLastIndex = function(callback, opt_thisArg) {};

/**
 * @param {function(this:S, number, number, !TypedArray) : ?} callback
 * @param {S=} opt_thisArg
 * @return {undefined}
 * @template S
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/forEach
 */
TypedArray.prototype.forEach = function(callback, opt_thisArg) {};

/**
 * NOTE: this is an ES2016 (ES7) extern.
 * @param {number} searchElement
 * @param {number=} opt_fromIndex
 * @return {boolean}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/includes
 */
TypedArray.prototype.includes = function(searchElement, opt_fromIndex) {};

/**
 * @param {number} searchElement
 * @param {number=} opt_fromIndex
 * @return {number}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/indexOf
 */
TypedArray.prototype.indexOf = function(searchElement, opt_fromIndex) {};

/**
 * @param {string=} opt_separator
 * @return {string}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/join
 */
TypedArray.prototype.join = function(opt_separator) {};

/**
 * @return {!IteratorIterable<number>}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/keys
 */
TypedArray.prototype.keys = function() {};

/**
 * @param {number} searchElement
 * @param {number=} opt_fromIndex
 * @return {number}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/lastIndexOf
 */
TypedArray.prototype.lastIndexOf = function(searchElement, opt_fromIndex) {};

/** @type {number} */
TypedArray.prototype.length;

/**
 * @param {function(this:S, number, number, !TypedArray) : number} callback
 * @param {S=} opt_thisArg
 * @return {THIS}
 * @this {THIS}
 * @template THIS,S
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/map
 */
TypedArray.prototype.map = function(callback, opt_thisArg) {};

/**
 * @param {function((number|INIT|RET), number, number, !TypedArray) : RET}
 *     callback
 * @param {INIT=} opt_initialValue
 * @return {RET}
 * @template INIT,RET
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/reduce
 */
TypedArray.prototype.reduce = function(callback, opt_initialValue) {};

/**
 * @param {function((number|INIT|RET), number, number, !TypedArray) : RET}
 *     callback
 * @param {INIT=} opt_initialValue
 * @return {RET}
 * @template INIT,RET
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/reduceRight
 */
TypedArray.prototype.reduceRight = function(callback, opt_initialValue) {};

/**
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/reverse
 */
TypedArray.prototype.reverse = function() {};

/**
 * @param {!ArrayBufferView|!Array<number>} array
 * @param {number=} opt_offset
 * @return {undefined}
 * @throws {!RangeError}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/set
 */
TypedArray.prototype.set = function(array, opt_offset) {};

/**
 * @param {number=} opt_begin
 * @param {number=} opt_end
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/slice
 */
TypedArray.prototype.slice = function(opt_begin, opt_end) {};

/**
 * @param {function(this:S, number, number, !TypedArray) : *} callback
 * @param {S=} opt_thisArg
 * @return {boolean}
 * @template S
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/some
 */
TypedArray.prototype.some = function(callback, opt_thisArg) {};

/**
 * @param {(function(number, number) : number)=} opt_compareFunction
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/sort
 */
TypedArray.prototype.sort = function(opt_compareFunction) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/subarray
 */
TypedArray.prototype.subarray = function(begin, opt_end) {};

/**
 * @return {!IteratorIterable<number>}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/values
 */
TypedArray.prototype.values = function() {};

/**
 * NOTE: this is an ES2023 extern.
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/toReversed
 */
TypedArray.prototype.toReversed = function() {};

/**
 * NOTE: this is an ES2023 extern.
 * @param {function(number, number): number=} compareFn
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/toSorted
 */
TypedArray.prototype.toSorted = function(compareFn) {};

/**
 * NOTE: this is an ES2023 extern.
 * @param {number} index
 * @param {number} value
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/with
 */
TypedArray.prototype.with = function(index, value) {};

/**
 * @return {string}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/toLocaleString
 * @override
 */
TypedArray.prototype.toLocaleString = function() {};

/**
 * @return {string}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/toString
 * @override
 */
TypedArray.prototype.toString = function() {};

/** @override */
TypedArray.prototype[Symbol.iterator] = function() {};

/**
 * @param {number|ArrayBufferView|Array<number>|ArrayBuffer|SharedArrayBuffer}
 *     length or array or buffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments} If the user passes a backing array, then indexed
 *     accesses will modify the backing array. JSCompiler does not model
 *     this well. In other words, if you have:
 *     <code>
 *     var x = new ArrayBuffer(1);
 *     var y = new Int8Array(x);
 *     y[0] = 2;
 *     </code>
 *     JSCompiler will not recognize that the last assignment modifies x.
 *     We workaround this by marking all these arrays as @modifies {arguments},
 *     to introduce the possibility that x aliases y.
 */
function Int8Array(length, opt_byteOffset, opt_length) {}

/** @const {number} */
Int8Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<number>|!Iterable<number>} source
 * @param {function(this:S, ?, number): number=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!Int8Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
Int8Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...number} var_args
 * @return {!Int8Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
Int8Array.of = function(var_args) {};


/**
 * Options to use when decoding base64 into a Uint8Array.
 * @record
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array/fromBase64#options
 */
function Uint8ArrayBase64Options() {}

/**
 * Specifies the base64 alphabet to use. This can either be "base64", which will include the special characters `+`
 * and `/`, or "base64url", which will include the special characters `-` and `_`. If not specified, it defaults to
 * "base64".
 * @type {(undefined|string)}
 */
Uint8ArrayBase64Options.prototype.alphabet;

/**
 * Specifies how to handle the last chunk of the base64 string if it is an incomplete chunk. The value "strict" requires
 * that any incomplete chunk be padded with `=` characters and any trailing bits must be zero. The value
 * "stop-before-partial" indicates that decoding should only handle complete chunks and ignore partial chunks, allowing
 * them to be handled separately if desired (such as in future calls). The default value is "loose", which will decode
 * an incomplete chunk even if not padded by `=`.
 * @type {(undefined|string)}
 */
Uint8ArrayBase64Options.prototype.lastChunkHandling;


/**
 * Results from Uint8Array.prototype.setFromBase64.
 * @record
 */
function Uint8ArraySetFromBase64Results() {}

/**
 * The number of characters read from the base64 string. If the entire string's contents fit into the Uint8Array,
 * this will be equal to string.length. Otherwise, this will be equal to the number of 4-character chunks that
 * fit into the array.
 * @type {number}
 */
Uint8ArraySetFromBase64Results.prototype.read;

/**
 * The number of bytes written into the Uint8Array.
 * @type {number}
 */
Uint8ArraySetFromBase64Results.prototype.written;

/**
 * Options to use when invoking Uint8Array.prototype.toBase64.
 * @record
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array/toBase64#options
 */
function Uint8ArrayToBase64Options() {}

/**
 * Specifies the base64 alphabet to use. This can either be "base64", which will include the special characters `+`
 * and `/`, or "base64url", which will include the special characters `-` and `_`. If not specified, it defaults to
 * "base64".
 * @type {(undefined|string)}
 */
Uint8ArrayToBase64Options.prototype.alphabet;

/**
 * If true, the output base64 string will omit any `=` padding characters at the end. Defaults to false.
 * @type {(undefined|boolean)}
 */
Uint8ArrayToBase64Options.prototype.omitPadding;

/**
 * Results from Uint8Array.prototype.setFromHex.
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array/setFromHex#return_value
 * @interface
 * @struct
 */
function Uint8ArraySetFromHexResults() {}

/**
 * The number of hex characters read from the input string. If the decoded data fits into the
 * array, it is the length of the input string; otherwise, it is the number of complete hex
 * characters that fit into the array.
 * @type {number}
 */
Uint8ArraySetFromHexResults.prototype.read;

/**
 * The number of bytes written into the Uint8Array. Will never be greater than this Uint8Array's
 * length.
 * @type {number}
 */
Uint8ArraySetFromHexResults.prototype.written;

/**
 * @param {number|ArrayBufferView|Array<number>|ArrayBuffer|SharedArrayBuffer}
 *     length or array or buffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 */
function Uint8Array(length, opt_byteOffset, opt_length) {}

/** @const {number} */
Uint8Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<number>|!Iterable<number>} source
 * @param {function(this:S, ?, number): number=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!Uint8Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
Uint8Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...number} var_args
 * @return {!Uint8Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
Uint8Array.of = function(var_args) {};

/**
 * Creates a new Uint8Array from a base64 encoded string.
 * @param {string} string a base64 string encoded array
 * @param {?Uint8ArrayBase64Options=} options an object specifying how to read the base64 string
 * @return {!Uint8Array} a newly created array with the specified bytes
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array/fromBase64
 */
Uint8Array.fromBase64 = function(string, options) {};

/**
 * Creates a new Uint8Array from a case-insensitive hex string with even length.
 * @param {string} string a hex string encoded array of bytes
 * @return {!Uint8Array} a newly created array with the specified bytes
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array/fromHex
 */
Uint8Array.fromHex = function(string) {};

/**
 * Sets the contents of the Uint8Array from a base64 encoded string.
 * @param {string} string a base64 string encoded array to write into this array
 * @param {?Uint8ArrayBase64Options=} options an object specifying how to read the base64 string
 * @return {!Uint8ArraySetFromBase64Results} results about the operation
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array/setFromBase64
 */
Uint8Array.prototype.setFromBase64 = function(string, options) {};

/**
 * Sets the contents of the Uint8Array from a hex encoded even length string. The string may
 * contain uppercase or lowercase hex characters.
 * @param {string} string a hex string of even length
 * @return {!Uint8ArraySetFromHexResults} results containing read and written stats
 */
Uint8Array.prototype.setFromHex = function(string) {};

/**
 * Encodes the contents of the Uint8Array into a base64 string.
 * @param {?Uint8ArrayToBase64Options=} options an object specifying how to encode the base64 string
 * @return {string} a base64 encoded string representing the contents of this array
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array/toBase64
 */
Uint8Array.prototype.toBase64 = function(options) {};

/**
 * Encodes the contents of the Uint8Array into a hex string.
 * @return {string} a hex encoded string representing the contents of this array
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array/toHex
 */
Uint8Array.prototype.toHex = function() {};

/**
 * @param {number|ArrayBufferView|Array<number>|ArrayBuffer|SharedArrayBuffer}
 *     length or array or buffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 */
function Uint8ClampedArray(length, opt_byteOffset, opt_length) {}

/** @const {number} */
Uint8ClampedArray.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<number>|!Iterable<number>} source
 * @param {function(this:S, ?, number): number=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!Uint8ClampedArray}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
Uint8ClampedArray.from = function(source, mapFn, thisArg) {};

/**
 * @param {...number} var_args
 * @return {!Uint8ClampedArray}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
Uint8ClampedArray.of = function(var_args) {};


/**
 * @param {number|ArrayBufferView|Array<number>|ArrayBuffer|SharedArrayBuffer}
 *     length or array or buffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 */
function Int16Array(length, opt_byteOffset, opt_length) {}

/** @const {number} */
Int16Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<number>|!Iterable<number>} source
 * @param {function(this:S, ?, number): number=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!Int16Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
Int16Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...number} var_args
 * @return {!Int16Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
Int16Array.of = function(var_args) {};


/**
 * @param {number|ArrayBufferView|Array<number>|ArrayBuffer|SharedArrayBuffer}
 *     length or array or buffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 */
function Uint16Array(length, opt_byteOffset, opt_length) {}

/** @const {number} */
Uint16Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<number>|!Iterable<number>} source
 * @param {function(this:S, ?, number): number=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!Uint16Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
Uint16Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...number} var_args
 * @return {!Uint16Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
Uint16Array.of = function(var_args) {};


/**
 * @param {number|ArrayBufferView|Array<number>|ArrayBuffer|SharedArrayBuffer}
 *     length or array or buffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 */
function Int32Array(length, opt_byteOffset, opt_length) {}

/** @const {number} */
Int32Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<number>|!Iterable<number>} source
 * @param {function(this:S, ?, number): number=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!Int32Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
Int32Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...number} var_args
 * @return {!Int32Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
Int32Array.of = function(var_args) {};


/**
 * @param {number|ArrayBufferView|Array<number>|ArrayBuffer|SharedArrayBuffer}
 *     length or array or buffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 */
function Uint32Array(length, opt_byteOffset, opt_length) {}

/** @const {number} */
Uint32Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<number>|!Iterable<number>} source
 * @param {function(this:S, ?, number): number=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!Uint32Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
Uint32Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...number} var_args
 * @return {!Uint32Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
Uint32Array.of = function(var_args) {};


/**
 * @param {number|ArrayBufferView|Array<number>|ArrayBuffer|SharedArrayBuffer}
 *     length
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Float16Array
 */
function Float16Array(length, opt_byteOffset, opt_length) {}

/** @const {number} */
Float16Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<number>|!Iterable<number>} source
 * @param {function(this:S, ?, number): number=} mapFn
 * @param {S=} thisArg
 * @return {!Float16Array}
 * @template S
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
Float16Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...number} var_args
 * @return {!Float16Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
Float16Array.of = function(var_args) {};

/**
 * @param {number|ArrayBufferView|Array<number>|ArrayBuffer|SharedArrayBuffer}
 *     length or array or buffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 */
function Float32Array(length, opt_byteOffset, opt_length) {}

/** @const {number} */
Float32Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<number>|!Iterable<number>} source
 * @param {function(this:S, ?, number): number=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!Float32Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
Float32Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...number} var_args
 * @return {!Float32Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
Float32Array.of = function(var_args) {};


/**
 * @param {number|ArrayBufferView|Array<number>|ArrayBuffer|SharedArrayBuffer}
 *     length or array or buffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 */
function Float64Array(length, opt_byteOffset, opt_length) {}

/** @const {number} */
Float64Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<number>|!Iterable<number>} source
 * @param {function(this:S, ?, number): number=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!Float64Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
Float64Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...number} var_args
 * @return {!Float64Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
Float64Array.of = function(var_args) {};


/**
 * @param {number|ArrayBufferView|Array<bigint>|ArrayBuffer|SharedArrayBuffer}
 *     lengthOrArrayOrBuffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} byteOffset
 * @param {number=} bufferLength
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 */
function BigInt64Array(lengthOrArrayOrBuffer, byteOffset, bufferLength) {}

/** @const {number} */
BigInt64Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<bigint>|!Iterable<bigint>} source
 * @param {function(this:S, ?, number): bigint=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!BigInt64Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
BigInt64Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...bigint} var_args
 * @return {!BigInt64Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
BigInt64Array.of = function(var_args) {};

/**
 * @param {(function(bigint, bigint) : number)=} opt_compareFunction
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @override
 * @suppress {checkTypes} The types are specialized in this override.
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/sort
 */
BigInt64Array.prototype.sort = function(opt_compareFunction) {};

/**
 * NOTE: this is an ES2023 extern.
 * @param {function(bigint, bigint): number=} compareFn
 * @return {!BigInt64Array}
 * @nosideeffects
 * @override
 * @suppress {checkTypes} The types are specialized in this override.
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/toSorted
 */
BigInt64Array.prototype.toSorted = function(compareFn) {};

/**
 * NOTE: this is an ES2023 extern.
 * @param {number} index
 * @param {bigint} value
 * @return {!BigInt64Array}
 * @nosideeffects
 * @override
 * @suppress {checkTypes} The types are specialized in this override.
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/with
 */
BigInt64Array.prototype.with = function(index, value) {};

/**
 * @param {number|ArrayBufferView|Array<bigint>|ArrayBuffer|SharedArrayBuffer}
 *     lengthOrArrayOrBuffer
 *     NOTE: We require that at least this first argument be present even though
 *         the ECMAScript spec allows it to be absent, because this is better
 *         for readability and detection of programmer errors.
 * @param {number=} byteOffset
 * @param {number=} bufferLength
 * @template TArrayBuffer (unused)
 * @constructor
 * @extends {TypedArray}
 * @throws {Error}
 * @modifies {arguments}
 */
function BigUint64Array(lengthOrArrayOrBuffer, byteOffset, bufferLength) {}

/** @const {number} */
BigUint64Array.BYTES_PER_ELEMENT;

/**
 * @param {string|!IArrayLike<bigint>|!Iterable<bigint>} source
 * @param {function(this:S, ?, number): bigint=} mapFn
 * @param {S=} thisArg
 * @template S
 * @return {!BigUint64Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/from
 */
BigUint64Array.from = function(source, mapFn, thisArg) {};

/**
 * @param {...bigint} var_args
 * @return {!BigUint64Array}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/of
 */
BigUint64Array.of = function(var_args) {};

/**
 * @param {(function(bigint, bigint) : number)=} opt_compareFunction
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @override
 * @suppress {checkTypes} The types are specialized in this override.
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/sort
 */
BigUint64Array.prototype.sort = function(opt_compareFunction) {};

/**
 * NOTE: this is an ES2023 extern.
 * @param {function(bigint, bigint): number=} compareFn
 * @return {!BigUint64Array}
 * @nosideeffects
 * @override
 * @suppress {checkTypes} The types are specialized in this override.
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/toSorted
 */
BigUint64Array.prototype.toSorted = function(compareFn) {};

/**
 * NOTE: this is an ES2023 extern.
 * @param {number} index
 * @param {bigint} value
 * @return {!BigUint64Array}
 * @nosideeffects
 * @override
 * @suppress {checkTypes} The types are specialized in this override.
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray/with
 */
BigUint64Array.prototype.with = function(index, value) {};
