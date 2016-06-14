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

goog.module('jscomp.runtime_tests.polyfill_tests.set_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');
const userAgent = goog.require('goog.userAgent');

const {
  assertIteratorContents,
} = testing;

const DONE = {done: true, value: void 0};

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

testSuite({
  testAdd_objects() {
    const set = new Set();
    assertEquals(0, set.size);
    checkAddHas(set, {});
    checkAddHas(set, {});
    checkAddHas(set, {});
    assertEquals(3, set.size);
  },

  testAdd_numbers() {
    const set = new Set();
    for (let i = 10; i < 20; i++) {
      checkAddHas(set, i);
      checkAddHas(set, i / 10);
      checkAddHas(set, String(i));
    }
    const keys = [+0, +Infinity, -Infinity, true, false, null, undefined];
    for (let i = 0; i < keys.length; i++) {
      checkAddHas(set, keys[i]);
    }
    assertEquals(37, set.size);

    // Note: +0 and -0 are the same
    assertTrue(set.has(-0));
    assertTrue(set.delete(-0));
    assertFalse(set.has(0));
  },

  testAdd_nans() {
    const set = new Set();
    assertFalse(set.has(NaN));
    set.add(NaN);
    assertTrue(set.has(NaN));
    assertTrue(set.has(NaN + 1));
    assertTrue(set.delete(NaN));
    assertFalse(set.has(NaN));
  },

  testAdd_sealedObjects() {
    // NOTE: IE8 doesn't support Object.seal.
    if (userAgent.IE && !userAgent.isVersionOrHigher(9)) return;
    const key1 = {};
    const key2 = {};
    const key3 = {};
    Object.preventExtensions(key1);
    Object.seal(key2);
    Object.freeze(key3);

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

  testKeyIdNotEnumerable() {
    // NOTE: IE8 doesn't support non-enumerable properties.
    if (userAgent.IE && !userAgent.isVersionOrHigher(9)) return;
    const set = new Set();
    const key = {};
    set.add(key);
    for (let k in key) {
      fail('Expected no enumerable properties in key but got ' + k);
    }
  },

  testDelete() {
    const set = new Set();
    const key = {};
    set.add(key);
    assertEquals(1, set.size);
    assertTrue(set.delete(key));
    assertEquals(0, set.size);
    assertFalse(set.delete(key));
    assertFalse(set.delete({}));

    set.add(0);
    assertTrue(set.delete(-0));
    assertEquals(0, set.size);
  },

  testClear() {
    const set = new Set();
    set.add('a');
    set.add('b');
    assertEquals(2, set.size);
    set.clear();
    assertEquals(0, set.size);
    assertFalse(set.has('a'));
  },

  testConstructor_generator() {
    const init = function*() {
      yield 'a';
      yield 'b';
    };
    const set = new Set(init());
    assertEquals(2, set.size);
    assertTrue(set.has('a'));
    assertTrue(set.has('b'));
  },

  testConstructor_array() {
    const set = new Set(['a', 'b']);
    assertEquals(2, set.size);
    assertTrue(set.has('a'));
    assertTrue(set.has('b'));
  },

  testForEach() {
    const set = new Set();
    set.add('a');
    set.add('b');
    const /** !Array<*> */ out = [];
    const receiver = {};
    /**
     * @this {!Object}
     * @param {string} value
     * @param {string} key
     * @param {!Set<string>} set
     */
    function func(value, key, set) {
      out.push(value, key);
      out.push(set);
      out.push(this);
    }
    set.forEach(func, receiver);
    assertArrayEquals(['a', 'a', set, receiver, 'b', 'b', set, receiver], out);
  },

  testForEach_concurrentMutation() {
    const /** !Set */ set = new Set();
    set.add('a');
    set.add('a1');
    const keys = [];
    set.forEach(function(value, key) {
      keys.push(key);
      if (set.delete(key + '1')) {
        set.add(key + '2');
      }
    });
    assertArrayEquals(['a', 'a2'], keys);
  },

  testForEach_clear() {
    const /** !Set */ set = new Set();
    set.add('a');
    set.add('b');
    let count = 0;
    set.forEach(function(value, key) {
      count++;
      set.clear();
      if (count < 5) set.add('c');
    });
    assertEquals(5, count);
  },

  testEntries() {
    const set = new Set();
    set.add('a');
    set.add('b');
    const iter = set.entries();
    assertObjectEquals({done: false, value: ['a', 'a']}, iter.next());
    assertObjectEquals({done: false, value: ['b', 'b']}, iter.next());
    assertObjectEquals(DONE, iter.next());
    assertObjectEquals(DONE, iter.next());
  },

  testValues() {
    const set = new Set();
    set.add('b');
    set.add('c');
    const iter = set.values();
    assertObjectEquals({done: false, value: 'b'}, iter.next());
    assertObjectEquals({done: false, value: 'c'}, iter.next());
    assertObjectEquals(DONE, iter.next());
    assertObjectEquals(DONE, iter.next());
  },

  testValues_continuesAfterClear() {
    const set = new Set();
    set.add('a');
    set.add('b');
    const iter = set.values();
    set.clear();
    set.add('c');
    assertObjectEquals({done: false, value: 'c'}, iter.next());
    set.clear();
    set.add('d');
    assertObjectEquals({done: false, value: 'd'}, iter.next());
    assertObjectEquals(DONE, iter.next());
    // But once it's done, it doesn't come back
    set.add('e');
    assertObjectEquals(DONE, iter.next());
  },

  testKeys_continuesAfterDelete() {
    const set = new Set();
    set.add('a');
    set.add('b');
    set.add('c');
    const iter = set.values();
    set.delete('a');
    set.add('b');
    set.delete('c');
    set.add('d');
    assertObjectEquals({done: false, value: 'b'}, iter.next());
    assertObjectEquals({done: false, value: 'd'}, iter.next());
    set.add('e');
    set.delete('d');
    set.add('f');
    set.delete('e');
    assertObjectEquals({done: false, value: 'f'}, iter.next());
    assertObjectEquals(DONE, iter.next());
    set.add('g');
    assertObjectEquals(DONE, iter.next());
  },

  testIterator() {
    const set = new Set();
    set.add('d');
    set.add(2);
    set.add('c');
    set.add(1);
    assertIteratorContents(set, 'd', 2, 'c', 1);
  },
});
