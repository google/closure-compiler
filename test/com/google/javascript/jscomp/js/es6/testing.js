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

goog.module('testing');
goog.setTestOnly();

/**
 * Asserts deep equality for objects and arrays.
 * @param {*} expected
 * @param {*} actual
 */
exports.assertDeepEquals = function(expected, actual) {
  assertEquals(
      'both arrays', expected instanceof Array, actual instanceof Array);
  assertEquals(
      'both objects', expected instanceof Object, actual instanceof Object);
  if (expected instanceof Array) {
    assertEquals(expected.length, actual.length);
    for (let i = 0; i < expected.length; i++) {
      exports.assertDeepEquals(expected[i], actual[i]);
    }
  } else if (expected instanceof Object) {
    for (let key in expected) {
      assertTrue('Missing key: ' + key, key in actual);
      exports.assertDeepEquals(expected[key], actual[key]);
    }
    for (let key in actual) {
      assertTrue('Unexpected key: ' + key, key in expected);
    }
  } else {
    assertEquals(expected, actual);
  }
};
