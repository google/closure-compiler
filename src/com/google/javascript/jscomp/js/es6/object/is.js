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

$jscomp.polyfill('Object.is', function(orig) {
  if (orig) return orig;

  /**
   * Polyfill for Object.is() method:
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/is
   *
   * Determines whether two values are the same value (that is,
   * functionally equivalent).  This is the same as ===-equality,
   * except for two cases: 0 is not the same as -0, and NaN is
   * the same as NaN.
   *
   * @param {*} left
   * @param {*} right
   * @return {boolean}
   */
  var polyfill = function(left, right) {
    if (left === right) {
      // Handle the 0 === -0 exception
      return (left !== 0) || (1 / left === 1 / /** @type {number} */ (right));
    } else {
      // Handle the NaN !== NaN exception
      return (left !== left) && (right !== right);
    }
  };

  return polyfill;
}, 'es6-impl', 'es3');
