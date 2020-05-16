/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * @fileoverview
 * @suppress {uselessCode}
 */

'require util/defines';
'require util/polyfill';
'require util/shouldpolyfill';

/**
 * @suppress {missingProperties,reportUnknownTypes}
 * @return {boolean}
 */
$jscomp.underscoreProtoCanBeSet = function() {
  var x = {a: true};
  var y = {};
  try {
    y.__proto__ = x;
    return y.a;
  } catch (e) {
    // __proto__ property is readonly (possibly IE 10?)
  }
  return false;
};

/**
 * If we can implement it, this will be a function that attempts to set the
 * prototype of an object, otherwise it will be `null`.
 *
 * It returns the first argument if successful. Throws a `TypeError` if the
 * object is not extensible.
 *
 * @type {null|function(!Object, ?Object): !Object}
 */
$jscomp.setPrototypeOf = ($jscomp.TRUST_ES6_POLYFILLS &&
                          typeof Object.setPrototypeOf == 'function') ?
    Object.setPrototypeOf :
    $jscomp.underscoreProtoCanBeSet() ? function(target, proto) {
      target.__proto__ = proto;
      if (target.__proto__ !== proto) {
        throw new TypeError(target + ' is not extensible');
      }
      return target;
    } : null;
