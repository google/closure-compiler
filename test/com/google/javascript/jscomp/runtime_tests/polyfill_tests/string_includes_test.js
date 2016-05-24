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

goog.module('jscomp.runtime_tests.polyfill_tests.string_includes_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertFails,
  noCheck,
} = testing;

testSuite({
  testIncludes_simple() {
    assertTrue('a'.includes('a'));
    assertTrue('a'.includes('a', 0));
    assertFalse('a'.includes('b'));
    assertFalse('a'.includes('a', 1));
    assertTrue('aa'.includes('a', 1));
    assertFalse('aa'.includes('aa', 1));
    assertTrue('abcde'.includes('bcd', 0));
    assertTrue('abcde'.includes('bcd', 1));
    assertFalse('abcde'.includes('bcd', 2));

    const re = 'asdf[a-z]+(asdf)?';
    assertTrue(re.includes('[a-z]+'));
    assertTrue(re.includes('(asdf)?'));
    assertFalse(re.includes('g'));
    assertFalse(re.includes('a', 12));
  },


  testIncludes_twoByteChars() {
    const greek = '\u039a\u0391\u03a3\u03a3\u0395';
    assertTrue(greek.includes('\u039a'));
    assertFalse(greek.includes('\u039a', 1));
    assertTrue(greek.includes('\u0391'));
    assertTrue(greek.includes('\u03a3'));
    assertTrue(greek.includes('\u03a3', 3));
    assertFalse(greek.includes('\u03a3', 4));
    assertTrue(greek.includes('\u0395'));
    assertFalse(greek.includes('\u0392'));

    assertTrue(greek.includes('\u039a\u0391'));
    assertTrue(greek.includes('\u0391\u03a3'));
    assertTrue(greek.includes('\u03a3\u03a3'));
    assertTrue(greek.includes('\u03a3\u0395'));

    assertFalse(greek.includes('\u0391\u03a3\u0395'));
  },


  /** @suppress {checkTypes} */
  testIncludes_casts() {
    assertFalse(''.includes(noCheck(null)));
    assertTrue('null'.includes(noCheck(null)));
    assertFalse(''.includes(noCheck(void 0)));
    assertTrue('undefined'.includes(noCheck(void 0)));

    assertTrue('xyz'.includes('x', noCheck(null)));
    assertTrue('xyz'.includes('x', noCheck(void 0)));
    assertTrue('xyz'.includes('x', noCheck('_')));
    assertTrue('xyz'.includes('x', noCheck('0')));
    assertTrue('xyz'.includes('x', -5));
    assertFalse('xyz'.includes('x', noCheck('1')));

    assertTrue('12345'.includes(noCheck(23)));
    assertTrue('12345'.includes(noCheck(345)));
    assertFalse('12345'.includes(noCheck(13)));
    assertTrue('12345'.includes(noCheck({toString: () => '23'})));
    assertFalse('12345'.includes(noCheck({toString: () => '13'})));

    assertTrue(String.prototype.includes.call(noCheck(42), noCheck(2)));
    assertTrue(String.prototype.includes.call(noCheck(42), noCheck(4)));
    assertFalse(String.prototype.includes.call(noCheck(42), noCheck(5)));

    assertTrue(
        String.prototype.includes.call(noCheck({toString: () => 'xyz'}), 'x'));
    assertFalse(
        String.prototype.includes.call(noCheck({toString: () => 'xyz'}), 'w'));

    const myobj = {toString: () => 'xyz', includes: String.prototype.includes};
    assertTrue(myobj.includes('x'));
    assertFalse(myobj.includes('w'));
  },


  /** @suppress {checkTypes} */
  testIncludes_errors() {
    assertFails(TypeError, () => 'abc'.includes(noCheck(/a/)));

    // TODO(sdh): requires strict mode, lost in transpilation (b/24413211)
    // assertFails(TypeError, () => String.prototype.includes.call(null, ''));
    // assertFails(TypeError, () => String.prototype.includes.call(void 0, ''));
  },
});
