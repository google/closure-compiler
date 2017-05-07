/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * @fileoverview Definitions for Clipboard API and events
 * based on "W3C Working Draft 13 December 2016"
 * @see https://www.w3.org/TR/2016/WD-clipboard-apis-20161213/
 *
 * @externs
 * @author vobruba.martin@gmail.com (Martin Vobruba)
 */


/**
 * @record
 * @extends {EventInit}
 * @see https://www.w3.org/TR/clipboard-apis/#clipboard-event-interfaces
 */
function ClipboardEventInit(){};

/** @type {(undefined|?DataTransfer)} */
ClipboardEventInit.prototype.clipboardData;


/**
 * @param {string} type
 * @param {!ClipboardEventInit=} opt_eventInit
 * @constructor
 * @extends {Event}
 * @see https://www.w3.org/TR/clipboard-apis/#clipboard-event-interfaces
 */
function ClipboardEvent(type, opt_eventInit) {}

/** @type {?DataTransfer} */
ClipboardEvent.prototype.clipboardData;