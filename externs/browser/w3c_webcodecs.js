/*
 * Copyright 2022 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's Web Codecs API.
 * @see https://www.w3.org/TR/webcodecs/
 * @externs
 */


/**
 * @constructor
 * @param {undefined|VideoColorSpaceInit} init
 * @see https://www.w3.org/TR/webcodecs/#videocolorspace
 */
function VideoColorSpace(init) {}

/**
 * @const {string|null}
 */
VideoColorSpace.prototype.primaries;

/**
 * @const {string|null}
 */
VideoColorSpace.prototype.transfer;

/**
 * @const {string|null}
 */
VideoColorSpace.prototype.matrix;

/**
* @const {boolean|null}
*/
VideoColorSpace.prototype.fullRange;


/**
 * @interface
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videocolorspaceinit
 */
function VideoColorSpaceInit() {}

/**
 * @type {undefined|string}
 */
VideoColorSpaceInit.prototype.primaries;

/**
 * @type {undefined|string}
 */
VideoColorSpaceInit.prototype.transfer;

/**
 * @type {undefined|string}
 */
VideoColorSpaceInit.prototype.matrix;

/**
* @type {undefined|boolean}
*/
VideoColorSpaceInit.prototype.fullRange;
