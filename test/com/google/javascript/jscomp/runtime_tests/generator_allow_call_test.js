/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * @fileoverview Tests for ES6 generators.
 */
goog.require('goog.testing.jsunit');

function testYieldInComplexExpression1() {
  var arr = [];
  function *f() {
    var obj = { method(x) { arr.push(x); }};
    obj.method(yield 1);
  }
  for (let value of f()) {
    assertEquals(1, value);
  }
  assertArrayEquals([undefined], arr);
}

function testYieldInComplexExpression2() {
  var arr = [];
  function *f() {
    var obj = { method(x) { arr.push(x); }};
    obj.method(yield 1);
  }
  var gen = f();
  gen.next();
  gen.next(3);
  assertArrayEquals([3], arr);
}

function testYieldInComplexExpression3() {
  var arr = [];
  function *f() {
    function getObj() {
      arr.push('getObj called');
      return { method(x) { arr.push(x); } };
    }
    getObj().method(yield 1);
  }
  for (let x of f()) {
    assertEquals(1, x);
  }
  assertArrayEquals(['getObj called', undefined], arr);
}

function testYieldInComplexExpression4() {
  var arr = [];
  function *f() {
    function getObj() {
      arr.push('getObj called');
      return { method(x) { arr.push(x); } };
    }
    getObj().method(yield 1);
  }
  var gen = f();
  gen.next();
  gen.next(3);
  assertArrayEquals(['getObj called', 3], arr);
}
