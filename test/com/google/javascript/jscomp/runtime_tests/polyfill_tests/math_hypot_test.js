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

goog.module('jscomp.runtime_tests.polyfill_tests.math_hypot_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testHypot() {
    assertRoughlyEquals(5, Math.hypot(3, 4), 1e-10);
    assertRoughlyEquals(5, Math.hypot(-3, 4), 1e-10);
    assertRoughlyEquals(13, Math.hypot(5, 12), 1e-10);
    assertRoughlyEquals(13, Math.hypot(5, -12), 1e-10);
    assertRoughlyEquals(Math.sqrt(2), Math.hypot(-1, -1), 1e-10);

    assertRoughlyEquals(13, Math.hypot(3, 4, 12), 1e-10);

    // Test overflow and underflow
    assertRoughlyEquals(5e300, Math.hypot(3e300, 4e300), 1e290);
    assertRoughlyEquals(5e300, Math.hypot(-3e300, -4e300), 1e290);

    assertRoughlyEquals(5e-300, Math.hypot(3e-300, 4e-300), 1e290);
    assertRoughlyEquals(5e-300, Math.hypot(-3e-300, -4e-300), 1e290);
  },
});
