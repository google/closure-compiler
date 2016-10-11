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
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


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
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, ERROR);
    options.setWarningLevel(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT, ERROR);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, ERROR);
    options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_REQUIRE, ERROR);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, ERROR);
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
  public void testRedeclaration() {
    String code = "function f() { var x; var x; }";
    String expectedCode = "function f() { var x; }";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars1() {
    String code = "function f() { var x; var x, y; }";
    String expectedCode = "function f() { var x; var y; }";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars2() {
    String code = "function f() { var x; var y, x; }";
    String expectedCode = "function f() { var x; var y; }";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_withValue() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  var x = 0;",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  x = 0;",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue1() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  var x = 0, y;",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  x = 0;",
        "var y;",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue2() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  var y, x = 0;",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  var y;",
        "x = 0;",
        "}");
    assertChanges(code, expectedCode);
  }

  // Make sure the vars stay in the same order, so that in case the get*
  // functions have side effects, we don't change the order they're called in.
  @Test
  public void testRedeclaration_multipleVars_withValue3() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var y;",
        "  var x = getX(), y = getY(), z = getZ();",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var y;",
        "  var x = getX();",
        "y = getY();",
        "var z = getZ();",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue4() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  var x = getX(), y = getY(), z = getZ();",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  x = getX();",
        "var y = getY(), z = getZ();",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue5() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var z;",
        "  var x = getX(), y = getY(), z = getZ();",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var z;",
        "  var x = getX(), y = getY();",
        "z = getZ();",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testEarlyReference() {
    String code = "if (x < 0) alert(1);\nvar x;";
    String expectedCode = "var x;\n" + code;
    assertChanges(code, expectedCode);
  }

  @Test
  public void testEarlyReferenceInFunction() {
    String code = "function f() {\n  if (x < 0) alert(1);\nvar x;\n}";
    String expectedCode = "function f() {\n  var x;\nif (x < 0) alert(1);\nvar x;\n}";
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
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "",
            "",
            "goog.require('b');",
            "goog.require('a');",
            "goog.require('c');",
            "",
            "",
            "alert(1);"),
        LINE_JOINER.join(
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
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
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "goog.provide('x');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('b');",
            "goog.require('a');",
            "",
            "alert(1);"),
        LINE_JOINER.join(
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "goog.provide('x');",
            "",
            "goog.require('a');",
            "/** @suppress {extraRequire} */",
            "goog.require('b');",
            "",
            "alert(1);"));
  }

  @Test
  public void testSortRequiresInGoogModule() {
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
  public void testMissingRequireInGoogModule() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "alert(new a.b.C());"),
        LINE_JOINER.join(
            "goog.module('m');",
            // TODO(tbreisacher): Add the shorthand form instead: var C = goog.require('a.b.C');
            "goog.require('a.b.C');",
            "",
            // TODO(tbreisacher): Also change this to use the shorthand: alert(new C());
            "alert(new a.b.C());"));
  }

  @Test
  public void testShortRequireInGoogModule() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var c = goog.require('a.b.c');",
            "",
            "alert(a.b.c);"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var c = goog.require('a.b.c');",
            "",
            "alert(c);"));
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

  @Test
  public void testExtraRequire() {
    assertChanges(
        LINE_JOINER.join(
            "goog.require('goog.object');",
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"),
        LINE_JOINER.join(
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"));
  }

  @Test
  public void testDuplicateRequire() {
    assertChanges(
        LINE_JOINER.join(
            "goog.require('goog.string');",
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"),
        LINE_JOINER.join(
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"));
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
