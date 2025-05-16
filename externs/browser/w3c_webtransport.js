/*
 * Copyright 2025 The Closure Compiler Authors.
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
 * @fileoverview W3C WebTransport specification.
 * @see https://developer.mozilla.org/en-US/docs/Web/API/WebTransport_API
 *
 * @externs
 */


/**
 * @typedef {{
 *   algorithm: (string|undefined),
 *   value: (BufferSource|undefined),
 * }}
 */
var WebTransportHash;

/**
 * @typedef {{
 *   closeCode: (number|undefined),
 *   reason: (string|undefined),
 * }}
 */
var WebTransportCloseInfo;

/**
 * @typedef {{
 *   allowPooling: (boolean|undefined),
 *   congestionControl: (string|undefined),
 *   requireUnreliable: (boolean|undefined),
 *   serverCertificateHashes: (!Array<!WebTransportHash>|undefined),
 * }}
 */
var WebTransportOptions;

/**
 * @typedef {{
 *   sendOrder: (number|undefined),
 * }}
 */
var WebTransportSendStreamOptions;

/**
 * @constructor
 * @param {(string|!URL)} url
 * @param {!WebTransportOptions=} options
 * @see https://developer.mozilla.org/docs/Web/API/WebTransport
 */
function WebTransport(url, options) {};

/** @type {!Promise<!WebTransportCloseInfo>} */
WebTransport.prototype.closed;

/** @type {!WebTransportDatagramDuplexStream} */
WebTransport.prototype.datagrams;

/** @type {!ReadableStream} */
WebTransport.prototype.incomingBidirectionalStreams;

/** @type {!ReadableStream} */
WebTransport.prototype.incomingUnidirectionalStreams;

/** @type {!Promise<undefined>} */
WebTransport.prototype.ready;

/**
 * @param {WebTransportCloseInfo=} closeInfo
 * @return {void}
 */
WebTransport.prototype.close = function(closeInfo) {};

/**
 * @param {!WebTransportSendStreamOptions=} options
 * @return {!Promise<!WebTransportBidirectionalStream>}
 */
WebTransport.prototype.createBidirectionalStream = function(options) {};

/**
 * @param {!WebTransportSendStreamOptions=} options
 * @return {!Promise<!WritableStream>}
 */
WebTransport.prototype.createUnidirectionalStream = function(options) {};


/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/WebTransportBidirectionalStream
 */
function WebTransportBidirectionalStream() {};

/** @type {!ReadableStream} */
WebTransportBidirectionalStream.prototype.readable;

/** @type {!WritableStream} */
WebTransportBidirectionalStream.prototype.writable;


/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/WebTransportDatagramDuplexStream
 */
function WebTransportDatagramDuplexStream() {};

/** @type {number} */
WebTransportDatagramDuplexStream.prototype.incomingHighWaterMark;

/** @type {number|null} */
WebTransportDatagramDuplexStream.prototype.incomingMaxAge;

/** @type {number} */
WebTransportDatagramDuplexStream.prototype.maxDatagramSize;

/** @type {number} */
WebTransportDatagramDuplexStream.prototype.outgoingHighWaterMark;

/** @type {number|null} */
WebTransportDatagramDuplexStream.prototype.outgoingMaxAge;

/** @type {!ReadableStream} */
WebTransportDatagramDuplexStream.prototype.readable;

/** @type {!WritableStream} */
WebTransportDatagramDuplexStream.prototype.writable;

/**
 * @typedef {{
 *   source: (string|undefined),
 *   streamErrorCode: (number|undefined),
 * }}
 */
var WebTransportErrorOptions;

/**
 * @constructor
 * @extends {DOMException}
 * @param {string=} message
 * @param {!WebTransportErrorOptions=} options
 * @see https://developer.mozilla.org/docs/Web/API/WebTransportError
 */
function WebTransportError(message, options) {};

/** @type {string} */
WebTransportError.prototype.source;

/** @type {number|null} */
WebTransportError.prototype.streamErrorCode;




