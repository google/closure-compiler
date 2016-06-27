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

'require es6/symbol es6/map util/polyfill';

/**
 * Whether to skip the conformance check and simply use the polyfill always.
 * @define {boolean}
 */
$jscomp.ASSUME_NO_NATIVE_SET = false;

$jscomp.polyfill('Set', function(NativeSet) {

  // Perform a conformance check to ensure correct native implementation.
  var isConformant = !$jscomp.ASSUME_NO_NATIVE_SET && (function() {
    if (!NativeSet ||
        !NativeSet.prototype.entries ||
        typeof Object.seal != 'function') {
      return false;
    }
    // Some implementations don't support constructor arguments.
    try {
      NativeSet = /** @type {function(new: Set, !Iterator=)} */ (NativeSet);
      var value = Object.seal({x: 4});
      var set = new NativeSet($jscomp.makeIterator([value]));
      if (!set.has(value) || set.size != 1 || set.add(value) != set ||
          set.size != 1 || set.add({x: 4}) != set || set.size != 2) {
        return false;
      }
      var iter = set.entries();
      var item = iter.next();
      if (item.done || item.value[0] != value || item.value[1] != value) {
        return false;
      }
      item = iter.next();
      if (item.done || item.value[0] == value || item.value[0].x != 4 ||
          item.value[1] != item.value[0]) {
        return false;
      }
      return iter.next().done;
    } catch (err) { // This should hopefully never happen, but let's be safe.
      return false;
    }
  })();
  if (isConformant) return NativeSet;

  // We depend on Symbol.iterator, so ensure it's loaded.
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();



  /**
   * Polyfill for the global Map data type.
   * @constructor
   * @struct
   * @implements {Iterable<VALUE>}
   * @template KEY, VALUE
   * @param {!Iterable<VALUE>|!Array<VALUE>|null=} opt_iterable
   *     Optional data to populate the set.
   */
  // TODO(sdh): fix param type if heterogeneous arrays ever supported.
  var PolyfillSet = function(opt_iterable) {
    /** @private @const {!Map<VALUE, VALUE>} */
    this.map_ = new Map();
    if (opt_iterable) {
      var iter = $jscomp.makeIterator(opt_iterable);
      var entry;
      while (!(entry = iter.next()).done) {
        var item = /** @type {!IIterableResult<VALUE>} */ (entry).value;
        this.add(item);
      }
    }
    // Note: this property should not be changed.  If we're willing to give up
    // ES3 support, we could define it as a property directly.  It should be
    // marked readonly if such an annotation ever comes into existence.
    this.size = this.map_.size;
  };


  /**
   * Adds or updates a value in the set.
   * @param {VALUE} value
   */
  PolyfillSet.prototype.add = function(value) {
    this.map_.set(value, value);
    this.size = this.map_.size;
    return this;
  };


  /**
   * Deletes an element from the set.
   * @param {VALUE} value
   * @return {boolean}
   */
  PolyfillSet.prototype.delete = function(value) {
    var result = this.map_.delete(value);
    this.size = this.map_.size;
    return result;
  };


  /** Clears the set. */
  PolyfillSet.prototype.clear = function() {
    this.map_.clear();
    this.size = 0;
  };


  /**
   * Checks whether the given value is in the set.
   * @param {VALUE} value
   * @return {boolean} True if the set contains the given value.
   */
  PolyfillSet.prototype.has = function(value) {
    return this.map_.has(value);
  };


  /**
   * Returns an iterator of entries.
   * @return {!IteratorIterable<!Array<VALUE>>}
   */
  PolyfillSet.prototype.entries = function() {
    return this.map_.entries();
  };


  /**
   * Returns an iterator of entries.
   * @return {!IteratorIterable<VALUE>}
   */
  PolyfillSet.prototype[Symbol.iterator] = function() {
    return this.map_.values();
  };


  /**
   * Returns an iterator of values.
   * @return {!IteratorIterable<VALUE>}
   */
  PolyfillSet.prototype.values = function() {
    return this.map_.values();
  };


  /**
   * Iterates over the set, running the given function on each element.
   * @param {function(this: THIS, VALUE, VALUE, !PolyfillSet<VALUE>)} callback
   * @param {THIS=} opt_thisArg
   * @template THIS
   */
  PolyfillSet.prototype.forEach = function(callback, opt_thisArg) {
    var set = this;
    this.map_.forEach(function(value) {
      return callback.call(/** @type {?} */ (opt_thisArg), value, value, set);
    });
  };


  return PolyfillSet;
}, 'es6-impl', 'es3');
