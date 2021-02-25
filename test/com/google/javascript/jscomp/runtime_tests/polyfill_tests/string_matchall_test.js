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

goog.module('jscomp.runtime_tests.polyfill_tests.string_matchall_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const assertFails = testing.assertFails;
const assertRegExpResultEquals = testing.assertRegExpResultEquals;

/**
 * Helper function for creating expected results
 * @param {!Array<string>} matches
 * @param {number} index
 * @param {string} input
 * @return {!Object}
 */
function resultBuilder(matches, index, input) {
  let result = {};
  let value = [...matches];
  value.index = index;
  value.input = input;
  result.value = value;
  result.done = false;
  return result;
}

/**
 * Custom assert for String.prototype.matchAll
 */
function assertMatchAllEquals(expected, actual) {
  assertEquals(expected.done, actual.done);
  assertRegExpResultEquals(expected.value, actual.value);
}

const endOfIterator = {
  value: undefined,
  done: true
};

testSuite({
  testMatchAll_validRegExString() {
    let regexString = 'abc';
    let testString = 'abcabc';

    let matchAllIterator = testString.matchAll(regexString);
    let firstResult = resultBuilder(['abc'], 0, testString);
    let secondResult = resultBuilder(['abc'], 3, testString);


    assertMatchAllEquals(firstResult, matchAllIterator.next());
    assertMatchAllEquals(secondResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  },

  testMatchAll_invalidRegExString() {
    assertFails(Error, () => 'abc'.matchAll('(abc'));
  },

  disabledTestMatchAll_regExNoGlobalTag_shouldThrowError() {
    // TODO(annieyw): enable this when chrome is following the correct behaviour
    // V8 has implemented the change already and it will ship with M80.
    assertFails(TypeError, () => 'test1test2'.matchAll(/test/));
  },

  testMatchAll_validRegExGlobalTag() {
    let regex = /test/g;
    let testString = 'test1test2';

    let matchAllIterator = testString.matchAll(regex);
    let firstResult = resultBuilder(['test'], 0, testString);
    let secondResult = resultBuilder(['test'], 5, testString);

    assertMatchAllEquals(firstResult, matchAllIterator.next());
    assertMatchAllEquals(secondResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  },

  testMatchAll_emptyArray() {
    let testString = 'a';

    let matchAllIterator = testString.matchAll( /** @type {?} */ ([]));
    let firstResult = resultBuilder([''], 0, testString);
    let secondResult = resultBuilder([''], 1, testString);

    assertMatchAllEquals(firstResult, matchAllIterator.next());
    assertMatchAllEquals(secondResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  },

  testMatchAll_number() {
    let testString = '2';

    let matchAllIterator = testString.matchAll( /** @type {?} */ (2));
    let firstResult = resultBuilder(['2'], 0, testString);

    assertMatchAllEquals(firstResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  },

  testMatchAll_null() {
    let testString = 'null';

    let matchAllIterator = testString.matchAll( /** @type {?} */ (null));
    let firstResult = resultBuilder(['null'], 0, testString);

    assertMatchAllEquals(firstResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  },

  testMatchAll_stringArray() {
    let testString = 'a,b';

    let matchAllIterator = testString.matchAll( /** @type {?} */ (['a', 'b']));
    let firstResult = resultBuilder(['a,b'], 0, testString);

    assertMatchAllEquals(firstResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  },

  testMatchAll_capturingGroups() {
    let regex = /t(e)(st(\d?))/g;
    let testString = 'test1test2';

    let matchAllIterator = testString.matchAll(regex);
    let firstResult =
        resultBuilder(['test1', 'e', 'st1', '1'], 0, testString);
    let secondResult =
        resultBuilder(['test2', 'e', 'st2', '2'], 5, testString);

    assertMatchAllEquals(firstResult, matchAllIterator.next());
    assertMatchAllEquals(secondResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  },

  testMatchAll_emptyMatchGlobal() {
    let regex = /.?/g;
    let testString = 'ab';

    let matchAllIterator = testString.matchAll(regex);
    let firstResult = resultBuilder(['a'], 0, testString);
    let secondResult = resultBuilder(['b'], 1, testString);
    let thirdResult = resultBuilder([''], 2, testString);

    assertMatchAllEquals(firstResult, matchAllIterator.next());
    assertMatchAllEquals(secondResult, matchAllIterator.next());
    assertMatchAllEquals(thirdResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  },

  testMatchAll_emptyMatchStartOfStringGlobal() {
    let regex = /^/g;
    let testString = 'abc';

    let matchAllIterator = testString.matchAll(regex);
    let firstResult = resultBuilder([''], 0, testString);

    assertMatchAllEquals(firstResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
    // Calling exec repeatedly would always report a match on the empty
    // beginning of string, but matchAll() iterator will never report the same
    // string twice.
  },

  testMatchAll_noLoopingAfterFinishedMatching() {
    let regex = /a/g;
    let testString = 'a';

    let matchAllIterator = testString.matchAll(regex);
    let firstResult = resultBuilder(['a'], 0, testString);

    assertMatchAllEquals(firstResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
    // iterator should continue to report done, if next() is called again
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  },

  testMatchAll_noLoopingZeroWidthMatches_matchesOverlap() {
    let regex = /(?=start_(\d) (.+?) end_\1)/g;  // Notice the lookahead wrapper
    let testString = 'start_0 start_1 middle end_1 end_0';

    let matchAllIterator = testString.matchAll(regex);
    let first = resultBuilder(['', '0', 'start_1 middle end_1'], 0, testString);
    let second = resultBuilder(['', '1', 'middle'], 8, testString);

    assertMatchAllEquals(first, matchAllIterator.next());
    assertMatchAllEquals(second, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  },

  testMatchAll_noLoopingZeroWidthMatches_matchesDontOverlap() {
    let regex = /\b/g;
    let testString = 'hello world';

    let itr = testString.matchAll(regex);

    assertMatchAllEquals(resultBuilder([''], 0, testString), itr.next());
    assertMatchAllEquals(resultBuilder([''], 5, testString), itr.next());
    assertMatchAllEquals(resultBuilder([''], 6, testString), itr.next());
    assertMatchAllEquals(resultBuilder([''], 11, testString), itr.next());
    assertObjectEquals(endOfIterator, itr.next());
  },
});
