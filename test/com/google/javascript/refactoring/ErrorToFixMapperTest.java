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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CheckLevel.ERROR;
import static com.google.javascript.jscomp.CheckLevel.OFF;
import static com.google.javascript.jscomp.CheckLevel.WARNING;
import static com.google.javascript.jscomp.parsing.Config.JsDocParsing.INCLUDE_ALL_COMMENTS;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;
import java.util.Collection;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// TODO(tjgq): Re-enable the disabled tests once CheckRequiresAndProvidesSorted is updated to use
// RequiresFixer. Currently, CheckRequiresAndProvidesSorted and ErrorToFixMapper sometimes disagree
// on whether a fix is required, which causes no fix to be suggested when it should.

/** Test case for {@link ErrorToFixMapper}. */
@RunWith(JUnit4.class)
public class ErrorToFixMapperTest {
  private FixingErrorManager errorManager;
  private CompilerOptions options;
  private Compiler compiler;
  private String preexistingCode;

  @Before
  public void setUp() {
    errorManager = new FixingErrorManager();
    compiler = new Compiler(errorManager);
    preexistingCode = "";
    compiler.disableThreads();
    errorManager.setCompiler(compiler);

    options = RefactoringDriver.getCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, WARNING);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, ERROR);
    options.setWarningLevel(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT, ERROR);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, WARNING);
    options.setWarningLevel(DiagnosticGroups.STRICTER_MISSING_REQUIRE, ERROR);
    options.setWarningLevel(DiagnosticGroups.STRICTER_MISSING_REQUIRE_TYPE, ERROR);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, ERROR);
    options.setWarningLevel(DiagnosticGroups.STRICT_MODULE_CHECKS, WARNING);
    options.setCodingConvention(new GoogleCodingConvention());
  }

  @AutoValue
  abstract static class ExpectedFix {
    /** Optional string describing the fix. */
    @Nullable
    abstract String description();
    /** What the code should look like after applying the fix. */
    abstract String fixedCode();

    static Builder builder() {
      return new AutoValue_ErrorToFixMapperTest_ExpectedFix.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder description(@Nullable String description);

      abstract Builder fixedCode(String fixedCode);

      abstract ExpectedFix build();
    }
  }

  @Test
  public void testMissingSuperCall() {
    assertExpectedFixes(
        lines(
            "class C {",
            "}",
            "class D extends C {",
            "  constructor() {", // Must have a super call here.
            "  }",
            "}",
            ""),
        ExpectedFix.builder()
            .fixedCode(
                lines(
                    "class C {",
                    "}",
                    "class D extends C {",
                    "  constructor() {",
                    "super();",
                    "  }",
                    "}",
                    ""))
            .build());
  }

  @Test
  public void testInvalidSuperCall() {
    assertExpectedFixes(
        lines(
            "class C {", //
            "  method() {}",
            "}",
            "class D extends C {",
            "  method() {",
            "    return super();", // super() constructor call invalid here
            "  }",
            "}",
            ""),
        ExpectedFix.builder()
            .description("Call 'super.method' instead")
            .fixedCode(
                lines(
                    "class C {", //
                    "  method() {}",
                    "}",
                    "class D extends C {",
                    "  method() {",
                    "    return super.method();",
                    "  }",
                    "}",
                    ""))
            .build());
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
    assertExpectedFixes(
        code,
        ExpectedFix.builder()
            .description("Remove debugger statement")
            .fixedCode(expectedCode)
            .build());
  }

  @Test
  public void testEmptyStatement1() {
    assertExpectedFixes(
        "var x;;",
        ExpectedFix.builder().description("Remove empty statement").fixedCode("var x;").build());
  }

  @Test
  public void testEmptyStatement2() {
    assertExpectedFixes(
        "var x;;\nvar y;",
        ExpectedFix.builder()
            .description("Remove empty statement")
            .fixedCode("var x;\nvar y;")
            .build());
  }

  @Test
  public void testEmptyStatement3() {
    assertExpectedFixes(
        "function f() {};\nf();",
        ExpectedFix.builder()
            .description("Remove empty statement")
            .fixedCode("function f() {}\nf();")
            .build());
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
  public void testMissingBangOnEnum() {
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, OFF);
    String prelude = "/** @enum {number} */ var Enum;\n";
    assertChanges(prelude + "/** @type {Enum} */ var o;", prelude + "/** @type {!Enum} */ var o;");
  }

  @Test
  public void testMissingBangOnTypedef() {
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, OFF);
    String prelude = "/** @typedef {number} */ var Num;\n";
    assertChanges(prelude + "/** @type {Num} */ var o;", prelude + "/** @type {!Num} */ var o;");
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
    assertExpectedFixes(
        code,
        ExpectedFix.builder()
            .description("Remove redundant declaration")
            .fixedCode(expectedCode)
            .build());
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
    assertExpectedFixes(
        "function f(x) { var x = 3; }",
        ExpectedFix.builder()
            .description("Convert redundant declaration to assignment")
            .fixedCode("function f(x) { x = 3; }")
            .build());
  }

  @Test
  public void testRedeclaration_params() {
    assertNoChanges("function f(x, x) {}");
  }

  @Test
  public void testEarlyReference() {
    String code = "if (x < 0) alert(1);\nvar x;";
    String expectedCode = "var x;\n" + code;
    assertExpectedFixes(
        code,
        ExpectedFix.builder()
            .description("Insert var declaration statement")
            .fixedCode(expectedCode)
            .build());
  }

  @Test
  public void testEarlyReferenceInFunction() {
    String code = "function f() {\n  if (x < 0) alert(1);\nvar x;\n}";
    String expectedCode = "function f() {\n  var x;\nif (x < 0) alert(1);\nvar x;\n}";
    assertExpectedFixes(
        code,
        ExpectedFix.builder()
            .description("Insert var declaration statement")
            .fixedCode(expectedCode)
            .build());
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
  public void testFixProvides_sort() {
    assertChanges(
        lines(
            "/** @fileoverview foo */",
            "",
            "goog.provide('b');",
            "goog.provide('a');",
            "goog.provide('c');",
            "",
            "alert(1);"),
        lines(
            "/** @fileoverview foo */",
            "",
            "goog.provide('a');",
            "goog.provide('b');",
            "goog.provide('c');",
            "",
            "alert(1);"));
  }

  @Test
  public void testFixProvides_deduplicate() {
    assertChanges(
        lines(
            "/** @fileoverview foo */",
            "",
            "goog.provide('a');",
            "goog.provide('b');",
            "goog.provide('a');",
            "",
            "alert(1);"),
        lines(
            "/** @fileoverview foo */",
            "",
            "goog.provide('a');",
            "goog.provide('b');",
            "",
            "alert(1);"));
  }

  @Test
  public void testFixProvides_alreadySorted() {
    assertNoChanges(
        lines(
            "/** @fileoverview foo */",
            "",
            "goog.provide('a');",
            "goog.provide('b');",
            "goog.provide('c');",
            "",
            "alert(1);"));
  }

  @Test
  public void testFixNonAliasedRequire() {
    assertChanges(
        lines(
            "/** @fileoverview foo */",
            "",
            "goog.module('m');",
            "",
            "goog.require('x');",
            useInCode("x")),
        lines(
            "/** @fileoverview foo */",
            "",
            "goog.module('m');",
            "",
            "const x = goog.require('x');",
            useInCode("x")));
  }

  @Test
  public void testFixNonAliasedRequireType() {
    assertChanges(
        lines(
            "/** @fileoverview foo */",
            "",
            "goog.module('m');",
            "",
            "goog.requireType('x');",
            useInType("x")),
        lines(
            "/** @fileoverview foo */",
            "",
            "goog.module('m');",
            "",
            "const x = goog.requireType('x');",
            useInType("x")));
  }

  @Test
  public void testFixRequires_sortStandaloneOnly() {
    assertChanges(
        fileWithImports(
            "goog.require('b');",
            "goog.requireType('d');",
            "goog.requireType('c');",
            "goog.forwardDeclare('f');",
            "goog.require('a');",
            "goog.forwardDeclare('e');",
            useInCode("a", "b"),
            useInType("c", "d", "e", "f")),
        fileWithImports(
            "goog.forwardDeclare('e');",
            "goog.forwardDeclare('f');",
            "goog.require('a');",
            "goog.require('b');",
            "goog.requireType('c');",
            "goog.requireType('d');",
            useInCode("a", "b"),
            useInType("c", "d", "e", "f")));
  }

  @Test
  public void testFixRequires_sortAllTypes() {
    assertChanges(
        fileWithImports(
            "goog.requireType('a');",
            "goog.require('b');",
            "const f = goog.require('f');",
            "const {d} = goog.require('d');",
            "const e = goog.requireType('e');",
            "const {c} = goog.requireType('c');",
            "goog.forwardDeclare('g');",
            useInCode("a", "d", "f"),
            useInType("b", "c", "e", "g")),
        fileWithImports(
            "const e = goog.requireType('e');",
            "const f = goog.require('f');",
            "const {c} = goog.requireType('c');",
            "const {d} = goog.require('d');",
            "goog.forwardDeclare('g');",
            "goog.require('b');",
            "goog.requireType('a');",
            useInCode("a", "d", "f"),
            useInType("b", "c", "e", "g")));
  }

  @Test
  public void testLineCommentDoesNotGetDeleted() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "goog.require('d');", //
            "// dummy", //
            "goog.require('b');", //
            useInCode("d", "b")), //
        fileWithImports(
            "// dummy", //
            "goog.require('b');", //
            "goog.require('d');", //
            useInCode("d", "b")));
  }

  @Test
  public void testBlockCommentDoesNotGetDeleted() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "goog.require('d');", //
            "/* dummy */", //
            "goog.require('b');", //
            useInCode("d", "b")),
        fileWithImports(
            "/* dummy */", //
            "goog.require('b');", //
            "goog.require('d');", //
            useInCode("d", "b")));
  }

  @Test
  public void testFirstBlockCommentDoesNotGetDeleted() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "/* dummy */", //
            "goog.require('d');", //
            "goog.require('b');", //
            useInCode("d", "b")),
        fileWithImports(
            "goog.require('b');", //
            "/* dummy */", //
            "goog.require('d');", //
            useInCode("d", "b")));
  }

  @Test
  public void testIndividualCommentsMoveCorrectly() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "// dummy 1", //
            "goog.require('d');", //
            "// dummy 2", //
            "goog.require('b');", //
            useInCode("d", "b")),
        fileWithImports(
            "// dummy 2", //
            "goog.require('b');", //
            "// dummy 1", //
            "goog.require('d');", //
            useInCode("d", "b")));
  }

  @Test
  public void testIndividualCommentsMoveCorrectly2() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "// dummy 1", //
            "goog.require('d');", //
            "// dummy 2", //
            "goog.require('b');", //
            "// dummy 3",
            "goog.require('a');",
            useInCode("d", "b", "a")),
        fileWithImports(
            "// dummy 3", //
            "goog.require('a');",
            "// dummy 2", //
            "goog.require('b');", //
            "// dummy 1", //
            "goog.require('d');", //
            useInCode("d", "b", "a")));
  }

  @Test
  public void testIndividualCommentsMoveCorrectly3() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "// dummy 1", //
            "goog.require('d');", //
            "// dummy 2", //
            "goog.require('b');", //
            "goog.require('a');",
            useInCode("d", "b", "a")),
        fileWithImports(
            "goog.require('a');",
            "// dummy 2", //
            "goog.require('b');", //
            "// dummy 1", //
            "goog.require('d');", //
            useInCode("d", "b", "a")));
  }

  @Test
  public void testIndividualCommentsMoveCorrectly4() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "// dummy 1", //
            "goog.require('d');", //
            "goog.require('b');", //
            "goog.require('a');",
            useInCode("d", "b", "a")),
        fileWithImports(
            "goog.require('a');",
            "goog.require('b');", //
            "// dummy 1", //
            "goog.require('d');", //
            useInCode("d", "b", "a")));
  }

  @Test
  public void testMultipleCommentsMoveCorrectly() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "// dummy 1", //
            "// dummy 2", //
            "goog.require('d');", //
            "goog.require('b');", //
            "goog.require('a');",
            useInCode("d", "b", "a")),
        fileWithImports(
            "goog.require('a');",
            "goog.require('b');", //
            "// dummy 1", //
            "// dummy 2", //
            "goog.require('d');", //
            useInCode("d", "b", "a")));
  }

  @Test
  public void testMultipleCommentsMoveCorrectly2() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "// dummy 1", //
            "\n", //
            "// dummy 2", //
            "goog.require('d');", //
            "goog.require('b');", //
            "goog.require('a');",
            useInCode("d", "b", "a")),
        fileWithImports(
            "goog.require('a');",
            "goog.require('b');", //
            "// dummy 1", //
            "\n", //
            "// dummy 2", //
            "goog.require('d');", //
            useInCode("d", "b", "a")));
  }

  @Test
  public void testFirstLineCommentDoesNotGetDeleted() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "// dummy",
            "goog.require('d');", //
            "goog.require('b');", //
            useInCode("d", "b")),
        fileWithImports(
            "goog.require('b');", //
            "// dummy", //
            "goog.require('d');", //
            useInCode("d", "b")));
  }

  @Test
  public void testBothJSDocAndNonJSDocCommentsTogether() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "/** JSDoc gets preserved*/",
            "// dummy",
            "goog.require('d');", //
            "goog.require('b');", //
            useInCode("d", "b")),
        fileWithImports(
            "goog.require('b');", //
            "// dummy",
            "/** JSDoc gets preserved*/",
            "goog.require('d');", //
            useInCode("d", "b")));
  }

  @Test
  public void testFileOverviewCommentsPreservedAfterSortingProvides() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        lines(
            "// Copyright 2011 The Closure Library Authors. All Rights Reserved.",
            "/**",
            " * @fileoverview Date/time formatting symbols for all locales.",
            " * @suppress {const} */",
            "// clang-format off",
            "goog.provide('goog.i18n.DateTimeSymbols_af');",
            "goog.provide('goog.i18n.DateTimeSymbols');",
            "goog.provide('goog.i18n.DateTimeSymbolsType');"),
        lines(
            "// Copyright 2011 The Closure Library Authors. All Rights Reserved.",
            "/**",
            " * @fileoverview Date/time formatting symbols for all locales.",
            " * @suppress {const} */",
            "// clang-format off",
            "goog.provide('goog.i18n.DateTimeSymbols');",
            "goog.provide('goog.i18n.DateTimeSymbolsType');",
            "goog.provide('goog.i18n.DateTimeSymbols_af');"));
  }

  @Test
  public void testFileOverviewCommentsPreservedAfterSortingProvides2() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        lines(
            "// clang-format off",
            "/**",
            " * @fileoverview Date/time formatting symbols for all locales.",
            " * @suppress {const} */",
            "goog.provide('goog.i18n.DateTimeSymbols_af');",
            "goog.provide('goog.i18n.DateTimeSymbols');",
            "goog.provide('goog.i18n.DateTimeSymbolsType');"),
        lines(
            "// clang-format off",
            "/**",
            " * @fileoverview Date/time formatting symbols for all locales.",
            " * @suppress {const} */",
            "goog.provide('goog.i18n.DateTimeSymbols');",
            "goog.provide('goog.i18n.DateTimeSymbolsType');",
            "goog.provide('goog.i18n.DateTimeSymbols_af');"));
  }

  @Test
  public void testBothJSDocAndNonJSDocCommentsTogether2() {
    options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
    assertChanges(
        fileWithImports(
            "// dummy",
            "/** JSDoc gets preserved*/",
            "goog.require('d');", //
            "goog.require('b');", //
            useInCode("d", "b")),
        fileWithImports(
            "goog.require('b');", //
            "// dummy",
            "/** JSDoc gets preserved*/",
            "goog.require('d');", //
            useInCode("d", "b")));
  }

  @Test
  public void testFixRequires_deduplicate_standalone() {
    assertChanges(
        fileWithImports(
            "goog.require('a');",
            "goog.require('a');",
            "goog.require('b');",
            "goog.requireType('b');",
            "goog.requireType('c');",
            "goog.requireType('c');",
            "goog.forwardDeclare('b');",
            "goog.forwardDeclare('c');",
            "goog.forwardDeclare('d');",
            useInCode("a", "b"),
            useInType("c", "d")),
        fileWithImports(
            "goog.forwardDeclare('d');",
            "goog.require('a');",
            "goog.require('b');",
            "goog.requireType('c');",
            useInCode("a", "b"),
            useInType("c", "d")));
  }

  @Test
  public void testFixRequires_preserveMultipleAliases() {
    // a3 changes from goog.requireType to goog.require because we enforce all imports for the same
    // namespace to have the same strength when rewriting.
    assertChanges(
        fileWithImports(
            "const a3 = goog.requireType('a');",
            "const a2 = goog.require('a');",
            "const a1 = goog.require('a');",
            useInCode("a1", "a2"),
            useInType("a3")),
        fileWithImports(
            "const a1 = goog.require('a');",
            "const a2 = goog.require('a');",
            "const a3 = goog.require('a');",
            useInCode("a1", "a2"),
            useInType("a3")));
  }

  @Test
  public void testFixRequires_mergeDestructuring() {
    assertChanges(
        fileWithImports(
            "const {c, a: a2} = goog.require('a');",
            "const {b, a: a1} = goog.require('a');",
            "const {a} = goog.require('a');",
            "const {} = goog.require('a');",
            useInCode("a", "a1", "a2", "b", "c")),
        fileWithImports(
            "const {a, a: a1, a: a2, b, c} = goog.require('a');",
            useInCode("a", "a1", "a2", "b", "c")));
  }

  @Test
  public void testFixRequires_mergeDestructuring_multiplePrimitives() {
    assertChanges(
        fileWithImports(
            "const {c, a} = goog.require('a');",
            "const {b, a} = goog.requireType('a');",
            useInCode("a", "c"),
            useInType("a", "b")),
        fileWithImports(
            "const {a, b, c} = goog.require('a');", useInCode("a", "c"), useInType("a", "b")));
  }

  @Test
  public void testFixRequires_nonAliasedRequire() {
    assertChanges(
        fileWithImports("goog.require('a');", useInCode("a")),
        fileWithImports("const a = goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_mutlipleFixesSpecifySameRequire() {
    assertChanges(
        fileWithImports(
            "goog.require('a.b');", //
            useInCode("a.b", "a.b", "a.b")),
        fileWithImports(
            "const b = goog.require('a.b');", //
            useInCode("b", "b", "b")));
  }

  @Ignore
  @Test
  public void testFixRequires_emptyDestructuring_alone() {
    // It would be nice to dedeuplicate the destructuring require with the aliased require,
    // but it's not clear that this pattern is common enough to warrant special casing
    assertChanges(
        fileWithImports("const {} = goog.require('a');", useInCode("a")),
        fileWithImports("goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_emptyDestructuringStandaloneBySamePrimitive() {
    assertChanges(
        fileWithImports("const {} = goog.require('a');", "goog.require('a');", useInCode("a")),
        fileWithImports("goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_emptyDestructuringStandaloneByStrongerPrimitive() {
    assertChanges(
        fileWithImports("const {} = goog.requireType('a');", "goog.require('a');", useInCode("a")),
        fileWithImports("goog.require('a');", useInCode("a")));
  }

  @Ignore
  @Test
  public void testFixRequires_emptyDestructuringStandaloneByWeakerPrimitive() {
    // It would be nice to dedeuplicate the destructuring require with the aliased require,
    // but it's not clear that this pattern is common enough to warrant special casing
    assertChanges(
        fileWithImports("const {} = goog.require('a');", "goog.requireType('a');", useInCode("a")),
        fileWithImports("goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_emptyDestructuringAliasedBySamePrimitive() {
    assertChanges(
        fileWithImports(
            "const {} = goog.require('a');", "const a = goog.require('a');", useInCode("a")),
        fileWithImports("const a = goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_emptyDestructuringAliasedByStrongerPrimitive() {
    assertChanges(
        fileWithImports(
            "const {} = goog.requireType('a');", "const a = goog.require('a');", useInCode("a")),
        fileWithImports("const a = goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_emptyDestructuringAliasedByWeakerPrimitive() {
    assertChanges(
        fileWithImports(
            "const {} = goog.require('a');", "const a = goog.requireType('a');", useInCode("a")),
        fileWithImports("const a = goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_aliasPreservedWhenDestructuring() {
    assertNoChanges(
        fileWithImports(
            "const a = goog.require('a');",
            "const b = goog.require('b');",
            "const {a1, a2} = goog.require('a');",
            "const {b1, b2} = goog.require('b');",
            "",
            useInCode("a1", "a2", "b", "b1", "b2"),
            useInType("a")));
  }

  @Test
  public void attachesInlineJsDocToParam() {
    assertNoChanges(
        " class C {\n" + "  inlineJSDocWithDefault(/** boolean= */ isSomething) {}\n" + "}\n");
  }

  @Test
  public void attachesInlineJsDocToDefaultParam() {
    assertNoChanges(
        " class C {\n"
            + "  inlineJSDocWithDefault(/** boolean= */ isSomething = true) {}\n"
            + "}\n");
  }

  @Test
  public void testFixRequires_standaloneAliasedBySamePrimitive() {
    assertChanges(
        fileWithImports("const a = goog.require('a');", "goog.require('a');", useInCode("a")),
        fileWithImports("const a = goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_standaloneAliasedByStrongerPrimitive() {
    assertChanges(
        fileWithImports("const a = goog.require('a');", "goog.requireType('a');", useInCode("a")),
        fileWithImports("const a = goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_standaloneAliasedByWeakerPrimitive() {
    assertChanges(
        fileWithImports("const a = goog.requireType('a');", "goog.require('a');", useInCode("a")),
        fileWithImports("const a = goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_standaloneDestructuredBySamePrimitive() {
    assertChanges(
        fileWithImports("const {a} = goog.require('a');", "goog.require('a');", useInCode("a")),
        fileWithImports("const {a} = goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_standaloneDestructuredByStrongerPrimitive() {
    assertChanges(
        fileWithImports("const {a} = goog.require('a');", "goog.requireType('a');", useInCode("a")),
        fileWithImports("const {a} = goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_standaloneDestructuredByWeakerPrimitive() {
    assertChanges(
        fileWithImports("const {a} = goog.requireType('a');", "goog.require('a');", useInCode("a")),
        fileWithImports("const {a} = goog.require('a');", useInCode("a")));
  }

  @Test
  public void testFixRequires_varAndLetBecomeConstIfUnsorted() {
    assertChanges(
        fileWithImports(
            "var b = goog.require('b');", "let a = goog.require('a');", useInCode("a", "b")),
        fileWithImports(
            "const a = goog.require('a');", "const b = goog.require('b');", useInCode("a", "b")));
  }

  @Test
  public void testFixRequires_varAndLetDoNotBecomeConstIfAlreadySorted() {
    // TODO(tjgq): Consider rewriting to const even when already sorted.
    assertNoChanges(
        fileWithImports(
            "let a = goog.require('a');", "var b = goog.require('b');", useInCode("a", "b")));
  }

  @Test
  public void testFixRequires_preserveJsDoc_whenSorting() {
    assertChanges(
        fileWithImports(
            "const c = goog.require('c');",
            "/**",
            " * @suppress {extraRequire} Because I said so.",
            " */",
            "const b = goog.require('b');",
            "const a = goog.require('a');",
            useInCode("a", "b", "c")),
        fileWithImports(
            "const a = goog.require('a');",
            "/**",
            " * @suppress {extraRequire} Because I said so.",
            " */",
            "const b = goog.require('b');",
            "const c = goog.require('c');",
            useInCode("a", "b", "c")));
  }

  @Test
  public void testFixRequires_preserveJsDoc_whenMergingStandalone() {
    assertChanges(
        fileWithImports(
            "/**",
            " * @suppress {extraRequire} Because I said so.",
            " */",
            "goog.require('a');",
            "goog.requireType('a');",
            useInCode("a")),
        fileWithImports(
            "/**",
            " * @suppress {extraRequire} Because I said so.",
            " */",
            "goog.require('a');",
            useInCode("a")));
  }

  @Test
  public void testFixRequires_preserveJsDoc_whenMergingDestructures_single() {
    assertChanges(
        fileWithImports(
            "/**",
            " * @suppress {extraRequire} Because I said so.",
            " */",
            "const {b} = goog.require('a');",
            "const {c} = goog.require('a');",
            useInCode("b", "c")),
        fileWithImports(
            "/**",
            " * @suppress {extraRequire} Because I said so.",
            " */",
            "const {b, c} = goog.require('a');",
            useInCode("b", "c")));
  }

  @Test
  public void testFixRequires_preserveJsDoc_whenMergingDestructures_multiple() {
    // TODO(tjgq): Consider merging multiple @suppress annotations into a single comment.
    assertChanges(
        fileWithImports(
            "/**",
            " * @suppress {extraRequire} Because I said so.",
            " */",
            "const {b} = goog.require('a');",
            "/**",
            " * @suppress {extraRequire} Because I rule.",
            " */",
            "const {c} = goog.require('a');",
            useInCode("b", "c")),
        fileWithImports(
            "/**",
            " * @suppress {extraRequire} Because I said so.",
            " */",
            "/**",
            " * @suppress {extraRequire} Because I rule.",
            " */",
            "const {b, c} = goog.require('a');",
            useInCode("b", "c")));
  }

  @Test
  public void testFixRequires_veryLongNames() {
    assertNoChanges(
        fileWithImports(
            "const"
                + " veryLongIdentifierSoLongThatItGoesPastThe80CharactersLimitAndYetWeShouldNotLineBreak"
                + " = goog.require('b');",
            "const"
                + " {anotherVeryLongIdentifierSoLongThatItGoesPastThe80CharactersLimitAndYetWeShouldNotLineBreak}"
                + " = goog.require('a');",
            "",
            useInCode(
                "veryLongIdentifierSoLongThatItGoesPastThe80CharactersLimitAndYetWeShouldNotLineBreak",
                "anotherVeryLongIdentifierSoLongThatItGoesPastThe80CharactersLimitAndYetWeShouldNotLineBreak")));
  }

  @Test
  public void testFixRequires_veryLongNames_whenMergingDestructures() {
    assertChanges(
        fileWithImports(
            "const {veryLongSymbolThatMapsTo: veryLongLocalNameForIt} = goog.require('a');",
            "const {anotherVeryLongSymbolThatMapsTo: veryLongLocalNameForItAlso} ="
                + " goog.require('a');",
            useInCode("veryLongLocalNameForIt", "veryLongLocalNameForItAlso")),
        fileWithImports(
            "const {anotherVeryLongSymbolThatMapsTo: veryLongLocalNameForItAlso,"
                + " veryLongSymbolThatMapsTo: veryLongLocalNameForIt} = goog.require('a');",
            useInCode("veryLongLocalNameForIt", "veryLongLocalNameForItAlso")));
  }

  @Test
  public void testFixRequires_noRequires() {
    assertNoChanges(fileWithImports());
  }

  @Test
  @Ignore
  public void testMissingRequire_inJSDoc_withWhitespace() {
    // TODO(b/159336400): Fix this test so the fix is valid
    preexistingCode = "goog.provide('some.really.very.long.namespace.SuperInt');";
    assertExpectedFixes(
        lines(
            "goog.module('m');",
            "",
            "/** @interface @implements {some.really.very.long.",
            "                            namespace.SuperInt} */",
            "class Bar {}"),
        ExpectedFix.builder()
            .fixedCode(
                lines(
                    "goog.module('m');",
                    "const SuperInt = goog.require('some.really.very.long.namespace.SuperInt');",
                    "",
                    "/** @interface @implements {SuperInt} */",
                    "class Bar {}"))
            .build());
  }

  @Test
  public void testMissingRequire_unsorted1() {
    // Both the fix for requires being unsorted, and the fix for the missing require, are applied.
    // However, the end result is still out of order.
    preexistingCode = "goog.provide('goog.dom.DomHelper');";
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
    preexistingCode = "goog.provide('goog.rays.Xray');";
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
    preexistingCode = "goog.provide('a.b.C');";
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
    preexistingCode = "goog.provide('a.b.C');";
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
            "alert(new C());",
            "alert(new C());"));
  }

  @Test
  public void testMissingRequireInGoogModule_call() {
    preexistingCode = "goog.provide('a.b');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
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
            "/** @constructor @extends {Animal} */",
            "function Cat() {}"));
  }

  @Test
  public void testStandaloneVarDoesntCrashMissingRequire() {
    preexistingCode = "goog.provide('goog.Animal');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
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
  public void testAddLhsToGoogRequire_conflictingName() {
    preexistingCode = "goog.provide('world.util.Animal');";
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "goog.require('world.util.Animal');",
            "",
            "const Animal = null;",
            "",
            "class Cat extends world.util.Animal {}"),
        lines(
            "goog.module('m');",
            "",
            "const UtilAnimal = goog.require('world.util.Animal');",
            "",
            "const Animal = null;",
            "",
            "class Cat extends UtilAnimal {}"));
  }

  @Test
  public void testAddLhsToGoogRequire_conflictingName_fromOtherSuggestion() {
    preexistingCode = "goog.provide('world.util.Animal'); goog.provide('rara.exotic.Animal');";
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "goog.require('rare.exotic.Animal');",
            "goog.require('world.util.Animal');",
            "",
            "/** @implements {rare.exotic.Animal} */",
            "class Cat extends world.util.Animal {}"),
        lines(
            "goog.module('m');",
            "",
            "const ExoticAnimal = goog.require('rare.exotic.Animal');",
            "const Animal = goog.require('world.util.Animal');",
            "",
            "/** @implements {ExoticAnimal} */",
            "class Cat extends Animal {}"));
  }

  @Test
  public void testAddLhsToGoogRequire_new() {
    preexistingCode = "goog.provide('world.util.Animal');";
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
    preexistingCode = "goog.provide('magical.factories'); goog.provide('world.util.Animal');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
    assertChanges(
        lines(
            "goog.module('m');",
            "",
            "goog.require('world.util.Animal');",
            "",
            "/** @type {!world.util.Animal} */",
            "var cat;"),
        lines(
            "goog.module('m');",
            "",
            "const Animal = goog.require('world.util.Animal');",
            "",
            "/** @type {!Animal} */",
            "var cat;"));
  }

  @Test
  public void testSwitchToShorthand_JSDoc1() {
    preexistingCode = "goog.provide('world.util.Animal');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
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
    preexistingCode = "goog.provide('world.util.Animal');";
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
            "/** @constructor @extends {Animal} */",
            "world.util.Cat = function() {};"));
  }

  @Test
  public void testMissingRequireInGoogModule_googString() {
    preexistingCode = "goog.provide('goog.string');";
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
    preexistingCode = "goog.provide('goog.structs.Map');";
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
    preexistingCode = "goog.provide('x.B');";
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
    preexistingCode = "goog.provide('x.B');";
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
  public void testExtraRequire() {
    assertExpectedFixes(
        lines(
            "goog.require('goog.object');",
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"),
        ExpectedFix.builder()
            .description("Delete extra require")
            .fixedCode(
                lines(
                    "goog.require('goog.string');", //
                    "",
                    "alert(goog.string.parseInt('7'));"))
            .build());
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
    assertExpectedFixes(
        lines(
            "goog.module('x');",
            "",
            "const {bar, foo: googUtilFoo} = goog.require('goog.util');",
            "",
            "alert(bar(7));"),
        ExpectedFix.builder()
            .description("Delete unused symbol")
            .fixedCode(
                lines(
                    "goog.module('x');",
                    "",
                    "const {bar} = goog.require('goog.util');",
                    "",
                    "alert(bar(7));"))
            .build());
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

  // TODO(tjgq): Make this not crash on ClosureRewriteModule#updateGoogRequire.
  @Ignore
  @Test
  public void testNoCrashOnInvalidMultiRequireStatement() {
    assertNoChanges(
        fileWithImports(
            "const a = goog.require('a'), b = goog.require('b');", useInCode("a", "b")));
  }

  @Test
  public void testConstantCaseName_let() {
    assertExpectedFixes(
        "goog.module('m'); let CONSTANT_CASE = 'value';",
        ExpectedFix.builder()
            .description("Make explicitly constant")
            .fixedCode("goog.module('m'); const CONSTANT_CASE = 'value';")
            .build());
  }

  @Test
  public void testConstantCaseName_var() {
    assertExpectedFixes(
        lines(
            "goog.module('m');", //
            "var CONSTANT_CASE = 'value';"),
        ExpectedFix.builder()
            .description("Make explicitly constant")
            .fixedCode(
                lines(
                    "goog.module('m');", //
                    "/** @const */",
                    "var CONSTANT_CASE = 'value';"))
            .build());
  }

  @Test
  public void testConstantCaseName_varWithExistingJSDoc() {
    assertNoChanges(
        "goog.module('m'); /** @type {string} Some description */ var CONSTANT_CASE = 'value';");
  }

  private String fileWithImports(String... imports) {
    return lines(
        lines("/*", " * @fileoverview", " */", "goog.module('x');", ""),
        lines(imports),
        lines("", "module.exports = function() {};"));
  }

  private String useInCode(String... names) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    for (String name : names) {
      sb.append("use(").append(name).append(");\n");
    }
    return sb.toString();
  }

  private String useInType(String... names) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    for (String name : names) {
      sb.append("/** @type {!").append(name).append("} */ var _").append(name).append(";\n");
    }
    return sb.toString();
  }

  private void compileExpectingAtLeastOneWarningOrError(String originalCode) {
    compileExpectingAtLeastOneWarningOrError(getInputMap(originalCode));
  }

  private void compileExpectingAtLeastOneWarningOrError(ImmutableMap<String, String> inputMap) {
    ImmutableList<SourceFile> inputs =
        inputMap.entrySet().stream()
            .map(e -> SourceFile.fromCode(e.getKey(), e.getValue()))
            .collect(toImmutableList());
    compiler.compile(
        ImmutableList.<SourceFile>of(), // Externs
        inputs,
        options);
    ImmutableList<JSError> warningsAndErrors =
        ImmutableList.<JSError>builder()
            .addAll(compiler.getWarnings())
            .addAll(compiler.getErrors())
            .build();
    assertWithMessage("warnings/errors").that(warningsAndErrors).isNotEmpty();
  }

  /** @deprecated Use assertExpectedFixes() for new cases or when amending existing ones */
  @Deprecated
  private void assertChanges(String originalCode, String expectedCode) {
    compileExpectingAtLeastOneWarningOrError(originalCode);
    Collection<SuggestedFix> fixes = errorManager.getAllFixes();
    assertWithMessage("fixes").that(fixes).isNotEmpty();
    String newCode =
        ApplySuggestedFixes.applySuggestedFixesToCode(fixes, getInputMap(originalCode)).get("test");
    assertThat(newCode).isEqualTo(expectedCode);
  }

  /** @deprecated Use assertExpectedFixes() for new cases or when amending existing ones */
  @Deprecated
  private void assertChanges(String originalCode, String... expectedFixes) {
    compileExpectingAtLeastOneWarningOrError(originalCode);
    SuggestedFix[] fixes = errorManager.getAllFixes().toArray(new SuggestedFix[0]);
    assertWithMessage("fixes").that(fixes).hasLength(expectedFixes.length);
    for (int i = 0; i < fixes.length; i++) {
      String newCode =
          ApplySuggestedFixes.applySuggestedFixesToCode(
                  ImmutableList.of(fixes[i]), getInputMap(originalCode))
              .get("test");
      assertThat(newCode).isEqualTo(expectedFixes[i]);
    }
  }

  private void assertExpectedFixes(String originalCode, ExpectedFix... expectedFixes) {
    compileExpectingAtLeastOneWarningOrError(originalCode);
    SuggestedFix[] fixes = errorManager.getAllFixes().toArray(new SuggestedFix[0]);
    assertWithMessage("Unexpected number of fixes").that(fixes).hasLength(expectedFixes.length);
    for (int i = 0; i < fixes.length; i++) {
      ExpectedFix expectedFix = expectedFixes[i];
      SuggestedFix actualFix = fixes[i];
      assertWithMessage("Actual fix[" + i + "]: " + actualFix)
          .that(actualFix.getDescription())
          .isEqualTo(expectedFix.description());
      String newCode =
          ApplySuggestedFixes.applySuggestedFixesToCode(
                  ImmutableList.of(fixes[i]), getInputMap(originalCode))
              .get("test");
      assertThat(newCode).isEqualTo(expectedFix.fixedCode());
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

  private ImmutableMap<String, String> getInputMap(String originalCode) {
    return ImmutableMap.of("preexistingCode", preexistingCode, "test", originalCode);
  }

  private String lines(String... lines) {
    return String.join("\n", lines);
  }
}
