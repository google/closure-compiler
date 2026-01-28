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
 * @fileoverview
 * @suppress {lintVarDeclarations}
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

  /** @const {!WeakMap<!Object, string>} */
  var idMap = new WeakMap();

  // Numeric indices to avoid ambiguation failures breaking code that extends
  // the Map class.  They will actually be extending the polyfill if it is used.
  // We need to make sure that ambiguation will not try to reuse the property
  // names for any properties that the extending class adds.
  /**
   * @const Index for PolyfillMap's data property, of type
   *        !Object<!Array<!$jscomp.MapEntry<KEY, VALUE>>>
   */
  var DATA = 0;
  /**
   * @const Index for PolyfillMap's head property, of type
   *        !$jscomp.MapEntry<KEY, VALUE>
   */
  var HEAD = 1;

  /**
   * Polyfill for the global Map data type.
   * @constructor
   * @unrestricted
   * @extends {Map<KEY, VALUE>}
   * @implements {Iterable<!Array<KEY|VALUE>>}
   * @template KEY, VALUE
   * @param {!Iterable<!Array<KEY|VALUE>>|!Array<!Array<KEY|VALUE>>|null=}
   *     opt_iterable Optional data to populate the map.
   */
  // TODO(sdh): fix param type if heterogeneous arrays ever supported.
  var PolyfillMap = function(opt_iterable) {
    this[DATA] = {};
    this[HEAD] = createHead();

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
      r.list = (this[DATA][r.id] = []);
    }
    if (!r.entry) {
      r.entry = {
        next: this[HEAD],
        previous: this[HEAD].previous,
        head: this[HEAD],
        key: key,
        value: value,
      };
      r.list.push(r.entry);
      this[HEAD].previous.next = r.entry;
      this[HEAD].previous = r.entry;
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
      if (!r.list.length) delete this[DATA][r.id];
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
    this[DATA] = {};
    this[HEAD] =
        this[HEAD].previous = createHead();
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
    var list = map[DATA][id];
    if (list && $jscomp.owns(map[DATA], id)) {
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
    var entry = map[HEAD];
    return $jscomp.iteratorPrototype(function() {
      if (entry) {
        while (entry.head != map[HEAD]) {
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

// Note: This needs to be defined here as opposed to requiring an
// es6/map/groupby.js file above so that the Map polyfill is defined first.
$jscomp.polyfill('Map.groupBy', function(orig) {
  if (orig) return orig;

  /**
   * Groups elements of the provided iterable into a map using keys returned
   * by the input callback function.
   *
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map/groupBy
   *
   * @param {!Iterable<VALUE>} items An iterable of items to be grouped.
   * @param {function(VALUE, number): KEY} callbackFn A function that
   *     provides the key for each item.
   * @return {!Map<KEY, !Array<VALUE>>}
   * @template KEY, VALUE
   */
  var polyfill = function(items, callbackFn) {
    if (typeof callbackFn !== 'function') {
      throw new TypeError('callbackFn must be a function');
    }
    var result = new Map();
    var index = 0;
    var iter = $jscomp.makeIterator(items);
    for (var entry = iter.next(); !entry.done; entry = iter.next()) {
      var item = entry.value;
      var key = callbackFn(item, index++);
      var group = result.get(key);
      if (!group) {
        group = [];
        result.set(key, group);
      }
      group.push(item);
    }
    return result;
  };

  return polyfill;
}, 'es_next', 'es3');
