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

goog.module('jscomp.runtime_tests.polyfill_tests.string_fromcodepoint_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertFails,
  noCheck,
} = testing;

testSuite({
  testFromCodePoint_simple() {
    assertEquals('abc', String.fromCodePoint(0x61, 0x62, 0x63));
    assertEquals('\0', String.fromCodePoint(noCheck('')));
    //assertEquals('', String.fromCodePoint());
    assertEquals('\0', String.fromCodePoint(-0));
    assertEquals('\0', String.fromCodePoint(0));
    assertEquals('\0', String.fromCodePoint(noCheck(false)));
    assertEquals('\0', String.fromCodePoint(noCheck(null)));
  },


  testFromCodePoint_surrogatePairs() {
    assertEquals('\uD834\uDF06', String.fromCodePoint(0x1D306));
    assertEquals('\uD834\uDF06a', String.fromCodePoint(0x1D306, 0x61));
    assertEquals('ab\uD834\uDF06', String.fromCodePoint(0x61, 0x62, 0x1D306));
    assertEquals(
        '\uD834\uDF06a\uD834\uDF07',
        String.fromCodePoint(0x1D306, 0x61, 0x1D307));
  },


  /** @suppress {checkTypes} */
  testFromCodePoint_errors() {
    assertFails(RangeError, () => String.fromCodePoint(noCheck('_')));
    assertFails(RangeError, () => String.fromCodePoint(noCheck('Infinity')));
    assertFails(RangeError, () => String.fromCodePoint(noCheck('-Infinity')));
    assertFails(RangeError, () => String.fromCodePoint(Infinity));
    assertFails(RangeError, () => String.fromCodePoint(-Infinity));
    assertFails(RangeError, () => String.fromCodePoint(0x10FFFF + 1));
    assertFails(RangeError, () => String.fromCodePoint(3.14));
    assertFails(RangeError, () => String.fromCodePoint(-1));
    assertFails(RangeError, () => String.fromCodePoint(noCheck({})));
    assertFails(RangeError, () => String.fromCodePoint(NaN));
    assertFails(RangeError, () => String.fromCodePoint(noCheck(/./)));
    assertFails(
        Error,
        () => String.fromCodePoint(noCheck({valueOf() { throw Error(); }})));
  },


  /** @suppress {checkTypes} */
  testFromCodePoint_casts() {
    assertEquals('a', String.fromCodePoint(noCheck({valueOf: () => 0x61})));
    let tmp = 0x60;
    assertEquals('a', String.fromCodePoint(noCheck({valueOf: () => ++tmp})));
    assertEquals('b', String.fromCodePoint(noCheck({valueOf: () => ++tmp})));
    // TODO(sdh): v8 tests also pass in some weird long (3/2*2^32) arrays
  },
});
