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
 * @fileoverview Validates that `iterator` is an Object and `callback` is
 * callable per ECMA-262 eager validation rules before iteration begins.
 *
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.map
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.filter
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.flatmap
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.foreach
 * @suppress {uselessCode}
 */
'require base';
'require es6/util/lazyiterator';
'require util/checkisobject';

/**
 * Validates that `iterator` is an Object and `callback` is callable per
 * ECMA-262 eager validation rules before iteration begins.
 *
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.map
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.filter
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.flatmap
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.foreach
 * @param {?} iterator
 * @param {?} callback
 * @return {void}
 * @suppress {reportUnknownTypes}
 */
$jscomp.validateIteratorCallback = function(iterator, callback) {
  $jscomp.checkIsObject(iterator);
  if (typeof callback != 'function') {
    $jscomp.iteratorCloseAbrupt(iterator);
    throw new TypeError('callback must be a function');
  }
};
