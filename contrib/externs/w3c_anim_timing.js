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

/**
 * @interface
 */
function FrameRequestCallback() {};

/**
 * @param {number} timestamp
 */
FrameRequestCallback.prototype.sample = function(timestamp) {};

/**
 * @param {FrameRequestCallback|Function} callback
 * @param {Element} element
 * @return {number}
 */
function requestAnimationFrame(callback, element) {};

/**
 * @param {number} handle
 */
function cancelRequestAnimationFrame(handle) {};

/**
 * @param {FrameRequestCallback|Function} callback
 * @param {Element} element
 * @return {number}
 */
function webkitRequestAnimationFrame(callback, element) {};

/**
 * @param {number} handle
 */
function webkitCancelRequestAnimationFrame(handle) {};

/**
 * @param {FrameRequestCallback|Function} callback
 * @param {Element} element
 * @return {number}
 */
function mozRequestAnimationFrame(callback, element) {};

/**
 * @param {number} handle
 */
function mozCancelRequestAnimationFrame(handle) {};
