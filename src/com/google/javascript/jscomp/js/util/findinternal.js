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

/**
 * @fileoverview Utility for Array methods that find elements.
 */
'require base';

// TODO(sdh): would be nice to template on the ARRAY type as well,
// so that the third arg type of callback can be refined to be
// exactly the same as the array type, but then there's no way to
// enforce that it must, in fact, be an array.
/**
 * Internal implementation of find.
 * @param {!IArrayLike<VALUE>} array
 * @param {function(this: THIS, VALUE, number, !IArrayLike<VALUE>): *} callback
 * @param {THIS} thisArg
 * @return {{i: number, v: (VALUE|undefined)}}
 * @template THIS, VALUE
 */
$jscomp.findInternal = function(array, callback, thisArg) {
  if (array instanceof String) {
    array = /** @type {!IArrayLike} */ (String(array));
  }
  var len = array.length;
  for (var i = 0; i < len; i++) {
    var value = array[i];
    if (callback.call(thisArg, value, i, array)) return {i: i, v: value};
  }
  return {i: -1, v: void 0};
};
