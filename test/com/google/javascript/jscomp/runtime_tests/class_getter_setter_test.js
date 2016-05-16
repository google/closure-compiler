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

var GetterSetter = class {
  constructor() {
    this.counter = 0;
  }

  get foo() {
    return 'foo';
  }

  set foo(val) {
    this.counter++;
  }
}

function testGetter() {
  let gs = new GetterSetter();
  assertEquals('foo', gs.foo);
}

function testSetter() {
  let gs = new GetterSetter();
  gs.foo = 'bar';
  assertEquals(1, gs.counter);
}

var Base = class {
  get foo() {
    return 'base';
  }

  set foo(val) {
    throw new Error('Should not have been called.');
  }
}

var Sub = class extends Base {
  constructor() {
    super();
    this.counter = 0;
  }

  get foo() {
    return 'sub';
  }

  set foo(val) {
    this.counter++;
  }
}

function testSubclassGetter() {
  let s = new Sub();
  assertEquals('sub', s.foo);
  assertEquals(0, s.counter);
  s.foo = 'new value';
  assertEquals(1, s.counter);
}

var Multiple = class {
  get foo() {
    return 'foo';
  }

  get bar() {
    return 'bar';
  }
}

function testMultiple() {
  let s =  new Multiple();
  assertEquals('foo', s.foo);
  assertEquals('bar', s.bar);
}
