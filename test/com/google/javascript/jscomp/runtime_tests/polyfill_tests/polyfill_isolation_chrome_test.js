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
 * @fileoverview Test that --isolate_polyfills defaults to the native
 * symbols if present. (This is tested for by checking for a native Symbol
 * implementation.)
 */
goog.module('jscomp.runtime_tests.polyfill_tests.polyfill_isolation_chrome_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testPolyfillReceiversWithSideEffectsOnlyEvaledOnce() {
    assertEquals(0, window['getCounterValue']());
    assertEquals(true, window['invokeStrStartsWith']('123', '1'));
    assertEquals(1, window['getCounterValue']());
    assertEquals(false, window['invokeStrStartsWith']('123', '0'));
    assertEquals(2, window['getCounterValue']());
  },

  /**
   * Tests that the compiled binary uses the native implementation of various
   * ES6 methods, not the compiler polyfills.
   */
  testSymbolPolyfill_usesNativeImplementation() {
    assertEquals(Symbol, window['jscomp_Symbol']);
  },
  testPromisePolyfill_usesNativeImplementation() {
    assertEquals(Promise, window['jscomp_Promise']);
  },
  testStringStartsWithPolyfill_usesNativeImplementation() {
    assertEquals(
        String.prototype.startsWith, window['jscomp_String_startsWith']);
  },
  testStaticEs6Polyfills_useNativeImplementation() {
    assertEquals(Math.sign, window['jscomp_Math_sign']);
    assertEquals(Object.assign, window['jscomp_Object_assign']);
    assertEquals(Object.setPrototypeOf, window['jscomp_Object_setPrototypeOf']);
    assertEquals(Reflect.construct, window['jscomp_Reflect_construct']);
  },
  /** Methods from a newer spec version than ES6 are still polyfilled */
  testPromiseAllSettledPolyfill_ignoresNativeImpl() {
    // From ES2020
    assertNotEquals(Promise.allSettled, window['jscomp_Promise_settled']);
  },
  testArrayIncludesPolyfill_ignoresNativeImpl() {
    // From ES2016
    assertNotEquals(Array.prototype.includes, window['jscomp_Array_includes']);
  },
});
