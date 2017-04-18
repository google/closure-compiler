/*
 * Copyright 2017 The Closure Compiler Authors
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

/*
 * Ensure projects don't execute this file.
 * The throw is to catch executions of this file, however, without the guard,
 * the compiler's flow analysis stops at the throw, even for an externs file.
 * Therefore, the Math.random() guard fools the compiler during externs
 * processing.
 */
if (Math.random() < 1) {  // always true but the compiler doesn't know that
  throw 'Externs file "hls.js" should not be executed';
}


/**
 * @constructor
 * @return {!Hls}
 */
function Hls() {};

/**
 * @return {boolean}
 */
Hls.isSupported = function() {};

/**
 * @type {string}
 */
Hls.version;

/**
 * @type {Array}
 */
Hls.DefaultConfig;

/**
 * @return {undefined}
 */
Hls.prototype.destroy = function() {};

/**
 * @param {HTMLVideoElement} media
 * @return {undefined}
 */
Hls.prototype.attachMedia = function(media) {};

/**
 * @return {undefined}
 */
Hls.prototype.detachMedia = function() {};

/**
 * @param {string} url
 * @return {undefined}
 */
Hls.prototype.loadSource = function(url) {};

/**
 * @return {undefined}
 */
Hls.prototype.startLoad = function() {};

/**
 * @return {undefined}
 */
Hls.prototype.stopLoad = function() {};

/**
 * @return {undefined}
 */
Hls.prototype.swapAudioCodec = function() {};

/**
 * @return {undefined}
 */
Hls.prototype.recoverMediaError = function() {};

/**
 * @type {Array}
 */
Hls.prototype.levels;

/**
 * @type {number}
 */
Hls.prototype.currentLevel;

/**
 * @type {number}
 */
Hls.prototype.nextLevel;

/**
 * @type {number}
 */
Hls.prototype.loadLevel;

/**
 * @type {number}
 */
Hls.prototype.nextLoadLevel;

/**
 * @type {number}
 */
Hls.prototype.firstLevel;

/**
 * @type {number}
 */
Hls.prototype.startLevel;

/**
 * @type {number}
 */
Hls.prototype.autoLevelCapping;

/**
 * @type {boolean}
 */
Hls.prototype.autoLevelEnabled;

/**
 * @type {number}
 */
Hls.prototype.manualLevel;

/**
 * @type {number}
 */
Hls.prototype.minAutoLevel;

/**
 * @type {number}
 */
Hls.prototype.maxAutoLevel;

/**
 * @type {number}
 */
Hls.prototype.nextAutoLevel;

/**
 * @type {Array}
 */
Hls.prototype.audioTracks;

/**
 * @type {number}
 */
Hls.prototype.audioTrack;

/**
 * @type {number}
 */
Hls.prototype.liveSyncPosition;

/**
 * @type {Array}
 */
Hls.prototype.subtitleTracks;

/**
 * @type {number}
 */
Hls.prototype.subtitleTrack;

/**
 * @param {string} event
 * @param {function(Object=, Object=): void} callback
 * @return {undefined}
 */
Hls.prototype.on = function(event, callback) {};


/**
 * @constructor
 */
Hls.Events = function () {};

/** @const {string} */
Hls.Events.MEDIA_ATTACHING;

/** @const {string} */
Hls.Events.MEDIA_ATTACHED;

/** @const {string} */
Hls.Events.MEDIA_DETACHING;

/** @const {string} */
Hls.Events.MEDIA_DETACHED;

/** @const {string} */
Hls.Events.BUFFER_RESET;

/** @const {string} */
Hls.Events.BUFFER_CODECS;

/** @const {string} */
Hls.Events.BUFFER_CREATED;

/** @const {string} */
Hls.Events.BUFFER_APPENDING;

/** @const {string} */
Hls.Events.BUFFER_APPENDED;

/** @const {string} */
Hls.Events.BUFFER_EOS;

/** @const {string} */
Hls.Events.BUFFER_FLUSHING;

/** @const {string} */
Hls.Events.BUFFER_FLUSHED;

/** @const {string} */
Hls.Events.MANIFEST_LOADING;

/** @const {string} */
Hls.Events.MANIFEST_LOADED;

/** @const {string} */
Hls.Events.MANIFEST_PARSED;

/** @const {string} */
Hls.Events.LEVEL_SWITCH;

/** @const {string} */
Hls.Events.LEVEL_SWITCHING;

/** @const {string} */
Hls.Events.LEVEL_SWITCHED;

/** @const {string} */
Hls.Events.LEVEL_LOADING;

/** @const {string} */
Hls.Events.LEVEL_LOADED;

/** @const {string} */
Hls.Events.LEVEL_UPDATED;

/** @const {string} */
Hls.Events.LEVEL_PTS_UPDATED;

/** @const {string} */
Hls.Events.AUDIO_TRACKS_UPDATED;

/** @const {string} */
Hls.Events.AUDIO_TRACK_SWITCH;

/** @const {string} */
Hls.Events.AUDIO_TRACK_SWITCHING;

/** @const {string} */
Hls.Events.AUDIO_TRACK_SWITCHED;

/** @const {string} */
Hls.Events.AUDIO_TRACK_LOADING;

/** @const {string} */
Hls.Events.AUDIO_TRACK_LOADED;

/** @const {string} */
Hls.Events.SUBTITLE_TRACKS_UPDATED;

/** @const {string} */
Hls.Events.SUBTITLE_TRACK_SWITCH;

/** @const {string} */
Hls.Events.SUBTITLE_TRACK_LOADING;

/** @const {string} */
Hls.Events.SUBTITLE_TRACK_LOADED;

/** @const {string} */
Hls.Events.SUBTITLE_FRAG_PROCESSED;

/** @const {string} */
Hls.Events.INIT_PTS_FOUND;

/** @const {string} */
Hls.Events.FRAG_LOADING;

/** @const {string} */
Hls.Events.FRAG_LOAD_PROGRESS;

/** @const {string} */
Hls.Events.FRAG_LOAD_EMERGENCY_ABORTED;

/** @const {string} */
Hls.Events.FRAG_LOADED;

/** @const {string} */
Hls.Events.FRAG_DECRYPTED;

/** @const {string} */
Hls.Events.FRAG_PARSING_INIT_SEGMENT;

/** @const {string} */
Hls.Events.FRAG_PARSING_USERDATA;

/** @const {string} */
Hls.Events.FRAG_PARSING_METADATA;

/** @const {string} */
Hls.Events.FRAG_PARSING_DATA;

/** @const {string} */
Hls.Events.FRAG_PARSED;

/** @const {string} */
Hls.Events.FRAG_BUFFERED;

/** @const {string} */
Hls.Events.FRAG_CHANGED;

/** @const {string} */
Hls.Events.FPS_DROP;

/** @const {string} */
Hls.Events.FPS_DROP_LEVEL_CAPPING;

/** @const {string} */
Hls.Events.ERROR;

/** @const {string} */
Hls.Events.DESTROYING;

/** @const {string} */
Hls.Events.KEY_LOADING;

/** @const {string} */
Hls.Events.KEY_LOADED;

/** @const {string} */
Hls.Events.STREAM_STATE_TRANSITION;


/**
 * @constructor
 */
Hls.ErrorTypes = function () {};

/** @const {string} */
Hls.ErrorTypes.NETWORK_ERROR;

/** @const {string} */
Hls.ErrorTypes.MEDIA_ERROR;

/** @const {string} */
Hls.ErrorTypes.MUX_ERROR;

/** @const {string} */
Hls.ErrorTypes.OTHER_ERROR;


/**
 * @constructor
 */
Hls.ErrorDetails = function () {};

/** @const {string} */
Hls.ErrorDetails.MANIFEST_LOAD_ERROR;

/** @const {string} */
Hls.ErrorDetails.MANIFEST_LOAD_TIMEOUT;

/** @const {string} */
Hls.ErrorDetails.MANIFEST_PARSING_ERROR;

/** @const {string} */
Hls.ErrorDetails.MANIFEST_INCOMPATIBLE_CODECS_ERROR;

/** @const {string} */
Hls.ErrorDetails.LEVEL_LOAD_ERROR;

/** @const {string} */
Hls.ErrorDetails.LEVEL_LOAD_TIMEOUT;

/** @const {string} */
Hls.ErrorDetails.LEVEL_SWITCH_ERROR;

/** @const {string} */
Hls.ErrorDetails.AUDIO_TRACK_LOAD_ERROR;

/** @const {string} */
Hls.ErrorDetails.AUDIO_TRACK_LOAD_TIMEOUT;

/** @const {string} */
Hls.ErrorDetails.FRAG_LOAD_ERROR;

/** @const {string} */
Hls.ErrorDetails.FRAG_LOOP_LOADING_ERROR;

/** @const {string} */
Hls.ErrorDetails.FRAG_LOAD_TIMEOUT;

/** @const {string} */
Hls.ErrorDetails.FRAG_DECRYPT_ERROR;

/** @const {string} */
Hls.ErrorDetails.FRAG_PARSING_ERROR;

/** @const {string} */
Hls.ErrorDetails.REMUX_ALLOC_ERROR;

/** @const {string} */
Hls.ErrorDetails.KEY_LOAD_ERROR;

/** @const {string} */
Hls.ErrorDetails.KEY_LOAD_TIMEOUT;

/** @const {string} */
Hls.ErrorDetails.BUFFER_ADD_CODEC_ERROR;

/** @const {string} */
Hls.ErrorDetails.BUFFER_APPEND_ERROR;

/** @const {string} */
Hls.ErrorDetails.BUFFER_APPENDING_ERROR;

/** @const {string} */
Hls.ErrorDetails.BUFFER_STALLED_ERROR;

/** @const {string} */
Hls.ErrorDetails.BUFFER_FULL_ERROR;

/** @const {string} */
Hls.ErrorDetails.BUFFER_SEEK_OVER_HOLE;

/** @const {string} */
Hls.ErrorDetails.BUFFER_NUDGE_ON_STALL;

/** @const {string} */
Hls.ErrorDetails.INTERNAL_EXCEPTION;

/** @const {string} */
Hls.ErrorDetails.WEBVTT_EXCEPTION;


// TODO: define all variables and functions for the following classes:

/**
 * @constructor
 * @param {Hls} hls
 * @return {!AbrController}
 */
function AbrController(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!AudioStreamController}
 */
function AudioStreamController(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!AudioTrackController}
 */
function AudioTrackController(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!BufferController}
 */
function BufferController(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!CapLevelController}
 */
function CapLevelController(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!FPSController}
 */
function FPSController(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!LevelController}
 */
function LevelController(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!StreamController}
 */
function StreamController(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!SubtitleStreamController}
 */
function SubtitleStreamController(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!SubtitleTrackController}
 */
function SubtitleTrackController(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!TimelineController}
 */
function TimelineController(hls) {};

/**
 * @constructor
 * @param {Object} subtle
 * @param {Object} iv
 * @return {!AESCrypto}
 */
function AESCrypto(subtle, iv) {};

/**
 * @constructor
 * @return {!AESDecryptor}
 */
function AESDecryptor() {};

/**
 * @constructor
 * @param {Object} observer
 * @param {Object} config
 * @return {!Decrypter}
 */
function Decrypter(observer, config) {};

/**
 * @constructor
 * @param {Object} subtle
 * @param {Object} key
 * @return {!FastAESKey}
 */
function FastAESKey(subtle, key) {};

/**
 * @constructor
 * @param {Object} observer
 * @param {Object} remuxer
 * @param {Object} config
 * @return {!AACDemuxer}
 */
function AACDemuxer(observer, remuxer, config) {};

/**
 * @constructor
 * @param {Object} observer
 * @param {Object} typeSupported
 * @param {Object} config
 * @param {Object} vendor
 * @return {!DemuxerInline}
 */
function DemuxerInline(observer, typeSupported, config, vendor) {};

/**
 * @constructor
 * @param {Hls} hls
 * @param {Object} id
 * @return {!Demuxer}
 */
function Demuxer(hls, id) {};

/**
 * @constructor
 * @param {Object} data
 * @return {!ExpGolomb}
 */
function ExpGolomb(data) {};

/**
 * @constructor
 * @param {Object} data
 * @return {!ID3}
 */
function ID3(data) {};

/**
 * @constructor
 * @param {Object} observer
 * @param {Object} remuxer
 * @return {!MP4Demuxer}
 */
function MP4Demuxer(observer, remuxer) {};

/**
 * @constructor
 * @param {Object} observer
 * @param {Object} config
 * @param {Object} decryptdata
 * @param {Object} discardEPB
 * @return {!SampleAesDecrypter}
 */
function SampleAesDecrypter(observer, config, decryptdata, discardEPB) {};

/**
 * @constructor
 * @param {Object} observer
 * @param {Object} remuxer
 * @param {Object} config
 * @param {Object} typeSupported
 * @return {!TSDemuxer}
 */
function TSDemuxer(observer, remuxer, config, typeSupported) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!EventHandler}
 */
function EventHandler(hls) {};

/**
 * @constructor
 * @return {!AAC}
 */
function AAC() {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!FragmentLoader}
 */
function FragmentLoader(hls) {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!KeyLoader}
 */
function KeyLoader(hls) {};

/**
 * @constructor
 * @return {!LevelKey}
 */
function LevelKey() {};

/**
 * @constructor
 * @return {!Fragment}
 */
function Fragment() {};

/**
 * @constructor
 * @param {Hls} hls
 * @return {!PlaylistLoader}
 */
function PlaylistLoader(hls) {};

/**
 * @constructor
 * @return {!MP4}
 */
function MP4() {};

/**
 * @constructor
 * @param {Object} observer
 * @param {Object} config
 * @param {Object} typeSupported
 * @param {Object} vendor
 * @return {!MP4Remuxer}
 */
function MP4Remuxer(observer, config, typeSupported, vendor) {};

/**
 * @constructor
 * @param {Object} observer
 * @return {!PassThroughRemuxer}
 */
function PassThroughRemuxer(observer) {};

/**
 * @constructor
 * @param {(string|Array)} attrs
 * @return {!AttrList}
 */
function AttrList(attrs) {};

/**
 * @constructor
 * @param {Object} foreground
 * @param {Object} underline
 * @param {Object} italics
 * @param {Object} background
 * @param {Object} flash
 * @return {!PenState}
 */
function PenState(foreground, underline, italics, background, flash) {};

/**
 * @constructor
 * @param {Object} uchar
 * @param {Object} foreground
 * @param {Object} underline
 * @param {Object} italics
 * @param {Object} background
 * @param {Object} flash
 * @return {!StyledUnicodeChar}
 */
function StyledUnicodeChar(uchar, foreground, underline, italics, background, flash) {};

/**
 * @constructor
 * @return {!Row}
 */
function Row() {};

/**
 * @constructor
 * @return {!CaptionScreen}
 */
function CaptionScreen() {};

/**
 * @constructor
 * @param {Object} channelNumber
 * @param {Object} outputFilter
 * @return {!Cea608Channel}
 */
function Cea608Channel(channelNumber, outputFilter) {};

/**
 * @constructor
 * @param {Object} field
 * @param {Object} out1
 * @param {Object} out2
 * @return {!Cea608Parser}
 */
function Cea608Parser(field, out1, out2) {};

/**
 * @constructor
 * @param {Hls} hls
 * @param {Object} slow
 * @param {Object} fast
 * @param {Object} defaultEstimate
 * @return {!EwmaBandWidthEstimator}
 */
function EwmaBandWidthEstimator(hls, slow, fast, defaultEstimate) {};

/**
 * @constructor
 * @param {Object} halfLife
 * @return {!EWMA}
 */
function EWMA(halfLife) {};

/**
 * @constructor
 * @param {Object} config
 * @return {!XhrLoader}
 */
function XhrLoader(config) {};
