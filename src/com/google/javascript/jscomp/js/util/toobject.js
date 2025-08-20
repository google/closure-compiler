/*
 * Copyright 2025 The Closure Compiler Authors.
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

'require base';

/**
 * Implementation of abstract operation ToObject.
 *
 * Converts `x` to its object representation (e.g. `42` to `new Number(42)`). If
 * `x` is already an object, it is returned as is.
 *
 * Throws a TypeError if `x` is `null` or `undefined`.
 *
 * @param {*} x The value to convert to an object.
 * @return {!Object} The object literal.
 * @see https://tc39.es/ecma262/multipage/abstract-operations.html#sec-toobject
 */
$jscomp.toObject = function(x) {
  if (x == null) {
    throw new TypeError('No nullish arg');
  }
  // The Object constructor is basically toObject but does not throw.
  return Object(x);
};
