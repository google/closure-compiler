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
 * @fileoverview Polyfills for ES6 Array functions.
 */

$jscomp.array = $jscomp.array || {};


/**
 * Returns a constant "done" value signaling that iteration is over.
 * @return {{done: boolean}}
 * @private
 */
$jscomp.array.done_ = function() {
  return {done: true, value: void 0};
};


/**
 * Use a special function since this isn't actually
 * supposed to be a generator.
 * @param {!IArrayLike<INPUT>} array
 * @param {function(number, INPUT): OUTPUT} func
 * @return {!IteratorIterable<OUTPUT>}
 * @private
 * @template INPUT, OUTPUT
 * @suppress {checkTypes}
 */
$jscomp.array.arrayIterator_ = function(array, func) {
  // NOTE: IE8 doesn't support indexing from boxed Strings.
  if (array instanceof String) array = String(array);
  let i = 0;
  const iter = {
    next() {
      if (i < array.length) {
        const index = i++;
        return {value: func(index, array[index]), done: false};
      }
      iter.next = $jscomp.array.done_;
      return $jscomp.array.done_();  // Note: fresh instance every time
    },
    [Symbol.iterator]() { return iter; },
  };
  return iter;
};


// TODO(sdh): would be nice to template on the ARRAY type as well,
// so that the third arg type of callback can be refined to be
// exactly the same as the array type, but then there's no way to
// enforce that it must, in fact, be an array.
/**
 * Internal implementation of find.
 * @param {!IArrayLike<VALUE>} array
 * @param {function(this: THIS, VALUE, number, !IArrayLike<VALUE>): *} callback
 * @param {THIS} thisArg
 * @return {{i: number, v: (VALUE|undefined)}}
 * @private
 * @template THIS, VALUE
 */
$jscomp.array.findInternal_ = function(array, callback, thisArg) {
  if (array instanceof String) {
    array = /** @type {!IArrayLike} */ (String(array));
  }
  const len = array.length;
  for (let i = 0; i < len; i++) {
    const value = array[i];
    if (callback.call(thisArg, value, i, array)) return {i: i, v: value};
  }
  return {i: -1, v: void 0};
};


/**
 * Creates a new Array from an array-like or iterable object.
 *
 * <p>Polyfills the static function Array.from().  Does not support
 * constructor inheritance (i.e. (subclass of Array).from), and
 * relies on the compiler to check the validity of inputs rather
 * than producing spec-compliant TypeErrors.
 *
 * @param {!IArrayLike<INPUT>|!Iterator<INPUT>|!Iterable<INPUT>}
 *     arrayLike An array-like or iterable.
 * @param {(function(this: THIS, INPUT): OUTPUT)=} opt_mapFn
 *     Function to call on each argument.
 * @param {THIS=} opt_thisArg
 *     Object to use as 'this' when calling mapFn.
 * @return {!Array<OUTPUT>}
 * @template INPUT, OUTPUT, THIS
 */
$jscomp.array.from = function(
    arrayLike, opt_mapFn = (x => x), opt_thisArg = void 0) {
  const result = [];
  if (arrayLike[Symbol.iterator]) {
    const iter = arrayLike[Symbol.iterator]();
    let next;
    while (!(next = iter.next()).done) {
      result.push(opt_mapFn.call(opt_thisArg, next.value));
    }
  } else {
    const len = arrayLike.length;  // need to support non-iterables
    for (let i = 0; i < len; i++) {
      result.push(opt_mapFn.call(opt_thisArg, arrayLike[i]));
    }
  }
  return result;
};


/**
 * Creates an array from a fixed set of arguments.
 *
 * <p>Polyfills the static function Array.of().  Does not support
 * constructor inheritance (i.e. (subclass of Array).of).
 *
 * @param {...*} elements Elements to include in the array.
 * @return {!Array<*>}
 * TODO(tbreisacher): Put back the at-template type after b/26884264 is fixed.
 */
$jscomp.array.of = function(...elements) {
  return $jscomp.array.from(elements);
};


/**
 * Returns an iterator of [key, value] arrays, one for each entry
 * in the given array.
 *
 * @this {!IArrayLike<VALUE>}
 * @return {!IteratorIterable<!Array<number|VALUE>>}
 * @template VALUE
 */
$jscomp.array.entries = function() {
  return $jscomp.array.arrayIterator_(this, (i, v) => [i, v]);
};


/**
 * Installs non-enumberable methods
 * @param {string} method
 * @param {!Function} fn
 * @private
 */
$jscomp.array.installHelper_ = function(method, fn) {
  // Don't set Array.prototype values in IE8.
  // NOTE: Object.defineProperties doesn't exist on IE8, and while
  // Object.defineProperty does it works for DOM objects.
  if (!Array.prototype[method] && Object.defineProperties &&
      Object.defineProperty) {
    Object.defineProperty(
        Array.prototype, method,
        {configurable: true, enumerable: false, writable: true, value: fn});
  }
};


/**
 * Installs the Array.prototype.entries polyfill.
 * @suppress {const,checkTypes}
 */
$jscomp.array.entries$install = function() {
  $jscomp.array.installHelper_('entries', $jscomp.array.entries);
};


/**
 * Returns an iterator of keys of the given array.
 *
 * @this {!IArrayLike}
 * @return {!IteratorIterable<number>}
 */
$jscomp.array.keys = function() {
  return $jscomp.array.arrayIterator_(this, i => i);
};


/**
 * Installs the Array.prototype.keys polyfill.
 * @suppress {const,checkTypes}
 */
$jscomp.array.keys$install = function() {
  $jscomp.array.installHelper_('keys', $jscomp.array.keys);
};


/**
 * Returns an iterator of values of the given array.
 *
 * @this {!IArrayLike<VALUE>}
 * @return {!IteratorIterable<VALUE>}
 * @template VALUE
 */
$jscomp.array.values = function() {
  return $jscomp.array.arrayIterator_(this, (_, v) => v);
};


/**
 * Installs the Array.prototype.values polyfill.
 * @suppress {const,checkTypes}
 */
$jscomp.array.values$install = function() {
  $jscomp.array.installHelper_('values', $jscomp.array.values);
};


/**
 * Copies elements from one part of the array to another.
 *
 * @this {!IArrayLike<VALUE>}
 * @param {number} target Start index to copy elements to.
 * @param {number} start Start index to copy elements from.
 * @param {number=} opt_end Index from which to end copying.
 * @return {!IArrayLike<VALUE>} The array, with the copy performed in-place.
 * @template VALUE
 */
$jscomp.array.copyWithin = function(target, start, opt_end = void 0) {
  const len = this.length;
  target = Number(target);
  start = Number(start);
  opt_end = Number(opt_end != null ? opt_end : len);
  if (target < start) {
    opt_end = Math.min(opt_end, len);
    while (start < opt_end) {
      if (start in this) {
        this[target++] = this[start++];
      } else {
        delete this[target++];
        start++;
      }
    }
  } else {
    opt_end = Math.min(opt_end, len + start - target);
    target += opt_end - start;
    while (opt_end > start) {
      if (--opt_end in this) {
        this[--target] = this[opt_end];
      } else {
        delete this[target];
      }
    }
  }
  return this;
};


/**
 * Installs the Array.prototype.copyWithin polyfill./
 * @suppress {const,checkTypes}
 */
$jscomp.array.copyWithin$install = function() {
  $jscomp.array.installHelper_('copyWithin', $jscomp.array.copyWithin);
};


/**
 * Fills elements of an array with a constant value.
 *
 * @this {!IArrayLike<VALUE>}
 * @param {VALUE} value Value to fill.
 * @param {number=} opt_start Start index, or zero if omitted.
 * @param {number=} opt_end End index, or length if omitted.
 * @return {!IArrayLike<VALUE>} The array, with the fill performed in-place.
 * @template VALUE
 */
$jscomp.array.fill = function(value, opt_start = 0, opt_end = void 0) {
  if (opt_end == null || !value.length) opt_end = this.length || 0;
  opt_end = Number(opt_end);
  for (let i = Number(opt_start || 0); i < opt_end; i++) {
    this[i] = value;
  }
  return this;
};


/**
 * Installs the Array.prototype.fill polyfill.
 * @suppress {const,checkTypes}
 */
$jscomp.array.fill$install = function() {
  $jscomp.array.installHelper_('fill', $jscomp.array.fill);
};


/**
 * Finds and returns an element that satisfies the given predicate.
 *
 * @this {!IArrayLike<VALUE>}
 * @param {function(this: THIS, VALUE, number, !IArrayLike<VALUE>): *} callback
 * @param {THIS=} opt_thisArg
 * @return {VALUE|undefined} The found value, or undefined.
 * @template VALUE, THIS
 */
$jscomp.array.find = function(callback, opt_thisArg = void 0) {
  return $jscomp.array.findInternal_(this, callback, opt_thisArg).v;
};


/**
 * Installs the Array.prototype.find polyfill.
 * @suppress {const,checkTypes}
 */
$jscomp.array.find$install = function() {
  $jscomp.array.installHelper_('find', $jscomp.array.find);
};


/**
 * Finds an element that satisfies the given predicate, returning its index.
 *
 * @this {!IArrayLike<VALUE>}
 * @param {function(this: THIS, VALUE, number, !IArrayLike<VALUE>): *} callback
 * @param {THIS=} opt_thisArg
 * @return {VALUE|undefined} The found value, or undefined.
 * @template VALUE, THIS
 */
$jscomp.array.findIndex = function(callback, opt_thisArg = void 0) {
  return $jscomp.array.findInternal_(this, callback, opt_thisArg).i;
};


/**
 * Installs the Array.prototype.findIndex polyfill.
 * @suppress {const,checkTypes}
 */
$jscomp.array.findIndex$install = function() {
  $jscomp.array.installHelper_('findIndex', $jscomp.array.findIndex);
};
