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
 * @fileoverview Polyfill for array destructuring.
 */
'require base';


/**
 * Copies the values from an Iterator into an Array. The important difference
 * between this and $jscomp.arrayFromIterable is that if the iterator's
 * next() method has already been called one or more times, this method returns
 * only the values that haven't been yielded yet.
 * @param {!Iterator<T>} iterator
 * @return {!Array<T>}
 * @template T
 */
$jscomp.arrayFromIterator = function(iterator) {
  var i;
  var arr = [];
  while (!(i = iterator.next()).done) {
    arr.push(i.value);
  }
  return arr;
};
