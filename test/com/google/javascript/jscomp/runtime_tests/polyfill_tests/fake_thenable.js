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

/* NOTE: This file only supports ES3 syntax, because it won't be compiled for
 * test execution.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.FakeThenable');
goog.setTestOnly();

var promiseTesting = goog.require('jscomp.runtime_tests.polyfill_tests.promise_testing');
var asyncExecute = promiseTesting.asyncExecute;

/**
 * A thenable with configurable behavior.
 * @constructor
 * @struct
 * @implements {IThenable<TYPE>}
 * @template TYPE
 * @param {string} immediateOrAsync Should {@code then()} make a callback
 *     immediately ('immediate') or asynchronously ('async')? Note that making
 *     a callback immediately is actually bad behavior for a Thenable, so it
 *     tests the robustness of our code when used with ill-behaved thenables.
 * @param {string} fulfillOrRejectOrThrow Should {@code then()} call
 *     {@code onFullfill} ('fulfill') or {@code onReject} ('reject') or throw an
 *     exception ('throw')? Throwing an exception is bad behavior, and always
 *     happens during the {@link throw()} call regardless of the value of
 *     {@code immediateOrAsync} parameter.
 * @param {*} valueOrReason The value passed to {@code onFulfill} or
 *     {@code onReject} or thrown as an exception.
 */
var FakeThenable = function(
    immediateOrAsync, fulfillOrRejectOrThrow, valueOrReason) {
  this.immediateOrAsync_ = immediateOrAsync;
  this.fulfillOrRejectOrThrow_ = fulfillOrRejectOrThrow;
  this.valueOrReason_ = valueOrReason;
};

/** @override */
FakeThenable.prototype.then = function(onFulfilled, onRejected) {
  switch (this.fulfillOrRejectOrThrow_) {
    case 'fulfill':
      this.execute_(onFulfilled);
      break;
    case 'reject':
      this.execute_(onRejected);
      break;
    case 'throw':
      throw this.valueOrReason_;
    default:
      throw new Error('unexpected value: ' + this.fulfillOrRejectOrThrow_);
  }
  return /** @type {?} */ (this);
};

FakeThenable.prototype.execute_ = function(method) {
  switch (this.immediateOrAsync_) {
    case 'immediate':
      method.call(undefined, this.valueOrReason_);
      break;
    case 'async':
      asyncExecute(method, undefined, [this.valueOrReason_]);
      break;
    default:
      throw new Error('unexpected value: ' + this.immediateOrAsync_);
  }
};

/**
 * @export
 * @template TYPE
 * @param {TYPE} value
 * @return {!FakeThenable<TYPE>}
 */
FakeThenable.immediatelyFulfill = function(value) {
  return new FakeThenable('immediate', 'fulfill', value);
};

/**
 * @export
 * @template TYPE
 * @param {TYPE} value
 * @return {!FakeThenable<TYPE>}
 */
FakeThenable.asyncFulfill = function(value) {
  return new FakeThenable('async', 'fulfill', value);
};

/**
 * @export
 * @param {?} reason
 * @return {!FakeThenable<?>}
 */
FakeThenable.immediatelyReject = function(reason) {
  return new FakeThenable('immediate', 'reject', reason);
};

/**
 * @export
 * @param {?} reason
 * @return {!FakeThenable<?>}
 */
FakeThenable.asyncReject = function(reason) {
  return new FakeThenable('async', 'reject', reason);
};

/**
 * @export
 * @param {?} exception
 * @return {!FakeThenable<?>}
 */
FakeThenable.immediatelyThrow = function(exception) {
  return new FakeThenable('immediate', 'throw', exception);
};

exports = FakeThenable;
