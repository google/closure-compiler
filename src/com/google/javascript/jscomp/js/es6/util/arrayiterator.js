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
 * @fileoverview Utility method for creating an iterator for Arrays.
 */
'require base';

/**
 * Returns an internal iterator from the given array.
 * @param {!Array<T>} array
 * @return {function():!IIterableResult<T>}
 * @template T
 */
$jscomp.arrayIteratorImpl = function(array) {
  var index = 0;
  return function() {
    if (index < array.length) {
      return {
        done: false,
        value: array[index++],
      };
    } else {
      return {done: true};
    }
  };
};

/**
 * Returns an internal iterator from the given array.
 * @param {!Array<T>} array
 * @return {!Iterator<T>}
 * @template T
 */
$jscomp.arrayIterator = function(array) {
  return /** @type {!Iterator<T>} */ ({next: $jscomp.arrayIteratorImpl(array)});
};

