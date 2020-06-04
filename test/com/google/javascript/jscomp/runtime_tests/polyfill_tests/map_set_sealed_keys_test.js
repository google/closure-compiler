/*
 * Copyright 2020 The Closure Compiler Authors.
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
 * Tests for the behavior of sealed/frozen/non-extensible objects when inserted
 * into polyfilled data structures.
 *
 * These tests are separated from the main Map/Set/WeakMap/WeakSet tests because
 * polyfill isolation currently does not support inserting frozen objects.
 */

goog.module('jscomp.runtime_tests.polyfill_tests.weakmap_frozen_keys_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');

testSuite({
  testWeakMap_sealedKeys() {
    if (!Object.seal) return;

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

  testMap_sealedKeys() {
    if (!Object.seal) return;

    /**
     * @param {!Map<K, V>} map
     * @param {K} key
     * @param {V} value
     * @template K, V
     */
    function checkSetGet(map, key, value) {
      const size = map.size;
      assertFalse(map.has(key));
      assertEquals(map, map.set(key, value));
      assertEquals(value, map.get(key));
      assertTrue(map.has(key));
      assertEquals(size + 1, map.size);
    }
    const key1 = Object.seal({});
    const key2 = Object.seal({});
    const key3 = Object.freeze({});

    const map = new Map();
    checkSetGet(map, key1, 'a');
    checkSetGet(map, key2, 'b');
    checkSetGet(map, key3, 'c');

    assertEquals(3, map.size);
    assertEquals('a', map.get(key1));
    assertEquals('b', map.get(key2));
    assertEquals('c', map.get(key3));
    assertTrue(map.delete(key1));
    assertFalse(map.delete(key1));
    assertTrue(map.delete(key2));
    assertTrue(map.delete(key3));
    assertEquals(0, map.size);
  },

  testSet_add_sealedObjects() {
    // NOTE: IE8 doesn't support Object.seal.
    if (userAgent.IE && !userAgent.isVersionOrHigher(9)) return;

    /**
     * @param {!Set<E>} set
     * @param {E} elem
     * @template E
     */
    function checkAddHas(set, elem) {
      const size = set.size;
      assertFalse(set.has(elem));
      assertEquals(set, set.add(elem));
      assertTrue(set.has(elem));
      assertEquals(size + 1, set.size);
    }
    const key1 = Object.preventExtensions({});
    const key2 = Object.seal({});
    const key3 = Object.freeze({});

    const set = new Set();
    checkAddHas(set, key1);
    checkAddHas(set, key2);
    checkAddHas(set, key3);

    assertEquals(3, set.size);
    assertTrue(set.delete(key1));
    assertFalse(set.delete(key1));
    assertTrue(set.delete(key2));
    assertTrue(set.delete(key3));
    assertEquals(0, set.size);
  },

  testWeakSet_sealedKeys() {
    if (!Object.seal) return;

    /**
     * @param {!WeakSet<T>} set
     * @param {T} value
     * @template T
     */
    function checkSetHas(set, value) {
      assertFalse(set.has(value));
      assertEquals(set, set.add(value));
      assertTrue(set.has(value));
    }

    const key1 = Object.preventExtensions({});
    const key2 = Object.seal({});
    const key3 = Object.freeze({});

    const set = new WeakSet();
    checkSetHas(set, key1);
    checkSetHas(set, key2);
    checkSetHas(set, key3);

    assertTrue(set.has(key1));
    assertTrue(set.has(key2));
    assertTrue(set.has(key3));
    assertTrue(set.delete(key1));
    assertFalse(set.delete(key1));
    assertTrue(set.delete(key2));
    assertTrue(set.delete(key3));

    assertFalse(set.has(key1));
    assertFalse(set.has(key2));
    assertFalse(set.has(key3));
  },
});
