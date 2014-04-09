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
import com.google.common.collect.Lists;
import junit.framework.TestCase;

import java.util.List;

/**
 * Tests for error message filtering.
 * @author nicksantos@google.com (Nick Santos)
 */
public class RhinoErrorReporterTest extends TestCase {

  private boolean reportMisplacedTypeAnnotations;
  private boolean reportEs3Props;

  @Override
  protected void setUp() throws Exception {
    reportMisplacedTypeAnnotations = false;
    reportEs3Props = true;
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

  public void testMisplacedTypeAnnotation() throws Exception {
    reportMisplacedTypeAnnotations = false;

    assertNoWarningOrError("var x = /** @type {string} */ y;");

    reportMisplacedTypeAnnotations = true;

    String message =
        "Type annotations are not allowed here. " +
        "Are you missing parentheses?";
    JSError error = assertWarning(
        "var x = /** @type {string} */ y;",
        RhinoErrorReporter.MISPLACED_TYPE_ANNOTATION,
        message);

    assertEquals(1, error.getLineNumber());
    assertEquals(30, error.getCharno());
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
        Iterables.getOnlyElement(Lists.newArrayList(compiler.getErrors()));
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
        Iterables.getOnlyElement(Lists.newArrayList(compiler.getWarnings()));
    assertEquals(type, error.getType());
    assertEquals(description, error.description);
    return error;
  }

  private Compiler parseCode(String code) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    if (reportMisplacedTypeAnnotations) {
      options.setWarningLevel(
          DiagnosticGroups.MISPLACED_TYPE_ANNOTATION,
          CheckLevel.WARNING);
    }

    if (!reportEs3Props) {
      options.setWarningLevel(
          DiagnosticGroups.ES3,
          CheckLevel.OFF);
    }

    List<SourceFile> externs = ImmutableList.of();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("input", code));
    compiler.init(externs, inputs, options);
    compiler.parseInputs();
    return compiler;
  }
}
