/*
 * Copyright 2023 The Closure Compiler Authors.
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
'require util/findlastinternal';
'require util/polyfill';

/**
 * @param {*} orig
 * @return {*}
 */
$jscomp.typedArrayFindLast = function(orig) {
  if (orig) return orig;

  /**
   * Finds and returns an element that satisfies the given predicate.
   *
   * @this {!IArrayLike<VALUE>}
   * @param {function(this: THIS, VALUE, number, !IArrayLike<VALUE>): *}
   *     callback
   * @param {THIS=} opt_thisArg
   * @return {VALUE|undefined} The found value, or undefined.
   * @template VALUE, THIS
   * @suppress {reportUnknownTypes}
   */
  var polyfill = function(callback, opt_thisArg) {
    return $jscomp.findLastInternal(this, callback, opt_thisArg).v;
  };

  return polyfill;
};

$jscomp.polyfill(
    'Int8Array.prototype.findLast', $jscomp.typedArrayFindLast, 'es_next',
    'es5');
$jscomp.polyfill(
    'Uint8Array.prototype.findLast', $jscomp.typedArrayFindLast, 'es_next',
    'es5');
$jscomp.polyfill(
    'Uint8ClampedArray.prototype.findLast', $jscomp.typedArrayFindLast,
    'es_next', 'es5');
$jscomp.polyfill(
    'Int16Array.prototype.findLast', $jscomp.typedArrayFindLast, 'es_next',
    'es5');
$jscomp.polyfill(
    'Uint16Array.prototype.findLast', $jscomp.typedArrayFindLast, 'es_next',
    'es5');
$jscomp.polyfill(
    'Int32Array.prototype.findLast', $jscomp.typedArrayFindLast, 'es_next',
    'es5');
$jscomp.polyfill(
    'Uint32Array.prototype.findLast', $jscomp.typedArrayFindLast, 'es_next',
    'es5');
$jscomp.polyfill(
    'Float32Array.prototype.findLast', $jscomp.typedArrayFindLast, 'es_next',
    'es5');
$jscomp.polyfill(
    'Float64Array.prototype.findLast', $jscomp.typedArrayFindLast, 'es_next',
    'es5');
