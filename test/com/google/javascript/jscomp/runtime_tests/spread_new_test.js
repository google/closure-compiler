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

/** @private */
class Example {
  constructor(x, y, z) {
    assertTrue(this instanceof Example);
    assertEquals(2, x);
    assertEquals(3, y);
    assertEquals(5, z);
  }
}

function testSpreadNew_arguments() {
  function f(var_args) {
    let example = new Example(...arguments);
    assertTrue(example instanceof Example);
  }
  f(2, 3, 5);

  function g(var_args) {
    let example = new Example(2, ...arguments);
  }
  g(3, 5);
}

function testSpreadNew_array() {
  let args = [2, 3, 5];
  let example = new Example(...args);
  assertTrue(example instanceof Example);

  args = [3, 5];
  example = new Example(2, ...args);
  assertTrue(example instanceof Example);
}
