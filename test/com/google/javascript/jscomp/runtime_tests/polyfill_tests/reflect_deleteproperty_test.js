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

goog.module('jscomp.runtime_tests.polyfill_tests.reflect_deleteproperty_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

const LEGACY = typeof Object.defineProperties !== 'function';
const MODERN = typeof Object.defineProperties === 'function';

testSuite({
  testDeleteProperty() {
    const obj = {'x': 23, 'y': 42};
    if (LEGACY) {
      obj['z'] = 281;
    } else {
      Object.defineProperty(obj, 'z', {value: 281});
    }

    assertTrue(Reflect.deleteProperty(obj, 'x'));
    assertFalse('x' in obj);
    assertEquals(LEGACY, Reflect.deleteProperty(obj, 'z'));
    assertEquals(MODERN, 'z' in obj);
    assertEquals(42, obj['y']);
  },

  testDeleteProperty_notConfigurable() {
    if (LEGACY) return;

    const obj = Object.create(null, {'x': {writable: true, value: 12}});
    assertFalse(Reflect.deleteProperty(obj, 'x'));
    assertTrue('x' in obj);

    const sub = Object.create(obj);
    assertTrue(Reflect.deleteProperty(sub, 'x'));
    assertTrue('x' in sub);
  },

  testDeleteProperty_sealed() {
    if (LEGACY) return;

    const obj = {'x': 12, 'y': 15};
    Object.preventExtensions(obj);
    assertTrue(Reflect.deleteProperty(obj, 'x'));
    assertFalse('x' in obj);

    Object.seal(obj);
    assertFalse(Reflect.deleteProperty(obj, 'y'));
    assertTrue('y' in obj);
  },
});
