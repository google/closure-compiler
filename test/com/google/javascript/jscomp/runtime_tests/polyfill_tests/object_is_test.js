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

goog.module('jscomp.runtime_tests.polyfill_tests.object_is_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
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
