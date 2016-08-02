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

'require util/defineproperty';
'require util/global';

/** @const {string} */
$jscomp.SYMBOL_PREFIX = 'jscomp_symbol_';


/**
 * Initializes the Symbol function.
 * @suppress {reportUnknownTypes}
 */
$jscomp.initSymbol = function() {
  // Only need to do this once. All future calls are no-ops.
  $jscomp.initSymbol = function() {};

  if (!$jscomp.global.Symbol) {
    $jscomp.global.Symbol = $jscomp.Symbol;
  }
};


/** @private {number} */
$jscomp.symbolCounter_ = 0;


/**
 * Produces "symbols" (actually just unique strings).
 * @param {string=} opt_description
 * @return {symbol}
 * @suppress {reportUnknownTypes}
 */
$jscomp.Symbol = function(opt_description) {
  return /** @type {symbol} */ (
      $jscomp.SYMBOL_PREFIX + (opt_description || '') + ($jscomp.symbolCounter_++));
};


/**
 * Initializes Symbol.iterator (if it's not already defined) and adds a
 * Symbol.iterator property to the Array prototype.
 * @suppress {reportUnknownTypes}
 */
$jscomp.initSymbolIterator = function() {
  $jscomp.initSymbol();
  var symbolIterator = $jscomp.global.Symbol.iterator;
  if (!symbolIterator) {
    symbolIterator = $jscomp.global.Symbol.iterator =
        $jscomp.global.Symbol('iterator');
  }

  if (typeof Array.prototype[symbolIterator] != 'function') {
    $jscomp.defineProperty(
        Array.prototype, symbolIterator, {
          configurable: true,
          writable: true,
          /**
           * @this {Array}
           * @return {!IteratorIterable}
           */
          value: function() {
            return $jscomp.arrayIterator(this);
          }
        });
  }

  // Only need to do this once. All future calls are no-ops.
  $jscomp.initSymbolIterator = function() {};
};


/**
 * Returns an iterator from the given array.
 * @param {!Array<T>} array
 * @return {!IteratorIterable<T>}
 * @template T
 */
$jscomp.arrayIterator = function(array) {
  var index = 0;
  return $jscomp.iteratorPrototype(function() {
    if (index < array.length) {
      return {
        done: false,
        value: array[index++],
      };
    } else {
      return {done: true};
    }
  });
};


/**
 * Returns an iterator with the given `next` method.  Passing
 * all iterators through this function allows easily extending
 * the definition of `%IteratorPrototype%` if methods are ever
 * added to it in the future.
 *
 * @param {function(this: Iterator<T>): T} next
 * @return {!IteratorIterable<T>}
 * @template T
 * @suppress {reportUnknownTypes}
 */
$jscomp.iteratorPrototype = function(next) {
  $jscomp.initSymbolIterator();

  var iterator = {next: next};
  /**
   * @this {IteratorIterable}
   * @return {!IteratorIterable}
   */
  iterator[$jscomp.global.Symbol.iterator] = function() { return this; };
  return /** @type {!IteratorIterable} */ (iterator);
};
