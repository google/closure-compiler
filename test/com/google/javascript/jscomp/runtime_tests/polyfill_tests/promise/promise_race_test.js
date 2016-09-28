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
 * @fileoverview Tests for the Promise.race() polyfilled method.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.promise_race_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const FakeThenable = goog.require('jscomp.runtime_tests.polyfill_tests.FakeThenable');

testSuite({
  // NOTE: Promise.race([]) returns a Promise that will never resolve.

  testSingleNonThenable() {
    const expectedResult = {};
    return Promise.race([expectedResult])
        .then(result => assertEquals(expectedResult, result));
  },

  testSingleFulfilledForeignThenable() {
    const expectedResult = {};
    return Promise.race([FakeThenable.asyncFulfill(expectedResult)])
        .then(result => assertEquals(expectedResult, result));
  },

  testSingleRejectedForeignThenable() {
    const expectedReason = new Error('reject reason');
    return Promise.race([FakeThenable.asyncReject(expectedReason)])
        .then(
            result => fail(`unexpected resolve: ${result}`),
            reason => assertEquals(expectedReason, reason));
  },

  testSingleResolvedPromise() {
    const expectedResult = {};
    return Promise.race([Promise.resolve(expectedResult)])
        .then(result => assertEquals(expectedResult, result));
  },

  testSingleRejectedPromise() {
    const expectedReason = new Error('reject reason');
    return Promise.race([FakeThenable.asyncReject(expectedReason)])
        .then(
            result => fail(`unexpected resolve: ${result}`),
            reason => assertEquals(expectedReason, reason));
  },

  testFirstFulfilledPromiseWins() {
    const expectedResult = {};
    let resolveDelayed;
    const delayedPromise = new Promise(r => resolveDelayed = r);
    const values = [
      delayedPromise,
      Promise.resolve(expectedResult),
      Promise.reject('promise rejected'),
      Promise.resolve('later promise')
    ];
    const p = Promise.race(values)
        .then(result => assertEquals(expectedResult, result));
    resolveDelayed('delayed promise');
    return p;
  },

  testFirstFulfilledPromiseWins_noneImmediate() {
    const expectedResult = {};
    const resolveDelayedFulfilled = [];
    const delayedFulfilled = [
      new Promise(r => resolveDelayedFulfilled[0] = r),
      new Promise(r => resolveDelayedFulfilled[1] = r)
    ];
    let rejectDelayedRejected;
    const delayedRejected = new Promise((_, r) => rejectDelayedRejected = r);
    const values = [
      delayedFulfilled[1],
      delayedRejected,
      delayedFulfilled[0]
    ];
    const p = Promise.race(values)
        .then(result => assertEquals(expectedResult, result));
    resolveDelayedFulfilled[0](expectedResult);
    rejectDelayedRejected('reject occurred when fulfillment expected');
    resolveDelayedFulfilled[1]('delayedFulfilled[1]');
    return p;
  },

  testFirstRejectedPromiseWins() {
    const expectedReason = new Error('expected reason');
    let resolveDelayed;
    const delayedPromise = new Promise(r => resolveDelayed = r);
    const values = [
      delayedPromise,
      Promise.reject(expectedReason),
      Promise.reject('later reject'),
      Promise.resolve('later resolve')
    ];
    const p = Promise.race(values)
        .then(
            result => fail(`unexpectedly fulfilled: ${result}`),
            reason => assertEquals(expectedReason, reason));
    resolveDelayed('delayed promise');
    return p;
  },

  testFirstRejectedPromiseWins_noneImmediate() {
    const expectedReason = new Error('expected reason');
    let resolveDelayedFulfilled;
    const delayedFulfilled = new Promise(r => resolveDelayedFulfilled = r);
    const rejectDelayedRejected = [];
    const delayedRejected = [
      new Promise((_, r) => rejectDelayedRejected[0] = r),
      new Promise((_, r) => rejectDelayedRejected[1] = r)
    ];
    const values = [
      delayedRejected[1],
      delayedFulfilled,
      delayedRejected[0]
    ];
    const p = Promise.race(values)
        .then(
            result => fail(`unexpectedly fulfilled: ${result}`),
            reason => assertEquals(expectedReason, reason));
    rejectDelayedRejected[0](expectedReason);
    rejectDelayedRejected[1]('rejectDelayed[1]');
    resolveDelayedFulfilled('delayed fulfilled');
    return p;
  },
});
