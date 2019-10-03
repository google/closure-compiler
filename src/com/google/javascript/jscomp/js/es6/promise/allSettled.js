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
'require util/polyfill';
'require es6/promise/promise';
'require es6/array/from';

$jscomp.polyfill('Promise.allSettled', function(orig) {
  if (orig) return orig;

  /**
   * @param {*} value
   * @return {{status: string, value: *}}
   */
  function fulfilledResult(value) {
    return {status: 'fulfilled', value: value};
  }

  /**
   * @param {*} reason
   * @return {{status: string, reason: *}}
   */
  function rejectedResult(reason) {
    return {status: 'rejected', reason: reason};
  }

  /**
   * These types are weaker than they could be, but they're strong enough for
   * this context.
   * @this {typeof Promise}
   * @param {!Iterable<*>} thenablesOrValues
   * @return {!Promise<!Array<*>>}
   */
  var polyfill = function(thenablesOrValues) {
    // The spec requires allSettled to be called directly on the Promise
    // constructor, which is consistent with its requirement for Promise.all().
    /** @type {typeof Promise} */
    var PromiseConstructor = this;

    /**
     * @param {*} maybeThenable
     * @return {!Promise<*>}
     */
    function convertToAllSettledResult(maybeThenable) {
      return PromiseConstructor.resolve(maybeThenable)
          .then(fulfilledResult, rejectedResult);
    }

    // Create an array of promises that resolve to the appropriate result
    // objects and never reject.
    var wrappedResults =
        Array.from(thenablesOrValues, convertToAllSettledResult);
    return PromiseConstructor.all(wrappedResults);
  };
  return polyfill;
}, 'es_next', 'es3');
