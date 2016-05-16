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
 * Tests transpilation of spread calls.
 *
 * @author moz@google.com (Michael Zhou)
 */
goog.require('goog.testing.jsunit');

function testBasic() {
  function f(a, b) { return [a, b]; }
  var arr = [1, 2];
  assertArrayEquals([1, 2], f(...arr));
}

function testExpression() {
  function f(a, b) { return [a, b]; }
  function arr() { return [1, 2]; }
  assertArrayEquals([1, 2], f(...arr()));
}

function testArguments() {
  function makeArray(a, b) {
    return [a, b];
  }
  function f(var_args) { return makeArray(...arguments); }
  assertArrayEquals([1, 2], f(1, 2));
}

function testMultipleSpread() {
  function f(a, b, c, d, e) { return [a, b, c, d, e]; }
  var b = [2, 3], c = [4, 5];
  assertArrayEquals([1, 2, 3, 4, 5], f(1, ...b, ...c));
}

function testStringSpread() {
  function f(s, t, r, i, n, g) { return [s, t, r, i, n, g]; }
  assertArrayEquals(['s', 't', 'r', 'i', 'n', 'g'], f(...'string'));
}

function testIterableSpread() {
  // TODO(dimvar): Remove annotation once Iterable is a @record and NTI can
  // handle @record.
  /** @return {!Iterable<?>} */
  function* gen() {
    for (let i = 0; i < 3; i++)
      yield i;
  }

  function f(a, b, c) { return [a, b, c]; }
  assertArrayEquals([0, 1, 2], f(...gen()));
}
