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
 * @fileoverview Tests for the Promise polyfill constructor.
 * The tc39/test262 Promise tests were consulted while writing these tests to
 * make sure we have adequate coverage.
 * https://github.com/tc39/test262/tree/master/test/built-ins/Promise
 */
goog.module('jscomp.runtime_tests.polyfill_tests.promise_constructor_test');
goog.setTestOnly();

const FakeThenable = goog.require('jscomp.runtime_tests.polyfill_tests.FakeThenable');
const promiseTesting = goog.require('jscomp.runtime_tests.polyfill_tests.promise_testing');
const asyncAssertPromiseFulfilled = promiseTesting.asyncAssertPromiseFulfilled;
const asyncAssertPromiseRejected = promiseTesting.asyncAssertPromiseRejected;
const asyncExecute = promiseTesting.asyncExecute;
const userAgent = goog.require('goog.userAgent');

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testResolve() {
    let fulfilledValue = 'fulfilled value';
    let promise =
        new Promise((resolve, reject) => { resolve(fulfilledValue); });
    return asyncAssertPromiseFulfilled(promise, fulfilledValue);
  },

  testIgnoreSecondResolve() {
    let fulfilledValue = 'fulfilled value';
    let promise = new Promise((resolve, reject) => {
      resolve(fulfilledValue);
      resolve('other value');
    });
    return asyncAssertPromiseFulfilled(promise, fulfilledValue);
  },

  testIgnoreRejectAfterResolve() {
    let fulfilledValue = 'fulfilled value';
    let promise = new Promise((resolve, reject) => {
      resolve(fulfilledValue);
      reject(new Error('ignore this error'));
    });
    return asyncAssertPromiseFulfilled(promise, fulfilledValue);
  },

  testIgnoreThrowAfterResolve() {
    let fulfilledValue = 'fulfilled value';
    let promise = new Promise((resolve, reject) => {
      resolve(fulfilledValue);
      throw new Error('ignore this thrown error');
    });
    return asyncAssertPromiseFulfilled(promise, fulfilledValue);
  },

  testReject() {
    let rejectedReason = new Error('reason');
    let promise = new Promise((resolve, reject) => { reject(rejectedReason); });
    return asyncAssertPromiseRejected(promise, rejectedReason);
  },

  testIgnoreResolveAfterReject() {
    let rejectedReason = new Error('reason');
    let promise = new Promise((resolve, reject) => {
      reject(rejectedReason);
      resolve('other value');
    });
    return asyncAssertPromiseRejected(promise, rejectedReason);
  },

  testIgnoreSecondReject() {
    let rejectedReason = new Error('reason');
    let promise = new Promise((resolve, reject) => {
      reject(rejectedReason);
      reject(new Error('ignore this error'));
    });
    return asyncAssertPromiseRejected(promise, rejectedReason);
  },

  testIgnoreThrowAfterReject() {
    let rejectedReason = new Error('reason');
    let promise = new Promise((resolve, reject) => {
      reject(rejectedReason);
      throw new Error('ignore this thrown error');
    });
    return asyncAssertPromiseRejected(promise, rejectedReason);
  },

  testExecutorThrowsAnException() {
    let rejectedReason = new Error('expected error');
    let receivedValue = undefined;
    let receivedReason = undefined;
    let promise = new Promise((resolve, reject) => { throw rejectedReason; });

    return asyncAssertPromiseRejected(promise, rejectedReason);
  },

  testSelfResolutionError() {
    if (userAgent.GECKO && Promise.name != 'PolyfillPromise') {
      // Some versions of Firefox native Promise don't detect self resolution,
      // but it doesn't seem reasonable to polyfill just for that.
      return;
    }
    let selfResolutionError =
        new TypeError('A Promise cannot resolve to itself');
    let resolvePromise;
    let promise =
        new Promise((resolve, reject) => { resolvePromise = resolve; });
    resolvePromise(promise);
    return promise.then(
        (value) => {
          assertObjectEquals(
              'unexpectedly resolved to: ' + value, promise, value);
          throw new Error('promise resolved to itself');
        },
        (reason) => { assertObjectEquals(selfResolutionError, reason); });
  },

  testResolveToFulfilledPromise() {
    let fulfilledValue = 'fulfilled value';
    let fulfilledPromise =
        new Promise((resolve, reject) => { resolve(fulfilledValue); });
    let testPromise =
        new Promise((resolve, reject) => { resolve(fulfilledPromise); });

    return asyncAssertPromiseFulfilled(testPromise, fulfilledValue);
  },

  testResolveToAsyncFulfilledPromise() {
    let fulfilledValue = 'fulfilled value';
    let fulfilledPromise = new Promise((resolve, reject) => {
      asyncExecute(resolve, undefined, [fulfilledValue]);
    });
    let testPromise =
        new Promise((resolve, reject) => { resolve(fulfilledPromise); });

    return asyncAssertPromiseFulfilled(testPromise, fulfilledValue);
  },

  testResolveToRejectedPromise() {
    let rejectedReason = new Error('rejected reason');
    let rejectedPromise =
        new Promise((resolve, reject) => { reject(rejectedReason); });
    let testPromise =
        new Promise((resolve, reject) => { resolve(rejectedPromise); });

    return asyncAssertPromiseRejected(testPromise, rejectedReason);
  },

  testResolveToAsyncRejectedPromise() {
    let rejectedReason = new Error('rejected reason');
    let rejectedPromise = new Promise((resolve, reject) => {
      asyncExecute(reject, undefined, [rejectedReason]);
    });
    let testPromise =
        new Promise((resolve, reject) => { resolve(rejectedPromise); });

    return asyncAssertPromiseRejected(testPromise, rejectedReason);
  },

  testRejectWithPromiseAsReason() {
    let promiseUsedAsReason = new Promise((resolve, reject) => {});
    let testPromise =
        new Promise((resolve, reject) => reject(promiseUsedAsReason));
    // Reject treats a Promise object like any other value.
    return asyncAssertPromiseRejected(testPromise, promiseUsedAsReason);
  },

  testResolveToAsyncFulfilledThenable() {
    let fulfilledValue = 'fulfilled value';
    let fulfilledThenable = FakeThenable.asyncFulfill(fulfilledValue);
    let testPromise =
        new Promise((resolve, reject) => { resolve(fulfilledThenable); });

    return asyncAssertPromiseFulfilled(testPromise, fulfilledValue);
  },

  testResolveToImmediatelyFulfilledThenable() {
    let fulfilledValue = 'fulfilled value';
    let fulfilledThenable = FakeThenable.immediatelyFulfill(fulfilledValue);
    let testPromise =
        new Promise((resolve, reject) => { resolve(fulfilledThenable); });

    return asyncAssertPromiseFulfilled(testPromise, fulfilledValue);
  },

  testResolveToAsyncRejectedThenable() {
    let rejectedReason = new Error('rejected reason');
    let rejectedThenable = FakeThenable.asyncReject(rejectedReason);
    let testPromise =
        new Promise((resolve, reject) => { resolve(rejectedThenable); });

    return asyncAssertPromiseRejected(testPromise, rejectedReason);
  },

  testResolveToImmediatelyRejectedThenable() {
    let rejectedReason = new Error('rejected reason');
    let rejectedThenable = FakeThenable.immediatelyReject(rejectedReason);
    let testPromise =
        new Promise((resolve, reject) => { resolve(rejectedThenable); });

    return asyncAssertPromiseRejected(testPromise, rejectedReason);
  },

  testResolveToThenableThatThrows() {
    let rejectedReason = new Error('rejected reason');
    let rejectedThenable = FakeThenable.immediatelyThrow(rejectedReason);
    let testPromise =
        new Promise((resolve, reject) => { resolve(rejectedThenable); });

    return asyncAssertPromiseRejected(testPromise, rejectedReason);
  },

  testRejectWithThenableAsReason() {
    let thenableUsedAsReason = FakeThenable.asyncFulfill(true);
    let testPromise =
        new Promise((resolve, reject) => reject(thenableUsedAsReason));
    // Reject treats a Promise object like any other value.
    return asyncAssertPromiseRejected(testPromise, thenableUsedAsReason);
  },

  testResolveThen() {
    let expectedValue = 'expected value';
    return Promise.resolve(expectedValue).then((value) => {
      assertEquals(expectedValue, value);
    });
  },

  testRejectThen() {
    let expectedError = new Error('expected error');
    return Promise.reject(expectedError)
        .then(
            (value) => {
              throw new Error('unexpectedly resolved to: ' + value);
            },
            (reason) => { assertObjectEquals(expectedError, reason); });
  },

  testRejectThenCatch() {
    let expectedError = new Error('expected error');
    return Promise.reject(expectedError)
        .then((value) => {
          throw new Error('unexpectedly resolved to: ' + value);
        })
        .catch((reason) => { assertObjectEquals(expectedError, reason); });
  },

  testExceptionInThenRejects() {
    let expectedError = new Error('expected error');
    return Promise.resolve()
        .then((value) => { throw expectedError; })
        .catch((reason) => { assertObjectEquals(expectedError, reason); });
  },
});
