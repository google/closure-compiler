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

var i = 0;

var Base = class {
  static get foo() { i++; return 'base'; }
}

var Sub1 = class extends Base {}

var Sub2 = class extends Base {
  static get foo() { return 'sub2'; }
}

function testInheritedGetter() {
  assertEquals(0, i);
  assertEquals('base', Base.foo);
  assertEquals(1, i);
  assertEquals('base', Sub1.foo);
  assertEquals(2, i);
  assertEquals('sub2', Sub2.foo);
  assertEquals(2, i);
}
