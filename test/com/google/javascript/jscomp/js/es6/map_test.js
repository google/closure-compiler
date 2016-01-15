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

goog.module('$jscomp_map_test');
goog.setTestOnly();

const jsunit = goog.require('goog.testing.jsunit');
const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('testing');

const assertDeepEquals = testing.assertDeepEquals;

const DONE = {done: true, value: void 0};

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

testSuite({
  testObjectKeys() {
    const map = new Map();
    assertEquals(0, map.size);
    checkSetGet(map, {}, 23);
    checkSetGet(map, {}, 'str');
    checkSetGet(map, {}, {});
    assertEquals(3, map.size);
  },

  testNumericKeys() {
    const map = new Map();
    for (let i = 10; i < 20; i++) {
      checkSetGet(map, i, {});
      checkSetGet(map, i / 10, {});
      checkSetGet(map, String(i), {});
    }
    const keys = [+0, +Infinity, -Infinity, true, false, null, undefined];
    for (let k of keys) {
      checkSetGet(map, k, {});
    }
    assertEquals(37, map.size);

    // Note: +0 and -0 are the same key
    assertTrue(map.has(-0));
    assertEquals(map.get(0), map.get(-0));
  },

  testNanKeys() {
    const map = new Map();
    const obj = {};
    assertFalse(map.has(NaN));
    map.set(NaN, obj);
    assertTrue(map.has(NaN));
    assertTrue(map.has(NaN + 1));
    assertEquals(obj, map.get(NaN));
    assertTrue(map.delete(NaN));
    assertFalse(map.has(NaN));
  },

  testSealedKeys() {
    if (!Object.seal) return;
    const key1 = {};
    const key2 = {};
    const key3 = {};
    Object.seal(key1);
    Object.seal(key2);
    Object.freeze(key3);

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

  testKeyIdNotEnumerable() {
    if (!Object.defineProperty) return;
    const map = new Map();
    const key = {};
    map.set(key, 1);
    for (let k in key) {
      fail('Expected no enumerable properties in key but got ' + k);
    }
  },

  testDelete() {
    const map = new Map();
    const key = {};
    map.set(key, 'delete me');
    assertEquals(1, map.size);
    assertTrue(map.delete(key));
    assertEquals(0, map.size);
    assertFalse(map.delete(key));
    assertFalse(map.delete({}));
    assertUndefined(map.get(key));

    map.set(0, {});
    assertTrue(map.delete(-0));
    assertEquals(0, map.size);
  },

  testClear() {
    const map = new Map();
    map.set('a', 'b');
    map.set('b', 'c');
    assertEquals(2, map.size);
    map.clear();
    assertEquals(0, map.size);
    assertFalse(map.has('a'));
    assertUndefined(map.get('b'));
  },

  testConstructor_generator() {
    const init = function*() {
      yield ['a', 4];
      yield ['b', 3];
    };
    const map = new Map(init());
    assertEquals(2, map.size);
    assertTrue(map.has('a'));
    assertEquals(4, map.get('a'));
    assertEquals(3, map.get('b'));
  },

  testConstructor_array() {
    const map = new Map([['a', 3], ['b', 5]]);
    assertEquals(2, map.size);
    assertTrue(map.has('b'));
    assertEquals(3, map.get('a'));
  },

  testForEach() {
    const map = new Map();
    map.set('a', 'b');
    map.set('c', 'd');
    const out = [];
    const receiver = {};
    map.forEach(function(value, key, map) {
      out.push(value, key, map, this);
    }, receiver);
    assertArrayEquals(['b', 'a', map, receiver, 'd', 'c', map, receiver], out);
  },

  testForEach_concurrentMutation() {
    const map = new Map();
    map.set('a', 'b');
    map.set('a1', 'b1');
    const keys = [];
    map.forEach(function(value, key) {
      keys.push(key);
      if (map.delete(key + '1')) {
        map.set(key + '2', value + '2');
      }
    });
    assertArrayEquals(['a', 'a2'], keys);
  },

  testForEach_clear() {
    const map = new Map();
    map.set('a', 'b');
    map.set('c', 'd');
    let count = 0;
    map.forEach(function(value, key) {
      count++;
      map.clear();
      if (count < 5) map.set('e', 'f');
    });
    assertEquals(5, count);
  },

  testEntries() {
    const map = new Map();
    map.set('a', 'b');
    map.set('c', 'd');
    const iter = map.entries();
    assertDeepEquals({done: false, value: ['a', 'b']}, iter.next());
    assertDeepEquals({done: false, value: ['c', 'd']}, iter.next());
    assertDeepEquals(DONE, iter.next());
    assertDeepEquals(DONE, iter.next());
  },

  testValues() {
    const map = new Map();
    map.set('a', 'b');
    map.set('c', 'd');
    const iter = map.values();
    assertDeepEquals({done: false, value: 'b'}, iter.next());
    assertDeepEquals({done: false, value: 'd'}, iter.next());
    assertDeepEquals(DONE, iter.next());
    assertDeepEquals(DONE, iter.next());
  },

  testKeys() {
    const map = new Map();
    map.set('a', 'b');
    map.set('c', 'd');
    const iter = map.keys();
    assertDeepEquals({done: false, value: 'a'}, iter.next());
    assertDeepEquals({done: false, value: 'c'}, iter.next());
    assertDeepEquals(DONE, iter.next());
    assertDeepEquals(DONE, iter.next());
  },

  testKeys_continuesAfterClear() {
    const map = new Map();
    map.set('a', 'b');
    map.set('b', 'c');
    const iter = map.keys();
    map.clear();
    map.set('e', 'f');
    assertDeepEquals({done: false, value: 'e'}, iter.next());
    map.clear();
    map.set('g', 'h');
    assertDeepEquals({done: false, value: 'g'}, iter.next());
    assertDeepEquals(DONE, iter.next());
    // But once it's done, it doesn't come back
    map.set('i', 'j');
    assertDeepEquals(DONE, iter.next());
  },

  testKeys_continuesAfterDelete() {
    const map = new Map();
    map.set('a', 1);
    map.set('b', 2);
    map.set('c', 3);
    const iter = map.keys();
    map.delete('a');
    map.set('b', 4);
    map.delete('c');
    map.set('d', 5);
    assertDeepEquals({done: false, value: 'b'}, iter.next());
    assertDeepEquals({done: false, value: 'd'}, iter.next());
    map.set('e', 6);
    map.delete('d');
    map.set('f', 7);
    map.delete('e');
    assertDeepEquals({done: false, value: 'f'}, iter.next());
    assertDeepEquals(DONE, iter.next());
    map.set('g', 8);
    assertDeepEquals(DONE, iter.next());
  }
});
