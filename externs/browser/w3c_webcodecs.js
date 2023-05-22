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


/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-alphaoption
 * Valid values are "keep" and "discard".
 */
var AlphaOption;

/**
 * @typedef {!Object}
 * Currently an empty list.
 * Note - using a non-standard name to avoid collision with
 * requestVideoFramCallback's VideoFrameMetadata type.
 * @see https://w3c.github.io/webcodecs/video_frame_metadata_registry.html
 */
var WebCodecsVideoFrameMetadata;

/**
 * @typedef {{
 *   duration: (number|undefined),
 *   timestamp: (number|undefined),
 *   alpha: (!AlphaOption|undefined),
 *   visibleRect: (!DOMRectInit|undefined),
 *   displayWidth: (number|undefined),
 *   displayHeight: (number|undefined),
 *   metadata: (!WebCodecsVideoFrameMetadata|undefined)
 * }}
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videoframeinit
 */
var VideoFrameInit;

/**
 * @typedef {{
 *   offset: number,
 *   stride: number
 * }}
 * @see https://www.w3.org/TR/webcodecs/#dictdef-planelayout
 */
var PlaneLayout;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#pixel-format
 */
var VideoPixelFormat;

/**
 * @typedef {{
 *   format: !VideoPixelFormat,
 *   codedWidth: number,
 *   codedHeight: number,
 *   timestamp: number,
 *   duration: (number|undefined),
 *   layout: (!Array<!PlaneLayout>|undefined),
 *   visibleRect: (!DOMRectInit|undefined),
 *   displayWidth: (number|undefined),
 *   displayHeight: (number|undefined),
 *   colorSpace: (!VideoColorSpaceInit|undefined)
 * }}
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videoframeinit
 */
var VideoFrameBufferInit;

/**
 * @param {!CanvasImageSource|!BufferSource} imageOrData
 * @param {!VideoFrameInit|!VideoFrameBufferInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#videoframe-constructors
 */
function VideoFrame(imageOrData, init) {}

/**
 * @type {?VideoPixelFormat}
 */
VideoFrame.prototype.format;

/**
 * @type {number}
 */
VideoFrame.prototype.codedWidth;

/**
 * @type {number}
 */
VideoFrame.prototype.codedHeight;

/**
 * @type {?DOMRectReadOnly}
 */
VideoFrame.prototype.codedRect;

/**
 * @type {?DOMRectReadOnly}
 */
VideoFrame.prototype.visibleRect;

/**
 * @type {number}
 */
VideoFrame.prototype.displayWidth;

/**
 * @type {number}
 */
VideoFrame.prototype.displayHeight;

/**
 * @type {number|null}
 */
VideoFrame.prototype.duration;

/**
 * @type {number|null}
 */
VideoFrame.prototype.timestamp;

/**
 * @type {!VideoColorSpace}
 */
VideoFrame.prototype.colorSpace;

/** @return {!WebCodecsVideoFrameMetadata} */
VideoFrame.prototype.metadata = function() {};

/**
 * @typedef {{
 *   rect: (!DOMRectInit|undefined),
 *   layout: (!Array<!PlaneLayout>|undefined)
 * }}
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videoframecopytooptions
 */
var VideoFrameCopyToOptions;

/**
 * @param {!VideoFrameCopyToOptions=} options
 * @return {number}
 */
VideoFrame.prototype.allocationSize = function(options) {};

/**
 * @param {!BufferSource} destination
 * @param {!VideoFrameCopyToOptions=} options
 * @return {!Promise<!Array<!PlaneLayout>>}
 */
VideoFrame.prototype.copyTo = function(destination, options) {};

/**
 * @return {!VideoFrame}
 */
VideoFrame.prototype.clone = function() {};

VideoFrame.prototype.close = function() {};
