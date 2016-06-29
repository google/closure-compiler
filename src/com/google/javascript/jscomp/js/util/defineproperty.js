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

/**
 * @fileoverview Provides methods to polyfill native objects.
 */
'require base';


/**
 * Polyfill for Object.defineProperty() method:
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/defineProperty
 *
 * Obviously we can't support getters and setters in ES3, so we
 * throw a TypeError in that case.  We also refuse to define
 * properties on Array.prototype and Object.prototype, since we
 * can't make them non-enumerable and this messes up peoples' for
 * loops.  Beyond this, we simply assign values and not worry
 * about enumerability or writeability.
 * @param {?} target
 * @param {string} property
 * @param {?} descriptor
 */
$jscomp.defineProperty =
    typeof Object.defineProperties == 'function' ?
    Object.defineProperty :
    function(target, property, descriptor) {
      descriptor = /** @type {!ObjectPropertyDescriptor} */ (descriptor);
      if (descriptor.get || descriptor.set) {
        throw new TypeError('ES3 does not support getters and setters.');
      }
      if (target == Array.prototype || target == Object.prototype) return;
      target[property] = descriptor.value;
    };
