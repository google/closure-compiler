/*
 * Copyright 2022 The Closure Compiler Authors.
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

goog.module('jscomp.runtime_tests.polyfill_tests.object_hasOwn_test');

goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testHasOwn1() {
    let object1 = {'foo': false};
    assertEquals(true, Object.hasOwn(object1, 'foo'));
    assertEquals(false, Object.hasOwn(object1, 'bar'));
  },

  testHasOwn2() {
    let object2 = Object.create({'foo': true});
    assertEquals(false, Object.hasOwn(object2, 'foo'));
    assertEquals(false, Object.hasOwn(object2, 'bar'));
  },

  testHasOwn3() {
    let object3 = Object.create(null);
    assertEquals(false, Object.hasOwn(object3, 'foo'));

    object3['bar'] = false;
    assertEquals(true, Object.hasOwn(object3, 'bar'));
  }

});
