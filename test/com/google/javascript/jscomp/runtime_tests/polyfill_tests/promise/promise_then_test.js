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
 * @fileoverview Tests for the Promise.prototype.then() polyfilled method.
 * The tc39/test262 Promise tests were consulted while writing these tests to
 * make sure we have adequate coverage.
 * https://github.com/tc39/test262/tree/master/test/built-ins/Promise
 */
goog.module('jscomp.runtime_tests.polyfill_tests.promise_then_test');
goog.setTestOnly();

const FakeThenable = goog.require('jscomp.runtime_tests.polyfill_tests.FakeThenable');
const promiseTesting = goog.require('jscomp.runtime_tests.polyfill_tests.promise_testing');
const asyncAssertPromiseFulfilled = promiseTesting.asyncAssertPromiseFulfilled;
const asyncAssertPromiseRejected = promiseTesting.asyncAssertPromiseRejected;

const testSuite = goog.require('goog.testing.testSuite');

/**
 * Handles boilerplate code for managing an asynchronous test case.
 *
 * <p>The return value from this function must be returned from the test method.
 * @param {!function(function(*), function(*))} testBody is passed
 *     passTest() and failTest(reason) functions. It executes the test and
 *     arranges to have one or the other of these functions called when the
 *     test results are known.
 * @return {!Thenable} that is settled when the test completes
 */
function executeTest(testBody) {
  let passTest, failTest;
  let testResults = new Promise((resolve, reject) => {
    passTest = resolve;
    failTest = reject;
  });

  testBody(passTest, failTest);
  return testResults;
}

/**
 * Handles boilerplate code for managing multiple similar asynchronous test
 * cases.
 *
 * <p>The return value from this function must be returned from the test method.
 * @template TEST_DATA
 * @param {!Array<TEST_DATA>} testData one element per test case
 * @param {!function(TEST_DATA, function(*), function(*))} testMethod is passed
 *     one element from testData followed by the passTest(), and
 *     failTest(reason) functions. It executes the test case and
 *     arranges to have one or the other of these functions called when the
 *     test results are known.
 * @return {!Thenable} that is settled when all test cases are complete
 */
function executeTests(testData, testMethod) {
  function createTestBody(testData) {
    return (passTest, failTest) => testMethod(testData, passTest, failTest);
  }
  let testResultPromise = Promise.resolve();
  // NOTE: Not using Promise.all() here, because this test is going to be
  //       submitted before its implementation.
  for (let i = 0; i < testData.length; ++i) {
    testResultPromise =
        testResultPromise.then(() => executeTest(createTestBody(testData[i])));
  }
  return testResultPromise;
}

/**
 * @typedef {{resolvedValue:*, expectedValue:*}}
 */
let ResolvedTestData;

/**
 * @return {!Array<ResolvedTestData>}
 */
function getResolvedTestData() {
  let nonObjectValue = 'something';
  let objectValue = {};
  return [
    {resolvedValue: nonObjectValue, expectedValue: nonObjectValue},
    {resolvedValue: objectValue, expectedValue: objectValue},
    {
      resolvedValue: Promise.resolve(nonObjectValue),
      expectedValue: nonObjectValue
    },
    {resolvedValue: Promise.resolve(objectValue), expectedValue: objectValue},
    {
      resolvedValue: FakeThenable.immediatelyFulfill(objectValue),
      expectedValue: objectValue
    },
    {
      resolvedValue: FakeThenable.asyncFulfill(objectValue),
      expectedValue: objectValue
    },
  ];
}

testSuite({
  testBadThis() {
    let voidFunction = () => undefined;
    function testWithThis(testThis) {
      let p = new Promise(voidFunction);
      let exception =
          assertThrows(() => p.then.call(testThis, voidFunction, voidFunction));
      assertTrue('Wrong exception type', exception instanceof TypeError);
    }

    testWithThis(3);
    testWithThis({});
  },

  testImmediatelyResolvedValueOrThenableResultPassedToOnFulfilledHandler() {
    return executeTests(
        getResolvedTestData(), (testData, passTest, failTest) => {
          new Promise(resolve => resolve(testData.resolvedValue))
              .then(value => assertEquals(testData.expectedValue, value))
              .then(passTest, failTest);
        });
  },

  testDeferredResolvedValueOrThenableResultPassedToOnFulfilledHandler() {
    return executeTests(
        getResolvedTestData(), (testData, passTest, failTest) => {
          let resolveP;
          let p = new Promise(resolve => resolveP = resolve);

          p.then(value => assertEquals(testData.expectedValue, value))
              .then(passTest, failTest);
          resolveP(testData.resolvedValue);
        });
  },

  testImmediatelyRejectedReasonPassedToOnRejectedHandler() {
    let rejectedReasons = [
      'reason',
      {},
      Promise.resolve({}),
      FakeThenable.immediatelyFulfill({}),
      FakeThenable.asyncFulfill({}),
    ];
    return executeTests(
        rejectedReasons, (rejectedReason, passTest, failTest) => {
          new Promise((resolve, reject) => reject(rejectedReason))
              .then(
                  value => fail('unexpectedly fulfilled with: ' + value),
                  reason => assertEquals(rejectedReason, reason))
              .then(passTest, failTest);
        });
  },

  testDeferredRejectedReasonPassedToOnRejectedHandler() {
    let rejectedReasons = [
      'reason',
      {},
      Promise.resolve({}),
      FakeThenable.immediatelyFulfill({}),
      FakeThenable.asyncFulfill({}),
    ];
    return executeTests(
        rejectedReasons, (rejectedReason, passTest, failTest) => {
          let rejectP;
          new Promise((resolve, reject) => rejectP = reject)
              .then(
                  value => fail('unexpectedly fulfilled with: ' + value),
                  reason => assertEquals(rejectedReason, reason))
              .then(passTest, failTest);
          rejectP(rejectedReason);
        });
  },

  testFulfilledValuePassesThroughWhenOnResolveNotAFunction() {
    let fulfilledValue = {};
    let p = Promise.resolve(fulfilledValue);
    let onResolveValues = [undefined, 2, 'str', {}];

    return executeTests(onResolveValues, (onResolve, passTest, failTest) => {
      p.then(
           /** @type {null|undefined|function(this:undefined, !Object): ?} */
           (onResolve))
          .then(value => assertEquals(fulfilledValue, value))
          .then(passTest, failTest);
    });
  },

  testRejectedReasonPassesThroughWhenOnRejectNotAFunction() {
    let rejectedReason = {};
    let p = Promise.reject(rejectedReason);
    let onRejectValues = [undefined, 2, 'str', {}];

    return executeTests(onRejectValues, (onReject, passTest, failTest) => {
      p.then(
           undefined,
           /** @type {null|undefined|function(this:undefined, *): ?} */
           (onReject))
          .then(
              value => fail('promise unexpectedly fulfilled: ' + value),
              reason => assertEquals(rejectedReason, reason))
          .then(passTest, failTest);
    });
  },

  testOnResolveNonObjFulfillsChildPromiseWhenPendingParentResolves() {
    return executeTest((passTest, failTest) => {
      let resolveParent;
      let parent = new Promise(resolve => resolveParent = resolve);
      let childFulfilledValue = 21;
      let child = parent.then(() => childFulfilledValue);

      child.then(value => assertEquals(childFulfilledValue, value))
          .then(passTest, failTest);
      resolveParent();
    });
  },

  testOnResolveMethodsCalledInPredictableOrder01() {
    return executeTest((passTest, failTest) => {
      let sequence = [];
      let p = new Promise((resolve) => {
        sequence.push(1);
        resolve('');
      });

      p.then(() => sequence.push(3))
          .then(() => sequence.push(5))
          .then(() => sequence.push(7))
          .catch(failTest);
      sequence.push(2);
      p.then(() => sequence.push(4))
          .then(() => sequence.push(6))
          .then(() => sequence.push(8))
          .then(() => assertArrayEquals([1, 2, 3, 4, 5, 6, 7, 8], sequence))
          .then(passTest, failTest);
    });
  },

  testOnResolveMethodsCalledInPredictableOrder02() {
    return executeTest((passTest, failTest) => {
      let sequence = [];
      let resolveP1, rejectP2;
      let p1 = new Promise((resolve) => resolveP1 = resolve)
                   .then(msg => sequence.push(msg))
                   .then(() => assertArrayEquals([1, 2, 3], sequence))
                   .then(passTest, failTest);
      let p2 = new Promise((resolve, reject) => rejectP2 = reject)
                   .then(
                       () => failTest('Promise unexpectedly fulfilled'),
                       msg => sequence.push(msg))
                   .catch(failTest);

      rejectP2(2);
      resolveP1(3);
      sequence.push(1);
    });
  },

  testOnResolveMethodsCalledInPredictableOrder03() {
    return executeTest((passTest, failTest) => {
      let resolveP1, rejectP2;
      let sequence = [];
      let p1 = new Promise(resolve => resolveP1 = resolve);
      let p2 = new Promise((resolve, reject) => rejectP2 = reject);

      rejectP2(3);
      resolveP1(2);
      p1.then(msg => sequence.push(msg)).catch(failTest);
      p2.then(
            () => failTest('Promise unexpectedly fuliflled'),
            msg => sequence.push(msg))
          .then(() => assertArrayEquals([1, 2, 3], sequence))
          .then(passTest, failTest);
      sequence.push(1);
    });
  },

  testOnResolveMethodsCalledInPredictableOrder04() {
    return executeTest((passTest, failTest) => {
      let sequence = [];
      let resolveP1, rejectP2;
      let p1 = new Promise(resolve => resolveP1 = resolve);
      let p2 = new Promise((resolve, reject) => rejectP2 = reject);

      rejectP2(3);
      resolveP1(2);
      Promise.resolve().then(() => {
        p1.then(msg => sequence.push(msg));
        p2.catch(msg => sequence.push(msg))
            .then(() => assertArrayEquals([1, 2, 3], sequence))
            .then(passTest, failTest);
      }, failTest);
      sequence.push(1);
    });
  },

  testOnResolveMethodsCalledInPredictableOrder05() {
    return executeTest((passTest, failTest) => {
      let sequence = [];
      let pResolve;
      let p = new Promise(resolve => pResolve = resolve);

      sequence.push(1);
      p.then(() => {
         sequence.push(3);
         assertArrayEquals([1, 2, 3], sequence);
       }).catch(failTest);
      Promise.resolve()
          .then(() => {
            p.then(() => {
               sequence.push(4);
               assertArrayEquals([1, 2, 3, 4], sequence);
             }).then(passTest, failTest);
            sequence.push(2);
            assertArrayEquals([1, 2], sequence);
            pResolve();
          })
          .catch(failTest);
    });
  },

  testOnResolveMethodsCalledInPredictableOrder06() {
    return executeTest((passTest, failTest) => {
      let sequence = [];
      let pResolve;
      let p = new Promise(resolve => pResolve = resolve);

      sequence.push(1);
      pResolve();
      p.then(() => {
         sequence.push(3);
         assertArrayEquals([1, 2, 3], sequence);
       }).catch(failTest);
      Promise.resolve()
          .then(() => {
            p.then(() => {
               sequence.push(5);
               assertArrayEquals([1, 2, 3, 4, 5], sequence);
             }).then(passTest, failTest);
            sequence.push(4);
            assertArrayEquals([1, 2, 3, 4], sequence);
          })
          .catch(failTest);
      sequence.push(2);
    });
  },

  testOnResolveMethodsCalledInPredictableOrder07() {
    return executeTest((passTest, failTest) => {
      let sequence = [];
      let pReject;
      let p = new Promise((resolve, reject) => pReject = reject);

      sequence.push(1);
      pReject();
      p.then(() => failTest('Promise unexpectedly fulfilled'), () => {
         sequence.push(3);
         assertArrayEquals([1, 2, 3], sequence);
       }).catch(failTest);
      Promise.resolve()
          .then(() => {
            p.then(() => failTest('Promise unexpectedly fulfilled'), () => {
               sequence.push(5);
               assertArrayEquals([1, 2, 3, 4, 5], sequence);
             }).then(passTest, failTest);
            sequence.push(4);
            assertArrayEquals([1, 2, 3, 4], sequence);
          })
          .catch(failTest);
      sequence.push(2);
    });
  },

  // TODO(bradfordcsmith): Tests for classes extending Promise.
});
