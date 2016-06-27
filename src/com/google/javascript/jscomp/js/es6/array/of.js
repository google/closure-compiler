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

'require es6/array/from util/polyfill';

$jscomp.polyfill('Array.of', function(orig) {
  if (orig) return orig;

  /**
   * Creates an array from a fixed set of arguments.
   *
   * <p>Polyfills the static function Array.of().  Does not support
   * constructor inheritance (i.e. (subclass of Array).of).
   *
   * @param {...T} var_args Elements to include in the array.
   * @return {!Array<T>}
   * @template T
   */
  var polyfill = function(var_args) {
    return Array.from(arguments);
  };

  return polyfill;
}, 'es6-impl', 'es3');
