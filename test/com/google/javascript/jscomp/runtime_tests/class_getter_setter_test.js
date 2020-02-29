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
};

function testGetter() {
  let gs = new GetterSetter();
  assertEquals('foo', /** @type {?} */ (gs.foo));
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
};

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
};

function testSubclassGetter() {
  let s = new Sub();
  assertEquals('sub', /** @type {?} */ (s.foo));
  assertEquals(0, s.counter);
  s.foo = 'new value';
  assertEquals(1, s.counter);
}

class FooWithSuffix extends Sub {
  constructor() {
    super();
    this.foo_ = 'initial-value';
  }

  /** @return {string} */
  get foo() {
    // Invoke super class getter
    return /** @type {?} */ (super.foo) + '-' + /** @type {?} */ (this.foo_);
  }

  /** @param {string} val */
  set foo(val) {
    this.foo_ = val;
  }
}

function testInvokeSuperGetter() {
  let s1 = new FooWithSuffix();
  let s2 = new FooWithSuffix();

  s1.foo = 'modified';
  assertEquals('sub-modified', s1.foo);
  // s2 should not have been affected
  assertEquals('sub-initial-value', s2.foo);
}

var Multiple = class {
  get foo() {
    return 'foo';
  }

  get bar() {
    return 'bar';
  }
};

function testMultiple() {
  let s =  new Multiple();
  assertEquals('foo', /** @type {?} */ (s.foo));
  assertEquals('bar', /** @type {?} */ (s.bar));
}

/**
 * @param {string} str
 * @return {string}
 */
const identity = str => str;

/** @unrestricted */
var ComputedGetterSetter = class {
  constructor() {
    this.counter = 0;
  }

  get[identity('foo')]() {
    return 'foo';
  }

  set[identity('fo') + 'o'](val) {
    this.counter++;
  }
};

function testComputedGetter() {
  let gs = new ComputedGetterSetter();
  assertEquals('foo', gs['foo']);
}

function testComputedSetter() {
  let gs = new ComputedGetterSetter();
  gs['foo'] = 'bar';
  assertEquals(1, gs.counter);
}

const /** !Array<number> */ numbers = [];
/**
 * @param {number} n
 * @return {number}
 */
const append = function(n) {
  numbers.push(n);
  return n;
};

/** @unrestricted */
var ComputedGetterSetterSideEffects = class {
  get[append(1)]() {}
  set[append(1)](x) {}
  set[append(2)](x) {}
  [append(3)]() {}
  static[append(4)]() {}
  get[append(5)]() {}
  set[append(5)](y) {}
};

function testComputedSideEffectOrdering() {
  new ComputedGetterSetterSideEffects();
  assertArrayEquals(numbers, [1, 1, 2, 3, 4, 5, 5]);
}

const one = 1;
const alsoOne = 1;

/** @unrestricted */
var ComputedGetterSetterKeyOverride = class {
  constructor() {
    this.counter = 0;
  }

  get[one]() {
    return 1;
  }

  set[alsoOne](val) {
    this.counter++;
  }
};

function testComputedGetter_getterNotOverridenBySetter() {
  let gs = new ComputedGetterSetterKeyOverride();
  assertEquals(1, gs[1]);
}

function testComputedSetter_setterCanFollowGetter() {
  let gs = new ComputedGetterSetterKeyOverride();
  assertEquals(0, gs.counter);
  gs[1] = 2;
  assertEquals(1, gs.counter);
}
