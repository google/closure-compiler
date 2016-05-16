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
 * Tests dynamic dispatch of functions after transpilation
 * from ES6 to ES3.
 *
 * @author mattloring@google.com (Matthew Loring)
 */
goog.provide('baz.Bar');
goog.provide('baz.Foo');

goog.require('goog.testing.jsunit');

var DynamicDispSup = class {
  g(str) {
    return this.f(str);
  }
  f(str) {
    return str + 'super';
  }
}
var DynamicDispSub = class extends DynamicDispSup {
  f(str) {
    return str + 'sub';
  }
}

function testDynamicDispatch() {
  var dds = new DynamicDispSub();
  assertEquals('hisub', dds.g('hi'));
}

var SuperCallSup = class {
  foo(b) {
    return b + 'sup';
  }
}
var SuperCallSub = class extends SuperCallSup {
  foo(bar) {
    return super.foo(bar);
  }
}

function testSuperCall() {
  var scs = new SuperCallSub();
  assertEquals('hsup', scs.foo('h'));
}

var ConstructorNoArgs = class {
  constructor() {
    this.a = 1729;
  }
}

function testConstructorEmpty() {
  assertEquals(1729, (new ConstructorNoArgs()).a);
}

var ConstructorArgs = class {
  /** @param {number} b */
  constructor(b) {
    this.a = b;
  }
}

function testConstructorArgs() {
  assertEquals(1729, (new ConstructorArgs(1729)).a);
}

var SuperConstructorSup = class {
  constructor() {
    this.m = 1729;
  }
  getM() {
    return this.m;
  }
  method() {
    class SuperConstructorSub extends SuperConstructorSup {
      constructor() {
        super();
      }
    }
    var d = new SuperConstructorSub();
    return d.getM();
  }
}

function testSuperConstructor() {
  var scsup = new SuperConstructorSup();
  assertEquals(1729, scsup.method());
}

/** @export */
baz.Foo = class {

  constructor(i) {
    this.i = i;
  }

  f() {
    return this.i;
  }

}

baz.Bar = class extends baz.Foo {
  constructor() {
    super(1729);
  }
}

function testQualifiedNames() {
  assertEquals(1729, (new baz.Bar()).f());
}

function testExport() {
  assertEquals(5, new window['baz']['Foo'](5).f());
}

var A = class {
  constructor() {
    this.x = 1234;
  }
  init() {}
  /** @nocollapse */
  static create() {
    var newThis = new this();
    newThis.init.apply(newThis, arguments);
    return newThis;
  }
}

var B = class extends A {
  init() {
    super.init();
  }
}

function testImplicitSuperConstructorCall() {
  assertEquals(1234, B.create().x);
}

