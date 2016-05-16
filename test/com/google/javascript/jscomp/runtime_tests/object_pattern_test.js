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

function testBasic() {
  var {key: value} = {key: 'value'};
  assertEquals('value', value);
}

function testShorthand() {
  var {key} = {key: 'value'};
  assertEquals('value', key);
}

function testNested() {
  var {key1: {key2}} = {key1: {key2: 'value'}};
  assertEquals('value', key2);
}

function testAssign() {
  var x, y;
  ({a: x, b: y} = {a: 1, b: 2});
  assertEquals(1, x);
  assertEquals(2, y);
}

function testSideEffects() {
  let callCount = 0;

  /** @return {{a: number, b: number}} */
  function f() {
    callCount++;
    return {a: 1, b: 2};
  }
  const {a, b} = f();
  assertEquals(1, a);
  assertEquals(2, b);
  assertEquals(1, callCount);
}

/** @suppress {newCheckTypes} */
function testInitializer() {
  function f() {
    return {};
  }
  var {key1 = 'default'} = f();
  assertEquals('default', key1);
}

function testFunction() {
  function f({key: value}) {
    assertEquals('v', value);
  }
  f({key: 'v'});

  function g(x, {key1: value1, key2: value2}, y) {
    assertEquals('foo', x);
    assertEquals('v1', value1);
    assertEquals('v2', value2);
    assertEquals('bar', y);
  }
  g('foo', {key2: 'v2', key1: 'v1'}, 'bar');
}

function testFunctionDefault1() {
  var x = 1;
  function f({x = 2}) {
    assertEquals(2, x);
  }
  f({});
}

function testFunctionDefault2() {
  var x = 1;
  function f({x = 2}) {
    assertEquals(3, x);
  }
  f({x: 3});
}

function testFunctionDefault3() {
  function f({x, y} = {x: 'x', y: 'y'}) {
    assertEquals('x', x);
    assertEquals('y', y);
  }
  f();
}

function testFunctionDefault4() {
  function f({x, y} = {x: 'x', y: 'y'}) {
    assertEquals('X', x);
    assertEquals('Y', y);
  }
  f({x: 'X', y: 'Y'});
}

function testFunctionDefaultWithRescopedVariable1() {
  var x = 1;
  function f({y = x}) {
    var x = y + '!';
    assertEquals('3!', x);
  }
  f({y: 3});
}

function testFunctionDefaultWithRescopedVariable2() {
  var x = 1;
  function f({y = x}) {
    var x = 2;
    var z = y + '!';
    assertEquals('1!', z);
  }
  f({});
}

function testFunctionDefaultWithRescopedVariable3() {
  var x = 1;
  function f({ outer : {y = x}}) {
    var x = y + '!';
    assertEquals('3!', x);
  }
  f({outer: {y: 3}});
}

function testArrowFunction() {
  var f = ({key: value}) => assertEquals('v', value);
  f({key: 'v'});

  var g = (x, {key1: value1, key2: value2}, y) => {
    assertEquals('foo', x);
    assertEquals('v1', value1);
    assertEquals('v2', value2);
    assertEquals('bar', y);
  }
  g('foo', {key2: 'v2', key1: 'v1'}, 'bar');
}

function testComputedProps() {
  var {['-']: x} = {['-']: 1}
  assertEquals(1, x);

  function f({['*']: y}) {
    assertEquals(2, y);
  }
  f({['*']: 2});
}

function testComputedProps2() {
  var a = '&';
  function g({[a]: y}) {
    let a = y + '!';
    assertEquals('3!', a);
  }
  g({['&']: 3});
}

function testComputedProps3() {
  var a = '&';
  function g({x: {[a]: y}}) {
    let a = y + '!';
    assertEquals('3!', a);
  }
  g({x: {['&']: 3}});
}

function testStringKeys() {
  var {'&': x} = {'&': 1};
  assertEquals(1, x);

  function f({'&': y}) {
    assertEquals(2, y);
  }
  f({'&': 2});
}

function testNumericKeys() {
  var {3.4: x} = {3.4: 'x'};
  assertEquals('x', x);

  function f({5.6: y}) {
    assertEquals('y', y);
  }
  f({5.6: 'y'});
}

/**
 * Make sure that side effects in the param list happen in the right order.
 */
function testSideEffectsParamList() {
  var sideEffects = [];
  function a() { sideEffects.push('a'); }
  function b() { sideEffects.push('b'); }

  function f({x = a()}, y = b()) {}
  f({});
  assertArrayEquals(['a', 'b'], sideEffects);
}

function testGetpropAsAssignmentTarget() {
  var o = {};
  ({a: o.x} = {a: 1});
  assertEquals(1, o.x);

  o = {};
  ({a: o['y']} = {a: 1});
  assertEquals(1, o['y']);

  for ({length: o.z} in {'123456': 0}) {
    assertEquals(6, o.z);
  }

  for ({length: o['w']} in {'123456789': 0}) {
    assertEquals(9, o['w']);
  }
}
