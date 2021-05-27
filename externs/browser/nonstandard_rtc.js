/*
 * Copyright 2019 The Closure Compiler Authors
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
 * @fileoverview Nonstandard definitions for components of the WebRTC browser
 * API.
 *
 * @externs
 */

/**
 * @type {function(new: MediaStream,
 *                 (!MediaStream|!Array<!MediaStreamTrack>)=)}
 */
var webkitMediaStream;

/**
 * @param {MediaStreamConstraints} constraints A MediaStreamConstraints object.
 * @param {function(!MediaStream)} successCallback
 *     A NavigatorUserMediaSuccessCallback function.
 * @param {function(!NavigatorUserMediaError)=} errorCallback A
 *     NavigatorUserMediaErrorCallback function.
 * @see http://dev.w3.org/2011/webrtc/editor/getusermedia.html
 * @see https://www.w3.org/TR/mediacapture-streams/
 * @return {undefined}
 */
Navigator.prototype.webkitGetUserMedia = function(
    constraints, successCallback, errorCallback) {};

/** @const */
var webkitRTCPeerConnection = RTCPeerConnection;

/**
 * This is a stats object which was present in a previous iteration of the
 * standard, before some refactoring removed it and moved all fields to the
 * appropriate objects. It is still implemented by Chrome, and needed by
 * applications as some fields were only moved to their correct locations
 * relatively recently (M86).
 * TODO(b/154215269): And b/187442090; remove once users have switched to
 * reading stats only from the new standardized locations.
 * @see https://w3c.github.io/webrtc-stats/archives/20170614/webrtc-stats.html#idl-def-rtcmediastreamtrackstats
 * @interface
 * @extends {RTCStats}
 */
function RTCMediaStreamTrackStats() {}

/** @const {string} */
RTCMediaStreamTrackStats.prototype.trackIdentifier;

/** @const {string} */
RTCMediaStreamTrackStats.prototype.mediaSourceId;

/** @const {boolean} */
RTCMediaStreamTrackStats.prototype.remoteSource;

/** @const {boolean} */
RTCMediaStreamTrackStats.prototype.ended;

/** @const {boolean} */
RTCMediaStreamTrackStats.prototype.detached;

/** @const {string} */
RTCMediaStreamTrackStats.prototype.kind;

/** @const {?Date|number|undefined} */
RTCMediaStreamTrackStats.prototype.estimatedPlayoutTimestamp;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.frameWidth;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.frameHeight;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.framesPerSecond;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.framesCaptured;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.framesSent;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.framesReceived;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.framesDecoded;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.framesDropped;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.framesCorrupted;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.partialFramesLost;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.fullFramesLost;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.audioLevel;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.totalAudioEnergy;

/** @const {boolean|undefined} */
RTCMediaStreamTrackStats.prototype.voiceActivityFlag;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.echoReturnLoss;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.echoReturnLossEnhancement;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.totalSamplesSent;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.totalSamplesReceived;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.totalSamplesDuration;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.concealedSamples;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.silentConcealedSamples;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.concealmentEvents;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.insertedSamplesForDeceleration;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.removedSamplesForAcceleration;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.delayedPacketOutageSamples;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.jitterBufferDelay;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.jitterBufferEmittedCount;

/** @const {number|undefined} */
RTCMediaStreamTrackStats.prototype.jitterBufferFlushes;

/**
 * @type {string}
 * Set of possible string values: 'very-low', 'low', 'medium', 'high'.
 */
RTCMediaStreamTrackStats.prototype.priority;
