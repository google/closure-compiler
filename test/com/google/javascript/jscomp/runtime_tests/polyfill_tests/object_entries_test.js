/*
 * Copyright 2017 The Closure Compiler Authors.
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

goog.module('jscomp.runtime_tests.polyfill_tests.object_entries_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const assertPropertyListEquals = testing.assertPropertyListEquals;

const SYMBOL_IS_POLYFILLED = typeof Symbol('') === 'string';

testSuite({
  testEntries() {
    assertPropertyListEquals(
        [['1', 4], ['a', 1], ['c', 2], ['b', 3]],
        Object.entries({'a': 1, 'c': 2, 'b': 3, 1: 4}));
    if (!SYMBOL_IS_POLYFILLED) {
      assertPropertyListEquals([], Object.entries({[Symbol()]: 1}));
    }
  },
});
