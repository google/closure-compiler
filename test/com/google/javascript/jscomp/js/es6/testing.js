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
 * @param {string=} position
 */
exports.assertDeepEquals = function(expected, actual, position = '') {
  position = position || '<actual>';
  assertEquals(position + ' instanceof Array',
      expected instanceof Array, actual instanceof Array);
  assertEquals(position + ' instanceof Object',
      expected instanceof Object, actual instanceof Object);
  if (expected instanceof Array) {
    assertEquals(position + '.length', expected.length, actual.length);
    for (let i = 0; i < expected.length; i++) {
      exports.assertDeepEquals(expected[i], actual[i], `${position}[${i}]`);
    }
  } else if (expected instanceof Object) {
    for (let key in expected) {
      assertTrue(`Missing key: ${position}.${key}`, key in actual);
      exports.assertDeepEquals(
          expected[key], actual[key], `${position}.${key}`);
    }
    for (let key in actual) {
      assertTrue(`Unexpected key: ${position}.${key}`, key in expected);
    }
  } else {
    assertEquals(position, expected, actual);
  }
};
