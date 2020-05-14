/*
 * Copyright 2020 The Closure Compiler Authors.
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
 * @fileoverview Tests for polyfilling Symbol.iterator onto TypedArrays.
 *
 * We don't polyfill any TypedArrays so this test will fail on IE8/9.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.typed_array_test');

const testSuite = goog.require('goog.testing.testSuite');

testSuite({

  testInt8Array() {
    const iter = new Int8Array([2, 4, 6])[Symbol.iterator]();
    assertObjectEquals({value: 2, done: false}, iter.next());
    assertObjectEquals({value: 4, done: false}, iter.next());
    assertObjectEquals({value: 6, done: false}, iter.next());
    assertTrue(iter.next().done);
  },

  testUint8Array() {
    const iter = new Uint8Array([2, 4, 6])[Symbol.iterator]();
    assertObjectEquals({value: 2, done: false}, iter.next());
    assertObjectEquals({value: 4, done: false}, iter.next());
    assertObjectEquals({value: 6, done: false}, iter.next());
    assertTrue(iter.next().done);
  },

  testUint8ClampedArray() {
    const iter = new Uint8ClampedArray([2, 4, 6])[Symbol.iterator]();
    assertObjectEquals({value: 2, done: false}, iter.next());
    assertObjectEquals({value: 4, done: false}, iter.next());
    assertObjectEquals({value: 6, done: false}, iter.next());
    assertTrue(iter.next().done);
  },


  testInt16Array() {
    const iter = new Int16Array([2, 4, 6])[Symbol.iterator]();
    assertObjectEquals({value: 2, done: false}, iter.next());
    assertObjectEquals({value: 4, done: false}, iter.next());
    assertObjectEquals({value: 6, done: false}, iter.next());
    assertTrue(iter.next().done);
  },


  testUint16Array() {
    const iter = new Uint16Array([2, 4, 6])[Symbol.iterator]();
    assertObjectEquals({value: 2, done: false}, iter.next());
    assertObjectEquals({value: 4, done: false}, iter.next());
    assertObjectEquals({value: 6, done: false}, iter.next());
    assertTrue(iter.next().done);
  },

  testInt32Array() {
    const iter = new Int32Array([2, 4, 6])[Symbol.iterator]();
    assertObjectEquals({value: 2, done: false}, iter.next());
    assertObjectEquals({value: 4, done: false}, iter.next());
    assertObjectEquals({value: 6, done: false}, iter.next());
    assertTrue(iter.next().done);
  },

  testUint32Array() {
    const iter = new Uint32Array([2, 4, 6])[Symbol.iterator]();
    assertObjectEquals({value: 2, done: false}, iter.next());
    assertObjectEquals({value: 4, done: false}, iter.next());
    assertObjectEquals({value: 6, done: false}, iter.next());
    assertTrue(iter.next().done);
  },

  testFloat32Array() {
    const iter = new Float32Array([2, 4, 6])[Symbol.iterator]();
    assertObjectEquals({value: 2, done: false}, iter.next());
    assertObjectEquals({value: 4, done: false}, iter.next());
    assertObjectEquals({value: 6, done: false}, iter.next());
    assertTrue(iter.next().done);
  },
  testFloat64Array() {
    const iter = new Float64Array([2, 4, 6])[Symbol.iterator]();
    assertObjectEquals({value: 2, done: false}, iter.next());
    assertObjectEquals({value: 4, done: false}, iter.next());
    assertObjectEquals({value: 6, done: false}, iter.next());
    assertTrue(iter.next().done);
  },

});
