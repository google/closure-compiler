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

goog.module('jscomp.runtime_tests.polyfill_tests.string_repeat_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertFails,
  noCheck,
} = testing;

testSuite({
  testRepeat_simple() {
    assertEquals('aaa', 'a'.repeat(3));
    assertEquals('abababab', 'ab'.repeat(4));
    assertEquals('', ''.repeat(5));
    assertEquals('', 'ab'.repeat(0));

    assertEquals('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'a'.repeat(37));
    assertEquals('\u10D8\u10D8\u10D8', '\u10D8'.repeat(3));
  },


  /** @suppress {checkTypes} */
  testRepeat_casts() {
    assertEquals('', 'a'.repeat(noCheck(void 0)));
    assertEquals('', 'a'.repeat(noCheck(null)));
    assertEquals('000', String.prototype.repeat.call(noCheck(0), 3));
    assertEquals(
        '-1-1', String.prototype.repeat.call(noCheck(-1), noCheck([2])));
    assertEquals('2.12.12.12.1', String.prototype.repeat.call(noCheck(2.1), 4));
    assertEquals('', String.prototype.repeat.call(noCheck([]), noCheck([4])));
    assertEquals(
        '1,2,31,2,3', String.prototype.repeat.call(noCheck([1, 2, 3]), 2));
    assertEquals(
        'truetrue', String.prototype.repeat.call(noCheck(true), noCheck([2])));
    assertEquals('falsefalse', String.prototype.repeat.call(noCheck(false), 2));
    assertEquals('abababab', 'ab'.repeat(4.2));
    const myobj = {toString: () => 'abc', repeat: ''.repeat};
    assertEquals('abcabc', myobj.repeat(2));
  },


  /** @suppress {checkTypes} */
  testRepeat_errors() {
    assertFails(RangeError, () => 'a'.repeat(-1));
    assertFails(RangeError, () => 'a'.repeat(Infinity));

    // TODO(sdh): the native version of this OOM on Edge
    if (!userAgent.EDGE) {
      assertFails(RangeError, () => 'a'.repeat(Math.pow(2, 40)));
    }

    // TODO(sdh): requires strict mode, lost in transpilation (b/24413211)
    // assertFails(TypeError, String.prototype.repeat.call(null, 0));
    // assertFails(TypeError, String.prototype.repeat.call(void 0, 1));
  },
});
