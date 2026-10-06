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
 * @fileoverview Polyfill for for-of loop IteratorClose.
 * @suppress {uselessCode}
 */
'require base';

/**
 * Closes an iterator upon abrupt completion of a for-of loop.
 * If the iteration result is not done, calls the iterator's `return` method
 * if one exists.
 *
 * @param {?} iterator
 * @param {?} iterResult
 * @return {void}
 * @suppress {reportUnknownTypes}
 */
$jscomp.iteratorClose = function(iterator, iterResult) {
  var returnMethod;
  if (iterResult && !iterResult.done && (returnMethod = iterator['return'])) {
    returnMethod.call(iterator);
  }
};
