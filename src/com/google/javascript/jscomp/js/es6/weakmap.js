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
'require es6/util/makeiterator';
'require util/defineproperty';
'require util/owns';
'require util/polyfill';

$jscomp.polyfill('WeakMap',
    /**
     * @param {*} NativeWeakMap
     * @return {*}
     * @suppress {reportUnknownTypes}
     */
    function(NativeWeakMap) {
  /**
   * Checks conformance of the existing WeakMap.
   * @return {boolean} True if the browser's implementation conforms.
   */
  function isConformant() {
    if (!NativeWeakMap || !Object.seal) return false;
    try {
      var x = Object.seal({});
      var y = Object.seal({});
      var map = new /** @type {function(new: WeakMap, !Array)} */ (
          NativeWeakMap)([[x, 2], [y, 3]]);
      if (map.get(x) != 2 || map.get(y) != 3) return false;
      map.delete(x);
      map.set(y, 4);
      return !map.has(x) && map.get(y) == 4;
    } catch (err) { // This should hopefully never happen, but let's be safe.
      return false;
    }
  }
  if ($jscomp.USE_PROXY_FOR_ES6_CONFORMANCE_CHECKS) {
    if (NativeWeakMap && $jscomp.ES6_CONFORMANCE) return NativeWeakMap;
  } else {
    if (isConformant()) return NativeWeakMap;
  }

  var prop = '$jscomp_hidden_' + Math.random();

  /** @constructor */
  function WeakMapMembership() {}

  /**
   * Inserts the hidden property into the target.
   * @param {!Object} target
   */
  function insert(target) {
    if (!$jscomp.owns(target, prop)) {
      var obj = new WeakMapMembership();
      // TODO(sdh): This property will be enumerated in IE8.  If this becomes
      // a problem, we could avoid it by copying an infrequently-used non-enum
      // method (like toLocaleString) onto the object itself and encoding the
      // property on the copy instead.  This codepath must be easily removable
      // if IE8 support is not needed.
      $jscomp.defineProperty(target, prop, {value: obj});
    }
  }

  /**
   * Monkey-patches the freezing methods to ensure that the hidden
   * property is added before any freezing happens.
   * @param {string} name
   */
  function patch(name) {
    var prev = Object[name];
    if (prev) {
      Object[name] = function(target) {
        if (target instanceof WeakMapMembership) {
          return target;
        } else {
          insert(target);
          return prev(target);
        }
      };
    }
  }
  patch('freeze');
  patch('preventExtensions');
  patch('seal');
  // Note: no need to patch Reflect.preventExtensions since the polyfill
  // just calls Object.preventExtensions anyway (and if it's not polyfilled
  // then neither is WeakMap).

  var index = 0;

  /**
   * Polyfill for WeakMap:
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WeakMap
   *
   * This implementation is as non-leaky as possible, due to patching
   * the freezing and sealing operations.  It does not include any logic
   * to handle cases where a key was somehow made non-extensible without
   * the special hidden property being added.  It takes some care to ensure
   * the hidden property is not enumerated over nor discoverable, though
   * it's not completely secure (particularly in IE8).
   *
   * @constructor
   * @extends {WeakMap<KEY, VALUE>}
   * @template KEY, VALUE
   * @param {!Iterator<!Array<KEY|VALUE>>|!Array<!Array<KEY|VALUE>>|null=}
   *     opt_iterable Optional initial data.
   */
  var PolyfillWeakMap = function(opt_iterable) {
    /** @private @const {string} */
    this.id_ = (index += (Math.random() + 1)).toString();

    if (opt_iterable) {
      var iter = $jscomp.makeIterator(opt_iterable);
      var entry;
      while (!(entry = iter.next()).done) {
        var item = entry.value;
        this.set(/** @type {KEY} */ (item[0]), /** @type {VALUE} */ (item[1]));
      }
    }
  };

  /** @override */
  PolyfillWeakMap.prototype.set = function(key, value) {
    insert(key);
    if (!$jscomp.owns(key, prop)) {
      // NOTE: If the insert() call fails on the key, but the property
      // has previously successfully been added higher up the prototype
      // chain, then we'll silently misbehave.  Instead, throw immediately
      // before doing something bad.  If this becomes a problem (e.g. due
      // to some rogue frozen objects) then we may need to add a slow and
      // leaky fallback array to each WeakMap instance, as well as extra
      // logic in each accessor to use it (*only*) when necessary.
      throw new Error('WeakMap key fail: ' + key);
    }
    key[prop][this.id_] = value;
    return this;
  };

  /** @override */
  PolyfillWeakMap.prototype.get = function(key) {
    return $jscomp.owns(key, prop) ? key[prop][this.id_] : undefined;
  };

  /** @override */
  PolyfillWeakMap.prototype.has = function(key) {
    return $jscomp.owns(key, prop) && $jscomp.owns(key[prop], this.id_);
  };

  /** @override */
  PolyfillWeakMap.prototype.delete = function(key) {
    if (!$jscomp.owns(key, prop) ||
        !$jscomp.owns(key[prop], this.id_)) {
      return false;
    }
    return delete key[prop][this.id_];
  };

  return PolyfillWeakMap;
}, 'es6', 'es3');
