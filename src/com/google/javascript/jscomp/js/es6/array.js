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
 * Creates an iterator from an array-like, with a transformation function.
 * @param {!IArrayLike<INPUT>} array
 * @param {function(number, INPUT): OUTPUT} transform
 * @return {!IteratorIterable<OUTPUT>}
 * @template INPUT, OUTPUT
 * @suppress {checkTypes}
 */
$jscomp.iteratorFromArray = function(array, transform) {
  $jscomp.initSymbolIterator();
  // NOTE: IE8 doesn't support indexing from boxed Strings.
  if (array instanceof String) array = array + '';
  var i = 0;
  var iter = {
    next: function() {
      if (i < array.length) {
        var index = i++;
        return {value: transform(index, array[index]), done: false};
      }
      iter.next = function() { return {done: true, value: void 0}; };
      return iter.next();
    }
  };
  iter[Symbol.iterator] = function() { return iter; };
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
 * @template THIS, VALUE
 */
$jscomp.findInternal = function(array, callback, thisArg) {
  if (array instanceof String) {
    array = /** @type {!IArrayLike} */ (String(array));
  }
  var len = array.length;
  for (var i = 0; i < len; i++) {
    var value = array[i];
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
  $jscomp.array.from = function(arrayLike, opt_mapFn, opt_thisArg) {
    $jscomp.initSymbolIterator();
    opt_mapFn = opt_mapFn != null ? opt_mapFn : function(x) { return x; };
    var result = [];
    // NOTE: this is cast to ? because [] on @struct is an error
    var iteratorFunction = /** @type {?} */ (arrayLike)[Symbol.iterator];
    if (typeof iteratorFunction == 'function') {
      arrayLike = iteratorFunction.call(arrayLike);
    }
    if (typeof arrayLike.next == 'function') {
      var next;
      while (!(next = arrayLike.next()).done) {
        result.push(
            opt_mapFn.call(/** @type {?} */ (opt_thisArg), next.value));
      }
    } else {
      var len = arrayLike.length;  // need to support non-iterables
      for (var i = 0; i < len; i++) {
        result.push(
            opt_mapFn.call(/** @type {?} */ (opt_thisArg), arrayLike[i]));
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
   * @param {...T} var_args Elements to include in the array.
   * @return {!Array<T>}
   * @template T
   */
  $jscomp.array.of = function(var_args) {
    return $jscomp.array.from(arguments);
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
    return $jscomp.iteratorFromArray(
        this, function(i, v) { return [i, v]; });
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
    return $jscomp.iteratorFromArray(this, function(i) { return i; });
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
    return $jscomp.iteratorFromArray(this, function(k, v) { return v; });
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
  $jscomp.array.copyWithin = function(target, start, opt_end) {
    var len = this.length;
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
  $jscomp.array.fill = function(value, opt_start, opt_end) {
    var length = this.length || 0;
    if (opt_start < 0) {
      opt_start = Math.max(0, length + /** @type {number} */ (opt_start));
    }
    if (opt_end == null || opt_end > length) opt_end = length;
    opt_end = Number(opt_end);
    if (opt_end < 0) opt_end = Math.max(0, length + opt_end);
    for (var i = Number(opt_start || 0); i < opt_end; i++) {
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
   * @param {function(this: THIS, VALUE, number, !IArrayLike<VALUE>): *}
   *     callback
   * @param {THIS=} opt_thisArg
   * @return {VALUE|undefined} The found value, or undefined.
   * @template VALUE, THIS
   */
  $jscomp.array.find = function(callback, opt_thisArg) {
    return $jscomp.findInternal(this, callback, opt_thisArg).v;
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
   * @param {function(this: THIS, VALUE, number, !IArrayLike<VALUE>): *}
   *     callback
   * @param {THIS=} opt_thisArg
   * @return {number} The found value, or undefined.
   * @template VALUE, THIS
   */
  $jscomp.array.findIndex = function(callback, opt_thisArg) {
    return $jscomp.findInternal(this, callback, opt_thisArg).i;
  };


  /**
   * Installs the Array.prototype.findIndex polyfill.
   * @suppress {const,checkTypes}
   */
  $jscomp.array.findIndex$install = function() {
    $jscomp.array.installHelper_('findIndex', $jscomp.array.findIndex);
  };
