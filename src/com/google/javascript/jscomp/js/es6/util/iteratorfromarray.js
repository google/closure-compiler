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
 * @fileoverview Utilities for iterator-returning methods.
 */
'require es6/symbol';


$jscomp.array = $jscomp.array || {};


/**
 * Creates an iterator from an array-like, with a transformation function.
 * @param {!IArrayLike<INPUT>} array
 * @param {function(number, INPUT): OUTPUT} transform
 * @return {!IteratorIterable<OUTPUT>}
 * @template INPUT, OUTPUT
 * @suppress {checkTypes}
 */
$jscomp.iteratorFromArray = function(array, transform) {
  $jscomp.initSymbolIterator();
  // NOTE: IE8 doesn't support indexing from boxed Strings.
  if (array instanceof String) array = array + '';
  var i = 0;
  var iter = {
    next: function() {
      if (i < array.length) {
        var index = i++;
        return {value: transform(index, array[index]), done: false};
      }
      iter.next = function() { return {done: true, value: void 0}; };
      return iter.next();
    }
  };
  iter[Symbol.iterator] = function() { return iter; };
  return iter;
};
