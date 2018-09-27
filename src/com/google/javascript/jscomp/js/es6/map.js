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

'require es6/conformance';
'require es6/symbol';
'require es6/util/makeiterator';
'require es6/weakmap';
'require util/defines';
'require util/owns';
'require util/polyfill';


/**
 * Internal record type for entries.
 * @record
 * @template KEY, VALUE
 * @suppress {reportUnknownTypes}
 */
$jscomp.MapEntry = function() {
  /** @type {!$jscomp.MapEntry<KEY, VALUE>} */
  this.previous;
  /** @type {!$jscomp.MapEntry<KEY, VALUE>} */
  this.next;
  /** @type {?Object} */
  this.head;
  /** @type {KEY} */
  this.key;
  /** @type {VALUE} */
  this.value;
};


$jscomp.polyfill('Map',
    /**
     * @param {*} NativeMap
     * @return {*}
     * @suppress {reportUnknownTypes}
     */
    function(NativeMap) {

  /**
   * Checks conformance of the existing Map.
   * @return {boolean} True if the browser's implementation conforms.
   * @suppress {missingProperties} "entries" unknown prototype
   */
  function isConformant() {
    if ($jscomp.ASSUME_NO_NATIVE_MAP ||
        !NativeMap ||
        typeof NativeMap != "function" ||
        !NativeMap.prototype.entries ||
        typeof Object.seal != 'function') {
      return false;
    }
    // Some implementations don't support constructor arguments.
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
  }

  if ($jscomp.USE_PROXY_FOR_ES6_CONFORMANCE_CHECKS) {
    if (NativeMap && $jscomp.ES6_CONFORMANCE) return NativeMap;
  } else {
    if (isConformant()) return NativeMap;
  }

  // We depend on Symbol.iterator, so ensure it's loaded.
  $jscomp.initSymbolIterator();


  /** @const {!WeakMap<!Object, string>} */
  var idMap = new WeakMap();


  /**
   * Polyfill for the global Map data type.
   * @constructor
   * @struct
   * @extends {Map<KEY, VALUE>}
   * @implements {Iterable<!Array<KEY|VALUE>>}
   * @template KEY, VALUE
   * @param {!Iterable<!Array<KEY|VALUE>>|!Array<!Array<KEY|VALUE>>|null=}
   *     opt_iterable Optional data to populate the map.
   */
  // TODO(sdh): fix param type if heterogeneous arrays ever supported.
  var PolyfillMap = function(opt_iterable) {
    /** @private {!Object<!Array<!$jscomp.MapEntry<KEY, VALUE>>>} */
    this.data_ = {};

    /** @private {!$jscomp.MapEntry<KEY, VALUE>} */
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


  /** @override */
  PolyfillMap.prototype.set = function(key, value) {
    // normalize -0/+0 to +0
    key = key === 0 ? 0 : key;
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


  /** @override */
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


  /** @override */
  PolyfillMap.prototype.clear = function() {
    this.data_ = {};
    this.head_ = this.head_.previous = createHead();
    this.size = 0;
  };


  /** @override */
  PolyfillMap.prototype.has = function(key) {
    return !!(maybeGetEntry(this, key).entry);
  };


  /** @override */
  PolyfillMap.prototype.get = function(key) {
    var entry = maybeGetEntry(this, key).entry;
    // NOTE: this cast is a lie, but so is the extern.
    return /** @type {VALUE} */ (entry &&
      /** @type {VALUE} */ (entry.value));
  };


  /** @override */
  PolyfillMap.prototype.entries = function() {
    return makeIterator(this, /** @return {!Array<(KEY|VALUE)>} */ function(
        /** !$jscomp.MapEntry<KEY, VALUE> */ entry) {
      return ([entry.key, entry.value]);
    });
  };


  /** @override */
  PolyfillMap.prototype.keys = function() {
    return makeIterator(this, /** @return {KEY} */ function(
        /** !$jscomp.MapEntry<KEY, VALUE> */ entry) {
      return entry.key;
    });
  };


  /** @override */
  PolyfillMap.prototype.values = function() {
    return makeIterator(this, /** @return {VALUE} */ function(
        /** !$jscomp.MapEntry<KEY, VALUE> */ entry) {
      return entry.value;
    });
  };


  /** @override */
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


  /** @type {?} */ (PolyfillMap.prototype)[Symbol.iterator] =
      PolyfillMap.prototype.entries;


  /**
   * Returns an entry or undefined.
   * @param {!PolyfillMap<KEY, VALUE>} map
   * @param {KEY} key
   * @return {{id: string,
   *           list: (!Array<!$jscomp.MapEntry<KEY, VALUE>>|undefined),
   *           index: number,
   *           entry: (!$jscomp.MapEntry<KEY, VALUE>|undefined)}}
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
   * @param {function(!$jscomp.MapEntry<KEY, VALUE>): T} func
   * @return {!IteratorIterable<T>}
   * @template KEY, VALUE, T
   * @private
   */
  var makeIterator = function(map, func) {
    var entry = map.head_;
    return $jscomp.iteratorPrototype(function() {
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
    });
  };


  /**
   * Makes a new "head" element.
   * @return {!$jscomp.MapEntry<KEY, VALUE>}
   * @template KEY, VALUE
   * @suppress {checkTypes} ignore missing key/value for head only
   */
  var createHead = function() {
    var head = /** type {!$jscomp.MapEntry<KEY, VALUE>} */ ({});
    head.previous = head.next = head.head = head;
    return head;
  };


  /**
   * Counter for generating IDs.
   * @private {number}
   */
  var mapIndex = 0;


  /**
   * @param {*} obj An extensible object.
   * @return {string} A unique ID.
   */
  var getId = function(obj) {
    var type = obj && typeof obj;
    if (type == 'object' || type == 'function') {
      obj = /** @type {!Object} */ (obj);
      if (!idMap.has(obj)) {
        var id = '' + (++mapIndex);
        idMap.set(obj, id);
        return id;
      }
      return idMap.get(obj);
    }
    // Add a prefix since obj could be '__proto__';
    return 'p_' + obj;
  };


  return PolyfillMap;
}, 'es6', 'es3');
