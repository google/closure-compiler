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

goog.module('$jscomp_object_test');
goog.setTestOnly();

const jsunit = goog.require('goog.testing.jsunit');
const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('testing');

const assertDeepEquals = testing.assertDeepEquals;

testSuite({

  testAssertDeepEquals() {
    // Quick sanity check, since we don't unit test assertDeepEquals
    assertDeepEquals({a: 4}, {a: 4});
    assertThrowsJsUnitException(() => assertDeepEquals({}, {a: 4}));
    assertThrowsJsUnitException(() => assertDeepEquals({a: 4}, {}));
  },

  testAssign_simple() {
    const obj = {a: 2, z: 3};
    assertEquals(obj, Object.assign(obj, {a: 4, b: 5}, null, {c: 6, b: 7}));
    assertDeepEquals({a: 4, b: 7, c: 6, z: 3}, obj);
  },

  testAssign_skipsPrototypeProperties() {
    if (!Object.create) return;
    const proto = {a: 4, b: 5};
    const from = Object.create(proto);
    from.a = 6;
    from.c = 7;
    assertDeepEquals({a: 6, c: 7}, Object.assign({}, from));
    assertDeepEquals({a: 6, b: 1, c: 7}, Object.assign({b: 1}, from));
  },

  testAssign_skipsNonEnumerableProperties() {
    const from = {'b': 23};
    try {
      Object.defineProperty(from, 'a', {enumerable: false, value: 42});
    } catch (err) {
      return; // Object.defineProperty in IE8 test harness exists, always fails
    }
    assertDeepEquals({'b': 23}, Object.assign({}, from));
    assertDeepEquals({'a': 1, 'b': 23}, Object.assign({'a': 1}, from));
  },

  testIs() {
    assertTrue(Object.is(4, 4));
    assertTrue(Object.is(0, 0));
    assertTrue(Object.is('4', '4'));
    assertTrue(Object.is('', ''));
    assertTrue(Object.is(true, true));
    assertTrue(Object.is(false, false));
    assertTrue(Object.is(null, null));
    assertTrue(Object.is(undefined, undefined));
    assertTrue(Object.is(NaN, NaN));
    const obj = {};
    assertTrue(Object.is(obj, obj));

    assertFalse(Object.is(0, -0));
    assertFalse(Object.is({}, {}));
    assertFalse(Object.is(4, '4'));
    assertFalse(Object.is(null, void 0));
    assertFalse(Object.is(1, true));
    assertFalse(Object.is(0, false));
    assertFalse(Object.is('', false));
  }
});
