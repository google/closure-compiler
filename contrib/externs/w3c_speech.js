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
 * @fileoverview Definitions for W3C's Speech Input API draft (in progress).
 * http://www.w3.org/2005/Incubator/htmlspeech/2010/10/google-api-draft.html
 * This file contains only those functions/properties that are actively
 * used in the Voice Search experiment. Because the draft is under discussion
 * and constantly evolving, this file does not attempt to stay in sync with it.
 *
 * @externs
 */

// W3C Speech API
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

