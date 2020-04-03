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

/**
 * @fileoverview Symbol polyfill.
 * @suppress {uselessCode}
 */

'require es6/util/arrayiterator';
'require util/defineproperty';
'require util/global';
'require util/polyfill';

/**
 * Initializes the Symbol function.
 * @suppress {reportUnknownTypes}
 * @noinline
 */
// TODO(rishipal): Remove this function
$jscomp.initSymbol = function() {};

$jscomp.polyfill('Symbol', function(orig) {
  if (orig) return orig;  // no polyfill needed
  $jscomp.initSymbol();   // to ensure initSymbol isn't removed as some tests
                          // rely on it.

  /**
   * @struct @constructor
   * @param {string} id
   * @param {string=} opt_description
   */
  var SymbolClass = function(id, opt_description) {
    /** @private @const {string} */
    this.$jscomp$symbol$id_ = id;

    /** @const {string|undefined} */
    this.description;

    // description is read-only.
    $jscomp.defineProperty(
        this, 'description',
        {configurable: true, writable: true, value: opt_description});
  };


  /** @override */
  SymbolClass.prototype.toString = function() {
    return this.$jscomp$symbol$id_;
  };


  /** @const {string} */
  var SYMBOL_PREFIX = 'jscomp_symbol_';

  var counter = 0;

  /**
   * Produces "symbols" (actually just unique strings).
   * @param {string=} opt_description
   * @return {!SymbolClass}
   * @this {?Object}
   */
  var symbolPolyfill = function(opt_description) {
    if (this instanceof symbolPolyfill) {
      throw new TypeError('Symbol is not a constructor');
    }
    return (new SymbolClass(
        SYMBOL_PREFIX + (opt_description || '') + '_' + (counter++),
        opt_description));
  };

  return symbolPolyfill;
}, 'es6', 'es3');

/**
 * Initializes Symbol.iterator (if it's not already defined) and adds a
 * Symbol.iterator property to the Array prototype.
 * @suppress {reportUnknownTypes}
 */
$jscomp.initSymbolIterator = function() {
  var symbolIterator = $jscomp.global['Symbol'].iterator;
  if (!symbolIterator) {
    symbolIterator = $jscomp.global['Symbol'].iterator =
        $jscomp.global['Symbol']('Symbol.iterator');
  }

  if (typeof Array.prototype[symbolIterator] != 'function') {
    $jscomp.defineProperty(Array.prototype, symbolIterator, {
      configurable: true,
      writable: true,
      /**
       * @this {Array}
       * @return {!IteratorIterable}
       */
      value: function() {
        return $jscomp.iteratorPrototype($jscomp.arrayIteratorImpl(this));
      }
    });
  }

  // Only need to do this once. All future calls are no-ops.
  $jscomp.initSymbolIterator = function() {};
};


/**
 * Initializes Symbol.asyncIterator (if it's not already defined)
 * @suppress {reportUnknownTypes}
 */
$jscomp.initSymbolAsyncIterator = function() {
  var symbolAsyncIterator = $jscomp.global['Symbol'].asyncIterator;
  if (!symbolAsyncIterator) {
    symbolAsyncIterator = $jscomp.global['Symbol'].asyncIterator =
        $jscomp.global['Symbol']('Symbol.asyncIterator');
  }

  // Only need to do this once. All future calls are no-ops.
  $jscomp.initSymbolAsyncIterator = function() {};
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
  iterator[$jscomp.global['Symbol'].iterator] = function() {
    return this;
  };
  return /** @type {!IteratorIterable} */ (iterator);
};
