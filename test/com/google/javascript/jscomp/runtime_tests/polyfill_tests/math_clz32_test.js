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

goog.module('jscomp.runtime_tests.polyfill_tests.math_clz32_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  noCheck,
} = testing;

testSuite({
  testClz32() {
    assertEquals(32, Math.clz32(0));
    let x = 1;
    for (let i = 31; i >= 0; i--) {
      assertEquals(i, Math.clz32(x));
      assertEquals(i, Math.clz32(2 * x - 1));
      x *= 2;
    }
    assertEquals(26, Math.clz32(noCheck('52')));
    assertEquals(26, Math.clz32(noCheck([52])));
    assertEquals(32, Math.clz32(noCheck([52, 53])));

    // Overflow cases
    assertEquals(32, Math.clz32(0x100000000));
    assertEquals(31, Math.clz32(0x100000001));

    // NaN -> 0
    assertEquals(32, Math.clz32(NaN));
    assertEquals(32, Math.clz32(noCheck('foo')));
    assertEquals(32, Math.clz32(Infinity));
  },
});
