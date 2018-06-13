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

'require base';

/**
 * Definition for object reflection.
 *
 * Internal compiler version of closure library goog.reflect.object.
 *
 * Use this if you have an object literal whose keys need to have the same names
 * as the properties of some class even after they are renamed by the compiler.
 *
 * @param {?Object} type class, interface, or record
 * @param {T} object Object literal whose properties must be renamed
 *     consistently with type
 * @return {T} The object literal.
 * @template T
 */
$jscomp.reflectObject = function(type, object) {
  return object;
};
