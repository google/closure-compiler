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
 * @fileoverview Polyfills for ES6 Map.
 */


/**
 * Polyfill for the global Map data type.
 * @implements {Iterable<!Array<KEY|VALUE>>}
 * @template KEY, VALUE
 */
$jscomp.Map = class {


  /**
   * Mini test suite for the browser's implementation of Map, so that we
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

    /** @type {function(new: Map, !Iterator)} */
    const Map = $jscomp.global['Map'];
    if (!Map || !Map.prototype.entries || !Object.seal) return false;
    // Some implementations don't support constructor arguments.
    try {
      const key = Object.seal({x: 4});
      const map = new Map($jscomp.makeIterator([[key, 's']]));
      if (map.get(key) != 's' || map.size != 1 || map.get({x: 4}) ||
          map.set({x: 4}, 't') != map || map.size != 2) {
        return false;
      }
      const /** !Iterator<!Array> */ iter = map.entries();
      let item = iter.next();
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
  }


  /**
   * Makes a new "head" element.
   * @return {!$jscomp.Map.Entry_<KEY, VALUE>}
   * @template KEY, VALUE
   * @suppress {checkTypes} ignore missing key/value for head only
   * @private
   */
  static createHead_() {
    const head = /** type {!$jscomp.Map.Entry_<KEY, VALUE>} */ ({});
    head.previous = head.next = head.head = head;
    return head;
  }


  /**
   * @param {*} obj An extensible object.
   * @return {string} A unique ID.
   * @private
   */
  static getId_(obj) {
    // TODO(sdh): could use goog.getUid for this if it exists.
    // (This might work better with goog.defineClass)
    if (!(obj instanceof Object)) {
      return String(obj);
    }
    if (!($jscomp.Map.key_ in obj)) {
      if (obj instanceof Object &&
          Object.isExtensible && Object.isExtensible(obj)) {
        $jscomp.Map.defineProperty_(
            obj, $jscomp.Map.key_, ++$jscomp.Map.index_);
      }
    }
    if (!($jscomp.Map.key_ in obj)) {
      // String representation is best we can do, though it's not stricty
      // guaranteed to be consistent (i.e. for mutable objects).  But for
      // non-extensible objects, there's nothing better we could possibly
      // use for bucketing.  We prepend ' ' for two reasons: (1) to
      // separate from objects (whose uids are digits) and (2) to prevent
      // the illegal key '__proto__'.  This should also prevent any other
      // weird non-enumerable keys.
      return ' ' + obj;
    }
    return obj[$jscomp.Map.key_];
  }


  // TODO(sdh): fix this type if heterogeneous arrays ever supported.
  /**
   * @param {!Iterable<!Array<KEY|VALUE>>|!Array<!Array<KEY|VALUE>>=}
   *     opt_iterable Optional data to populate the map.
   */
  constructor(opt_iterable = []) {
    /** @private {!Object<!Array<!$jscomp.Map.Entry_<KEY, VALUE>>>} */
    this.data_ = {};

    /** @private {!$jscomp.Map.Entry_<KEY, VALUE>} */
    this.head_ = $jscomp.Map.createHead_();

    // Note: this property should not be changed.  If we're willing to give up
    // ES3 support, we could define it as a property directly.  It should be
    // marked readonly if such an annotation ever comes into existence.
    /** @type {number} */
    this.size = 0;

    if (opt_iterable) {
      for (const item of opt_iterable) {
        this.set(/** @type {KEY} */ (item[0]), /** @type {VALUE} */ (item[1]));
      }
    }
  }


  /**
   * Adds or updates a value in the map.
   * @param {KEY} key
   * @param {VALUE} value
   */
  set(key, value) {
    let {id, list, entry} = this.maybeGetEntry_(key);
    if (!list) {
      list = (this.data_[id] = []);
    }
    if (!entry) {
      entry = {
        next: this.head_,
        previous: this.head_.previous,
        head: this.head_,
        key,
        value,
      };
      list.push(entry);
      this.head_.previous.next = entry;
      this.head_.previous = entry;
      this.size++;
    } else {
      entry.value = value;
    }
    return this;
  }


  /**
   * Deletes an element from the map.
   * @param {KEY} key
   * @return {boolean} Whether the entry was deleted.
   */
  delete(key) {
    const {id, list, index, entry} = this.maybeGetEntry_(key);
    if (entry && list) {
      list.splice(index, 1);
      if (!list.length) delete this.data_[id];
      entry.previous.next = entry.next;
      entry.next.previous = entry.previous;
      entry.head = null;
      this.size--;
      return true;
    }
    return false;
  }


  /**
   * Clears the map.
   */
  clear() {
    this.data_ = {};
    this.head_ = this.head_.previous = $jscomp.Map.createHead_();
    this.size = 0;
  }


  /**
   * Checks whether the given key is in the map.
   * @param {*} key
   * @return {boolean} True if the map contains the given key.
   */
  has(key) {
    return Boolean(this.maybeGetEntry_(key).entry);
  }


  /**
   * Retrieves an element from the map, by key.
   * @param {*} key
   * @return {VALUE|undefined}
   */
  get(key) {
    const {entry} = this.maybeGetEntry_(key);
    return entry && entry.value;
  }


  /**
   * Returns an entry or undefined.
   * @param {KEY} key
   * @return {{id: string,
   *           list: (!Array<!$jscomp.Map.Entry_<KEY, VALUE>>|undefined),
   *           index: number,
   *           entry: (!$jscomp.Map.Entry_<KEY, VALUE>|undefined)}}
   * @private
   */
  maybeGetEntry_(key) {
    const id = $jscomp.Map.getId_(key);
    const list = this.data_[id];
    if (list) {
      for (let index = 0; index < list.length; index++) {
        const entry = list[index];
        if ((key !== key && entry.key !== entry.key) || key === entry.key) {
          return {id, list, index, entry};
        }
      }
    }
    return {id, list, index: -1, entry: void 0};
  }


  /**
   * Returns an iterator of entries.
   * @return {!IteratorIterable<!Array<KEY|VALUE>>}
   */
  entries() {
    return this.iter_(entry => [entry.key, entry.value]);
  }


  /**
   * Returns an iterator of keys.
   * @return {!IteratorIterable<KEY>}
   */
  keys() {
    return this.iter_(entry => entry.key);
  }


  /**
   * Returns an iterator of values.
   * @return {!IteratorIterable<VALUE>}
   */
  values() {
    return this.iter_(entry => entry.value);
  }


  /**
   * Iterates over the map, running the given function on each element.
   * @param {function(this: THIS, VALUE, KEY, !$jscomp.Map<KEY, VALUE>)}
   *     callback
   * @param {THIS=} opt_thisArg
   * @template THIS
   */
  forEach(callback, opt_thisArg = void 0) {
    for (const entry of this.entries()) {
      callback.call(
          opt_thisArg,
          /** @type {VALUE} */ (entry[1]),
          /** @type {KEY} */ (entry[0]),
          /** @type {!$jscomp.Map<KEY, VALUE>} */ (this));
    }
  }


  /**
   * Maps over the entries with the given function.
   * @param {function(!$jscomp.Map.Entry_<KEY, VALUE>): T} func
   * @return {!IteratorIterable<T>}
   * @template T
   * @private
   */
  iter_(func) {
    const map = this;
    let entry = this.head_;
    return /** @type {!IteratorIterable} */ ({
      next() {
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
      },
      [Symbol.iterator]() {
        return /** @type {!Iterator} */ (this);
      }
    });
  }
};


/**
 * Counter for generating IDs.
 * @private {number}
 */
$jscomp.Map.index_ = 0;


/**
 * @param {!Object} obj
 * @param {string} key
 * @param {*} value
 * @private
 */
$jscomp.Map.defineProperty_ =
    Object.defineProperty ?
        function(obj, key, value) {
          Object.defineProperty(obj, key, {value: String(value)});
        } : function(obj, key, value) {
          obj[key] = String(value);
        };


/**
 * Internal record type for entries.
 * @private @record
 * @template KEY, VALUE
 */
$jscomp.Map.Entry_ = function() {};


/** @type {!$jscomp.Map.Entry_<KEY, VALUE>} */
$jscomp.Map.Entry_.prototype.previous;


/** @type {!$jscomp.Map.Entry_<KEY, VALUE>} */
$jscomp.Map.Entry_.prototype.next;


/** @type {?Object} */
$jscomp.Map.Entry_.prototype.head;


/** @type {KEY} */
$jscomp.Map.Entry_.prototype.key;


/** @type {VALUE} */
$jscomp.Map.Entry_.prototype.value;


/**
 * Whether to skip the conformance check and simply use the polyfill always.
 * @define {boolean}
 */
$jscomp.Map.ASSUME_NO_NATIVE = false;


/** Decides between the polyfill and the native implementation. */
$jscomp.Map$install = function() {
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  if (!$jscomp.Map.ASSUME_NO_NATIVE && $jscomp.Map.checkBrowserConformance_()) {
    $jscomp.Map = $jscomp.global['Map'];
  } else {
    // Install the iterator.
    $jscomp.Map.prototype[Symbol.iterator] = $jscomp.Map.prototype.entries;

    /**
     * Fixed key used for storing generated object IDs.
     * @private @const {symbol}
     */
    $jscomp.Map.key_ = Symbol('map-id-key');
  }
  // TODO(sdh): this prevents inlining; is there another way to avoid
  // duplicate work but allow this function to be inlined exactly once?
  $jscomp.Map$install = function() {};
};
