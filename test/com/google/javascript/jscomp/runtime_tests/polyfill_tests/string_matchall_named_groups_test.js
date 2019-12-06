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

goog.module('jscomp.runtime_tests.polyfill_tests.string_matchall_named_groups_test');
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
 * @param {!Object.<string, string>} groups
 * @return {!Object}
 */
function resultBuilder(matches, index, input, groups) {
  let result = {};
  let value = [...matches];
  value.index = index;
  value.input = input;
  value.groups = groups;
  result.value = value;
  result.done = false;
  return result;
}

/**
 * Custom assert for String.prototype.matchAll
 */
function assertMatchAllIterEquals(expected, actual) {
  assertRegExpResultEquals(expected.value, actual.value);
  assertEquals(expected.done, actual.done);
}

const endOfIterator = {
  value: undefined,
  done: true
};

/**
 * We have defined a separate function for making a new RegExp object to prevent
 * the compiler from inlining it. In some browsers directly declaring the regexp
 * with the slash (/) notations causes older browsers to fail when parsing the
 * code, so it never actually runs. We need a runtime exception from creating
 * the RegExp instead.
 *
 * @param {string} string
 * @return {!RegExp}
 * @noinline
 */
function newGlobalRegExp(string) {
  return new RegExp(string, 'g');
}

testSuite({
  shouldRunTests() {
    // Don't run these tests on older browsers that don't recognize the
    // named capture groups syntax.
    try {
      let regString = '(?<name>test)';
      let regex = newGlobalRegExp(regString);
      return regex.exec('test') != null;
    } catch (error) {
      return false;
    }
  },

  testMatchAll_namedCapturingGroup() {
    let regString = 't(?<name>e)(st([0-9]?))';
    let regex = newGlobalRegExp(regString);
    let testString = 'test1test2';

    let matchAllIterator = testString.matchAll(regex);
    let firstResult =
        resultBuilder(['test1', 'e', 'st1', '1'], 0, testString, {name: 'e'});
    let secondResult =
        resultBuilder(['test2', 'e', 'st2', '2'], 5, testString, {name: 'e'});

    assertMatchAllIterEquals(firstResult, matchAllIterator.next());
    assertMatchAllIterEquals(secondResult, matchAllIterator.next());
    assertObjectEquals(endOfIterator, matchAllIterator.next());
  }
});
