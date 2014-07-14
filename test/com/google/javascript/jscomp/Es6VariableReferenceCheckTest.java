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

package com.google.javascript.jscomp;

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;



/**
 * Test that errors and warnings are generated in appropriate cases and
 * appropriate cases only by VariableReferenceCheck when checking ES6 input
 *
 */
public class Es6VariableReferenceCheckTest extends CompilerTestCase {

  private static final String VARIABLE_RUN =
      "var a = 1; var b = 2; var c = a + b, d = c;";

  private static final String LET_RUN =
      "let a = 1; let b = 2; let c = a + b, d = c;";

  private boolean enableAmbiguousFunctionCheck = false;

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    if (enableAmbiguousFunctionCheck) {
      options.setWarningLevel(
          DiagnosticGroups.AMBIGUOUS_FUNCTION_DECL, CheckLevel.WARNING);
    }
    return options;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    // Treats bad reads as errors, and reports bad write warnings.
    return new VariableReferenceCheck(compiler, CheckLevel.WARNING);
  }

  @Override
  public void setUp() throws Exception {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    enableAmbiguousFunctionCheck = false;
  }

  public void testCorrectCode() {
    assertNoWarning("function foo(d) { (function() { d.foo(); }); d.bar(); } ");
    assertNoWarning("function foo() { bar(); } function bar() { foo(); } ");
    assertNoWarning("function f(d) { d = 3; }");
    assertNoWarning(VARIABLE_RUN);
    assertNoWarning(LET_RUN);
    assertNoWarning("function f() { " + VARIABLE_RUN + "}");
    assertNoWarning("function f() { " + LET_RUN + "}");
    assertNoWarning("if (a) { var x; }");
    assertNoWarning("try { let e; } catch (e) { let x; }");
  }

  public void testUndeclaredLet() {
    assertUndeclaredError(Joiner.on('\n').join(
        "if (a) {",
        "  x = 3;",
        "  let x;",
        "}"
    ));

    assertUndeclaredError(Joiner.on('\n').join(
        "var x = 1;",
        "if (true) {",
        "  x++;",
        "  let x = 3;",
        "}"
    ));
  }

  public void testUndeclaredConst() {
    assertUndeclaredError(Joiner.on('\n').join(
        "if (a) {",
        "  x = 3;",
        "  const x = 3;",
        "}"
    ));

    // For the following, IE 11 gives "Assignment to const", but technically
    // they are also undeclared references, which get caught in the first place.
    assertUndeclaredError(Joiner.on('\n').join(
        "var x = 1;",
        "if (true) {",
        "  x++;",
        "  const x = 3;",
        "}"
    ));

    assertUndeclaredError("a = 1; const a = 0;");
    assertUndeclaredError("a++; const a = 0;");
  }

  public void testIllegalLetShadowing() {
    assertRedeclareError(Joiner.on('\n').join(
        "if (a) {",
        "  let x;",
        "  var x;",
        "}"
    ));

    assertRedeclareError(Joiner.on('\n').join(
        "if (a) {",
        "  let x;",
        "  let x;",
        "}"
    ));

    assertRedeclareError(Joiner.on('\n').join(
        "function f() {",
        "  let x;",
        "  if (a) {",
        "    var x;",
        "  }",
        "}"
    ));

    assertNoWarning(Joiner.on('\n').join(
        "function f() {",
        "  if (a) {",
        "    let x;",
        "  }",
        "  var x;",
        "}"
    ));

    assertNoWarning(Joiner.on('\n').join(
        "function f() {",
        "  if (a) {",
        "    let x;",
        "  }",
        "  if (b) {",
        "    var x;",
        "  }",
        "}"
    ));

    assertRedeclareError("let x; var x;");
    assertRedeclareError("var x; let x;");
    assertRedeclareError("let x; let x;");
  }

  public void testDuplicateLetConst() {
    assertRedeclareError("let x, x;");
    assertRedeclareError("const x = 0, x = 0;");
  }

  public void testIllegalLetConstEarlyReference() {
    assertUndeclaredError("let x = x");
    assertUndeclaredError("const x = x");
    assertUndeclaredError("let x = x || 0");
    assertUndeclaredError("const x = x || 0");
    // In the following cases, "x" might not be reachable but we warn anyways
    assertUndeclaredError("let x = expr || x");
    assertUndeclaredError("const x = expr || x");
  }

  public void testCorrectEarlyReference() {
    assertNoWarning("var goog = goog || {}");
    assertNoWarning("function f() { a = 2; } var a = 2;");
  }

  public void testIllegalConstShadowing() {
    assertRedeclareError(Joiner.on('\n').join(
        "if (a) {",
        "  const x = 3;",
        "  var x;",
        "}"
    ));

    assertRedeclareError(Joiner.on('\n').join(
        "function f() {",
        "  const x = 3;",
        "  if (a) {",
        "    var x;",
        "  }",
        "}"
    ));
  }

  public void testVarShadowing() {
    assertRedeclare(Joiner.on('\n').join(
        "if (a) {",
        "  var x;",
        "  var x;",
        "}"
    ));

    assertRedeclareError(Joiner.on('\n').join(
        "if (a) {",
        "  var x;",
        "  let x;",
        "}"
    ));

    assertRedeclare(Joiner.on('\n').join(
        "function f() {",
        "  var x;",
        "  if (a) {",
        "    var x;",
        "  }",
        "}"
    ));

    assertRedeclareError(Joiner.on('\n').join(
        "function f() {",
        "  if (a) {",
        "    var x;",
        "  }",
        "  let x;",
        "}"
    ));

    assertNoWarning(Joiner.on('\n').join(
        "function f() {",
        "  var x;",
        "  if (a) {",
        "    let x;",
        "  }",
        "}"
    ));

    assertNoWarning(Joiner.on('\n').join(
        "function f() {",
        "  if (a) {",
        "    var x;",
        "  }",
        "  if (b) {",
        "    let x;",
        "  }",
        "}"
    ));
  }

  public void testParameterShadowing() {
    assertParameterShadowed("function f(x) { let x; }");
    assertParameterShadowed("function f(x) { const x = 3; }");
    assertRedeclare("function f(x) { function x() {} }");
    assertParameterShadowed("function f(X) { class X {} }");
    assertRedeclare("function f(x) { var x; }");
    assertParameterShadowed("function f(x=3) { var x; }");
    assertParameterShadowed("function f(...x) { var x; }");
    assertParameterShadowed("function f(x=3) { function x() {} }");
    assertParameterShadowed("function f(...x) { function x() {} }");
    assertNoWarning("function f(x) { if (true) { let x; } }");
  }

  public void testReassignedConst() {
    assertReassign("const a = 0; a = 1;");
    assertReassign("const a = 0; a++;");
  }

  public void testLetConstNotDirectlyInBlock() {
    testSame("if (true) let x = 3;", VariableReferenceCheck.DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testSame("if (true) const x = 3;", VariableReferenceCheck.DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testSame("if (true) function f() {}", VariableReferenceCheck.DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testSame("if (true) class C {}", VariableReferenceCheck.DECLARATION_NOT_DIRECTLY_IN_BLOCK);
  }

  public void testFunctionHoisting() {
    assertUndeclared("if (true) { f(); function f() {} }");
  }

  public void testArrowFunction() {
    assertNoWarning("var f = x => { return x+1; };");
    assertNoWarning("var odds = [1,2,3,4].filter((n) => n%2 == 1)");
    assertRedeclare("var f = x => {var x;}");
    assertParameterShadowed("var f = x => {let x;}");
  }

  public void testTryCatch() {
    assertRedeclareError(Joiner.on('\n').join(
        "function f() {",
        "  try {",
        "    let e = 0;",
        "    if (true) {",
        "      let e = 1;",
        "    }",
        "  } catch (e) {",
        "    let e;",
        "  }",
        "}"
    ));

    assertRedeclareError(Joiner.on('\n').join(
        "function f() {",
        "  try {",
        "    let e = 0;",
        "    if (true) {",
        "      let e = 1;",
        "    }",
        "  } catch (e) {",
        "      var e;",
        "  }",
        "}"
    ));

    assertRedeclareError(Joiner.on('\n').join(
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
        "}"
    ));
  }

  public void testClass() {
    assertNoWarning("class A { f() { return 1729; } }");
  }

  public void testClassExtend() {
    assertNoWarning("class A {} class C extends A {} C = class extends A {}");
  }

  /*public void testDefaultParam() {
    assertUndeclaredError("function f(x=a) { var a; }");
    assertUndeclaredError("function f(x=a()) { function a() {} }");
    assertUndeclaredError("function f(x=[a, b]) { var a; var b; }");
    assertUndeclaredError("function f(x=(function f(){ return a; })()) { var a; }");
    assertNoWarning("let b; function f(x=b) { var b; }");
  }*/

  /**
   * Expects the JS to generate one bad-read error.
   */
  private void assertReassign(String js) {
    testSame(js, VariableReferenceCheck.REASSIGNED_CONSTANT);
  }

  /**
   * Expects the JS to generate one bad-read warning.
   */
  private void assertRedeclare(String js) {
    testSame(js, VariableReferenceCheck.REDECLARED_VARIABLE);
  }

  /**
   * Expects the JS to generate one bad-read error.
   */
  private void assertRedeclareError(String js) {
    testSame(js, VariableReferenceCheck.REDECLARED_VARIABLE_ERROR);
  }

  /**
   * Expects the JS to generate one bad-read error.
   */
  private void assertParameterShadowed(String js) {
    testSame(js, VariableReferenceCheck.PARAMETER_SHADOWED_ERROR);
  }

  /**
   * Expects the JS to generate one bad-write error.
   */
  private void assertUndeclared(String js) {
    testSame(js, VariableReferenceCheck.UNDECLARED_REFERENCE);
  }

  /**
   * Expects the JS to generate one bad-write error.
   */
  private void assertUndeclaredError(String js) {
    testSame(js, VariableReferenceCheck.UNDECLARED_REFERENCE_ERROR);
  }

  /**
   * Expects the JS to generate no errors or warnings.
   */
  private void assertNoWarning(String js) {
    testSame(js);
  }
}
