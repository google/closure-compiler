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


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs-avc-codec-registration/#avc-encoder-config
 */
function AvcEncoderConfig() {}

/** @type {(undefined|string)} */
AvcEncoderConfig.prototype.format;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-encodedvideochunkinit
 */
function EncodedVideoChunkInit() {}

/** @type {(!ArrayBuffer|!ArrayBufferView)} */
EncodedVideoChunkInit.prototype.data;

/** @type {(undefined|number)} */
EncodedVideoChunkInit.prototype.duration;

/** @type {number} */
EncodedVideoChunkInit.prototype.timestamp;

/** @type {string} */
EncodedVideoChunkInit.prototype.type;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#callbackdef-encodedvideochunkoutputcallback
 */
function EncodedVideoChunkOutputCallback() {}


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#encoded-video-chunk-metadata
 */
function EncodedVideoChunkMetadata() {}

/** @type {(undefined|!VideoDecoderConfig)} */
EncodedVideoChunkMetadata.prototype.decoderConfig;

/** @type {(undefined|number)} */
EncodedVideoChunkMetadata.prototype.temporalLayerId;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videodecoderconfig
 */
function VideoDecoderConfig() {}
/** @type {string} */
VideoDecoderConfig.prototype.codec;

/** @type {(undefined|number)} */
VideoDecoderConfig.prototype.codedHeight;

/** @type {(undefined|number)} */
VideoDecoderConfig.prototype.codedWidth;

/** @type {(undefined|!VideoColorSpaceInit)} */
VideoDecoderConfig.prototype.colorSpace;

/** @type {(undefined|!ArrayBuffer|!ArrayBufferView)} */
VideoDecoderConfig.prototype.description;

/** @type {(undefined|number)} */
VideoDecoderConfig.prototype.displayAspectHeight;

/** @type {(undefined|number)} */
VideoDecoderConfig.prototype.displayAspectWidth;

/** @type {(undefined|string)} */
VideoDecoderConfig.prototype.hardwareAcceleration;

/** @type {(undefined|boolean)} */
VideoDecoderConfig.prototype.optimizeForLatency;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videodecoderinit
 */
function VideoDecoderInit() {}

/** @type {!WebCodecsErrorCallback} */
VideoDecoderInit.prototype.error;

/** @type {!VideoFrameOutputCallback} */
VideoDecoderInit.prototype.output;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videodecodersupport
 */
function VideoDecoderSupport() {}

/** @type {!VideoDecoderConfig} */
VideoDecoderSupport.prototype.config;

/** @type {boolean} */
VideoDecoderSupport.prototype.supported;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videoencoderconfig
 */
function VideoEncoderConfig() {}

/** @type {(undefined|string)} */
VideoEncoderConfig.prototype.alpha;

/** @type {(undefined|!AvcEncoderConfig)} */
VideoEncoderConfig.prototype.avc;

/** @type {(undefined|number)} */
VideoEncoderConfig.prototype.bitrate;

/** @type {(undefined|string)} */
VideoEncoderConfig.prototype.bitrateMode;

/** @type {string} */
VideoEncoderConfig.prototype.codec;

/** @type {(undefined|number)} */
VideoEncoderConfig.prototype.displayHeight;

/** @type {(undefined|number)} */
VideoEncoderConfig.prototype.displayWidth;

/** @type {(undefined|number)} */
VideoEncoderConfig.prototype.framerate;

/** @type {(undefined|string)} */
VideoEncoderConfig.prototype.hardwareAcceleration;

/** @type {number} */
VideoEncoderConfig.prototype.height;

/** @type {(undefined|string)} */
VideoEncoderConfig.prototype.latencyMode;

/** @type {(undefined|string)} */
VideoEncoderConfig.prototype.scalabilityMode;

/** @type {number} */
VideoEncoderConfig.prototype.width;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#video-encoder-options
 */
function VideoEncoderEncodeOptions() {}

/** @type {(undefined|null|boolean)} */
VideoEncoderEncodeOptions.prototype.keyFrame;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videoencoderinit
 */
function VideoEncoderInit() {}

/** @type {!WebCodecsErrorCallback} */
VideoEncoderInit.prototype.error;

/** @type {!EncodedVideoChunkOutputCallback} */
VideoEncoderInit.prototype.output;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#video-encoder-support
 */
function VideoEncoderSupport() {}

/** @type {!VideoEncoderConfig} */
VideoEncoderSupport.prototype.config;

/** @type {boolean} */
VideoEncoderSupport.prototype.supported;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#callbackdef-videoframeoutputcallback
 */
function VideoFrameOutputCallback() {}

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#callbackdef-webcodecserrorcallback
 */
function WebCodecsErrorCallback() {}


/**
 * @param {!AudioEncoderInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#audioencoder-constructors
 */
function AudioEncoder(init) {}

/**
 * @type {!CodecState}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-state
 */
AudioEncoder.prototype.state;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-encodequeuesize
 */
AudioEncoder.prototype.encodeQueueSize;

/**
 * @type {(function(!Event)|undefined)}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-ondequeue
 */
AudioEncoder.prototype.ondequeue;

/**
 * @param {!AudioEncoderConfig} config
 * @return {!Promise<!AudioEncoderSupport>}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-isconfigsupported
 */
AudioEncoder.isConfigSupported = function(config) {};

/**
 * @param {!AudioEncoderConfig} config
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-configure
 */
AudioEncoder.prototype.configure = function(config) {};

/**
 * @param {!AudioData} data
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-encode
 */
AudioEncoder.prototype.encode = function(data) {};

/**
 * @return {!Promise<undefined>}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-flush
 */
AudioEncoder.prototype.flush = function() {};

/**
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-reset
 */
AudioEncoder.prototype.reset = function() {};

/**
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-close
 */
AudioEncoder.prototype.close = function() {};


/**
 * @param {!EncodedAudioChunkInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#encodedaudiochunk-constructors
 */
function EncodedAudioChunk(init) {}

/**
 * @type {!EncodedAudioChunkType}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunk-type
 */
EncodedAudioChunk.prototype.type;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunk-timestamp
 */
EncodedAudioChunk.prototype.timestamp;

/**
 * @type {number|null}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunk-duration
 */
EncodedAudioChunk.prototype.duration;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunk-bytelength
 */
EncodedAudioChunk.prototype.byteLength;

/**
 * @param {!BufferSource} destination
 */
EncodedAudioChunk.prototype.copyTo = function(destination) {};


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-encodedaudiochunkinit
 */
function EncodedAudioChunkInit() {}

/**
 * @type {!EncodedAudioChunkType}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunkinit-type
 */
EncodedAudioChunkInit.prototype.type;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunkinit-timestamp
 */
EncodedAudioChunkInit.prototype.timestamp;

/**
 * @type {undefined|number}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunkinit-duration
 */
EncodedAudioChunkInit.prototype.duration;

/**
 * @type {!BufferSource}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunkinit-data
 */
EncodedAudioChunkInit.prototype.data;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-encodedaudiochunkmetadata
 */
function EncodedAudioChunkMetadata() {}

/**
 * @type {AudioDecoderConfig}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunkmetadata-decoderconfig
 */
EncodedAudioChunkMetadata.prototype.decoderConfig;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-audioencoderconfig
 */
function AudioEncoderConfig() {}

/**
 * @type {string}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderconfig-codec
 */
AudioEncoderConfig.prototype.codec;

/**
 * @type {(undefined|number)}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderconfig-samplerate
 */
AudioEncoderConfig.prototype.sampleRate;

/**
 * @type {(undefined|number)}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderconfig-numberofchannels
 */
AudioEncoderConfig.prototype.numberOfChannels;

/**
 * @type {(undefined|number)}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderconfig-bitrate
 */
AudioEncoderConfig.prototype.bitrate;

/**
 * @type {(undefined|!BitrateMode)}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderconfig-bitratemode
 */
AudioEncoderConfig.prototype.bitrateMode;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-audiodecoderconfig
 */
function AudioDecoderConfig() {}

/**
 * @type {string}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodecoderconfig-codec
 */
AudioDecoderConfig.prototype.codec;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodecoderconfig-samplerate
 */
AudioDecoderConfig.prototype.sampleRate;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodecoderconfig-numberofchannels
 */
AudioDecoderConfig.prototype.numberOfChannels;

/**
 * @type {BufferSource}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodecoderconfig-description
 */
AudioDecoderConfig.prototype.description;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-audioencodersupport
 */
function AudioEncoderSupport() {}

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencodersupport-supported
 */
AudioEncoderSupport.prototype.supported;

/**
 * @type {!AudioEncoderConfig}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencodersupport-config
 */
AudioEncoderSupport.prototype.config;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-audioencoderinit
 */
function AudioEncoderInit() {}

/**
 * @type {!EncodedAudioChunkOutputCallback}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderinit-output
 */
AudioEncoderInit.prototype.output;

/**
 * @type {!WebCodecsErrorCallback}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderinit-error
 */
AudioEncoderInit.prototype.error;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#callbackdef-encodedaudiochunkoutputcallback
 */
function EncodedAudioChunkOutputCallback() {}


/**
 * @param {!AudioDataInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#audiodata-constructors
 */
function AudioData(init) {}

/**
 * @type {?AudioSampleFormat}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-format
 */
AudioData.prototype.format;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-samplerate
 */
AudioData.prototype.sampleRate;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-numberofframes
 */
AudioData.prototype.numberOfFrames;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-numberofchannels
 */
AudioData.prototype.numberOfChannels;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-duration
 */
AudioData.prototype.duration;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-timestamp
 */
AudioData.prototype.timestamp;

/**
 * @param {!AudioDataCopyToOptions} options
 * @return {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-allocationsize
 */
AudioData.prototype.allocationSize = function(options) {};

/**
 * @param {!BufferSource} destination
 * @param {!AudioDataCopyToOptions} options
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-copyto
 */
AudioData.prototype.copyTo = function(destination, options) {};

/**
 * @return {!AudioData}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-clone
 */
AudioData.prototype.clone = function() {};

/**
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-close
 */
AudioData.prototype.close = function() {};


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-audiodatainit
 */
function AudioDataInit() {}

/**
 * @type {!AudioSampleFormat}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatainit-format
 */
AudioDataInit.prototype.format;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatainit-samplerate
 */
AudioDataInit.prototype.sampleRate;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatainit-numberofframes
 */
AudioDataInit.prototype.numberOfFrames;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatainit-numberofchannels
 */
AudioDataInit.prototype.numberOfChannels;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatainit-timestamp
 */
AudioDataInit.prototype.timestamp;

/**
 * @type {!BufferSource}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatainit-data
 */
AudioDataInit.prototype.data;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-audiodatacopytooptions
 */
function AudioDataCopyToOptions() {}

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatacopytooptions-planeindex
 */
AudioDataCopyToOptions.prototype.planeIndex;

/**
 * @type {undefined|number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatacopytooptions-frameoffset
 */
AudioDataCopyToOptions.prototype.frameOffset;

/**
 * @type {undefined|number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatacopytooptions-framecount
 */
AudioDataCopyToOptions.prototype.frameCount;

/**
 * @type {undefined|!AudioSampleFormat}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatacopytooptions-format
 */
AudioDataCopyToOptions.prototype.format;


/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-codecstate
 */
var CodecState;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-audiosampleformat
 */
var AudioSampleFormat;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/mediastream-recording/#enumdef-bitratemode
 */
var BitrateMode;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-encodedaudiochunktype
 */
var EncodedAudioChunkType;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-encodedvideochunktype
 */
var EncodedVideoChunkType;

/**
 * @param {!VideoEncoderInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#videoencoder-constructors
 */
function VideoEncoder(init) {}

/**
 * @type {!CodecState}
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-state
 */
VideoEncoder.prototype.state;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-encodequeuesize
 */
VideoEncoder.prototype.encodeQueueSize;

/**
 * @type {(function(!Event)|undefined)}
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-ondequeue
 */
VideoEncoder.prototype.ondequeue;

/**
 * @param {!VideoEncoderConfig} config
 * @return {!Promise<!VideoEncoderSupport>}
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-isconfigsupported
 */
VideoEncoder.isConfigSupported = function(config) {};

/**
 * @param {!VideoEncoderConfig} config
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-configure
 */
VideoEncoder.prototype.configure = function(config) {};

/**
 * @param {!VideoFrame} frame
 * @param {undefined|!VideoEncoderEncodeOptions} options
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-encode
 */
VideoEncoder.prototype.encode = function(frame, options) {};

/**
 * @return {!Promise<undefined>}
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-flush
 */
VideoEncoder.prototype.flush = function() {};

/**
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-reset
 */
VideoEncoder.prototype.reset = function() {};

/**
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-close
 */
VideoEncoder.prototype.close = function() {};


/**
 * @param {!EncodedVideoChunkInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#encodedvideochunk-constructors
 */
function EncodedVideoChunk(init) {}

/**
 * @type {!EncodedVideoChunkType}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedvideochunk-type
 */
EncodedVideoChunk.prototype.type;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedvideochunk-timestamp
 */
EncodedVideoChunk.prototype.timestamp;

/**
 * @type {number|null}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedvideochunk-duration
 */
EncodedVideoChunk.prototype.duration;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedvideochunk-bytelength
 */
EncodedVideoChunk.prototype.byteLength;

/**
 * @param {!BufferSource} destination
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedvideochunk-copyto
 */
EncodedVideoChunk.prototype.copyTo = function(destination) {};
