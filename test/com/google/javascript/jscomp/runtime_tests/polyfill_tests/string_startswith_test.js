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

goog.module('jscomp.runtime_tests.polyfill_tests.string_startswith_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertFails,
  noCheck,
} = testing;

testSuite({
  testStartsWith_simple() {
    assertTrue('a'.startsWith('a'));
    assertTrue('a'.startsWith('a', 0));
    assertFalse('a'.startsWith('b'));
    assertFalse('ab'.startsWith('b'));
    assertFalse('a'.startsWith('a', 1));
    assertTrue('aa'.startsWith('a', 1));
    assertFalse('aa'.startsWith('aa', 1));
    assertTrue('abcde'.startsWith('bcd', 1));
    assertFalse('abcde'.startsWith('bcd', 2));
  },


  testStartsWith_twoByteChars() {
    const greek = '\u039a\u0391\u03a3\u03a3\u0395';
    assertTrue(greek.startsWith('\u039a'));
    assertFalse(greek.startsWith('\u039a', 1));
    assertTrue(greek.startsWith('\u0391', 1));
    assertFalse(greek.startsWith('\u0391'));

    assertTrue(greek.startsWith('\u039a\u0391'));
    assertFalse(greek.startsWith('\u0391\u03a3'));
    assertTrue(greek.startsWith('\u0391\u03a3', 1));
    assertTrue(greek.startsWith('\u03a3\u03a3', 2));
    assertTrue(greek.startsWith('\u03a3\u0395', 3));
  },


  /** @suppress {checkTypes} */
  testStartsWith_casts() {
    assertFalse(''.startsWith(noCheck(null)));
    assertTrue('null'.startsWith(noCheck(null)));
    assertFalse(''.startsWith(noCheck(void 0)));
    assertTrue('undefined'.startsWith(noCheck(void 0)));

    assertTrue('xyz'.startsWith('x', noCheck(null)));
    assertTrue('xyz'.startsWith('x', noCheck(void 0)));
    assertTrue('xyz'.startsWith('x', noCheck('_')));
    assertTrue('xyz'.startsWith('x', noCheck('0')));
    assertTrue('xyz'.startsWith('x', -5));
    assertFalse('xyz'.startsWith('x', noCheck('1')));
    assertTrue('xyz'.startsWith('y', noCheck('1')));

    assertTrue('12345'.startsWith(noCheck(23), 1));
    assertTrue('12345'.startsWith(noCheck(345), 2));
    assertFalse('12345'.startsWith(noCheck(13)));
    assertTrue('12345'.startsWith(noCheck({toString: () => '23'}), 1));
    assertFalse('12345'.startsWith(noCheck({toString: () => '13'})));

    assertTrue(String.prototype.startsWith.call(noCheck(42), noCheck(2), 1));
    assertTrue(String.prototype.startsWith.call(noCheck(42), noCheck(4)));
    assertFalse(String.prototype.startsWith.call(noCheck(42), noCheck(2)));

    assertTrue(
        String.prototype.startsWith.call(
            noCheck({toString: () => 'xyz'}), 'x'));
    assertFalse(
        String.prototype.startsWith.call(
            noCheck({toString: () => 'xyz'}), 'y'));

    const myobj = {toString: () => 'xyz', startsWith: ''.startsWith};
    assertTrue(myobj.startsWith('x'));
    assertFalse(myobj.startsWith('y'));
  },


  /** @suppress {checkTypes} */
  testStartsWith_errors() {
    assertFails(TypeError, () => 'abc'.startsWith(noCheck(/a/)));

    // TODO(sdh): requires strict mode, lost in transpilation (b/24413211)
    // assertFails(TypeError, () => String.prototype.startsWith.call(null, ''));
    // assertFails(TypeError, () => String.prototype.startsWith.call(void 0, ''));
  },
});
