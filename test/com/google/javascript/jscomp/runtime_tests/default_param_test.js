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
 * Tests transpilation of default parameters.
 *
 * @author moz@google.com (Michael Zhou)
 */
goog.require('goog.testing.jsunit');

function testBasic() {
  function f(a = 3) { return a; }
  assertEquals(3, f());
}

function testOverrideDefault() {
  function f(a = 3) { return a; }
  assertEquals(4, f(4));
}

function testMultipleDefaults() {
  function f(a = 3, b = 4) { return [a, b]; }
  assertArrayEquals([3, 4], f());
  assertArrayEquals([1, 4], f(1));
  assertArrayEquals([1, 2], f(1, 2));
}

function testRegularAndDefault() {
  function f(a, b, c = 3) { return [a, b, c]; }
  assertArrayEquals([1, 2, 3], f(1, 2));
}

function testComplexDefault() {
  var z = 5;
  function f(y = (function() { z = 3; return z; })()) { return z; }
  assertEquals(3, f());
}

function testArrowFunctionDefaultShadowed() {
  var x = true;
  var f = (a = x) => { const x = false; return a; }
  assertTrue(f());
}

function testDefaultShadowedByVar() {
  var x = true;
  function f(a = x) { var x = false; return a; }
  assertTrue(f());
}

function testDefaultShadowedByVar1() {
  var x = 4;
  function f(a = x) { var x = 5; { let x = 99; } return a + x; }
  assertEquals(9, f());
}

function testDefaultShadowedByLet() {
  var x = 4;
  function f(a = x) { let x = 5; { let x = 99; } return a + x; }
  assertEquals(9, f());
}
