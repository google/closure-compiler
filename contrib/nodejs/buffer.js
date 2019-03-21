/*
 * Copyright 2012 The Closure Compiler Authors.
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
 * @fileoverview Definitions for node's buffer module.
 * @see http://nodejs.org/api/buffer.html
 * @see https://github.com/joyent/node/blob/master/lib/buffer.js
 */

/**
 * @const
 */
var buffer = {};

/** @const {function(new:Buffer, ...?)} */
buffer.Buffer;

/**
 * @param {number} size
 * @constructor
 */
buffer.SlowBuffer = function(size) {};

/**
 *
 * @param {string} string
 * @param {number|string} offset
 * @param {number|string=} length
 * @param {number|string=} encoding
 * @return {*}
 */
buffer.SlowBuffer.prototype.write;

/**
 * @param {number} start
 * @param {number} end
 * @return {Buffer}
 */
buffer.SlowBuffer.prototype.slice;

/**
 * @return {string}
 */
buffer.SlowBuffer.prototype.toString;

/**
 * @param {number} size
 * @param {(string|!Buffer|number)=} fill
 * @param {string=} encoding
 * @return {!Buffer}
 */
buffer.Buffer.alloc;
