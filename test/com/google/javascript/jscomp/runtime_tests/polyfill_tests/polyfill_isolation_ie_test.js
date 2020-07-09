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
goog.module('jscomp.runtime_tests.polyfill_tests.polyfill_isolation_ie_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  /**
   * Test that no native methods are added to the global scope, but the compiled
   * binary is still able to invoke these methods via methods added to window.
   */
  testStrStartsWith_notDefinedGlobally: function() {
    assertUndefined(window.String.prototype.startsWith);
    assertTrue(window['jscomp_String_startsWith'].call('abc', 'a'));
  },
  testMap_notDefinedGlobally: function() {
    assertUndefined(window.Map);
    assertFalse(new window['jscomp_Map']().has(1));
  },
  testArrayOf_notDefinedGlobally: function() {
    assertUndefined(window.Array.of);
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
   */
  testSymbolPolyfill_doesNotUseExistingPolyfill: function() {
    assertNotEquals(window.Symbol, window['jscomp_Symbol']);
  },
  testPromisePolyfill_doesNotUseExistingPolyfill: function() {
    assertNotEquals(window['Promise'], window['jscomp_Promise']);
  },
  testArrayIncludesPolyfill_doesNotUseExistingPolyfill: function() {
    assertNotEquals(Array.prototype.includes, window['jscomp_Array_includes']);

    // The JSCompiler polyfill works as expected, while the other polyfill does
    // not.
    assertTrue(window['jscomp_Array_includes'].call([1], 1));
    assertFalse(Array.prototype.includes.call([1], 1));
  },
  testObjectStatics_doNotUseExistingPolyfill: function() {
    assertNotEquals(Object.assign, window['jscomp_Object_assign']);
    assertNotEquals(
        Object.setPrototypeOf, window['jscomp_Object_setPrototypeOf']);
  },
  testReflectConstruct_doesNotUseExistingPolyfill: function() {
    assertNotEquals(Reflect.construct, window['jscomp_Reflect_construct']);
  },
  testMathSignPolyfill_doesNotUseExistingPolyfill: function() {
    assertNotEquals(Math.sign, window['jscomp_Math_sign']);

    // The JSCompiler polyfill works as expected, while the other polyfill does
    // not.
    assertEquals(1, window['jscomp_Math_sign'](1));
    assertEquals(-1, Math.sign(1));
  },

  /**
   * Polyfill isolation does not support adding frozen objects to maps or sets,
   * so test that attempting to add such an object throws.
   */
  testWeakMap_sealedKeys() {
    if (!Object.seal) return;

    const key1 = Object.seal({});
    const key2 = Object.seal({});
    const key3 = Object.freeze({});

    const map = new window['jscomp_WeakMap']();
    assertThrows(() => map.set(key1, 0));
    assertThrows(() => map.set(key2, 0));
    assertThrows(() => map.set(key3, 0));
  },

  testMap_sealedKeys() {
    if (!Object.seal) return;

    const key1 = Object.seal({});
    const key2 = Object.seal({});
    const key3 = Object.freeze({});

    const map = new window['jscomp_Map']();
    assertThrows(() => map.set(key1, 0));
    assertThrows(() => map.set(key2, 0));
    assertThrows(() => map.set(key3, 0));
  },

  testSet_add_sealedObjects() {
    if (!Object.seal) return;

    const key1 = Object.preventExtensions({});
    const key2 = Object.seal({});
    const key3 = Object.freeze({});

    const set = new window['jscomp_Set']();
    assertThrows(() => set.add(key1, 0));
    assertThrows(() => set.add(key2, 0));
    assertThrows(() => set.add(key3, 0));
  },

  testWeakSet_sealedKeys() {
    if (!Object.seal) return;

    const key1 = Object.preventExtensions({});
    const key2 = Object.seal({});
    const key3 = Object.freeze({});

    const set = new window['jscomp_WeakSet']();
    assertThrows(() => set.add(key1, 0));
    assertThrows(() => set.add(key2, 0));
    assertThrows(() => set.add(key3, 0));
  },
});
