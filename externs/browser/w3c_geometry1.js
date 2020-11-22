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

/**
 * @constructor
 * @param {number=} x
 * @param {number=} y
 * @param {number=} z
 * @param {number=} w
 * @see https://www.w3.org/TR/geometry-1/#dom-dompointreadonly-dompointreadonly
 * TODO(larsrc): Remove duplicate definitions, then remove these
 * @suppress {duplicate}
 */
function DOMPointReadOnly(x, y, z, w) {}

/**
 * @param {!DOMPointInit} other
 * @return {!DOMPointReadOnly}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompointreadonly-frompoint
 * @suppress {duplicate}
 */
DOMPointReadOnly.prototype.fromPoint = function(other) {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompointreadonly-x
 * @suppress {duplicate}
 */
DOMPointReadOnly.prototype.x;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompointreadonly-y
 * @suppress {duplicate}
 */
DOMPointReadOnly.prototype.y;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompointreadonly-z
 * @suppress {duplicate}
 */
DOMPointReadOnly.prototype.z;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompointreadonly-w
 * @suppress {duplicate}
 */
DOMPointReadOnly.prototype.w;

/**
 * @constructor
 * @extends {DOMPointReadOnly}
 * @param {number=} x
 * @param {number=} y
 * @param {number=} z
 * @param {number=} w
 * @see https://www.w3.org/TR/geometry-1/#dom-dompoint-dompoint
 * @suppress {duplicate}
 */
function DOMPoint(x, y, z, w) {}

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompoint-x
 * @suppress {duplicate}
 */
DOMPoint.prototype.x;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompoint-y
 * @suppress {duplicate}
 */
DOMPoint.prototype.y;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompoint-z
 * @suppress {duplicate}
 */
DOMPoint.prototype.z;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompoint-w
 * @suppress {duplicate}
 */
DOMPoint.prototype.w;

/**
 * @record
 * @see https://www.w3.org/TR/geometry-1/#dictdef-dompointinit
 * @suppress {duplicate}
 */
function DOMPointInit() {}

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompointinit-x
 * @suppress {duplicate}
 */
DOMPointInit.prototype.x;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompointinit-y
 * @suppress {duplicate}
 */
DOMPointInit.prototype.y;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompointinit-z
 * @suppress {duplicate}
 */
DOMPointInit.prototype.z;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dompointinit-w
 * @suppress {duplicate}
 */
DOMPointInit.prototype.w;

/**
 * @constructor
 * @param {string|Array<number>} init
 * @see https://www.w3.org/TR/geometry-1/#dommatrixreadonly
 * @suppress {duplicate}
 */
function DOMMatrixReadOnly(init) {}

/**
 * @param {!DOMMatrixInit} other
 * @return {!DOMMatrixReadOnly}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-frommatrix
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.fromMatrix = function(other) {};

/**
 * @param {!Float32Array} array32
 * @return {!DOMMatrixReadOnly}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-fromfloat32array
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.fromFloat32Array = function(array32) {};

/**
 * @param {!Float64Array} array64
 * @return {!DOMMatrixReadOnly}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-fromfloat64array
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.fromFloat64Array = function(array64) {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-a
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.a;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-b
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.b;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-c
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.c;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-d
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.d;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-e
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.e;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-f
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.f;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m11
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m11;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m12
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m12;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m13
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m13;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m14
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m14;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m21
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m21;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m22
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m22;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m23
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m23;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m24
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m24;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m31
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m31;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m32
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m32;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m33
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m33;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m34
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m34;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m41
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m41;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m42
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m42;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m43
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m43;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m44
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.m44;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-is2d
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.is2D;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-isidentity
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.isIdentity;

/**
 * @param {number=} tx
 * @param {number=} ty
 * @param {number=} tz
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-translate
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.translate = function(tx, ty, tz) {};

/**
 * @param {number=} scaleX
 * @param {number=} scaleY
 * @param {number=} scaleZ
 * @param {number=} originX
 * @param {number=} originY
 * @param {number=} originZ
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-scale
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.scale = function(
    scaleX, scaleY, scaleZ, originX, originY, originZ) {};

/**
 * @param {number=} scaleX
 * @param {number=} scaleY
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-scalenonuniform
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.scaleNonUniform = function(scaleX, scaleY) {};

/**
 * @param {number=} scale
 * @param {number=} originX
 * @param {number=} originY
 * @param {number=} originZ
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-scale3d
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.scale3d = function(
    scale, originX, originY, originZ) {};

/**
 * @param {number=} rotX
 * @param {number=} rotY
 * @param {number=} rotZ
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-rotate
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.rotate = function(rotX, rotY, rotZ) {};

/**
 * @param {number=} x
 * @param {number=} y
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-rotatefromvector
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.rotateFromVector = function(x, y) {};

/**
 * @param {number=} x
 * @param {number=} y
 * @param {number=} z
 * @param {number=} angle
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-rotateaxisangle
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.rotateAxisAngle = function(x, y, z, angle) {};

/**
 * @param {number=} sx
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-skewx
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.skewX = function(sx) {};

/**
 * @param {number=} sy
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-skewy
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.skewY = function(sy) {};

/**
 * @param {!DOMMatrixInit} other
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-multiply
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.multiply = function(other) {};

/**
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-flipx
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.flipX = function() {};

/**
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-flipy
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.flipY = function() {};

/**
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-inverse
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.inverse = function() {};

/**
 * @param {DOMPointInit} point
 * @return {!DOMPoint}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-transformpoint
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.transformPoint = function(point) {};

/**
 * @return {!Float32Array}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-tofloat32array
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.toFloat32Array = function() {};

/**
 * @return {!Float64Array}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-tofloat64array
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.toFloat64Array = function() {};

/**
 * @return {Object}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-tojson
 * @suppress {duplicate}
 */
DOMMatrixReadOnly.prototype.toJSON = function() {};

/**
 * @constructor
 * @extends {DOMMatrixReadOnly}
 * @param {string|Array<number>} init
 * @see https://www.w3.org/TR/geometry-1/#dommatrix
 * @suppress {duplicate}
 */
function DOMMatrix(init) {}

/**
 * @param {!DOMMatrixInit} other
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-frommatrix
 * @suppress {duplicate}
 */
DOMMatrix.fromMatrix = function(other) {};

/**
 * @param {!Float32Array} array32
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-fromfloat32array
 * @suppress {duplicate}
 */
DOMMatrix.fromFloat32Array = function(array32) {};

/**
 * @param {!Float64Array} array64
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-fromfloat64array
 * @suppress {duplicate}
 */
DOMMatrix.fromFloat64Array = function(array64) {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-a
 * @suppress {duplicate}
 */
DOMMatrix.prototype.a;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-b
 * @suppress {duplicate}
 */
DOMMatrix.prototype.b;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-c
 * @suppress {duplicate}
 */
DOMMatrix.prototype.c;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-d
 * @suppress {duplicate}
 */
DOMMatrix.prototype.d;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-e
 * @suppress {duplicate}
 */
DOMMatrix.prototype.e;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-f
 * @suppress {duplicate}
 */
DOMMatrix.prototype.f;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m11
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m11;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m12
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m12;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m13
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m13;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m14
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m14;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m21
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m21;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m22
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m22;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m23
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m23;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m24
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m24;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m31
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m31;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m32
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m32;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m33
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m33;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m34
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m34;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m41
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m41;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m42
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m42;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m43
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m43;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixreadonly-m44
 * @suppress {duplicate}
 */
DOMMatrix.prototype.m44;

/**
 * @param {!DOMMatrixInit} other
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-multiply
 * @suppress {duplicate}
 */
DOMMatrix.prototype.multiplySelf = function(other) {};

/**
 * @param {!DOMMatrixInit} other
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-premultiply
 * @suppress {duplicate}
 */
DOMMatrix.prototype.preMultiplySelf = function(other) {};

/**
 * @param {number=} tx
 * @param {number=} ty
 * @param {number=} tz
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-translate
 * @suppress {duplicate}
 */
DOMMatrix.prototype.translateSelf = function(tx, ty, tz) {};

/**
 * @param {number=} scaleX
 * @param {number=} scaleY
 * @param {number=} scaleZ
 * @param {number=} originX
 * @param {number=} originY
 * @param {number=} originZ
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-scale
 * @suppress {duplicate}
 */
DOMMatrix.prototype.scaleSelf = function(
    scaleX, scaleY, scaleZ, originX, originY, originZ) {};

/**
 * @param {number=} scale
 * @param {number=} originX
 * @param {number=} originY
 * @param {number=} originZ
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-scale3d
 * @suppress {duplicate}
 */
DOMMatrix.prototype.scale3dSelf = function(
    scale, originX, originY, originZ) {};

/**
 * @param {number=} rotX
 * @param {number=} rotY
 * @param {number=} rotZ
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-rotate
 * @suppress {duplicate}
 */
DOMMatrix.prototype.rotateSelf = function(rotX, rotY, rotZ) {};

/**
 * @param {number=} x
 * @param {number=} y
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-rotatefromvector
 * @suppress {duplicate}
 */
DOMMatrix.prototype.rotateFromVectorSelf = function(x, y) {};

/**
 * @param {number=} x
 * @param {number=} y
 * @param {number=} z
 * @param {number=} angle
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-rotateaxisangle
 * @suppress {duplicate}
 */
DOMMatrix.prototype.rotateAxisAngleSelf = function(x, y, z, angle) {};

/**
 * @param {number=} sx
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-skewx
 * @suppress {duplicate}
 */
DOMMatrix.prototype.skewXSelf = function(sx) {};

/**
 * @param {number=} sy
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-skewy
 * @suppress {duplicate}
 */
DOMMatrix.prototype.skewYSelf = function(sy) {};

/**
 * @return {!DOMMatrix}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix-inverse
 * @suppress {duplicate}
 */
DOMMatrix.prototype.inverseSelf = function() {};

/**
 * @record
 * @see https://www.w3.org/TR/geometry-1/#dictdef-dommatrix2dinit
 * @suppress {duplicate}
 */
function DOMMatrix2DInit() {}

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-a
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.a;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-b
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.b;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-c
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.c;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-d
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.d;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-e
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.e;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-f
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.f;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-m11
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.m11;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-m12
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.m12;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-m21
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.m21;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-m22
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.m22;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-m41
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.m41;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrix2dinit-m42
 * @suppress {duplicate}
 */
DOMMatrix2DInit.prototype.m42;

/**
 * @record
 * @extends {DOMMatrix2DInit}
 * @see https://www.w3.org/TR/geometry-1/#dictdef-dommatrixinit
 * @suppress {duplicate}
 */
function DOMMatrixInit() {}

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-m13
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.m13;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-m14
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.m14;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-m23
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.m23;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-m24
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.m24;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-m31
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.m31;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-m32
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.m32;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-m33
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.m33;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-m34
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.m34;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-m43
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.m43;

/**
 * @type {number}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-m44
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.m44;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/geometry-1/#dom-dommatrixinit-is2d
 * @suppress {duplicate}
 */
DOMMatrixInit.prototype.is2D;
