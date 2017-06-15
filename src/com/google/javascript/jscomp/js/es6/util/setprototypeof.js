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

/**
 * @fileoverview
 * @suppress {uselessCode}
 */

/**
 * @param {!Object} target
 * @param {!Object} proto
 * @return {boolean} whether the prototype was able to be set
 */
$jscomp.setPrototypeOf = function(target, proto) {
  if (Object.setPrototypeOf) {
    Object.setPrototypeOf(target, proto);
    return true;
  }

  // IE<11 has no way to polyfill this, so don't even try.
  if (typeof ''.__proto__ != 'object') {
    return false;
  }

  target.__proto__ = proto;
  if (target.__proto__ !== proto) {
    throw new TypeError(target + ' is not extensible');
  }
  return true;
};
