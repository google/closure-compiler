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
 * Tests transpilation of rest parameters.
 *
 * @author moz@google.com (Michael Zhou)
 */
goog.require('goog.testing.jsunit');

function testBasic() {
  function f(...args) { return args; }
  assertArrayEquals([1, 2, 3], f(1, 2, 3));
}

function testEmpty() {
  function f(...args) { return args; }
  assertArrayEquals([], f());
}

function testRegularAndRest() {
  function f(a, b, ...c) { return [[a, b], c]; }
  assertArrayEquals([[1, 2], [3, 4]], f(1, 2, 3, 4));
}

/**
 * Just making sure that we don't build a type annotation that
 * crashes the typechecker.
 * See https://github.com/google/closure-compiler/issues/1207
 * @param {...!Array<*>} x
 */
function testGithub1207(...x) {
  var y = x;
}

function testRestArrayPattern() {
  function f(a, ...[re, s, ...t]) { return [a, re, s, t]; }

  var params = f(1, 2, 3, 4, 5);
  assertEquals(params[0], 1);
  assertEquals(params[1], 2);
  assertEquals(params[2], 3);
  assertArrayEquals(params[3], [4, 5]);
}
