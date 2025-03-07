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
 * @param {!VideoColorSpaceInit|undefined} init
 * @see https://www.w3.org/TR/webcodecs/#videocolorspace
 */
function VideoColorSpace(init) {}

/**
 * @const {!VideoColorPrimaries|null}
 */
VideoColorSpace.prototype.primaries;

/**
 * @const {!VideoTransferCharacteristics|null}
 */
VideoColorSpace.prototype.transfer;

/**
 * @const {!VideoMatrixCoefficients|null}
 */
VideoColorSpace.prototype.matrix;

/**
 * @const {boolean|null}
 */
VideoColorSpace.prototype.fullRange;

/**
 * @override
 * @return {!VideoColorSpaceInit}
 */
VideoColorSpace.prototype.toJSON = function() {};

/**
 * @interface
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videocolorspaceinit
 */
function VideoColorSpaceInit() {}

/**
 * @type {!VideoColorPrimaries|undefined}
 */
VideoColorSpaceInit.prototype.primaries;

/**
 * @type {!VideoTransferCharacteristics|undefined}
 */
VideoColorSpaceInit.prototype.transfer;

/**
 * @type {!VideoMatrixCoefficients|undefined}
 */
VideoColorSpaceInit.prototype.matrix;

/**
 * @type {boolean|undefined}
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
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videoframeinit
 */
var VideoFrameInit;

/** @type {!AlphaOption|undefined} */
VideoFrameInit.prototype.alpha;

/** @type {number|undefined} */
VideoFrameInit.prototype.displayHeight;

/** @type {number|undefined} */
VideoFrameInit.prototype.displayWidth;

/** @type {number|undefined} */
VideoFrameInit.prototype.duration;

/** @type {number|undefined} */
VideoFrameInit.prototype.timestamp;

/** @type {!DOMRectInit|undefined} */
VideoFrameInit.prototype.visibleRect;

/** @type {!WebCodecsVideoFrameMetadata|undefined} */
VideoFrameInit.prototype.metadata;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-planelayout
 */
var PlaneLayout;

/** @type {number} */
PlaneLayout.prototype.offset;

/** @type {number} */
PlaneLayout.prototype.stride;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#pixel-format
 */
var VideoPixelFormat;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videoframeinit
 */
var VideoFrameBufferInit;

/** @type {number} */
VideoFrameBufferInit.prototype.codedHeight;

/** @type {number} */
VideoFrameBufferInit.prototype.codedWidth;

/** @type {!VideoColorSpaceInit|undefined} */
VideoFrameBufferInit.prototype.colorSpace;

/** @type {number|undefined} */
VideoFrameBufferInit.prototype.displayHeight;

/** @type {number|undefined} */
VideoFrameBufferInit.prototype.displayWidth;

/** @type {number|undefined} */
VideoFrameBufferInit.prototype.duration;

/** @type {string} */
VideoFrameBufferInit.prototype.format;

/** @type {!Array<!PlaneLayout>|undefined} */
VideoFrameBufferInit.prototype.layout;

/** @type {number} */
VideoFrameBufferInit.prototype.timestamp;

/** @type {!DOMRectInit|undefined} */
VideoFrameBufferInit.prototype.visibleRect;

/**
 * @param {!CanvasImageSource|!AllowSharedBufferSource} imageOrData
 * @param {!VideoFrameInit|!VideoFrameBufferInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#videoframe-constructors
 */
function VideoFrame(imageOrData, init) {}

/** @const {?VideoPixelFormat} */
VideoFrame.prototype.format;

/** @const {number} */
VideoFrame.prototype.codedWidth;

/** @const {number} */
VideoFrame.prototype.codedHeight;

/** @const {?DOMRectReadOnly} */
VideoFrame.prototype.codedRect;

/** @const {?DOMRectReadOnly} */
VideoFrame.prototype.visibleRect;

/** @const {number} */
VideoFrame.prototype.displayWidth;

/** @const {number} */
VideoFrame.prototype.displayHeight;

/** @const {?number} */
VideoFrame.prototype.duration;

/** @const {number} */
VideoFrame.prototype.timestamp;

/** @const {!VideoColorSpace} */
VideoFrame.prototype.colorSpace;

/**
 * @param {!VideoFrameCopyToOptions=} options
 * @return {number}
 */
VideoFrame.prototype.allocationSize = function(options) {};

/** @return {!VideoFrame} */
VideoFrame.prototype.clone = function() {};

/** */
VideoFrame.prototype.close = function() {};

/**
 * @param {!AllowSharedBufferSource} destination
 * @param {!VideoFrameCopyToOptions=} options
 * @return {!Promise<!Array<!PlaneLayout>>}
 */
VideoFrame.prototype.copyTo = function(destination, options) {};

/** @return {!WebCodecsVideoFrameMetadata} */
VideoFrame.prototype.metadata = function() {};

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videoframecopytooptions
 */
var VideoFrameCopyToOptions;

/** @type {!PredefinedColorSpace|undefined} */
VideoFrameCopyToOptions.prototype.colorSpace;

/** @type {!VideoPixelFormat|undefined} */
VideoFrameCopyToOptions.prototype.format;

/** @type {!Array<!PlaneLayout>|undefined} */
VideoFrameCopyToOptions.prototype.layout;

/** @type {!DOMRectInit|undefined} */
VideoFrameCopyToOptions.prototype.rect;

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

/** @type {!AvcBitstreamFormat|undefined} */
AvcEncoderConfig.prototype.format;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-encodedvideochunkinit
 */
function EncodedVideoChunkInit() {}

/** @type {!AllowSharedBufferSource} */
EncodedVideoChunkInit.prototype.data;

/** @type {number|undefined} */
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

/** @type {!VideoDecoderConfig|undefined} */
EncodedVideoChunkMetadata.prototype.decoderConfig;

/** @type {number|undefined} */
EncodedVideoChunkMetadata.prototype.temporalLayerId;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videodecoderconfig
 */
function VideoDecoderConfig() {}

/** @type {string} */
VideoDecoderConfig.prototype.codec;

/** @type {number|undefined} */
VideoDecoderConfig.prototype.codedHeight;

/** @type {number|undefined} */
VideoDecoderConfig.prototype.codedWidth;

/** @type {!VideoColorSpaceInit|undefined} */
VideoDecoderConfig.prototype.colorSpace;

/** @type {!AllowSharedBufferSource|undefined} */
VideoDecoderConfig.prototype.description;

/** @type {number|undefined} */
VideoDecoderConfig.prototype.displayAspectHeight;

/** @type {number|undefined} */
VideoDecoderConfig.prototype.displayAspectWidth;

/** @type {!HardwareAcceleration|undefined} */
VideoDecoderConfig.prototype.hardwareAcceleration;

/** @type {boolean|undefined} */
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

/** @type {!VideoDecoderConfig|undefined} */
VideoDecoderSupport.prototype.config;

/** @type {boolean|undefined} */
VideoDecoderSupport.prototype.supported;

/**
 * Available only in secure contexts.
 * @param {!VideoDecoderInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#videodecoder-constructors
 */
function VideoDecoder(init) {}

/**
 * @param {!VideoDecoderConfig} config
 * @return {!Promise<!VideoDecoderSupport>}
 * @see https://www.w3.org/TR/webcodecs/#dom-videodecoder-isconfigsupported
 */
VideoDecoder.isConfigSupported = function(config) {};

/** @const {number} */
VideoDecoder.prototype.decodeQueueSize;

/** @type {function(!Event)|undefined} */
VideoDecoder.prototype.ondequeue;

/** @const {!CodecState} */
VideoDecoder.prototype.state;

/** */
VideoDecoder.prototype.close = function() {};

/** @param {!VideoDecoderConfig} config */
VideoDecoder.prototype.configure = function(config) {};

/** @param {!EncodedVideoChunk} chunk */
VideoDecoder.prototype.decode = function(chunk) {};

/** @return {!Promise<undefined>} */
VideoDecoder.prototype.flush = function() {};

/** */
VideoDecoder.prototype.reset = function() {};

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-videoencoderconfig
 */
function VideoEncoderConfig() {}

/** @type {!AlphaOption|undefined} */
VideoEncoderConfig.prototype.alpha;

/** @type {!AvcEncoderConfig|undefined} */
VideoEncoderConfig.prototype.avc;

/** @type {number|undefined} */
VideoEncoderConfig.prototype.bitrate;

/** @type {!VideoEncoderBitrateMode|undefined} */
VideoEncoderConfig.prototype.bitrateMode;

/** @type {string} */
VideoEncoderConfig.prototype.codec;

/** @type {number|undefined} */
VideoEncoderConfig.prototype.displayHeight;

/** @type {number|undefined} */
VideoEncoderConfig.prototype.displayWidth;

/** @type {number|undefined} */
VideoEncoderConfig.prototype.framerate;

/** @type {!HardwareAcceleration|undefined} */
VideoEncoderConfig.prototype.hardwareAcceleration;

/** @type {number} */
VideoEncoderConfig.prototype.height;

/** @type {!LatencyMode|undefined} */
VideoEncoderConfig.prototype.latencyMode;

/** @type {string|undefined} */
VideoEncoderConfig.prototype.scalabilityMode;

/** @type {number} */
VideoEncoderConfig.prototype.width;


/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#video-encoder-options
 */
function VideoEncoderEncodeOptions() {}

/** @type {!VideoEncoderEncodeOptionsForAvc|undefined} */
VideoEncoderEncodeOptions.prototype.avc;

/** @type {boolean|null|undefined} */
VideoEncoderEncodeOptions.prototype.keyFrame;

/**
 * @record
 * @struct
 */
function VideoEncoderEncodeOptionsForAvc() {}

/** @type {number|null|undefined} */
VideoEncoderEncodeOptionsForAvc.prototype.quantizer;

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

/** @type {!VideoEncoderConfig|undefined} */
VideoEncoderSupport.prototype.config;

/** @type {boolean|undefined} */
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
 * Available only in secure contexts.
 * @param {!AudioEncoderInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#audioencoder-constructors
 */
function AudioEncoder(init) {}

/**
 * @const {!CodecState}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-state
 */
AudioEncoder.prototype.state;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoder-encodequeuesize
 */
AudioEncoder.prototype.encodeQueueSize;

/**
 * @type {function(!Event)|undefined}
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
 * @const {!EncodedAudioChunkType}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunk-type
 */
EncodedAudioChunk.prototype.type;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunk-timestamp
 */
EncodedAudioChunk.prototype.timestamp;

/**
 * @const {number|null}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunk-duration
 */
EncodedAudioChunk.prototype.duration;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunk-bytelength
 */
EncodedAudioChunk.prototype.byteLength;

/**
 * @param {!AllowSharedBufferSource} destination
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
 * @type {number|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunkinit-duration
 */
EncodedAudioChunkInit.prototype.duration;

/**
 * @type {!AllowSharedBufferSource}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunkinit-data
 */
EncodedAudioChunkInit.prototype.data;

/**
 * @type {!Array<!ArrayBuffer>|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedaudiochunkinit-transfer
 */
EncodedAudioChunkInit.prototype.transfer;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-encodedaudiochunkmetadata
 */
function EncodedAudioChunkMetadata() {}

/**
 * @type {!AudioDecoderConfig|undefined}
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
 * @type {number|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderconfig-samplerate
 */
AudioEncoderConfig.prototype.sampleRate;

/**
 * @type {number|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderconfig-numberofchannels
 */
AudioEncoderConfig.prototype.numberOfChannels;

/**
 * @type {number|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderconfig-bitrate
 */
AudioEncoderConfig.prototype.bitrate;

/**
 * @type {!BitrateMode|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencoderconfig-bitratemode
 */
AudioEncoderConfig.prototype.bitrateMode;

/** @type {!OpusEncoderConfig|undefined} */
AudioEncoderConfig.prototype.opus;

/**
 * @record
 * @struct
 */
function OpusEncoderConfig() {}

/** @type {number|undefined} */
OpusEncoderConfig.prototype.complexity;

/** @type {!OpusBitstreamFormat|undefined} */
OpusEncoderConfig.prototype.format;

/** @type {number|undefined} */
OpusEncoderConfig.prototype.frameDuration;

/** @type {number|undefined} */
OpusEncoderConfig.prototype.packetlossperc;

/** @type {boolean|undefined} */
OpusEncoderConfig.prototype.usedtx;

/** @type {boolean|undefined} */
OpusEncoderConfig.prototype.useinbandfec;

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
 * @type {!BufferSource|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodecoderconfig-description
 */
AudioDecoderConfig.prototype.description;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-audiodecoderinit
 */
function AudioDecoderInit() {}

/**
 * @type {!WebCodecsErrorCallback}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodecoderinit-error
 */
AudioDecoderInit.prototype.error;

/**
 * @type {!AudioDataOutputCallback}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodecoderinit-output
 */
AudioDecoderInit.prototype.output;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#callbackdef-audiodataoutputcallback
 */
function AudioDataOutputCallback() {}

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-audiodecodersupport
 */
function AudioDecoderSupport() {}

/**
 * @type {!AudioDecoderConfig|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodecodersupport-config
 */
AudioDecoderSupport.prototype.config;

/**
 * @type {boolean|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodecodersupport-supported
 */
AudioDecoderSupport.prototype.supported;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-audioencodersupport
 */
function AudioEncoderSupport() {}

/**
 * @type {boolean|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audioencodersupport-supported
 */
AudioEncoderSupport.prototype.supported;

/**
 * @type {!AudioEncoderConfig|undefined}
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
 * @const {?AudioSampleFormat}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-format
 */
AudioData.prototype.format;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-samplerate
 */
AudioData.prototype.sampleRate;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-numberofframes
 */
AudioData.prototype.numberOfFrames;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-numberofchannels
 */
AudioData.prototype.numberOfChannels;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodata-duration
 */
AudioData.prototype.duration;

/**
 * @const {number}
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
 * @param {!AllowSharedBufferSource} destination
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
 * @type {!Array<!ArrayBuffer>|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatainit-transfer
 */
AudioDataInit.prototype.transfer;

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
 * @type {number|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatacopytooptions-frameoffset
 */
AudioDataCopyToOptions.prototype.frameOffset;

/**
 * @type {number|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatacopytooptions-framecount
 */
AudioDataCopyToOptions.prototype.frameCount;

/**
 * @type {!AudioSampleFormat|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodatacopytooptions-format
 */
AudioDataCopyToOptions.prototype.format;

/**
 * Available only in secure contexts.
 * @param {!AudioDecoderInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#audiodecoder-interface
 */
function AudioDecoder(init) {}

/** @const {number} */
AudioDecoder.prototype.decodeQueueSize;

/** @type {function(!Event)|undefined} */
AudioDecoder.prototype.ondequeue;

/** @const {!CodecState} */
AudioDecoder.prototype.state;

/** */
AudioDecoder.prototype.close = function() {};

/**
 * @param {!AudioDecoderConfig} config
 */
AudioDecoder.prototype.configure = function(config) {};

/**
 * @param {!EncodedAudioChunk} chunk
 */
AudioDecoder.prototype.decode = function(chunk) {};

/**
 * @return {!Promise<undefined>}
 */
AudioDecoder.prototype.flush = function() {};

/** */
AudioDecoder.prototype.reset = function() {};

/**
 * @param {!AudioDecoderConfig} config
 * @return {!Promise<!AudioDecoderSupport>}
 * @see https://www.w3.org/TR/webcodecs/#dom-audiodecoder-isconfigsupported
 */
AudioDecoder.isConfigSupported = function(config) {};

/**
 * @typedef {string}
 */
var AvcBitstreamFormat;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-videoencoderbitratemode
 */
var VideoEncoderBitrateMode;

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
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-hardwareacceleration
 */
var HardwareAcceleration;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-latencymode
 */
var LatencyMode;

/**
 * @typedef {string}
 * @see https://html.spec.whatwg.org/multipage/canvas.html#predefinedcolorspace
 */
var PredefinedColorSpace;

/**
 * @typedef {string}
 * @see https://html.spec.whatwg.org/multipage/imagebitmap-and-animations.html#colorspaceconversion
 */
var ColorSpaceConversion;

/**
 * @typedef {string}
 */
var OpusBitstreamFormat;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-videomatrixcoefficients
 */
var VideoMatrixCoefficients;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-videocolorprimaries
 */
var VideoColorPrimaries;

/**
 * @typedef {string}
 * @see https://www.w3.org/TR/webcodecs/#enumdef-videotransfercharacteristics
 */
var VideoTransferCharacteristics;

/**
 * @typedef {ArrayBuffer|ArrayBufferView|ReadableStream}
 * @see https://www.w3.org/TR/webcodecs/#typedefdef-imagebuffersource
 */
var ImageBufferSource;

/**
 * Available only in secure contexts.
 * @param {!VideoEncoderInit} init
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#videoencoder-constructors
 */
function VideoEncoder(init) {}

/**
 * @const {!CodecState}
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-state
 */
VideoEncoder.prototype.state;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-videoencoder-encodequeuesize
 */
VideoEncoder.prototype.encodeQueueSize;

/**
 * @type {function(!Event)|undefined}
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
 * @param {!VideoEncoderEncodeOptions|undefined} options
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
 * @const {!EncodedVideoChunkType}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedvideochunk-type
 */
EncodedVideoChunk.prototype.type;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedvideochunk-timestamp
 */
EncodedVideoChunk.prototype.timestamp;

/**
 * @const {number|null}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedvideochunk-duration
 */
EncodedVideoChunk.prototype.duration;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedvideochunk-bytelength
 */
EncodedVideoChunk.prototype.byteLength;

/**
 * @param {!AllowSharedBufferSource} destination
 * @see https://www.w3.org/TR/webcodecs/#dom-encodedvideochunk-copyto
 */
EncodedVideoChunk.prototype.copyTo = function(destination) {};

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-imagedecodeoptions
 */
function ImageDecodeOptions() {}

/**
 * @type {boolean|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecodeoptions-completeframesonly
 */
ImageDecodeOptions.prototype.completeFramesOnly;

/**
 * @type {number|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecodeoptions-frameindex
 */
ImageDecodeOptions.prototype.frameIndex;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-imagedecoderesult
 */
function ImageDecodeResult() {}

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoderesult-complete
 */
ImageDecodeResult.prototype.complete;

/**
 * @type {!VideoFrame}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoderesult-image
 */
ImageDecodeResult.prototype.image;

/**
 * @record
 * @struct
 * @see https://www.w3.org/TR/webcodecs/#dictdef-imagedecoderinit
 */
function ImageDecoderInit() {}

/**
 * @type {string}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoderinit-type
 */
ImageDecoderInit.prototype.type;

/**
 * @type {!ImageBufferSource}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoderinit-data
 */
ImageDecoderInit.prototype.data;

/**
 * @type {!ColorSpaceConversion|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoderinit-colorspaceconversion
 */
ImageDecoderInit.prototype.colorSpaceConversion;

/**
 * @type {number|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoderinit-desiredwidth
 */
ImageDecoderInit.prototype.desiredWidth;

/**
 * @type {number|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoderinit-desiredheight
 */
ImageDecoderInit.prototype.desiredHeight;

/**
 * @type {boolean|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoderinit-preferanimation
 */
ImageDecoderInit.prototype.preferAnimation;

/**
 * @type {!Array<!ArrayBuffer>|undefined}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoderinit-transfer
 */
ImageDecoderInit.prototype.transfer;

/**
 * @constructor
 * @param {!ImageDecoderInit} init
 * @see https://www.w3.org/TR/webcodecs/#imagedecoder
 */
function ImageDecoder(init) {}

/**
 * @const {boolean}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoder-complete
 */
ImageDecoder.prototype.complete;

/**
 * @const {!Promise<undefined>}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoder-completed
 */
ImageDecoder.prototype.completed;

/**
 * @const {!ImageTrackList}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoder-tracks
 */
ImageDecoder.prototype.tracks;

/**
 * @const {string}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoder-type
 */
ImageDecoder.prototype.type;

/**
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoder-close
 */
ImageDecoder.prototype.close = function() {};

/**
 * @param {!ImageDecodeOptions=} options
 * @return {!Promise<!ImageDecodeResult>}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoder-decode
 */
ImageDecoder.prototype.decode = function(options) {};

/**
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoder-reset
 */
ImageDecoder.prototype.reset = function() {};

/**
 * @param {string} type
 * @return {!Promise<boolean>}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagedecoder-istypesupported
 */
ImageDecoder.isTypeSupported = function(type) {};

/**
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#imagetrack
 */
function ImageTrack() {}

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagetrack-animated
 */
ImageTrack.prototype.animated;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagetrack-framecount
 */
ImageTrack.prototype.frameCount;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagetrack-repetitionCount
 */
ImageTrack.prototype.repetitionCount;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagetrack-selected
 */
ImageTrack.prototype.selected;

/**
 * @constructor
 * @see https://www.w3.org/TR/webcodecs/#imagetracklist
 */
function ImageTrackList() {}

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagetracklist-length
 */
ImageTrackList.prototype.length;

/**
 * @type {!Promise<undefined>}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagetracklist-ready
 */
ImageTrackList.prototype.ready;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagetracklist-selectedindex
 */
ImageTrackList.prototype.selectedIndex;

/**
 * @type {!ImageTrack}
 * @see https://www.w3.org/TR/webcodecs/#dom-imagetracklist-selectedtrack
 */
ImageTrackList.prototype.selectedTrack;
