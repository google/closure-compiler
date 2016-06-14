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

goog.module('jscomp.runtime_tests.polyfill_tests.array_from_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  iterable,
  noCheck,
} = testing;

testSuite({
  shouldRunTests() {
    // Disable tests for IE8 and below.
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testFrom() {
    const arr = ['a', 2, 'c'];
    assertNotEquals(arr, Array.from(arr));
    assertObjectEquals(arr, Array.from(arr));

    assertObjectEquals(
        ['a', void 0, 42], Array.from({length: 3, 0: 'a', 2: 42}));
    assertObjectEquals(['a', void 0], Array.from({length: 2, 0: 'a', 2: 42}));
    assertObjectEquals(['a', 'c', 'b'], Array.from(noCheck('acb')));
    assertObjectEquals(['a', 'c', 'b'], Array.from(noCheck('acb')));

    (function(var_args) {
      assertObjectEquals(['x', 'y'], Array.from(arguments));
    })('x', 'y');

    assertObjectEquals(['x', 'y'], Array.from(iterable('x', 'y')));

    const x2 = x => x + x;
    assertObjectEquals([2, 4, 8], Array.from([1, 2, 4], x2));
    assertObjectEquals(['aa', 'cc', 'bb'], Array.from(noCheck('acb'), x2));
    assertObjectEquals([6], Array.from({length: 1, 0: 3}, x2));
    assertObjectEquals([6, 'xx'], Array.from(iterable(3, 'x'), x2));

    /**
     * @this {!Function}
     * @param {?} x
     * @return {?}
     */
    const applyThisTwice = function(x) { return this(this(x)); };
    assertObjectEquals([4, 8, 16], Array.from([1, 2, 4], applyThisTwice, x2));
    assertObjectEquals(['aaaa'], Array.from(noCheck('a'), applyThisTwice, x2));
  },
});
