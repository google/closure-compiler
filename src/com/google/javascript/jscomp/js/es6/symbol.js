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
 * @noinline
 */
// TODO(rishipal): Remove this function
$jscomp.initSymbol = function() {};

$jscomp.polyfill('Symbol', function(orig) {
  if (orig) return orig;  // no polyfill needed

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


  /**
   * Identifier for this compiled binary.
   * @const {number}
   */
  var BIN_ID = (Math.random() * 1e9) >>> 0;

  /** @const {string} */
  var SYMBOL_PREFIX = 'jscomp_symbol_' + BIN_ID + '_';

  /** @type {number} */
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
        SYMBOL_PREFIX + (opt_description || '') + '_' + counter++,
        opt_description));
  };

  return symbolPolyfill;
}, 'es6', 'es3');

$jscomp.polyfill('Symbol.iterator', function(orig) {
  if (orig) return orig;  // no polyfill needed

  var symbolIterator = Symbol('Symbol.iterator');

  // Polyfill 'Symbol.iterator' onto Array.
  $jscomp.defineProperty(Array.prototype, symbolIterator, {
    configurable: true,
    writable: true,
    /**
     * @this {IArrayLike}
     * @return {!IteratorIterable}
     */
    value: function() {
      return $jscomp.iteratorPrototype($jscomp.arrayIteratorImpl(this));
    }
  });

  return symbolIterator;
}, 'es6', 'es3');

$jscomp.polyfill('Symbol.asyncIterator', function(orig) {
  if (orig) {
    return orig;
  }
  return Symbol('Symbol.asyncIterator');
}, 'es9', 'es3');

$jscomp.polyfill('Symbol.toStringTag', function(orig) {
  if (orig) {
    return orig;
  }
  return Symbol('Symbol.toStringTag');
}, 'es6', 'es3');

/**
 * Returns an iterator with the given `next` method.  Passing
 * all iterators through this function allows easily extending
 * the definition of `%IteratorPrototype%` if methods are ever
 * added to it in the future.
 *
 * @param {function(this: Iterator<T>): T} next
 * @return {!IteratorIterable<T>}
 * @template T
 */
$jscomp.iteratorPrototype = function(next) {
  var iterator = {next: next};
  /**
   * @this {IteratorIterable}
   * @return {!IteratorIterable}
   */
  iterator[Symbol.iterator] = function() {
    return this;
  };
  return /** @type {!IteratorIterable} */ (iterator);
};
