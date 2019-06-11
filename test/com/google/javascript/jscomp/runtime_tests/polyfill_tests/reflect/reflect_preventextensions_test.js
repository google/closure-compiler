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

goog.module('jscomp.runtime_tests.polyfill_tests.reflect_preventextensions_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

const LEGACY = typeof Object.defineProperties !== 'function';
const MODERN = typeof Object.defineProperties === 'function';

testSuite({
  testPreventExtensions_modern() {
    if (LEGACY) return;

    let obj = {'a': 21};
    assertTrue(Reflect.preventExtensions(obj));
    assertFalse(Object.isExtensible(obj));
    assertTrue(Reflect.preventExtensions(obj));

    obj['a'] = 15;
    assertEquals(15, obj['a']);

    try {
      // TODO(sdh): why does this not *always* throw, since goog.module?
      obj['b'] = 12;
    } catch (ok) { /* note: will only throw in strict mode */ }
    assertFalse('b' in obj);
  },

  testPreventExtensions_legacy() {
    if (MODERN) return;
    assertFalse(Reflect.preventExtensions({}));
  },
});
