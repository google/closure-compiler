/*
 * Copyright 2014 The Closure Compiler Authors.
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
package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.CheckRegExp.MALFORMED_REGEXP;
import static com.google.javascript.jscomp.MarkUntranspilableFeaturesAsRemoved.UNTRANSPILABLE_FEATURE_PRESENT;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MarkUntranspilableFeaturesAsRemovedTest extends CompilerTestCase {

  private LanguageMode languageIn;
  private LanguageMode languageOut;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    languageIn = LanguageMode.ECMASCRIPT_NEXT;
    languageOut = LanguageMode.ECMASCRIPT3;
    disableTypeCheck();
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageIn(languageIn);
    options.setLanguageOut(languageOut);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new MarkUntranspilableFeaturesAsRemoved(
        compiler, languageIn.toFeatureSet(), languageOut.toFeatureSet());
  }

  @Test
  public void testEs2018RegexFlagS() {
    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2018;
    testSame("const a = /asdf/;");
    testSame("const a = /asdf/g;");
    testSame("const a = /asdf/s;");
    testSame("const a = /asdf/gs;");

    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2017;
    testSame("const a = /asdf/;");
    testSame("const a = /asdf/g;");
    testError(
        "const a = /asdf/s;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        "Cannot convert ECMASCRIPT_2018 feature "
            + "\"RegExp flag 's'\" to targeted output language. "
            + "Either remove feature \"RegExp flag 's'\" "
            + "or raise output level to ECMASCRIPT_2018.");
    testError(
        "const a = /asdf/gs;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        "Cannot convert ECMASCRIPT_2018 feature "
            + "\"RegExp flag 's'\" to targeted output language. "
            + "Either remove feature \"RegExp flag 's'\" "
            + "or raise output level to ECMASCRIPT_2018.");
  }

  @Test
  public void testInvalidRegExpReportsWarning() {
    testWarning("const a = /([0-9a-zA-Z_\\-]{20,}/", MALFORMED_REGEXP);
  }

  @Test
  public void testEs2018RegexLookbehind() {
    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2018;
    testSame("const a = /(?<=asdf)/;");
    testSame("const a = /(?<!asdf)/;");

    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2017;
    testSame("const a = /(?=asdf)/;"); // Lookaheads are fine
    testSame("const a = /(?!asdf)/g;"); // Lookaheads are fine
    testError(
        "const a = /(?<=asdf)/;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        "Cannot convert ECMASCRIPT_2018 feature "
            + "\"RegExp Lookbehind\" to targeted output language. "
            + "Either remove feature \"RegExp Lookbehind\" or "
            + "raise output level to ECMASCRIPT_2018.");
    testError(
        "const a = /(?<!asdf)/;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        "Cannot convert ECMASCRIPT_2018 feature "
            + "\"RegExp Lookbehind\" to targeted output language. "
            + "Either remove feature \"RegExp Lookbehind\" or "
            + "raise output level to ECMASCRIPT_2018.");
  }

  @Test
  public void testEs2018RegexUnicodePropertyEscape() {
    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2018;
    testSame("const a = /\\p{Script=Greek}/u;");
    testSame("const a = /\\P{Script=Greek}/u;");
    testSame("const a = /\\p{Script=Greek}/;"); // Without u flag, /\p/ is same as /p/
    testSame("const a = /\\P{Script=Greek}/;"); // Without u flag, /\p/ is same as /p/

    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2017;
    testSame("const a = /\\p{Script=Greek}/;"); // Without u flag, /\p/ is same as /p/
    testSame("const a = /\\P{Script=Greek}/;"); // Without u flag, /\p/ is same as /p/
    testError(
        "const a = /\\p{Script=Greek}/u;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        "Cannot convert ECMASCRIPT_2018 feature "
            + "\"RegExp unicode property escape\" to targeted "
            + "output language. Either remove feature \"RegExp unicode property escape\" or raise "
            + "output level to ECMASCRIPT_2018.");
    testError(
        "const a = /\\P{Script=Greek}/u;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        "Cannot convert ECMASCRIPT_2018 feature "
            + "\"RegExp unicode property escape\" to targeted "
            + "output language. Either remove feature \"RegExp unicode property escape\" or raise "
            + "output level to ECMASCRIPT_2018.");
  }

  @Test
  public void testRegExpConstructorCalls() {
    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2017;
    // TODO(bradfordcsmith): report errors from RegExp in this form
    testSame("const a = new RegExp('asdf', 'gs');");
  }

  @Test
  public void testEs2018RegexNamedCaptureGroups() {
    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2018;
    testSame("const a = /(?<name>)/u;");

    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2017;
    testError(
        "const a = /(?<name>)/;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        "Cannot convert ECMASCRIPT_2018 feature \"RegExp named groups\" "
            + "to targeted output language. "
            + "Either remove feature \"RegExp named groups\" "
            + "or raise output level to ECMASCRIPT_2018.");
    testError(
        "const a = /(?<$var>).*/u;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        "Cannot convert ECMASCRIPT_2018 feature \"RegExp named groups\" "
            + "to targeted output language. "
            + "Either remove feature \"RegExp named groups\" "
            + "or raise output level to ECMASCRIPT_2018.");
    // test valid regex with '<' or '>' that is not named capture group
    testSame("const a = /(<name>)/;");
    testSame("const a = /(>.>)/u;");
  }

  @Test
  public void testEs2018RegexNamedCaptureGroupsBackReferencing() {
    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2018;
    testSame("const a = /^(?<half>.*).\\k<half>$/u;");

    languageIn = LanguageMode.ECMASCRIPT_2018;
    languageOut = LanguageMode.ECMASCRIPT_2017;
    testError(
        "const a = /^(?<half>.*).\\k<half>$/u;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        "Cannot convert ECMASCRIPT_2018 feature \"RegExp named groups\" "
            + "to targeted output language. "
            + "Either remove feature \"RegExp named groups\" "
            + "or raise output level to ECMASCRIPT_2018.");

    // test that in named groups backreferencing, the backslash is not removed
    testSame("const a = /.\\k<half>$/u;");
  }
}
