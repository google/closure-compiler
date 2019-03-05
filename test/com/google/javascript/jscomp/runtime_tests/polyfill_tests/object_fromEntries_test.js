/*
 * Copyright 2019 The Closure Compiler Authors.
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

/**
 * @fileoverview Tests for the Object.fromEntries polyfill.
 */

goog.module('jscomp.runtime_tests.es_2019.object');

goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testNullAndUndefinedAreError() {
    let /** ? */ fn = Object.fromEntries;
    assertThrows(() => fn());
    assertThrows(() => fn(undefined));
    assertThrows(() => fn(null));
  },

  testYieldNonObjectIsError() {
    assertThrows(() => Object.fromEntries([0]));
  },

  testEmptyArray() {
    assertObjectEquals({}, Object.fromEntries([]));
  },

  testEmptyTuple() {
    assertObjectEquals({'undefined': undefined}, Object.fromEntries([[]]));
  },

  testFromArrayOfTuples() {
    assertObjectEquals({a: 0, b: 1}, Object.fromEntries([['a', 0], ['b', 1]]));
  },

  testFromIterableOfTuples() {
    assertObjectEquals({a: 0, b: 1}, Object.fromEntries((function*() {
      yield ['a', 0];
      yield ['b', 1];
    })()));
  },

  testFromArrayOfObjects() {
    assertObjectEquals(
        {a: 0, b: 1},
        Object.fromEntries([{0: 'a', 1: 0}, {0: 'b', 1: 1}]));
  },

  testFromIterableOfObjects() {
    assertObjectEquals({a: 0, b: 1}, Object.fromEntries((function*() {
      yield {0: 'a', 1: 0};
      yield {0: 'b', 1: 1};
    })()));
  },

  testFromMap() {
    assertObjectEquals(
        {a: 0, b: 1}, Object.fromEntries(new Map([['a', 0], ['b', 1]])));
    assertObjectEquals(
        {a: 0, b: 1},
        Object.fromEntries(new Map(Object.entries({a: 0, b: 1}))));
  },

  testDuplicateKeysOverrides() {
    assertObjectEquals({a: 1}, Object.fromEntries([['a', 0], ['a', 1]]));
  },
});
