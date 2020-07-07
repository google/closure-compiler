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

'require es6/array/copywithin';
'require util/polyfill';

/**
 * @param {*} orig
 * @return {*}
 */
$jscomp.typedArrayCopyWithin = function(orig) {
  if (orig) return orig;
  return Array.prototype.copyWithin;
};

$jscomp.polyfill(
    'Int8Array.prototype.copyWithin', $jscomp.typedArrayCopyWithin, 'es6',
    'es5');
$jscomp.polyfill(
    'Uint8Array.prototype.copyWithin', $jscomp.typedArrayCopyWithin, 'es6',
    'es5');
$jscomp.polyfill(
    'Uint8ClampedArray.prototype.copyWithin', $jscomp.typedArrayCopyWithin,
    'es6', 'es5');
$jscomp.polyfill(
    'Int16Array.prototype.copyWithin', $jscomp.typedArrayCopyWithin, 'es6',
    'es5');
$jscomp.polyfill(
    'Uint16Array.prototype.copyWithin', $jscomp.typedArrayCopyWithin, 'es6',
    'es5');
$jscomp.polyfill(
    'Int32Array.prototype.copyWithin', $jscomp.typedArrayCopyWithin, 'es6',
    'es5');
$jscomp.polyfill(
    'Uint32Array.prototype.copyWithin', $jscomp.typedArrayCopyWithin, 'es6',
    'es5');
$jscomp.polyfill(
    'Float32Array.prototype.copyWithin', $jscomp.typedArrayCopyWithin, 'es6',
    'es5');
$jscomp.polyfill(
    'Float64Array.prototype.copyWithin', $jscomp.typedArrayCopyWithin, 'es6',
    'es5');
