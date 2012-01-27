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
 * @fileoverview Definitions for the fullscreen API specification of HTML5.
 * @see http://dvcs.w3.org/hg/fullscreen/raw-file/tip/Overview.html
 *
 * @externs
 */

// Externs as defined by the specification.
Element.prototype.requestFullscreen = function() {};

/** @type {boolean} */
Document.prototype.fullscreenEnabled;

/** @type {Element} */
Document.prototype.fullscreenElement;

Document.prototype.exitFullscreen = function() {};

// Externs definitions of browser current implementations.
// Firefox 10 implementation.
Element.prototype.mozRequestFullScreen = function() {};

/** @type {boolean} */
Document.prototype.mozFullScreen;

Document.prototype.mozCancelFullScreen = function() {};

/** @type {Element} */
Document.prototype.mozFullScreenElement;

/** @type {boolean} */
Document.prototype.mozFullScreenEnabled;

// Chrome 18 implementation.
Element.prototype.webkitRequestFullScreen = function() {};

/** @type {boolean} */
Document.prototype.webkitIsFullScreen;

Document.prototype.webkitCancelFullScreen = function() {};

/** @type {Element} */
Document.prototype.webkitCurrentFullScreenElement;

/** @type {boolean} */
Document.prototype.webkitFullScreenKeyboardInputAllowed;

/** @type {number} */
Element.ALLOW_KEYBOARD_INPUT = 1;

/** @type {number} */
Element.prototype.ALLOW_KEYBOARD_INPUT = 1;
