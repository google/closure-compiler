/*
 * Copyright 2021 The Closure Compiler Authors.
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
goog.module('jscomp.runtime_tests.polyfill_tests.string_replaceall_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const assertFails = testing.assertFails;


/**
 * Tests replaceAll by replacing all instances of 'Xyz' or /Xyz/g with '+'.
 */
testSuite({
  testReplaceAll_emptyString() {
    assertEquals(''.replaceAll('Xyz', '+'), '');
    assertEquals(''.replaceAll(/Xyz/g, '+'), '');
  },

  testReplaceAll_missing() {
    assertEquals('some text'.replaceAll('Xyz', '+'), 'some text');
    assertEquals('some text'.replaceAll(/Xyz/g, '+'), 'some text');
  },

  testReplaceAll_simple() {
    assertEquals('Xyz'.replaceAll('Xyz', '+'), '+');
    assertEquals('Xyz'.replaceAll('Xyz', null), 'null');
    assertEquals('X+y'.replaceAll('+', '-'), 'X-y');
    assertEquals('Xy+z', 'XyXyzz'.replaceAll('Xyz', '+'));
    assertEquals('some+text', 'someXyztext'.replaceAll('Xyz', '+'));
    assertEquals('some++text', 'someXyzXyztext'.replaceAll('Xyz', '+'));
  },

  testReplaceAll_withRegExpSearchQuery() {
    assertEquals('Xyz'.replaceAll(/Xyz/g, '+'), '+');
    assertEquals('someXyztext'.replaceAll(/Xyz/g, '+'), 'some+text');
    assertEquals('someXyzXyztext'.replaceAll(/Xyz/g, '+'), 'some++text');
    assertEquals(
        'someXyzmoreXyztext'.replaceAll(/Xyz/g, '+'), 'some+more+text');
  },

  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/replaceAll#non-global_regex_throws
  testReplaceAll_withRegExpSearchQuery_failsWithoutGlobalFlag() {
    const msg =
        'String.prototype.replaceAll called with a non-global RegExp argument.';
    assertThrows(msg, () => 'Xyz'.replaceAll(/Xyz/, '+'));
  },

  testReplaceAll_functionArg_capturingGroups() {
    // function whose return value to substitute.
    const replacerFn = (m, p1, p2) =>
        '-FirstGroup-' + p1 + '-SecondGroup-' + p2 + '-';
    assertEquals(
        'someXyzXyztext'.replaceAll(/(X)(yz)/g, replacerFn),
        'some-FirstGroup-X-SecondGroup-yz--FirstGroup-X-SecondGroup-yz-text');
  },

  testReplaceAll_stringSearchQuery_withReplacerFunction() {
    // function whose return value to substitute.
    const replacerFn = () => '+';
    assertEquals('+', 'Xyz'.replaceAll('Xyz', replacerFn));
    assertEquals('Xy+z', 'XyXyzz'.replaceAll('Xyz', replacerFn));
    assertEquals('some+text', 'someXyztext'.replaceAll('Xyz', replacerFn));
    assertEquals('some++text', 'someXyzXyztext'.replaceAll('Xyz', replacerFn));
  },

  testReplaceAll_withRegExpSearchQuery_withReplacerFunction() {
    // function whose return value to substitute.
    const replacerFn = () => '+';
    assertEquals('Xyz'.replaceAll(/Xyz/g, replacerFn), '+');
    assertEquals('someXyztext'.replaceAll(/Xyz/g, replacerFn), 'some+text');
    assertEquals('someXyzXyztext'.replaceAll(/Xyz/g, replacerFn), 'some++text');
    assertEquals(
        'someXyzmoreXyztext'.replaceAll(/Xyz/g, replacerFn), 'some+more+text');
  },

  testReplaceAll_withRegExpSearchQuery_withReplacerFunction_withArgs() {
    // function whose return value to substitute.
    const replacerFn = (x) => x + 'Some';
    assertEquals('Xyz'.replaceAll(/Xyz/g, replacerFn('Arg')), 'ArgSome');
    assertEquals(
        'someXyztext'.replaceAll(/Xyz/g, replacerFn('Arg')), 'someArgSometext');
    assertEquals(
        'someXyzXyztext'.replaceAll(/Xyz/g, replacerFn('Arg')),
        'someArgSomeArgSometext');
    assertEquals(
        'someXyzmoreXyztext'.replaceAll(/Xyz/g, replacerFn('Arg')),
        'someArgSomemoreArgSometext');
  },

  // "$$", "$`", "$'" and "$&" are special replacement strings
  testReplaceAll_specialReplacement() {
    assertEquals('Xyz'.replaceAll('Xyz', '$$'), '$');
    assertEquals('PreXyzPost'.replaceAll('Xyz', '$&'), 'PreXyzPost');
    assertEquals('PreXyzPost'.replaceAll('Xyz', '$`'), 'PrePrePost');
    assertEquals('PreXyzPost'.replaceAll('Xyz', '$\''), 'PrePostPost');
    assertEquals('PreXyzPostXyz'.replaceAll('Xyz', '$\''), 'PrePostXyzPost');
    assertEquals('123'.replaceAll('2', '$`'), '113');
  },

  // "$n" is a special replacement string when `searchValue` is a regex.
  // Do a literal replacement if `searchValue` is a string.
  testReplaceAll_specialReplacement_nthSubMatch() {
    // $n is a literal replacement when `searchValue` is a string.
    assertEquals('PreXyzPost'.replaceAll('Xyz', '$1'), 'Pre$1Post');
    assertEquals(
        'PreXyzPost'.replaceAll('X(y)z', '$1'),
        'PreXyzPost');  // no match: no-op
    assertEquals('PreXyzXyzPost'.replaceAll('Xyz', '$1'), 'Pre$1$1Post');

    // $n works when searchValue is a regex.
    assertEquals('PreXyzPost'.replaceAll(/(X)(y)(z)/g, '$2'), 'PreyPost');
    assertEquals('PreXyzXyzPost'.replaceAll(/(X)(y)(z)/g, '$2'), 'PreyyPost');
    assertEquals(
        'PreXyzXyzPost'.replaceAll(/(X)(y)(z)/g, '$99'),
        'Pre$99$99Post');  // only 3 valid matches; match $99 becomes a literal
                           // replacement.
  },

  // "$<name>" is a special replacement string in `replaceAll`
  testReplaceAll_specialReplacement_namedGroupMatch() {
    let regExp;
    try {
      regExp = new RegExp('X(?<Name>yz)', 'g');
    } catch (e) {
      // browser lacks support for `?<Name>` named group; do nothing.
      return;
    }
    assertEquals('PreXyzPost'.replaceAll(regExp, '$<Name>'), 'PreyzPost');
    // all occurrences replaced.
    assertEquals(
        'PreXyzXyzXyzPost'.replaceAll(regExp, '$<Name>'), 'PreyzyzyzPost');
  },

  // No match found with the <?Name> group syntax in a string searchValue .
  testReplaceAll_stringSearchValue_namedGroupMatch() {
    assertEquals(
        'PreXyzXyzXyzPost'.replaceAll('X(?<Name>yz)', '$<Name>'),
        'PreXyzXyzXyzPost');
  },
});
