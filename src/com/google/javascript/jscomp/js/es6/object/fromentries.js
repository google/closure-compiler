/*
 * Copyright 2019 The Closure Compiler Authors.
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
'require es6/symbol';
'require util/defineproperty';
'require util/polyfill';

$jscomp.polyfill('Object.fromEntries', function(orig) {
  if (orig) {
    return orig;
  }

  /**
   * @param {!Iterable<*>} iter
   * @return {!Object}
   * @see https://github.com/tc39/proposal-object-from-entries/blob/master/polyfill.js
   */
  function fromEntries(iter) {
    var obj = {};

    if (!(Symbol.iterator in iter)) {
      throw new TypeError('' + iter + ' is not iterable');
    }

    var iteratorFn = (/** @type {function(): !Iterator<!Object<number, *>>} */ (
        iter[Symbol.iterator]));
    var iterator = iteratorFn.call(iter);

    for (var result = iterator.next(); !result.done; result = iterator.next()) {
      var pair = result.value;

      if (Object(pair) !== pair) {
        throw new TypeError('iterable for fromEntries should yield objects');
      }

      var key = pair[0];
      var val = pair[1];
      // Use Object.defineProperty to implement CreateDataPropertyOrThrow per
      // ECMA-262 24.1.1.2 (Object.fromEntries): defining an own property on
      // obj rather than going through a plain assignment, which would
      // invoke an inherited setter (e.g. for a "__proto__" key) instead of
      // creating an own property, letting a caller-supplied key/value pair
      // reassign obj's own prototype.
      Object.defineProperty(obj, /** @type {string|symbol} */ (key), {
        value: val,
        writable: true,
        enumerable: true,
        configurable: true,
      });
    }

    return obj;
  }

  return fromEntries;
}, 'es_2019', 'es3');

