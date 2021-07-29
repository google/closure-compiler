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
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for error message filtering.
 *
 */
@RunWith(JUnit4.class)
public final class RhinoErrorReporterTest {
  private boolean reportLintWarnings;
  private CompilerOptions.LanguageMode languageIn;

  @Before
  public void setUp() throws Exception {
    reportLintWarnings = true;
    this.languageIn = CompilerOptions.LanguageMode.UNSUPPORTED;
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

  @Test
  public void testLanguageFeatureInHigherLanguageInError() {
    this.languageIn = CompilerOptions.LanguageMode.ECMASCRIPT_2015;
    assertError(
        "2 ** 3",
        RhinoErrorReporter.LANGUAGE_FEATURE,
        "This language feature is only supported for ECMASCRIPT_2016 mode or better: "
            + "exponent operator (**).");
  }

  /**
   * Verifies that the compiler emits an error for the given code.
   */
  private void assertNoWarningOrError(String code) {
    Compiler compiler = parseCode(code);
    assertWithMessage("Expected error").that(compiler.getErrorCount()).isEqualTo(0);
    assertWithMessage("Expected warning").that(compiler.getErrorCount()).isEqualTo(0);
  }

  /** Verifies that the compiler emits an error for the given code. */
  private JSError assertError(String code, DiagnosticType type, String description) {
    Compiler compiler = parseCode(code);
    assertWithMessage("Expected error").that(compiler.getErrorCount()).isEqualTo(1);

    JSError error = Iterables.getOnlyElement(compiler.getErrors());
    assertThat(error.getType()).isEqualTo(type);
    assertThat(error.getDescription()).isEqualTo(description);
    return error;
  }

  /** Verifies that the compiler emits a warning for the given code. */
  private JSError assertWarning(String code, DiagnosticType type, String description) {
    Compiler compiler = parseCode(code);
    assertWithMessage("Expected warning").that(compiler.getWarningCount()).isEqualTo(1);

    JSError error = Iterables.getOnlyElement(compiler.getWarnings());
    assertThat(error.getType()).isEqualTo(type);
    assertThat(error.getDescription()).isEqualTo(description);
    return error;
  }

  private Compiler parseCode(String code) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();

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

    options.setLanguageIn(languageIn);

    List<SourceFile> externs = ImmutableList.of();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("input", code));
    compiler.init(externs, inputs, options);
    compiler.parseInputs();
    return compiler;
  }
}
