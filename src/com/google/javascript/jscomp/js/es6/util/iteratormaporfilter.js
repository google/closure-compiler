/*
 * Copyright 2026 The Closure Compiler Authors.
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
 * @fileoverview Shared implementation for ES2025 Iterator.prototype.map and
 * Iterator.prototype.filter.
 *
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.map
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.filter
 * @suppress {uselessCode}
 */
'require base';
'require es6/util/lazyiterator';
'require es6/util/validateiteratorcallback';
'require util/checkisobject';

/**
 * Common lazy iterator factory for `Iterator.prototype.map` and `filter`.
 *
 * @param {?} iterator
 * @param {?} callback
 * @param {boolean} isFilter
 * @return {!Iterator<VALUE>}
 * @template VALUE
 * @suppress {reportUnknownTypes}
 */
$jscomp.iteratorMapOrFilter = function(iterator, callback, isFilter) {
  $jscomp.validateIteratorCallback(iterator, callback);
  var nextMethod = iterator.next;
  var counter = 0;
  return $jscomp.createLazyIterator(
      iterator,
      function() {
        while (true) {
          var nextResult = $jscomp.checkIsObject(nextMethod.call(iterator));
          if (nextResult.done) {
            return;
          }
          var value = nextResult.value;
          var selectedOrMapped;
          try {
            selectedOrMapped = callback(value, counter++);
          } catch (e) {
            $jscomp.iteratorCloseAbrupt(iterator);
            throw e;
          }
          if (!isFilter || selectedOrMapped) {
            return $jscomp.iteratorResult(
                isFilter ? value : selectedOrMapped, false);
          }
        }
      });
};
