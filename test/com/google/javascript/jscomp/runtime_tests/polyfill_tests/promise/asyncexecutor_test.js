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

goog.module('jscomp.runtime_tests.polyfill_tests.asyncexecutor_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

// NOTE: Since they are implementation details of Promise, the AsyncExecutor
// class type definition is not visible to the compiler here.

/** @interface */
class AsyncExecutor {
  asyncExecute(/** function():? */ f) {}
}

/**
 * Captures AsyncExecutor calls to asyncExecuteFunction().
 */
class AsyncExecuteFunctionHandler {
  constructor() {
    /** @type {!Array<!Function>} */
    this.scheduledFunctions = [];
  }

  /**
   * @param {!Function} f
   */
  asyncExecuteFunction(f) { this.scheduledFunctions.push(f); }

  executeNextFunction() {
    assertTrue('no functions scheduled', this.scheduledFunctions.length > 0);
    this.scheduledFunctions.shift().call();
  }

  assertNextFunctionThrows(expectedException) {
    let actualException = assertThrows(() => this.executeNextFunction());
    assertObjectEquals(expectedException, actualException);
  }

  static forExecutor(executor) {
    const handler = new AsyncExecuteFunctionHandler();
    executor.asyncExecuteFunction = f => handler.asyncExecuteFunction(f);
    return handler;
  }
}

/**
 * Provides a method we can conveniently call asynchronously and a method to
 * test that the calls were made in the expected order.
 */
class TestObject {
  constructor() { this.results = []; }

  pushResult(value) { this.results.push(value); }

  pushResultFunc(value) { return () => this.pushResult(value); }

  assertResultsInOrder(...expectedResults) {
    assertSameElements('unexpected results', expectedResults, this.results);
    assertObjectEquals('results out of order', expectedResults, this.results);
  }
}

/**
 * @return {!AsyncExecutor}
 * @throws if unable to create a new AsyncExecutor
 */
function newAsyncExecutor() {
  // IMPORTANT NOTE: Promise must be used somewhere in this file to trigger
  //     inclusion of the Promise polyfill code, which is where AsyncExecutor
  //     is defined.
  return /** {!AsyncExecutor} */ (Promise['$jscomp$new$AsyncExecutor']());
}

testSuite({
  shouldRunTests() {
    try {
      // throws an exception if RealAsyncExecutor is undefined.
      newAsyncExecutor();
    } catch (ignored) {
      return false;
    }
    return true;
  },

  testAsyncExecutorSingleBatch() {
    const executor = newAsyncExecutor();
    const asyncExecuteFunctionHandler =
        AsyncExecuteFunctionHandler.forExecutor(executor);
    const testObject = new TestObject();

    executor.asyncExecute(testObject.pushResultFunc('one'));
    executor.asyncExecute(testObject.pushResultFunc('two'));
    executor.asyncExecute(testObject.pushResultFunc('three'));

    // No executions until we trigger the asyncExecuteFunctionHandler
    testObject.assertResultsInOrder();
    // All executions done in order in a single batch.
    asyncExecuteFunctionHandler.executeNextFunction();
    testObject.assertResultsInOrder('one', 'two', 'three');
  },

  testAsyncExecutorSeparateBatches() {
    const executor = newAsyncExecutor();
    const asyncExecuteFunctionHandler =
        AsyncExecuteFunctionHandler.forExecutor(executor);
    const testObject = new TestObject();

    executor.asyncExecute(testObject.pushResultFunc('one'));

    // only one result after first batch
    asyncExecuteFunctionHandler.executeNextFunction();
    testObject.assertResultsInOrder('one');

    executor.asyncExecute(testObject.pushResultFunc('two'));
    executor.asyncExecute(testObject.pushResultFunc('three'));

    // All executions done after the second batch
    asyncExecuteFunctionHandler.executeNextFunction();
    testObject.assertResultsInOrder('one', 'two', 'three');
  },

  testAsyncExecutorExceptionsDoNotBlockOtherJobs() {
    const executor = newAsyncExecutor();
    const asyncExecuteFunctionHandler =
        AsyncExecuteFunctionHandler.forExecutor(executor);
    const testObject = new TestObject();
    const error1 = new Error('error 1');
    const error2 = new Error('error 2');

    executor.asyncExecute(testObject.pushResultFunc('one'));
    executor.asyncExecute(() => { throw error1; });
    executor.asyncExecute(testObject.pushResultFunc('two'));
    executor.asyncExecute(() => { throw error2; });
    executor.asyncExecute(testObject.pushResultFunc('three'));

    // All executions done in order in a single batch.
    asyncExecuteFunctionHandler.executeNextFunction();
    testObject.assertResultsInOrder('one', 'two', 'three');

    // errors thrown in separate jobs & same order they were thrown originally
    asyncExecuteFunctionHandler.assertNextFunctionThrows(error1);
    asyncExecuteFunctionHandler.assertNextFunctionThrows(error2);
  },

  testNewJobsGetAddedToExecutingBatch() {
    const executor = newAsyncExecutor();
    const asyncExecuteFunctionHandler =
        AsyncExecuteFunctionHandler.forExecutor(executor);
    const testObject = new TestObject();

    executor.asyncExecute(testObject.pushResultFunc('one'));
    executor.asyncExecute(
        () => executor.asyncExecute(testObject.pushResultFunc('two')));
    executor.asyncExecute(
        () => executor.asyncExecute(
            () => executor.asyncExecute(testObject.pushResultFunc('three'))));
    // All executions done in order in a single batch.
    asyncExecuteFunctionHandler.executeNextFunction();
    testObject.assertResultsInOrder('one', 'two', 'three');
  },
});
