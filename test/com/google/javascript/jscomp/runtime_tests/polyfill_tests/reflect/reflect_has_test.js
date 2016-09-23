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

goog.module('jscomp.runtime_tests.polyfill_tests.reflect_has_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');
const userAgent = goog.require('goog.userAgent');

const noCheck = testing.noCheck;
const objectCreate = testing.objectCreate;

/** @type {boolean} */
const LEGACY = userAgent.IE && !userAgent.isVersionOrHigher(9);

testSuite({
  testHas() {
    const obj = {'x': 5, 12: 13};
    assertTrue(Reflect.has(obj, 'x'));
    assertTrue(Reflect.has(obj, noCheck(12)));
    assertTrue(Reflect.has(obj, '12'));
    assertFalse(Reflect.has(obj, 'y'));
    assertFalse(Reflect.has(obj, noCheck(13)));

    const sub = objectCreate(obj);
    sub[12] = 15;
    sub['y'] = 99;

    assertTrue(Reflect.has(sub, 'x'));
    assertTrue(Reflect.has(sub, 'y'));
    assertTrue(Reflect.has(sub, '12'));

    assertFalse(Reflect.has(sub, 'a'));
    assertFalse(Reflect.has(sub, 'b'));
    assertFalse(Reflect.has(sub, noCheck(13)));

    // Test getters and non-enumerable properties (except on ES3 browsers)
    if (LEGACY) return;
    Object.defineProperty(sub, 'a', {get() { return 2; }});
    Object.defineProperty(sub, 'b', {value: 9});
    assertTrue(Reflect.has(sub, 'a'));
    assertTrue(Reflect.has(sub, 'b'));
  },
});
