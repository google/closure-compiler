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
 * @fileoverview Iteration driver for eager ES2025 Iterator helper methods.
 * @suppress {uselessCode}
 */
'require base';
'require es6/util/iteratorclose';
'require util/checkisobject';

/**
 * Iterates `iterator`, calling `step(value, index)` for each element. If `step`
 * returns true, the iterator is closed normally via `.return()` and iteration
 * stops. Abrupt completion from `step` closes the iterator via IteratorClose.
 *
 * @param {?} iterator
 * @param {?} callback user callback to validate as callable
 * @param {function(?, number): (boolean|undefined)} step
 * @return {void}
 * @suppress {reportUnknownTypes}
 */
$jscomp.iteratorDo = function(iterator, callback, step) {
  $jscomp.checkIsObject(iterator);
  if (typeof callback != 'function') {
    var returnMethod = iterator['return'];
    if (returnMethod != null) {
      try {
        returnMethod.call(iterator);
      } catch (e) {
        // Ignored per ECMA-262 7.4.11 step 5 (IteratorClose):
        // If completion.[[Type]] is throw, return ? completion.
      }
    }
    throw new TypeError();
  }
  var nextMethod = iterator.next;
  var index = 0;
  var /** !IIterableResult<?> */ nextResult;
  while (!(nextResult = $jscomp.checkIsObject(nextMethod.call(iterator))).done) {
    var stop;
    try {
      stop = step(nextResult.value, index++);
    } catch (e) {
      try {
        $jscomp.iteratorClose(iterator, nextResult);
      } catch (ignored) {
        // Ignored per ECMA-262 7.4.11 step 5 (IteratorClose):
        // If completion.[[Type]] is throw, return ? completion.
      }
      throw e;
    }
    if (stop) {
      var returnMethod = iterator['return'];
      if (returnMethod != null) {
        $jscomp.checkIsObject(returnMethod.call(iterator));
      }
      return;
    }
  }
};
