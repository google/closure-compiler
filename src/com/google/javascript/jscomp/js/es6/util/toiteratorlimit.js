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
 * @fileoverview Validates that `iterator` is an Object and converts `limit`
 * to a non-negative integer (or +Infinity) per ECMA-262.
 *
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.take
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.drop
 * @suppress {uselessCode}
 */
'require base';
'require util/checkisobject';
'require util/tointegerorinfinity';

/**
 * Validates that `iterator` is an Object and converts `limit` to a non-negative
 * integer (or +Infinity) per ECMA-262 `Iterator.prototype.take` / `drop`.
 *
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.take
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.drop
 * @param {?} iterator
 * @param {?} limit
 * @return {number}
 * @suppress {reportUnknownTypes}
 */
$jscomp.toIteratorLimit = function(iterator, limit) {
  $jscomp.checkIsObject(iterator);
  var numLimit = +limit;
  if (numLimit !== numLimit ||
      (numLimit > 9007199254740991 && numLimit < Infinity)) {
    throw new RangeError('Limit must be a non-negative number');
  }
  var integerLimit = $jscomp.toIntegerOrInfinity(numLimit);
  if (integerLimit < 0) {
    throw new RangeError('Limit must be a non-negative number');
  }
  return integerLimit;
};
