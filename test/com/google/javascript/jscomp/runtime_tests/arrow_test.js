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
 * Tests transpilation of arrow functions.
 *
 * @author moz@google.com (Michael Zhou)
 */
goog.require('goog.testing.jsunit');

function testBasic() {
  var a = [1, 2, 3];
  function map(arr, f) {
    var result = [];
    for (var i = 0; i < arr.length; i++) {
      result.push(f(arr[i]));
    }
    return result;
  }
  assertArrayEquals([2, 3, 4], map(a, (x) => x + 1));
}

function testTemplate() {
  var f = x => `x is ${x}`;
  assertEquals('x is foo', f('foo'));
}

function testArguments() {
  function getArgs(x) {
    return ((y) => arguments)(7);
  }

  assertEquals(5, getArgs(5)[0]);
}

function forEach(arr, f) {
  for (var i = 0; i < arr.length; i++) {
    f(arr[i]);
  }
}

function testTwoArrowFunctionsInSameFunctionScope() {
  var count = 0;
  forEach([1, 2], x => count += x);
  forEach([3, 4], x => count += x);
  assertEquals(10, count);
}

function testTwoArrowFunctionsInSameBlockScope() {
  while (true) {
    var count = 0;
    forEach([1, 2], x => count += x);
    forEach([3, 4], x => count += x);
    assertEquals(10, count);
    break;
  }
}

// https://github.com/google/closure-compiler/issues/932
function testBug932_this() {
  var count = 0;
  class C {
    log(x) {
      count++;
    }

    f(xs) {
      forEach(xs, x => this.log(x));
      if (xs.length > 1) {
        forEach(xs, x => this.log(x));
      }
    }
  }

  var c = new C();
  c.f([1, 2]);
  assertEquals(4, count);
}

// https://github.com/google/closure-compiler/issues/932
function testBug932_arguments() {
  var log = [];

  class C {
    log(x) {
      log.push(x);
    }

    f(var_args) {
      forEach(arguments, x => this.log(arguments[0]));
      if (arguments.length > 1) {
        forEach(arguments, x => this.log(arguments[1]));
      }
    }
  }

  var c = new C();
  c.f(3, 4);
  assertArrayEquals([3, 3, 4, 4], log);
}
