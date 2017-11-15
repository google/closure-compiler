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

goog.module('jscomp.runtime_tests.polyfill_tests.object_getownpropertydescriptors_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');


testSuite({
  shouldRunTests() {
    // Not polyfilled to ES3
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testGetOwnPropertyDescriptors() {
    const obj = {'x': 5, 12: 13};
    assertObjectEquals({
      'x': {value: 5, configurable: true, enumerable: true, writable: true},
      12:  {value: 13, configurable: true, enumerable: true, writable: true},
    }, Object.getOwnPropertyDescriptors(obj));

    const sub = Object.create(obj);
    sub[12] = 15;
    sub['y'] = 23;
    assertObjectEquals({
      'y': {value: 23, configurable: true, enumerable: true, writable: true},
      12: {value: 15, configurable: true, enumerable: true, writable: true},
    }, Object.getOwnPropertyDescriptors(sub));
  },

  testGetOwnPropertyDescriptors_symbol() {
    const sym = Symbol();
    const obj = {[sym]: 5, 'a': 12};
    assertObjectEquals({
      [sym]: {value: 5, configurable: true, enumerable: true, writable: true},
      'a':  {value: 12, configurable: true, enumerable: true, writable: true},
    }, Object.getOwnPropertyDescriptors(obj));
  },

  testGetOwnPropertyDescriptors_getterAndSetter() {
    const obj = {};
    const get = function() { return 42; };
    /**
     * @param {number} x
     * @this {!Object}
     */
    const set = function(x) { this.x = x; };
    Object.defineProperty(obj, 'foo', {get: get, set: set});
    assertObjectEquals(
        {'foo': {get: get, set: set, enumerable: false, configurable: false}},
        Object.getOwnPropertyDescriptors(obj));
  },

  testGetOwnPropertyDescriptors_nonEnumerable() {
    const obj = {};
    Object.defineProperty(obj, 'a', {value: 42});
    Object.defineProperty(obj, 'b', {value: 17, configurable: true});
    assertObjectEquals({
      'a': {value: 42, enumerable: false, configurable: false, writable: false},
      'b': {value: 17, enumerable: false, configurable: true, writable: false},
    }, Object.getOwnPropertyDescriptors(obj));
  },
});
