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


$jscomp.polyfill('Reflect.apply', function(orig) {
  if (orig) return orig;
  var apply = Function.prototype.apply;

  /**
   * Polyfill for Reflect.apply() method:
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Reflect/apply
   *
   * Calls a target function with arguments as specified, just
   * as Function.prototype.apply.
   *
   * @param {function(this: THIS, ...*): RESULT} target The function to call.
   * @param {THIS} thisArg The 'this' argument.
   * @param {!Array} argList The arguments as a list.
   * @return {RESULT} The result of the function call.
   * @template THIS, RESULT
   */
  var polyfill = function(target, thisArg, argList) {
    return apply.call(target, thisArg, argList);
  };
  return polyfill;
}, 'es6', 'es3');
