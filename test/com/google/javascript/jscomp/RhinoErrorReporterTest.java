/*
 * Copyright 2010 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.parsing.JsDocInfoParser.BAD_TYPE_WIKI_LINK;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for error message filtering.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class RhinoErrorReporterTest {

  private boolean reportEs3Props;
  private boolean reportLintWarnings;

  @Before
  public void setUp() throws Exception {
    reportEs3Props = true;
    reportLintWarnings = true;
  }

  @Test
  public void testTrailingComma() {
    String message =
        "Parse error. IE8 (and below) will parse trailing commas in " +
        "array and object literals incorrectly. " +
        "If you are targeting newer versions of JS, " +
        "set the appropriate language_in option.";
    assertError(
        "var x = [1,];",
        RhinoErrorReporter.TRAILING_COMMA,
        message);
    JSError error = assertError(
        "var x = {\n" +
        "    1: 2,\n" +
        "};",
        RhinoErrorReporter.TRAILING_COMMA,
        message);

    assertThat(error.getLineNumber()).isEqualTo(2);
    assertThat(error.getCharno()).isEqualTo(8);
  }

  @Test
  public void testInvalidEs3Prop() {
    reportEs3Props = false;

    assertNoWarningOrError("var x = y.function;");

    reportEs3Props = true;

    String message =
        "Keywords and reserved words are not allowed as unquoted property " +
        "names in older versions of JavaScript. " +
        "If you are targeting newer versions of JavaScript, " +
        "set the appropriate language_in option.";
    JSError error = assertWarning(
        "var x = y.function;",
        RhinoErrorReporter.INVALID_ES3_PROP_NAME,
        message);

    assertThat(error.getLineNumber()).isEqualTo(1);
    assertThat(error.getCharno()).isEqualTo(10);
  }

  @Test
  public void testMissingTypeWarnings() {
    reportLintWarnings = false;

    assertNoWarningOrError("/** @return */ function f() {}");

    reportLintWarnings = true;

    String message =
        "Missing type declaration.";
    JSError error = assertWarning(
        "/** @return */ function f() {}",
        RhinoErrorReporter.JSDOC_MISSING_TYPE_WARNING,
        message);

    assertThat(error.getLineNumber()).isEqualTo(1);
    assertThat(error.getCharno()).isEqualTo(4);
  }

  @Test
  public void testMissingCurlyBraceWarning() {
    reportLintWarnings = false;
    assertNoWarningOrError("/** @type string */ var x;");

    reportLintWarnings = true;
    assertWarning(
        "/** @type string */ var x;",
        RhinoErrorReporter.JSDOC_MISSING_BRACES_WARNING,
        "Bad type annotation. Type annotations should have curly braces." + BAD_TYPE_WIKI_LINK);
  }

  /**
   * Verifies that the compiler emits an error for the given code.
   */
  private void assertNoWarningOrError(String code) {
    Compiler compiler = parseCode(code);
    assertWithMessage("Expected error").that(compiler.getErrorCount()).isEqualTo(0);
    assertWithMessage("Expected warning").that(compiler.getErrorCount()).isEqualTo(0);
  }

  /**
   * Verifies that the compiler emits an error for the given code.
   */
  private JSError assertError(
      String code, DiagnosticType type, String description) {
    Compiler compiler = parseCode(code);
    assertWithMessage("Expected error").that(compiler.getErrorCount()).isEqualTo(1);

    JSError error =
        Iterables.getOnlyElement(Arrays.asList(compiler.getErrors()));
    assertThat(error.getType()).isEqualTo(type);
    assertThat(error.description).isEqualTo(description);
    return error;
  }

  /**
   * Verifies that the compiler emits an error for the given code.
   */
  private JSError assertWarning(
      String code, DiagnosticType type, String description) {
    Compiler compiler = parseCode(code);
    assertWithMessage("Expected warning").that(compiler.getWarningCount()).isEqualTo(1);

    JSError error =
        Iterables.getOnlyElement(Arrays.asList(compiler.getWarnings()));
    assertThat(error.getType()).isEqualTo(type);
    assertThat(error.description).isEqualTo(description);
    return error;
  }

  private Compiler parseCode(String code) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);

    if (!reportEs3Props) {
      options.setWarningLevel(
          DiagnosticGroups.ES3,
          CheckLevel.OFF);
    }

    if (!reportLintWarnings) {
      options.setWarningLevel(
          DiagnosticGroups.LINT_CHECKS,
          CheckLevel.OFF);
    } else {
      options.setWarningLevel(
          DiagnosticGroups.LINT_CHECKS,
          CheckLevel.WARNING);
      options.setWarningLevel(
          DiagnosticGroups.JSDOC_MISSING_TYPE,
          CheckLevel.WARNING);
    }

    List<SourceFile> externs = ImmutableList.of();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("input", code));
    compiler.init(externs, inputs, options);
    compiler.parseInputs();
    return compiler;
  }
}
