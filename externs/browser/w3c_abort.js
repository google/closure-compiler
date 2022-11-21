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
 * @interface
 * @extends {EventTarget}
 * @see https://dom.spec.whatwg.org/#interface-AbortSignal
 */
function AbortSignal() {}

/** @const {boolean} */
AbortSignal.prototype.aborted;

/** @type {?function(!Event)} */
AbortSignal.prototype.onabort;

/** @const {?} */
AbortSignal.prototype.reason;

/** @return {void} */
AbortSignal.prototype.throwIfAborted = function() {};

/**
 * @param {?=} reason
 * @return {AbortSignal}
 */
AbortSignal.abort = function (reason) {};

/**
 * @param {number} time
 * @return {AbortSignal}
 */
AbortSignal.timeout = function(time) {};


/**
 * @constructor
 * @see https://dom.spec.whatwg.org/#interface-abortcontroller
 */
function AbortController() {}

/** @const {!AbortSignal} */
AbortController.prototype.signal;

/**
 * @param {?=} reason
 * @return {void}
 */
AbortController.prototype.abort = function(reason) {};
