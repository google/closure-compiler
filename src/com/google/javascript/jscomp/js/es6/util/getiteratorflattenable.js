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
 * @fileoverview Shared GetIteratorFlattenable abstract operation for ES2025
 * Iterator.from and Iterator.prototype.flatMap.
 *
 * @see https://tc39.es/ecma262/#sec-getiteratorflattenable
 * @suppress {uselessCode}
 */
'require base';
'require es6/symbol';
'require es6/util/arrayiterator';
'require util/checkisobject';

/**
 * Implements ECMA-262 `GetIteratorFlattenable(obj, primitiveHandling)`,
 * returning the underlying iterator object before `GetIteratorDirect` reads
 * `.next`.
 *
 * When `opt_allowStrings` is true (`iterate-strings` in `Iterator.from`),
 * primitive strings are accepted alongside Objects; otherwise
 * (`reject-primitives` in `Iterator.prototype.flatMap`), all primitives
 * including primitive strings are rejected with a `TypeError`.
 *
 * @see https://tc39.es/ecma262/#sec-getiteratorflattenable
 * @param {?} obj
 * @param {boolean=} opt_allowStrings
 * @return {!IteratorLike<T>}
 * @template T
 * @suppress {reportUnknownTypes}
 */
$jscomp.getIteratorFlattenable = function(obj, opt_allowStrings) {
  if (!opt_allowStrings || typeof obj != 'string') {
    $jscomp.checkIsObject(obj);
  }
  var iteratorMethod = /** @type {?} */ (obj)[Symbol.iterator];
  if (iteratorMethod != null) {
    if (typeof iteratorMethod != 'function') {
      throw new TypeError('[Symbol.iterator] is not a function');
    }
    return $jscomp.checkIsObject(iteratorMethod.call(obj));
  }
  if (typeof obj == 'string' || obj instanceof String) {
    // In ES3/ES5 or when Symbol.iterator is polyfilled, String.prototype lacks
    // a Symbol.iterator property. Per GetIteratorFlattenable, strings (both
    // primitives when opt_allowStrings is true, and boxed String wrapper
    // objects) are explicitly converted to iterators here.
    // Note: when Symbol is polyfilled, arrayIteratorImpl yields UTF-16 code
    // units rather than unicode code points (same limitation as
    // $jscomp.makeIterator).
    return $jscomp.iteratorPrototype(
        $jscomp.arrayIteratorImpl(
            /** @type {!IArrayLike<?>} */ (/** @type {?} */ (obj))));
  }
  return /** @type {!IteratorLike<T>} */ (obj);
};
