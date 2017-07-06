/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.VariableReferenceCheck.DECLARATION_NOT_DIRECTLY_IN_BLOCK;
import static com.google.javascript.jscomp.VariableReferenceCheck.EARLY_REFERENCE;
import static com.google.javascript.jscomp.VariableReferenceCheck.EARLY_REFERENCE_ERROR;
import static com.google.javascript.jscomp.VariableReferenceCheck.REASSIGNED_CONSTANT;
import static com.google.javascript.jscomp.VariableReferenceCheck.REDECLARED_VARIABLE;
import static com.google.javascript.jscomp.VariableReferenceCheck.REDECLARED_VARIABLE_ERROR;
import static com.google.javascript.jscomp.VariableReferenceCheck.UNUSED_LOCAL_ASSIGNMENT;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Test that warnings are generated in appropriate cases and appropriate
 * cases only by VariableReferenceCheck
 *
 */
public final class VariableReferenceCheckTest extends CompilerTestCase {

  private static final String LET_RUN =
      "let a = 1; let b = 2; let c = a + b, d = c;";

  private static final String VARIABLE_RUN =
      "var a = 1; var b = 2; var c = a + b, d = c;";

  private boolean enableUnusedLocalAssignmentCheck;

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    if (enableUnusedLocalAssignmentCheck) {
      options.setWarningLevel(DiagnosticGroups.UNUSED_LOCAL_VARIABLE, CheckLevel.WARNING);
    }
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    // Treats bad reads as errors, and reports bad write warnings.
    return new VariableReferenceCheck(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    enableUnusedLocalAssignmentCheck = false;
  }

  public void testDoubleTryCatch() {
    testSame(
        LINE_JOINER.join(
            "function g() {",
            "  return f;",
            "",
            "  function f() {",
            "    try {",
            "    } catch (e) {",
            "      alert(e);",
            "    }",
            "    try {",
            "    } catch (e) {",
            "      alert(e);",
            "    }",
            "  }",
            "}"));
  }

  public void testDoubleTryCatch_withES6Modules() {
    testSame(
        LINE_JOINER.join(
            "export",
            "function g() {",
            "  return f;",
            "",
            "  function f() {",
            "    try {",
            "    } catch (e) {",
            "      alert(e);",
            "    }",
            "    try {",
            "    } catch (e) {",
            "      alert(e);",
            "    }",
            "  }",
            "}"));
  }

  public void testCorrectCode() {
    assertNoWarning("function foo(d) { (function() { d.foo(); }); d.bar(); } ");
    assertNoWarning("function foo() { bar(); } function bar() { foo(); } ");
    assertNoWarning("function f(d) { d = 3; }");
    assertNoWarning(VARIABLE_RUN);
    assertNoWarning("if (a) { var x; }");
    assertNoWarning("function f() { " + VARIABLE_RUN + "}");

    assertNoWarningEs6(LET_RUN);
    assertNoWarningEs6("function f() { " + LET_RUN + "}");
    assertNoWarningEs6("try { let e; } catch (e) { let x; }");
  }

  public void testCorrectCode_withES6Modules() {
    assertNoWarning("export function foo(d) { (function() { d.foo(); }); d.bar(); } ");
  }

  public void testCorrectShadowing() {
    assertNoWarning(VARIABLE_RUN + "function f() { " + VARIABLE_RUN + "}");
  }

  public void testCorrectShadowing_withES6Modules() {
    assertNoWarning(VARIABLE_RUN + "export function f() { " + VARIABLE_RUN + "}");
  }

  public void testCorrectRedeclare() {
    assertNoWarning(
        "function f() { if (1) { var a = 2; } else { var a = 3; } }");
  }

  public void testCorrectRedeclare_withES6Modules() {
    assertNoWarning("export function f() { if (1) { var a = 2; } else { var a = 3; } }");
  }

  public void testCorrectRecursion() {
    assertNoWarning("function f() { var x = function() { x(); }; }");
  }

  public void testCorrectRecursion_withES6Modules() {
    assertNoWarning("export function f() { var x = function() { x(); }; }");
  }

  public void testCorrectCatch() {
    assertNoWarning("function f() { try { var x = 2; } catch (x) {} }");
    assertNoWarning("function f(e) { e = 3; try {} catch (e) {} }");
  }

  public void testCorrectCatch_withES6Modules() {
    assertNoWarning("export function f() { try { var x = 2; } catch (x) {} }");
  }

  public void testRedeclare() {
    // Only test local scope since global scope is covered elsewhere
    assertRedeclare("function f() { var a = 2; var a = 3; }");
    assertRedeclare("function f(a) { var a = 2; }");
    assertRedeclare("function f(a) { if (!a) var a = 6; }");
    // NOTE: We decided to not give warnings to the following cases. The function won't be
    // overwritten at runtime anyway.
    assertNoWarning("function f() { var f = 1; }");
    assertNoWarningEs6("function f() { let f = 1; }");
  }

  public void testRedeclare_withES6Modules() {
    assertRedeclare("export function f() { var a = 2; var a = 3; }");
    assertNoWarningEs6("export function f() { let f = 1; }");
  }

  public void testIssue166a() {
    testError(
        "try { throw 1 } catch(e) { /** @suppress {duplicate} */ var e=2 }",
        REDECLARED_VARIABLE_ERROR);
  }

  public void testIssue166b() {
    testError(
        "function a() { try { throw 1 } catch(e) { /** @suppress {duplicate} */ var e=2 } };",
        REDECLARED_VARIABLE_ERROR);
  }

  public void testIssue166b_withES6Modules() {
    testError(
        LINE_JOINER.join(
            "export function a() {",
            "  try {",
            "    throw 1",
            "  } catch (e) {",
            "      /** @suppress {duplicate} */",
            "      var e = 2",
            "  }",
            "};"),
        REDECLARED_VARIABLE_ERROR);
  }

  public void testIssue166c() {
    testError(
        "var e = 0; try { throw 1 } catch(e) { /** @suppress {duplicate} */ var e=2 }",
        REDECLARED_VARIABLE_ERROR);
  }

  public void testIssue166d() {
    testError(
        LINE_JOINER.join(
            "function a() {",
            "  var e = 0; try { throw 1 } catch(e) {",
            "    /** @suppress {duplicate} */ var e = 2;",
            "  }",
            "};"),
        REDECLARED_VARIABLE_ERROR);
  }

  public void testIssue166e() {
    testSame("var e = 2; try { throw 1 } catch(e) {}");
  }

  public void testIssue166e_withES6Modules() {
    testSame("export var e = 2; try { throw 1 } catch(e) {}");
  }

  public void testIssue166f() {
    testSame(
        LINE_JOINER.join(
            "function a() {",
            "  var e = 2;",
            "  try { throw 1 } catch(e) {}",
            "}"));
  }

  public void testEarlyReference() {
    assertUndeclared("function f() { a = 2; var a = 3; }");
  }

  public void testEarlyReference_withES6Modules() {
    assertUndeclared("export function f() { a = 2; var a = 3; }");
  }

  public void testCorrectEarlyReference() {
    assertNoWarning("var goog = goog || {}");
    assertNoWarning("var google = google || window['google'] || {}");
    assertNoWarning("function f() { a = 2; } var a = 2;");
  }

  public void testCorrectEarlyReference_withES6Modules() {
    assertNoWarning("export function f() { a = 2; } var a = 2;");
  }

  public void testUnreferencedBleedingFunction() {
    assertNoWarning("var x = function y() {}");
    assertNoWarning("var x = function y() {}; var y = 1;");
  }

  public void testUnreferencedBleedingFunction_withES6Modules() {
    assertNoWarning("export var x = function y() {}");
  }

  public void testReferencedBleedingFunction() {
    assertNoWarning("var x = function y() { return y(); }");
  }

  public void testReferencedBleedingFunction_withES6Modules() {
    assertNoWarning("export var x = function y() { return y(); }");
  }

  public void testDoubleDeclaration() {
    assertRedeclare("function x(y) { if (true) { var y; } }");
  }

  public void testDoubleDeclaration2() {
    assertRedeclare("function x() { var y; if (true) { var y; } }");
  }

  public void testDoubleDeclaration_withES6Modules() {
    assertRedeclare("export function x(y) { if (true) { var y; } }");
  }

  public void testHoistedFunction1() {
    assertNoWarning("f(); function f() {}");
  }

  public void testHoistedFunction2() {
    assertNoWarning("function g() { f(); function f() {} }");
  }

  public void testHoistedFunction_withES6Modules() {
    assertNoWarning("export function g() { f(); function f() {} }");
  }

  public void testNonHoistedFunction() {
    assertUndeclaredEs6("if (true) { f(); function f() {} }");
  }

  public void testNonHoistedFunction2() {
    assertNoWarningEs6("if (false) { function f() {} f(); }");
  }

  public void testNonHoistedFunction3() {
    assertNoWarningEs6("function g() { if (false) { function f() {} f(); }}");
  }

  public void testNonHoistedFunction4() {
    assertAmbiguousEs6("if (false) { function f() {} }  f();");
  }

  public void testNonHoistedFunction5() {
    assertAmbiguousEs6("function g() { if (false) { function f() {} }  f(); }");
  }

  public void testNonHoistedFunction6() {
    assertUndeclaredEs6("if (false) { f(); function f() {} }");
  }

  public void testNonHoistedFunction7() {
    assertUndeclaredEs6("function g() { if (false) { f(); function f() {} }}");
  }

  public void testNonHoistedFunction_withES6Modules() {
    assertUndeclaredEs6("export function g() { if (false) { f(); function f() {} }}");
  }

  public void testNonHoistedRecursiveFunction1() {
    assertNoWarningEs6("if (false) { function f() { f(); }}");
  }

  public void testNonHoistedRecursiveFunction2() {
    assertNoWarningEs6("function g() { if (false) { function f() { f(); }}}");
  }

  public void testNonHoistedRecursiveFunction3() {
    assertNoWarningEs6("function g() { if (false) { function f() { f(); g(); }}}");
  }

  public void testNonHoistedRecursiveFunction_withES6Modules() {
    assertNoWarningEs6("export function g() { if (false) { function f() { f(); g(); }}}");
  }

  public void testForOf() {
    assertEarlyReferenceError("for (let x of []) { console.log(x); let x = 123; }");
    assertNoWarningEs6("for (let x of []) { let x; }");
  }

  public void testDestructuringInFor() {
    testSame("for (let [key, val] of X){}");
    testSame("for (let [key, [nestKey, nestVal], val] of X){}");

    testSame("var {x: a, y: b} = {x: 1, y: 2}; a++; b++;");
    testWarning("a++; var {x: a} = {x: 1};", EARLY_REFERENCE);
  }

  public void testNoWarnInExterns1() {
    // Verify duplicate suppressions are properly recognized.
    String externs = "var google; /** @suppress {duplicate} */ var google";
    String code = "";
    testSame(externs, code);
  }

  public void testNoWarnInExterns2() {
    // Verify we don't complain about early references in externs
    String externs = "window; var window;";
    String code = "";
    testSame(externs, code);
  }

  public void testNoWarnInExterns_withES6Modules() {
    String externs = "export var google; /** @suppress {duplicate} */ var google";
    String code = "";
    testSame(externs, code);
  }

  public void testUnusedLocalVar() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("function f() { var a; }");
    assertUnused("function f() { var a = 2; }");
    assertUnused("function f() { var a; a = 2; }");
  }

  public void testUnusedLocalVar_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("export function f() { var a; }");
  }

  public void testUnusedTypedefInModule() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("goog.module('m'); var x;");
    assertUnusedEs6("goog.module('m'); let x;");

    testSame("goog.module('m'); /** @typedef {string} */ var x;");
    testSame("goog.module('m'); /** @typedef {string} */ let x;");
  }

  public void testUnusedTypedefInES6Module() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("import 'm'; var x;");
    assertUnusedEs6("import 'm'; let x;");

    testSame("import 'm'; /** @typedef {string} */ var x;");
  }

  public void testAliasInModule() {
    enableUnusedLocalAssignmentCheck = true;
    testSame(
        LINE_JOINER.join(
            "goog.module('m');",
            "const x = goog.require('x');",
            "const y = x.y;",
            "/** @type {y} */ var z;",
            "alert(z);"));
  }

  public void testAliasInES6Module() {
    enableUnusedLocalAssignmentCheck = true;
    testSame(
        LINE_JOINER.join(
            "import 'm';",
            "import x from 'x';",
            "export const y = x.y;",
            "export /** @type {y} */ var z;",
            "alert(z);"));
  }

  /**
   * Inside a goog.scope, don't warn because the alias might be used in a type annotation.
   */
  public void testUnusedLocalVarInGoogScope() {
    enableUnusedLocalAssignmentCheck = true;
    testSame("goog.scope(function f() { var a; });");
    testSame("goog.scope(function f() { /** @typedef {some.long.name} */ var a; });");
    testSame("goog.scope(function f() { var a = some.long.name; });");
  }

  public void testUnusedLocalLet() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnusedEs6("function f() { let a; }");
    assertUnusedEs6("function f() { let a = 2; }");
    assertUnusedEs6("function f() { let a; a = 2; }");
  }

  public void testUnusedLocalLet_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnusedEs6("export function f() { let a; }");
  }

  public void testUnusedLocalConst() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnusedEs6("function f() { const a = 2; }");
  }

  public void testUnusedLocalConst_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnusedEs6("export function f() { const a = 2; }");
  }

  public void testUnusedLocalArgNoWarning() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f(a) {}");
  }

  public void testUnusedLocalArgNoWarning_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export function f(a) {}");
  }

  public void testUnusedGlobalNoWarning() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("var a = 2;");
  }

  public void testUnusedGlobalNoWarning_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export var a = 2;");
  }

  public void testUnusedGlobalInBlockNoWarning() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("if (true) { var a = 2; }");
  }

  public void testUnusedLocalInBlock() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnusedEs6("if (true) { let a = 2; }");
    assertUnusedEs6("if (true) { const a = 2; }");
  }

  public void testUnusedAssignedInInnerFunction() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("function f() { var x = 1; function g() { x = 2; } }");
  }

  public void testUnusedAssignedInInnerFunction_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("export function f() { var x = 1; function g() { x = 2; } }");
  }

  public void testIncrementDecrementResultUsed() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f() { var x = 5; while (x-- > 0) {} }");
    assertNoWarning("function f() { var x = -5; while (x++ < 0) {} }");
    assertNoWarning("function f() { var x = 5; while (--x > 0) {} }");
    assertNoWarning("function f() { var x = -5; while (++x < 0) {} }");
  }

  public void testIncrementDecrementResultUsed_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export function f() { var x = 5; while (x-- > 0) {} }");
  }

  public void testUsedInInnerFunction() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f() { var x = 1; function g() { use(x); } }");
  }

  public void testUsedInInnerFunction_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export function f() { var x = 1; function g() { use(x); } }");
  }

  public void testUsedInShorthandObjLit() {
    enableUnusedLocalAssignmentCheck = true;
    assertUndeclaredEs6("var z = {x}; z(); var x;");
    testSame("var {x} = foo();");
    testSame("var {x} = {};"); // TODO(moz): Maybe add a warning for this case
    testSame("function f() { var x = 1; return {x}; }");
  }

  public void testUsedInShorthandObjLit_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertUndeclaredEs6("export var z = {x}; z(); var x;");
    testSame("export var {x} = foo();");
  }

  public void testUnusedCatch() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f() { try {} catch (x) {} }");
  }

  public void testUnusedCatch_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export function f() { try {} catch (x) {} }");
  }

  public void testIncrementCountsAsUse() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("var a = 2; var b = []; b[a++] = 1;");
  }

  public void testIncrementCountsAsUse_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export var a = 2; var b = []; b[a++] = 1;");
  }

  public void testForIn() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("for (var prop in obj) {}");
    assertNoWarning("for (prop in obj) {}");
    assertNoWarning("var prop; for (prop in obj) {}");
  }

  public void testUnusedCompoundAssign() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("var x = 0; function f() { return x += 1; }");
    assertNoWarningEs6("var x = 0; var f = () => x += 1;");
    assertNoWarningEs6(
        LINE_JOINER.join(
            "function f(elapsed) {",
            "  let fakeMs = 0;",
            "  stubs.replace(goog, 'now', () => fakeMs += elapsed);",
            "}"));
    assertNoWarningEs6(
        LINE_JOINER.join(
            "function f(elapsed) {",
            "  let fakeMs = 0;",
            "  stubs.replace(goog, 'now', () => fakeMs -= elapsed);",
            "}"));
  }

  public void testUnusedCompoundAssign_withES6Modules() {
    assertNoWarningEs6(
        LINE_JOINER.join(
            "export function f(elapsed) {",
            "  let fakeMs = 0;",
            "  stubs.replace(goog, 'now', () => fakeMs -= elapsed);",
            "}"));
  }

  public void testChainedAssign() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("var a, b = 0, c; a = b = c; alert(a);");
  }

  public void testChainedAssign_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export var a, b = 0, c; a = b = c; alert(a);");
  }

  public void testGoogModule() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("goog.module('example'); var X = 3; use(X);");
    assertUnused("goog.module('example'); var X = 3;");
  }

  public void testES6Module() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("import 'example'; var X = 3; use(X);");
    assertUnused("import 'example'; var X = 3;");
  }

  public void testGoogModule_bundled() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("goog.loadModule(function(exports) { 'use strict';"
                    + "goog.module('example'); var X = 3; use(X);"
                    + "return exports; });");
    assertUnused("goog.loadModule(function(exports) { 'use strict';"
                 + "goog.module('example'); var X = 3;"
                 + "return exports; });");
  }

  public void testGoogModule_destructuring() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarningEs6("goog.module('example'); var {x} = goog.require('y'); use(x);");
    // We could warn here, but it's already caught by the extra require check.
    assertNoWarningEs6("goog.module('example'); var {x} = goog.require('y');");
  }

  public void testES6Module_destructuring() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarningEs6("import 'example'; import {x} from 'y'; use(x);");
  }

  public void testGoogModule_require() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("goog.module('example'); var X = goog.require('foo.X'); use(X);");
    // We could warn here, but it's already caught by the extra require check.
    assertNoWarning("goog.module('example'); var X = goog.require('foo.X');");
  }

  public void testES6Module_import() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("import 'example'; import X from 'foo.X'; use(X);");
  }

  public void testGoogModule_forwardDeclare() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var X = goog.forwardDeclare('foo.X');",
            "",
            "/** @type {X} */ var x = 0;",
            "alert(x);"));

    assertNoWarning("goog.module('example'); var X = goog.forwardDeclare('foo.X');");
  }

  public void testGoogModule_usedInTypeAnnotation() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning(
        "goog.module('example'); var X = goog.require('foo.X'); /** @type {X} */ var y; use(y);");
  }

  public void testES6Module_usedInTypeAnnotation() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning(
        "import 'example'; import X from 'foo.X'; export /** @type {X} */ var y; use(y);");
  }

  public void testGoogModule_duplicateRequire() {
    assertRedeclareError(
        "goog.module('bar'); const X = goog.require('foo.X'); const X = goog.require('foo.X');");
    assertRedeclareError(
        "goog.module('bar'); let X = goog.require('foo.X'); let X = goog.require('foo.X');");
    assertRedeclareError(
        "goog.module('bar'); const X = goog.require('foo.X'); let X = goog.require('foo.X');");
    assertRedeclareError(
        "goog.module('bar'); let X = goog.require('foo.X'); const X = goog.require('foo.X');");
  }

  public void testUndeclaredLet() {
    assertEarlyReferenceError("if (a) { x = 3; let x;}");

    assertEarlyReferenceError(LINE_JOINER.join(
        "var x = 1;",
        "if (true) {",
        "  x++;",
        "  let x = 3;",
        "}"));
  }

  public void testUndeclaredLet_withES6Modules() {
    assertEarlyReferenceError(
        LINE_JOINER.join("export var x = 1;", "if (true) {", "  x++;", "  let x = 3;", "}"));
  }

  public void testUndeclaredConst() {
    assertEarlyReferenceError("if (a) { x = 3; const x = 3;}");

    // For the following, IE 11 gives "Assignment to const", but technically
    // they are also undeclared references, which get caught in the first place.
    assertEarlyReferenceError(LINE_JOINER.join(
        "var x = 1;",
        "if (true) {",
        "  x++;",
        "  const x = 3;",
        "}"));

    assertEarlyReferenceError("a = 1; const a = 0;");
    assertEarlyReferenceError("a++; const a = 0;");
  }

  public void testIllegalLetShadowing() {
    assertRedeclareError("if (a) { let x; var x;}");

    assertRedeclareError("if (a) { let x; let x;}");

    assertRedeclareError(LINE_JOINER.join(
        "function f() {",
        "  let x;",
        "  if (a) {",
        "    var x;",
        "  }",
        "}"));

    assertNoWarningEs6(
        LINE_JOINER.join(
            "function f() {",
            "  if (a) {",
            "    let x;",
            "  }",
            "  var x;",
            "}"));

    assertNoWarningEs6(
        LINE_JOINER.join(
            "function f() {",
            "  if (a) { let x; }",
            "  if (b) { var x; }",
            "}"));

    assertRedeclareError("let x; var x;");
    assertRedeclareError("var x; let x;");
    assertRedeclareError("let x; let x;");
  }

  public void testIllegalLetShadowing_withES6Modules() {
    assertRedeclareError(
        LINE_JOINER.join(
            "export function f() {", "  let x;", "  if (a) {", "    var x;", "  }", "}"));

    assertNoWarningEs6(
        LINE_JOINER.join(
            "export function f() {", "  if (a) {", "    let x;", "  }", "  var x;", "}"));

    assertRedeclareError("export let x; var x;");
  }

  public void testDuplicateLetConst() {
    assertRedeclareError("let x, x;");
    assertRedeclareError("const x = 0, x = 0;");
  }

  public void testIllegalBlockScopedEarlyReference() {
    assertEarlyReferenceError("let x = x");
    assertEarlyReferenceError("let [x] = x");
    assertEarlyReferenceError("const x = x");
    assertEarlyReferenceError("let x = x || 0");
    assertEarlyReferenceError("const x = x || 0");
    // In the following cases, "x" might not be reachable but we warn anyways
    assertEarlyReferenceError("let x = expr || x");
    assertEarlyReferenceError("const x = expr || x");
    assertEarlyReferenceError("X; class X {};");
  }

  public void testIllegalConstShadowing() {
    assertRedeclareError("if (a) { const x = 3; var x;}");

    assertRedeclareError(LINE_JOINER.join(
        "function f() {",
        "  const x = 3;",
        "  if (a) {",
        "    var x;",
        "  }",
        "}"));
  }

  public void testIllegalConstShadowing_withES6Modules() {
    assertRedeclareError(
        LINE_JOINER.join(
            "export function f() {", "  const x = 3;", "  if (a) {", "    var x;", "  }", "}"));
  }

  public void testVarShadowing() {
    assertRedeclareGlobal("if (a) { var x; var x;}");
    assertRedeclareError("if (a) { var x; let x;}");

    assertRedeclare("function f() { var x; if (a) { var x; }}");
    assertRedeclareError("function f() { if (a) { var x; } let x;}");
    assertNoWarningEs6("function f() { var x; if (a) { let x; }}");

    assertNoWarningEs6(
        LINE_JOINER.join(
            "function f() {",
            "  if (a) { var x; }",
            "  if (b) { let x; }",
            "}"));
  }

  public void testVarShadowing_withES6Modules01() {
    assertRedeclare("export function f() { var x; if (a) { var x; }}");
  }

  public void testVarShadowing_withES6Modules02() {
    assertRedeclareError("export function f() { if (a) { var x; } let x;}");
  }

  public void testVarShadowing_withES6Modules03() {
    assertNoWarningEs6("export function f() { var x; if (a) { let x; }}");
  }

  public void testVarShadowing_withES6Modules04() {
    assertNoWarningEs6(
        LINE_JOINER.join(
            "export function f() {", "  if (a) { var x; }", "  if (b) { let x; }", "}"));
  }

  public void testParameterShadowing() {
    assertRedeclareError("function f(x) { let x; }");
    assertRedeclareError("function f(x) { const x = 3; }");
    assertRedeclareError("function f(X) { class X {} }");

    assertRedeclare("function f(x) { function x() {} }");
    assertRedeclare("function f(x) { var x; }");
    assertRedeclareEs6("function f(x=3) { var x; }");
    assertNoWarningEs6("function f(...x) {}");
    assertRedeclareEs6("function f(...x) { var x; }");
    assertRedeclareEs6("function f(...x) { function x() {} }");
    assertRedeclareEs6("function f(x=3) { function x() {} }");
    assertNoWarningEs6("function f(x) { if (true) { let x; } }");
    assertNoWarningEs6(LINE_JOINER.join(
        "function outer(x) {",
        "  function inner() {",
        "    let x = 1;",
        "  }",
        "}"));
    assertNoWarning(LINE_JOINER.join(
        "function outer(x) {",
        "  function inner() {",
        "    var x = 1;",
        "  }",
        "}"));

    assertRedeclareEs6("function f({a, b}) { var a = 2 }");
    assertRedeclareEs6("function f({a, b}) { if (!a) var a = 6; }");
  }

  public void testParameterShadowing_withES6Modules() {
    assertRedeclareError("export function f(x) { let x; }");

    assertRedeclare("export function f(x) { function x() {} }");

    assertRedeclareEs6("export function f(x=3) { var x; }");

    assertNoWarningEs6("export function f(...x) {}");

    assertNoWarning(
        LINE_JOINER.join(
            "export function outer(x) {", "  function inner() {", "    var x = 1;", "  }", "}"));
  }

  public void testReassignedConst() {
    assertReassign("const a = 0; a = 1;");
    assertReassign("const a = 0; a++;");
  }

  public void testLetConstNotDirectlyInBlock() {
    testSame("if (true) var x = 3;");
    testError("if (true) let x = 3;", DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testError("if (true) const x = 3;", DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testError("if (true) class C {}", DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testError("if (true) function f() {}", DECLARATION_NOT_DIRECTLY_IN_BLOCK);
  }

  public void testFunctionHoisting() {
    assertUndeclaredEs6("if (true) { f(); function f() {} }");
  }

  public void testFunctionHoistingRedeclaration1() {
    String[] js = {
      "var x;",
      "function x() {}",
    };
    String message = "Variable x declared more than once. First occurence: input0";
    testError(srcs(js), error(VarCheck.VAR_MULTIPLY_DECLARED_ERROR, message));
  }

  public void testFunctionHoistingRedeclaration2() {
    String[] js = {
      "function x() {}",
      "var x;",
    };
    String message = "Variable x declared more than once. First occurence: input0";
    testError(srcs(js), error(VarCheck.VAR_MULTIPLY_DECLARED_ERROR, message));
  }

  public void testArrowFunction() {
    assertNoWarningEs6("var f = x => { return x+1; };");
    assertNoWarningEs6("var odds = [1,2,3,4].filter((n) => n%2 == 1)");
    assertRedeclareEs6("var f = x => {var x;}");
    assertRedeclareError("var f = x => {let x;}");
  }

  public void testArrowFunction_withES6Modules() {
    assertNoWarningEs6("export var f = x => { return x+1; };");
    assertRedeclareEs6("export var f = x => {var x;}");
    assertRedeclareError("export var f = x => {let x;}");
  }

  public void testTryCatch() {
    assertRedeclareError(
        LINE_JOINER.join(
            "function f() {",
            "  try {",
            "    let e = 0;",
            "    if (true) {",
            "      let e = 1;",
            "    }",
            "  } catch (e) {",
            "    let e;",
            "  }",
            "}"));

    assertRedeclareError(
        LINE_JOINER.join(
            "function f() {",
            "  try {",
            "    let e = 0;",
            "    if (true) {",
            "      let e = 1;",
            "    }",
            "  } catch (e) {",
            "      var e;",
            "  }",
            "}"));

    assertRedeclareError(
        LINE_JOINER.join(
            "function f() {",
            "  try {",
            "    let e = 0;",
            "    if (true) {",
            "      let e = 1;",
            "    }",
            "  } catch (e) {",
            "    function e() {",
            "      var e;",
            "    }",
            "  }",
            "}"));
  }

  public void testTryCatch_withES6Modules() {
    assertRedeclareError(
        LINE_JOINER.join(
            "export function f() {",
            "  try {",
            "    let e = 0;",
            "    if (true) {",
            "      let e = 1;",
            "    }",
            "  } catch (e) {",
            "    let e;",
            "  }",
            "}"));
  }

  public void testClass() {
    assertNoWarningEs6("class A { f() { return 1729; } }");
  }

  public void testClass_withES6Modules() {
    assertNoWarningEs6("export class A { f() { return 1729; } }");
  }

  public void testRedeclareClassName() {
    assertNoWarningEs6("var Clazz = class Foo {}; var Foo = 3;");
  }

  public void testRedeclareClassName_withES6Modules() {
    assertNoWarningEs6("export var Clazz = class Foo {}; var Foo = 3;");
  }

  public void testClassExtend() {
    assertNoWarningEs6("class A {} class C extends A {} C = class extends A {}");
  }

  public void testClassExtend_withES6Modules() {
    assertNoWarningEs6("export class A {} class C extends A {} C = class extends A {}");
  }

  public void testArrayPattern() {
    assertNoWarningEs6("var [a] = [1];");
    assertNoWarningEs6("var [a, b] = [1, 2];");
    assertUndeclaredEs6("alert(a); var [a] = [1];");
    assertUndeclaredEs6("alert(b); var [a, b] = [1, 2];");

    assertUndeclaredEs6("[a] = [1]; var a;");
    assertUndeclaredEs6("[a, b] = [1]; var b;");
  }

  public void testArrayPattern_withES6Modules01() {
    assertNoWarningEs6("export var [a] = [1];");
  }

  public void testArrayPattern_defaultValue() {
    assertNoWarningEs6("var [a = 1] = [2];");
    assertNoWarningEs6("var [a = 1] = [];");
    assertUndeclaredEs6("alert(a); var [a = 1] = [2];");
    assertUndeclaredEs6("alert(a); var [a = 1] = [];");

    assertUndeclaredEs6("alert(a); var [a = b] = [1];");
    assertUndeclaredEs6("alert(a); var [a = b] = [];");
  }

  public void testArrayPattern_defaultValue_withES6Modules01() {
    assertNoWarningEs6("export var [a = 1] = [2];");
  }

  public void testObjectPattern() {
    assertNoWarningEs6("var {a: b} = {a: 1};");
    assertNoWarningEs6("var {a: b} = {};");
    assertNoWarningEs6("var {a} = {a: 1};");

    // 'a' is not declared at all, so the 'a' passed to alert() references
    // the global variable 'a', and there is no warning.
    assertNoWarningEs6("alert(a); var {a: b} = {};");

    assertUndeclaredEs6("alert(b); var {a: b} = {a: 1};");
    assertUndeclaredEs6("alert(a); var {a} = {a: 1};");

    assertUndeclaredEs6("({a: b} = {}); var a, b;");
  }

  public void testObjectPattern_withES6Modules01() {
    assertNoWarningEs6("export var {a: b} = {a: 1};");
  }

  public void testObjectPattern_defaultValue() {
    assertUndeclaredEs6("alert(b); var {a: b = c} = {a: 1};");
    assertUndeclaredEs6("alert(b); var c; var {a: b = c} = {a: 1};");
    assertUndeclaredEs6("var {a: b = c} = {a: 1}; var c;");
    assertUndeclaredEs6("alert(b); var {a: b = c} = {};");
    assertUndeclaredEs6("alert(a); var {a = c} = {a: 1};");
    assertUndeclaredEs6("alert(a); var {a = c} = {};");
  }

  public void testObjectPattern_defaultValue_withES6Modules() {
    assertUndeclaredEs6("export var {a: b = c} = {a: 1}; var c;");
  }

  /**
   * We can't catch all possible runtime errors but it's useful to have some
   * basic checks.
   */
  public void testDefaultParam() {
    assertEarlyReferenceError("function f(x=a) { let a; }");
    assertEarlyReferenceError(LINE_JOINER.join(
        "function f(x=a) { let a; }",
        "function g(x=1) { var a; }"));
    assertEarlyReferenceError("function f(x=a) { var a; }");
    assertEarlyReferenceError("function f(x=a()) { function a() {} }");
    assertEarlyReferenceError("function f(x=[a]) { var a; }");
    assertEarlyReferenceError("function f(x=y, y=2) {}");
    assertEarlyReferenceError("function f(x=x) {}");
    assertEarlyReferenceError("function f([x]=x) {}");
    // x within a function isn't referenced at the time the default value for x is evaluated.
    assertNoWarningEs6("function f(x=()=>x) {}");
    assertNoWarningEs6("function f(x=a) {}");
    assertNoWarningEs6("function f(x=a) {} var a;");
    assertNoWarningEs6("let b; function f(x=b) { var b; }");
    assertNoWarningEs6("function f(y = () => x, x = 5) { return y(); }");
    assertNoWarningEs6("function f(x = new foo.bar()) {}");
    assertNoWarningEs6("var foo = {}; foo.bar = class {}; function f(x = new foo.bar()) {}");
  }

  public void testDefaultParam_withES6Modules() {
    assertEarlyReferenceError("export function f(x=a) { let a; }");
    assertNoWarningEs6("export function f(x=()=>x) {}");
  }

  public void testDestructuring() {
    testSame(LINE_JOINER.join(
        "function f() { ",
        "  var obj = {a:1, b:2}; ",
        "  var {a:c, b:d} = obj; ",
        "}"));
    testSame(LINE_JOINER.join(
        "function f() { ",
        "  var obj = {a:1, b:2}; ",
        "  var {a, b} = obj; ",
        "}"));

    assertRedeclareEs6(LINE_JOINER.join(
        "function f() { ",
        "  var obj = {a:1, b:2}; ",
        "  var {a:c, b:d} = obj; ",
        "  var c = b;",
        "}"));

    assertUndeclaredEs6(LINE_JOINER.join(
        "function f() { ",
        "  var {a:c, b:d} = obj;",
        "  var obj = {a:1, b:2};",
        "}"));
    assertUndeclaredEs6(LINE_JOINER.join(
        "function f() { ",
        "  var {a, b} = obj;",
        "  var obj = {a:1, b:2};",
        "}"));
    assertUndeclaredEs6(LINE_JOINER.join(
        "function f() { ",
        "  var e = c;",
        "  var {a:c, b:d} = {a:1, b:2};",
        "}"));
  }

  public void testDestructuring_withES6Modules() {
    testSame(
        LINE_JOINER.join(
            "export function f() { ", "  var obj = {a:1, b:2}; ", "  var {a:c, b:d} = obj; ", "}"));

    assertRedeclareEs6(
        LINE_JOINER.join(
            "export function f() { ",
            "  var obj = {a:1, b:2}; ",
            "  var {a:c, b:d} = obj; ",
            "  var c = b;",
            "}"));

    assertUndeclaredEs6(
        LINE_JOINER.join(
            "export function f() { ", "  var {a:c, b:d} = obj;", "  var obj = {a:1, b:2};", "}"));
  }

  public void testDestructuringInLoop() {
    testSame("for (let {length: x} in obj) {}");

    testSame("for (let [{length: z}, w] in obj) {}");
  }

  public void testEnhancedForLoopTemporalDeadZone() {
    assertEarlyReferenceError("for (let x of [x]);");
    assertEarlyReferenceError("for (let x in [x]);");
    assertEarlyReferenceError("for (const x of [x]);");
    testSame("for (var x of [x]);");
    testSame("for (let x of [() => x]);");
    testSame("let x = 1; for (let y of [x]);");
  }

  public void testEnhancedForLoopTemporalDeadZone_withES6Modules() {
    testSame("export let x = 1; for (let y of [x]);");
  }

  /**
   * Expects the JS to generate one bad-read error.
   */
  private void assertRedeclare(String js) {
    testWarning(js, REDECLARED_VARIABLE);
  }

  private void assertRedeclareEs6(String js) {
    testWarning(js, REDECLARED_VARIABLE);
  }

  private void assertRedeclareError(String js) {
    testError(js, REDECLARED_VARIABLE_ERROR);
  }

  private void assertReassign(String js) {
    testError(js, REASSIGNED_CONSTANT);
  }

  private void assertRedeclareGlobal(String js) {
    testError(js, VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  /**
   * Expects the JS to generate one bad-write warning.
   */
  private void assertUndeclared(String js) {
    testWarning(js, EARLY_REFERENCE);
  }

  /**
   * Expects the JS to generate one bad-write warning.
   */
  private void assertUndeclaredEs6(String js) {
    testWarning(js, EARLY_REFERENCE);
  }

  private void assertEarlyReferenceError(String js) {
    testError(js, EARLY_REFERENCE_ERROR);
  }

  /**
   * Expects the JS to generate one bad-write warning.
   */
  private void assertAmbiguousEs6(String js) {
    testSame(js); // In ES6, these are block scoped functions, so no ambiguity.
  }

  /**
   * Expects the JS to generate one unused local error.
   */
  private void assertUnused(String js) {
    testWarning(js, UNUSED_LOCAL_ASSIGNMENT);
  }

  /**
   * Expects the JS to generate one unused local error.
   */
  private void assertUnusedEs6(String js) {
    testWarning(js, UNUSED_LOCAL_ASSIGNMENT);
  }

  /**
   * Expects the JS to generate no errors or warnings.
   */
  private void assertNoWarning(String js) {
    testSame(js);
  }

  /**
   * Expects the JS to generate no errors or warnings.
   */
  private void assertNoWarningEs6(String js) {
    testSame(js);
  }
}
