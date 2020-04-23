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
 * @fileoverview Definitions for W3C's Geometry Interfaces Module Level 1 spec.
 *  The whole file has been fully type annotated. Created from
 *  https://www.w3.org/TR/geometry-1/
 *
 * @externs
 */

/**
 * @deprecated ClientRect has been replaced by DOMRect in the latest spec.
 * @constructor
 * @see https://www.w3.org/TR/cssom-view/#changes-from-2011-08-04
 */
function ClientRect() {}

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-clientrect-top
 */
ClientRect.prototype.top;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-clientrect-right
 */
ClientRect.prototype.right;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-clientrect-bottom
 */
ClientRect.prototype.bottom;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-clientrect-left
 */
ClientRect.prototype.left;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-clientrect-width
 */
ClientRect.prototype.width;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-clientrect-height
 */
ClientRect.prototype.height;

/**
 * @constructor
 * @extends {ClientRect} for backwards compatibility
 * @param {number=} x
 * @param {number=} y
 * @param {number=} width
 * @param {number=} height
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-domrectreadonly
 */
function DOMRectReadOnly(x, y, width, height) {}

/**
 * @param {!DOMRectInit} other
 * @return {!DOMRectReadOnly}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-fromrect
 */
DOMRectReadOnly.prototype.fromRect = function(other) {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-x
 */
DOMRectReadOnly.prototype.x;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-y
 */
DOMRectReadOnly.prototype.y;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-width
 */
DOMRectReadOnly.prototype.width;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-height
 */
DOMRectReadOnly.prototype.height;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-top
 */
DOMRectReadOnly.prototype.top;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-right
 */
DOMRectReadOnly.prototype.right;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-bottom
 */
DOMRectReadOnly.prototype.bottom;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-left
 */
DOMRectReadOnly.prototype.left;

/**
 * @constructor
 * @extends {DOMRectReadOnly}
 * @param {number=} x
 * @param {number=} y
 * @param {number=} width
 * @param {number=} height
 * @see https://www.w3.org/TR/geometry-1/#dom-domrect-domrect
 */
function DOMRect(x, y, width, height) {}

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrect-x
 */
DOMRect.prototype.x;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrect-y
 */
DOMRect.prototype.y;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrect-width
 */
DOMRect.prototype.width;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrect-height
 */
DOMRect.prototype.height;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-top
 */
DOMRect.prototype.top;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-right
 */
DOMRect.prototype.right;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-bottom
 */
DOMRect.prototype.bottom;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectreadonly-left
 */
DOMRect.prototype.left;

/**
 * @constructor
 * @see https://www.w3.org/TR/geometry-1/#dictdef-domrectinit
 */
function DOMRectInit() {}

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectinit-x
 */
DOMRectInit.prototype.x;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectinit-y
 */
DOMRectInit.prototype.y;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectinit-width
 */
DOMRectInit.prototype.width;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-domrectinit-height
 */
DOMRectInit.prototype.height;
