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

goog.module('jscomp.runtime_tests.polyfill_tests.string_raw_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({

  testStringRaw_templateTag() {
    let tab = String.raw`\t`;
    assertEquals("foo\\n", String.raw`foo\n`);
    assertEquals("foo1bar", String.raw`foo${1}bar`);
    assertEquals("foo1bar2baz", String.raw`foo${1}bar${1+1}baz`);
    assertEquals("11\\t12", String.raw`11${tab}12`);
    assertEquals("11\\t12", String.raw`11${String.raw`\t`}12`);
  },

  testStringRaw_functionCall() {
    let arg = [];
    arg.raw = [];
    const template = /** @type {!ITemplateArray} */ (arg);

    // Empty array of template literals.
    assertEquals('', String.raw(template));

    arg.push('foo\n');
    arg.raw.push('foo\\n');
    // Too many substitution parameters. Both should be ignored.
    assertEquals('foo\\n', String.raw(template, 1, 2));
    arg.push('bar');
    arg.raw.push('bar');
    // Too many substitution parameters. The second one should be ignored.
    assertEquals('foo\\n1bar', String.raw(template, 1, 2));
    // Normal case.
    assertEquals('foo\\n1bar', String.raw(template, 1));
    // Too few substitution parameters. Just concatenate the template strings.
    assertEquals('foo\\nbar', String.raw(template));
  },

  /** @suppress {checkTypes} */
  testStringRaw_functionCallWithNull() {
    assertThrows(() => String.raw(null));
  },

});
