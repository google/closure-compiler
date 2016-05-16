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
 * Tests ES6 for...of iteration.
 *
 * @author mattloring@google.com (Matthew Loring)
 */
goog.require('goog.testing.jsunit');

// https://github.com/google/closure-compiler/issues/717
function testIterationVarShadows() {
  let x = 'outer';
  let result = [];
  for (let x of [5, 6, 7]) {  // let: This is a new 'x' variable.
    result.push(x);
  }
  assertEquals(5, result[0]);
  assertEquals(6, result[1]);
  assertEquals(7, result[2]);
  assertEquals('outer', x);
}

function testIterationVarIsDeclaredInOuterScope() {
  let x = 'outer';
  let result = [];
  for (x of [5, 6, 7]) {  // no declaration: This is the same 'x' as above.
    result.push(x);
  }
  assertEquals(5, result[0]);
  assertEquals(6, result[1]);
  assertEquals(7, result[2]);
  assertEquals(7, x);
}

function testForOfString() {
  let result = [];
  for (let c of 'a string') {
    result.push(c);
  }
  assertArrayEquals(['a', ' ', 's', 't', 'r', 'i', 'n', 'g'], result);
}

function testArguments() {
  function f(var_args) {
    let result = [];
    for (let a of arguments) {
      result.push(a);
    }
    return result;
  }
  assertArrayEquals([5, 6, 7, 8], f(5, 6, 7, 8));
}

function testForOfArray() {
  var arr = [5, 6, 7];
  var result = [];
  for (var val of arr) {
    result.push(val);
  }
  assertEquals(5, result[0]);
  assertEquals(6, result[1]);
  assertEquals(7, result[2]);
}

function testForOfArrayLet() {
  var arr = [5, 6, 7];
  var result = [];
  for (let val of arr) {
    result.push(val);
  }
  assertEquals(5, result[0]);
  assertEquals(6, result[1]);
  assertEquals(7, result[2]);
}

function testForOfArrayNested() {
  var arr = [5, 6, 7];
  var result = [];
  for (var val of arr) {
    for (var valN of arr) {
      result.push(val + valN);
    }
  }
  assertEquals(10, result[0]);
  assertEquals(11, result[1]);
  assertEquals(12, result[2]);
  assertEquals(11, result[3]);
  assertEquals(12, result[4]);
  assertEquals(13, result[5]);
  assertEquals(12, result[6]);
  assertEquals(13, result[7]);
  assertEquals(14, result[8]);
}
