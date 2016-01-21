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

goog.module('$jscomp_number_test');
goog.setTestOnly();

const jsunit = goog.require('goog.testing.jsunit');
const testSuite = goog.require('goog.testing.testSuite');


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
    assertFalse(Number.isFinite('42'));
    assertFalse(Number.isFinite('5.3'));
    assertFalse(Number.isFinite('foo'));
  },


  testIsInteger() {
    assertTrue(Number.isInteger(0));
    assertTrue(Number.isInteger(-0));
    assertTrue(Number.isInteger(1));
    assertTrue(Number.isInteger(1.0));
    assertTrue(Number.isInteger(10));
    assertTrue(Number.isInteger(1e300));
    assertTrue(Number.isInteger(-1e10));

    assertFalse(Number.isInteger(Infinity));
    assertFalse(Number.isInteger(-Infinity));
    assertFalse(Number.isInteger(NaN));
    assertFalse(Number.isInteger(1.2));
    assertFalse(Number.isInteger(1e-5));
    assertFalse(Number.isInteger('12'));
    assertFalse(Number.isInteger('1.2'));
    assertFalse(Number.isInteger('foo'));
  },


  testIsNaN() {
    assertTrue(Number.isNaN(NaN));

    assertFalse(Number.isNaN('NaN'));
    assertFalse(Number.isNaN('foo'));
    assertFalse(Number.isNaN(0));
    assertFalse(Number.isNaN(1));
    assertFalse(Number.isNaN(Infinity));
    assertFalse(Number.isNaN(-Infinity));
  },


  testIsSafeInteger() {
    assertTrue(Number.isSafeInteger(0));
    assertTrue(Number.isSafeInteger(1));
    assertTrue(Number.isSafeInteger(-1));
    assertTrue(Number.isSafeInteger(100));

    const overflow = Math.pow(2, 53);
    assertTrue(Number.isSafeInteger(overflow - 1));
    assertTrue(Number.isSafeInteger(-overflow + 1));
    assertFalse(Number.isSafeInteger(overflow + 1));
    assertFalse(Number.isSafeInteger(-overflow));

    assertFalse(Number.isSafeInteger(Infinity));
    assertFalse(Number.isSafeInteger(-Infinity));
    assertFalse(Number.isSafeInteger(NaN));
    assertFalse(Number.isSafeInteger(1e300));
    assertFalse(Number.isSafeInteger(1.2));
    assertFalse(Number.isSafeInteger('42'));
  },


  testEpsilon() {
    assertEquals(1, 1 + (Number.EPSILON / 2));
    assertNotEquals(1, 1 + (Number.EPSILON * 0.5000001));
    assertEquals(Number.EPSILON, (1 + (Number.EPSILON * 0.5000001)) - 1);
  },


  testMaxSafeInteger() {
    assertEquals(Number.MAX_SAFE_INTEGER + 1, Number.MAX_SAFE_INTEGER + 2);
    assertNotEquals(Number.MAX_SAFE_INTEGER, Number.MAX_SAFE_INTEGER + 1);
    assertNotEquals(Number.MAX_SAFE_INTEGER, Number.MAX_SAFE_INTEGER - 1);
  },


  testMinSafeInteger() {
    assertEquals(Number.MIN_SAFE_INTEGER - 1, Number.MIN_SAFE_INTEGER - 2);
    assertNotEquals(Number.MIN_SAFE_INTEGER, Number.MIN_SAFE_INTEGER - 1);
    assertNotEquals(Number.MIN_SAFE_INTEGER, Number.MIN_SAFE_INTEGER + 1);
  },
});
