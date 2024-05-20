/*
 * Copyright 2024 The Closure Compiler Authors.
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
 * @fileoverview Promise.withResolvers polyfill.
 * @suppress {uselessCode}
 */

'require util/polyfill';
'require es6/promise/promise';

$jscomp.polyfill('Promise.withResolvers', function(orig) {
  if (orig) return orig;

  /**
   * @return {!Promise.PromiseWithResolvers<VALUE>}
   * @template VALUE
   */
  var polyfill = function() {
    var resolve;
    var reject;
    var promise = new Promise(function(res, rej) {
      resolve = res;
      reject = rej;
    });
    return {promise: promise, resolve: resolve, reject: reject};
  };

  return polyfill;

  /* expected to be part of es_2024, but that's not yet a featureset */
}, 'es_next', 'es3');
