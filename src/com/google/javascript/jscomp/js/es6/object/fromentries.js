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

    $jscomp.initSymbolIterator();

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
      obj[key] = val;
    }

    return obj;
  }

  return fromEntries;
}, 'es_2019', 'es3');
