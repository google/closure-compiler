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

'require util/finddescriptor util/polyfill';


$jscomp.polyfill('Reflect.get', function(orig) {
  if (orig) return orig;

  /**
   * Polyfill for Reflect.get() method:
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Reflect/get
   *
   * Applies the 'getprop' operator as a function.
   *
   * @param {!Object} target Target on which to get the property.
   * @param {string} propertyKey Name of the property to get.
   * @param {!Object=} opt_receiver An optional 'this' to use for a getter.
   * @return {*} The value of the property.
   */
  var polyfill = function(target, propertyKey, opt_receiver) {
    if (arguments.length <= 2) {
      return target[propertyKey];
    }
    var property = $jscomp.findDescriptor(target, propertyKey);
    if (property) {
      return property.get ? property.get.call(opt_receiver) : property.value;
    }
    return undefined;
  };
  return polyfill;
}, 'es6', 'es5'); // ES5: findDescriptor requires getPrototypeOf
