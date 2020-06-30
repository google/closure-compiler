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

goog.module('jscomp.runtime_tests.polyfill_tests.typedarray_fill_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testFill_uint8() {
    assertObjectEquals(
        new Uint8Array([9, 9, 9]), new Uint8Array([1, 2, 3]).fill(9));
  },

  testFill_uint8Clamped() {
    assertObjectEquals(
        new Uint8ClampedArray([9, 9, 9]),
        new Uint8ClampedArray([1, 2, 3]).fill(9));
  },

  testFill_int8() {
    assertObjectEquals(
        new Int8Array([9, 9, 9]), new Int8Array([1, 2, 3]).fill(9));
  },

  testFill_uint16() {
    assertObjectEquals(
        new Uint16Array([9, 9, 9]), new Uint16Array([1, 2, 3]).fill(9));
  },

  testFill_int16() {
    assertObjectEquals(
        new Int16Array([9, 9, 9]), new Int16Array([1, 2, 3]).fill(9));
  },

  testFill_uint32() {
    assertObjectEquals(
        new Uint32Array([9, 9, 9]), new Uint32Array([1, 2, 3]).fill(9));
  },

  testFill_int32() {
    assertObjectEquals(
        new Int32Array([9, 9, 9]), new Int32Array([1, 2, 3]).fill(9));
  },

  testFill_float32() {
    assertObjectEquals(
        new Float32Array([9, 9, 9]), new Float32Array([1, 2, 3]).fill(9));
  },

  testFill_float64() {
    assertObjectEquals(
        new Float64Array([9, 9, 9]), new Float64Array([1, 2, 3]).fill(9));
  },
});
