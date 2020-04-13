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
 * @fileoverview Test that --isolate_polyfills prevents injection of polyfills
 * directly into the global scope.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.polyfill_isolation_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  /**
   * Test that no native methods are added to the global scope, but the compiled
   * binary is still able to invoke these methods via methods added to window.
   *
   * TODO(lharker): all these calls should be `assertUndefined`.
   */
  testStrStartsWith_notDefinedGlobally: function() {
    assertNotUndefined(window.String.prototype.startsWith);
    assertTrue(window['jscomp_String_startsWith'].call('abc', 'a'));
  },
  testMap_notDefinedGlobally: function() {
    assertNotUndefined(window.Map);
    assertFalse(new window['jscomp_Map']().has(1));
  },
  testArrayOf_notDefinedGlobally: function() {
    assertNotUndefined(window.Array.of);
    assertArrayEquals([1, 2, 3], window['jscomp_Array_of'](1, 2, 3));
  },

  testPolyfillReceiversWithSideEffectsOnlyEvaledOnce: function() {
    assertEquals(0, window['getCounterValue']());
    assertEquals(true, window['invokeStrStartsWith']('123', '1'));
    assertEquals(1, window['getCounterValue']());
    assertEquals(false, window['invokeStrStartsWith']('123', '0'));
    assertEquals(2, window['getCounterValue']());
  },

  /**
   * Tests the compiled binary against some incorrect 'polyfills' that the
   * corresponding HTML file defined on window.
   *
   * TODO(lharker): enable --isolate_polyfills and fix these tests to assert
   * the correct behavior.
   */
  testSymbolPolyfill_doesNotUseExistingPolyfill: function() {
    // TODO(b/132718547): window.Symbol should be undefined.
    assertEquals(window.Symbol, window['jscomp_Symbol']);
  },
  testPromisePolyfill_doesNotUseExistingPolyfill: function() {
    // TODO(lharker): these should not be equal.
    assertEquals(window['Promise'], window['jscomp_Promise']);
  },
  testArrayIncludesPolyfill_doesNotUseExistingPolyfill: function() {
    // TODO(lharker): these should not be equal.
    assertEquals(Array.prototype.includes, window['jscomp_Array_includes']);

    // The below call should be assertTrue, but it's using the buggy polyfill
    // instead of the correct polyfill.
    assertFalse(window['jscomp_Array_includes'].call([1], 1));
    assertFalse(Array.prototype.includes.call([1], 1));
  },
  testMathSignPolyfill_doesNotUseExistingPolyfill: function() {
    // TODO(lharker): these should not be equal.
    assertEquals(Math.sign, window['jscomp_Math_sign']);

    // The below call is using the bad Math.sign polyfill instead of the correct
    // compiler polyfill. It should return "1" not "-1".
    assertEquals(-1, window['jscomp_Math_sign'](1));
    assertEquals(-1, Math.sign(1));
  }
});
