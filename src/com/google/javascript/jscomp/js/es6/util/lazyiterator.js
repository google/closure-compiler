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
 * @fileoverview Shared runtime utilities and lazy iterator factory for ES2025
 * Iterator helper methods.
 * @suppress {uselessCode}
 */
'require base';
'require es6/iterator';
'require es6/symbol';
'require util/checkisobject';
'require util/objectcreate';

/**
 * Closes `iterator` on abrupt (throw) completion per ECMA-262 IteratorClose,
 * suppressing any secondary error thrown while retrieving or calling `return`.
 *
 * Kept separate from `$jscomp.iteratorClose` (`es6/util/iteratorclose`) because
 * transpiled `for-of` loops invoke `$jscomp.iteratorClose` on non-throw exits
 * (`break` and `return`), where exceptions from `return()` must propagate.
 *
 * @see https://tc39.es/ecma262/#sec-iteratorclose
 * @param {?} iterator
 * @return {void}
 * @suppress {reportUnknownTypes}
 */
$jscomp.iteratorCloseAbrupt = function(iterator) {
  try {
    var returnMethod = iterator['return'];
    if (typeof returnMethod == 'function') {
      returnMethod.call(iterator);
    }
  } catch (ignored) {
    // Ignored per ECMA-262 7.4.11 step 5 (IteratorClose):
    // If completion is a throw completion, return ? completion.
  }
};

/**
 * Closes `iterator` on normal/return completion per ECMA-262 IteratorClose,
 * propagating exceptions and validating that `return()` returns an Object.
 *
 * @see https://tc39.es/ecma262/#sec-iteratorclose
 * @param {?} iterator
 * @return {void}
 * @suppress {reportUnknownTypes}
 */
$jscomp.iteratorCloseNormal = function(iterator) {
  var returnMethod = iterator['return'];
  if (returnMethod != null) {
    if (typeof returnMethod != 'function') {
      throw new TypeError('Iterator return is not a function');
    }
    $jscomp.checkIsObject(returnMethod.call(iterator));
  }
};


/**
 * Creates an iterator result object per ECMA-262 CreateIterResultObject.
 *
 * @see https://tc39.es/ecma262/#sec-createiterresultobject
 * @param {VALUE=} opt_value
 * @param {boolean=} opt_done
 * @return {!IIterableResult<VALUE>}
 * @template VALUE
 * @suppress {reportUnknownTypes}
 */
$jscomp.iteratorResult = function(opt_value, opt_done) {
  if (opt_done == null) {
    opt_done = true;
  }
  return {value: opt_value, done: opt_done};
};

/** @const {number} */
$jscomp.ITERATOR_STATE_SUSPENDED_START = 1;

/** @const {number} */
$jscomp.ITERATOR_STATE_SUSPENDED_YIELD = 2;

/** @const {number} */
$jscomp.ITERATOR_STATE_EXECUTING = 3;

/** @const {number} */
$jscomp.ITERATOR_STATE_COMPLETED = 4;

/**
 * Validates that `iterator` is a valid lazy iterator instance and not currently
 * executing, returning its current lifecycle state.
 *
 * @see https://tc39.es/ecma262/#sec-%iteratorhelperprototype%.next
 * @see https://tc39.es/ecma262/#sec-%iteratorhelperprototype%.return
 * @param {?} iterator
 * @return {number}
 * @suppress {reportUnknownTypes}
 */
$jscomp.getLazyIteratorState = function(iterator) {
  if (!iterator || iterator.self_ !== iterator) {
    throw new TypeError('Method called on incompatible object');
  }
  var state = iterator.state_;
  if (state === $jscomp.ITERATOR_STATE_EXECUTING) {
    throw new TypeError('Generator is already running');
  }
  return state;
};

/**
 * Lazily initialized `%IteratorHelperPrototype%` shared by all lazy Iterator
 * instances. Initialized on first call to `$jscomp.createLazyIterator` so this
 * runtime library has no top-level side effects and can be completely
 * dead-code-eliminated when unused.
 *
 * @see https://tc39.es/ecma262/#sec-%iteratorhelperprototype%-object
 * @type {?Object}
 */
$jscomp.lazyIteratorPrototype = null;

/**
 * Creates a lazy Iterator instance inheriting from
 * `%IteratorHelperPrototype%` (whose prototype is `Iterator.prototype`).
 * `stepFn` returns `{value: v, done: false}` to yield, or `undefined` when
 * exhausted.
 *
 * @see https://tc39.es/ecma262/#sec-%iteratorhelperprototype%-object
 * @see https://tc39.es/ecma262/#sec-%iteratorhelperprototype%.next
 * @see https://tc39.es/ecma262/#sec-%iteratorhelperprototype%.return
 * @param {?} iterator The underlying iterator to close on `.return()`.
 * @param {function(): (!IIterableResult<T>|undefined)} stepFn
 * @return {!Iterator<T>}
 * @template T
 * @suppress {reportUnknownTypes}
 */
$jscomp.createLazyIterator = function(iterator, stepFn) {
  var prototype = $jscomp.lazyIteratorPrototype;
  if (!prototype) {
    /**
     * @param {?} helper
     * @param {boolean} isReturn
     * @return {!IIterableResult<?>}
     */
    var resume = function(helper, isReturn) {
      var state = $jscomp.getLazyIteratorState(helper);
      if (state != $jscomp.ITERATOR_STATE_COMPLETED) {
        helper.state_ =
            isReturn && state == $jscomp.ITERATOR_STATE_SUSPENDED_START ?
            $jscomp.ITERATOR_STATE_COMPLETED :
            $jscomp.ITERATOR_STATE_EXECUTING;
        try {
          if (isReturn) {
            $jscomp.iteratorCloseNormal(helper.iter_);
          } else {
            var res = helper.step_();
            if (res) {
              helper.state_ = $jscomp.ITERATOR_STATE_SUSPENDED_YIELD;
              return res;
            }
          }
        } finally {
          if (helper.state_ != $jscomp.ITERATOR_STATE_SUSPENDED_YIELD) {
            helper.state_ = $jscomp.ITERATOR_STATE_COMPLETED;
            helper.step_ = helper.iter_ = null;
          }
        }
      }
      return $jscomp.iteratorResult();
    };
    prototype = $jscomp.lazyIteratorPrototype =
        $jscomp.objectCreate(Iterator.prototype);
    prototype.next = /** @this {?} */ function() {
      return resume(this, false);
    };
    prototype.return = /** @this {?} */ function() {
      return resume(this, true);
    };
    // https://tc39.es/ecma262/#sec-%iteratorhelperprototype%-@@tostringtag
    prototype[Symbol.toStringTag] = 'Iterator Helper';
  }
  var lazyIterator = $jscomp.objectCreate(prototype);
  lazyIterator.self_ = lazyIterator;
  lazyIterator.state_ = $jscomp.ITERATOR_STATE_SUSPENDED_START;
  lazyIterator.iter_ = iterator;
  lazyIterator.step_ = stepFn;
  return /** @type {!Iterator<T>} */ (lazyIterator);
};
