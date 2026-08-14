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

'require es6/reflect/getownpropertydescriptor';
'require es6/reflect/isextensible';
'require es6/reflect/reflect';
'require util/finddescriptor';
'require util/polyfill';


$jscomp.polyfill('Reflect.set', function(orig) {
  if (orig) return orig;

  /**
   * Polyfill for Reflect.set() method:
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Reflect/set
   *
   * Applies the 'setprop' operator as a function.
   *
   * @param {!Object} target Target on which to get the property.
   * @param {string} propertyKey Name of the property to get.
   * @param {*} value Value to set.
   * @param {!Object=} opt_receiver An optional 'this' to use for a setter.
   * @return {boolean} Whether setting was successful.
   */
  var polyfill = function(target, propertyKey, value, opt_receiver) {
    var property = $jscomp.findDescriptor(target, propertyKey);
    var receiver = arguments.length > 3 ? opt_receiver : target;
    if (property) {
      if (property.get || property.set) {
        if (property.set) {
          property.set.call(receiver, value);
          return true;
        }
        return false;
      }
      if (!property.writable) {
        return false;
      }
    }
    if (receiver == null) {
      return false;
    }
    if (typeof receiver !== 'object' && typeof receiver !== 'function') {
      return false;
    }
    // Per ECMA-262 26.1.13 / 9.1.9 (SetWithReceiver / CreateDataProperty):
    // When target is not receiver or setting a data property on receiver,
    // Object.defineProperty is used to define or update an own data property on
    // receiver directly rather than `receiver[propertyKey] = value` (which would
    // invoke prototype setters on receiver's prototype chain and fail to properly
    // isolate data property definitions per specification).
    var receiverDesc = Reflect.getOwnPropertyDescriptor(receiver, propertyKey);
    if (receiverDesc) {
      if (receiverDesc.get || receiverDesc.set || !receiverDesc.writable) {
        return false;
      }
      Object.defineProperty(receiver, propertyKey, {value: value});
      return true;
    }
    if (!Reflect.isExtensible(receiver)) {
      return false;
    }
    Object.defineProperty(receiver, propertyKey, {
      value: value,
      writable: true,
      enumerable: true,
      configurable: true
    });
    return true;
  };
  return polyfill;
}, 'es6', 'es5'); // ES5: findDescriptor requires getPrototypeOf
