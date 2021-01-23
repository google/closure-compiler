/*
 * Copyright 2021 The Closure Compiler Authors
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