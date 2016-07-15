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

goog.module('jscomp.runtime_tests.polyfill_tests.object_setprototypeof_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');

/** @type {boolean} */
const LEGACY = userAgent.IE && !userAgent.isVersionOrHigher(11);

testSuite({
  shouldRunTests() {
    // Not polyfilled to ES3
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testSetPrototypeOf_modern() {
    if (LEGACY) return;

    const obj = {};
    const sub = {};
    Object.setPrototypeOf(sub, obj);
    obj.x = 23;
    assertObjectEquals({x: 23}, sub);
  },

  testSetPrototypeOf_legacy() {
    if (!LEGACY) return;
    assertThrows(() => Object.setPrototypeOf({}, {}));
  }
});
