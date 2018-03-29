/*
 * Copyright 2018 The Closure Compiler Authors.
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
goog.module('jscomp.runtime_tests.polyfill_tests.promise_finally_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  async testFinallyCarriesForwardResolveValue() {
    let expected = 1;
    await Promise.resolve(expected)
        .finally(() => {
          return expected + 1;
        })
        .then(
            (a) => {
              assertTrue('Wrong result', a === expected);
            },
            () => {
              fail("unexpected");
            });
  },

  async testFinallyCarriesForwardRejectValue() {
    let expected = new Error("expected");
    await Promise.reject(expected)
        .finally(() => {
          return 1;  // some success value;
        })
        .then(
            () => {
              fail("unexpected");
            },
            (a) => {
              assertTrue('Wrong result', a === expected);
              return undefined; // SUCCESS
            });
  },

  async testFinallyThrowOverridesPromiseResults1() {
    let expected = new Error("expected");
    await Promise.resolve(1)
        .finally(() => {
          throw expected;
        })
        .then(
            () => {
              fail("unexpected");
            },
            (a) => {
              assertTrue('Wrong result', a === expected);
              return undefined; // SUCCESS
            });
  },

  async testFinallyThrowOverridesPromiseResults2() {
    let expected = new Error("expected");
    await Promise.resolve(1)
        .finally(() => {
          return Promise.reject(expected);
        })
        .then(
            () => {
              fail("unexpected");
            },
            (a) => {
              assertTrue('Wrong result', a === expected);
              return undefined; // SUCCESS
            });
  },

  async testFinallyThrowOverridesPromiseRejectReason1() {
    let expected = new Error("expected");
    await Promise.reject(new Error("unexpected"))
        .finally(() => {
          throw expected;
        })
        .then(
            () => {
              fail("unexpected");
            },
            (a) => {
              assertTrue('Wrong result', a === expected);
              return undefined; // SUCCESS
            });
  },

  async testFinallyThrowOverridesPromiseRejectReason2() {
    let expected = new Error("expected");
    await Promise.reject(new Error("unexpected"))
        .finally(() => {
          return Promise.reject(expected);
        })
        .then(
            () => {
              fail("unexpected");
            },
            (a) => {
              assertTrue('Wrong result', a === expected);
              return undefined; // SUCCESS
            });
  },
});
