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
 * @fileoverview Definitions for the API related to audio.
 * Definitions for the Web Audio API.
 * This file is based on the W3C Working Draft 15 March 2012.
 * @see http://www.w3.org/TR/webaudio/
 *
 * @externs
 */

/**
 * @constructor
 */
var AudioContext = function() {};

/** @type {!AudioDestinationNode} */
AudioContext.prototype.destination;

/** @type {number} */
AudioContext.prototype.sampleRate;

/** @type {number} */
AudioContext.prototype.currentTime;

/** @type {!AudioListener} */
AudioContext.prototype.listener;

/**
 * AudioContext.prototype.createBuffer() has 2 syntax:
 *   * Regular method:
 *     createBuffer = function(numberOfChannels, length, sampleRate) {};
 *
 *   * ArrayBuffer method:
 *     createBuffer = function(buffer, mixToMono) {};
 *
 * @param {number|ArrayBuffer} a
 * @param {number|boolean} b
 * @param {number=} sampleRate
 * @return {!AudioBuffer}
 */
AudioContext.prototype.createBuffer = function(a, b, sampleRate) {};

/**
 * @param {ArrayBuffer} audioData
 * @param {Function} successCallback
 * @param {Function=} errorCallback
 */
AudioContext.prototype.decodeAudioData = function(audioData, successCallback,
    errorCallback) {};

/**
 * @return {!AudioBufferSourceNode}
 */
AudioContext.prototype.createBufferSource = function() {};

/**
 * To be deprecated. Use createScriptProcessor instead.
 * @param {number} bufferSize
 * @param {number} numberOfInputs
 * @param {number} numberOfOuputs
 * @return {!ScriptProcessorNode}
 */
AudioContext.prototype.createJavaScriptNode = function(bufferSize,
    numberOfInputs, numberOfOuputs) {};

/**
 * @param {number} bufferSize
 * @param {number=} numberOfInputChannels_opt
 * @param {number=} numberOfOutputChannels_opt
 * @return {!ScriptProcessorNode}
 */
AudioContext.prototype.createScriptProcessor = function(bufferSize,
    numberOfInputChannels_opt, numberOfOutputChannels_opt) {};

/**
 * @return {!OscillatorNode}
 */
AudioContext.prototype.createOscillator = function() {};

/**
 * @return {!RealtimeAnalyserNode}
 */
AudioContext.prototype.createAnalyser = function() {};

/**
 * @deprecated Use createGain instead.
 * @return {!GainNode}
 */
AudioContext.prototype.createGainNode = function() {};

/**
 * @return {!GainNode}
 */
AudioContext.prototype.createGain = function() {};

/**
 * To be deprecated. Use createDelay instead.
 * @param {number=} maxDelayTime
 * @return {!DelayNode}
 */
AudioContext.prototype.createDelayNode = function(maxDelayTime) {};

/**
 * @param {number=} maxDelayTime
 * @return {!DelayNode}
 */
AudioContext.prototype.createDelay = function(maxDelayTime) {};

/**
 * @return {!BiquadFilterNode}
 */
AudioContext.prototype.createBiquadFilter = function() {};

/**
 * @return {!AudioPannerNode}
 */
AudioContext.prototype.createPanner = function() {};

/**
 * @return {!ConvolverNode}
 */
AudioContext.prototype.createConvolver = function() {};

/**
 * @return {!AudioChannelSplitter}
 */
AudioContext.prototype.createChannelSplitter = function() {};

/**
 * @return {!AudioChannelMerger}
 */
AudioContext.prototype.createChannelMerger = function() {};

/**
 * @return {!DynamicsCompressorNode}
 */
AudioContext.prototype.createDynamicsCompressor = function() {};

/**
 * @param {HTMLMediaElement} mediaElement
 * @return {!MediaElementAudioSourceNode}
 */
AudioContext.prototype.createMediaElementSource = function(mediaElement) {};

/**
 * @param {MediaStream} mediaStream
 * @return {!MediaStreamAudioSourceNode}
 */
AudioContext.prototype.createMediaStreamSource = function(mediaStream) {};

/**
 * @return {!MediaStreamAudioDestinationNode}
 */
AudioContext.prototype.createMediaStreamDestination = function() {};

/**
 * @param {number} numberOfChannels
 * @param {number} length
 * @param {number} sampleRate
 * @constructor
 * @extends {AudioContext}
 */
var OfflineAudioContext = function(numberOfChannels, length, sampleRate) {};

OfflineAudioContext.prototype.startRendering = function() {};

/** @type {function(OfflineAudioCompletionEvent)} */
OfflineAudioContext.prototype.oncomplete;

/**
 * @constructor
 * @extends {Event}
 */
var OfflineAudioCompletionEvent = function() {};

/** @type {AudioBuffer} */
OfflineAudioCompletionEvent.prototype.renderedBuffer;

/**
 * @constructor
 */
var AudioNode = function() {};

/**
 * @param {AudioNode} destination
 * @param {number=} output
 * @param {number=} input
 */
AudioNode.prototype.connect = function(destination, output, input) {};

/**
 * @param {number=} output
 */
AudioNode.prototype.disconnect = function(output) {};

/** @type {!AudioContext} */
AudioNode.prototype.context;

/** @type {number} */
AudioNode.prototype.numberOfInputs;

/** @type {number} */
AudioNode.prototype.numberOfOutputs;

/**
 * @constructor
 * @extends {AudioNode}
 */
var AudioSourceNode = function() {};

/**
 * @constructor
 * @extends {AudioNode}
 */
var AudioDestinationNode = function() {};

/** @type {number} */
AudioDestinationNode.prototype.numberOfChannels;

/**
 * @constructor
 * @extends {AudioDestinationNode}
 */
var MediaStreamAudioDestinationNode = function() {};

/**
 * @type {!MediaStream}
 * @const
 */
MediaStreamAudioDestinationNode.prototype.stream;

/**
 * @constructor
 */
var AudioParam = function() {};

/** @type {number} */
AudioParam.prototype.value;

/** @type {number} */
AudioParam.prototype.maxValue;

/** @type {number} */
AudioParam.prototype.minValue;

/** @type {number} */
AudioParam.prototype.defaultValue;

/** @type {number} */
AudioParam.prototype.units;

/**
 * @param {number} value
 * @param {number} time
 */
AudioParam.prototype.setValueAtTime = function(value, time) {};

/**
 * @param {number} value
 * @param {number} time
 */
AudioParam.prototype.linearRampToValueAtTime = function(value, time) {};

/**
 * @param {number} value
 * @param {number} time
 */
AudioParam.prototype.exponentialRampToValueAtTime = function(value, time) {};

/**
 * @param {number} targetValue
 * @param {number} time
 * @param {number} timeConstant
 */
AudioParam.prototype.setTargetValueAtTime = function(targetValue, time,
    timeConstant) {};

/**
 * @param {Float32Array} values
 * @param {number} time
 * @param {number} duration
 */
AudioParam.prototype.setValueCurveAtTime = function(values, time, duration) {};

/**
 * @param {number} startTime
 */
AudioParam.prototype.cancelScheduledValues = function(startTime) {};

/**
 * @constructor
 * @extends {AudioParam}
 */
var AudioGain = function() {};

/**
 * @constructor
 * @extends {AudioNode}
 */
var GainNode = function() {};

/** @type {AudioGain} */
GainNode.prototype.gain;

/**
 * @constructor
 * @extends {AudioNode}
 */
var DelayNode = function() {};

/** @type {AudioParam} */
DelayNode.prototype.delayTime;

/**
 * @constructor
 */
var AudioBuffer = function() {};

/** @type {AudioGain} */
AudioBuffer.prototype.gain;

/** @type {number} */
AudioBuffer.prototype.sampleRate;

/** @type {number} */
AudioBuffer.prototype.length;

/** @type {number} */
AudioBuffer.prototype.duration;

/** @type {number} */
AudioBuffer.prototype.numberOfChannels;

/**
 * @param {number} channel
 * @return {Float32Array}
 */
AudioBuffer.prototype.getChannelData = function(channel) {};

/**
 * @constructor
 * @extends {AudioSourceNode}
 */
var AudioBufferSourceNode = function() {};

/**
 * @const
 * @type {number}
 */
AudioBufferSourceNode.prototype.UNSCHEDULED_STATE; /* = 0 */

/**
 * @const
 * @type {number}
 */
AudioBufferSourceNode.prototype.SCHEDULED_STATE; /* = 1 */

/**
 * @const
 * @type {number}
 */
AudioBufferSourceNode.prototype.PLAYING_STATE; /* = 2 */

/**
 * @const
 * @type {number}
 */
AudioBufferSourceNode.prototype.FINISHED_STATE; /* = 3 */

/** @type {number} */
AudioBufferSourceNode.prototype.playbackState;

/** @type {AudioBuffer} */
AudioBufferSourceNode.prototype.buffer;

/** @type {number} */
AudioBufferSourceNode.prototype.gain;

/** @type {!AudioParam} */
AudioBufferSourceNode.prototype.playbackRate;

/** @type {boolean} */
AudioBufferSourceNode.prototype.loop;

/** @type {number} */
AudioBufferSourceNode.prototype.loopStart;

/** @type {number} */
AudioBufferSourceNode.prototype.loopEnd;

/**
 * @param {number} when
 * @param {number=} opt_offset
 * @param {number=} opt_duration
 */
AudioBufferSourceNode.prototype.start = function(when, opt_offset,
    opt_duration) {};

/**
 * @param {number} when
 */
AudioBufferSourceNode.prototype.stop = function(when) {};

/**
 * @param {number} when
 */
AudioBufferSourceNode.prototype.noteOn = function(when) {};

/**
 * @param {number} when
 * @param {number} grainOffset
 * @param {number} grainDuration
 */
AudioBufferSourceNode.prototype.noteGrainOn = function(when, grainOffset,
    grainDuration) {};

/**
 * @param {number} when
 */
AudioBufferSourceNode.prototype.noteOff = function(when) {};

/**
 * @constructor
 * @extends {AudioSourceNode}
 */
var MediaElementAudioSourceNode = function() {};

/**
 * @constructor
 * @extends {AudioSourceNode}
 */
var MediaStreamAudioSourceNode = function() {};

/**
 * @constructor
 * @extends {AudioNode}
 */
var JavaScriptAudioNode = function() {};

/** @type {EventListener} */
JavaScriptAudioNode.prototype.onaudioprocess;

/**
 * @const
 * @type {number}
 */
JavaScriptAudioNode.prototype.bufferSize;

/**
 * @constructor
 * @extends {AudioNode}
 */
var ScriptProcessorNode = function() {};

/** @type {EventListener} */
ScriptProcessorNode.prototype.onaudioprocess;

/**
 * @const
 * @type {number}
 */
ScriptProcessorNode.prototype.bufferSize;

/**
 * @constructor
 * @extends {Event}
 */
var AudioProcessingEvent = function() {};

/** @type {ScriptProcessorNode} */
AudioProcessingEvent.prototype.node;

/** @type {number} */
AudioProcessingEvent.prototype.playbackTime;

/** @type {AudioBuffer} */
AudioProcessingEvent.prototype.inputBuffer;

/** @type {AudioBuffer} */
AudioProcessingEvent.prototype.outputBuffer;

/**
 * @constructor
 * @extends {AudioNode}
 */
var AudioPannerNode = function() {};

/**
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.EQUALPOWER = 0;

/**
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.HRTF = 1;

/**
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.SOUNDFIELD = 2;

/**
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.LINEAR_DISTANCE = 0;

/**
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.INVERSE_DISTANCE = 1;

/**
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.EXPONENTIAL_DISTANCE = 2;

/** @type {number} */
AudioPannerNode.prototype.panningModel;

/**
 * @param {number} x
 * @param {number} y
 * @param {number} z
 */
AudioPannerNode.prototype.setPosition = function(x, y, z) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} z
 */
AudioPannerNode.prototype.setOrientation = function(x, y, z) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} z
 */
AudioPannerNode.prototype.setVelocity = function(x, y, z) {};

/** @type {number} */
AudioPannerNode.prototype.distanceModel;

/** @type {number} */
AudioPannerNode.prototype.refDistance;

/** @type {number} */
AudioPannerNode.prototype.maxDistance;

/** @type {number} */
AudioPannerNode.prototype.rolloffFactor;

/** @type {number} */
AudioPannerNode.prototype.coneInnerAngle;

/** @type {number} */
AudioPannerNode.prototype.coneOuterAngle;

/** @type {number} */
AudioPannerNode.prototype.coneOuterGain;

/** @type {AudioGain} */
AudioPannerNode.prototype.coneGain;

/** @type {AudioGain} */
AudioPannerNode.prototype.distanceGain;

/**
 * @constructor
 */
var AudioListener = function() {};

/** @type {number} */
AudioListener.prototype.gain;

/** @type {number} */
AudioListener.prototype.dopplerFactor;

/** @type {number} */
AudioListener.prototype.speedOfSound;

/**
 * @param {number} x
 * @param {number} y
 * @param {number} z
 */
AudioListener.prototype.setPosition = function(x, y, z) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} z
 * @param {number} xUp
 * @param {number} yUp
 * @param {number} zUp
 */
AudioListener.prototype.setOrientation = function(x, y, z, xUp, yUp, zUp) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} z
 */
AudioListener.prototype.setVelocity = function(x, y, z) {};

/**
 * @constructor
 * @extends {AudioNode}
 */
var ConvolverNode = function() {};

/** @type {AudioBuffer} */
ConvolverNode.prototype.buffer;

/** @type {boolean} */
ConvolverNode.prototype.normalize;

/**
 * @constructor
 * @extends {AudioNode}
 */
var RealtimeAnalyserNode = function() {};

/**
 * @param {Float32Array} array
 */
RealtimeAnalyserNode.prototype.getFloatFrequencyData = function(array) {};

/**
 * @param {Uint8Array} array
 */
RealtimeAnalyserNode.prototype.getByteFrequencyData = function(array) {};

/**
 * @param {Uint8Array} array
 */
RealtimeAnalyserNode.prototype.getByteTimeDomainData = function(array) {};

/** @type {number} */
RealtimeAnalyserNode.prototype.fftSize;

/** @type {number} */
RealtimeAnalyserNode.prototype.frequencyBinCount;

/** @type {number} */
RealtimeAnalyserNode.prototype.minDecibels;

/** @type {number} */
RealtimeAnalyserNode.prototype.maxDecibels;

/** @type {number} */
RealtimeAnalyserNode.prototype.smoothingTimeConstant;

/**
 * @constructor
 * @extends {AudioNode}
 */
var AudioChannelSplitter = function() {};

/**
 * @constructor
 * @extends {AudioNode}
 */
var AudioChannelMerger = function() {};

/**
 * @constructor
 * @extends {AudioNode}
 */
var DynamicsCompressorNode = function() {};

/** @type {!AudioParam} */
DynamicsCompressorNode.prototype.threshold;

/** @type {!AudioParam} */
DynamicsCompressorNode.prototype.knee;

/** @type {!AudioParam} */
DynamicsCompressorNode.prototype.ratio;

/** @type {!AudioParam} */
DynamicsCompressorNode.prototype.reduction;

/** @type {!AudioParam} */
DynamicsCompressorNode.prototype.attack;

/** @type {!AudioParam} */
DynamicsCompressorNode.prototype.release;

/**
 * @constructor
 * @extends {AudioNode}
 */
var BiquadFilterNode = function() {};

/**
 * @const
 * @type {number}
 */
BiquadFilterNode.prototype.LOWPASS = 0;

/**
 * @const
 * @type {number}
 */
BiquadFilterNode.prototype.HIGHPASS = 1;

/**
 * @const
 * @type {number}
 */
BiquadFilterNode.prototype.BANDPASS = 2;

/**
 * @const
 * @type {number}
 */
BiquadFilterNode.prototype.LOWSHELF = 3;

/**
 * @const
 * @type {number}
 */
BiquadFilterNode.prototype.HIGHSHELF = 4;

/**
 * @const
 * @type {number}
 */
BiquadFilterNode.prototype.PEAKING = 5;

/**
 * @const
 * @type {number}
 */
BiquadFilterNode.prototype.NOTCH = 6;

/**
 * @const
 * @type {number}
 */
BiquadFilterNode.prototype.ALLPASS = 7;

/** @type {number} */
BiquadFilterNode.prototype.type;

/** @type {!AudioParam} */
BiquadFilterNode.prototype.frequency;

/** @type {!AudioParam} */
BiquadFilterNode.prototype.Q;

/** @type {!AudioParam} */
BiquadFilterNode.prototype.gain;

/**
 * @param {Float32Array} frequencyHz
 * @param {Float32Array} magResponse
 * @param {Float32Array} phaseResponse
 */
BiquadFilterNode.prototype.getFrequencyResponse = function(frequencyHz,
    magResponse, phaseResponse) {};

/**
 * @constructor
 */
var WaveTable = function() {};

/**
 * @constructor
 * @extends {AudioNode}
 */
var OscillatorNode = function() {};

/** @type {string} */
OscillatorNode.prototype.type;

/** @type {number} */
OscillatorNode.prototype.playbackState;

/** @type {!AudioParam} */
OscillatorNode.prototype.frequency;

/** @type {!AudioParam} */
OscillatorNode.prototype.detune;

/** @type {function(number)} */
OscillatorNode.prototype.start;

/** @type {function(number)} */
OscillatorNode.prototype.stop;

/** @type {function(WaveTable)} */
OscillatorNode.prototype.setWaveTable;

/**
 * @constructor
 * @extends {AudioNode}
 */
var WaveShaperNode = function() {};

/** @type {Float32Array} */
WaveShaperNode.prototype.curve;

/**
 * Definitions for the Web Audio API with webkit prefix.
 */

/**
 * @constructor
 * @extends {AudioContext}
 */
var webkitAudioContext = function() {};

/**
 * @param {number} numberOfChannels
 * @param {number} length
 * @param {number} sampleRate
 * @constructor
 * @extends {OfflineAudioContext}
 */
var webkitOfflineAudioContext =
    function(numberOfChannels, length, sampleRate) {};

/**
 * @constructor
 * @extends {AudioPannerNode}
 */
var webkitAudioPannerNode = function() {};

/**
 * Definitions for the Audio API as implemented in Firefox.
 *   Please note that this document describes a non-standard experimental API.
 *   This API is considered deprecated.
 * @see https://developer.mozilla.org/en/DOM/HTMLAudioElement
 */

/**
 * @param {string=} src
 * @constructor
 */
var Audio = function(src) {};

/**
 * @param {number} channels
 * @param {number} rate
 */
Audio.prototype.mozSetup = function(channels, rate) {};

/**
 * @param {Array|Float32Array} buffer
 */
Audio.prototype.mozWriteAudio = function(buffer) {};

/**
 * @return {number}
 */
Audio.prototype.mozCurrentSampleOffset = function() {};
