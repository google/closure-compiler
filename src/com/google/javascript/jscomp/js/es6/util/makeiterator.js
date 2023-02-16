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
 * @fileoverview Polyfill for for-of loops.
 */
'require es6/util/arrayiterator';

/**
 * Creates an iterator for the given iterable.  This iterator should never
 * be exposed to user code.
 *
 * @param {string|!Iterable<T>|!Iterator<T>|!Arguments} iterable
 * @return {!Iterator<T>}
 * @template T
 * @suppress {reportUnknownTypes}
 */
$jscomp.makeIterator = function(iterable) {
  // NOTE: Disabling typechecking because [] not allowed on @struct.
  var iteratorFunction = typeof Symbol != 'undefined' && Symbol.iterator &&
      (/** @type {?} */ (iterable)[Symbol.iterator]);
  if (iteratorFunction) {
    return iteratorFunction.call(iterable);
  }
  if (typeof iterable['length'] == 'number') {
    return $jscomp.arrayIterator(/** @type {!Array} */ (iterable));
  }
  throw new Error(String(iterable) + ' is not an iterable or ArrayLike');
};
