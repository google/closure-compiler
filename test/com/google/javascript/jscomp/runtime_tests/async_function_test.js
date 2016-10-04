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
 * @fileoverview Tests async function behavior.
 */
goog.module('jscomp.runtime_tests.async_function_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testEmptyFunction() {
    async function empty() {}
    return empty().then(
        v => assertEquals(undefined, v));
  },

  testExpressionBodyArrowFunction() {
    const expected = {};
    const f = async() => expected;
    return f().then(v => assertEquals(expected, v));
  },

  testExplicitPromiseReturned() {
    const expected = {};
    return (async() => Promise.resolve(expected))().then(
        v => assertEquals(expected, v));
  },

  testSimpleReturn() {
    async function f() {
      return 1;
    }
    return f().then(v => assertEquals(1, v));
  },

  testSimpleReturnOfPromise() {
    async function f() {
      return Promise.resolve(1);
    }
    return f().then(v => assertEquals(1, v));
  },

  testReturnAwaitPromise() {
    async function f() {
      return await Promise.resolve(1);
    }
    return f().then(v => assertEquals(1, v));
  },

  testExceptionThrown() {
    const error = new Error('expected');
    const f = async() => {
      throw error;
    };
    return f().then(
        v => fail(`resolved to ${v} when error was expected`),
        e => assertEquals(error, e));
  },

  testIntermediatePromiseRejection() {
    const error = new Error('expected');
    const rejectingPromise = Promise.reject(error);
    async function action1() {
      return Promise.reject(error);
    }
    async function action2() {
      fail('action 2 called unexpectedly');
    }
    async function failsFirstAction() {
      await action1();
      await action2();
      fail('Shouldn\'t reach here');
    }
    return failsFirstAction()
        .then(
            v => fail(`unexpectedly resolved with: ${v}`),
            e => assertEquals(error, e));
  },

  testAwaitResultsPassedCorrectly() {
    async function asyncCompose(startValue, ...functionList) {
      let endValue = startValue;
      for (const f of functionList) {
        endValue = await f(endValue);
      }
      return endValue;
    }

    function asyncExpectThenReturn(valueToExpect, valueToReturn) {
      return async function(inputPromise) {
        assertEquals(valueToExpect, await inputPromise);
        return valueToReturn;
      };
    }

    return asyncCompose(
        0, asyncExpectThenReturn(0, 1), asyncExpectThenReturn(1, 2))
            .then(v => assertEquals(2, v));
  },

  testMemberFunctionUsingThis() {
    class C {
      constructor() {
        this.value = 0;
      }

      async delayedGetValue() {
        return this.value;
      }
    }
    const c = new C();
    return c.delayedGetValue().then(v => assertEquals(0, v));
  },

  testMemberFunctionUsingThisInArrowFunction() {
    class C {
      constructor() {
        this.value = 0;
      }

      async delayedIncrementAndReturnThis() {
        return Promise.resolve().then(() => (this.value++, this));
      }
    }
    const c = new C();
    return c.delayedIncrementAndReturnThis().then(result => {
      assertEquals(c, result);
      assertEquals(1, c.value);
    });
  },

  testArgumentsHandledCorrectly() {
    const expected1 = {};
    const expected2 = 2;

    async function awaitAllArgs(...unused) {
      const results = [];
      for (let i = 0; i < arguments.length; ++i) {
        // this instance of arguments must be replaced with an alias
        results[i] = await arguments[i];
      }
      return results;
    }
    // Arguments can validly be Promises or not.
    return awaitAllArgs(expected1, Promise.resolve(expected2))
        .then(v => assertObjectEquals([expected1, expected2], v));
  },

  testClosureArgumentsHandledCorrectly() {
    async function f() {
      function argCountPromise(...unused) {
        // this instance of arguments must *not* be replaced with an alias
        return Promise.resolve(arguments.length);
      }
      return Promise.all([argCountPromise(1), argCountPromise(1, 2)]);
    }
    f().then(v => assertObjectEquals([1, 2], v));
  },
});
