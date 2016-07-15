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

'require util/polyfill es6/object/getownpropertysymbols';


$jscomp.polyfill('Reflect.ownKeys', function(orig) {
  if (orig) return orig;

  /**
   * Polyfill for Reflect.ownKeys() method:
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Reflect/ownKeys
   *
   * Returns an array of the object's own property keys.
   *
   * @param {!Object} target
   * @return {!Array<(string|symbol)>}
   */
  var polyfill = function(target) {
    var keys = Object.getOwnPropertyNames(target);
    keys.push.apply(keys, Object.getOwnPropertySymbols(target));
    return keys;
  };
  return polyfill;
}, 'es6', 'es5'); // ES5: Requires Object.getOwnPropertyNames
