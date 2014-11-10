/*
 * Copyright 2012 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's Web Intents specification.
 *
 * Created from
 * http://dvcs.w3.org/hg/web-intents/raw-file/tip/spec/Overview.html
 * and
 * http://srcs.cc/w/Source/WebCore/Modules/intents/DOMWindowIntents.idl
 * http://srcs.cc/w/Source/WebCore/Modules/intents/DeliveredIntent.idl
 * http://srcs.cc/w/Source/WebCore/Modules/intents/Intent.idl
 * http://srcs.cc/w/Source/WebCore/Modules/intents/IntentResultCallback.idl
 * http://srcs.cc/w/Source/WebCore/Modules/intents/NavigatorIntents.idl
 * http://srcs.cc/w/Source/WebCore/html/HTMLIntentElement.idl
 *
 * @externs
 */

/**
 * @param {string} action
 * @param {string} type
 * @param {*=} opt_data
 * @param {Array.<Transferable>=} opt_transferList
 * @constructor
 */
function Intent(action, type, opt_data, opt_transferList) {}

/** @type {string} */
Intent.prototype.action;

/** @type {string} */
Intent.prototype.type;

/** @type {*} */
Intent.prototype.data;

/**
 * @constructor
 * @extends {Intent}
 */
function DeliveredIntent() {}

/** @type {Array.<MessagePort>} */
DeliveredIntent.prototype.ports;

/**
 * @param {string} key
 * @return {string}
 * @nosideeffects
 */
DeliveredIntent.prototype.getExtra = function(key) {};

/**
 * @param {*} result
 */
DeliveredIntent.prototype.postResult = function(result) {};

/**
 * @param {*} result
 */
DeliveredIntent.prototype.postFailure = function(result) {};

/** @type {DeliveredIntent} */
Window.prototype.intent;

/**
 * @param {Intent} intent
 * @param {function(*) : void=} opt_successCallback
 * @param {function(*) : void=} opt_failureCallback
 */
Navigator.prototype.startActivity =
    function(intent, opt_successCallback, opt_failureCallback) {};

/**
 * @constructor
 * @extends {HTMLElement}
 */
function HTMLIntentElement() {}

/**
 * @type {string}
 */
HTMLIntentElement.prototype.action;

/**
 * @type {string}
 */
HTMLIntentElement.prototype.type;

/**
 * @type {string}
 */
HTMLIntentElement.prototype.href;

/**
 * @type {string}
 */
HTMLIntentElement.prototype.title;

/**
 * @type {string}
 */
HTMLIntentElement.prototype.disposition;

// Variants of the above for the implementation inside WebKit, which is
// vendor-prefixed.

/** @type {DeliveredIntent} */
Window.prototype.webkitIntent;

/**
 * @param {string} action
 * @param {string} type
 * @param {*=} opt_data
 * @param {Array.<Transferable>=} opt_transferList
 * @constructor
 * @extends {Intent}
 */
function WebKitIntent(action, type, opt_data, opt_transferList) {}

/**
 * @param {WebKitIntent} intent
 * @param {function(*) : void=} opt_successCallback
 * @param {function(*) : void=} opt_failureCallback
 */
Navigator.prototype.webkitStartActivity =
    function(intent, opt_successCallback, opt_failureCallback) {};