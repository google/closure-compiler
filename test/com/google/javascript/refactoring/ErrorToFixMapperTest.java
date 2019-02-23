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
import static com.google.javascript.jscomp.CheckLevel.OFF;
import static com.google.javascript.jscomp.CheckLevel.WARNING;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.JSError;
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
  private FixingErrorManager errorManager;
  private CompilerOptions options;
  private Compiler compiler;

  @Before
  public void setUp() {
    errorManager = new FixingErrorManager();
    compiler = new Compiler(errorManager);
    compiler.disableThreads();
    errorManager.setCompiler(compiler);

    options = RefactoringDriver.getCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, WARNING);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, ERROR);
    options.setWarningLevel(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT, ERROR);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, WARNING);
    options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_REQUIRE, ERROR);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, ERROR);
  }

  @Test
  public void testDebugger() {
    String code =
        lines(
            "function f() {", //
            "  debugger;",
            "}");
    String expectedCode =
        lines(
            "function f() {", //
            "  ",
            "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testEmptyStatement1() {
    assertChanges("var x;;", "var x;");
  }

  @Test
  public void testEmptyStatement2() {
    assertChanges("var x;;\nvar y;", "var x;\nvar y;");
  }

  @Test
  public void testEmptyStatement3() {
    assertChanges("function f() {};\nf();", "function f() {}\nf();");
  }

  @Test
  public void testImplicitNullability1() {
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, OFF);
    assertChanges(
        "/** @type {Object} */ var o;",
        "/** @type {?Object} */ var o;",
        "/** @type {!Object} */ var o;");
  }

  @Test
  public void testImplicitNullability2() {
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, OFF);
    assertChanges(
        "/** @param {Object} o */ function f(o) {}",
        "/** @param {?Object} o */ function f(o) {}",
        "/** @param {!Object} o */ function f(o) {}");
  }

  @Test
  public void testImplicitNullability3() {
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, OFF);
    String originalCode =
        lines(
            "/**",
            " * Some non-ASCII characters: αβγδε",
            " * @param {Object} o",
            " */",
            "function f(o) {}");
    String expected1 =
        lines(
            "/**",
            " * Some non-ASCII characters: αβγδε",
            " * @param {?Object} o",
            " */",
            "function f(o) {}");
    String expected2 =
        lines(
            "/**",
            " * Some non-ASCII characters: αβγδε",
            " * @param {!Object} o",
            " */",
            "function f(o) {}");
    assertChanges(originalCode, expected1, expected2);
  }

  @Test
  public void testMissingNullabilityModifier1() {
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, OFF);
    assertChanges(
        "/** @type {Object} */ var o;",
        "/** @type {!Object} */ var o;",
        "/** @type {?Object} */ var o;");
  }

  @Test
  public void testMissingNullabilityModifier2() {
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, OFF);
    assertChanges(
        "/** @param {Object} o */ function f(o) {}",
        "/** @param {!Object} o */ function f(o) {}",
        "/** @param {?Object} o */ function f(o) {}");
  }

  @Test
  public void testMissingNullabilityModifier3() {
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, OFF);
    String originalCode =
        lines(
            "/**",
            " * Some non-ASCII characters: αβγδε",
            " * @param {Object} o",
            " */",
            "function f(o) {}");
    String expected1 =
        lines(
            "/**",
            " * Some non-ASCII characters: αβγδε",
            " * @param {!Object} o",
            " */",
            "function f(o) {}");
    String expected2 =
        lines(
            "/**",
            " * Some non-ASCII characters: αβγδε",
            " * @param {?Object} o",
            " */",
            "function f(o) {}");
    assertChanges(originalCode, expected1, expected2);
  }

  @Test
  public void testNullMissingNullabilityModifier1() {
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, OFF);
    assertChanges("/** @type {Object} */ var x = null;", "/** @type {?Object} */ var x = null;");
  }

  @Test
  public void testNullMissingNullabilityModifier2() {
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, OFF);
    assertNoChanges("/** @type {?Object} */ var x = null;");
  }

  @Test
  public void testMissingNullabilityModifier_nonNullValue() {
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, OFF);
    assertChanges(
        "/** @type {Object} */ var o = {};",
        "/** @type {!Object} */ var o = {};",
        "/** @type {?Object} */ var o = {};");
  }

  @Test
  public void testRedundantNullabilityModifier1() {
    assertChanges("/** @type {!string} */ var x;", "/** @type {string} */ var x;");
  }

  @Test
  public void testRedundantNullabilityModifier2() {
    assertChanges(
        "/** @type {!{foo: string, bar: !string}} */ var x;",
        "/** @type {{foo: string, bar: string}} */ var x;");
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
    String code =
        lines(
            "function f() {", //
            "  var x;",
            "  var x = 0;",
            "}");
    String expectedCode = lines("function f() {", "  var x;", "  x = 0;", "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue1() {
    String code =
        lines(
            "function f() {", //
            "  var x;",
            "  var x = 0, y;",
            "}");
    String expectedCode = lines("function f() {", "  var x;", "  x = 0;", "var y;", "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue2() {
    String code =
        lines(
            "function f() {", //
            "  var x;",
            "  var y, x = 0;",
            "}");
    String expectedCode = lines("function f() {", "  var x;", "  var y;", "x = 0;", "}");
    assertChanges(code, expectedCode);
  }

  // Make sure the vars stay in the same order, so that in case the get*
  // functions have side effects, we don't change the order they're called in.
  @Test
  public void testRedeclaration_multipleVars_withValue3() {
    String code =
        lines(
            "function f() {", //
            "  var y;",
            "  var x = getX(), y = getY(), z = getZ();",
            "}");
    String expectedCode =
        lines(
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
    String code =
        lines(
            "function f() {", //
            "  var x;",
            "  var x = getX(), y = getY(), z = getZ();",
            "}");
    String expectedCode =
        lines(
            "function f() {", //
            "  var x;",
            "  x = getX();",
            "var y = getY(), z = getZ();",
            "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue5() {
    String code =
        lines(
            "function f() {", //
            "  var z;",
            "  var x = getX(), y = getY(), z = getZ();",
            "}");
    String expectedCode =
        lines(
            "function f() {", //
            "  var z;",
            "  var x = getX(), y = getY();",
            "z = getZ();",
            "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclarationOfParam() {
    assertChanges("function f(x) { var x = 3; }", "function f(x) { x = 3; }");
  }

  @Test
  public void testRedeclaration_params() {
    assertNoChanges("function f(x, x) {}");
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
  public void testRequiresSorted_standalone() {
    assertChanges(
        lines(
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "",
            "",
            "goog.require('b');",
            "goog.requireType('a');",
            "goog.requireType('d');",
            "goog.require('c');",
            "",
            "",
            "alert(1);"),
        lines(
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "",
            "",
            "goog.requireType('a');",
            "goog.require('b');",
            "goog.require('c');",
            "goog.requireType('d');",
            "",
            "",
            "alert(1);"));
  }

  @Test
  public void testRequiresSorted_suppressExtra() {
    assertChanges(
        lines(
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "goog.provide('x');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.requireType('c');",
            "/** @suppress {extraRequire} */",
            "goog.require('b');",
            "goog.require('a');",
            "",
            "alert(1);"),
        lines(
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "goog.provide('x');",
            "",
            "goog.require('a');",
            "/** @suppress {extraRequire} */",
            "goog.require('b');",
            "/** @suppress {extraRequire} */",
            "goog.requireType('c');",
            "",
            "alert(1);"));
  }

  @Test
  public void testSortRequiresInGoogModule_let() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('a.c');",
            "/** @suppress {extraRequire} */",
            "goog.require('a.b');",
            "",
            "let localVar;"),
        lines(
            "goog.module('m');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('a.b');",
            "/** @suppress {extraRequire} */",
            "goog.require('a.c');",
            "",
            "let localVar;"));
  }

  @Test
  public void testSortRequiresInGoogModule_const() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('a.c');",
            "/** @suppress {extraRequire} */",
            "goog.require('a.b');",
            "",
            "const FOO = 0;"),
        lines(
            "goog.module('m');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('a.b');",
            "/** @suppress {extraRequire} */",
            "goog.require('a.c');",
            "",
            "const FOO = 0;"));
  }

  /**
   * Using this form in a goog.module is a violation of the style guide, but still fairly common.
   */
  @Test
  public void testSortRequiresInGoogModule_standalone() {
    assertChanges(
        lines(
            "/** @fileoverview @suppress {strictModuleChecks} */",
            "goog.module('m');",
            "",
            "goog.require('a.c');",
            "goog.require('a.b.d');",
            "goog.require('a.b.c');",
            "",
            "alert(a.c());",
            "alert(a.b.d());",
            "alert(a.b.c());"),
        lines(
            "/** @fileoverview @suppress {strictModuleChecks} */",
            "goog.module('m');",
            "",
            "goog.require('a.b.c');",
            "goog.require('a.b.d');",
            "goog.require('a.c');",
            "",
            "alert(a.c());",
            "alert(a.b.d());",
            "alert(a.b.c());"));
  }

  @Test
  public void testSortRequiresInGoogModule_shorthand() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "var c2 = goog.require('a.c');",
            "var d = goog.require('a.b.d');",
            "var c1 = goog.require('a.b.c');",
            "",
            "alert(c1());",
            "alert(d());",
            "alert(c2());"),
        lines(
            "goog.module('m');",
            "",
            "var c1 = goog.require('a.b.c');",
            "var c2 = goog.require('a.c');",
            "var d = goog.require('a.b.d');",
            "",
            "alert(c1());",
            "alert(d());",
            "alert(c2());"));
  }

  @Test
  public void testSortRequiresInGoogModule_destructuring() {
    assertChanges(
        lines(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const {fooBar} = goog.require('x');",
            "const {foo, bar} = goog.requireType('y');"),
        lines(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const {foo, bar} = goog.requireType('y');",
            "const {fooBar} = goog.require('x');"));
  }

  @Test
  public void testSortRequiresInGoogModule_shorthandAndStandalone() {
    assertChanges(
        lines(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const shorthand2 = goog.requireType('a');",
            "goog.require('standalone.two');",
            "goog.requireType('standalone.one');",
            "const shorthand1 = goog.require('b');"),
        lines(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const shorthand1 = goog.require('b');",
            "const shorthand2 = goog.requireType('a');",
            "goog.requireType('standalone.one');",
            "goog.require('standalone.two');"));
  }

  @Test
  public void testSortRequiresInGoogModule_allThreeStyles() {
    assertChanges(
        lines(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const shorthand2 = goog.requireType('a');",
            "goog.require('standalone.two');",
            "const {destructuring2} = goog.requireType('c');",
            "const {destructuring1} = goog.require('d');",
            "goog.requireType('standalone.one');",
            "const shorthand1 = goog.require('b');"),
        lines(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const shorthand1 = goog.require('b');",
            "const shorthand2 = goog.requireType('a');",
            "const {destructuring1} = goog.require('d');",
            "const {destructuring2} = goog.requireType('c');",
            "goog.requireType('standalone.one');",
            "goog.require('standalone.two');"));
  }

  @Test
  public void testSortRequiresInGoogModule_withFwdDeclare() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const s = goog.require('s');",
            "const g = goog.forwardDeclare('g');",
            "const f = goog.forwardDeclare('f');",
            "const r = goog.require('r');",
            "",
            "alert(r, s);"),
        lines(
            "goog.module('x');",
            "",
            "const r = goog.require('r');",
            "const s = goog.require('s');",
            "const f = goog.forwardDeclare('f');",
            "const g = goog.forwardDeclare('g');",
            "",
            "alert(r, s);"));
  }

  @Test
  public void testSortRequiresInGoogModule_withOtherStatements() {
    // The requires after "const {Bar} = bar;" are not sorted.
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const foo = goog.require('foo');",
            "const bar = goog.require('bar');",
            "const {Bar} = bar;",
            "const util = goog.require('util');",
            "const type = goog.requireType('type');",
            "const {doCoolThings} = util;",
            "",
            "/** @type {!type} */ let x;",
            "doCoolThings(foo, Bar);"),
        lines(
            "goog.module('x');",
            "",
            "const bar = goog.require('bar');",
            "const foo = goog.require('foo');",
            "const {Bar} = bar;",
            "const util = goog.require('util');",
            "const type = goog.requireType('type');",
            "const {doCoolThings} = util;",
            "",
            "/** @type {!type} */ let x;",
            "doCoolThings(foo, Bar);"));
  }

  @Test
  public void testSortRequiresInGoogModule_veryLongRequire() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "const {veryLongDestructuringStatementSoLongThatWeGoPast80CharactersBeforeGettingToTheClosingCurlyBrace} = goog.require('other');",
            "const {anotherVeryLongDestructuringStatementSoLongThatWeGoPast80CharactersBeforeGettingToTheClosingCurlyBrace} = goog.requireType('type');",
            "const shorter = goog.require('shorter');",
            "",
            "/** @type {!anotherVeryLongDestructuringStatementSoLongThatWeGoPast80CharactersBeforeGettingToTheClosingCurlyBrace} */ var x;",
            "use(veryLongDestructuringStatementSoLongThatWeGoPast80CharactersBeforeGettingToTheClosingCurlyBrace);",
            "use(shorter);"),
        lines(
            "goog.module('m');",
            "",
            "const shorter = goog.require('shorter');",
            "const {anotherVeryLongDestructuringStatementSoLongThatWeGoPast80CharactersBeforeGettingToTheClosingCurlyBrace} = goog.requireType('type');",
            "const {veryLongDestructuringStatementSoLongThatWeGoPast80CharactersBeforeGettingToTheClosingCurlyBrace} = goog.require('other');",
            "",
            "/** @type {!anotherVeryLongDestructuringStatementSoLongThatWeGoPast80CharactersBeforeGettingToTheClosingCurlyBrace} */ var x;",
            "use(veryLongDestructuringStatementSoLongThatWeGoPast80CharactersBeforeGettingToTheClosingCurlyBrace);",
            "use(shorter);"));
  }

  @Test
  public void testSortRequiresAndForwardDeclares() {
    assertChanges(
        lines(
            "goog.provide('x');",
            "",
            "goog.require('s');",
            "goog.forwardDeclare('g');",
            "goog.forwardDeclare('f');",
            "goog.require('r');",
            "",
            "alert(r, s);"),
        lines(
            "goog.provide('x');",
            "",
            "goog.require('r');",
            "goog.require('s');",
            "goog.forwardDeclare('f');",
            "goog.forwardDeclare('g');",
            "",
            "alert(r, s);"));
  }

  @Test
  public void testMissingRequireInGoogProvideFile() {
    assertChanges(
        lines(
            "goog.provide('p');", //
            "",
            "alert(new a.b.C());"),
        lines(
            "goog.provide('p');", //
            "goog.require('a.b.C');",
            "",
            "alert(new a.b.C());"));
  }

  @Test
  public void testMissingRequire_unsorted1() {
    // Both the fix for requires being unsorted, and the fix for the missing require, are applied.
    // However, the end result is still out of order.
    assertChanges(
        lines(
            "goog.module('module');",
            "",
            "const Xray = goog.require('goog.Xray');",
            "const Anteater = goog.require('goog.Anteater');",
            "",
            "alert(new Anteater());",
            "alert(new Xray());",
            "alert(new goog.dom.DomHelper());"),
        lines(
            "goog.module('module');",
            "",
            "const DomHelper = goog.require('goog.dom.DomHelper');",
            "const Anteater = goog.require('goog.Anteater');",
            "const Xray = goog.require('goog.Xray');",
            "",
            "alert(new Anteater());",
            "alert(new Xray());",
            "alert(new DomHelper());"));
  }

  @Test
  public void testMissingRequire_unsorted2() {
    // Both the fix for requires being unsorted, and the fix for the missing require, are applied.
    // The end result is ordered.
    assertChanges(
        lines(
            "goog.module('module');",
            "",
            "const DomHelper = goog.require('goog.dom.DomHelper');",
            "const Anteater = goog.require('goog.Anteater');",
            "",
            "alert(new Anteater());",
            "alert(new goog.rays.Xray());",
            "alert(new DomHelper());"),
        lines(
            "goog.module('module');",
            "",
            "const Anteater = goog.require('goog.Anteater');",
            "const DomHelper = goog.require('goog.dom.DomHelper');",
            "const Xray = goog.require('goog.rays.Xray');",
            "",
            "alert(new Anteater());",
            "alert(new Xray());",
            "alert(new DomHelper());"));
  }

  @Test
  public void testMissingRequireInGoogModule() {
    assertChanges(
        lines(
            "goog.module('m');", //
            "",
            "alert(new a.b.C());"),
        lines(
            "goog.module('m');", //
            "const C = goog.require('a.b.C');",
            "",
            "alert(new C());"));
  }

  @Test
  public void testMissingRequireInGoogModuleTwice() {
    assertChanges(
        lines(
            "goog.module('m');", //
            "",
            "alert(new a.b.C());",
            "alert(new a.b.C());"),
        lines(
            "goog.module('m');",
            "const C = goog.require('a.b.C');",
            "",
            // TODO(tbreisacher): Can we make automatically switch both lines to use 'new C()'?
            "alert(new a.b.C());",
            "alert(new C());"));
  }

  @Test
  public void testMissingRequireInGoogModule_call() {
    assertChanges(
        lines(
            "goog.module('m');", //
            "",
            "alert(a.b.c());"),
        lines(
            "goog.module('m');", //
            "const b = goog.require('a.b');",
            "",
            "alert(b.c());"));
  }

  @Test
  public void testMissingRequireInGoogModule_extends() {
    assertChanges(
        lines(
            "goog.module('m');", //
            "",
            "class Cat extends world.util.Animal {}"),
        lines(
            "goog.module('m');",
            "const Animal = goog.require('world.util.Animal');",
            "",
            "class Cat extends Animal {}"));
  }

  @Test
  public void testMissingRequireInGoogModule_atExtends() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "/** @constructor @extends {world.util.Animal} */",
            "function Cat() {}"),
        lines(
            "goog.module('m');",
            "const Animal = goog.require('world.util.Animal');",
            "",
            // TODO(tbreisacher): Change this to "@extends {Animal}"
            "/** @constructor @extends {world.util.Animal} */",
            "function Cat() {}"));
  }

  @Test
  public void testBothFormsOfRequire() {
    assertChanges(
        lines(
            "goog.module('example');",
            "",
            "goog.require('foo.bar.SoyRenderer');",
            "const SoyRenderer = goog.require('foo.bar.SoyRenderer');",
            "",
            "function setUp() {",
            "  const soyService = new foo.bar.SoyRenderer();",
            "}",
            ""),
        lines(
            "goog.module('example');",
            "",
            "const SoyRenderer = goog.require('foo.bar.SoyRenderer');",
            "goog.require('foo.bar.SoyRenderer');",
            "",
            "function setUp() {",
            "  const soyService = new SoyRenderer();",
            "}",
            ""));
  }

  @Test
  public void testStandaloneVarDoesntCrashMissingRequire() {
    assertChanges(
        lines(
            "goog.module('m');", //
            "",
            "var x;",
            "",
            "class Cat extends goog.Animal {}"),
        lines(
            "goog.module('m');",
            "const Animal = goog.require('goog.Animal');",
            "",
            "var x;",
            "",
            "class Cat extends Animal {}"));
  }

  @Test
  public void testAddLhsToGoogRequire() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "goog.require('world.util.Animal');",
            "",
            "class Cat extends world.util.Animal {}"),
        lines(
            "goog.module('m');",
            "",
            "const Animal = goog.require('world.util.Animal');",
            "",
            "class Cat extends Animal {}"));
  }

  @Test
  public void testAddLhsToGoogRequire_new() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "goog.require('world.util.Animal');",
            "",
            "let cat = new world.util.Animal();"),
        lines(
            "goog.module('m');",
            "",
            "const Animal = goog.require('world.util.Animal');",
            "",
            "let cat = new Animal();"));
  }

  @Test
  public void testAddLhsToGoogRequire_getprop() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "goog.require('magical.factories');",
            "goog.require('world.util.AnimalType');",
            "",
            "let cat = magical.factories.createAnimal(world.util.AnimalType.CAT);"),
        lines(
            "goog.module('m');",
            "",
            "const factories = goog.require('magical.factories');",
            "const AnimalType = goog.require('world.util.AnimalType');",
            "",
            "let cat = factories.createAnimal(AnimalType.CAT);"));
  }

  @Test
  public void testAddLhsToGoogRequire_jsdoc() {
    // TODO(tbreisacher): Add "const Animal = " before the goog.require and change
    // world.util.Animal to Animal
    assertNoChanges(
        lines(
            "goog.module('m');",
            "",
            "goog.require('world.util.Animal');",
            "",
            "/** @type {!world.util.Animal} */",
            "var cat;"));
  }

  @Test
  public void testSwitchToShorthand_JSDoc1() {
    assertChanges(
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @constructor @implements {world.util.Animal} */",
            "function Cat() {}"),
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @constructor @implements {Animal} */",
            "function Cat() {}"));
  }

  @Test
  public void testSwitchToShorthand_JSDoc2() {
    assertChanges(
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @constructor @extends {world.util.Animal} */",
            "function Cat() {}"),
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @constructor @extends {Animal} */",
            "function Cat() {}"));
  }

  @Test
  public void testSwitchToShorthand_JSDoc3() {
    assertChanges(
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @type {!world.util.Animal} */",
            "var animal;"),
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @type {!Animal} */",
            "var animal;"));
  }

  @Test
  public void testSwitchToShorthand_JSDoc4() {
    assertChanges(
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @type {?world.util.Animal} */",
            "var animal;"),
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @type {?Animal} */",
            "var animal;"));
  }

  @Test
  public void testSwitchToShorthand_JSDoc5() {
    assertChanges(
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @type {?Array<!world.util.Animal>} */",
            "var animals;"),
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @type {?Array<!Animal>} */",
            "var animals;"));
  }

  @Test
  public void testSwitchToShorthand_JSDoc6() {
    assertChanges(
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @type {?Array<!world.util.Animal.Turtle>} */",
            "var turtles;"),
        lines(
            "goog.module('m');",
            "var Animal = goog.require('world.util.Animal');",
            "",
            "/** @type {?Array<!Animal.Turtle>} */",
            "var turtles;"));
  }

  @Test
  public void testSwitchToShorthand_JSDoc7() {
    assertChanges(
        lines(
            "goog.module('m');",
            "var AnimalAltName = goog.require('world.util.Animal');",
            "",
            "/** @type {?Array<!world.util.Animal.Turtle>} */",
            "var turtles;"),
        lines(
            "goog.module('m');",
            "var AnimalAltName = goog.require('world.util.Animal');",
            "",
            "/** @type {?Array<!AnimalAltName.Turtle>} */",
            "var turtles;"));
  }

  @Test
  public void testMissingRequireInGoogModule_atExtends_qname() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "/** @constructor @extends {world.util.Animal} */",
            "world.util.Cat = function() {};"),
        lines(
            "goog.module('m');",
            "const Animal = goog.require('world.util.Animal');",
            "",
            // TODO(tbreisacher): Change this to "@extends {Animal}"
            "/** @constructor @extends {world.util.Animal} */",
            "world.util.Cat = function() {};"));
  }

  @Test
  public void testMissingRequireInGoogModule_googString() {
    assertChanges(
        lines(
            "goog.module('m');", //
            "",
            "alert(goog.string.trim('   str    '));"),
        lines(
            "goog.module('m');",
            "const googString = goog.require('goog.string');",
            "",
            "alert(googString.trim('   str    '));"));
  }

  @Test
  public void testMissingRequireInGoogModule_googStructsMap() {
    assertChanges(
        lines(
            "goog.module('m');", //
            "",
            "alert(new goog.structs.Map());"),
        lines(
            "goog.module('m');",
            "const StructsMap = goog.require('goog.structs.Map');",
            "",
            "alert(new StructsMap());"));
  }

  @Test
  public void testMissingRequireInGoogModule_insertedInCorrectOrder() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "const A = goog.require('a.A');",
            "const C = goog.require('c.C');",
            "",
            "alert(new A(new x.B(new C())));"),
        lines(
            "goog.module('m');",
            "",
            // Requires are sorted by the short name, not the full namespace.
            "const A = goog.require('a.A');",
            "const B = goog.require('x.B');",
            "const C = goog.require('c.C');",
            "",
            "alert(new A(new B(new C())));"));
  }

  @Test
  public void testMissingRequireInGoogModule_alwaysInsertsConst() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "var A = goog.require('a.A');",
            "var C = goog.require('c.C');",
            "",
            "alert(new A(new x.B(new C())));"),
        lines(
            "goog.module('m');",
            "",
            "var A = goog.require('a.A');",
            "const B = goog.require('x.B');",
            "var C = goog.require('c.C');",
            "",
            "alert(new A(new B(new C())));"));
  }

  @Test
  public void testSortShorthandRequiresInGoogModule() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "var B = goog.require('x.B');",
            "var A = goog.require('a.A');",
            "var C = goog.require('c.C');",
            "",
            "alert(new A(new B(new C())));"),
        lines(
            "goog.module('m');",
            "",
            // Requires are sorted by the short name, not the full namespace.
            "var A = goog.require('a.A');",
            "var B = goog.require('x.B');",
            "var C = goog.require('c.C');",
            "",
            "alert(new A(new B(new C())));"));
  }

  @Test
  public void testUnsortedAndMissingLhs() {
    assertChanges(
        lines(
            "goog.module('foo');",
            "",
            "goog.require('example.controller');",
            "const Bar = goog.require('example.Bar');",
            "",
            "alert(example.controller.SOME_CONSTANT);",
            "alert(Bar.doThings);"),
        lines(
            "goog.module('foo');",
            "",
            "const Bar = goog.require('example.Bar');",
            "goog.require('example.controller');",
            "",
            "alert(example.controller.SOME_CONSTANT);",
            "alert(Bar.doThings);"));
  }

  @Test
  public void testShortRequireInGoogModule1() {
    assertChanges(
        lines(
            "goog.module('m');", //
            "",
            "var c = goog.require('a.b.c');",
            "",
            "alert(a.b.c);"),
        lines(
            "goog.module('m');", //
            "",
            "var c = goog.require('a.b.c');",
            "",
            "alert(c);"));
  }

  @Test
  public void testShortRequireInGoogModule2() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "var Classname = goog.require('a.b.Classname');",
            "",
            "alert(a.b.Classname.instance_.foo());"),
        lines(
            "goog.module('m');",
            "",
            "var Classname = goog.require('a.b.Classname');",
            "",
            "alert(Classname.instance_.foo());"));
  }

  @Test
  public void testShortRequireInGoogModule3() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "var Classname = goog.require('a.b.Classname');",
            "",
            "alert(a.b.Classname.INSTANCE_.foo());"),
        lines(
            "goog.module('m');",
            "",
            "var Classname = goog.require('a.b.Classname');",
            "",
            "alert(Classname.INSTANCE_.foo());"));
  }

  @Test
  public void testShortRequireInGoogModule4() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "var object = goog.require('goog.object');",
            "",
            "alert(goog.object.values({x:1}));"),
        lines(
            "goog.module('m');",
            "",
            "var object = goog.require('goog.object');",
            "",
            "alert(object.values({x:1}));"));
  }

  @Test
  public void testShortRequireInGoogModule5() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "var Widget = goog.require('goog.Widget');",
            "",
            "alert(new goog.Widget());"),
        lines(
            "goog.module('m');",
            "",
            "var Widget = goog.require('goog.Widget');",
            "",
            "alert(new Widget());"));
  }

  @Test
  public void testShortRequireInGoogModule6() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "var GoogWidget = goog.require('goog.Widget');",
            "",
            "alert(new goog.Widget());"),
        lines(
            "goog.module('m');",
            "",
            "var GoogWidget = goog.require('goog.Widget');",
            "",
            "alert(new GoogWidget());"));
  }

  /**
   * Here, if the short name weren't provided the suggested fix would use 'Table' for both,
   * but since there is a short name provided for each one, it uses those names.
   */
  @Test
  public void testShortRequireInGoogModule7() {
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "var CoffeeTable = goog.require('coffee.Table');",
            "var KitchenTable = goog.require('kitchen.Table');",
            "",
            "alert(new coffee.Table(), new kitchen.Table());"),
        lines(
            "goog.module('m');",
            "",
            "var CoffeeTable = goog.require('coffee.Table');",
            "var KitchenTable = goog.require('kitchen.Table');",
            "",
            "alert(new CoffeeTable(), new KitchenTable());"));
  }

  @Test
  public void testBug65602711a() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const {X} = goog.require('ns.abc.xyz');",
            "",
            "use(ns.abc.xyz.X);"),
        lines(
            "goog.module('x');", //
            "",
            "const {X} = goog.require('ns.abc.xyz');",
            "",
            "use(X);"));
  }

  @Test
  public void testBug65602711b() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const {X: X2} = goog.require('ns.abc.xyz');",
            "",
            "use(ns.abc.xyz.X);"),
        lines(
            "goog.module('x');",
            "",
            "const {X: X2} = goog.require('ns.abc.xyz');",
            "",
            "use(X2);"));
  }

  @Test
  public void testProvidesSorted1() {
    assertChanges(
        lines(
            "/** @fileoverview foo */",
            "",
            "",
            "goog.provide('b');",
            "goog.provide('a');",
            "goog.provide('c');",
            "",
            "",
            "alert(1);"),
        lines(
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
        lines(
            "goog.require('goog.object');",
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"),
        lines("goog.require('goog.string');", "", "alert(goog.string.parseInt('7'));"));
  }

  @Test
  public void testExtraRequireType() {
    assertChanges(
        lines(
            "goog.requireType('goog.events.Listenable');",
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"),
        lines(
            "goog.require('goog.string');", //
            "",
            "alert(goog.string.parseInt('7'));"));
  }

  @Test
  public void testExtraRequire_module() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const googString = goog.require('goog.string');",
            "const object = goog.require('goog.object');",
            "alert(googString.parseInt('7'));"),
        lines(
            "goog.module('x');",
            "",
            "const googString = goog.require('goog.string');",
            "alert(googString.parseInt('7'));"));
  }

  @Test
  public void testExtraRequireType_module() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const googString = goog.require('goog.string');",
            "const Listenable = goog.requireType('goog.events.Listenable');",
            "alert(googString.parseInt('7'));"),
        lines(
            "goog.module('x');",
            "",
            "const googString = goog.require('goog.string');",
            "alert(googString.parseInt('7'));"));
  }

  @Test
  public void testExtraRequire_destructuring_unusedInitialMember() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const {foo, bar} = goog.require('goog.util');",
            "",
            "alert(bar(7));"),
        lines(
            "goog.module('x');",
            "",
            "const {bar} = goog.require('goog.util');",
            "",
            "alert(bar(7));"));
  }

  @Test
  public void testExtraRequire_destructuring_unusedFinalMember() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const {foo, bar} = goog.require('goog.util');",
            "",
            "alert(foo(7));"),
        lines(
            "goog.module('x');",
            "",
            "const {foo} = goog.require('goog.util');",
            "",
            "alert(foo(7));"));
  }

  @Test
  public void testExtraRequire_destructuring_unusedMiddleMember() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const {foo, bar, qux} = goog.require('goog.util');",
            "",
            "alert(foo(qux(7)));"),
        lines(
            "goog.module('x');",
            "",
            "const {foo, qux} = goog.require('goog.util');",
            "",
            "alert(foo(qux(7)));"));
  }

  /** Because of overlapping replacements, it takes two runs to fully fix this case. */
  @Test
  public void testExtraRequire_destructuring_multipleUnusedMembers() {
    assertChanges(
        lines(
            "goog.module('x');", //
            "",
            "const {foo, bar, qux} = goog.require('goog.util');"),
        lines(
            "goog.module('x');", //
            "",
            "const {qux} = goog.require('goog.util');"));
  }

  @Test
  public void testExtraRequire_destructuring_allUnusedMembers() {
    assertChanges(
        lines(
            "goog.module('x');", //
            "",
            "const {qux} = goog.require('goog.util');"),
        lines(
            "goog.module('x');", //
            "",
            "const {} = goog.require('goog.util');"));
  }

  @Test
  public void testExtraRequire_destructuring_unusedShortnameMember() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const {foo: googUtilFoo, bar} = goog.require('goog.util');",
            "",
            "alert(bar(7));"),
        lines(
            "goog.module('x');",
            "",
            "const {bar} = goog.require('goog.util');",
            "",
            "alert(bar(7));"));
  }

  @Test
  public void testExtraRequire_destructuring_keepShortnameMember() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const {foo: googUtilFoo, bar} = goog.require('goog.util');",
            "",
            "alert(googUtilFoo(7));"),
        lines(
            "goog.module('x');",
            "",
            "const {foo: googUtilFoo} = goog.require('goog.util');",
            "",
            "alert(googUtilFoo(7));"));
  }

  @Test
  public void testExtraRequire_destructuring_onlyUnusedShortnameMember() {
    assertChanges(
        lines(
            "goog.module('x');", //
            "",
            "const {foo: googUtilFoo} = goog.require('goog.util');"),
        lines(
            "goog.module('x');", //
            "",
            "const {} = goog.require('goog.util');"));
  }

  @Test
  public void testExtraRequire_destructuring_noMembers() {
    assertChanges(
        lines(
            "goog.module('x');", //
            "",
            "const {} = goog.require('goog.util');"),
        lines(
            "goog.module('x');", //
            "",
            ""));
  }

  @Test
  public void testExtraRequireType_destructuring() {
    assertChanges(
        lines(
            "goog.module('x');",
            "",
            "const {foo: googUtilFoo, bar, baz: googUtilBaz, qux} = goog.requireType('goog.util');",
            "",
            "/** @type {!googUtilFoo} */ let x;",
            "/** @type {!qux} */ let x;"),
        lines(
            "goog.module('x');",
            "",
            "const {foo: googUtilFoo, qux} = goog.requireType('goog.util');",
            "",
            "/** @type {!googUtilFoo} */ let x;",
            "/** @type {!qux} */ let x;"));
  }

  @Test
  public void testExtraRequire_unsorted() {
    // There is also a warning because requires are not sorted. That one is not fixed because
    // the fix would conflict with the extra-require fix.
    assertChanges(
        lines(
            "goog.require('goog.string');",
            "goog.require('goog.object');",
            "goog.require('goog.dom');",
            "",
            "alert(goog.string.parseInt('7'));",
            "alert(goog.dom.createElement('div'));"),
        lines(
            "goog.require('goog.string');",
            "goog.require('goog.dom');",
            "",
            "alert(goog.string.parseInt('7'));",
            "alert(goog.dom.createElement('div'));"));
  }

  private void assertChanges(String originalCode, String expectedCode) {
    compiler.compile(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", originalCode)),
        options);
    ImmutableList<JSError> warningsAndErrors =
        ImmutableList.<JSError>builder()
            .addAll(compiler.getWarnings())
            .addAll(compiler.getErrors())
            .build();
    assertThat(warningsAndErrors).named("warnings/errors").isNotEmpty();
    Collection<SuggestedFix> fixes = errorManager.getAllFixes();
    assertThat(fixes).named("fixes").isNotEmpty();
    String newCode =
        ApplySuggestedFixes.applySuggestedFixesToCode(fixes, ImmutableMap.of("test", originalCode))
            .get("test");
    assertThat(newCode).isEqualTo(expectedCode);
  }

  private void assertChanges(String originalCode, String... expectedFixes) {
    compiler.compile(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", originalCode)),
        options);
    ImmutableList<JSError> warningsAndErrors =
        ImmutableList.<JSError>builder()
            .addAll(compiler.getWarnings())
            .addAll(compiler.getErrors())
            .build();
    assertThat(warningsAndErrors).named("warnings/errors").isNotEmpty();
    SuggestedFix[] fixes = errorManager.getAllFixes().toArray(new SuggestedFix[0]);
    assertThat(fixes).named("fixes").hasLength(expectedFixes.length);
    for (int i = 0; i < fixes.length; i++) {
      String newCode =
          ApplySuggestedFixes.applySuggestedFixesToCode(
                  ImmutableList.of(fixes[i]), ImmutableMap.of("test", originalCode))
              .get("test");
      assertThat(newCode).isEqualTo(expectedFixes[i]);
    }
  }

  protected void assertNoChanges(String originalCode) {
    compiler.compile(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", originalCode)),
        options);
    Collection<SuggestedFix> fixes = errorManager.getAllFixes();
    assertThat(fixes).isEmpty();
  }

  private String lines(String... lines) {
    return String.join("\n", lines);
  }
}
