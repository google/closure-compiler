/*
 * Copyright 2018 The Closure Compiler Authors.
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
goog.module('jscomp.test.object_spread');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

/** @type {number} */
var callCount = 0;

/** @return {{a: number, b: number, c: number}} */
function foo() {
  callCount++;
  return {a: 1, b: 2, c: 3};
}

/** @return {string} */
function baz() {
  callCount++;
  return 'baz';
}

/** @return {number} */
function getCallCount() {
  return callCount;
}

/**
 * Performs the check `assertEquals('undefined', typeofResult);`.
 *
 * This is intended to work with the `typeof` operator. It's useful when testing
 * the existence of properties the compiler doesn't know about because the
 * compiler doesn't warn about missing properties inside `typeof`.
 *
 * Example   : `assertIsStringUndefined(typeof rest.a);`
 * Instead of: `assertUndefined(rest.a);`
 *
 * @param {string} typeofResult
 */
function assertIsStringUndefined(typeofResult) {
  assertEquals('undefined', typeofResult);
}

testSuite({

  setUp() {
    callCount = 0;
  },

  testBasicSpread() {
    var spread = foo();
    var x = {d: 4, e: 5, ...spread};
    assertEquals(1, callCount);
    assertEquals(4, x.d);
    assertEquals(5, x.e);
    assertEquals(1, x.a);
    assertEquals(2, x.b);
    assertEquals(3, x.c);

    var xx = {d: 4, e: 5, ...spread};
    assertEquals(1, callCount);
    assertEquals(4, xx.d);
    assertEquals(5, xx.e);
    assertEquals(1, xx.a);
    assertEquals(2, xx.b);
    assertEquals(3, xx.c);

    var y = {d: 4, e: 5, ...foo()};
    assertEquals(2, callCount);
    assertEquals(4, y.d);
    assertEquals(5, y.e);
    assertEquals(1, y.a);
    assertEquals(2, y.b);
    assertEquals(3, y.c);

    var spread2 = {f: 8, g: 9};
    var z = {d: 4, e: 5, ...spread, ...spread2};
    assertEquals(2, callCount);
    assertEquals(4, z.d);
    assertEquals(5, z.e);
    assertEquals(1, z.a);
    assertEquals(2, z.b);
    assertEquals(3, z.c);
    assertEquals(8, z.f);
    assertEquals(9, z.g);
  },

  testSpreadOverwriting() {
    var x = {
      ...foo(),
      ...{
        c: 4
      }
    };
    assertEquals(1, x.a);
    assertEquals(2, x.b);
    assertEquals(4, x.c);
  },

  testNestedSpread() {
    var spread = foo();
    var x = {
      d: 1,
      e: 2,
      ...{
        ...spread
      }
    };
    assertEquals(1, callCount);
    assertEquals(1, x.d);
    assertEquals(2, x.e);
    assertEquals(1, x.a);
    assertEquals(2, x.b);
    assertEquals(3, x.c);
  },

  testSpreadWithComputedProperties() {
    var x = {d: 1, [baz()]: getCallCount(), ...foo()};
    assertEquals(2, callCount);
    assertEquals(1, x.d);
    assertEquals(1, x['baz']);
    assertEquals(1, x.a);
    assertEquals(2, x.b);
    assertEquals(3, x.c);
  },

  testBasicRest() {
    var {a: d, b: e, ...rest} = foo();
    assertEquals(1, callCount);
    assertEquals(1, d);
    assertEquals(2, e);
    assertIsStringUndefined(typeof rest.a);
    assertIsStringUndefined(typeof rest.b);
    assertEquals(3, rest.c);
  },

  testBasicRestConst() {
    const {a: d, b: e, ...rest} = foo();
    assertEquals(1, callCount);
    assertEquals(1, d);
    assertEquals(2, e);
    assertIsStringUndefined(typeof rest.a);
    assertIsStringUndefined(typeof rest.b);
    assertEquals(3, rest.c);
  },

  testBasicRestLet() {
    let {a: d, b: e, ...rest} = foo();
    assertEquals(1, callCount);
    assertEquals(1, d);
    assertEquals(2, e);
    assertIsStringUndefined(typeof rest.a);
    assertIsStringUndefined(typeof rest.b);
    assertEquals(3, rest.c);
  },

  testBasicRestAssign() {
    var d, e, rest;
    ({a: d, b: e, ...rest} = foo());
    assertEquals(1, callCount);
    assertEquals(1, d);
    assertEquals(2, e);
    assertIsStringUndefined(typeof rest.a);
    assertIsStringUndefined(typeof rest.b);
    assertEquals(3, rest.c);
  },

  testRestAmongBindings() {
    var pre = foo(), {a: d, b: e, ...rest} = foo(), post = foo();
    assertEquals(3, callCount);
    assertEquals(1, d);
    assertEquals(2, e);
    assertIsStringUndefined(typeof rest.a);
    assertIsStringUndefined(typeof rest.b);
    assertEquals(3, rest.c);

    // these assertions just make sure pre and post are read to squelch lint.
    assertEquals(1, pre.a);
    assertEquals(1, post.a);
  },

  testRestAmongAssigns() {
    var pre, d, e, rest, post;
    pre = foo(), {a: d, b: e, ...rest} = foo(), post = foo();
    assertEquals(3, callCount);
    assertEquals(1, d);
    assertEquals(2, e);
    assertIsStringUndefined(typeof rest.a);
    assertIsStringUndefined(typeof rest.b);
    assertEquals(3, rest.c);

    // these assertions just make sure pre and post are read to squelch lint.
    assertEquals(1, pre.a);
    assertEquals(1, post.a);
  },

  testRestWithComputedProp() {
    var {a: d, [baz()]: e, ...rest} = {'baz': 10, ...foo()};
    assertEquals(2, callCount);
    assertEquals(1, d);
    assertEquals(10, e);
    assertUndefined(rest.a);
    assertEquals(2, rest.b);
    assertEquals(3, rest.c);
  },

  testRestInForOf() {
    var d, e, rest;
    for ({a: d, b: e, ...rest} of [foo(), foo(), foo()]) {
      assertEquals(3, callCount);
      assertEquals(1, d);
      assertEquals(2, e);
      assertUndefined(rest.a);
      assertUndefined(rest.b);
      assertEquals(3, rest.c);
    }
  },
  testRestInForOfVar() {
    for (var {a: d, b: e, ...rest} of [foo(), foo(), foo()]) {
      assertEquals(3, callCount);
      assertEquals(1, d);
      assertEquals(2, e);
      assertUndefined(rest.a);
      assertUndefined(rest.b);
      assertEquals(3, rest.c);
    }
    assertEquals(1, d);
    assertEquals(2, e);
    assertUndefined(rest.a);
    assertUndefined(rest.b);
    assertEquals(3, rest.c);
  },

  testRestInParamList() {
    var stripx = function({x = baz(), ...rest}) {
      return {x, rest};
    };
    var q = stripx({x: 1, z: 2});
    // default function not called when param supplied explicitly
    assertEquals(0, callCount);
    assertEquals(1, q.x);
    assertUndefined(q.rest.x);
    assertEquals(2, q.rest.z);

    // same thing but with arrow notation
    var stripy = ({y = baz(), ...rest}) => {
      return {y, rest};
    };
    var p = stripy({y: 1, z: 2});
    // default function not called when param supplied explicitly
    assertEquals(0, callCount);
    assertEquals(1, p.y);
    assertUndefined(q.rest.y);
    assertEquals(2, p.rest.z);
  },

  testRestInCatch() {
    try {
      throw foo();
    } catch ({a: d, ...rest}) {
      assertEquals(1, d);
      assertEquals(2, rest.b);
    }
  },
});
