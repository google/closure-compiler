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


$jscomp.polyfill('Reflect.setPrototypeOf', function(orig) {
  if (orig) return orig;

  // IE<11 has no way to polyfill this, so don't even try.
  if (typeof ''.__proto__ != 'object') return null;

  /**
   * Polyfill for Reflect.setPrototypeOf() method:
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Reflect/setPrototypeOf
   *
   * Sets the prototype in a "standard" way.
   *
   * @param {!Object} target Target on which to get the property.
   * @param {?Object} proto The new prototype.
   * @return {boolean} Whether the prototype was successfully set.
   */
  var polyfill = function(target, proto) {
    try {
      target.__proto__ = proto;
      return target.__proto__ === proto;
    } catch (err) {
      return false;
    }
  };
  return polyfill;
}, 'es6', 'es5');
