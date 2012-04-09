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
 * @fileoverview Definitions for timing control for script base animations. The
 *  whole file has been fully type annotated.
 *
 * @see http://www.w3.org/TR/animation-timing/
 * @see http://webstuff.nfshost.com/anim-timing/Overview.html
 * @externs
 */

/**
 * @param {function(number)} callback
 * @param {Element=} opt_element In early versions of this API, the callback
 *     was invoked only if the element was visible.
 * @return {number}
 */
function requestAnimationFrame(callback, opt_element) {};

/**
 * @param {number} handle
 */
function cancelRequestAnimationFrame(handle) {};

/**
 * @param {function(number)} callback
 * @param {Element=} opt_element
 * @return {number}
 */
function webkitRequestAnimationFrame(callback, opt_element) {};

/**
 * @param {number} handle
 */
function webkitCancelRequestAnimationFrame(handle) {};

/**
 * @param {function(number)} callback
 * @param {Element=} opt_element
 * @return {number}
 */
function mozRequestAnimationFrame(callback, opt_element) {};

/**
 * @param {number} handle
 */
function mozCancelRequestAnimationFrame(handle) {};

/**
 * @param {function(number)} callback
 * @param {Element=} opt_element
 * @return {number}
 */
function ieRequestAnimationFrame(callback, opt_element) {};

/**
 * @param {number} handle
 */
function ieCancelRequestAnimationFrame(handle) {};

/**
 * @param {function(number)} callback
 * @param {Element=} opt_element
 * @return {number}
 */
function oRequestAnimationFrame(callback, opt_element) {};

/**
 * @param {number} handle
 */
function oCancelRequestAnimationFrame(handle) {};

/**
 * @param {function(number)} callback
 * @param {Element=} opt_element
 * @return {number}
 */
Window.prototype.requestAnimationFrame = function(callback, opt_element) {};

/**
 * @param {number} handle
 */
Window.prototype.cancelRequestAnimationFrame = function(handle) {};

/**
 * @param {function(number)} callback
 * @param {Element=} opt_element
 * @return {number}
 */
Window.prototype.webkitRequestAnimationFrame = function(callback, opt_element) {};

/**
 * @param {number} handle
 */
Window.prototype.webkitCancelRequestAnimationFrame = function(handle) {};

/**
 * @param {function(number)} callback
 * @param {Element=} opt_element
 * @return {number}
 */
Window.prototype.mozRequestAnimationFrame = function(callback, opt_element) {};

/**
 * @param {number} handle
 */
Window.prototype.mozCancelRequestAnimationFrame = function(handle) {};

/**
 * @param {function(number)} callback
 * @param {Element=} opt_element
 * @return {number}
 */
Window.prototype.ieRequestAnimationFrame = function(callback, opt_element) {};

/**
 * @param {number} handle
 */
Window.prototype.ieCancelRequestAnimationFrame = function(handle) {};

/**
 * @param {function(number)} callback
 * @param {Element=} opt_element
 * @return {number}
 */
Window.prototype.oRequestAnimationFrame = function(callback, opt_element) {};

/**
 * @param {number} handle
 */
Window.prototype.oCancelRequestAnimationFrame = function(handle) {};
