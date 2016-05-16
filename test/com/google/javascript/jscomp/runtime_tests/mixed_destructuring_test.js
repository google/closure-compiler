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

goog.require('goog.testing.jsunit');

function testObjectInArray() {
  var [{a: x}] = [{a: 'x'}];
  assertEquals('x', x);
}

function testArrayInObject() {
  var {a: [b, c]} = {a: [1, 2]};
  assertEquals(1, b);
  assertEquals(2, c);
}

function testObjectInArray_defaultValue() {
  var [{a: x} = {a: 'y'}] = [{a: 'x'}];
  assertEquals('x', x);

  [{a: x} = {a: 'x'}] = [];
  assertEquals('x', x);
}

function testFunction1() {
  function f({x}, [y]) {
    assertEquals(0, x);
    assertEquals(1, y);
  }
  f({x:0}, [1]);
}

function testFunction2() {
  function f([x, {y: z}]) {
    assertEquals(1, x);
    assertEquals(2, z);
  }
  f([1, {y: 2}]);
}

function testFunction3() {
  function f([x, {y}]) {
    assertEquals(1, x);
    assertEquals(2, y);
  }
  f([1, {y: 2}]);
}

function testFunction4() {
  function f({x: [y]}) {
    assertEquals(1, y);
  }
  f({x: [1]});
}
