/*
 * Copyright 2011 Google Inc.
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
 * @fileoverview Definitions for W3C's Speech Input 2010 draft API and the
 * 2012 Web Speech draft API (in progress).
 * 2010 Speech Input API:
 * http://www.w3.org/2005/Incubator/htmlspeech/2010/10/google-api-draft.html
 * 2012 Web Speech API:
 * http://dvcs.w3.org/hg/speech-api/raw-file/tip/speechapi.html
 * This file contains only those functions/properties that are actively
 * used in the Voice Search experiment. Because the draft is under discussion
 * and constantly evolving, this file does not attempt to stay in sync with it.
 *
 * @externs
 */

// W3C Speech Input API implemented in Chrome M12
/**
 * @constructor
 * @extends {UIEvent}
 */
function SpeechInputEvent() {}

/** @type {SpeechInputResultList} */
SpeechInputEvent.prototype.results;


/**
 * @constructor
 */
function SpeechInputResultList() {}

/** @type {number} */
SpeechInputResultList.prototype.length;


/**
 * @constructor
 */
function SpeechInputResult() {}

/** @type {string} */
SpeechInputResult.prototype.utterance;

/** @type {number} */
SpeechInputResult.prototype.confidence;


// HTMLInputElement
/** @type {boolean} */
HTMLInputElement.prototype.webkitspeech;

/** @type {?function (Event)} */
HTMLInputElement.prototype.onwebkitspeechchange;



// W3C Web Speech API implemented in Chrome M23
/**
 * @constructor
 */
function webkitSpeechGrammar() {}

/** @type {string} */
webkitSpeechGrammar.prototype.src;

/** @type {number} */
webkitSpeechGrammar.prototype.weight;


/**
 * @constructor
 */
function webkitSpeechGrammarList() {}

/** @type {number} */
webkitSpeechGrammarList.prototype.length;


/**
 * @constructor
 */
function SpeechRecognitionAlternative() {}

/** @type {string} */
SpeechRecognitionAlternative.prototype.transcript;

/** @type {number} */
SpeechRecognitionAlternative.prototype.confidence;


/**
 * @constructor
 */
function SpeechRecognitionResult() {}

/** @type {boolean} */
SpeechRecognitionResult.prototype.isFinal;

/** @type {number} */
SpeechRecognitionResult.prototype.length;

/** @type {Document} */
SpeechRecognitionResult.prototype.emma;

/** @type {function(number): SpeechRecognitionAlternative} */
SpeechRecognitionResult.prototype.item = function(index) {};


/**
 * @constructor
 */
function SpeechRecognitionResultList() {}

/** @type {number} */
SpeechRecognitionResultList.prototype.length;

/** @type {function(number): SpeechRecognitionResult} */
SpeechRecognitionResultList.prototype.item = function(index) {};


/**
 * @constructor
 * @extends {Event}
 */
function webkitSpeechRecognitionEvent() {}

/** @type {SpeechRecognitionResult} */
webkitSpeechRecognitionEvent.prototype.results;

/** @type {number} */
webkitSpeechRecognitionEvent.prototype.resultIndex;

/** @type {SpeechRecognitionResultList} */
webkitSpeechRecognitionEvent.prototype.resultHistory;


/**
 * @constructor
 */
function webkitSpeechRecognitionError() {}

/** @type {number} */
webkitSpeechRecognitionError.prototype.code;

/** @type {number} */
webkitSpeechRecognitionError.prototype.OTHER;

/** @type {number} */
webkitSpeechRecognitionError.prototype.NO_SPEECH;

/** @type {number} */
webkitSpeechRecognitionError.prototype.ABORTED;

/** @type {number} */
webkitSpeechRecognitionError.prototype.AUDIO_CAPTURE;

/** @type {number} */
webkitSpeechRecognitionError.prototype.NETWORK;

/** @type {number} */
webkitSpeechRecognitionError.prototype.NOT_ALLOWED;

/** @type {number} */
webkitSpeechRecognitionError.prototype.SERVICE_NOT_ALLOWED;

/** @type {number} */
webkitSpeechRecognitionError.prototype.BAD_GRAMMAR;

/** @type {number} */
webkitSpeechRecognitionError.prototype.LANGUAGE_NOT_SUPPORTED;

/** @type {string} */
webkitSpeechRecognitionError.prototype.message;


/**
 * @constructor
 */
function webkitSpeechRecognition() {}

/** @type {webkitSpeechGrammarList} */
webkitSpeechRecognition.prototype.grammars;

/** @type {string} */
webkitSpeechRecognition.prototype.lang;

/** @type {boolean} */
webkitSpeechRecognition.prototype.continuous;

/** @type {boolean} */
webkitSpeechRecognition.prototype.interimResults;

/** @type {number} */
webkitSpeechRecognition.prototype.maxAlternatives;

/** @type {function(): undefined} */
webkitSpeechRecognition.prototype.start;

/** @type {function(): undefined} */
webkitSpeechRecognition.prototype.stop;

/** @type {function(): undefined} */
webkitSpeechRecognition.prototype.abort;

/** @type {function(Event): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onaudiostart;

/** @type {function(Event): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onsoundstart;

/** @type {function(Event): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onspeechstart;

/** @type {function(Event): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onaudioend;

/** @type {function(Event): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onsoundend;

/** @type {function(Event): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onspeechend;

/** @type {function(Event): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onend;

/** @type {function(Event): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onstart;

/** @type {function(webkitSpeechRecognitionEvent): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onnomatch;

/** @type {function(webkitSpeechRecognitionEvent): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onresult;

/** @type {function(webkitSpeechRecognitionError): (boolean|undefined)} */
webkitSpeechRecognition.prototype.onerror;

