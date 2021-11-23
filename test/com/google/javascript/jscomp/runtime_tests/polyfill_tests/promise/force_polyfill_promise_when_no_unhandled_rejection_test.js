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
/**
 * @fileoverview Support code for testing
 *     $jscomp.FORCE_POLYFILL_PROMISE_WHEN_NO_UNHANDLED_REJECTION.
 * @suppress {undefinedVars,missingProperties} Suppress warnings on $jscomp.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.force_polyfill_promise_when_no_unhandled_rejection_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');


/** @return {boolean} Whether Promise is natively supported. */
function hasNativePromise() {
  return Promise.toString().includes('[native code]');
}

testSuite({
  shouldRunTests() {
    return $jscomp.FORCE_POLYFILL_PROMISE_WHEN_NO_UNHANDLED_REJECTION;
  },
  testForcePolyfillPromise() {
    // Always override native Promise with the polyfill if unhandledrejection is
    // not supported.
    assertFalse(hasNativePromise());
  },
});
