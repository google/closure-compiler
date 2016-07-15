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


$jscomp.polyfill('Reflect.defineProperty', function(orig) {
  if (orig) return orig;

  /**
   * Polyfill for Reflect.defineProperty() method:
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Reflect/defineProperty
   *
   * Version of Object.defineProperty that returns a boolean.
   *
   * @param {!Object} target Target on which to define the property.
   * @param {string} propertyKey Name of the property to define.
   * @param {!ObjectPropertyDescriptor} attributes Property attributes.
   * @return {boolean} Whether the property was defined.
   */
  var polyfill = function(target, propertyKey, attributes) {
    try {
      Object.defineProperty(target, propertyKey, attributes);
      var desc = Object.getOwnPropertyDescriptor(target, propertyKey);
      if (!desc) return false;
      return desc.configurable === (attributes.configurable || false) &&
          desc.enumerable === (attributes.enumerable || false) &&
          ('value' in desc ?
              desc.value === attributes.value &&
                  desc.writable === (attributes.writable || false) :
              desc.get === attributes.get &&
                  desc.set === attributes.set);
    } catch (err) {
      return false;
    }
  };
  return polyfill;
}, 'es6', 'es5'); // ES5: Requires Object.defineProperty
