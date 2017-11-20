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

goog.module('jscomp.runtime_tests.polyfill_tests.number_parseint_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const noCheck = testing.noCheck;

testSuite({
  testParseInt() {
    assertEquals(42, Number.parseInt('42', 10));
    assertEquals(-2, Number.parseInt('-2', 10));
    assertEquals(0, Number.parseInt('0', 10));
    assertTrue(Object.is(-0, Number.parseInt('-0', 10)));

    // Truncation
    assertEquals(4, Number.parseInt('4.7', 10));
    assertEquals(-2, Number.parseInt('-2.9', 10));

    // Radix
    assertEquals(27, Number.parseInt('1b', 16));
    assertEquals(-13, Number.parseInt('-D', 20));
    assertEquals(32, Number.parseInt('40', 8));

    // Overflow
    assertEquals(1e24, Number.parseInt('1000000000000000000000000', 10));

    // Odd cases involving toString
    assertEquals(6, Number.parseInt(noCheck(6), 10));
    assertEquals(4, Number.parseInt(noCheck({toString() { return '4'; }}), 10));
    assertEquals(-2, Number.parseInt(noCheck(-2.9), 10));

    // Garbage after the last digit
    assertEquals(5, Number.parseInt('5x', 10));
    assertEquals(-6, Number.parseInt('-6.2e5', 10));
    assertEquals(-9, Number.parseInt('-11.5', 8));

    // Cases that don't parse at all
    assertNaN(Number.parseInt(noCheck({valueOf() { return 7; }}), 10));
    assertNaN(Number.parseInt('NaN', 10));
    assertNaN(Number.parseInt('Infinity', 10));
    assertNaN(Number.parseInt('x', 10));
    assertNaN(Number.parseInt('', 10));
  },
});
