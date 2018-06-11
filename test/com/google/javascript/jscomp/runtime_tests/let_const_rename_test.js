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

function testDoWhileFold() {
  let x = 1;
  (function () {
    do {
      let x = 2;
      assertEquals(2, x);
    } while (x = 10, false);
  })();
  assertEquals(10, x);
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

function testLoopClosureWithNestInnerFunctions() {
  let x = [];
  for (let i = 0; i < 3; i++) {
    x.push(() => () => i);
  }
  let res = [];
  for (let i = 0; i < x.length; i++) {
    res.push(x[i]()());
  }
  assertArrayEquals([0, 1, 2], res);
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

function testLabeledLoop() {
  const arr = [];
  label:
  for (let i = 0; i < 3; i++) {
    arr.push(() => i);
    continue label;
  }
  assertArrayEquals([0, 1, 2], [arr[0](), arr[1](), arr[2]()]);
}

function testRenamingDoesNotBreakObjectShorthand() {
  let x = 2;
  {
    let x = 4;
    assertObjectEquals({x: 4}, {x});
  }
  assertObjectEquals({x: 2}, {x});
}

function testContinueDoesNotBreakClosures() {
  // github issue #2779
  var closures = [];
  var x = 0;
  while (x < 5) {
    const y = x;
    closures.push(function() {
      return y;
    });
    x++;
    continue;  // does this skip the update for the y variable?
  }
  var results = [];
  for (let i = 0; i < closures.length; ++i) {
    results[i] = closures[i]();
  }
  assertArrayEquals([0, 1, 2, 3, 4], results);
}

function testNestedContinueDoesNotBreakClosures() {
  // github issue #2779
  const inputWords = ['abc', 'def', 'ghi', 'jkl'];
  const wordClosures = [];
  const letterClosures = [];
  OUTER: while (inputWords.length > 0) {
    const word = inputWords.shift();
    for (const letter of word) {
      if (letter == 'a') {
        continue;  // skip letter a
      } else if (letter == 'h') {
        continue OUTER;  // skip word containing 'i'
      } else {
        letterClosures.push(() => letter);
      }
    }
    wordClosures.push(() => word);
  }
  const words = [];
  for (const wordFunc of wordClosures) {
    words.push(wordFunc());
  }
  // 'ghi' was skipped because it contained 'h'
  assertArrayEquals(['abc', 'def', 'jkl'], words);

  let letters = '';
  for (const letterFunc of letterClosures) {
    letters += letterFunc();
  }
  // a skipped explicitly
  // h skipped explicitly
  // i skipped because it was after h and in the same word
  assertEquals('bcdefgjkl', letters);
}

function testLetWithSameName_multivariateDeclaration() {
  // See https://github.com/google/closure-compiler/issues/2969
  {
    let x = 1, y = 2;
    assertEquals(1, x);
    assertEquals(2, y);
  }
  {
    let x = 3, y = 4;
    assertEquals(3, x);
    assertEquals(4, y);
  }
}

function testLetInLoopClosure_multivariateDeclaration() {
  for (let i = 0; i < 3; i++) {
    let x, y;
    // Verify that x and y are always undefined here, even though they are
    // reassigned later in the loop.
    assertUndefined(x);
    assertUndefined(y);
    function f() {
      x = y = 3;
    }
    f();
    assertEquals(3, x);
    assertEquals(3, y);
  }
}
