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

'require util/polyfill';
'require es6/promise/promise';

$jscomp.polyfill('Promise.prototype.finally', function(orig) {
  if (orig) return orig;

  /**
   * @this {!Promise<VALUE>}
   * @param {function():?} onFinally
   * @return {!Promise<VALUE>}
   * @template VALUE
   * @suppress {reportUnknownTypes}
   */
  var polyfill = function(onFinally) {
    return this.then(
        function(value) {
          var promise = Promise.resolve(onFinally());
          return promise.then(function () { return value; });
        },
        function(reason) {
          var promise = Promise.resolve(onFinally());
          return promise.then(function () { throw reason; });
        });
  };

  return polyfill;
}, 'es9', 'es3');
