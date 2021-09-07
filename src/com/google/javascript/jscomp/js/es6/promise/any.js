/*
 * Copyright 2018 The Closure Compiler Authors.
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
'require es6/array/from';
'require es6/promise/promise';
'require es6/promise/aggregateerror';


$jscomp.polyfill('Promise.any', function(orig) {
  if (orig) return orig;

  // This matches the AggregateError's message that V8 reports, but may not
  // match other browsers.
  var aggregate_error_msg = 'All promises were rejected';

  /**
   * Convert all iterables to an array to be able to filter them using `.map`.
   * @param {!Iterable<VALUE>} iterable
   * @return {!Array<VALUE>}
   * @template VALUE
   */
  function resolvingArray(iterable) {
    if (iterable instanceof Array) {
      return iterable;
    } else {
      return Array.from(iterable);
    }
  }

  /**
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Promise
   * @param {!Iterable<*>} thenablesOrValues
   * @return {!Promise<*>}
   * @suppress {reportUnknownTypes}
   */
  var polyfill = function(thenablesOrValues) {
    thenablesOrValues = resolvingArray(thenablesOrValues);
    return Promise
        .all(thenablesOrValues.map(function(p) {
          return Promise.resolve(p).then(
              function(val) {
                // One of the promises succeeded.
                // Treat it as a rejection so `Promise.all` immediately bails
                // out.
                throw val;
              },
              function(err) {
                // One of the promises rejected, count that as a resolution so
                // that `Promise.all` will continue to wait for other possible
                // successes.
                return err;
              });
        }))
        .then(
            // If the above 'Promise.all' resolved, then none of the promises
            // succeeded. Return a failed Promise with an AggregateError
            // containing all errors.
            function(errors) {
              throw new AggregateError(errors, aggregate_error_msg);
            },
            // If 'Promise.all' rejected, return a Promise that propagates the
            // succeeded Promise's result.
            function(val) {
              return val;
            });
  };
  return polyfill;
}, 'es_2021', 'es3');
