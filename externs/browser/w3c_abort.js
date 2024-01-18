/*
 * Copyright 2018 The Closure Compiler Authors
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
 * @fileoverview Definitions for AbortController
 * @see https://dom.spec.whatwg.org/#aborting-ongoing-activities
 * @externs
 */



/**
 * @constructor
 * @implements {EventTarget}
 * @see https://dom.spec.whatwg.org/#interface-AbortSignal
 */
function AbortSignal() {}

/** @override */
AbortSignal.prototype.addEventListener = function(
    type, listener, opt_options) {};

/** @override */
AbortSignal.prototype.removeEventListener = function(
    type, listener, opt_options) {};

/** @override */
AbortSignal.prototype.dispatchEvent = function(evt) {};

/**
 * @param {*=} reason
 * @return {!AbortSignal}
 * @see https://dom.spec.whatwg.org/#dom-abortsignal-abort
 */
AbortSignal.abort = function(reason) {};

/**
 * @param {number} milliseconds
 * @return {!AbortSignal}
 * @see https://dom.spec.whatwg.org/#dom-abortsignal-timeout
 */
AbortSignal.timeout = function(milliseconds) {};

/**
 * @param {!Iterable} signals
 * @return {!AbortSignal}
 * @see https://dom.spec.whatwg.org/#dom-abortsignal-any
 */
AbortSignal.any = function(signals) {};

/** @type {boolean} */
AbortSignal.prototype.aborted;

/** @type {?function(!Event)} */
AbortSignal.prototype.onabort;

/** @type {*} */
AbortSignal.prototype.reason;

/** @return {void} */
AbortSignal.prototype.throwIfAborted = function() {};

/**
 * @constructor
 * @see https://dom.spec.whatwg.org/#interface-abortcontroller
 */
function AbortController() {}

/** @const {!AbortSignal} */
AbortController.prototype.signal;

/**
 * @param {*=} reason
 * @return {void}
 */
AbortController.prototype.abort = function(reason) {};
