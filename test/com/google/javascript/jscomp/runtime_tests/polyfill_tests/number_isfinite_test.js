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

goog.module('jscomp.runtime_tests.polyfill_tests.number_isfinite_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  noCheck,
} = testing;

testSuite({
  testIsFinite() {
    assertTrue(Number.isFinite(0));
    assertTrue(Number.isFinite(-0));
    assertTrue(Number.isFinite(1));
    assertTrue(Number.isFinite(-1));
    assertTrue(Number.isFinite(1.5));
    assertTrue(Number.isFinite(-1.5));
    assertTrue(Number.isFinite(1e-300));
    assertTrue(Number.isFinite(1e300));
    assertTrue(Number.isFinite(-1e300));

    assertFalse(Number.isFinite(Infinity));
    assertFalse(Number.isFinite(-Infinity));
    assertFalse(Number.isFinite(NaN));
    assertFalse(Number.isFinite(noCheck('42')));
    assertFalse(Number.isFinite(noCheck('5.3')));
    assertFalse(Number.isFinite(noCheck('foo')));
  },
});
