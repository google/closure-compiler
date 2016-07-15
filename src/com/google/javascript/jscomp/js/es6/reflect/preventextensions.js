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


$jscomp.polyfill('Reflect.preventExtensions', function(orig) {
  if (orig) return orig;

  if (typeof Object.preventExtensions != 'function') {
    return function() { return false; };
  }

  /**
   * Polyfill for Reflect.preventExtensions() method:
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Reflect/preventExtensions
   *
   * Same function as Object.preventExtensions (the spec says
   * to throw if the input is not an object, but jscompiler will
   * fail to typecheck, so there's no reason to distinguish here).
   *
   * @param {!Object} target
   * @return {boolean}
   */
  var polyfill = function(target) {
    Object.preventExtensions(target);
    return !Object.isExtensible(target);
  };
  return polyfill;
}, 'es6', 'es3');
