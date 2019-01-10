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

goog.module('jscomp.runtime_tests.async_iteration_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

/**
 * @param {{value: ?, done: boolean}} expected
 * @param {{value: ?, done: boolean}} actual
 */
function compareResults(expected, actual) {
  assertEquals(expected.value, actual.value);
  assertEquals(expected.done, actual.done);
}

/**
 * @param {VALUE} result
 * @param {number} millis
 * @return {Promise<VALUE>}
 * @template VALUE
 */
function resolveAfterWaiting(result, millis) {
  return new Promise(function(resolve, reject) {
    try {
      setTimeout(function() {
        resolve(result);
      }, millis);
    } catch (err) {
      reject(err);
    }
  });
}

testSuite({
  testAsyncGenBasic() {
    async function* foo() {
      yield 1;
      yield await 2;
      yield Promise.resolve(3);
      yield await Promise.resolve(4);
    }
    return (async function() {
      let gen = foo();
      compareResults({value: 1, done: false}, await gen.next());
      compareResults({value: 2, done: false}, await gen.next());
      compareResults({value: 3, done: false}, await gen.next());
      compareResults({value: 4, done: false}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenPrecedences() {
    async function* foo() {
      yield(await Promise.resolve(1) + 1);  // await has unary-op precedence
      yield 'abc' +
          'd';  // yield has a lower precedence
    }
    return (async function() {
      let gen = foo();
      compareResults({value: 2, done: false}, await gen.next());
      compareResults({value: 'abcd', done: false}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenNestedYieldExecutionOrder() {
    async function* foo() {
      yield(yield 2 + (yield 1)) + (yield 4 + (yield 3));
      //     a          b            c          d
    }
    return (async function() {
      let gen = foo();
      compareResults({value: 1, done: false}, await gen.next());
      compareResults({value: '2b', done: false}, await gen.next('b'));
      compareResults({value: 3, done: false}, await gen.next('ab'));
      compareResults({value: '4d', done: false}, await gen.next('d'));
      compareResults({value: 'abcd', done: false}, await gen.next('cd'));
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenYields() {
    async function* foo() {
      yield await Promise.resolve(1234);
      yield;
      yield* ['test1', 'test2'];
      yield* (async function*() {
        yield 42;
        yield 43;
      })();
    }
    return (async function() {
      let gen = foo();
      compareResults({value: 1234, done: false}, await gen.next());
      compareResults({value: undefined, done: false}, await gen.next());
      compareResults({value: 'test1', done: false}, await gen.next());
      compareResults({value: 'test2', done: false}, await gen.next());
      compareResults({value: 42, done: false}, await gen.next());
      compareResults({value: 43, done: false}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenInputValue() {
    async function* foo(val) {
      while (val !== 0) {
        val = yield val * val;
      }
    }
    return (async function() {
      let gen = foo(0);
      compareResults({value: undefined, done: true}, await gen.next(1));

      gen = foo(1);
      compareResults({value: 1, done: false}, await gen.next());
      compareResults({value: 4, done: false}, await gen.next(2));
      compareResults({value: 9, done: false}, await gen.next(3));
      compareResults({value: undefined, done: true}, await gen.next(0));
    })();
  },
  testAsyncGenReturn() {
    async function* foo() {
      yield await Promise.resolve(1);
      yield await Promise.resolve(2);
      yield await Promise.resolve(3);
      return 4;
    }
    return (async function() {
      let gen = foo();
      // "Natural" return
      compareResults({value: 1, done: false}, await gen.next());
      compareResults({value: 2, done: false}, await gen.next());
      compareResults({value: 3, done: false}, await gen.next());
      compareResults({value: 4, done: true}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());

      // "Forced" return
      gen = foo();
      compareResults({value: 1, done: false}, await gen.next());
      compareResults({value: 2, done: false}, await gen.next());
      compareResults({value: 5, done: true}, await gen.return(5));
      compareResults({value: undefined, done: true}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenThrow() {
    async function* foo() {
      yield await Promise.resolve(1);
      yield await Promise.resolve(2);
      yield await Promise.resolve(3);
      yield await Promise.resolve(4);
    }
    return (async function() {
      let gen = foo();
      compareResults({value: 1, done: false}, await gen.next());
      compareResults({value: 2, done: false}, await gen.next());
      let error = new Error('Expected');
      await gen.throw(error).then(
          v => fail(`resolved to ${v} when error was expected`),
          e => assertEquals(error, e));
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenError() {
    var error = new Error('thrown from generator');
    async function* foo() {
      yield 1;
      throw error;
    }
    return (async function() {
      let gen = foo();
      compareResults({value: 1, done: false}, await gen.next());
      await gen.next().then(
          v => fail(`resolved to ${v} when error was expected`),
          e => assertEquals(error, e));
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenDelegate() {
    async function* foo() {
      yield 'before';
      var a = yield* syncDelegate();
      var b = yield* asyncDelegate();
      yield a + b;
      yield 'after';
    }
    function* syncDelegate() {
      yield 11;
      yield 12;
      yield 13;
      // This value is not yielded, the yield* resolves to this.
      return 'sync & ';
    }
    async function* asyncDelegate() {
      yield 21;
      yield 22;
      yield 23;
      return 'async';
    }
    return (async function() {
      let gen = foo();
      compareResults({value: 'before', done: false}, await gen.next());
      compareResults({value: 11, done: false}, await gen.next());
      compareResults({value: 12, done: false}, await gen.next());
      compareResults({value: 13, done: false}, await gen.next());
      compareResults({value: 21, done: false}, await gen.next());
      compareResults({value: 22, done: false}, await gen.next());
      compareResults({value: 23, done: false}, await gen.next());
      compareResults({value: 'sync & async', done: false}, await gen.next());
      compareResults({value: 'after', done: false}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenDelegateUncaughtThrow() {
    let error = new Error('Expected');
    async function* foo() {
      yield 1;
      try {
        yield* [2, -111, -222];
      } catch (err) {
        assertEquals(error, err);
        yield 3;
        yield* [4, -333, -444];
      }
    }
    return (async function() {
      let gen = foo();
      compareResults({value: 1, done: false}, await gen.next());
      compareResults({value: 2, done: false}, await gen.next());
      // Tests error uncaught by delegate but caught by generator.
      compareResults({value: 3, done: false}, await gen.throw(error));
      // Tests that we do not go back into the first delegate.
      compareResults({value: 4, done: false}, await gen.next());
      // Tests error uncaught by both delegate and generator.
      gen.throw(error).then(
          v => fail(`resolved to ${v} when error was expected`),
          e => assertEquals(error, e));
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenDelegateCaughtThrow() {
    let error1 = new Error('Should have been caught by delegate');
    let error2 = new Error('Should have been caught by generator');
    async function* foo() {
      yield 1;
      try {
        yield* delegate();
        yield - 999;
      } catch (err) {  // delegate does not catch the second err from call 4
        assertEquals(error2, err);
        yield 4;
      }
      yield 5;
    }
    function* delegate() {
      try {
        yield 2;
      } catch (err) {  // delegate catches the first err from call 3
        assertEquals(error1, err);
        yield 3;
      }
      yield - 999;
    }
    return (async function() {
      let gen = foo();
      compareResults({value: 1, done: false}, await gen.next());
      compareResults({value: 2, done: false}, await gen.next());
      compareResults({value: 3, done: false}, await gen.throw(error1));
      compareResults({value: 4, done: false}, await gen.throw(error2));
      compareResults({value: 5, done: false}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenRaceOfNexts() {
    async function* foo() {
      yield resolveAfterWaiting(1, 100);
      yield resolveAfterWaiting(2, 100);
      yield resolveAfterWaiting(3, 100);
      yield 4;
      yield 5;
      yield 6;
    }
    let gen = foo();
    return Promise.all([
      gen.next().then(
          (actual) => compareResults({value: 1, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 2, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 3, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 4, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 5, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 6, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: undefined, done: true}, actual)),
    ]);
  },
  testAsyncGenRaceOfNextAndReturn() {
    async function* foo() {
      yield resolveAfterWaiting(1, 100);
      yield resolveAfterWaiting(2, 100);
      yield resolveAfterWaiting(3, 100);
      yield resolveAfterWaiting(4, 100);
      yield resolveAfterWaiting(5, 100);
      yield resolveAfterWaiting(6, 100);
    }
    let gen = foo();
    return Promise.all([
      gen.next().then(
          (actual) => compareResults({value: 1, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 2, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 3, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 4, done: false}, actual)),
      gen.return(42).then(
          (actual) => compareResults({value: 42, done: true}, actual)),
      gen.next().then(
          (actual) => compareResults({value: undefined, done: true}, actual)),
    ]);
  },
  testAsyncGenRaceOfNextAndThrow() {
    async function* foo() {
      yield resolveAfterWaiting(1, 100);
      yield resolveAfterWaiting(2, 100);
      yield resolveAfterWaiting(3, 100);
      yield resolveAfterWaiting(4, 100);
      yield resolveAfterWaiting(5, 100);
      yield resolveAfterWaiting(6, 100);
    }
    let gen = foo();
    return Promise.all([
      gen.next().then(
          (actual) => compareResults({value: 1, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 2, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 3, done: false}, actual)),
      gen.next().then(
          (actual) => compareResults({value: 4, done: false}, actual)),
      gen.throw(42).then(
          (ignored) => assertEquals('throw', 'did not throw'),
          (err) => assertEquals(42, err)),
      gen.next().then(
          (actual) => compareResults({value: undefined, done: true}, actual)),
    ]);
  },
  testAsyncGenThisAndArguments() {
    let C = class {
      constructor(argIdx) {
        /** @type {string} */
        this.msg = 'first_';
        /** @const {number} */
        this.argIdx = argIdx;
      }
      async * foo(arg0, arg1, arg2) {
        while (this.msg !== 'last_') {
          this.msg = yield this.msg + arguments[this.argIdx];
        }
        yield this.msg + arguments[this.argIdx];
      }
    };
    let c = new C(0);
    let gen = c.foo('foo', 'wrong_arg', 'another_wrong_arg');
    return (async function() {
      compareResults({value: 'first_foo', done: false}, await gen.next());
      compareResults({value: 'next_foo', done: false}, await gen.next('next_'));
      compareResults({value: 'next_foo', done: false}, await gen.next('next_'));
      compareResults({value: 'last_foo', done: false}, await gen.next('last_'));
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testAsyncGenSuper() {
    let A = class {
      constructor() {
        /** @const {string} */
        this.foo = 'ABC';
      }
      m() {
        return this.foo;
      }
    };
    let B = class extends A {
      async * m() {
        yield* super.m();
      }
    };
    let gen = new B().m();
    return (async function() {
      compareResults({value: 'A', done: false}, await gen.next());
      compareResults({value: 'B', done: false}, await gen.next());
      compareResults({value: 'C', done: false}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },

  testForAwaitOfOverArray() {
    async function foo() {
      let results = [];
      for await (let val of [1, 2, 3]) {
        results.push(val);
      }
      return results;
    }
    return foo().then(function(results) {
      assertEquals(1, results[0]);
      assertEquals(2, results[1]);
      assertEquals(3, results[2]);
    });
  },
  testForAwaitOfOverAsyncGenerator() {
    async function* bar() {
      yield 0;
      yield 1;
      yield 2;
    }
    async function foo() {
      let results = [];
      for await (let val of bar()) {
        results.push(val + 1);
      }
      return results;
    }

    return foo().then(function(results) {
      assertEquals(1, results[0]);
      assertEquals(2, results[1]);
      assertEquals(3, results[2]);
    });
  },
  testForAwaitInAsyncGenerator() {
    async function* bar() {
      yield [10, 11, 12];
      yield [20, 21, 22];
      yield [30, 31, 32];
    }
    async function* foo() {
      for await (let val of bar()) {
        yield* val;
      }
    }

    return (async function() {
      let gen = /** @type {AsyncGenerator<?>} */ (foo());
      compareResults({value: 10, done: false}, await gen.next());
      compareResults({value: 11, done: false}, await gen.next());
      compareResults({value: 12, done: false}, await gen.next());
      compareResults({value: 20, done: false}, await gen.next());
      compareResults({value: 21, done: false}, await gen.next());
      compareResults({value: 22, done: false}, await gen.next());
      compareResults({value: 30, done: false}, await gen.next());
      compareResults({value: 31, done: false}, await gen.next());
      compareResults({value: 32, done: false}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
  testNestedForAwaitWithLabels() {
    async function* bar() {
      yield* [0, 1, 2, 3, 4, 5];
    }
    async function* foo() {
      outer: for await (let oVal of bar()) {
        inner: for await (let iVal of bar()) {
          if (iVal > oVal) {
            continue outer;
          } else if (oVal === 5 && iVal === 1) {
            break inner;
          }
          yield(oVal * 10) + iVal;
        }
      }
    }

    return (async function() {
      let gen = /** @type {AsyncGenerator<?>} */ (foo());
      // oVal: 0
      compareResults({value: 0, done: false}, await gen.next());
      // oVal: 1
      compareResults({value: 10, done: false}, await gen.next());
      compareResults({value: 11, done: false}, await gen.next());
      // oVal: 2
      compareResults({value: 20, done: false}, await gen.next());
      compareResults({value: 21, done: false}, await gen.next());
      compareResults({value: 22, done: false}, await gen.next());
      // oVal: 3
      compareResults({value: 30, done: false}, await gen.next());
      compareResults({value: 31, done: false}, await gen.next());
      compareResults({value: 32, done: false}, await gen.next());
      compareResults({value: 33, done: false}, await gen.next());
      // oVal: 4
      compareResults({value: 40, done: false}, await gen.next());
      compareResults({value: 41, done: false}, await gen.next());
      compareResults({value: 42, done: false}, await gen.next());
      compareResults({value: 43, done: false}, await gen.next());
      compareResults({value: 44, done: false}, await gen.next());
      // oVal: 5
      compareResults({value: 50, done: false}, await gen.next());
      compareResults({value: undefined, done: true}, await gen.next());
    })();
  },
});
