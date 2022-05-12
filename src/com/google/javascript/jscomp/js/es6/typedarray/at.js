/*
 * Copyright 2022 The Closure Compiler Authors.
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
 * @fileoverview Typed array at polyfill.
 * @suppress {uselessCode}
 */
'require es6/util/atmethod';
'require util/polyfill';

/**
 * @param {*} orig
 * @return {*}
 */
$jscomp.typedArrayAt = function(orig) {
  if (orig) return orig;
  return $jscomp.atMethod;
};

$jscomp.polyfill(
    'Int8Array.prototype.at', $jscomp.typedArrayAt, 'es_next', 'es5');
$jscomp.polyfill(
    'Uint8Array.prototype.at', $jscomp.typedArrayAt, 'es_next', 'es5');
$jscomp.polyfill(
    'Uint8ClampedArray.prototype.at', $jscomp.typedArrayAt, 'es_next', 'es5');
$jscomp.polyfill(
    'Int16Array.prototype.at', $jscomp.typedArrayAt, 'es_next', 'es5');
$jscomp.polyfill(
    'Uint16Array.prototype.at', $jscomp.typedArrayAt, 'es_next', 'es5');
$jscomp.polyfill(
    'Int32Array.prototype.at', $jscomp.typedArrayAt, 'es_next', 'es5');
$jscomp.polyfill(
    'Uint32Array.prototype.at', $jscomp.typedArrayAt, 'es_next', 'es5');
$jscomp.polyfill(
    'Float32Array.prototype.at', $jscomp.typedArrayAt, 'es_next', 'es5');
$jscomp.polyfill(
    'Float64Array.prototype.at', $jscomp.typedArrayAt, 'es_next', 'es5');
