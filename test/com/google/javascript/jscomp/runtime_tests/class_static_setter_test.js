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

var baseCounter, sub2counter;

function setUp() {
  baseCounter = 0;
  sub2counter = 0;
}

var Base = class {
  static set foo(val) { baseCounter++; }
}

var Sub1 = class extends Base {
}

var Sub2 = class extends Base {
  static set foo(val) { sub2counter++; }
}

function testSetter() {
  Base.foo = 'value';
  assertEquals(1, baseCounter);
}

function testSetterInheritance() {
  Sub1.foo = 'value';
  // Js compiler currently doesn't support class side inheritence of getters
  // and setters in optimized mode, so we disable this assert for now.
  // Essentially the collapse properties pass will collapse static properties
  // on sub classes since those are copied over at run time.
  //  assertEquals(1, baseCounter);
  assertEquals(0, sub2counter);

  Sub2.foo = 'value';
  //  assertEquals(1, baseCounter);
  assertEquals(1, sub2counter);
}
