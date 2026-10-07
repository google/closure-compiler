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
 * @fileoverview Polyfill for the ES2025 Iterator.from static method.
 */
'require es6/iterator';
'require es6/symbol';
'require es6/util/arrayiterator';
'require util/checkisobject';
'require util/objectcreate';
'require util/polyfill';

$jscomp.polyfill('Iterator.from', function(orig) {
  if (orig) return orig;

  /**
   * Prototype for wrapped valid iterators (%WrapForValidIteratorPrototype%).
   * Its prototype is Iterator.prototype.
   *
   * @see https://tc39.es/ecma262/#sec-%wrapforvaliditeratorprototype%-object
   * @type {!Object}
   */
  var wrapForValidIteratorPrototype = $jscomp.objectCreate(Iterator.prototype);

  wrapForValidIteratorPrototype.next = function() {
    return this.nextMethod_.call(this.iterator_);
  };

  wrapForValidIteratorPrototype.return = function() {
    var iterator = this.iterator_;
    var returnMethod = iterator.return;
    if (returnMethod != null) {
      if (typeof returnMethod !== 'function') {
        throw new TypeError('iterator.return is not a function');
      }
      return returnMethod.call(iterator);
    }
    return {value: void 0, done: true};
  };

  /**
   * Implements GetIteratorFlattenable(O, iterate-strings) and wraps the result
   * in %WrapForValidIteratorPrototype% if it is not already an Iterator
   * instance.
   *
   * @see https://tc39.es/ecma262/#sec-iterator.from
   * @see https://tc39.es/ecma262/#sec-getiteratorflattenable
   *
   * @param {!Iterable<T>|!IteratorLike<T>|string} iterable
   * @return {!Iterator<T>}
   * @template T
   * @suppress {reportUnknownTypes}
   */
  var from = function(iterable) {
    if (typeof iterable != 'string') $jscomp.checkIsObject(iterable);

    var /** !IteratorLike<T> */ iterator;
    var iteratorMethod = /** @type {?} */ (iterable)[Symbol.iterator];
    if (iteratorMethod != null) {
      if (typeof iteratorMethod !== 'function') {
        throw new TypeError('[Symbol.iterator] is not a function');
      }
      iterator = iteratorMethod.call(iterable);
      $jscomp.checkIsObject(iterator);
    } else if (typeof iterable === 'string' || iterable instanceof String) {
      // In ES3/ES5 or when Symbol.iterator is polyfilled, String.prototype
      // lacks a Symbol.iterator property. Per GetIteratorFlattenable spec,
      // strings (both primitives and String wrapper objects) are explicitly
      // converted to iterators here.
      // Note: when Symbol is polyfilled, arrayIteratorImpl yields UTF-16 code
      // units rather than unicode code points (same limitation as $jscomp.makeIterator).
      return /** @type {!Iterator<T>} */ ($jscomp.iteratorPrototype(
          $jscomp.arrayIteratorImpl(
              /** @type {!IArrayLike<?>} */ (/** @type {?} */ (iterable)))));
    } else {
      iterator = /** @type {!IteratorLike<T>} */ (iterable);
    }

    var nextMethod = iterator.next;

    if (iterator instanceof Iterator) {
      return iterator;
    }

    var wrapped = $jscomp.objectCreate(wrapForValidIteratorPrototype);
    wrapped.iterator_ = iterator;
    wrapped.nextMethod_ = nextMethod;
    return /** @type {!Iterator<T>} */ (wrapped);
  };

  return from;
}, 'es_next', 'es3');
