/*
 * Copyright 2021 The Closure Compiler Authors.
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
 * @fileoverview Tests for the Promise.prototype.any() polyfilled method.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.promise_any_test');
goog.setTestOnly();

const promiseTesting = goog.require('jscomp.runtime_tests.polyfill_tests.promise_testing');
const asyncAssertPromiseFulfilled = promiseTesting.asyncAssertPromiseFulfilled;
const asyncAssertPromiseRejected = promiseTesting.asyncAssertPromiseRejected;

const testSuite = goog.require('goog.testing.testSuite');
const aggregate_error_msg = 'All promises were rejected';

/**
 * Checks that the actual error is an AggregateError and matches expected.
 * @param {*} actual
 * @param {!AggregateError} expected
 */
function checkAggregateError(actual, expected) {
  // Not comparing aggregate error's message because certain non-transpiled
  // tests, generate a different error message. e.g. `'No Promise in Promise.any
  // was resolved'` v/s Chrome's `'All promises were rejected'`
  assertTrue(actual instanceof AggregateError);
  assertEquals(actual.name, 'AggregateError');
  assertEquals(actual.errors.length, expected.errors.length);
  for (let i = 0; i < actual.errors.length; i++) {
    assertEquals(actual[i].message, expected[i].message);
  }
}

testSuite({
  testEmptyArray() {
    let expectedAggregateError = new AggregateError([], aggregate_error_msg);
    return Promise.any([])
        .then((value) => {
          throw new Error('unexpected resolution of ' + value);
        })
        .catch(
            (aggregateError) =>
                checkAggregateError(aggregateError, expectedAggregateError));
  },

  testEmptySet() {
    let expectedAggregateError = new AggregateError([], aggregate_error_msg);
    return Promise.any(new Set([]))
        .then((value) => {
          throw new Error('unexpected resolution of ' + value);
        })
        .catch(
            (aggregateError) =>
                checkAggregateError(aggregateError, expectedAggregateError));
  },

  testSinglePromiseResolved() {
    let fulfilledValue = 'result';
    let promise = new Promise((resolve, reject) => {
      resolve(fulfilledValue);
    });
    let promises = [promise];
    return Promise.any(promises)
        .then((result) => assertEquals(result, 'result'))
        .catch((e) => {
          throw new Error('unexpected rejection of ' + e);
        });
  },

  testSinglePromiseUnResolved() {
    let reason = 'reason';
    let expectedAggregateError =
        new AggregateError([new Error(reason)], aggregate_error_msg);

    let promises = [Promise.reject(reason)];
    let promiseAny = Promise.any(promises);
    promiseAny
        .then((value) => {
          throw new Error('unexpected resolution of ' + value);
        })
        .catch(
            (aggregateError) =>
                checkAggregateError(aggregateError, expectedAggregateError));
  },

  testResolvedAndUnresolvedPromises() {
    const promises = [
      Promise.reject('ERROR A'),
      Promise.reject('ERROR B'),
      Promise.resolve('result'),
    ];
    let promiseAny = Promise.any(promises);
    promiseAny.then((result) => assertEquals(result, 'result')).catch((e) => {
      throw new Error('unexpected rejection of ' + e);
    });
  },

  testAllRejected() {
    const promises = [
      Promise.reject('ERROR A'),
      Promise.reject('ERROR B'),
      Promise.reject('ERROR C'),
    ];
    let errors =
        [new Error('ERROR A'), new Error('ERROR B'), new Error('ERROR C')];
    let expectedAggregateError =
        new AggregateError(errors, aggregate_error_msg);
    let promiseAny = Promise.any(promises);
    promiseAny
        .then((value) => {
          throw new Error('unexpected resolution of ' + value);
        })
        .catch(
            (aggregateError) =>
                checkAggregateError(aggregateError, expectedAggregateError));
  },
});
