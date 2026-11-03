/*
 * Copyright 2026 The Closure Compiler Authors
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
 * @fileoverview The current draft spec of the Translator and Language Detector
 * APIs, plus the shared infrastructure they use from the Writing Assistance
 * APIs.
 * @see https://webmachinelearning.github.io/translation-api/
 * @see https://webmachinelearning.github.io/writing-assistance-apis/#supporting
 * @externs
 */

/**
 * @typedef {string}
 * Valid values: 'unavailable', 'downloadable', 'downloading', 'available'.
 * @see https://webmachinelearning.github.io/writing-assistance-apis/#enumdef-availability
 */
var Availability;

/**
 * Fires "downloadprogress" events, which are ProgressEvents.
 * @constructor
 * @implements {EventTarget}
 * @see https://webmachinelearning.github.io/writing-assistance-apis/#createmonitor
 */
function CreateMonitor() {}

/** @override */
CreateMonitor.prototype.addEventListener = function(
    type, listener, opt_options) {};

/** @override */
CreateMonitor.prototype.removeEventListener = function(
    type, listener, opt_options) {};

/** @override */
CreateMonitor.prototype.dispatchEvent = function(evt) {};

/** @type {?function(!ProgressEvent)} */
CreateMonitor.prototype.ondownloadprogress;

/**
 * @typedef {function(!CreateMonitor): void}
 * @see https://webmachinelearning.github.io/writing-assistance-apis/#callbackdef-createmonitorcallback
 */
var CreateMonitorCallback;


/**
 * @record
 * @see https://webmachinelearning.github.io/translation-api/#dictdef-translatorcreatecoreoptions
 */
function TranslatorCreateCoreOptions() {}

/** @type {string} */
TranslatorCreateCoreOptions.prototype.sourceLanguage;

/** @type {string} */
TranslatorCreateCoreOptions.prototype.targetLanguage;

/**
 * @record
 * @extends {TranslatorCreateCoreOptions}
 * @see https://webmachinelearning.github.io/translation-api/#dictdef-translatorcreateoptions
 */
function TranslatorCreateOptions() {}

/** @type {!AbortSignal|undefined} */
TranslatorCreateOptions.prototype.signal;

/** @type {!CreateMonitorCallback|undefined} */
TranslatorCreateOptions.prototype.monitor;

/**
 * @record
 * @see https://webmachinelearning.github.io/translation-api/#dictdef-translatortranslateoptions
 */
function TranslatorTranslateOptions() {}

/** @type {!AbortSignal|undefined} */
TranslatorTranslateOptions.prototype.signal;

/**
 * @constructor
 * @see https://webmachinelearning.github.io/translation-api/#translator
 */
function Translator() {}

/**
 * @param {!TranslatorCreateOptions} options
 * @return {!Promise<!Translator>}
 * @see https://webmachinelearning.github.io/translation-api/#dom-translator-create
 */
Translator.create = function(options) {};

/**
 * @param {!TranslatorCreateCoreOptions} options
 * @return {!Promise<!Availability>}
 * @see https://webmachinelearning.github.io/translation-api/#dom-translator-availability
 */
Translator.availability = function(options) {};

/**
 * @param {string} input
 * @param {!TranslatorTranslateOptions=} options
 * @return {!Promise<string>}
 * @see https://webmachinelearning.github.io/translation-api/#dom-translator-translate
 */
Translator.prototype.translate = function(input, options) {};

/**
 * @param {string} input
 * @param {!TranslatorTranslateOptions=} options
 * @return {!ReadableStream<string>}
 * @see https://webmachinelearning.github.io/translation-api/#dom-translator-translatestreaming
 */
Translator.prototype.translateStreaming = function(input, options) {};

/**
 * @const {string}
 * @see https://webmachinelearning.github.io/translation-api/#dom-translator-sourcelanguage
 */
Translator.prototype.sourceLanguage;

/**
 * @const {string}
 * @see https://webmachinelearning.github.io/translation-api/#dom-translator-targetlanguage
 */
Translator.prototype.targetLanguage;

/**
 * @param {string} input
 * @param {!TranslatorTranslateOptions=} options
 * @return {!Promise<number>}
 * @see https://webmachinelearning.github.io/translation-api/#dom-translator-measureinputusage
 */
Translator.prototype.measureInputUsage = function(input, options) {};

/**
 * @const {number}
 * @see https://webmachinelearning.github.io/translation-api/#dom-translator-inputquota
 */
Translator.prototype.inputQuota;

/**
 * @return {void}
 * @see https://webmachinelearning.github.io/writing-assistance-apis/#dom-destroyablemodel-destroy
 */
Translator.prototype.destroy = function() {};


/**
 * @record
 * @see https://webmachinelearning.github.io/translation-api/#dictdef-languagedetectorcreatecoreoptions
 */
function LanguageDetectorCreateCoreOptions() {}

/** @type {!Array<string>|undefined} */
LanguageDetectorCreateCoreOptions.prototype.expectedInputLanguages;

/**
 * @record
 * @extends {LanguageDetectorCreateCoreOptions}
 * @see https://webmachinelearning.github.io/translation-api/#dictdef-languagedetectorcreateoptions
 */
function LanguageDetectorCreateOptions() {}

/** @type {!AbortSignal|undefined} */
LanguageDetectorCreateOptions.prototype.signal;

/** @type {!CreateMonitorCallback|undefined} */
LanguageDetectorCreateOptions.prototype.monitor;

/**
 * @record
 * @see https://webmachinelearning.github.io/translation-api/#dictdef-languagedetectordetectoptions
 */
function LanguageDetectorDetectOptions() {}

/** @type {!AbortSignal|undefined} */
LanguageDetectorDetectOptions.prototype.signal;

/**
 * @record
 * @see https://webmachinelearning.github.io/translation-api/#dictdef-languagedetectionresult
 */
function LanguageDetectionResult() {}

/** @type {string|undefined} */
LanguageDetectionResult.prototype.detectedLanguage;

/** @type {number|undefined} */
LanguageDetectionResult.prototype.confidence;

/**
 * @constructor
 * @see https://webmachinelearning.github.io/translation-api/#languagedetector
 */
function LanguageDetector() {}

/**
 * @param {!LanguageDetectorCreateOptions=} options
 * @return {!Promise<!LanguageDetector>}
 * @see https://webmachinelearning.github.io/translation-api/#dom-languagedetector-create
 */
LanguageDetector.create = function(options) {};

/**
 * @param {!LanguageDetectorCreateCoreOptions=} options
 * @return {!Promise<!Availability>}
 * @see https://webmachinelearning.github.io/translation-api/#dom-languagedetector-availability
 */
LanguageDetector.availability = function(options) {};

/**
 * @param {string} input
 * @param {!LanguageDetectorDetectOptions=} options
 * @return {!Promise<!Array<!LanguageDetectionResult>>}
 * @see https://webmachinelearning.github.io/translation-api/#dom-languagedetector-detect
 */
LanguageDetector.prototype.detect = function(input, options) {};

/**
 * @const {?Array<string>}
 * @see https://webmachinelearning.github.io/translation-api/#dom-languagedetector-expectedinputlanguages
 */
LanguageDetector.prototype.expectedInputLanguages;

/**
 * @param {string} input
 * @param {!LanguageDetectorDetectOptions=} options
 * @return {!Promise<number>}
 * @see https://webmachinelearning.github.io/translation-api/#dom-languagedetector-measureinputusage
 */
LanguageDetector.prototype.measureInputUsage = function(input, options) {};

/**
 * @const {number}
 * @see https://webmachinelearning.github.io/translation-api/#dom-languagedetector-inputquota
 */
LanguageDetector.prototype.inputQuota;

/**
 * @return {void}
 * @see https://webmachinelearning.github.io/writing-assistance-apis/#dom-destroyablemodel-destroy
 */
LanguageDetector.prototype.destroy = function() {};
