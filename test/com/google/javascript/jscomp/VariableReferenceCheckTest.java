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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Test that warnings are generated in appropriate cases and appropriate
 * cases only by VariableReferenceCheck
 *
 */
public final class VariableReferenceCheckTest extends Es6CompilerTestCase {

  private static final String VARIABLE_RUN =
      "var a = 1; var b = 2; var c = a + b, d = c;";

  private boolean enableUnusedLocalAssignmentCheck = false;

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    if (enableUnusedLocalAssignmentCheck) {
      options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    }
    return options;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    // Treats bad reads as errors, and reports bad write warnings.
    return new VariableReferenceCheck(compiler);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testCorrectCode() {
    assertNoWarning("function foo(d) { (function() { d.foo(); }); d.bar(); } ");
    assertNoWarning("function foo() { bar(); } function bar() { foo(); } ");
    assertNoWarning("function f(d) { d = 3; }");
    assertNoWarning(VARIABLE_RUN);
    assertNoWarning("if (a) { var x; }");
    assertNoWarning("function f() { " + VARIABLE_RUN + "}");
  }

  public void testCorrectShadowing() {
    assertNoWarning(VARIABLE_RUN + "function f() { " + VARIABLE_RUN + "}");
  }

  public void testCorrectRedeclare() {
    assertNoWarning(
        "function f() { if (1) { var a = 2; } else { var a = 3; } }");
  }

  public void testCorrectRecursion() {
    assertNoWarning("function f() { var x = function() { x(); }; }");
  }

  public void testCorrectCatch() {
    assertNoWarning("function f() { try { var x = 2; } catch (x) {} }");
    assertNoWarning("function f(e) { e = 3; try {} catch (e) {} }");
  }

  public void testRedeclare() {
    // Only test local scope since global scope is covered elsewhere
    assertRedeclare("function f() { var a = 2; var a = 3; }");
    assertRedeclare("function f(a) { var a = 2; }");
    assertRedeclare("function f(a) { if (!a) var a = 6; }");
  }

  public void testEarlyReference() {
    assertUndeclared("function f() { a = 2; var a = 3; }");
  }

  public void testCorrectEarlyReference() {
    assertNoWarning("var goog = goog || {}");
    assertNoWarning("function f() { a = 2; } var a = 2;");
  }

  public void testUnreferencedBleedingFunction() {
    assertNoWarning("var x = function y() {}");
    assertNoWarning("var x = function y() {}; var y = 1;");
  }

  public void testReferencedBleedingFunction() {
    assertNoWarning("var x = function y() { return y(); }");
  }

  public void testDoubleDeclaration() {
    assertRedeclare("function x(y) { if (true) { var y; } }");
  }

  public void testDoubleDeclaration2() {
    assertRedeclare("function x() { var y; if (true) { var y; } }");
  }

  public void testHoistedFunction1() {
    assertNoWarning("f(); function f() {}");
  }

  public void testHoistedFunction2() {
    assertNoWarning("function g() { f(); function f() {} }");
  }

  public void testNonHoistedFunction() {
    assertUndeclared("if (true) { f(); function f() {} }");
  }

  public void testNonHoistedFunction2() {
    assertNoWarning("if (false) { function f() {} f(); }");
  }

  public void testNonHoistedFunction3() {
    assertNoWarning("function g() { if (false) { function f() {} f(); }}");
  }

  public void testNonHoistedFunction4() {
    assertAmbiguous("if (false) { function f() {} }  f();");
  }

  public void testNonHoistedFunction5() {
    assertAmbiguous("function g() { if (false) { function f() {} }  f(); }");
  }

  public void testNonHoistedFunction6() {
    assertUndeclared("if (false) { f(); function f() {} }");
  }

  public void testNonHoistedFunction7() {
    assertUndeclared("function g() { if (false) { f(); function f() {} }}");
  }

  public void testNonHoistedRecursiveFunction1() {
    assertNoWarning("if (false) { function f() { f(); }}");
  }

  public void testNonHoistedRecursiveFunction2() {
    assertNoWarning("function g() { if (false) { function f() { f(); }}}");
  }

  public void testNonHoistedRecursiveFunction3() {
    assertNoWarning("function g() { if (false) { function f() { f(); g(); }}}");
  }

  public void testDestructuringInFor() {
    testSameEs6("for (let [key, val] of X){}");
    testSameEs6("for (let [key, [nestKey, nestVal], val] of X){}");

    testSameEs6("var {x: a, y: b} = {x: 1, y: 2}; a++; b++;");
    testWarningEs6("a++; var {x: a} = {x: 1};",
        VariableReferenceCheck.EARLY_REFERENCE);
  }

  public void testNoWarnInExterns1() {
    // Verify duplicate suppressions are properly recognized.
    String externs = "var google; /** @suppress {duplicate} */ var google";
    String code = "";
    testSame(externs, code, null);
  }

  public void testNoWarnInExterns2() {
    // Verify we don't complain about early references in externs
    String externs = "window; var window;";
    String code = "";
    testSame(externs, code, null);
  }

  public void testUnusedLocalVar() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("function f() { var a; }");
    assertUnused("function f() { var a = 2; }");
    assertUnused("function f() { var a; a = 2; }");
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

  public void testUnusedLocalConst() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnusedEs6("function f() { const a = 2; }");
  }

  public void testUnusedLocalArgNoWarning() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f(a) {}");
  }

  public void testUnusedGlobalNoWarning() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("var a = 2;");
  }

  public void testUnusedAssignedInInnerFunction() {
    enableUnusedLocalAssignmentCheck = true;
    assertUnused("function f() { var x = 1; function g() { x = 2; } }");
  }

  public void testIncrementDecrementResultUsed() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f() { var x = 5; while (x-- > 0) {} }");
    assertNoWarning("function f() { var x = -5; while (x++ < 0) {} }");
    assertNoWarning("function f() { var x = 5; while (--x > 0) {} }");
    assertNoWarning("function f() { var x = -5; while (++x < 0) {} }");
  }

  public void testUsedInInnerFunction() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f() { var x = 1; function g() { use(x); } }");
  }

  public void testUsedInShorthandObjLit() {
    enableUnusedLocalAssignmentCheck = true;
    testSameEs6("function f() { var x = 1; return {x}; }");
  }

  public void testUnusedCatch() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("function f() { try {} catch (x) {} }");
  }

  public void testIncrementCountsAsUse() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("var a = 2; var b = []; b[a++] = 1;");
  }

  public void testForIn() {
    enableUnusedLocalAssignmentCheck = true;
    assertNoWarning("for (var prop in obj) {}");
    assertNoWarning("for (prop in obj) {}");
    assertNoWarning("var prop; for (prop in obj) {}");
  }
  /**
   * Expects the JS to generate one bad-read error.
   */
  private void assertRedeclare(String js) {
    testWarning(js, VariableReferenceCheck.REDECLARED_VARIABLE);
  }

  /**
   * Expects the JS to generate one bad-write warning.
   */
  private void assertUndeclared(String js) {
    testWarning(js, VariableReferenceCheck.EARLY_REFERENCE);
  }

  /**
   * Expects the JS to generate one bad-write warning.
   */
  private void assertAmbiguous(String js) {
    testError(js, VariableReferenceCheck.AMBIGUOUS_FUNCTION_DECL,
        LanguageMode.ECMASCRIPT5);
    testSameEs6(js); // In ES6, these are block scoped functions, so no ambiguity.
  }

  /**
   * Expects the JS to generate one unused local error.
   */
  private void assertUnused(String js) {
    testWarning(js, VariableReferenceCheck.UNUSED_LOCAL_ASSIGNMENT);
  }

  /**
   * Expects the JS to generate one unused local error.
   */
  private void assertUnusedEs6(String js) {
    testWarningEs6(js, VariableReferenceCheck.UNUSED_LOCAL_ASSIGNMENT);
  }

  /**
   * Expects the JS to generate no errors or warnings.
   */
  private void assertNoWarning(String js) {
    testSame(js);
  }
}
