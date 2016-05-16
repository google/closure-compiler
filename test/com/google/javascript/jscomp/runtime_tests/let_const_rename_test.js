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
 * Tests transpilation of let/const declarations.
 *
 * @author moz@google.com (Michael Zhou)
 */
goog.require('goog.testing.jsunit');

function testLetShadowing() {
  function f() {
    var x = 1;
    if (x < 1) {
      let x = 2;
      x = function() { return x; };
    }
    return x;
  }
  assertEquals(1, f());
}

function testNonUniqueLet() {
  function f() {
    var x = 1;
    if (true) {
      let x = 2;
      assertEquals(2, x);
    }
    if (true) {
      let x;
      assertEquals(undefined, x);
    }
    return x;
  }
  assertEquals(1, f());
}

function testLoopLet_for() {
  for (let i = 0; i < 100; i++) {
    let y;
    assertEquals(undefined, y);
    y = i + 2;
  }
}

function testLoopLet_forTyped() {
  for (let i = 0; i < 100; i++) {
    /** @type {number} */
    let y;
    assertEquals(undefined, y);
    y = i + 2;
  }
}

function testLoopLet_forTypedInline() {
  for (let i = 0; i < 100; i++) {
    let /** number */ y;
    assertEquals(undefined, y);
    y = i + 2;
  }
}

function testLoopLet_forIn() {
  for (let i in [1, 2, 3, 4, 5, 6, 7, 8]) {
    let y;
    assertEquals(undefined, y);
    y = i + 2;
  }
}

function testLoopLet_forOf() {
  for (let i of [1, 2, 3, 4, 5, 6, 7, 8]) {
    let y;
    assertEquals(undefined, y);
    y = i + 2;
  }
}

function testLoopLet_while() {
  let i = 0;
  while (i<10) {
    i++;
    let y;
    assertEquals(undefined, y);
    y = i + 2;
  }
}

function testLoopLet_doWhile() {
  let i = 0;
  do {
    i++;
    let y;
    assertEquals(undefined, y);
    y = i + 2;
  } while (i<10);
}

function testCapturedMutatedLet() {
  function f() {
    if (true) {
      let value = 1;
      var g = function() {
        return value;
      };
      value++;
    }

    if (true) {
      let value = 3;
      value++;
    }
    return g;
  }
  assertEquals(2, (f())());
}


function testCapturedMutatedLet2() {
  function f(value) {
    if (true) {
      let value = 1;
      var g = function(x) {
        return value + x;
      };
      value++;
    }
    if (true) {
      let value = 3;
      value++;
    }
    return g(value);
  }
  assertEquals(12, f(10));
}

function testCapturedMutatedLet3() {
  var value = 7;
  function f() {
    if (true) {
      let value = 1;
      var g = function(x) {
        return value + x;
      };
      value++;
    }
    if (true) {
      let value = 3;
      value++;
    }
    return g(value);
  }
  assertEquals(9, f());
}

function testLoopCapturedLet() {
  const arr = [];
  for (let i = 0; i < 10; i++) {
    arr.push(function() { return i; });
  }
  assertEquals(5, arr[5]());
}

function testLoopCapturedLet1() {
  const arr = [];
  for (let i = 0; i < 10; i++) {
    let y = i;
    arr.push(function() { return y; });
  }
  assertEquals(5, arr[5]());
}

function testLoopCapturedLet2() {
  const arr = [];
  let i = 0;
  while (i < 10) {
    let y = i;
    arr.push(function() { return y; });
    i++;
  }
  assertEquals(5, arr[5]());
}

function testLoopCapturedLet3() {
  const arr = [];
  for (let i = 0; i < 10; i++) {
    let y = i;
    arr.push(function() { return y + i; });
  }
  assertEquals(10, arr[5]());
}

/**
 * In this case, the "i" in the loop body shadows the index variable "i",
 * which is allowed in ES6. We need to make sure the function pushed into the
 * array captures the inner "i" instead of the outer one.
 *
 * TODO(moz): Maybe generate a warning in this case.
 */
function testLoopCapturedLet4() {
  const arr = [];
  let x = 0;
  for (let i = 0; i < 10; i++) {
    let i = x + 1;
    arr.push(function() { return i + i; });
    x++;
  }
  assertEquals(12, arr[5]());
}

function testDoWhileCapturedLet() {
  const arr = [];
  let i = 0;
  do {
    let y = i;
    arr.push(function() { return y; });
    i++;
  } while (i < 10);
  assertEquals(5, arr[5]());
}

function testMutatedLoopCapturedLet() {
  const arr = [];
  for (let i = 0; i < 10; i++) {
    arr.push(function() { return ++i; });
  }
  assertEquals(6, arr[5]());
}

function testMutatedLoopCapturedLet1() {
  const arr = [];
  for (let i = 0; i < 10; i++) {
    arr.push(function() { return i; });
    i += 100;
  }
  assertEquals(100, arr[0]());
}

function testMutatedNestedLoopCapturedLet() {
  function f() {
    let fns = [];
    for (let i = 0; i < 10; i++) {
      for (let j = 0; j < 10; j++) {
         fns.push(function() {
           return j++ + i++;
         });
         fns.push(function() {
           return j++ + i++;
         });
      }
    }
    assertEquals(2, fns[5]());  // i = 0, j = 2
    assertEquals(4, fns[6]());  // i = 0 + 1, j = 2 + 1
  }
  f();
}

function testCommaInForInitializerAndIncrement() {
  const arr = [];
  for (let i = 0, j = 0; i < 10; i++, j++) {
    arr.push(function() { return i++ + j++; });
    arr.push(function() { return i++ + j++; });
  }
  assertEquals(4, arr[4]()); // i = 2, j = 2
  assertEquals(6, arr[5]()); // i = 2 + 1, j = 2 + 1
}

function testForInCapturedLet() {
  const arr = [];
  for (let i in [0, 1, 2, 3, 4, 5]) {
    i = Number(i);
    arr.push(function() { return i++; });
    arr.push(function() { return i++; });
  }
  assertEquals(2, arr[4]());  // i = 2
  assertEquals(3, arr[5]());  // i = 2 + 1
}
