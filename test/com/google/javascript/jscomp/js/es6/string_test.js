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

goog.module('$jscomp_string_test');
goog.setTestOnly();

const jsunit = goog.require('goog.testing.jsunit');
const testSuite = goog.require('goog.testing.testSuite');


// Note: jsunit's assertThrows doesn't take a constructor
/**
 * Asserts that the function fails with the given type of error.
 * @param {function(new: *)} expectedError
 * @param {function()} func
 */
function assertFails(expectedError, func) {
  try {
    func();
  } catch (err) {
    if (err instanceof expectedError) {
      return;
    }
    fail('Wrong exception type: expected ' + expectedError + ' but got ' + err);
  }
  fail('Expected to throw but didn\'t');
}


testSuite({
  testFromCodePoint_simple() {
    assertEquals('abc', String.fromCodePoint(0x61, 0x62, 0x63));
    assertEquals('\0', String.fromCodePoint(''));
    assertEquals('', String.fromCodePoint());
    assertEquals('\0', String.fromCodePoint(-0));
    assertEquals('\0', String.fromCodePoint(0));
    assertEquals('\0', String.fromCodePoint(false));
    assertEquals('\0', String.fromCodePoint(null));
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
    assertFails(RangeError, () => String.fromCodePoint('_'));
    assertFails(RangeError, () => String.fromCodePoint('Infinity'));
    assertFails(RangeError, () => String.fromCodePoint('-Infinity'));
    assertFails(RangeError, () => String.fromCodePoint(Infinity));
    assertFails(RangeError, () => String.fromCodePoint(-Infinity));
    assertFails(RangeError, () => String.fromCodePoint(0x10FFFF + 1));
    assertFails(RangeError, () => String.fromCodePoint(3.14));
    assertFails(RangeError, () => String.fromCodePoint(-1));
    assertFails(RangeError, () => String.fromCodePoint({}));
    assertFails(RangeError, () => String.fromCodePoint(NaN));
    assertFails(RangeError, () => String.fromCodePoint(/./));
    assertFails(
        Error, () => String.fromCodePoint({valueOf() { throw Error(); }}));
  },


  /** @suppress {checkTypes} */
  testFromCodePoint_casts() {
    assertEquals('a', String.fromCodePoint({valueOf: () => 0x61}));
    let tmp = 0x60;
    assertEquals('a', String.fromCodePoint({valueOf: () => ++tmp}));
    assertEquals('b', String.fromCodePoint({valueOf: () => ++tmp}));
    // TODO(sdh): v8 tests also pass in some weird long (3/2*2^32) arrays
  },


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
    assertEquals(0x61, 'abc'.codePointAt(''));
    assertEquals(0x61, 'abc'.codePointAt('_'));
    assertEquals(0x61, 'abc'.codePointAt(null));
    assertEquals(0x61, 'abc'.codePointAt());
    assertEquals(0x61, 'abc'.codePointAt(NaN));
    assertUndefined('abc'.codePointAt(Infinity));
    assertUndefined('abc'.codePointAt(-Infinity));
    assertUndefined('abc'.codePointAt(-1));

    assertEquals(0x31, String.prototype.codePointAt.call(14, 0));
    assertEquals(
        0x61, String.prototype.codePointAt.call({toString: () => 'abc' }, 0));
  },


  /** @suppress {checkTypes} */
  testCodePointAt_errors() {
    // TODO(sdh): requires strict mode, lost in transpilation (b/24413211)
    // assertFails(TypeError, String.prototype.codePointAt.call(null, 0));
    // assertFails(TypeError, String.prototype.codePointAt.call(void 0, 0));
  },


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
    assertEquals('', 'a'.repeat(void 0));
    assertEquals('', 'a'.repeat(null));
    assertEquals('000', String.prototype.repeat.call(0, 3));
    assertEquals('-1-1', String.prototype.repeat.call(-1, [2]));
    assertEquals('2.12.12.12.1', String.prototype.repeat.call(2.1, 4));
    assertEquals('', String.prototype.repeat.call([], [4]));
    assertEquals('1,2,31,2,3', String.prototype.repeat.call([1, 2, 3], 2));
    assertEquals('truetrue', String.prototype.repeat.call(true, [2]));
    assertEquals('falsefalse', String.prototype.repeat.call(false, 2));
    assertEquals('abababab', 'ab'.repeat(4.2));
    const myobj = {toString: () => 'abc', repeat: ''.repeat};
    assertEquals('abcabc', myobj.repeat(2));
  },


  /** @suppress {checkTypes} */
  testRepeat_errors() {
    assertFails(RangeError, () => 'a'.repeat(-1));
    assertFails(RangeError, () => 'a'.repeat(Infinity));

    // TODO(sdh): the native version of this OOM on Edge
    if (String.prototype.repeat == $jscomp.string.repeat) {
      assertFails(RangeError, () => 'a'.repeat(Math.pow(2, 40)));
    }

    // TODO(sdh): requires strict mode, lost in transpilation (b/24413211)
    // assertFails(TypeError, String.prototype.repeat.call(null, 0));
    // assertFails(TypeError, String.prototype.repeat.call(void 0, 1));
  },


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
    assertFalse(''.includes(null));
    assertTrue('null'.includes(null));
    assertFalse(''.includes(void 0));
    assertTrue('undefined'.includes(void 0));

    assertTrue('xyz'.includes('x', null));
    assertTrue('xyz'.includes('x', void 0));
    assertTrue('xyz'.includes('x', '_'));
    assertTrue('xyz'.includes('x', '0'));
    assertTrue('xyz'.includes('x', -5));
    assertFalse('xyz'.includes('x', '1'));

    assertTrue('12345'.includes(23));
    assertTrue('12345'.includes(345));
    assertFalse('12345'.includes(13));
    assertTrue('12345'.includes({toString: () => '23'}));
    assertFalse('12345'.includes({toString: () => '13'}));

    assertTrue(String.prototype.includes.call(42, 2));
    assertTrue(String.prototype.includes.call(42, 4));
    assertFalse(String.prototype.includes.call(42, 5));

    assertTrue(String.prototype.includes.call({toString: () => 'xyz'}, 'x'));
    assertFalse(String.prototype.includes.call({toString: () => 'xyz'}, 'w'));

    const myobj = {toString: () => 'xyz', includes: String.prototype.includes};
    assertTrue(myobj.includes('x'));
    assertFalse(myobj.includes('w'));
  },


  /** @suppress {checkTypes} */
  testIncludes_errors() {
    assertFails(TypeError, () => 'abc'.includes(/a/));

    // TODO(sdh): requires strict mode, lost in transpilation (b/24413211)
    // assertFails(TypeError, () => String.prototype.includes.call(null, ''));
    // assertFails(TypeError, () => String.prototype.includes.call(void 0, ''));
  },


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
    assertFalse(''.startsWith(null));
    assertTrue('null'.startsWith(null));
    assertFalse(''.startsWith(void 0));
    assertTrue('undefined'.startsWith(void 0));

    assertTrue('xyz'.startsWith('x', null));
    assertTrue('xyz'.startsWith('x', void 0));
    assertTrue('xyz'.startsWith('x', '_'));
    assertTrue('xyz'.startsWith('x', '0'));
    assertTrue('xyz'.startsWith('x', -5));
    assertFalse('xyz'.startsWith('x', '1'));
    assertTrue('xyz'.startsWith('y', '1'));

    assertTrue('12345'.startsWith(23, 1));
    assertTrue('12345'.startsWith(345, 2));
    assertFalse('12345'.startsWith(13));
    assertTrue('12345'.startsWith({toString: () => '23'}, 1));
    assertFalse('12345'.startsWith({toString: () => '13'}));

    assertTrue(String.prototype.startsWith.call(42, 2, 1));
    assertTrue(String.prototype.startsWith.call(42, 4));
    assertFalse(String.prototype.startsWith.call(42, 2));

    assertTrue(String.prototype.startsWith.call({toString: () => 'xyz'}, 'x'));
    assertFalse(String.prototype.startsWith.call({toString: () => 'xyz'}, 'y'));

    const myobj = {toString: () => 'xyz', startsWith: ''.startsWith};
    assertTrue(myobj.startsWith('x'));
    assertFalse(myobj.startsWith('y'));
  },


  /** @suppress {checkTypes} */
  testStartsWith_errors() {
    assertFails(TypeError, () => 'abc'.startsWith(/a/));

    // TODO(sdh): requires strict mode, lost in transpilation (b/24413211)
    // assertFails(TypeError, () => String.prototype.startsWith.call(null, ''));
    // assertFails(TypeError, () => String.prototype.startsWith.call(void 0, ''));
  },


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
    assertFalse(''.endsWith(null));
    assertTrue('null'.endsWith(null));
    assertFalse(''.endsWith(void 0));
    assertTrue('undefined'.endsWith(void 0));

    assertFalse('xyz'.endsWith('z', null)); // null -> 0
    assertTrue('xyz'.endsWith('z', void 0));
    assertTrue('xyz'.endsWith('', null));
    assertFalse('xyz'.endsWith('z', '_'));
    assertTrue('xyz'.endsWith('z', '3'));
    assertTrue('xyz'.endsWith('z', 100));
    assertFalse('xyz'.endsWith('z', -2));
    assertFalse('xyz'.endsWith('z', '2'));
    assertTrue('xyz'.endsWith('y', '2'));

    assertTrue('12345'.endsWith(23, 3));
    assertTrue('12345'.endsWith(345));
    assertFalse('12345'.endsWith(13, 3));
    assertTrue('12345'.endsWith({toString: () => '23'}, 3));
    assertFalse('12345'.endsWith({toString: () => '13'}, 3));

    assertTrue(String.prototype.endsWith.call(42, 2));
    assertTrue(String.prototype.endsWith.call(42, 4, 1));
    assertFalse(String.prototype.endsWith.call(42, 4));

    assertTrue(String.prototype.endsWith.call({toString: () => 'xyz'}, 'z'));
    assertFalse(String.prototype.endsWith.call({toString: () => 'xyz'}, 'y'));

    const myobj = {toString: () => 'xyz', endsWith: ''.endsWith};
    assertTrue(myobj.endsWith('z'));
    assertFalse(myobj.endsWith('y'));
  },


  /** @suppress {checkTypes} */
  testEndsWith_errors() {
    assertFails(TypeError, () => 'abc'.endsWith(/c/));

    // TODO(sdh): requires strict mode, lost in transpilation (b/24413211)
    // assertFails(TypeError, () => String.prototype.endsWith.call(null, ''));
    // assertFails(TypeError, () => String.prototype.endsWith.call(void 0, ''));
  },
});
