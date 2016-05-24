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

goog.module('jscomp.runtime_tests.polyfill_tests.math_cbrt_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertNegativeZero,
  assertPositiveZero,
} = testing;

testSuite({
  testCbrt() {
    assertPositiveZero(Math.cbrt(0));
    assertNegativeZero(Math.cbrt(-0));

    assertEquals(Infinity, Math.cbrt(Infinity));
    assertEquals(-Infinity, Math.cbrt(-Infinity));

    assertRoughlyEquals(2, Math.cbrt(8), 1e-10);
    assertRoughlyEquals(-2, Math.cbrt(-8), 1e-10);
    assertRoughlyEquals(3, Math.cbrt(27), 1e-10);
    assertRoughlyEquals(-3, Math.cbrt(-27), 1e-10);

    assertRoughlyEquals(6, Math.cbrt(216), 1e-10);
    assertRoughlyEquals(-5, Math.cbrt(-125), 1e-10);

    assertRoughlyEquals(1.44224957030741, Math.cbrt(3), 1e-14);
  }
});
