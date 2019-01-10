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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test that warnings are generated in appropriate cases and appropriate cases only by
 * VariableReferenceCheck
 *
 */
@RunWith(JUnit4.class)
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
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableUnusedLocalAssignmentCheck = false;
  }

  @Test
  public void testDoubleTryCatch() {
    testSame(
        lines(
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

  @Test
  public void testDoubleTryCatch_withES6Modules() {
    testSame(
        lines(
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

  @Test
  public void testCorrectCode() {
    assertNoWarning("function foo(d) { (function() { d.foo(); }); d.bar(); } ");
    assertNoWarning("function foo() { bar(); } function bar() { foo(); } ");
    assertNoWarning("function f(d) { d = 3; }");
    assertNoWarning(VARIABLE_RUN);
    assertNoWarning("if (a) { var x; }");
    assertNoWarning("function f() { " + VARIABLE_RUN + "}");

    assertNoWarning(LET_RUN);
    assertNoWarning("function f() { " + LET_RUN + "}");
    assertNoWarning("try { let e; } catch (e) { let x; }");
  }

  @Test
  public void testCorrectCode_withES6Modules() {
    assertNoWarning("export function foo(d) { (function() { d.foo(); }); d.bar(); } ");
  }

  @Test
  public void testCorrectShadowing() {
    assertNoWarning(VARIABLE_RUN + "function f() { " + VARIABLE_RUN + "}");
  }

  @Test
  public void testCorrectShadowing_withES6Modules() {
    assertNoWarning(VARIABLE_RUN + "export function f() { " + VARIABLE_RUN + "}");
  }

  @Test
  public void testCorrectRedeclare() {
    assertNoWarning(
        "function f() { if (1) { var a = 2; } else { var a = 3; } }");
  }

  @Test
  public void testCorrectRedeclare_withES6Modules() {
    assertNoWarning("export function f() { if (1) { var a = 2; } else { var a = 3; } }");
  }

  @Test
  public void testCorrectRecursion() {
    assertNoWarning("function f() { var x = function() { x(); }; }");
  }

  @Test
  public void testCorrectRecursion_withES6Modules() {
    assertNoWarning("export function f() { var x = function() { x(); }; }");
  }

  @Test
  public void testCorrectCatch() {
    assertNoWarning("function f() { try { var x = 2; } catch (x) {} }");
    assertNoWarning("function f(e) { e = 3; try {} catch (e) {} }");
  }

  @Test
  public void testCorrectCatch_withES6Modules() {
    assertNoWarning("export function f() { try { var x = 2; } catch (x) {} }");
  }

  @Test
  public void testRedeclare() {
    // Only test local scope since global scope is covered elsewhere
    assertRedeclare("function f() { var a = 2; var a = 3; }");
    assertRedeclare("function f(a) { var a = 2; }");
    assertRedeclare("function f(a) { if (!a) var a = 6; }");
    // NOTE: We decided to not give warnings to the following cases. The function won't be
    // overwritten at runtime anyway.
    assertNoWarning("function f() { var f = 1; }");
    assertNoWarning("function f() { let f = 1; }");
  }

  @Test
  public void testRedeclare_withES6Modules() {
    assertRedeclare("export function f() { var a = 2; var a = 3; }");
    assertNoWarning("export function f() { let f = 1; }");
    // In an ES6 module vars are in the module scope, not global, so they are covered here.
    assertRedeclare("export var a = 2; var a = 3;");
    assertRedeclare("export var a = 2; if (a) var a = 3;");
    assertRedeclare("function f() {} function f() {} export {f};");
  }

  @Test
  public void testIssue166a() {
    assertRedeclareError(
        "try { throw 1 } catch(e) { /** @suppress {duplicate} */ var e=2 }");
  }

  @Test
  public void testIssue166b() {
    assertRedeclareError(
        "function a() { try { throw 1 } catch(e) { /** @suppress {duplicate} */ var e=2 } };");
  }

  @Test
  public void testIssue166b_withES6Modules() {
    assertRedeclareError(
        lines(
            "export function a() {",
            "  try {",
            "    throw 1",
            "  } catch (e) {",
            "      /** @suppress {duplicate} */",
            "      var e = 2",
            "  }",
            "};"));
  }

  @Test
  public void testIssue166c() {
    assertRedeclareError(
        "var e = 0; try { throw 1 } catch(e) { /** @suppress {duplicate} */ var e=2 }");
  }

  @Test
  public void testIssue166d() {
    assertRedeclareError(
        lines(
            "function a() {",
            "  var e = 0; try { throw 1 } catch(e) {",
            "    /** @suppress {duplicate} */ var e = 2;",
            "  }",
            "};"));
  }

  @Test
  public void testIssue166e() {
    testSame("var e = 2; try { throw 1 } catch(e) {}");
  }

  @Test
  public void testIssue166e_withES6Modules() {
    testSame("export var e = 2; try { throw 1 } catch(e) {}");
  }

  @Test
  public void testIssue166f() {
    testSame(
        lines(
            "function a() {",
            "  var e = 2;",
            "  try { throw 1 } catch(e) {}",
            "}"));
  }

  @Test
  public void testEarlyReference() {
    assertEarlyReferenceWarning("function f() { a = 2; var a = 3; }");
  }

  @Test
  public void testEarlyReference_withES6Modules() {
    assertEarlyReferenceWarning("export function f() { a = 2; var a = 3; }");
  }

  @Test
  public void testCorrectEarlyReference() {
    assertNoWarning("var goog = goog || {}");
    assertNoWarning("var google = google || window['google'] || {}");
    assertNoWarning("function f() { a = 2; } var a = 2;");
  }

  @Test
  public void testCorrectEarlyReference_withES6Modules() {
    assertNoWarning("export function f() { a = 2; } var a = 2;");
  }

  @Test
  public void testUnreferencedBleedingFunction() {
    assertNoWarning("var x = function y() {}");
    assertNoWarning("var x = function y() {}; var y = 1;");
  }

  @Test
  public void testUnreferencedBleedingFunction_withES6Modules() {
    assertNoWarning("export var x = function y() {}");
  }

  @Test
  public void testReferencedBleedingFunction() {
    assertNoWarning("var x = function y() { return y(); }");
  }

  @Test
  public void testReferencedBleedingFunction_withES6Modules() {
    assertNoWarning("export var x = function y() { return y(); }");
  }

  @Test
  public void testVarShadowsFunctionName() {
    assertNoWarning("var x = function y() { var y; }");
    assertNoWarning("var x = function y() { let y; }");
  }

  @Test
  public void testVarShadowsFunctionName_withES6Modules() {
    assertNoWarning("export var x = function y() { var y; }");
    assertNoWarning("export var x = function y() { let y; }");
  }

  @Test
  public void testDoubleDeclaration() {
    assertRedeclare("function x(y) { if (true) { var y; } }");
  }

  @Test
  public void testDoubleDeclaration2() {
    assertRedeclare("function x() { var y; if (true) { var y; } }");
  }

  @Test
  public void testDoubleDeclaration_withES6Modules() {
    assertRedeclare("export function x(y) { if (true) { var y; } }");
  }

  @Test
  public void testHoistedFunction1() {
    assertNoWarning("f(); function f() {}");
  }

  @Test
  public void testHoistedFunction2() {
    assertNoWarning("function g() { f(); function f() {} }");
  }

  @Test
  public void testHoistedFunction_withES6Modules() {
    assertNoWarning("export function g() { f(); function f() {} }");
  }

  @Test
  public void testNonHoistedFunction() {
    assertEarlyReferenceWarning("if (true) { f(); function f() {} }");
  }

  @Test
  public void testNonHoistedFunction2() {
    assertNoWarning("if (false) { function f() {} f(); }");
  }

  @Test
  public void testNonHoistedFunction3() {
    assertNoWarning("function g() { if (false) { function f() {} f(); }}");
  }

  @Test
  public void testNonHoistedFunction4() {
    assertNoWarning("if (false) { function f() {} }  f();");
  }

  @Test
  public void testNonHoistedFunction5() {
    assertNoWarning("function g() { if (false) { function f() {} }  f(); }");
  }

  @Test
  public void testNonHoistedFunction6() {
    assertEarlyReferenceWarning("if (false) { f(); function f() {} }");
  }

  @Test
  public void testNonHoistedFunction7() {
    assertEarlyReferenceWarning("function g() { if (false) { f(); function f() {} }}");
  }

  @Test
  public void testNonHoistedFunction_withES6Modules() {
    assertEarlyReferenceWarning("export function g() { if (false) { f(); function f() {} }}");
  }

  @Test
  public void testNonHoistedRecursiveFunction1() {
    assertNoWarning("if (false) { function f() { f(); }}");
  }

  @Test
  public void testNonHoistedRecursiveFunction2() {
    assertNoWarning("function g() { if (false) { function f() { f(); }}}");
  }

  @Test
  public void testNonHoistedRecursiveFunction3() {
    assertNoWarning("function g() { if (false) { function f() { f(); g(); }}}");
  }

  @Test
  public void testNonHoistedRecursiveFunction_withES6Modules() {
    assertNoWarning("export function g() { if (false) { function f() { f(); g(); }}}");
  }

  @Test
  public void testForOf() {
    assertEarlyReferenceError("for (let x of []) { console.log(x); let x = 123; }");
    assertNoWarning("for (let x of []) { let x; }");
  }

  @Test
  public void testDestructuringInFor() {
    testSame("for (let [key, val] of X){}");
    testSame("for (let [key, [nestKey, nestVal], val] of X){}");

    testSame("var {x: a, y: b} = {x: 1, y: 2}; a++; b++;");
    testWarning("a++; var {x: a} = {x: 1};", EARLY_REFERENCE);
  }

  @Test
  public void testSuppressDuplicate_first() {
    String code = "/** @suppress {duplicate} */ var google; var google";
    testSame(code);
  }

  @Test
  public void testSuppressDuplicate_second() {
    String code = "var google; /** @suppress {duplicate} */ var google";
    testSame(code);
  }

  @Test
  public void testSuppressDuplicate_fileoverview() {
    String code =
        "/** @fileoverview @suppress {duplicate} */\n"
            + "/** @type {?} */ var google;\n"
            + " var google";
    testSame(code);
  }

  @Test
  public void testNoWarnDuplicateInExterns2() {
    // Verify we don't complain about early references in externs
    String externs = "window; var window;";
    String code = "";
    testSame(externs(externs), srcs(code));
  }

  @Test
  public void testNoWarnDuplicateInExterns_withES6Modules() {
    String externs = "export var google; /** @suppress {duplicate} */ var google";
    String code = "";
    testSame(externs(externs), srcs(code));
  }

  @Test
  public void testUnusedLocalVar() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("function f() { var a; }");
    assertUnused("function f() { var a = 2; }");
    assertUnused("function f() { var a; a = 2; }");
  }

  @Test
  public void testUnusedLocalVar_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("export function f() { var a; }");
  }

  @Test
  public void testUnusedTypedefInModule() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("goog.module('m'); var x;");
    assertUnused("goog.module('m'); let x;");

    testSame("goog.module('m'); /** @typedef {string} */ var x;");
    testSame("goog.module('m'); /** @typedef {string} */ let x;");
  }

  @Test
  public void testUnusedTypedefInES6Module() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("import 'm'; var x;");
    assertUnused("import 'm'; let x;");

    testSame("import 'm'; /** @typedef {string} */ var x;");
  }

  @Test
  public void testImportStar() {
    testSame("import * as ns from './foo.js'");
  }

  @Test
  public void testAliasInModule() {
    enableUnusedLocalAssignmentCheck = true;
    testSame(
        lines(
            "goog.module('m');",
            "const x = goog.require('x');",
            "const y = x.y;",
            "/** @type {y} */ var z;",
            "alert(z);"));
  }

  @Test
  public void testAliasInES6Module() {
    enableUnusedLocalAssignmentCheck = true;
    testSame(
        lines(
            "import 'm';",
            "import x from 'x';",
            "export const y = x.y;",
            "export /** @type {y} */ var z;",
            "alert(z);"));
  }

  @Test
  public void testUnusedImport() {
    enableUnusedLocalAssignmentCheck = true;
    // TODO(b/64566470): This test should give an UNUSED_LOCAL_ASSIGNMENT error for x.
    testSame("import x from 'Foo';");
  }

  @Test
  public void testExportedType() {
    enableUnusedLocalAssignmentCheck = true;
    testSame(lines("export class Foo {}", "export /** @type {Foo} */ var y;"));
  }

  /** Inside a goog.scope, don't warn because the alias might be used in a type annotation. */
  @Test
  public void testUnusedLocalVarInGoogScope() {
    enableUnusedLocalAssignmentCheck = true;
    testSame("goog.scope(function f() { var a; });");
    testSame("goog.scope(function f() { /** @typedef {some.long.name} */ var a; });");
    testSame("goog.scope(function f() { var a = some.long.name; });");
  }

  @Test
  public void testUnusedLocalLet() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("function f() { let a; }");
    assertUnused("function f() { let a = 2; }");
    assertUnused("function f() { let a; a = 2; }");
  }

  @Test
  public void testUnusedLocalLet_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("export function f() { let a; }");
  }

  @Test
  public void testUnusedLocalConst() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("function f() { const a = 2; }");
  }

  @Test
  public void testUnusedLocalConst_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("export function f() { const a = 2; }");
  }

  @Test
  public void testUnusedLocalArgNoWarning() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f(a) {}");
  }

  @Test
  public void testUnusedLocalArgNoWarning_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export function f(a) {}");
  }

  @Test
  public void testUnusedGlobalNoWarning() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("var a = 2;");
  }

  @Test
  public void testUnusedGlobalNoWarning_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export var a = 2;");
  }

  @Test
  public void testUnusedGlobalInBlockNoWarning() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("if (true) { var a = 2; }");
  }

  @Test
  public void testUnusedLocalInBlock() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("if (true) { let a = 2; }");
    assertUnused("if (true) { const a = 2; }");
  }

  @Test
  public void testUnusedAssignedInInnerFunction() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("function f() { var x = 1; function g() { x = 2; } }");
  }

  @Test
  public void testUnusedAssignedInInnerFunction_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("export function f() { var x = 1; function g() { x = 2; } }");
  }

  @Test
  public void testIncrementDecrementResultUsed() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f() { var x = 5; while (x-- > 0) {} }");
    assertNoWarning("function f() { var x = -5; while (x++ < 0) {} }");
    assertNoWarning("function f() { var x = 5; while (--x > 0) {} }");
    assertNoWarning("function f() { var x = -5; while (++x < 0) {} }");
  }

  @Test
  public void testIncrementDecrementResultUsed_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export function f() { var x = 5; while (x-- > 0) {} }");
  }

  @Test
  public void testUsedInInnerFunction() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f() { var x = 1; function g() { use(x); } }");
  }

  @Test
  public void testUsedInInnerFunction_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export function f() { var x = 1; function g() { use(x); } }");
  }

  @Test
  public void testUsedInShorthandObjLit() {
    enableUnusedLocalAssignmentCheck = true;
    assertEarlyReferenceWarning("var z = {x}; z(); var x;");
    testSame("var {x} = foo();");
    testSame("var {x} = {};"); // TODO(moz): Maybe add a warning for this case
    testSame("function f() { var x = 1; return {x}; }");
  }

  @Test
  public void testUsedInShorthandObjLit_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertEarlyReferenceWarning("export var z = {x}; z(); var x;");
    testSame("export var {x} = foo();");
  }

  @Test
  public void testUnusedCatch() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f() { try {} catch (x) {} }");
  }

  @Test
  public void testUnusedCatch_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export function f() { try {} catch (x) {} }");
  }

  @Test
  public void testIncrementCountsAsUse() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("var a = 2; var b = []; b[a++] = 1;");
  }

  @Test
  public void testIncrementCountsAsUse_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export var a = 2; var b = []; b[a++] = 1;");
  }

  @Test
  public void testForIn() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("for (var prop in obj) {}");
    assertNoWarning("for (prop in obj) {}");
    assertNoWarning("var prop; for (prop in obj) {}");
  }

  @Test
  public void testUnusedCompoundAssign() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("var x = 0; function f() { return x += 1; }");
    assertNoWarning("var x = 0; var f = () => x += 1;");
    assertNoWarning(
        lines(
            "function f(elapsed) {",
            "  let fakeMs = 0;",
            "  stubs.replace(goog, 'now', () => fakeMs += elapsed);",
            "}"));
    assertNoWarning(
        lines(
            "function f(elapsed) {",
            "  let fakeMs = 0;",
            "  stubs.replace(goog, 'now', () => fakeMs -= elapsed);",
            "}"));
  }

  @Test
  public void testUnusedCompoundAssign_withES6Modules() {
    assertNoWarning(
        lines(
            "export function f(elapsed) {",
            "  let fakeMs = 0;",
            "  stubs.replace(goog, 'now', () => fakeMs -= elapsed);",
            "}"));
  }

  @Test
  public void testChainedAssign() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("var a, b = 0, c; a = b = c; alert(a);");
    assertUnused(
        lines(
            "function foo() {",
            "  var a, b = 0, c;",
            "  a = b = c;",
            "  alert(a); ",
            "}",
            "foo();"));
  }

  @Test
  public void testChainedAssign_withES6Modules() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("export var a, b = 0, c; a = b = c; alert(a);");
  }

  @Test
  public void testGoogModule() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("goog.module('example'); var X = 3; use(X);");
    assertUnused("goog.module('example'); var X = 3;");
  }

  @Test
  public void testES6Module() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("import 'example'; var X = 3; use(X);");
    assertUnused("import 'example'; var X = 3;");
  }

  @Test
  public void testGoogModule_bundled() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("goog.loadModule(function(exports) { 'use strict';"
                    + "goog.module('example'); var X = 3; use(X);"
                    + "return exports; });");
    assertUnused("goog.loadModule(function(exports) { 'use strict';"
                 + "goog.module('example'); var X = 3;"
                 + "return exports; });");
  }

  @Test
  public void testGoogModule_destructuring() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("goog.module('example'); var {x} = goog.require('y'); use(x);");
    // We could warn here, but it's already caught by the extra require check.
    assertNoWarning("goog.module('example'); var {x} = goog.require('y');");
  }

  @Test
  public void testES6Module_destructuring() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("import 'example'; import {x} from 'y'; use(x);");
    assertNoWarning("import 'example'; import {x as x} from 'y'; use(x);");
    assertNoWarning("import 'example'; import {y as x} from 'y'; use(x);");
  }

  @Test
  public void testGoogModule_require() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("goog.module('example'); var X = goog.require('foo.X'); use(X);");
    // We could warn here, but it's already caught by the extra require check.
    assertNoWarning("goog.module('example'); var X = goog.require('foo.X');");
  }

  @Test
  public void testES6Module_import() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("import 'example'; import X from 'foo.X'; use(X);");
  }

  @Test
  public void testGoogModule_forwardDeclare() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning(
        lines(
            "goog.module('example');",
            "",
            "var X = goog.forwardDeclare('foo.X');",
            "",
            "/** @type {X} */ var x = 0;",
            "alert(x);"));

    assertNoWarning("goog.module('example'); var X = goog.forwardDeclare('foo.X');");
  }

  @Test
  public void testGoogModule_requireType() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("goog.module('example'); var X = goog.requireType('foo.X');");
  }

  @Test
  public void testGoogModule_usedInTypeAnnotation() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning(
        "goog.module('example'); var X = goog.require('foo.X'); /** @type {X} */ var y; use(y);");
  }

  @Test
  public void testES6Module_usedInTypeAnnotation() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning(
        "import 'example'; import X from 'foo.X'; export /** @type {X} */ var y; use(y);");
  }

  @Test
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

  @Test
  public void testUndeclaredLet() {
    assertEarlyReferenceError("if (a) { x = 3; let x;}");

    assertEarlyReferenceError(lines(
        "var x = 1;",
        "if (true) {",
        "  x++;",
        "  let x = 3;",
        "}"));
  }

  @Test
  public void testUndeclaredLet_withES6Modules() {
    assertEarlyReferenceError(
        lines("export var x = 1;", "if (true) {", "  x++;", "  let x = 3;", "}"));
  }

  @Test
  public void testUndeclaredConst() {
    assertEarlyReferenceError("if (a) { x = 3; const x = 3;}");

    // For the following, IE 11 gives "Assignment to const", but technically
    // they are also undeclared references, which get caught in the first place.
    assertEarlyReferenceError(lines(
        "var x = 1;",
        "if (true) {",
        "  x++;",
        "  const x = 3;",
        "}"));

    assertEarlyReferenceError("a = 1; const a = 0;");
    assertEarlyReferenceError("a++; const a = 0;");
  }

  @Test
  public void testIllegalLetShadowing() {
    assertRedeclareError("if (a) { let x; var x;}");

    assertRedeclareError("if (a) { let x; let x;}");

    assertRedeclareError(lines(
        "function f() {",
        "  let x;",
        "  if (a) {",
        "    var x;",
        "  }",
        "}"));

    assertNoWarning(
        lines("function f() {", "  if (a) {", "    let x;", "  }", "  var x;", "}"));

    assertNoWarning(
        lines("function f() {", "  if (a) { let x; }", "  if (b) { var x; }", "}"));

    assertRedeclareError("let x; var x;");
    assertRedeclareError("var x; let x;");
    assertRedeclareError("let x; let x;");
  }

  @Test
  public void testIllegalLetShadowing_withES6Modules() {
    assertRedeclareError(
        lines(
            "export function f() {", "  let x;", "  if (a) {", "    var x;", "  }", "}"));

    assertNoWarning(
        lines(
            "export function f() {", "  if (a) {", "    let x;", "  }", "  var x;", "}"));

    assertRedeclareError("export let x; var x;");
  }

  @Test
  public void testDuplicateLetConst() {
    assertRedeclareError("let x, x;");
    assertRedeclareError("const x = 0, x = 0;");
  }

  @Test
  public void testRedeclareInLabel() {
    assertRedeclareGlobal("a: var x, x;");
  }

  @Test
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

  @Test
  public void testIllegalConstShadowing() {
    assertRedeclareError("if (a) { const x = 3; var x;}");

    assertRedeclareError(lines(
        "function f() {",
        "  const x = 3;",
        "  if (a) {",
        "    var x;",
        "  }",
        "}"));
  }

  @Test
  public void testIllegalConstShadowing_withES6Modules() {
    assertRedeclareError(
        lines(
            "export function f() {", "  const x = 3;", "  if (a) {", "    var x;", "  }", "}"));
  }

  @Test
  public void testVarShadowing() {
    assertRedeclareGlobal("if (a) { var x; var x;}");
    assertRedeclareError("if (a) { var x; let x;}");

    assertRedeclare("function f() { var x; if (a) { var x; }}");
    assertRedeclareError("function f() { if (a) { var x; } let x;}");
    assertNoWarning("function f() { var x; if (a) { let x; }}");

    assertNoWarning(
        lines("function f() {", "  if (a) { var x; }", "  if (b) { let x; }", "}"));
  }

  @Test
  public void testVarShadowing_withES6Modules01() {
    assertRedeclare("export function f() { var x; if (a) { var x; }}");
  }

  @Test
  public void testVarShadowing_withES6Modules02() {
    assertRedeclareError("export function f() { if (a) { var x; } let x;}");
  }

  @Test
  public void testVarShadowing_withES6Modules03() {
    assertNoWarning("export function f() { var x; if (a) { let x; }}");
  }

  @Test
  public void testVarShadowing_withES6Modules04() {
    assertNoWarning(
        lines(
            "function f() {",
            "  if (a) { var x; }",
            "  if (b) { let x; }",
            "}"));
  }

  @Test
  public void testParameterShadowing() {
    assertRedeclareError("function f(x) { let x; }");
    assertRedeclareError("function f(x) { const x = 3; }");
    assertRedeclareError("function f(X) { class X {} }");

    assertRedeclare("function f(x) { function x() {} }");
    assertRedeclare("function f(x) { var x; }");
    assertRedeclare("function f(x=3) { var x; }");
    assertNoWarning("function f(...x) {}");
    assertRedeclare("function f(...x) { var x; }");
    assertRedeclare("function f(...x) { function x() {} }");
    assertRedeclare("function f(x=3) { function x() {} }");
    assertNoWarning("function f(x) { if (true) { let x; } }");
    assertNoWarning(
        lines(
            "function outer(x) {", "  function inner() {", "    let x = 1;", "  }", "}"));
    assertNoWarning(lines(
        "function outer(x) {",
        "  function inner() {",
        "    var x = 1;",
        "  }",
        "}"));

    assertRedeclare("function f({a, b}) { var a = 2 }");
    assertRedeclare("function f({a, b}) { if (!a) var a = 6; }");
  }

  @Test
  public void testParameterShadowing_withES6Modules() {
    assertRedeclareError("export function f(x) { let x; }");

    assertRedeclare("export function f(x) { function x() {} }");

    assertRedeclare("export function f(x=3) { var x; }");

    assertNoWarning("export function f(...x) {}");

    assertNoWarning(
        lines(
            "export function outer(x) {", "  function inner() {", "    var x = 1;", "  }", "}"));
  }

  @Test
  public void testReassignedConst() {
    assertReassign("const a = 0; a = 1;");
    assertReassign("const a = 0; a++;");
  }

  @Test
  public void testLetConstNotDirectlyInBlock() {
    testSame("if (true) var x = 3;");
    testError("if (true) let x = 3;", DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testError("if (true) const x = 3;", DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testError("if (true) class C {}", DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testError("if (true) function f() {}", DECLARATION_NOT_DIRECTLY_IN_BLOCK);
  }

  @Test
  public void testFunctionHoisting() {
    assertEarlyReferenceWarning("if (true) { f(); function f() {} }");
  }

  @Test
  public void testFunctionHoistingRedeclaration1() {
    String[] js = {
      "var x;",
      "function x() {}",
    };
    String message = "Variable x declared more than once. First occurrence: input0";
    testError(srcs(js), error(VarCheck.VAR_MULTIPLY_DECLARED_ERROR).withMessage(message));
  }

  @Test
  public void testFunctionHoistingRedeclaration2() {
    String[] js = {
      "function x() {}",
      "var x;",
    };
    String message = "Variable x declared more than once. First occurrence: input0";
    testError(srcs(js), error(VarCheck.VAR_MULTIPLY_DECLARED_ERROR).withMessage(message));
  }

  @Test
  public void testArrowFunction() {
    assertNoWarning("var f = x => { return x+1; };");
    assertNoWarning("var odds = [1,2,3,4].filter((n) => n%2 == 1)");
    assertRedeclare("var f = x => {var x;}");
    assertRedeclareError("var f = x => {let x;}");
  }

  @Test
  public void testArrowFunction_withES6Modules() {
    assertNoWarning("export var f = x => { return x+1; };");
    assertRedeclare("export var f = x => {var x;}");
    assertRedeclareError("export var f = x => {let x;}");
  }

  @Test
  public void testTryCatch() {
    assertRedeclareError(
        lines(
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
        lines(
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
        lines(
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

  @Test
  public void testTryCatch_withES6Modules() {
    assertRedeclareError(
        lines(
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

  @Test
  public void testClass() {
    assertNoWarning("class A { f() { return 1729; } }");
  }

  @Test
  public void testClass_withES6Modules() {
    assertNoWarning("export class A { f() { return 1729; } }");
  }

  @Test
  public void testRedeclareClassName() {
    assertNoWarning("var Clazz = class Foo {}; var Foo = 3;");
  }

  @Test
  public void testRedeclareClassName_withES6Modules() {
    assertNoWarning("export var Clazz = class Foo {}; var Foo = 3;");
  }

  @Test
  public void testClassExtend() {
    assertNoWarning("class A {} class C extends A {} C = class extends A {}");
  }

  @Test
  public void testClassExtend_withES6Modules() {
    assertNoWarning("export class A {} class C extends A {} C = class extends A {}");
  }

  @Test
  public void testArrayPattern() {
    assertNoWarning("var [a] = [1];");
    assertNoWarning("var [a, b] = [1, 2];");
    assertEarlyReferenceWarning("alert(a); var [a] = [1];");
    assertEarlyReferenceWarning("alert(b); var [a, b] = [1, 2];");

    assertEarlyReferenceWarning("[a] = [1]; var a;");
    assertEarlyReferenceWarning("[a, b] = [1]; var b;");
  }

  @Test
  public void testArrayPattern_withES6Modules01() {
    assertNoWarning("export var [a] = [1];");
  }

  @Test
  public void testArrayPattern_defaultValue() {
    assertNoWarning("var [a = 1] = [2];");
    assertNoWarning("var [a = 1] = [];");
    assertEarlyReferenceWarning("alert(a); var [a = 1] = [2];");
    assertEarlyReferenceWarning("alert(a); var [a = 1] = [];");

    assertEarlyReferenceWarning("alert(a); var [a = b] = [1];");
    assertEarlyReferenceWarning("alert(a); var [a = b] = [];");
  }

  @Test
  public void testArrayPattern_defaultValue_withES6Modules01() {
    assertNoWarning("export var [a = 1] = [2];");
  }

  @Test
  public void testObjectPattern() {
    assertNoWarning("var {a: b} = {a: 1};");
    assertNoWarning("var {a: b} = {};");
    assertNoWarning("var {a} = {a: 1};");

    // 'a' is not declared at all, so the 'a' passed to alert() references
    // the global variable 'a', and there is no warning.
    assertNoWarning("alert(a); var {a: b} = {};");
    assertNoWarning("alert(a); var {a: {a: b}} = {};");

    assertEarlyReferenceWarning("alert(b); var {a: b} = {a: 1};");
    assertEarlyReferenceWarning("alert(a); var {a} = {a: 1};");

    assertEarlyReferenceWarning("({a: b} = {}); var a, b;");
  }

  @Test
  public void testObjectPatternRest() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2018);
    assertNoWarning("var {a: b, ...r} = {a: 1};");
    assertNoWarning("var {a: b, ...r} = {};");
    assertNoWarning("var {a, ...r} = {a: 1};");

    assertNoWarning("alert(r);");
    assertEarlyReferenceWarning("alert(r); var {...r} = {a: 1};");

    assertNoWarning("({...a} = {});");
    assertEarlyReferenceWarning("({...a} = {}); var a;");
  }

  @Test
  public void testObjectPattern_withES6Modules01() {
    assertNoWarning("export var {a: b} = {a: 1};");
  }

  @Test
  public void testObjectPattern_defaultValue() {
    assertEarlyReferenceWarning("alert(b); var {a: b = c} = {a: 1};");
    assertEarlyReferenceWarning("alert(b); var c; var {a: b = c} = {a: 1};");
    assertEarlyReferenceWarning("var {a: b = c} = {a: 1}; var c;");
    assertEarlyReferenceWarning("alert(b); var {a: b = c} = {};");
    assertEarlyReferenceWarning("alert(a); var {a = c} = {a: 1};");
    assertEarlyReferenceWarning("alert(a); var {a = c} = {};");
  }

  @Test
  public void testObjectPattern_defaultValue_withES6Modules() {
    assertEarlyReferenceWarning("export var {a: b = c} = {a: 1}; var c;");
  }

  /** We can't catch all possible runtime errors but it's useful to have some basic checks. */
  @Test
  public void testDefaultParam() {
    assertEarlyReferenceError("function f(x=a) { let a; }");
    assertEarlyReferenceError(lines(
        "function f(x=a) { let a; }",
        "function g(x=1) { var a; }"));
    assertEarlyReferenceError("function f(x=a) { var a; }");
    assertEarlyReferenceError("function f(x=a()) { function a() {} }");
    assertEarlyReferenceError("function f(x=[a]) { var a; }");
    assertEarlyReferenceError("function f(x={a}) { let a; }");
    assertEarlyReferenceError("function f(x=y, y=2) {}");
    assertEarlyReferenceError("function f(x={y}, y=2) {}");
    assertEarlyReferenceError("function f(x=x) {}");
    assertEarlyReferenceError("function f([x]=x) {}");
    // x within a function isn't referenced at the time the default value for x is evaluated.
    assertNoWarning("function f(x=()=>x) {}");
    assertNoWarning("function f(x=a) {}");
    assertNoWarning("function f(x=a) {} var a;");
    assertNoWarning("let b; function f(x=b) { var b; }");
    assertNoWarning("function f(y = () => x, x = 5) { return y(); }");
    assertNoWarning("function f(x = new foo.bar()) {}");
    assertNoWarning("var foo = {}; foo.bar = class {}; function f(x = new foo.bar()) {}");
  }

  @Test
  public void testDefaultParam_withES6Modules() {
    assertEarlyReferenceError("export function f(x=a) { let a; }");
    assertNoWarning("export function f(x=()=>x) {}");
  }

  @Test
  public void testDestructuring() {
    testSame(lines(
        "function f() { ",
        "  var obj = {a:1, b:2}; ",
        "  var {a:c, b:d} = obj; ",
        "}"));
    testSame(lines(
        "function f() { ",
        "  var obj = {a:1, b:2}; ",
        "  var {a, b} = obj; ",
        "}"));

    assertRedeclare(
        lines(
            "function f() { ",
            "  var obj = {a:1, b:2}; ",
            "  var {a:c, b:d} = obj; ",
            "  var c = b;",
            "}"));

    assertEarlyReferenceWarning(
        lines("function f() { ", "  var {a:c, b:d} = obj;", "  var obj = {a:1, b:2};", "}"));
    assertEarlyReferenceWarning(
        lines("function f() { ", "  var {a, b} = obj;", "  var obj = {a:1, b:2};", "}"));
    assertEarlyReferenceWarning(
        lines("function f() { ", "  var e = c;", "  var {a:c, b:d} = {a:1, b:2};", "}"));
  }

  @Test
  public void testDestructuring_withES6Modules() {
    testSame(
        lines(
            "export function f() { ", "  var obj = {a:1, b:2}; ", "  var {a:c, b:d} = obj; ", "}"));

    assertRedeclare(
        lines(
            "export function f() { ",
            "  var obj = {a:1, b:2}; ",
            "  var {a:c, b:d} = obj; ",
            "  var c = b;",
            "}"));

    assertEarlyReferenceWarning(
        lines("export function f() { ", "  var {a:c, b:d} = obj;", "  var obj = {a:1, b:2};", "}"));
  }

  @Test
  public void testDestructuringInLoop() {
    testSame("for (let {length: x} in obj) {}");

    testSame("for (let [{length: z}, w] in obj) {}");
  }

  @Test
  public void testReferencingPreviouslyDeclaredVariableInConst() {
    testSame("const [a, b = a] = [];");
    // TODO(b/111441110): don't error on this. it's valid code.
    assertEarlyReferenceError("for (const [a, b = a] of []);");
  }

  @Test
  public void testEarlyReferenceInInnerBlock() {
    assertEarlyReferenceError("for (x of [1, 2, 3]) {} let x;");
    assertEarlyReferenceError("{ x; } let x;");
    assertEarlyReferenceError("{ C; } class C {}");
    assertEarlyReferenceWarning("{ x; }  var x;");
  }

  @Test
  public void testEarlyVariableReferenceInsideFunction() {
    testSame("function f() { x; } let x; f(); ");
    testSame("function f() { const f = () => x; let x = 3; return f; }");
    // NOTE: this will cause an error at runtime, but we don't report it because we don't track
    // where `f` is being called.
    testSame("function f() { x; } f(); let x;");
    testSame("function f() { x; } f(); var x;");
  }

  @Test
  public void testEnhancedForLoopTemporalDeadZone() {
    assertEarlyReferenceError("for (let x of [x]);");
    assertEarlyReferenceError("for (let x in [x]);");
    assertEarlyReferenceError("for (const x of [x]);");
    testSame("for (var x of [x]);");
    testSame("for (let x of [() => x]);");
    testSame("let x = 1; for (let y of [x]);");
  }

  @Test
  public void testEnhancedForLoopTemporalDeadZone_withES6Modules() {
    testSame("export let x = 1; for (let y of [x]);");
  }

  @Test
  public void testRedeclareVariableFromImport() {
    assertRedeclareError("import {x} from 'whatever'; let x = 0;");
    assertRedeclareError("import {x} from 'whatever'; const x = 0;");
    assertRedeclareError("import {x} from 'whatever'; var x = 0;");
    assertRedeclareError("import {x} from 'whatever'; function x() {}");
    assertRedeclareError("import {x} from 'whatever'; class x {}");

    assertRedeclareError("import x from 'whatever'; let x = 0;");

    assertRedeclareError("import * as ns from 'whatever'; let ns = 0;");

    assertRedeclareError("import {y as x} from 'whatever'; let x = 0;");

    assertRedeclareError("import {x} from 'whatever'; let {x} = {};");

    assertRedeclareError("import {x} from 'whatever'; let [x] = [];");

    assertRedeclareError("import {x, x} from 'whatever';");
    assertRedeclareError("import {x, y as x} from 'whatever';");
    assertRedeclareError("import {z as x, y as x} from 'whatever';");
    assertRedeclareError("import {x} from 'first'; import {x} from 'second';");
    assertRedeclareError("import {x} from 'first'; import {a as x} from 'second';");
    assertRedeclareError("import {b as x} from 'first'; import {a as x} from 'second';");

    testSame("import {x} from 'whatever'; function f() { let x = 0; }");

    testSame("import {x as x} from 'whatever'; function f() { let x = 0; }");
    testSame("import {y as x} from 'whatever'; function f() { let x = 0; }");
  }

  /**
   * Expects the JS to generate one bad-read error.
   */
  private void assertRedeclare(String js) {
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

  /** Expects the JS to generate one bad-write warning. */
  private void assertEarlyReferenceWarning(String js) {
    testWarning(js, EARLY_REFERENCE);
  }

  private void assertEarlyReferenceError(String js) {
    testError(js, EARLY_REFERENCE_ERROR);
  }

  /**
   * Expects the JS to generate one unused local error.
   */
  private void assertUnused(String js) {
    testWarning(js, UNUSED_LOCAL_ASSIGNMENT);
  }

  /**
   * Expects the JS to generate no errors or warnings.
   */
  private void assertNoWarning(String js) {
    testSame(js);
  }
}
