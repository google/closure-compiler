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
 * @fileoverview Tests for the Promise.allSettled() polyfilled method.
 *
 * These tests were written by taking the Promise.all() tests as a basis.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.promise_allSettled_test');
goog.setTestOnly();

const FakeThenable = goog.require('jscomp.runtime_tests.polyfill_tests.FakeThenable');
const testSuite = goog.require('goog.testing.testSuite');
const {assertObjectEquals} = goog.require('goog.testing.asserts');

/**
 * @param {*} value
 * @return {!Promise.AllSettledResultElement}
 */
function newFulfilledValue(value) {
  return {status: 'fulfilled', value: value};
}

/**
 * @param {*} reason
 * @return {!Promise.AllSettledResultElement}
 */
function newRejectedReason(reason) {
  return {status: 'rejected', reason: reason};
}

/**
 * Build and execute a test of Promise.allSettled()
 */
class AllSettledTestCaseBuilder {
  constructor() {
    this.inputIterable = undefined;
    this.expectedResults = undefined;
  }

  setInputIterable(inputIterable) {
    this.inputIterable = inputIterable;
    return this;
  }

  setExpectedResults(expectedResults) {
    this.expectedResults = expectedResults;
    return this;
  }

  test() {
    assertNotUndefined(
        'input for Promise.allSettled not defined', this.inputIterable);
    assertNotUndefined(
        'expected results for Promise.allSettled not defined',
        this.expectedResults);
    return Promise.allSettled(this.inputIterable).then(result => {
      assertObjectEquals(result, this.expectedResults);
    });
  }
}

testSuite({
  // allSettled() when passed an empty iterable should resolve to an empty
  // array
  testEmpty() {
    return Promise.all([
      new AllSettledTestCaseBuilder()
          .setInputIterable([])
          .setExpectedResults([])
          .test(),
      new AllSettledTestCaseBuilder()
          .setInputIterable(new Set([]))
          .setExpectedResults([])
          .test()
    ]);
  },

  // allSettled() when passed an iterable containing either a single
  // non-thenable value or a single thenable that resolves successfully
  // should resolve to a single-element array containing the resolved value
  testSingleFulfilled() {
    const expectedValue = {};

    function subtest(iterable) {
      return new AllSettledTestCaseBuilder()
          .setInputIterable(iterable)
          .setExpectedResults([newFulfilledValue(expectedValue)])
          .test();
    }

    return Promise.all([
      subtest([expectedValue]),
      subtest([FakeThenable.asyncFulfill(expectedValue)]),
      subtest([Promise.resolve(expectedValue)]),
      subtest(new Set([expectedValue])),
      subtest(new Set([FakeThenable.asyncFulfill(expectedValue)])),
      subtest(new Set([Promise.resolve(expectedValue)])),
    ]);
  },

  // allSettled() when passed an iterable containing a single thenable that
  // rejects should resolve to a single-element array containing the reason
  testSingleRejected() {
    const expectedReason = new Error('expected reason');
    const expectedResults = [newRejectedReason(expectedReason)];

    function subtest(iterable) {
      return new AllSettledTestCaseBuilder()
          .setInputIterable(iterable)
          .setExpectedResults(expectedResults)
          .test();
    }

    return Promise.all([
      subtest([FakeThenable.asyncReject(expectedReason)]),
      subtest([Promise.reject(expectedReason)]),
      subtest(new Set([FakeThenable.asyncReject(expectedReason)])),
      subtest(new Set([Promise.reject(expectedReason)])),
    ]);
  },

  // The result values should appear in the order of the original Promises,
  // not the order in which the promises were resolved.
  testTwoFulfilledOutOfOrder() {
    const p1 = Promise.resolve(1);
    const p2 = p1.then(() => 2);

    return new AllSettledTestCaseBuilder()
        .setInputIterable([p2, p1])
        .setExpectedResults([newFulfilledValue(2), newFulfilledValue(1)])
        .test();
  },

  // Unlike Promise.all(), the promise returned from Promise.allSettled()
  // should not reject and stop updating its result array when one of the input
  // promises rejects.
  testRejectDoesNotHideFulfilled() {
    const expectedReason = new Error('expected reason');
    const rejected = Promise.reject(expectedReason);
    const expectedValue = {};
    const resolvedAfterReject = rejected.catch(() => expectedValue);

    return new AllSettledTestCaseBuilder()
        .setInputIterable([resolvedAfterReject, rejected])
        .setExpectedResults([
          newFulfilledValue(expectedValue), newRejectedReason(expectedReason)
        ])
        .test();
  },

  // Unlike Promise.all(), Promise.allSettled() must wait for all promises to
  // be either fulfilled or rejected and then report all of the results.
  testMultipleRejectedAndFulfilled() {
    const expected1stValue = 'fulfilled before reject';
    const resolved1stPromise = Promise.resolve(expected1stValue);
    const expected1stReason = new Error('expected 1st reason');
    const rejected1stPromise = resolved1stPromise.then(() => {
      throw expected1stReason;
    });
    const expected2ndValue = 'fulfilled after reject';
    const resolved2ndPromise = rejected1stPromise.catch(() => expected2ndValue);
    const expected2ndReason = new Error('expected 2nd reason');
    const rejected2ndPromise = resolved2ndPromise.then(() => {
      throw expected2ndReason;
    });

    return new AllSettledTestCaseBuilder()
        .setInputIterable([
          resolved1stPromise, rejected1stPromise, resolved2ndPromise,
          rejected2ndPromise
        ])
        .setExpectedResults([
          newFulfilledValue(expected1stValue),
          newRejectedReason(expected1stReason),
          newFulfilledValue(expected2ndValue),
          newRejectedReason(expected2ndReason),
        ])
        .test();
  },
});
