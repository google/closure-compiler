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

'require es6/symbol es6/util/makeiterator util/defineproperty';
'require util/owns util/polyfill';

/**
 * Whether to skip the conformance check and simply use the polyfill always.
 * @define {boolean}
 */
$jscomp.ASSUME_NO_NATIVE_MAP = false;

$jscomp.polyfill('Map', function(NativeMap) {

  // Perform a conformance check to ensure correct native implementation.
  var isConformant = !$jscomp.ASSUME_NO_NATIVE_MAP && (function() {
    if (!NativeMap ||
        !NativeMap.prototype.entries ||
        typeof Object.seal != 'function') {
      return false;
    }
    // Some implementations don't support constructor arguments.
    /** @preserveTry */
    try {
      NativeMap = /** @type {function(new: Map, !Iterator=)} */ (NativeMap);
      var key = Object.seal({x: 4});
      var map = new NativeMap($jscomp.makeIterator([[key, 's']]));
      if (map.get(key) != 's' || map.size != 1 || map.get({x: 4}) ||
          map.set({x: 4}, 't') != map || map.size != 2) {
        return false;
      }
      var /** !Iterator<!Array> */ iter = map.entries();
      var item = iter.next();
      if (item.done || item.value[0] != key || item.value[1] != 's') {
        return false;
      }
      item = iter.next();
      if (item.done || item.value[0].x != 4 ||
          item.value[1] != 't' || !iter.next().done) {
        return false;
      }
      return true;
    } catch (err) { // This should hopefully never happen, but let's be safe.
      return false;
    }
  })();
  if (isConformant) return NativeMap;

  // We depend on Symbol.iterator, so ensure it's loaded.
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();



  /**
   * Internal record type for entries.
   * @record
   * @template KEY, VALUE
   */
  var MapEntry = function() {};


  /** @type {!MapEntry<KEY, VALUE>} */
  MapEntry.prototype.previous;


  /** @type {!MapEntry<KEY, VALUE>} */
  MapEntry.prototype.next;


  /** @type {?Object} */
  MapEntry.prototype.head;


  /** @type {KEY} */
  MapEntry.prototype.key;


  /** @type {VALUE} */
  MapEntry.prototype.value;


  /**
   * Polyfill for the global Map data type.
   * @constructor
   * @struct
   * @implements {Iterable<!Array<KEY|VALUE>>}
   * @template KEY, VALUE
   * @param {!Iterable<!Array<KEY|VALUE>>|!Array<!Array<KEY|VALUE>>|null=}
   *     opt_iterable Optional data to populate the map.
   */
  // TODO(sdh): fix param type if heterogeneous arrays ever supported.
  var PolyfillMap = function(opt_iterable) {
    /** @private {!Object<!Array<!MapEntry<KEY, VALUE>>>} */
    this.data_ = {};

    /** @private {!MapEntry<KEY, VALUE>} */
    this.head_ = createHead();

    // Note: this property should not be changed.  If we're willing to give up
    // ES3 support, we could define it as a property directly.  It should be
    // marked readonly if such an annotation ever comes into existence.
    /** @type {number} */
    this.size = 0;

    if (opt_iterable) {
      var iter = $jscomp.makeIterator(opt_iterable);
      var entry;
      while (!(entry = iter.next()).done) {
        var item =
            /** @type {!IIterableResult<!Array<KEY|VALUE>>} */ (entry).value;
        this.set(/** @type {KEY} */ (item[0]), /** @type {VALUE} */ (item[1]));
      }
    }
  };


  /**
   * Adds or updates a value in the map.
   * @param {KEY} key
   * @param {VALUE} value
   */
  PolyfillMap.prototype.set = function(key, value) {
    var r = maybeGetEntry(this, key);
    if (!r.list) {
      r.list = (this.data_[r.id] = []);
    }
    if (!r.entry) {
      r.entry = {
        next: this.head_,
        previous: this.head_.previous,
        head: this.head_,
        key: key,
        value: value,
      };
      r.list.push(r.entry);
      this.head_.previous.next = r.entry;
      this.head_.previous = r.entry;
      this.size++;
    } else {
      r.entry.value = value;
    }
    return this;
  };


  /**
   * Deletes an element from the map.
   * @param {KEY} key
   * @return {boolean} Whether the entry was deleted.
   */
  PolyfillMap.prototype.delete = function(key) {
    var r = maybeGetEntry(this, key);
    if (r.entry && r.list) {
      r.list.splice(r.index, 1);
      if (!r.list.length) delete this.data_[r.id];
      r.entry.previous.next = r.entry.next;
      r.entry.next.previous = r.entry.previous;
      r.entry.head = null;
      this.size--;
      return true;
    }
    return false;
  };


  /**
   * Clears the map.
   */
  PolyfillMap.prototype.clear = function() {
    this.data_ = {};
    this.head_ = this.head_.previous = createHead();
    this.size = 0;
  };


  /**
   * Checks whether the given key is in the map.
   * @param {KEY} key
   * @return {boolean} True if the map contains the given key.
   */
  PolyfillMap.prototype.has = function(key) {
    return !!(maybeGetEntry(this, key).entry);
  };


  /**
   * Retrieves an element from the map, by key.
   * @param {KEY} key
   * @return {VALUE}
   */
  PolyfillMap.prototype.get = function(key) {
    var entry = maybeGetEntry(this, key).entry;
    // NOTE: this cast is a lie, but so is the extern.
    return /** @type {VALUE} */ (entry && entry.value);
  };


  /**
   * Returns an iterator of entries.
   * @return {!IteratorIterable<!Array<KEY|VALUE>>}
   */
  PolyfillMap.prototype.entries = function() {
    return makeIterator(
        this, function(entry) { return [entry.key, entry.value]; });
  };


  /**
   * Returns an iterator of keys.
   * @return {!IteratorIterable<KEY>}
   */
  PolyfillMap.prototype.keys = function() {
    return makeIterator(this, function(entry) { return entry.key; });
  };


  /**
   * Returns an iterator of values.
   * @return {!IteratorIterable<VALUE>}
   */
  PolyfillMap.prototype.values = function() {
    return makeIterator(this, function(entry) { return entry.value; });
  };


  /**
   * Iterates over the map, running the given function on each element.
   * @param {function(this: THIS, VALUE, KEY, !PolyfillMap<KEY, VALUE>)}
   *     callback
   * @param {THIS=} opt_thisArg
   * @template THIS
   */
  PolyfillMap.prototype.forEach = function(callback, opt_thisArg) {
    var iter = this.entries();
    var item;
    while (!(item = iter.next()).done) {
      var entry = item.value;
      callback.call(
          /** @type {?} */ (opt_thisArg),
          /** @type {VALUE} */ (entry[1]),
          /** @type {KEY} */ (entry[0]),
          this);
    }
  };


  /**
   * Returns an iterator of entries.
   * @return {!IteratorIterable<!Array<KEY|VALUE>>}
   */
  PolyfillMap.prototype[Symbol.iterator] = PolyfillMap.prototype.entries;


  /**
   * Returns an entry or undefined.
   * @param {!PolyfillMap<KEY, VALUE>} map
   * @param {KEY} key
   * @return {{id: string,
   *           list: (!Array<!MapEntry<KEY, VALUE>>|undefined),
   *           index: number,
   *           entry: (!MapEntry<KEY, VALUE>|undefined)}}
   * @template KEY, VALUE
   */
  var maybeGetEntry = function(map, key) {
    var id = getId(key);
    var list = map.data_[id];
    if (list && $jscomp.owns(map.data_, id)) {
      for (var index = 0; index < list.length; index++) {
        var entry = list[index];
        if ((key !== key && entry.key !== entry.key) || key === entry.key) {
          return {id: id, list: list, index: index, entry: entry};
        }
      }
    }
    return {id: id, list: list, index: -1, entry: undefined};
  };


  /**
   * Maps over the entries with the given function.
   * @param {!PolyfillMap<KEY, VALUE>} map
   * @param {function(!MapEntry<KEY, VALUE>): T} func
   * @return {!IteratorIterable<T>}
   * @template KEY, VALUE, T
   * @private
   */
  var makeIterator = function(map, func) {
    var entry = map.head_;
    var iter = {
      next: function() {
        if (entry) {
          while (entry.head != map.head_) {
            entry = entry.previous;
          }
          while (entry.next != entry.head) {
            entry = entry.next;
            return {done: false, value: func(entry)};
          }
          entry = null; // make sure depletion is permanent
        }
        return {done: true, value: void 0};
      }
    };
    iter[Symbol.iterator] = function() {
      return /** @type {!Iterator} */ (iter);
    };
    return /** @type {!IteratorIterable} */ (iter);
  };


  /**
   * Counter for generating IDs.
   * @private {number}
   */
  var mapIndex = 0;


  /**
   * Makes a new "head" element.
   * @return {!MapEntry<KEY, VALUE>}
   * @template KEY, VALUE
   * @suppress {checkTypes} ignore missing key/value for head only
   */
  var createHead = function() {
    var head = /** type {!MapEntry<KEY, VALUE>} */ ({});
    head.previous = head.next = head.head = head;
    return head;
  };


  /**
   * Fixed key used for storing generated object IDs.
   * @const {symbol}
   */
  var idKey = Symbol('map-id-key');


  /**
   * @param {*} obj An extensible object.
   * @return {string} A unique ID.
   */
  var getId = function(obj) {
    // TODO(sdh): could use goog.getUid for this if it exists.
    // (This might work better with goog.defineClass)
    if (!(obj instanceof Object)) {
      // Prepend primitives with 'p_', which will avoid potentially dangerous
      // names like '__proto__', as well as anything from Object.prototype.
      return 'p_' + obj;
    }
    if (!(idKey in obj)) {
      /** @preserveTry */
      try {
        $jscomp.defineProperty(obj, idKey, {value: ++mapIndex});
      } catch (ignored) {}
    }
    if (!(idKey in obj)) {
      // String representation is best we can do, though it's not stricty
      // guaranteed to be consistent (i.e. for mutable objects).  But for
      // non-extensible objects, there's nothing better we could possibly
      // use for bucketing.  We prepend 'o_' (for object) for two reasons:
      // (1) to distinguish generated IDs (which are digits) and primitives,
      // and (2) to prevent illegal or dangerous keys (see above).
      return 'o_ ' + obj;
    }
    return obj[idKey];
  };


  return PolyfillMap;
}, 'es6-impl', 'es3');
