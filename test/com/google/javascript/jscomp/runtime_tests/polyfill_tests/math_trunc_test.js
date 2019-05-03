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

goog.module('jscomp.runtime_tests.polyfill_tests.math_trunc_test');
goog.setTestOnly();

const browser = goog.require('goog.labs.userAgent.browser');
const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const assertNegativeZero = testing.assertNegativeZero;
const assertPositiveZero = testing.assertPositiveZero;

testSuite({
  testTrunc() {
    // See https://bugs.chromium.org/p/v8/issues/detail?id=9203
    const isBadChrome = browser.isChrome()
        && browser.isVersionOrHigher('74')
        && !browser.isVersionOrHigher('75');
    assertPositiveZero(Math.trunc(0));
    assertPositiveZero(Math.trunc(0.2));
    if (!isBadChrome) {
      assertNegativeZero(Math.trunc(-0));
    }
    assertNegativeZero(Math.trunc(-0.2));
    assertEquals(1e12, Math.trunc(1e12 + 0.99));

    assertEquals(Infinity, Math.trunc(Infinity));
    assertEquals(-Infinity, Math.trunc(-Infinity));
  },
});
