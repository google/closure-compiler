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
 * @suppress {reportUnknownTypes}
 */
'require util/defines';


/**
 * Polyfill for Object.defineProperty() method:
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/defineProperty
 *
 * Refuses to define properties on Array.prototype and Object.prototype,
 * since we can't make them non-enumerable and this messes up peoples' for
 * loops.  Beyond this, we simply assign values and not worry
 * about enumerability or writeability.
 * @param {?} target
 * @param {string} property
 * @param {?} descriptor
 * @suppress {reportUnknownTypes}
 */
$jscomp.defineProperty =
    $jscomp.ASSUME_ES5 || typeof Object.defineProperties == 'function' ?
    Object.defineProperty :
    function(target, property, descriptor) {
      descriptor = /** @type {!ObjectPropertyDescriptor} */ (descriptor);
      // NOTE: This is currently never called with a descriptor outside
      // the control of the compiler.  If we ever decide to polyfill either
      // Object.defineProperty or Reflect.defineProperty for ES3, we should
      // explicitly check for `get` or `set` on the descriptor and throw a
      // TypeError, since it's impossible to properly polyfill it.
      if (target == Array.prototype || target == Object.prototype) return;
      target[property] = descriptor.value;
    };
