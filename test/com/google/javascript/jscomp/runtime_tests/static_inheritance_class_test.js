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
 * Tests static inheritance for new ES6 classes.
 *
 * @author mattloring@google.com (Matthew Loring)
 */
goog.provide('i.Foo');
goog.provide('ns.QualifiedChild');
goog.provide('ns.QualifiedParent');
goog.provide('nsO.QualifiedChild');
goog.provide('nsO.QualifiedParent');

goog.require('goog.testing.jsunit');

var StaticInSup = class {
  /** @nocollapse */
  static f() {
    return 1729;
  }
}
var StaticInSub  = class extends StaticInSup {
  f() {
    return 1730;
  }

  /** @nocollapse */
  static g() {
    return this.f();
  }
}

function testStaticInheritance() {
  assertEquals(1729, StaticInSub.g());
}

var Grandparent = class {
  /** @nocollapse */
  static f() {
    return 1729;
  }
}
var Parent = class extends Grandparent {}
var Child = class extends Parent {
  f() {
    return 1730;
  }

  /** @nocollapse */
  static g() {
    return this.f();
  }
}

function testMultiLevelStaticInheritance() {
  assertEquals(1729, Child.g());
}

i.Foo = class { static f() { return 1729; } };
var Bar = class extends i.Foo {}

function testStaticQualifiedName() {
  assertEquals(1729, Bar.f());
}

ns.QualifiedParent = class { static f() { return 1729; } };
ns.QualifiedChild = class extends ns.QualifiedParent {};

function testStaticQualifiedParentName() {
  assertEquals(1729, ns.QualifiedChild.f());
}

nsO.QualifiedParent = class { static f() { return 1729; } };
nsO.QualifiedChild = class extends nsO.QualifiedParent {
  static f() { return super.f() + 1; }
};

function testStaticQualifiedParentNameOverride() {
  assertEquals(1730, nsO.QualifiedChild.f());
}

var Base = class {
  /** @nocollapse */
  static f() { return this.g(); }
  /** @nocollapse */
  static g() { return 'Super.g'; }
}

var Sub = class extends Base {
  /** @nocollapse */
  static f() { return super.f(); }
  /** @nocollapse */
  static g() { return 'Sub.g'; }
}

function testStaticThisSuper() {
  assertEquals('Sub.g', Sub.f());
}

var A = class {
  static h() { return 'A.h'; }
}

var module$test = {};
/** @constructor */ module$test.A = A;

/** @constructor */ var B = A;

var C  = class extends module$test.A {}

var D  = class extends B {}

function testAliasedClass() {
  assertEquals('A.h', C.h());
  assertEquals('A.h', D.h());
}

var X = class {
  /** @return {!X} */
  static get() { return new X(); }
}

var Y = class extends X {
  /** @return {!Y} */
  static get() { return new Y(); }
}

function testBug20088015() {
  assertTrue(Y.get() instanceof Y);
}

// See b/27386199
function testCustomElem() {
  if (window.HTMLElement && document.registerElement) {
    var CustomElement = class extends HTMLElement {
      foo() {}
    };

    // Just make sure we don't throw errors.
    document.registerElement('x-custom', CustomElement);
    const elem = document.createElement('x-custom');
    assertTrue(elem instanceof CustomElement);
  }
}
