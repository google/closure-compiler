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
 * @fileoverview Support code for testing the Promise polyfill.
 * NOTE: This file only supports ES3 syntax, because it won't be compiled for
 * test execution.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.promise_testing');
goog.setTestOnly();

/**
 * Fail the test unless the promise is fulfilled with the given value.
 * @param {!IThenable} promise
 * @param {*} expectedValue
 * @return {!IThenable} to be returned form the test case method.
 */
function asyncAssertPromiseFulfilled(promise, expectedValue) {
  var stillInAssertMethod = true;
  var assertPromise = promise.then(
      function(value) {
        assertEquals(expectedValue, value);
        assertFalse('resolved too early', stillInAssertMethod);
      },
      function(reason) { throw new Error('unexpected rejection: ' + reason); });
  stillInAssertMethod = false;
  return assertPromise;
}
exports.asyncAssertPromiseFulfilled = asyncAssertPromiseFulfilled;

/**
 * Fail the test unless the promise is rejected with the given reason.
 * @param {!IThenable} promise
 * @param {*} expectedReason
 * @return {!IThenable} to be returned form the test case method.
 */
function asyncAssertPromiseRejected(promise, expectedReason) {
  var stillInAssertMethod = true;
  var assertPromise = promise.then(
      function(value) {
        throw new Error('unexpected resolution to: ' + value);
      },
      function(reason) {
        assertEquals(expectedReason, reason);
        assertFalse('rejected too early', stillInAssertMethod);
      });
  stillInAssertMethod = false;
  return assertPromise;
}
exports.asyncAssertPromiseRejected = asyncAssertPromiseRejected;

/**
 * Schedule a method to be executed in a later execution cycle.
 * @param {!Function} method
 * @param {?} context used as {@code this} for the method call
 * @param {!Array} args arguments to pass to the call.
 */
function asyncExecute(method, context, args) {
  setTimeout(function() { method.apply(context, args); }, 0);
}
exports.asyncExecute = asyncExecute;
