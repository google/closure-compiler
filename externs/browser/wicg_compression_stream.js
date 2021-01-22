/**
 * @fileoverview APIs used to compress and decompress streams of data.
 * @see https://wicg.github.io/compression
 * @see https://github.com/WICG/compression/blob/master/explainer.md
 * @externs
 */

/**
 * @constructor
 * @see https://wicg.github.io/compression/#compressionstream
 * @param {string} format
 * @see https://wicg.github.io/compression/#supported-formats
 * @template IN_VALUE, OUT_VALUE
 */
function CompressionStream(format) {}

/**
 * @type {!ReadableStream<IN_VALUE>}
 */
CompressionStream.prototype.readable;

/**
 * @type {!WritableStream<OUT_VALUE>}
 */
CompressionStream.prototype.writable;

/**
 * @constructor
 * @see https://wicg.github.io/compression/#decompression-stream
 * @param {string} format
 * @see https://wicg.github.io/compression/#supported-formats
 * @template IN_VALUE, OUT_VALUE
 */
function DecompressionStream(format) {}

/**
 * @type {!ReadableStream<IN_VALUE>}
 */
DecompressionStream.prototype.readable;

/**
 * @type {!WritableStream<OUT_VALUE>}
 */
DecompressionStream.prototype.writable;