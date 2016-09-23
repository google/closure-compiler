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

goog.module('jscomp.runtime_tests.polyfill_tests.reflect_get_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');
const userAgent = goog.require('goog.userAgent');

const noCheck = testing.noCheck;

testSuite({
  shouldRunTests() {
    // Not polyfilled to ES3
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testGet() {
    const obj = {'x': 5, 12: 13};
    assertEquals(5, Reflect.get(obj, 'x'));
    assertEquals(13, Reflect.get(obj, '12'));
    assertEquals(13, Reflect.get(obj, noCheck(12)));

    const sub = Object.create(obj);
    sub[12] = 15;
    assertEquals(5, Reflect.get(sub, 'x'));
    assertEquals(15, Reflect.get(sub, '12'));
  },

  testGet_getter() {
    const obj = {x: 1};
    Object.defineProperty(obj, 'foo', {get() { return 5; }});
    /**
     * @this {{x: number}}
     * @return {number}
     */
    const func = function() { return this.x + 12; };
    Object.defineProperty(obj, 'bar', {get: func});

    assertEquals(5, Reflect.get(obj, 'foo'));
    assertEquals(13, Reflect.get(obj, 'bar'));
    assertEquals(15, Reflect.get(obj, 'bar', {x: 3}));

    const sub = Object.create(obj);
    sub.x = 14;

    assertEquals(5, Reflect.get(sub, 'foo'));
    assertEquals(26, Reflect.get(sub, 'bar'));
    assertEquals(16, Reflect.get(sub, 'bar', {x: 4}));
  },

  testGet_getterThrows() {
    const obj = Object.create(null, {'x': {get() { throw new Error('err'); }}});
    assertThrows(() => Reflect.get(obj, 'x'));
  },
});
