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
package com.google.javascript.refactoring;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CheckLevel.ERROR;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.SourceFile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;

/**
 * Test case for {@link ErrorToFixMapper}.
 */

@RunWith(JUnit4.class)
public class ErrorToFixMapperTest {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  private FixingErrorManager errorManager;
  private CompilerOptions options;
  private Compiler compiler;

  @Before
  public void setUp() {
    errorManager = new FixingErrorManager();
    compiler = new Compiler(errorManager);
    errorManager.setCompiler(compiler);

    options = RefactoringDriver.getCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT, ERROR);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, ERROR);
  }

  @Test
  public void testDebugger() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  debugger;",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  ",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testInsertSemicolon1() {
    String code = "var x = 3";
    String expectedCode = "var x = 3;";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testInsertSemicolon2() {
    String code = "function f() { return 'it' }";
    String expectedCode = "function f() { return 'it'; }";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRequiresSorted1() {
    assertChanges(
        LINE_JOINER.join(
            "/** @fileoverview foo */",
            "",
            "",
            "goog.require('b');",
            "goog.require('a');",
            "goog.require('c');",
            "",
            "",
            "alert(1);"),
        LINE_JOINER.join(
            "/** @fileoverview foo */",
            "",
            "",
            "goog.require('a');",
            "goog.require('b');",
            "goog.require('c');",
            "",
            "",
            "alert(1);"));
  }

  @Test
  public void testRequiresSorted2() {
    assertChanges(
        LINE_JOINER.join(
            "goog.provide('x');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('b');",
            "goog.require('a');",
            "",
            "alert(1);"),
        LINE_JOINER.join(
            "goog.provide('x');",
            "",
            "goog.require('a');",
            "/** @suppress {extraRequire} */",
            "goog.require('b');",
            "",
            "alert(1);"));
  }

  @Test
  public void testRequiresInGoogModule() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "goog.require('a.c');",
            "goog.require('a.b.d');",
            "goog.require('a.b.c');",
            "",
            "alert(1);"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "goog.require('a.b.c');",
            "goog.require('a.b.d');",
            "goog.require('a.c');",
            "",
            "alert(1);"));
  }

  @Test
  public void testProvidesSorted1() {
    assertChanges(
        LINE_JOINER.join(
            "/** @fileoverview foo */",
            "",
            "",
            "goog.provide('b');",
            "goog.provide('a');",
            "goog.provide('c');",
            "",
            "",
            "alert(1);"),
        LINE_JOINER.join(
            "/** @fileoverview foo */",
            "",
            "",
            "goog.provide('a');",
            "goog.provide('b');",
            "goog.provide('c');",
            "",
            "",
            "alert(1);"));
  }

  private void assertChanges(String originalCode, String expectedCode) {
    compiler.compile(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", originalCode)),
        options);
    Collection<SuggestedFix> fixes = errorManager.getAllFixes();
    String newCode = ApplySuggestedFixes.applySuggestedFixesToCode(
        fixes, ImmutableMap.of("test", originalCode)).get("test");
    assertThat(newCode).isEqualTo(expectedCode);
  }
}
