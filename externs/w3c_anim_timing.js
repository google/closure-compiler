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
 *  whole file has been fully type annotated. Created from
 *  http://webstuff.nfshost.com/anim-timing/Overview.html
 *
 * @externs
 */

// TODO(nicksantos): Change {Function} to {function(number)}

/**
 * @param {Function} callback
 * @param {Element} element
 * @return {number}
 */
function requestAnimationFrame(callback, element) {};

/**
 * @param {number} handle
 */
function cancelRequestAnimationFrame(handle) {};

/**
 * @param {Function} callback
 * @param {Element} element
 * @return {number}
 */
function webkitRequestAnimationFrame(callback, element) {};

/**
 * @param {number} handle
 */
function webkitCancelRequestAnimationFrame(handle) {};

/**
 * @param {Function} callback
 * @param {Element} element
 * @return {number}
 */
function mozRequestAnimationFrame(callback, element) {};

/**
 * @param {number} handle
 */
function mozCancelRequestAnimationFrame(handle) {};

/**
 * @param {Function} callback
 * @param {Element} element
 * @return {number}
 */
function ieRequestAnimationFrame(callback, element) {};

/**
 * @param {number} handle
 */
function ieCancelRequestAnimationFrame(handle) {};

/**
 * @param {Function} callback
 * @param {Element} element
 * @return {number}
 */
function oRequestAnimationFrame(callback, element) {};

/**
 * @param {number} handle
 */
function oCancelRequestAnimationFrame(handle) {};

/**
 * @param {Function} callback
 * @param {Element} element
 * @return {number}
 */
Window.prototype.requestAnimationFrame = function(callback, element) {};

/**
 * @param {number} handle
 */
Window.prototype.cancelRequestAnimationFrame = function(handle) {};

/**
 * @param {Function} callback
 * @param {Element} element
 * @return {number}
 */
Window.prototype.webkitRequestAnimationFrame = function(callback, element) {};

/**
 * @param {number} handle
 */
Window.prototype.webkitCancelRequestAnimationFrame = function(handle) {};

/**
 * @param {Function} callback
 * @param {Element} element
 * @return {number}
 */
Window.prototype.mozRequestAnimationFrame = function(callback, element) {};

/**
 * @param {number} handle
 */
Window.prototype.mozCancelRequestAnimationFrame = function(handle) {};

/**
 * @param {Function} callback
 * @param {Element} element
 * @return {number}
 */
Window.prototype.ieRequestAnimationFrame = function(callback, element) {};

/**
 * @param {number} handle
 */
Window.prototype.ieCancelRequestAnimationFrame = function(handle) {};

/**
 * @param {Function} callback
 * @param {Element} element
 * @return {number}
 */
Window.prototype.oRequestAnimationFrame = function(callback, element) {};

/**
 * @param {number} handle
 */
Window.prototype.oCancelRequestAnimationFrame = function(handle) {};
