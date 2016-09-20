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

goog.module('jscomp.runtime_tests.polyfill_tests.weakset_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');

const IE8 = userAgent.IE && !userAgent.isVersionOrHigher(9);

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

testSuite({
  testObjectKeys() {
    const set = new WeakSet();

    const key1 = {};
    const key2 = {};
    const key3 = {};
    checkSetHas(set, key1);
    checkSetHas(set, key2);
    checkSetHas(set, key3);

    assertTrue(set.has(key1));
    assertTrue(set.has(key2));
    assertFalse(set.has({}));
  },

  testDelete() {
    const set = new WeakSet();
    const key = {};
    set.add(key);
    assertTrue(set.has(key));
    assertTrue(set.delete(key));
    assertFalse(set.delete(key));
    assertFalse(set.delete({}));
    assertFalse(set.has(key));
  },

  testSealedKeys() {
    if (!Object.seal) return;

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

  testKeyIdNotEnumerable() {
    if (IE8) return;
    const set = new WeakSet();
    const key = {};
    set.add(key);
    for (let k in key) {
      fail('Expected no enumerable properties in key but got ' + k);
    }
  },

  testConstructor_generator() {
    const a = {};
    const b = {};
    const init = function*() {
      yield a;
      yield b;
    };
    const set = new WeakSet(init());
    assertTrue(set.has(a));
    assertFalse(set.has({}));
  },

  testConstructor_array() {
    const a = {};
    const b = {};
    const set = new WeakSet([a, b]);
    assertTrue(set.has(a));
    assertTrue(set.has(b));
    assertFalse(set.has({}));
  },
});
