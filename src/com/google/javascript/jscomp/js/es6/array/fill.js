/*
 * Copyright 2016 The Closure Compiler Authors.
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

'require util/polyfill';
'require util/tointeger';
'require util/tolength';

$jscomp.polyfill('Array.prototype.fill', function(orig) {
  if (orig) return orig;

  /**
   * Fills elements of an array with a constant value.
   *
   * @this {!IArrayLike<VALUE>}
   * @param {VALUE} value Value to fill.
   * @param {number=} opt_start Start index, or zero if omitted.
   * @param {number=} opt_end End index, or length if omitted.
   * @return {!IArrayLike<VALUE>} The array, with the fill performed in-place.
   * @template VALUE
   * @suppress {reportUnknownTypes, strictPrimitiveOperators}
   */
  var polyfill = function(value, opt_start, opt_end) {
    'use strict';
    var length = $jscomp.toLength(this.length);
    var start = $jscomp.toInteger(opt_start);
    if (start < 0) {
      start = Math.max(0, length + start);
    }
    var end = opt_end === undefined ? length : $jscomp.toInteger(opt_end);
    if (end < 0) {
      end = Math.max(0, length + end);
    } else if (end > length) {
      end = length;
    }
    for (var i = start; i < end; i++) {
      this[i] = value;
    }
    return this;
  };

  return polyfill;
}, 'es6', 'es3');
