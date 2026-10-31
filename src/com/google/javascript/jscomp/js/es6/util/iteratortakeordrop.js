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
 * @fileoverview Shared iteration driver for ES2025 Iterator.prototype.take
 * and drop.
 *
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.take
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.drop
 * @suppress {uselessCode}
 */
'require base';
'require es6/util/lazyiterator';
'require es6/util/toiteratorlimit';
'require util/checkisobject';

/**
 * Common implementation for `Iterator.prototype.take` and `drop`.
 *
 * @param {?} iterator
 * @param {?} limit
 * @param {boolean} isDrop
 * @return {!Iterator<VALUE>}
 * @template VALUE
 * @suppress {reportUnknownTypes}
 */
$jscomp.iteratorTakeOrDrop = function(iterator, limit, isDrop) {
  var remaining = $jscomp.toIteratorLimit(iterator, limit);
  var nextMethod = iterator.next;
  return $jscomp.createLazyIterator(iterator, function() {
    if (!isDrop && !remaining--) {
      $jscomp.iteratorCloseNormal(iterator);
      return;
    }
    var nextResult;
    do {
      nextResult = $jscomp.checkIsObject(nextMethod.call(iterator));
      if (nextResult.done) {
        return;
      }
    } while (isDrop && remaining-- > 0);
    return $jscomp.iteratorResult(nextResult.value, false);
  });
};
