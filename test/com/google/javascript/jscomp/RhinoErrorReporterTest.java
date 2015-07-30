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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for error message filtering.
 * @author nicksantos@google.com (Nick Santos)
 */
public final class RhinoErrorReporterTest extends TestCase {

  private boolean reportEs3Props;
  private boolean reportLintWarnings;

  @Override
  protected void setUp() throws Exception {
    reportEs3Props = true;
    reportLintWarnings = true;
    super.setUp();
  }

  public void testTrailingComma() throws Exception {
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

    assertEquals(2, error.getLineNumber());
    assertEquals(8, error.getCharno());
  }

  public void testInvalidEs3Prop() throws Exception {
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

    assertEquals(1, error.getLineNumber());
    assertEquals(10, error.getCharno());
  }


  public void testMissingTypeWarnings() throws Exception {
    reportLintWarnings = false;

    assertNoWarningOrError("/** @return */ function f() {}");

    reportLintWarnings = true;

    String message =
        "Missing type declaration.";
    JSError error = assertWarning(
        "/** @return */ function f() {}",
        RhinoErrorReporter.JSDOC_MISSING_TYPE_WARNING,
        message);

    assertEquals(1, error.getLineNumber());
    assertEquals(4, error.getCharno());
  }

  /**
   * Verifies that the compiler emits an error for the given code.
   */
  private void assertNoWarningOrError(String code) {
    Compiler compiler = parseCode(code);
    assertEquals("Expected error", 0, compiler.getErrorCount());
    assertEquals("Expected warning", 0, compiler.getErrorCount());
  }

  /**
   * Verifies that the compiler emits an error for the given code.
   */
  private JSError assertError(
      String code, DiagnosticType type, String description) {
    Compiler compiler = parseCode(code);
    assertEquals("Expected error", 1, compiler.getErrorCount());

    JSError error =
        Iterables.getOnlyElement(Arrays.asList(compiler.getErrors()));
    assertEquals(type, error.getType());
    assertEquals(description, error.description);
    return error;
  }

  /**
   * Verifies that the compiler emits an error for the given code.
   */
  private JSError assertWarning(
      String code, DiagnosticType type, String description) {
    Compiler compiler = parseCode(code);
    assertEquals("Expected warning", 1, compiler.getWarningCount());

    JSError error =
        Iterables.getOnlyElement(Arrays.asList(compiler.getWarnings()));
    assertEquals(type, error.getType());
    assertEquals(description, error.description);
    return error;
  }

  private Compiler parseCode(String code) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();

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
    }

    List<SourceFile> externs = ImmutableList.of();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("input", code));
    compiler.init(externs, inputs, options);
    compiler.parseInputs();
    return compiler;
  }
}
