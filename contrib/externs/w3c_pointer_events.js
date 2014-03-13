/*
 * Copyright 2014 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's Pointer Events specification.
 *  Created from
 *   http://www.w3.org/TR/pointerevents/
 *
 * @externs
 */


/**
 * @type {boolean}
 * @see http://www.w3.org/TR/pointerevents/#widl-Navigator-pointerEnabled
 */
Navigator.prototype.pointerEnabled;

/**
 * @type {number}
 * @see http://www.w3.org/TR/pointerevents/#widl-Navigator-maxTouchPoints
 */
Navigator.prototype.maxTouchPoints;


/**
 * @typedef {{
 *   bubbles: (boolean|undefined),
 *   cancelable: (boolean|undefined),
 *   view: (Window|undefined),
 *   detail: (number|undefined),
 *   screenX: (number|undefined),
 *   screenY: (number|undefined),
 *   clientX: (number|undefined),
 *   clientY: (number|undefined),
 *   ctrlKey: (boolean|undefined),
 *   shiftKey: (boolean|undefined),
 *   altKey: (boolean|undefined),
 *   metaKey: (boolean|undefined),
 *   button: (number|undefined),
 *   buttons: (number|undefined),
 *   relatedTarget: (EventTarget|undefined),
 *   pointerId: (number|undefined),
 *   width: (number|undefined),
 *   height: (number|undefined),
 *   pressure: (number|undefined),
 *   tiltX: (number|undefined),
 *   tiltY: (number|undefined),
 *   pointerType: (string|undefined),
 *   isPrimary: (boolean|undefined)
 * }}
 */
var PointerEventInit;

/**
 * @constructor
 * @extends {MouseEvent}
 * @param {string} type
 * @param {PointerEventInit=} opt_eventInitDict
 * @see http://www.w3.org/TR/pointerevents/#pointerevent-interface
 */
function PointerEvent(type, opt_eventInitDict) {}

/** @type {number} */
PointerEvent.prototype.pointerId;

/** @type {number} */
PointerEvent.prototype.width;

/** @type {number} */
PointerEvent.prototype.height;

/** @type {number} */
PointerEvent.prototype.pressure;

/** @type {number} */
PointerEvent.prototype.tiltX;

/** @type {number} */
PointerEvent.prototype.tiltY;

/** @type {string} */
PointerEvent.prototype.pointerType;

/** @type {boolean} */
PointerEvent.prototype.isPrimary;
