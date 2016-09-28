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

/**
 * @fileoverview Tests for the Promise.all() polyfilled method.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.promise_all_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const FakeThenable = goog.require('jscomp.runtime_tests.polyfill_tests.FakeThenable');

function newPromiseCapability() {
  const capability = {};
  capability.promise = new Promise((resolve, reject) => {
    capability.resolve = resolve;
    capability.reject = reject;
  });
  return capability;
}

testSuite({
  testEmpty() {
    return Promise.all([
      Promise.all([]).then(r => assertArrayEquals([], r)),
      Promise.all(new Set([])).then(r => assertArrayEquals([], r))
    ]);
  },

  testSingleFulfilled() {
    const expectedResult = {};

    function subtest(iterable) {
      return Promise.all(iterable)
          .then(r => assertArrayEquals([expectedResult], r));
    }

    return Promise.all([
      subtest([expectedResult]),
      subtest([FakeThenable.asyncFulfill(expectedResult)]),
      subtest([Promise.resolve(expectedResult)]),
      subtest(new Set([expectedResult])),
      subtest(new Set([FakeThenable.asyncFulfill(expectedResult)])),
      subtest(new Set([Promise.resolve(expectedResult)])),
    ]);
  },

  testSingleRejected() {
    const expectedReason = new Error('expected reason');

    function subtest(iterable) {
      return Promise.all(iterable)
          .then(
              v => fail(`unexpectedly fulfilled with: ${v}`),
              r => assertEquals(expectedReason, r));
    }

    return Promise.all([
      subtest([FakeThenable.asyncReject(expectedReason)]),
      subtest([Promise.reject(expectedReason)]),
      subtest(new Set([FakeThenable.asyncReject(expectedReason)])),
      subtest(new Set([Promise.reject(expectedReason)])),
    ]);
  },

  testTwoFulfilled() {
    const p1 = Promise.resolve(1);
    const p2 = p1.then(() => 2);

    function subtest(input, expectedResults) {
      return Promise.all(input).then(
          r => assertArrayEquals(expectedResults, r));
    }

    return Promise.all([
      subtest([p1, p2], [1, 2]),
      subtest([p2, p1], [2, 1])
    ]);
  },

  testOneRejectedOneUnresolved() {
    const expectedReason = new Error('expected reason');
    const unresolved = new Promise(() => {});
    const rejected = Promise.reject(expectedReason);
    return Promise.all([unresolved, rejected]).then(
        fail, r => assertEquals(expectedReason, r));
  },

  testAtLeastOneRejected() {
    const expectedReason = new Error('expected reason');
    const fulfillBefore = Promise.resolve('fulfilled before reject');
    const expectedReject = fulfillBefore.then(() => { throw expectedReason; });
    const fulfillAfter = expectedReject.catch(() => 'fulfilled after reject');
    const secondReject = expectedReject.catch(() => { throw 'second reject'; });
    const unresolved = new Promise(() => {});

    return Promise.all([
      fulfillBefore, fulfillAfter, secondReject, unresolved, expectedReject
    ]).then(fail, r => assertEquals(expectedReason, r));
  },
});
