/*
 * Copyright 2020 The Closure Compiler Authors.
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

'require es6/array/fill';
'require util/polyfill';

/**
 * @param {*} orig
 * @return {*}
 */
$jscomp.typedArrayFill = function(orig) {
  if (orig) return orig;
  return Array.prototype.fill;
};

$jscomp.polyfill(
    'Int8Array.prototype.fill', $jscomp.typedArrayFill, 'es6', 'es5');
$jscomp.polyfill(
    'Uint8Array.prototype.fill', $jscomp.typedArrayFill, 'es6', 'es5');
$jscomp.polyfill(
    'Uint8ClampedArray.prototype.fill', $jscomp.typedArrayFill, 'es6', 'es5');
$jscomp.polyfill(
    'Int16Array.prototype.fill', $jscomp.typedArrayFill, 'es6', 'es5');
$jscomp.polyfill(
    'Uint16Array.prototype.fill', $jscomp.typedArrayFill, 'es6', 'es5');
$jscomp.polyfill(
    'Int32Array.prototype.fill', $jscomp.typedArrayFill, 'es6', 'es5');
$jscomp.polyfill(
    'Uint32Array.prototype.fill', $jscomp.typedArrayFill, 'es6', 'es5');
$jscomp.polyfill(
    'Float32Array.prototype.fill', $jscomp.typedArrayFill, 'es6', 'es5');
$jscomp.polyfill(
    'Float64Array.prototype.fill', $jscomp.typedArrayFill, 'es6', 'es5');
