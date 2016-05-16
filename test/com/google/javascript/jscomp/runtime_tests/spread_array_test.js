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
 * Tests transpilation of spread arrays.
 *
 * @author moz@google.com (Michael Zhou)
 */
goog.require('goog.testing.jsunit');

function testBasic() {
  var mid = [2, 3];
  assertArrayEquals([1, 2, 3, 4], [1, ...mid, 4]);
}

function testExpression() {
  function mid() { return [2, 3]; }
  assertArrayEquals([1, 2, 3, 4], [1, ...mid(), 4]);
}

function testMultipleSpread() {
  var mid = [2];
  function mid2() { return [3]; }
  assertArrayEquals([1, 2, 3, 4], [1, ...mid, ...mid2(), 4]);
}

function testArguments() {
  function f(a, b) { return [1, ...arguments, 4]; }
  assertArrayEquals([1, 2, 3, 4], f(2, 3));
}

function testStringSpread() {
  assertArrayEquals(['s', 't', 'r', 'i', 'n', 'g'], [...'string']);
  assertArrayEquals(['s', 't', 'r', 'i', 'n', 'g'], ['s', ...'trin', 'g']);
}

function testIterableSpread() {
  // TODO(dimvar): Remove annotation once Iterable is a @record and NTI can
  // handle @record.
  /** @return {!Iterable<?>} */
  function* gen() {
    for (let i = 0; i < 3; i++)
      yield i;
  }

  assertArrayEquals([0, 1, 2], [...gen()]);
  assertArrayEquals([-1, 0, 1, 2, 3], [-1, ...gen(), 3]);
}
