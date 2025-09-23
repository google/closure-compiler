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
 * Technically shipped with the initial version of TypedArray, but Cobalt 9 does
 * not support it at all and Cobalt 11 only supports it in 4 of the 9 TypedArray
 * subclasses.
 * @param {*} orig
 * @return {*}
 */
$jscomp.typedArrayFill = function(orig) {
  if (orig) return orig;
  return Array.prototype.fill;
};

$jscomp.polyfillTypedArrayMethod(
    'fill', $jscomp.typedArrayFill, 'es6', 'es5');
