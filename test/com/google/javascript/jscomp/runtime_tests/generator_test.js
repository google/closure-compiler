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
 * @fileoverview
 * Tests ES6 generators.
 *
 * @author mattloring@google.com (Matthew Loring)
 */
goog.require('goog.testing.jsunit');

/**
 * Tests the output of a generator against an expected array using the new
 * for..of syntax.
 *
 * @param {function(?)} generator
 * @param {!Array<number|undefined>} expected
 * @param {!Array<*>} args arguments to pass to generator function
 */
function forOfZipper(generator, expected, args) {
  var counter = 0;
  for (var v of generator.apply(null, args)) {
    assertEquals(expected[counter], v);
    counter++;
  }
  assertEquals(counter, expected.length);
}

/**
 * Tests the output of a generator against an expected array using a simple
 * for loop.
 *
 * @param {function(?)} generator
 * @param {!Array<number|undefined>} expected
 * @param {!Array<*>} args arguments to pass to generator function
 */
function directAccessZipper(generator, expected, args) {
  var iter = generator.apply(null, args)[Symbol.iterator]();
  for (var i = 0; i < expected.length; i++) {
    assertEquals(expected[i], iter.next().value);
  }
  assertEquals(true, iter.next().done);
}

/**
 * Tests the output of a generator against an expected array of values.
 *
 * @param {function(?)} generator
 * @param {!Array<number|undefined>} expected
 * @param {...*} var_args arguments to pass to generator function
 */
function compareToExpected(generator, expected, var_args) {
  var argsArray = [].slice.call(arguments, 2);
  forOfZipper.call(null, generator, expected, argsArray);
  directAccessZipper.call(null, generator, expected, argsArray);
}

/**
 * Asserts that the generator throws the given error.
 *
 * @param {function(...?)} generator
 * @param {?} error
 */
function checkThrows(generator, error) {
  var iter = generator();
  try {
    iter.next();
    fail('Expected an exception to be thrown, but none reached');
  } catch (e) {
    assertEquals(error, e);
    var res = iter.next();
    assertUndefined(res.value);
    assertTrue(res.done);
  }
}

function testGenerator() {
  function *f() {
    var i = 0;
    yield i;
    i = 1;
    yield i;
    i = i + 1;
    yield i;
  }
  compareToExpected(f, [0, 1, 2]);
}

function testGeneratorForOf() {
  function *f() {
    var a = [5, 6, 8];
    for (var n of a) {
      yield n;
    }
  }
  compareToExpected(f, [5, 6, 8]);
}

function testGeneratorForIn() {
  function *f() {
    var o = {a: 5, b: 6};
    for (var n in o) {
      yield o[n];
    }
  }
  var expected = [5, 6];
  var actual = [];
  for (var v of f()) {
    actual.push(v);
  }
  assertSameElements(expected, actual);

  function *g() {
    var i = 0;
    function getO() {
      if (i !== 0) {
        fail('This function should only execute once.');
      }
      i++;
      return {a: 5, b: 6};
    }
    for (var n in getO()) {
      yield 1;
    }
  }
  expected = [1, 1];
  actual = [];
  for (v of g()) {
    actual.push(v);
  }
  assertSameElements(expected, actual);
}

function testGeneratorNestedFor() {
  function *g() {
    for (var i = 0; i < 2; i++) {
      for (var j = 0; j < 2; j++) {
        yield i + j;
      }
    }
  }
  compareToExpected(g, [0, 1, 1, 2]);
}

function testGeneratorNestedWhile() {
  function *h() {
    var i = 0,
        j = 0;
    while (i < 2) {
      j = 0;
      while (j < 2) {
        yield i + j;
        j++;
      }
      i++;
    }
  }
  compareToExpected(h, [0, 1, 1, 2]);
}

function testGeneratorTopLevelBlock() {
  function *blocked() {
    var i = 0;
    {
      yield i;
      i = 1;
    }
    yield i;
    {
      i = i + 1;
      yield i;
    }
  }
  compareToExpected(blocked, [0, 1, 2]);
}

function testGeneratorIf() {
  function *forIf() {
    for (var i = 0; i < 7; i++) {
      if (i % 2 == 0) {
        yield i;
      }
    }
  }
  function *argIf(i) {
    if (i < 5) {
      yield i;
    }
  }
  compareToExpected(forIf, [0, 2, 4, 6]);

  compareToExpected(argIf, [1], 1);

  compareToExpected(argIf, [], 6);
}

function testGeneratorIfElse() {
  function *ifElse(i) {
    if (i < 5) {
      yield i;
    } else {
      yield 1729;
    }
  }
  compareToExpected(ifElse, [1], 1);

  compareToExpected(ifElse, [1729], 6);
}

function *returnGen() {
  for (var i = 0; i < 10; i++) {
    if (i == 5) {
      return 1729;
    }
    yield i;
  }
}

function testGeneratorReturn() {
  forOfZipper(returnGen, [0, 1, 2, 3, 4], []);
  directAccessZipper(returnGen, [0, 1, 2, 3, 4, 1729], []);
}

function testGeneratorContinueBreak() {
  function *continueBreak() {
    for (var i = 0; i < 4; i++) {
      if (i == 2) continue;
      for (var j = 0; j < 4; j++) {
        yield i + j;
        if (i > j) {
          continue;
        } else {
          break;
        }
      }
    }
  }
  function *innerBreak() {
    for (var i = 0; i < 4; i++) {
      if (i == 2) continue;
      for (var j = 0; j < 4; j++) {
        yield i + j;
        if (i > j) {
          yield 1729;
        } else {
          break;
        }
      }
    }
  }
  compareToExpected(continueBreak, [0, 1, 2, 3, 4, 5, 6]);
  compareToExpected(innerBreak, [0, 1, 1729, 2, 3, 1729, 4, 1729, 5, 1729, 6]);
}

function testGeneratorDoWhile() {
  function *doWhile() {
    var i = 0;
    do {
      yield i;
      i++;
    } while (i < 3);
  }

  function *doWhileFalse() {
    var i = 0;
    do {
      yield i;
      i++;
    } while (false);
  }

  function *doWhileNested() {
    var i = 1;
    do {
      var j = 1;
      do {
        yield i + (3 * j);
        j++;
      } while (false);
      i++;
    } while (false);
  }
  compareToExpected(doWhile, [0, 1, 2]);
  compareToExpected(doWhileFalse, [0]);
  compareToExpected(doWhileNested, [4]);
}

function testGeneratorUndef() {
  function *yieldUndef() {
    yield;
  }
  compareToExpected(yieldUndef, [undefined]);
}

function testGeneratorExpressions() {
  function *yieldExpr() {
    return (yield ((yield 1) + (yield 2)));
  }

  var iter = yieldExpr();
  assertEquals(1, iter.next().value);
  assertEquals(2, iter.next(3).value);
  assertEquals(7, iter.next(4).value);
  assertEquals(-2, iter.next(-2).value);
}

function testGeneratorYieldAll() {
  function *yieldAll() {
    yield (yield * returnGen());
  }

  function *yieldAllStatement() {
    yield * returnGen();
  }

  function *yieldAllArray() {
    yield * [1, 2, 3];
  }
  compareToExpected(yieldAll, [0, 1, 2, 3, 4, 1729]);
  compareToExpected(yieldAllStatement, [0, 1, 2, 3, 4]);
  compareToExpected(yieldAllArray, [1, 2, 3]);
}

function testGeneratorYieldAllNext() {
  function *yieldTwo() {
    yield (yield 1);
  }

  function *yieldMany() {
    yield * yieldTwo();
  }

  var iter = yieldMany();
  assertEquals(1, iter.next().value);
  assertEquals(2, iter.next(2).value);
  assertTrue(iter.next().done);
}

function testArguments() {
  function *yieldArguments() {
    yield arguments[0];
  }
  compareToExpected(yieldArguments, [1729], 1729);
}

function testArguments2() {
  var yieldArguments = function*() {
    yield arguments[0];
  }
  compareToExpected(yieldArguments, [1729], 1729);
}

function testSwitch() {
  function *yieldSwitch(i) {
    switch (i) {
      case 1:
        yield 5;
        break;
      case 2:
        yield 6;
      default:
        yield 7;
    }
    yield 42;
  }

  compareToExpected(yieldSwitch, [5, 42], 1);
  compareToExpected(yieldSwitch, [6, 7, 42], 2);
  compareToExpected(yieldSwitch, [7, 42], 5);
}

function testSwitchContinue() {
  function *yieldSwitchCont() {
    for (var i = 0; i < 5; i++) {
      switch (i) {
        case 1:
          yield 5;
          break;
        case 3:
          yield 6;
        default:
          if (i % 2 == 0) continue;
          yield 7;
      }
      yield 42;
    }
  }

  compareToExpected(yieldSwitchCont, [5, 42, 6, 7, 42]);
}

function testSwitchFallthrough() {
  function *yieldFallthrough(i) {
    switch (i) {
      case 1:
        yield 1;
      case 2:
        yield 2;
    }
  }

  compareToExpected(yieldFallthrough, [2], 2);
  compareToExpected(yieldFallthrough, [1, 2], 1);
  compareToExpected(yieldFallthrough, [], 0);
}

function testSwitchDefault() {
  function *defOnly() {
    switch (5) {
      default:
        yield 1;
    }
  }

  compareToExpected(defOnly, [1]);
}

function testSideEffects() {
  function *sideEffects(i) {
    yield (++i * (yield i) - ++i);
  }

  var iter = sideEffects(0);
  assertEquals(1, iter.next().value);
  assertEquals(3, iter.next(5).value);
  assertTrue(iter.next().done);

  function *sideEffectsNest(i) {
    yield (++i + (yield (++i * (yield ++i) + ++i)));
  }

  iter = sideEffectsNest(0);
  assertEquals(3, iter.next().value);
  assertEquals(14, iter.next(5).value);
  assertEquals(6, iter.next(5).value);
  assertTrue(iter.next().done);
}

function testShortCircuiting_or() {
  function *or() {
    yield (yield 1) || (yield 2);
  }

  var iter = or();
  assertEquals(1, iter.next().value);
  assertEquals(3, iter.next(3).value);
  assertTrue(iter.next().done);
  iter = or();
  assertEquals(1, iter.next().value);
  assertEquals(2, iter.next(0).value);
  assertEquals(5, iter.next(5).value);
  assertTrue(iter.next().done);
}

function testShortCircuiting_and() {
  function *and() {
    yield (yield 1) && (yield 2);
  }

  var iter = and();
  assertEquals(1, iter.next().value);
  assertEquals(0, iter.next(0).value);
  assertTrue(iter.next().done);
  iter = and();
  assertEquals(1, iter.next().value);
  assertEquals(2, iter.next(3).value);
  assertEquals(5, iter.next(5).value);
  assertTrue(iter.next().done);
}

function testShortCircuiting_ternary() {
  function *ternaryLit() {
    yield (true ? 5 : (yield 4));
    yield (false ? (yield 6) : 7);
  }

  compareToExpected(ternaryLit, [5, 7]);

  function *ternary() {
    (yield 1) ? (yield 2) : (yield 3);
  }

  var iter = ternary();
  assertEquals(1, iter.next().value);
  assertEquals(2, iter.next(1).value);
  assertTrue(iter.next().done);
  iter = ternary();
  assertEquals(1, iter.next().value);
  assertEquals(3, iter.next(0).value);
  assertTrue(iter.next().done);
}

function testShortCircuiting_nested() {
  function *nested() {
    (yield 1) || (yield 2) && (yield 3)
  }

  var iter = nested();
  assertEquals(1, iter.next().value);
  assertTrue(iter.next(3).done);
  iter = nested();
  assertEquals(1, iter.next().value);
  assertEquals(2, iter.next(0).value);
  assertTrue(iter.next(0).done);
  iter = nested();
  assertEquals(1, iter.next().value);
  assertEquals(2, iter.next(0).value);
  assertEquals(3, iter.next(1).value);
  assertTrue(iter.next().done);
}

function testShortCircuitingWithSideEffects() {
  function *sideEffects(i) {
    yield (i++ * ((yield i) || (yield i+1)) - i++);
  }

  var iter = sideEffects(0);
  assertEquals(1, iter.next().value);
  assertEquals(-1, iter.next(1).value);
  assertTrue(iter.next().done);
  iter = sideEffects(1);
  assertEquals(2, iter.next().value);
  assertEquals(3, iter.next(0).value);
  assertEquals(-1, iter.next(1).value);
  assertTrue(iter.next().done);
}

function testGeneratorNested() {
  function *yieldArgumentsception() {
    function *innerGen(n) {
      yield arguments[0];
    }

    yield arguments[0];
    yield * innerGen(arguments[1]);
  }

  compareToExpected(yieldArgumentsception, [1, 2], 1, 2);
}

function testYieldInput() {
  function *stringBuilder() {
    var message = ""
    while (true) {
      var letter = yield;
      message += letter
      if (letter == '\n') {
        break;
      }
    }
    return message;
  }

  var builder = stringBuilder();

  builder.next();

  for (var i = 0; i < 5; i++) {
    builder.next('i' + i)
  }
  assertEquals("i0i1i2i3i4\n", builder.next('\n').value);
}

function testThrow() {
  function *thrower() {
    throw 1;
  }
  checkThrows(thrower, 1);
}

function testNestedFunction() {
  function * g() {
    yield f();
    function f() { return 1; }
  }
  compareToExpected(g, [1]);
}

function testGeneratorHoisted() {
  compareToExpected(f, [1, 2]);
  function *f() {
    yield 1;
    yield 2;
  }
}

function testGeneratorForParentNotBlock() {
  function *f() {
    l1:l2:for (;yield 1;yield 2) {}
  }

  var iter = f();
  assertEquals(1, iter.next().value);
  assertEquals(2, iter.next(true).value);
  assertEquals(1, iter.next().value);
  assertTrue(iter.next(false).done);

  function *g() {
    if (true) for (;yield 1;yield 2) {}
  }

  iter = g();
  assertEquals(1, iter.next().value);
  assertEquals(2, iter.next(true).value);
  assertEquals(1, iter.next().value);
  assertTrue(iter.next(false).done);
}

function testGeneratorYieldLoopGuardIncr() {
  function *f() {
    for (;yield 1;yield 2) {}
  }

  var iter = f();
  assertEquals(1, iter.next().value);
  assertEquals(2, iter.next(true).value);
  assertEquals(1, iter.next().value);
  assertTrue(iter.next(false).done);

  function *g() {
    while (yield 1) {}
  }

  iter = g();
  assertEquals(1, iter.next().value);
  assertEquals(1, iter.next(true).value);
  assertEquals(1, iter.next(true).value);
  assertTrue(iter.next(false).done);

  function *h() {
    do {} while (yield 1)
  }

  iter = h();
  assertEquals(1, iter.next().value);
  assertEquals(1, iter.next(true).value);
  assertEquals(1, iter.next(true).value);
  assertTrue(iter.next(false).done);

  function *l() {
    var sum = 0;
    var f = function*() { yield 1; };
    for (var i = 0; i < 3; i++, f = function*() { yield i; }) {
      sum += f().next().value;
    }
    yield sum;
  }

  iter = l();
  assertEquals(4, iter.next().value);
  assertTrue(iter.next().done);

  function *shortCircuit(b) {
    while (b || (yield 1)) {}
  }

  iter = shortCircuit(false);
  assertEquals(1, iter.next().value);
  assertEquals(1, iter.next(2).value);
  assertEquals(1, iter.next(42).value);
  assertTrue(iter.next(0).done);

  function *shortCircuitIf(b) {
    if (b || (yield 1)) {}
  }

  iter = shortCircuitIf(true);
  assertTrue(iter.next().done);
  iter = shortCircuitIf(false);
  assertEquals(1, iter.next().value);
  assertTrue(iter.next(0).done);
}

function testGeneratorBlockScoped() {
  'use strict';
  var f;
  assertUndefined(f);
  {
    function *f() {
      yield 1;
      yield 2;
    }
    compareToExpected(f, [1, 2]);
  }
  assertUndefined(f);
}

function testTryCatchNoYield() {
  var reached = false;
  var reached2 = false;
  function *tryCatch() {
    yield 1;
    try {
      throw 1;
      fail('Expected an exception to be thrown, but none reached');
    } catch (err) {
      reached = true;
      assertEquals(1, err);
    } finally {
      reached2 = true;
    }
    yield 2;
  }
  compareToExpected(tryCatch, [1, 2]);
  assertTrue(reached && reached2);
}

function testLabels() {
  function*labeledBreakContinue() {
    l1:
    for (var i = 0; i < 3; i++) {
      l2:
      for (var j = 0; j < 3; j++) {
        for (var k = 0; k < 3; k++) {
          yield i + 3*j + 5*k;
          if (k == 1) {
            break l2;
          } else {
            continue l1;
          }
        }
      }
    }
  }
  compareToExpected(labeledBreakContinue, [0, 1, 2]);
}

function testGeneratorReuse() {
  function* f() {
    yield 12;
  }
  assertEquals(12, f().next().value);
  assertEquals(12, f().next().value);
}

var NumberCounter = class {
  constructor(max) {
    this.max = max;
  }

  *count() {
    for (let i = 0; i < this.max; i++) {
      yield i;
    }
  }
}

function testGeneratorMethodThis() {
  var counter = new NumberCounter(5);
  compareToExpected(goog.bind(counter.count, counter), [0,1,2,3,4]);
}

function testTryCatchSimpleYield() {
  function *tryCatch() {
    try {
      yield 1;
      throw 2;
      yield 3;
    } catch (err) {
      yield 4;
    }
  }
  function *tryCatchNested() {
    try {
      try {
        yield 1;
        throw 2;
      } catch (rre) {
        yield 3;
        throw 4;
      }
    } catch (err) {
      yield 5;
    }
  }
  function *useError() {
    try {
      yield 1;
      throw 2;
    } catch (e) {
      yield e;
    }
  }
  function *emptyCatch() {
    try {
      throw 1
    } catch (e) {}
    yield 1;
  }
  function *tryInCatch() {
    try {
      throw 1
    } catch (e) {
      try {
        yield e;
        throw 2
      } catch(e2) {
        yield e2;
      }
    }
    yield 3;
  }
  compareToExpected(tryCatch, [1, 4]);
  compareToExpected(tryCatchNested, [1, 3, 5]);
  compareToExpected(useError, [1, 2]);
  compareToExpected(emptyCatch, [1]);
  compareToExpected(tryInCatch, [1, 2, 3]);
}

function testFinally() {
  function *finallyAfterCatch() {
    try {
      yield 1;
      throw 2;
    } catch (e) {
      yield e;
      yield 3;
    } finally {
      yield 4;
    }
  }

  function *finallyAfterTry() {
    try {
      yield 1;
    } catch (e) {
      yield 3;
    } finally {
      yield 4;
    }
  }

  function *justFinally() {
    try {
      yield 1;
    } finally {
      yield 2;
    }
  }

  function *tryBreak() {
    for (var j = 0; j < 1; j++) {
      try {
        yield 1;
        break;
      } finally {
        yield 2;
      }
    }
  }

  function *uncaughtThrowInCatch() {
    try {
      try { throw 1; } catch (i) { throw 5;}
    } catch (e) {
      yield e;
    }
  }

  function *uncaughtThrowInFinally() {
    try {
      try {} finally { throw 5;}
    } catch (e) {
      yield e;
    }
  }

  function *uncaughtThrowInFor() {
    try {
      for (;;) {
        try { throw 1; } catch (e) { throw 5; }
      }
    } catch (e) {
      yield e;
    }
  }

  function *correctStateProgression() {
    var x = 0;
    try {
      try {
        assertEquals(0, x++);
        throw 1;
      } catch (e) {
        assertEquals(1, x++);
        throw 5;
      } finally {
        assertEquals(2, x++);
      }
    } catch (e) {
      assertEquals(3, x++);
      yield 10;
    }
  }

  function *correctStateProgression2() {
    var x = 0;
    try {
      try {
        assertEquals(0, x++);
        throw 1;
      } finally {
        assertEquals(1, x++);
      }
    } catch (e) {
      assertEquals(2, x++);
      yield 10;
    }
  }

  function *containedBreak() {
    try {
      for (;;) {
        yield 1;
        break;
      }
    } finally {
      yield 2;
    }
  }

  compareToExpected(finallyAfterCatch, [1, 2, 3, 4]);
  compareToExpected(finallyAfterTry, [1, 4]);
  compareToExpected(justFinally, [1, 2]);
  compareToExpected(tryBreak, [1, 2]);
  compareToExpected(uncaughtThrowInCatch, [5]);
  compareToExpected(uncaughtThrowInFinally, [5]);
  compareToExpected(uncaughtThrowInFor, [5]);
  compareToExpected(correctStateProgression, [10]);
  compareToExpected(correctStateProgression2, [10]);
  compareToExpected(containedBreak, [1, 2]);

  function *uncaughtThrowInTry() {
    try {
      throw 1;
    } finally {
    }
  }
  checkThrows(uncaughtThrowInTry, 1);
}

function testGeneratorThrow() {
  function *simple() {
    yield 1;
  }

  function *tryCatch() {
    try {
      yield 1;
    } catch (e) {
      yield e;
    }
  }

  var iter = simple();
  var first = iter.next();
  assertEquals(1, first.value);
  assertFalse(first.done);
  try {
    iter.throw(42);
    fail('Expected an exception to be thrown, but none reached');
  } catch (e) {
    assertEquals(42, e);
  }

  iter = tryCatch();
  first = iter.next();
  assertEquals(1, first.value);
  assertFalse(first.done);
  var second = iter.throw(2);
  assertEquals(2, second.value);
  assertFalse(second.done);
  assertTrue(iter.next().done);
}

function testForInDeleteOrUndef() {
  function *undef() {
    var o = {i:undefined, j:2};
    for (var key in o) {
      yield o[key];
    }
  }

  var expected = [undefined, 2];
  var actual = [];
  for (var v of undef()) {
    actual.push(v);
  }
  assertSameElements(expected, actual);

  function *del() {
    var o = {i:2, j:2};
    for (var key in o) {
      yield o[key];
      for (var k in o) {
        delete o[k];
      }
    }
  }

  var out = [];
  for (v of del()) {
    out.push(v);
  }
  assertEquals(1, out.length);
  assertEquals(2, out[0]);
}
