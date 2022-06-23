/*
 * Copyright 2022 The Closure Compiler Authors.
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

$jscomp.polyfill('Object.hasOwn', function(orig) {
  if (orig) {
    return orig;
  }

  /**
   *
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/hasOwn
   *
   * Object.hasOwn() is a replacement for Object.hasOwnProperty()
   *
   * @param {!Object} object
   * @param {string} property
   * @return {boolean} true if object has own exsisting property and not
   *     inhereted
   */

  var hasOwn = function(object, property) {
    return Object.prototype.hasOwnProperty.call(object, property);
  };

  return hasOwn;
}, 'es_next', 'es3');
