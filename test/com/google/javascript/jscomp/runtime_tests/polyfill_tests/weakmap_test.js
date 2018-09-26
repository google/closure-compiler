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

goog.module('jscomp.runtime_tests.polyfill_tests.weakmap_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');

const IE8 = userAgent.IE && !userAgent.isVersionOrHigher(9);

/**
 * @param {!WeakMap<K, V>} map
 * @param {K} key
 * @param {V} value
 * @template K, V
 */
function checkSetGet(map, key, value) {
  assertFalse(map.has(key));
  assertEquals(map, map.set(key, value));
  assertEquals(value, map.get(key));
  assertTrue(map.has(key));
}

/**
 * Feeze an object recursively
 *
 * @param {?} o
 * @return {?}
 *
 * See https://github.com/substack/deep-freeze/blob/master/index.js
 */
function deepFreeze (o) {
  Object.freeze(o);

  Object.getOwnPropertyNames(o).forEach(function (prop) {
    if (o.hasOwnProperty(prop)
    && o[prop] !== null
    && (typeof o[prop] === "object" || typeof o[prop] === "function")
    && !Object.isFrozen(o[prop])) {
      deepFreeze(o[prop]);
    }
  });

  return o;
}

testSuite({
  testObjectKeys() {
    const map = new WeakMap();

    const key1 = {};
    const key2 = {};
    const key3 = {};
    checkSetGet(map, key1, 23);
    checkSetGet(map, key2, 'str');
    checkSetGet(map, key3, {});

    assertEquals(23, map.get(key1));
    assertEquals('str', map.get(key2));
  },

  testDelete() {
    const map = new WeakMap();
    const key = {};
    map.set(key, 'delete me');
    assertTrue(map.has(key));
    assertTrue(map.delete(key));
    assertFalse(map.delete(key));
    assertFalse(map.delete({}));
    assertFalse(map.has(key));
    assertUndefined(map.get(key));
  },

  testSealedKeys() {
    if (!Object.seal) return;

    const key1 = Object.seal({});
    const key2 = Object.seal({});
    const key3 = Object.freeze({});

    const map = new WeakMap();
    checkSetGet(map, key1, 'a');
    checkSetGet(map, key2, 'b');
    checkSetGet(map, key3, 'c');

    assertEquals('a', map.get(key1));
    assertEquals('b', map.get(key2));
    assertEquals('c', map.get(key3));
    assertTrue(map.delete(key1));
    assertFalse(map.delete(key1));
    assertTrue(map.delete(key2));
    assertTrue(map.delete(key3));

    assertFalse(map.has(key1));
    assertFalse(map.has(key2));
    assertFalse(map.has(key3));
  },

  testKeyIdNotEnumerable() {
    if (IE8) return;
    const map = new WeakMap();
    const key = {};
    map.set(key, 1);
    for (let k in key) {
      fail('Expected no enumerable properties in key but got ' + k);
    }
  },

  testConstructor_generator() {
    const a = {};
    const b = {};
    const init = function*() {
      yield [a, 4];
      yield [b, 3];
    };
    const map = new WeakMap(init());
    assertTrue(map.has(a));
    assertFalse(map.has({}));
    assertEquals(4, map.get(a));
    assertEquals(3, map.get(b));
  },

  testConstructor_array() {
    const a = {};
    const b = {};
    const map = new WeakMap([[a, 3], [b, 5]]);
    assertTrue(map.has(a));
    assertTrue(map.has(b));
    assertFalse(map.has({}));
    assertEquals(3, map.get(a));
    assertEquals(5, map.get(b));
  },

  testDeepFreeze() {
    if (IE8) return;  // No Object.freeze on IE8

    // Verify we don't recurse forever in deepFreeze just because we patch
    // "Object.freeze" to support WeakMap.
    const a = {};
    assertEquals(a, deepFreeze(a));
  },
});
