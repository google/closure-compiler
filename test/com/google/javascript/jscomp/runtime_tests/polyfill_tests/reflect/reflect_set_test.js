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

goog.module('jscomp.runtime_tests.polyfill_tests.reflect_set_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');

testSuite({
  shouldRunTests() {
    // Not polyfilled to ES3
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testSet() {
    const obj = {'x': 5, 12: 13};
    assertTrue(Reflect.set(obj, 'x', 6));
    assertEquals(6, obj['x']);

    assertTrue(Reflect.set(obj, 'y', 12));
    assertEquals(12, obj['y']);
  },

  testSet_notWritable() {
    const obj = {};
    Object.defineProperty(obj, 'x', {value: 5});
    assertFalse(Reflect.set(obj, 'x', 12));
    assertEquals(5, obj['x']);

    const sub = Object.create(obj);

    assertFalse(Reflect.set(sub, 'x', 12));
    assertEquals(5, sub['x']);

    Object.defineProperty(sub, 'x', {writable: true, value: 12});
    assertTrue(Reflect.set(sub, 'x', 13));
    assertEquals(13, sub['x']);
  },

  testSet_notExtensible() {
    const obj = {'x': 12};
    Object.seal(obj);

    assertTrue(Reflect.set(obj, 'x', 13));
    assertEquals(13, obj['x']);

    assertFalse(Reflect.set(obj, 'y', 14));
    assertObjectEquals({'x': 13}, obj);

    Object.freeze(obj);
    assertFalse(Reflect.set(obj, 'x', 15));
    assertObjectEquals({'x': 13}, obj);
  },

  testSet_setter() {
    const calls = [];
    const obj =
        Object.create({a: 11}, {'x': {
          /**
           * @param {number} x
           * @this {{a: number}}
           */
          set(x) { calls.push([this.a, x]); }
        }});
    const sub = Object.create(obj, {a: {value: 13}});

    assertTrue(Reflect.set(obj, 'x', 2));
    assertTrue(Reflect.set(sub, 'x', 3));
    assertTrue(Reflect.set(obj, 'x', 5, {a: 17}));
    assertTrue(Reflect.set(sub, 'x', 7, {a: 19}));

    Object.defineProperty(sub, 'x', {writable: true, value: 1});
    assertTrue(Reflect.set(sub, 'x', 14));

    assertObjectEquals([[11, 2], [13, 3], [17, 5], [19, 7]], calls);
  },

  testSet_setterThrows() {
    const obj = Object.create(null, {'x': {set() { throw new Error('err'); }}});
    assertThrows(() => Reflect.set(obj, 'x', 13));
  },
});
