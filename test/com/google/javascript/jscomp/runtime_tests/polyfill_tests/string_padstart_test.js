/*
 * Copyright 2017 The Closure Compiler Authors.
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

goog.module('jscomp.runtime_tests.polyfill_tests.string_padstart_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');
const userAgent = goog.require('goog.userAgent');

const assertFails = testing.assertFails;
const noCheck = testing.noCheck;

testSuite({
  shouldRunTests() {
    // NOTE: padStart is not present in all browsers we currently test under.
    return !goog.global.NOT_TRANSPILED ||
        userAgent.CHROME && userAgent.isVersionOrHigher(57) ||
        userAgent.EDGE && userAgent.isVersionOrHigher(15) ||
        userAgent.FIREFOX && userAgent.isVersionOrHigher(51);
  },

  testPadStart_simple() {
    assertEquals('   42', '42'.padStart(5));
    assertEquals('42', '42'.padStart(0));
    assertEquals('42', '42'.padStart(-1));
    assertEquals('42', '42'.padStart(-Infinity));
    assertEquals('42', '42'.padStart(NaN));
    assertEquals('042', '42'.padStart(3, '0'));
    assertEquals('42', '42'.padStart(8, ''));
    assertEquals('xxxxx', ''.padStart(5, 'x'));
    assertEquals('123abc', 'abc'.padStart(6, '123456'));
    assertEquals('\u10D7\u10D7\u10D8', '\u10D8'.padStart(3, '\u10D7'));
  },


  /** @suppress {checkTypes} */
  testPadStart_casts() {
    var pad =
        (s, l, p = undefined) =>
            String.prototype.padStart.call(noCheck(s), l, noCheck(p));

    assertEquals('00000042', pad('42', 8, 0));
    assertEquals('      42', pad('42', 8, undefined));
    assertEquals('nullnu42', pad('42', 8, null));
    assertEquals('xxxxxx42', pad('42', 8, {toString: () => 'x'}));

    assertEquals('000042', pad(42, 6, '0'));
    assertEquals('     x', pad({toString: () => 'x'}, 6));
    assertEquals('-1,-2-3,-4', pad([3, -4], 10, [-1, -2]));
    assertEquals('truetfalse', pad(false, 10, true));
  },


  /** @suppress {checkTypes} */
  testPadStart_errors() {
    if (userAgent.EDGE) {
      assertFails(Error, () => 'a'.padEnd(Infinity)); // Out of Memory
    } else {
      assertFails(RangeError, () => 'a'.padStart(Infinity));
    }
  },
});
