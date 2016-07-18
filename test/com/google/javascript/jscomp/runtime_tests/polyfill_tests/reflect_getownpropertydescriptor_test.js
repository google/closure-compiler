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

goog.module('jscomp.runtime_tests.polyfill_tests.reflect_getownpropertydescriptor_test');
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

  testObject() {
    const o = {'x': 5};
    const s = Object.create(o);
    s['x'] = 6;
    if (Object.getOwnPropertyDescriptor(s, 'x') == null) {
      fail('expected not null');
    }
  },

  testGetOwnPropertyDescriptor() {
    const obj = {'x': 5, 12: 13};
    assertObjectEquals(
        {value: 5, configurable: true, enumerable: true, writable: true},
        Reflect.getOwnPropertyDescriptor(obj, 'x'));
    assertObjectEquals(
        {value: 13, configurable: true, enumerable: true, writable: true},
        Reflect.getOwnPropertyDescriptor(obj, '12'));
    assertObjectEquals(
        {value: 13, configurable: true, enumerable: true, writable: true},
        Reflect.getOwnPropertyDescriptor(obj, noCheck(12)));
    assertUndefined(Reflect.getOwnPropertyDescriptor(obj, 'y'));

    const sub = Object.create(obj);
    sub[12] = 15;
    // NOTE(sdh): without this assignment, the final assertion in this test
    // (on getOwnPropertyDescriptor(sub, '12')) will fail in IE11, since it
    // inexplicably (but consistently) returns undefined, but only when run
    // in headless mode, making it nearly impossible to debug.  For whatever
    // reason, assigning to sub['y'] ameliorates the problem.
    sub['y'] = 23;
    assertUndefined(Reflect.getOwnPropertyDescriptor(sub, 'x'));
    assertObjectEquals(
        {value: 23, configurable: true, enumerable: true, writable: true},
        Reflect.getOwnPropertyDescriptor(sub, 'y'));
    assertObjectEquals(
        {value: 15, configurable: true, enumerable: true, writable: true},
        Reflect.getOwnPropertyDescriptor(sub, '12'));
  },

  testGetOwnPropertyDescriptor_getterAndSetter() {
    const obj = {};
    const get = function() { return 42; };
    /**
     * @param {number} x
     * @this {!Object}
     */
    const set = function(x) { this.x = x; };
    Object.defineProperty(obj, 'foo', {get: get, set: set});
    assertObjectEquals(
        {get: get, set: set, enumerable: false, configurable: false},
        Reflect.getOwnPropertyDescriptor(obj, 'foo'));
  },

  testGetOwnPropertyDescriptor_nonEnumerable() {
    const obj = {};
    Object.defineProperty(obj, 'foo', {value: 42});
    Object.defineProperty(obj, 'bar', {value: 17, configurable: true});
    assertObjectEquals(
        {value: 42, enumerable: false, configurable: false, writable: false},
        Reflect.getOwnPropertyDescriptor(obj, 'foo'));
    assertObjectEquals(
        {value: 17, enumerable: false, configurable: true, writable: false},
        Reflect.getOwnPropertyDescriptor(obj, 'bar'));
  },
});
