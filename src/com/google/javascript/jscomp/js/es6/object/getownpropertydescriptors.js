/*
 * Copyright 2017 The Closure Compiler Authors.
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

'require es6/reflect/ownkeys';
'require util/polyfill';

$jscomp.polyfill('Object.getOwnPropertyDescriptors', function(orig) {
  if (orig) return orig;

  /**
   * Polyfills Object.getOwnPropertyDescriptors.
   *
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/entries
   *
   * @param {!Object} obj
   * @return {!Object<!ObjectPropertyDescriptor>}
   */
  var getOwnPropertyDescriptors = function(obj) {
    var result = {};
    var keys = Reflect.ownKeys(obj);
    for (var i = 0; i < keys.length; i++) {
      result[keys[i]] = Object.getOwnPropertyDescriptor(obj, keys[i]);
    }
    return result;
  };

  return getOwnPropertyDescriptors;
}, 'es8', 'es5');
