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

goog.module('jscomp.runtime_tests.polyfill_tests.string_endswith_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertFails,
  noCheck,
} = testing;

testSuite({
  testEndsWith_simple() {
    assertTrue('a'.endsWith('a'));
    assertTrue('a'.endsWith('a', 1));
    assertTrue('a'.endsWith('a', 3));
    assertFalse('a'.endsWith('b'));
    assertTrue('ab'.endsWith('b'));
    assertFalse('ab'.endsWith('a'));
    assertFalse('a'.endsWith('a', 0));
    assertTrue('aa'.endsWith('a', 2));
    assertFalse('aa'.endsWith('aa', 1));
    assertTrue('abcde'.endsWith('bcd', 4));
    assertFalse('abcde'.endsWith('bcd', 3));

    assertTrue('abc'.endsWith(''));
    assertTrue('abc'.endsWith('', 0));
    assertTrue('abc'.endsWith('', 1));
    assertTrue('abc'.endsWith('', 2));
    assertTrue('abc'.endsWith('', 3));
  },


  testEndsWith_twoByteChars() {
    const greek = '\u039a\u0391\u03a3\u03a3\u0395';
    assertTrue(greek.endsWith('\u0395'));
    assertFalse(greek.endsWith('\u0395', 4));
    assertTrue(greek.endsWith('\u03a3', 4));
    assertTrue(greek.endsWith('\u039a', 1));
    assertFalse(greek.endsWith('\u0391'));

    assertTrue(greek.endsWith('\u039a\u0391', 2));
    assertFalse(greek.endsWith('\u0391\u03a3', 2));
    assertTrue(greek.endsWith('\u0391\u03a3', 3));
    assertTrue(greek.endsWith('\u03a3\u03a3', 4));
    assertTrue(greek.endsWith('\u03a3\u0395'));
  },


  /** @suppress {checkTypes} */
  testEndsWith_casts() {
    assertFalse(''.endsWith(noCheck(null)));
    assertTrue('null'.endsWith(noCheck(null)));
    assertFalse(''.endsWith(noCheck(void 0)));
    assertTrue('undefined'.endsWith(noCheck(void 0)));

    assertFalse('xyz'.endsWith('z', noCheck(null))); // null -> 0
    assertTrue('xyz'.endsWith('z', noCheck(void 0)));
    assertTrue('xyz'.endsWith('', noCheck(null)));
    assertFalse('xyz'.endsWith('z', noCheck('_')));
    assertTrue('xyz'.endsWith('z', noCheck('3')));
    assertTrue('xyz'.endsWith('z', 100));
    assertFalse('xyz'.endsWith('z', -2));
    assertFalse('xyz'.endsWith('z', noCheck('2')));
    assertTrue('xyz'.endsWith('y', noCheck('2')));

    assertTrue('12345'.endsWith(noCheck(23), 3));
    assertTrue('12345'.endsWith(noCheck(345)));
    assertFalse('12345'.endsWith(noCheck(13), 3));
    assertTrue('12345'.endsWith(noCheck({toString: () => '23'}), 3));
    assertFalse('12345'.endsWith(noCheck({toString: () => '13'}), 3));

    assertTrue(String.prototype.endsWith.call(noCheck(42), noCheck(2)));
    assertTrue(String.prototype.endsWith.call(noCheck(42), noCheck(4), 1));
    assertFalse(String.prototype.endsWith.call(noCheck(42), noCheck(4)));

    assertTrue(
        String.prototype.endsWith.call(noCheck({toString: () => 'xyz'}), 'z'));
    assertFalse(
        String.prototype.endsWith.call(noCheck({toString: () => 'xyz'}), 'y'));

    const myobj = {toString: () => 'xyz', endsWith: ''.endsWith};
    assertTrue(myobj.endsWith('z'));
    assertFalse(myobj.endsWith('y'));
  },


  /** @suppress {checkTypes} */
  testEndsWith_errors() {
    assertFails(TypeError, () => 'abc'.endsWith(noCheck(/c/)));

    // TODO(sdh): requires strict mode, lost in transpilation (b/24413211)
    // assertFails(TypeError, () => String.prototype.endsWith.call(null, ''));
    // assertFails(TypeError, () => String.prototype.endsWith.call(void 0, ''));
  },
});
