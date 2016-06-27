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

'require util/polyfill';


$jscomp.polyfill('Number.EPSILON', function(orig) {
  /**
   * The difference 1 and the smallest number greater than 1.
   *
   * <p>Polyfills the static field Number.EPSILON.
   */
  return Math.pow(2, -52);
}, 'es6-impl', 'es3');


$jscomp.polyfill('Number.MAX_SAFE_INTEGER', function() {
  /**
   * The maximum safe integer, 2^53 - 1.
   *
   * <p>Polyfills the static field Number.MAX_SAFE_INTEGER.
   */
  return 0x1fffffffffffff;
}, 'es6-impl', 'es3');


$jscomp.polyfill('Number.MIN_SAFE_INTEGER', function() {
  /**
   * The minimum safe integer, -(2^53 - 1).
   *
   * <p>Polyfills the static field Number.MIN_SAFE_INTEGER.
   */
  return -0x1fffffffffffff;
}, 'es6-impl', 'es3');
