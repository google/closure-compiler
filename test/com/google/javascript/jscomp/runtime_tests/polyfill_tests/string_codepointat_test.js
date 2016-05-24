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

goog.module('jscomp.runtime_tests.polyfill_tests.string_codepointat_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  noCheck,
} = testing;

testSuite({
  testCodePointAt_containsSurrogatePair() {
    assertEquals(0x61, 'abc\uD834\uDF06def'.codePointAt(0));
    assertEquals(0x62, 'abc\uD834\uDF06def'.codePointAt(1));
    assertEquals(0x63, 'abc\uD834\uDF06def'.codePointAt(2));
    assertEquals(0x1D306, 'abc\uD834\uDF06def'.codePointAt(3));
    assertEquals(0xDF06, 'abc\uD834\uDF06def'.codePointAt(4));
    assertEquals(0x64, 'abc\uD834\uDF06def'.codePointAt(5));
    assertEquals(0x65, 'abc\uD834\uDF06def'.codePointAt(6));
    assertEquals(0x66, 'abc\uD834\uDF06def'.codePointAt(7));
    assertUndefined('abc\uD834\uDF06def'.codePointAt(8));
    assertUndefined('abc\uD834\uDF06def'.codePointAt(42));
  },


  testCodePointAt_startsWithSurrogatePair() {
    assertEquals(0x1D306, '\uD834\uDF06def'.codePointAt(0));
    assertEquals(0xDF06, '\uD834\uDF06def'.codePointAt(1));
    assertEquals(0x64, '\uD834\uDF06def'.codePointAt(2));
    assertEquals(0x65, '\uD834\uDF06def'.codePointAt(3));
    assertEquals(0x66, '\uD834\uDF06def'.codePointAt(4));
    assertUndefined('\uD834\uDF06def'.codePointAt(5));
  },


  testCodePointAt_startsWithHighSurrogate() {
    assertEquals(0xD834, '\uD834def'.codePointAt(0));
    assertEquals(0x64, '\uD834def'.codePointAt(1));
    assertEquals(0x65, '\uD834def'.codePointAt(2));
    assertEquals(0x66, '\uD834def'.codePointAt(3));
    assertUndefined('\uD834def'.codePointAt(4));
  },


  testCodePointAt_startsWithLowSurrogate() {
    assertEquals(0xDF06, '\uDF06def'.codePointAt(0));
    assertEquals(0x64, '\uDF06def'.codePointAt(1));
    assertEquals(0x65, '\uDF06def'.codePointAt(2));
    assertEquals(0x66, '\uDF06def'.codePointAt(3));
    assertUndefined('\uDF06def'.codePointAt(4));
  },


  /** @suppress {checkTypes} */
  testCodePointAt_casts() {
    assertEquals(0x61, 'abc'.codePointAt(noCheck('')));
    assertEquals(0x61, 'abc'.codePointAt(noCheck('_')));
    assertEquals(0x61, 'abc'.codePointAt(noCheck(null)));
    // TODO(sdh): fails type check.
    // assertEquals(0x61, 'abc'.codePointAt());
    assertEquals(0x61, 'abc'.codePointAt(NaN));
    assertUndefined('abc'.codePointAt(Infinity));
    assertUndefined('abc'.codePointAt(-Infinity));
    assertUndefined('abc'.codePointAt(-1));

    assertEquals(0x31, String.prototype.codePointAt.call(noCheck(14), 0));
    assertEquals(
        0x61,
        String.prototype.codePointAt.call(
            noCheck({toString: () => 'abc' }), 0));
  },


  /** @suppress {checkTypes} */
  testCodePointAt_errors() {
    // TODO(sdh): requires strict mode, lost in transpilation (b/24413211)
    // assertFails(TypeError, String.prototype.codePointAt.call(null, 0));
    // assertFails(TypeError, String.prototype.codePointAt.call(void 0, 0));
  },
});
