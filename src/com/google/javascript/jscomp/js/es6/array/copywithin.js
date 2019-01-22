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

$jscomp.polyfill('Array.prototype.copyWithin', function(orig) {
  // requires strict mode to throw for invalid `this` or params
  'use strict';

  if (orig) return orig;

  /**
   * Copies elements from one part of the array to another.
   *
   * @this {!IArrayLike<VALUE>}
   * @param {number} target Start index to copy elements to.
   * @param {number} start Start index to copy elements from.
   * @param {number=} opt_end Index from which to end copying.
   * @return {!IArrayLike<VALUE>} The array, with the copy performed in-place.
   * @template VALUE
   */
  var polyfill = function(target, start, opt_end) {
    var len = this.length;
    target = toInteger(target);
    start = toInteger(start);
    var end = opt_end === undefined ? len : toInteger(opt_end);
    var to = target < 0 ? Math.max(len + target, 0) : Math.min(target, len);
    var from = start < 0 ? Math.max(len + start, 0) : Math.min(start, len);
    var final = end < 0 ? Math.max(len + end, 0) : Math.min(end, len);
    if (to < from) {
      while (from < final) {
        if (from in this) {
          this[to++] = this[from++];
        } else {
          delete this[to++];
          from++;
        }
      }
    } else {
      final = Math.min(final, len + from - to);
      to += final - from;
      while (final > from) {
        if (--final in this) {
          this[--to] = this[final];
        } else {
          delete this[--to];
        }
      }
    }
    return this;
  };

  /**
   * @param {number} arg
   * @return {number}
   */
  function toInteger(arg) {
    var n = Number(arg);
    if (n === Infinity || n === -Infinity) {
      return n;
    }
    return n | 0;
  }

  return polyfill;
}, 'es6', 'es3');
