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
function AudioContext() {}

/** @type {!AudioDestinationNode} */
AudioContext.prototype.destination;

/** @type {number} */
AudioContext.prototype.sampleRate;

/** @type {number} */
AudioContext.prototype.currentTime;

/** @type {!AudioListener} */
AudioContext.prototype.listener;

/**
 * @param {number} numberOfChannels
 * @param {number} length
 * @param {number} sampleRate
 * @return {!AudioBuffer}
 */
AudioContext.prototype.createBuffer =
    function(numberOfChannels, length, sampleRate) {};

/**
 * @param {ArrayBuffer} audioData
 * @param {function(AudioBuffer)=} successCallback
 * @param {function(?)=} errorCallback
 * @return {!Promise<AudioBuffer>}
 */
AudioContext.prototype.decodeAudioData =
    function(audioData, successCallback, errorCallback) {};

/**
 * @return {!AudioBufferSourceNode}
 */
AudioContext.prototype.createBufferSource = function() {};

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
 * @return {!WaveShaperNode}
 */
AudioContext.prototype.createWaveShaper = function() {};

/**
 * @return {!AudioPannerNode}
 */
AudioContext.prototype.createPanner = function() {};

/**
 * @return {!StereoPannerNode}
 */
AudioContext.prototype.createStereoPanner = function() {};

/**
 * @return {!ConvolverNode}
 */
AudioContext.prototype.createConvolver = function() {};

/**
 * @param {number=} numberOfOutputs
 * @return {!AudioChannelSplitter}
 */
AudioContext.prototype.createChannelSplitter = function(numberOfOutputs) {};

/**
 * @param {number=} numberOfInputs
 * @return {!AudioChannelMerger}
 */
AudioContext.prototype.createChannelMerger = function(numberOfInputs) {};

/**
 * @return {!DynamicsCompressorNode}
 */
AudioContext.prototype.createDynamicsCompressor = function() {};

/**
 * @return {!OscillatorNode}
 */
AudioContext.prototype.createOscillator = function() {};

/**
 * @param {Float32Array} real
 * @param {Float32Array} imag
 * @return {!PeriodicWave}
 */
AudioContext.prototype.createPeriodicWave = function(real, imag) {};

/**
 * @param {number} numberOfChannels
 * @param {number} length
 * @param {number} sampleRate
 * @constructor
 * @extends {AudioContext}
 */
function OfflineAudioContext(numberOfChannels, length, sampleRate) {}

OfflineAudioContext.prototype.startRendering = function() {};

/** @type {function(OfflineAudioCompletionEvent)} */
OfflineAudioContext.prototype.oncomplete;

/**
 * @constructor
 * @extends {Event}
 */
function OfflineAudioCompletionEvent() {}

/** @type {AudioBuffer} */
OfflineAudioCompletionEvent.prototype.renderedBuffer;

/**
 * @constructor
 */
function AudioNode() {}

/**
 * @param {AudioNode|AudioParam} destination
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

/** @type {number} */
AudioNode.prototype.channelCount;

/** @type {string} */
AudioNode.prototype.channelCountMode;

/** @type {string} */
AudioNode.prototype.channelInterpretation;

/**
 * @constructor
 * @extends {AudioNode}
 */
function AudioSourceNode() {}

/**
 * @constructor
 * @extends {AudioNode}
 */
function AudioDestinationNode() {}

/**
 * To be deprecated. Use maxChannelCount instead.
 * @type {number}
 */
AudioDestinationNode.prototype.numberOfChannels;

/** @type {number} */
AudioDestinationNode.prototype.maxChannelCount;

/**
 * @constructor
 */
function AudioParam() {}

/** @type {number} */
AudioParam.prototype.value;

/**
 * To be deprecated.
 * @type {number}
 */
AudioParam.prototype.maxValue;

/**
 * To be deprecated.
 * @type {number}
 */
AudioParam.prototype.minValue;

/** @type {number} */
AudioParam.prototype.defaultValue;

/**
 * To be deprecated.
 * @type {number}
 */
AudioParam.prototype.units;

/**
 * @param {number} value
 * @param {number} startTime
 */
AudioParam.prototype.setValueAtTime = function(value, startTime) {};

/**
 * @param {number} value
 * @param {number} endTime
 */
AudioParam.prototype.linearRampToValueAtTime = function(value, endTime) {};

/**
 * @param {number} value
 * @param {number} endTime
 */
AudioParam.prototype.exponentialRampToValueAtTime = function(value, endTime) {};

/**
 * @param {number} target
 * @param {number} startTime
 * @param {number} timeConstant
 */
AudioParam.prototype.setTargetAtTime = function(target, startTime,
    timeConstant) {};

/**
 * @deprecated Use setTargetAtTime instead.
 * @param {number} target
 * @param {number} startTime
 * @param {number} timeConstant
 */
AudioParam.prototype.setTargetValueAtTime = function(target, startTime,
    timeConstant) {};

/**
 * @param {Float32Array} values
 * @param {number} startTime
 * @param {number} duration
 */
AudioParam.prototype.setValueCurveAtTime = function(values, startTime,
    duration) {};

/**
 * @param {number} startTime
 */
AudioParam.prototype.cancelScheduledValues = function(startTime) {};

/**
 * @constructor
 * @extends {AudioParam}
 */
function AudioGain() {}

/**
 * @constructor
 * @extends {AudioNode}
 */
function GainNode() {}

/** @type {AudioGain} */
GainNode.prototype.gain;

/**
 * @constructor
 * @extends {AudioNode}
 */
function DelayNode() {}

/** @type {AudioParam} */
DelayNode.prototype.delayTime;

/**
 * @constructor
 */
function AudioBuffer() {}

/**
 * To be deprecated.
 * @type {AudioGain}
 */
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
function AudioBufferSourceNode() {}

/**
 * To be deprecated.
 * @const
 * @type {number}
 */
AudioBufferSourceNode.prototype.UNSCHEDULED_STATE; /* = 0 */

/**
 * To be deprecated.
 * @const
 * @type {number}
 */
AudioBufferSourceNode.prototype.SCHEDULED_STATE; /* = 1 */

/**
 * To be deprecated.
 * @const
 * @type {number}
 */
AudioBufferSourceNode.prototype.PLAYING_STATE; /* = 2 */

/**
 * To be deprecated.
 * @const
 * @type {number}
 */
AudioBufferSourceNode.prototype.FINISHED_STATE; /* = 3 */

/**
 * To be deprecated.
 * @type {number}
 */
AudioBufferSourceNode.prototype.playbackState;

/** @type {AudioBuffer} */
AudioBufferSourceNode.prototype.buffer;

/**
 * To be deprecated.
 * @type {number}
 */
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
 * To be deprecated.
 * @param {number} when
 */
AudioBufferSourceNode.prototype.noteOn = function(when) {};

/**
 * To be deprecated.
 * @param {number} when
 * @param {number} grainOffset
 * @param {number} grainDuration
 */
AudioBufferSourceNode.prototype.noteGrainOn = function(when, grainOffset,
    grainDuration) {};

/**
 * To be deprecated.
 * @param {number} when
 */
AudioBufferSourceNode.prototype.noteOff = function(when) {};

/**
 * @constructor
 * @extends {AudioSourceNode}
 */
function MediaElementAudioSourceNode() {}

/**
 * To be deprecated. Use ScriptProcessorNode instead.
 * @constructor
 * @extends {AudioNode}
 */
function JavaScriptAudioNode() {}

/**
 * @type {EventListener|(function(!AudioProcessingEvent):(boolean|undefined))}
 */
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
function ScriptProcessorNode() {}

/**
 * @type {EventListener|(function(!AudioProcessingEvent):(boolean|undefined))}
 */
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
function AudioProcessingEvent() {}

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
function AudioPannerNode() {}

/**
 * To be deprecated. Use 'equalpower' instead.
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.EQUALPOWER = 0;

/**
 * To be deprecated. Use 'HRTF' instead.
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.HRTF = 1;

/**
 * To be deprecated.
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.SOUNDFIELD = 2;

/**
 * To be deprecated. Use 'linear' instead.
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.LINEAR_DISTANCE = 0;

/**
 * To be deprecated. Use 'inverse' instead.
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.INVERSE_DISTANCE = 1;

/**
 * To be deprecated. Use 'exponential' instead.
 * @const
 * @type {number}
 */
AudioPannerNode.prototype.EXPONENTIAL_DISTANCE = 2;

/** @type {number|string} */
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

/** @type {number|string} */
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

/**
 * To be deprecated.
 * @type {AudioGain}
 */
AudioPannerNode.prototype.coneGain;

/**
 * To be deprecated.
 * @type {AudioGain}
 */
AudioPannerNode.prototype.distanceGain;

/**
 * @constructor
 * @extends {AudioNode}
 * @see http://webaudio.github.io/web-audio-api/#the-stereopannernode-interface
 */
function StereoPannerNode() {}

/** @type {!AudioParam} */
StereoPannerNode.prototype.pan;

/**
 * @constructor
 */
function AudioListener() {}

/**
 * To be deprecated.
 * @type {number}
 */
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
function ConvolverNode() {}

/** @type {AudioBuffer} */
ConvolverNode.prototype.buffer;

/** @type {boolean} */
ConvolverNode.prototype.normalize;

/**
 * @constructor
 * @extends {AudioNode}
 */
function RealtimeAnalyserNode() {}

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
function AudioChannelSplitter() {}

/**
 * @constructor
 * @extends {AudioNode}
 */
function AudioChannelMerger() {}

/**
 * @constructor
 * @extends {AudioNode}
 */
function DynamicsCompressorNode() {}

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
function BiquadFilterNode() {}

/**
 * A read-able and write-able string that specifies the type of the filter.
 * See http://webaudio.github.io/web-audio-api/#the-biquadfilternode-interface
 * for valid values.
 * @type {string}
 */
BiquadFilterNode.prototype.type;

/** @type {!AudioParam} */
BiquadFilterNode.prototype.frequency;

/** @type {!AudioParam} */
BiquadFilterNode.prototype.detune;

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
 * @extends {AudioNode}
 */
function WaveShaperNode() {}

/** @type {Float32Array} */
WaveShaperNode.prototype.curve;

/** @type {string} */
WaveShaperNode.prototype.oversample;

/**
 * @constructor
 */
function WaveTable() {}

/**
 * @constructor
 */
function PeriodicWave() {}

/**
 * @constructor
 * @extends {AudioNode}
 */
function OscillatorNode() {}

/** @type {string} */
OscillatorNode.prototype.type;

/**
 * To be deprecated.
 * @type {number}
 */
OscillatorNode.prototype.playbackState;

/** @type {!AudioParam} */
OscillatorNode.prototype.frequency;

/** @type {!AudioParam} */
OscillatorNode.prototype.detune;

/** @type {function(number)} */
OscillatorNode.prototype.start;

/** @type {function(number)} */
OscillatorNode.prototype.stop;

/**
 * To be deprecated.
 * @type {function(WaveTable)}
 */
OscillatorNode.prototype.setWaveTable;

/** @type {function(PeriodicWave)} */
OscillatorNode.prototype.setPeriodicWave;

/** @type {EventListener} */
OscillatorNode.prototype.onended;

/**
 * @constructor
 * @extends {AudioSourceNode}
 */
function MediaStreamAudioSourceNode() {}

/**
 * @constructor
 * @extends {AudioDestinationNode}
 */
function MediaStreamAudioDestinationNode() {}

/**
 * @type {!MediaStream}
 * @const
 */
MediaStreamAudioDestinationNode.prototype.stream;

/**
 * Definitions for the Web Audio API with webkit prefix.
 */

/**
 * @constructor
 * @extends {AudioContext}
 */
function webkitAudioContext() {}

/**
 * @param {number} numberOfChannels
 * @param {number} length
 * @param {number} sampleRate
 * @constructor
 * @extends {OfflineAudioContext}
 */
function webkitOfflineAudioContext(numberOfChannels, length, sampleRate) {}

/**
 * @constructor
 * @extends {AudioPannerNode}
 */
function webkitAudioPannerNode() {}

/**
 * Definitions for the Audio API as implemented in Firefox.
 *   Please note that this document describes a non-standard experimental API.
 *   This API is considered deprecated.
 * @see https://developer.mozilla.org/en/DOM/HTMLAudioElement
 */

/**
 * @param {string=} src
 * @constructor
 * @extends {HTMLAudioElement}
 */
function Audio(src) {}

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
