/*
 * Copyright 2015 The Closure Compiler Authors
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
 * @fileoverview Definitions from the Streams API as of 22 June 2015.
 * @see https://streams.spec.whatwg.org/commit-snapshots/b2131ef6460c446817767d7575698f9fcfa554a3/
 * @externs
 */

/**
 * @typedef {{ value: (*|undefined), done: boolean }}
 */
var IteratorResult;

/**
 * @param {{ highWaterMark: number }} config
 * @constructor
 */
function ByteLengthQueuingStrategy(config) {}

/**
 * @param {*} chunk
 * @return {number}
 */
ByteLengthQueuingStrategy.prototype.size = function(chunk) {};

/**
 * @param {{ highWaterMark: number }} config
 * @constructor
 */
function CountQueuingStrategy(config) {}

/**
 * @param {*} chunk
 * @return {number}
 */
CountQueuingStrategy.prototype.size = function(chunk) {};

/**
 * The value of {@code highWaterMark} in a custom strategy must be non-negative.
 * @typedef {!CountQueuingStrategy|!ByteLengthQueuingStrategy|{
 *     size: (undefined|function(*): number),
 *     highWaterMark: number
 * }}
 */
var QueuingStrategy;

/**
 * @typedef {{
 *     writable: !WritableStream,
 *     readable: !ReadableStream
 * }}
 */
var TransformStream;


/**
 * @typedef {undefined|{
 *     start: (undefined|function(!ReadableStreamController): (!Promise<*>|void)),
 *     pull: (undefined|function(!ReadableStreamController): (!Promise<*>|void)),
 *     cancel: (undefined|function(*): (!Promise<*>|void))
 * }}
 */
var ReadableStreamSource;

/**
 * @see https://streams.spec.whatwg.org/#rs-class
 * @param {?ReadableStreamSource=} opt_underlyingSource
 * @param {QueuingStrategy=} opt_strategy
 * @constructor
 */
function ReadableStream(opt_underlyingSource, opt_strategy) {}

/** @type {boolean} */
ReadableStream.prototype.locked;

/**
 * @param {*} reason
 */
ReadableStream.prototype.cancel = function(reason) {};

/**
 * @return {!ReadableStreamReader}
 */
ReadableStream.prototype.getReader = function() {};

/**
 * @typedef {{
 *     preventClose: (undefined|boolean),
 *     preventAbort: (undefined|boolean),
 *     preventCancel: (undefined|boolean)
 * }}
 */
var PipeOptions;

/**
 * @param {!TransformStream} transform
 * @param {PipeOptions=} opt_options
 * @return {!ReadableStream}
 */
ReadableStream.prototype.pipeThrough = function(transform, opt_options) {};

/**
 * @param {!WritableStream} dest
 * @param {PipeOptions=} opt_options
 * @return {!Promise<*>}
 */
ReadableStream.prototype.pipeTo = function(dest, opt_options) {};

/**
 * @return {!Array<!ReadableStream>} Two-element array, containing resulting
 *     streams.
 */
ReadableStream.prototype.tee = function() {};

/**
 * @see https://streams.spec.whatwg.org/#rs-controller-class
 * @param {!ReadableStream} stream
 * @constructor
 */
function ReadableStreamController(stream) {}

/** @type {number} */
ReadableStreamController.prototype.desiredSize;

/** @return {undefined} */
ReadableStreamController.prototype.close = function() {};

/**
 * @param {*} chunk
 */
ReadableStreamController.prototype.enqueue = function(chunk) {};

/**
 * @param {*} e
 */
ReadableStreamController.prototype.error = function(e) {};

/**
 * @see https://streams.spec.whatwg.org/#reader-class
 * @param {!ReadableStream} stream
 * @constructor
 */
function ReadableStreamReader(stream) {}

/** @type {boolean} */
ReadableStreamReader.prototype.closed;

/**
 * @param {*} reason
 */
ReadableStreamReader.prototype.cancel = function(reason) {};

/**
 * @return {!Promise<!IteratorResult>}
 */
ReadableStreamReader.prototype.read = function() {};

/** @return {undefined} */
ReadableStreamReader.prototype.releaseLock = function() {};

/**
 * @typedef {(undefined|{
 *     start: (undefined|function(*): (!Promise<*>|void)),
 *     write: (undefined|function(*): (!Promise<*>|void)),
 *     close: (undefined|function(): (!Promise<*>|void)),
 *     abort: (undefined|function(*): (!Promise<*>|void))
 * })}
 */
var WritableStreamSink;

/**
 * @see https://streams.spec.whatwg.org/#ws-class
 * @param {WritableStreamSink=} opt_underlyingSink
 * @param {QueuingStrategy=} opt_strategy
 * @constructor
 */
function WritableStream(opt_underlyingSink, opt_strategy) {}

/** @type {!Promise<*>} */
WritableStream.prototype.closed;

/** @type {!Promise<*>} */
WritableStream.prototype.ready;

/** @type {string} */
WritableStream.prototype.state;

/**
 * @param {*} reason
 * @return {!Promise<undefined>}
 */
WritableStream.prototype.abort = function(reason) {};

/**
 * @return {!Promise<undefined>}
 */
WritableStream.prototype.close = function() {};

/**
 * @return {!Promise<*>}
 */
WritableStream.prototype.write = function(chunk) {};

/**
 * @see https://streams.spec.whatwg.org/#rbs-class
 * @param {?ReadableStreamSource=} opt_underlyingSource
 * @param {QueuingStrategy=} opt_strategy
 * @constructor
 */
function ReadableByteStream(opt_underlyingSource, opt_strategy) {}

/** @type {boolean} */
ReadableByteStream.prototype.locked;

/**
 * @param {*} reason
 */
ReadableByteStream.prototype.cancel = function(reason) {};

/**
 * @return {!ReadableByteStreamReader}
 */
ReadableByteStream.prototype.getReader = function() {};

/**
 * @param {!TransformStream} transform
 * @param {PipeOptions=} opt_options
 * @return {!ReadableByteStream}
 */
ReadableByteStream.prototype.pipeThrough = function(transform, opt_options) {};

/**
 * @param {!WritableStream} dest
 * @param {PipeOptions=} opt_options
 * @return {!Promise<*>}
 */
ReadableByteStream.prototype.pipeTo = function(dest, opt_options) {};

/**
 * @return {!Array<!ReadableByteStream>} Two-element array containing resulting
 *     streams.
 */
ReadableByteStream.prototype.tee = function() {};

/**
 * @see https://streams.spec.whatwg.org/#rs-controller-class
 * @param {!ReadableByteStream} stream
 * @constructor
 */
function ReadableByteStreamController(stream) {}

/** @type {number} */
ReadableByteStreamController.prototype.desiredSize;

/** @return {undefined} */
ReadableByteStreamController.prototype.close = function() {};

/**
 * @param {*} chunk
 */
ReadableByteStreamController.prototype.resolve = function(chunk) {};

/**
 * @param {*} e
 */
ReadableByteStreamController.prototype.error = function(e) {};

/**
 * @see https://streams.spec.whatwg.org/#reader-class
 * @param {!ReadableByteStream} stream
 * @constructor
 */
function ReadableByteStreamReader(stream) {}

/** @type {boolean} */
ReadableByteStreamReader.prototype.closed;

/**
 * @param {*} reason
 */
ReadableByteStreamReader.prototype.cancel = function(reason) {};

/**
 * @return {!Promise<!IteratorResult>}
 */
ReadableByteStreamReader.prototype.read = function() {};

/** @return {undefined} */
ReadableByteStreamReader.prototype.releaseLock = function() {};
