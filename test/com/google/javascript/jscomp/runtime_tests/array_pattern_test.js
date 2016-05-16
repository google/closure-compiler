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
 * Tests transpilation of destructuring array patterns.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
goog.require('goog.testing.jsunit');


/**
 * Keeps track of how many times numbers() is called.
 * This is used to ensure that the RHS of an array
 * destructuring is not evaluated multiple times.
 * @type {number}
 */
var callCount;

function setUp() {
  callCount = 0;
}

/** @return {!Array<number>} */
function numbers() {
  callCount++;
  return [1, 2, 3];
}

function testEmpty() {
  // No-op, just check that we don't crash.
  var [] = numbers();
}

function testVar() {
  var [a, b, c] = numbers();
  assertEquals(1, callCount);
  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(3, c);
}

function testVar_defaultValue() {
  var [a, b = 10, c] = numbers();
  assertEquals(1, callCount);
  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(3, c);

  var nums = [4, undefined, 6];
  var [d, e = 10, f] = nums;
  assertEquals(4, d);
  assertEquals(10, e);
  assertEquals(6, f);
}

function testLet() {
  let [a, b, c] = numbers();
  assertEquals(1, callCount);
  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(3, c);
}

function testAssign() {
  var a, b, c;
  assertEquals(0, callCount);
  assertEquals(undefined, a);
  assertEquals(undefined, b);
  assertEquals(undefined, c);

  [a, b, c] = numbers();
  assertEquals(1, callCount);
  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(3, c);
}

/** @suppress {newCheckTypes} */
function testConst() {
  const [a, b, c] = numbers();
  assertEquals(1, callCount);
  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(3, c);
}

function testElision() {
  let [a, , c] = numbers();
  assertEquals(1, callCount);
  assertEquals(1, a);
  assertEquals(3, c);
}

function testRest() {
  let [...rest] = numbers();
  assertEquals(1, callCount);
  assertArrayEquals(numbers(), rest);
}

function testRestArrayPattern() {
  let [r, ...[e, ...[s, ...t]]] = numbers();
  assertEquals(1, callCount);
  assertEquals(r, 1);
  assertEquals(e, 2);
  assertEquals(s, 3);
  assertArrayEquals([], t);
}

function testRestObjectPattern() {
  let [a, ...{length: num_rest}] = numbers();
  assertEquals(1, callCount);
  assertEquals(a, 1);
  assertEquals(num_rest, 2);
}

function testRest2() {
  let [first, ...rest] = numbers();
  assertEquals(1, callCount);
  assertEquals(first, 1);
  assertArrayEquals([2, 3], rest);
}

function testRest3() {
  let [first, second, ...rest] = numbers();
  assertEquals(1, callCount);
  assertEquals(first, 1);
  assertEquals(second, 2);
  assertArrayEquals([3], rest);
}

function testWithNameAfter() {
  var [a, b, c] = numbers(),
      x = 4;
  assertEquals(1, callCount);
  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(3, c);
  assertEquals(4, x);
}

function testWithNameBefore() {
  var x = 4,
      [a, b, c] = numbers();
  assertEquals(1, callCount);
  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(3, c);
  assertEquals(4, x);
}

function testWithNameBeforeAndAfter() {
  var x = 4,
      [a, b, c] = numbers(),
      y = 5;
  assertEquals(1, callCount);
  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(3, c);
  assertEquals(4, x);
  assertEquals(5, y);
}

function testWithNameBetween() {
  var [a, b, c] = numbers(),
      x = 4,
      [d, e, f] = numbers();
  assertEquals(2, callCount);
  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(3, c);
  assertEquals(4, x);
  assertEquals(1, d);
  assertEquals(2, e);
  assertEquals(3, f);
}

/** @return {!Array} */
function nestedNumbers() {
  return [1, [2, 3]];
}

/** @return {!Array} */
function deeplyNestedNumbers() {
  return [1, [2, [3]]];
}

function testNested() {
  var [a, [b, c]] = nestedNumbers();

  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(3, c);

  var [x, [y, [z]]] = deeplyNestedNumbers();
  assertEquals(1, x);
  assertEquals(2, y);
  assertEquals(3, z);
}

function testReallyNested() {
  let [[[[[[[[x]]]]]]]] = [[[[[[[[1]]]]]]]];

  assertEquals(1, x);
}

function testFunction() {
  function f([x, y]) {
    assertEquals(5, x);
    assertEquals(6, y);
  }
  f([5, 6]);
}

function testFunctionDefault1() {
  function f([x, y] = [1, 2]) {
    assertEquals(1, x);
    assertEquals(2, y);
  }
  f();
}

function testFunctionDefault2() {
  function f([x, y] = [1, 2]) {
    assertEquals(3, x);
    assertEquals(4, y);
  }
  f([3, 4]);
}

function testCatch() {
  try {
    throw [1,2,3];
  } catch([x, ...y]) {
    assertEquals(1, x);
    assertArrayEquals([2, 3], y);
  }
}

function testIterable() {
  function *gen() {
    let i = 0;
    while (i < 6) {
      yield i++;
    }
  }

  var [a, b, c] = gen();
  assertArrayEquals([0, 1, 2], [a, b, c]);

  var [first, ...rest] = gen();
  assertEquals(0, first);
  assertArrayEquals([1, 2, 3, 4, 5], rest);
}

function testIterable_default() {
  /** Yields: 0, undefined, 2, undefined, 4, undefined. */
  function *gen() {
    let i = 0;
    while (i < 6) {
      if (i % 2 == 0) {
        yield i;
      } else {
        yield undefined;
      }
      i++;
    }
  }

  var [a, b='b', c] = gen();
  assertArrayEquals([0, 'b', 2], [a, b, c]);

  [a='a', b, c] = gen();
  assertArrayEquals([0, undefined, 2], [a, b, c]);
}
