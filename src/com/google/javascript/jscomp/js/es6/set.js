/*
 * Copyright 2015 The Closure Compiler Authors.
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
 * @fileoverview Polyfills for ES6 Set.
 */


/**
 * Polyfill for the global Set data type.
 * @implements {Iterable<VALUE>}
 * @template VALUE
 */
$jscomp.Set = class {


  /**
   * Mini test suite for the browser's implementation of Set, so that we
   * can use it instead when possible.
   * @return {boolean} True if the browser conforms to the spec.
   * @private
   */
  static checkBrowserConformance_() {
    // We do a quick check for the object and some key properties:
    //  1. whether the 'entries' method (one of the last-standardized) exists,
    //  2. whether the constructor accepts an iterable parameter.

    // TODO(sdh): DEFINE to assume not conformant (try-catch especially)
    // TODO(sdh): how to do this without using goog?

    const Set = $jscomp.global['Set'];
    if (!Set || !Set.prototype.entries || !Object.seal) return false;
    // Some implementations don't support constructor arguments.
    // TODO(sdh): this whole function from here to the end was wrapped
    // in a try, just in case for some reason it failed - is that reasonable?
    const value = Object.seal({x: 4});
    const set = new Set($jscomp.makeIterator([value]));
    if (set.has(value) || set.size != 1 || set.add(value) != set ||
        set.size != 1 || set.add({x: 4}) != set || set.size != 2) {
      return false;
    }
    const iter = set.entries();
    let item = iter.next();
    if (item.done || item.value[0] != value || item.value[1] != value) {
      return false;
    }
    item = iter.next();
    if (item.done || item.value[0] == value || item.value[0].x != 4 ||
        item.value[1] != item.value[0]) {
      return false;
    }
    return iter.next().done;
  }


  // TODO(sdh): fix this type if heterogeneous arrays ever supported.
  /**
   * @param {!Iterable<VALUE>|!Array<VALUE>=} opt_iterable
   *     Optional data to populate the set.
   */
  constructor(opt_iterable = []) {
    /** @private @const {!$jscomp.Map<VALUE, VALUE>} */
    this.map_ = new $jscomp.Map();
    if (opt_iterable) {
      for (const item of opt_iterable) {
        this.add(/** @type {VALUE} */ (item));
      }
    }
    // Note: this property should not be changed.  If we're willing to give up
    // ES3 support, we could define it as a property directly.  It should be
    // marked readonly if such an annotation ever comes into existence.
    this.size = this.map_.size;
  }


  /**
   * Adds or updates a value in the set.
   * @param {VALUE} value
   */
  add(value) {
    this.map_.set(value, value);
    this.size = this.map_.size;
    return this;
  }


  /**
   * Deletes an element from the set.
   * @param {VALUE} value
   * @return {boolean}
   */
  delete(value) {
    const result = this.map_.delete(value);
    this.size = this.map_.size;
    return result;
  }


  /** Clears the set. */
  clear() {
    this.map_.clear();
    this.size = 0;
  }


  /**
   * Checks whether the given value is in the set.
   * @param {*} value
   * @return {boolean} True if the set contains the given value.
   */
  has(value) {
    return this.map_.has(value);
  }


  /**
   * Returns an iterator of entries.
   * @return {!IteratorIterable<!Array<VALUE>>}
   */
  entries() {
    return this.map_.entries();
  }


  /**
   * Returns an iterator of values.
   * @return {!IteratorIterable<VALUE>}
   */
  values() {
    return this.map_.values();
  }


  /**
   * Iterates over the set, running the given function on each element.
   * @param {function(this: THIS, VALUE, VALUE, !$jscomp.Set<VALUE>)} callback
   * @param {THIS=} opt_thisArg
   * @template THIS
   */
  forEach(callback, opt_thisArg = void 0) {
    this.map_.forEach(value => callback.call(opt_thisArg, value, value, this));
  }
};


/**
 * Whether to skip the conformance check and simply use the polyfill always.
 * @define {boolean}
 */
$jscomp.Set.ASSUME_NO_NATIVE = false;


/** Decides between the polyfill and the native implementation. */
$jscomp.Set$install = function() {
  if (!$jscomp.Set.ASSUME_NO_NATIVE && $jscomp.Set.checkBrowserConformance_()) {
    $jscomp.Set = $jscomp.global['Set'];
  } else {
    $jscomp.Map$install();
    $jscomp.Set.prototype[Symbol.iterator] = $jscomp.Set.prototype.values;
  }
  $jscomp.Set$install = function() {};
};
