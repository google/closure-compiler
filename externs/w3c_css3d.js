/*
 * Copyright 2010 Google Inc.
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
 * @fileoverview Definitions for W3C's CSS 3D Transforms specification.
 *  The whole file has been fully type annotated. Created from
 *  http://www.w3.org/TR/css3-3d-transforms/
 *
 * @externs
 */

/**
 * @constructor
 * @param {string} matrix
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
function CSSMatrix(matrix) {}

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m11;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m12;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m13;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m14;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m21;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m22;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m23;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m24;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m31;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m32;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m33;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m34;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m41;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m42;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m43;

/**
 * @type {number}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.m44;

/**
 * @param {string} string
 * @return {void}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.setMatrixValue = function(string) {};

/**
 * @param {CSSMatrix} secondMatrix
 * @return {CSSMatrix}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.multiply = function(secondMatrix) {};

/**
 * @return {CSSMatrix}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.inverse = function() {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} z
 * @return {CSSMatrix}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.translate = function(x, y, z) {};

/**
 * @param {number} scaleX
 * @param {number} scaleY
 * @param {number} scaleZ
 * @return {CSSMatrix}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.scale = function(scaleX, scaleY, scaleZ) {};

/**
 * @param {number} rotX
 * @param {number} rotY
 * @param {number} rotZ
 * @return {CSSMatrix}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.rotate = function(rotX, rotY, rotZ) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} z
 * @param {number} angle
 * @return {CSSMatrix}
 * @see http://www.w3.org/TR/css3-3d-transforms/#cssmatrix-interface
 */
CSSMatrix.prototype.rotateAxisAngle = function(x, y, z, angle) {};

/**
 * @constructor
 * @param {string} matrix
 * @extends {CSSMatrix}
 * @see http://developer.apple.com/safari/library/documentation/AudioVideo/Reference/WebKitCSSMatrixClassReference/WebKitCSSMatrix/WebKitCSSMatrix.html#//apple_ref/javascript/instm/WebKitCSSMatrix/setMatrixValue
 */
function WebKitCSSMatrix(matrix) {}

/**
 * @constructor
 * @param {string} matrix
 * @extends {CSSMatrix}
 * @see http://msdn.microsoft.com/en-us/library/windows/apps/hh453593.aspx
 */
function MSCSSMatrix(matrix) {}
