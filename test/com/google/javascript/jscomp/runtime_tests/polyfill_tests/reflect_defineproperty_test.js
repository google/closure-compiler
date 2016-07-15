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

goog.module('jscomp.runtime_tests.polyfill_tests.reflect_defineproperty_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');

testSuite({
  shouldRunTests() {
    // Not polyfilled to ES3
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testDefineProperty() {
    let obj = {};
    assertTrue(Reflect.defineProperty(obj, 'foo', {value: 23}));
    assertEquals(23, obj['foo']);
    assertObjectEquals(
        {value: 23, writable: false, enumerable: false, configurable: false},
        Object.getOwnPropertyDescriptor(obj, 'foo'));

    let x = 10;
    assertTrue(
        Reflect.defineProperty(
            obj, 'bar', {get() { return x++; }, set(y) { x += y; }}));
    assertEquals(10, obj['bar']);
    assertEquals(11, obj['bar']);
    obj['bar'] = 4;
    assertEquals(16, obj['bar']);

    assertTrue(Reflect.defineProperty(obj, 'baz', {value: 2, writable: true}));
    assertEquals(2, obj['baz']);
    assertObjectEquals(
        {value: 2, writable: true, enumerable: false, configurable: false},
        Object.getOwnPropertyDescriptor(obj, 'baz'));

    assertFalse(Reflect.defineProperty(obj, 'foo', {value: 4, writable: true}));
    assertEquals(23, obj['foo']);
  },
});
