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

goog.module('jscomp.runtime_tests.polyfill_tests.number_parsefloat_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const noCheck = testing.noCheck;

testSuite({
  testParseFloat() {
    assertEquals(42, Number.parseFloat('42'));
    assertEquals(-2.5e4, Number.parseFloat('-25E3'));
    assertEquals(2.6e-4, Number.parseFloat('.26e-3'));
    assertEquals(0, Number.parseFloat('0'));
    assertTrue(Object.is(-0, Number.parseFloat('-0')));

    assertEquals(Infinity, Number.parseFloat('Infinity'));
    assertEquals(-Infinity, Number.parseFloat('-Infinity'));
    assertNaN(Number.parseFloat('NaN'));

    // Overflow
    assertEquals(1e24, Number.parseFloat('1000000000000000000000000'));
    assertEquals(Infinity, Number.parseFloat('1e999'));
    assertEquals(-Infinity, Number.parseFloat('-1e999'));
    assertTrue(Object.is(0, Number.parseFloat('1e-999')));
    assertTrue(Object.is(-0, Number.parseFloat('-1e-999')));

    // Odd cases involving toString
    assertEquals(6.3, Number.parseFloat(noCheck(6.3)));
    assertEquals(
        4.2, Number.parseFloat(noCheck({toString() { return '4.2'; }})));
    assertEquals(-2.9, Number.parseFloat(noCheck(-2.9)));

    // Garbage after the last digit
    assertEquals(5, Number.parseFloat('5x'));
    assertEquals(-6.2e5, Number.parseFloat('-6.2e5yz'));
    assertEquals(-11.5, Number.parseFloat('-11.5-'));

    // Cases that don't parse at all
    assertNaN(Number.parseFloat(noCheck({valueOf() { return 7; }})));
    assertNaN(Number.parseFloat('x'));
    assertNaN(Number.parseFloat(''));
  },
});
