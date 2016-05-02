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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Test that errors and warnings are generated in appropriate cases and
 * appropriate cases only by VariableReferenceCheck when checking ES6 input
 *
 */
public final class Es6VariableReferenceCheckTest extends CompilerTestCase {

  private static final String LET_RUN =
      "let a = 1; let b = 2; let c = a + b, d = c;";

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new VariableReferenceCheck(compiler);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
  }

  public void testCorrectCode() {
    assertNoWarning(LET_RUN);
    assertNoWarning("function f() { " + LET_RUN + "}");
    assertNoWarning("try { let e; } catch (e) { let x; }");
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

    assertNoWarning(LINE_JOINER.join(
        "function f() {",
        "  if (a) {",
        "    let x;",
        "  }",
        "  var x;",
        "}"));

    assertNoWarning(
        LINE_JOINER.join(
            "function f() {",
            "  if (a) { let x; }",
            "  if (b) { var x; }",
            "}"));

    assertRedeclareError("let x; var x;");
    assertRedeclareError("var x; let x;");
    assertRedeclareError("let x; let x;");
  }

  public void testDuplicateLetConst() {
    assertRedeclareError("let x, x;");
    assertRedeclareError("const x = 0, x = 0;");
  }

  public void testIllegalBlockScopedEarlyReference() {
    assertEarlyReferenceError("let x = x");
    assertEarlyReferenceError("const x = x");
    assertEarlyReferenceError("let x = x || 0");
    assertEarlyReferenceError("const x = x || 0");
    // In the following cases, "x" might not be reachable but we warn anyways
    assertEarlyReferenceError("let x = expr || x");
    assertEarlyReferenceError("const x = expr || x");
    assertEarlyReferenceError("X; class X {};");
  }

  public void testCorrectEarlyReference() {
    assertNoWarning("var goog = goog || {}");
    assertNoWarning("function f() { a = 2; } var a = 2;");
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

  public void testVarShadowing() {
    assertRedeclareGlobal("if (a) { var x; var x;}");
    assertRedeclareError("if (a) { var x; let x;}");

    assertRedeclare("function f() { var x; if (a) { var x; }}");
    assertRedeclareError("function f() { if (a) { var x; } let x;}");
    assertNoWarning("function f() { var x; if (a) { let x; }}");

    assertNoWarning(
        LINE_JOINER.join(
            "function f() {",
            "  if (a) { var x; }",
            "  if (b) { let x; }",
            "}"));
  }

  public void testParameterShadowing() {
    assertParameterShadowed("function f(x) { let x; }");
    assertParameterShadowed("function f(x) { const x = 3; }");
    assertParameterShadowed("function f(X) { class X {} }");

    assertRedeclare("function f(x) { function x() {} }");
    assertRedeclare("function f(x) { var x; }");
    assertRedeclare("function f(x=3) { var x; }");
    assertNoWarning("function f(...x) {}");
    assertRedeclare("function f(...x) { var x; }");
    assertRedeclare("function f(...x) { function x() {} }");
    assertRedeclare("function f(x=3) { function x() {} }");
    assertNoWarning("function f(x) { if (true) { let x; } }");
    assertNoWarning(LINE_JOINER.join(
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

    assertRedeclare("function f({a, b}) { var a = 2 }");
    assertRedeclare("function f({a, b}) { if (!a) var a = 6; }");
  }

  public void testReassignedConst() {
    assertReassign("const a = 0; a = 1;");
    assertReassign("const a = 0; a++;");
  }

  public void testLetConstNotDirectlyInBlock() {
    testSame("if (true) var x = 3;");
    testError("if (true) let x = 3;",
        VariableReferenceCheck.DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testError("if (true) const x = 3;",
        VariableReferenceCheck.DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testError("if (true) class C {}",
        VariableReferenceCheck.DECLARATION_NOT_DIRECTLY_IN_BLOCK);
    testError("if (true) function f() {}",
        VariableReferenceCheck.DECLARATION_NOT_DIRECTLY_IN_BLOCK);
  }

  public void testFunctionHoisting() {
    assertEarlyReference("if (true) { f(); function f() {} }");
  }

  public void testFunctionHoistingRedeclaration1() {
    String[] js = {
      "var x;",
      "function x() {}",
    };
    String message = "Variable x first declared in input0";
    test(js, null, VarCheck.VAR_MULTIPLY_DECLARED_ERROR, null, message);
  }

  public void testFunctionHoistingRedeclaration2() {
    String[] js = {
      "function x() {}",
      "var x;",
    };
    String message = "Variable x first declared in input0";
    test(js, null, VarCheck.VAR_MULTIPLY_DECLARED_ERROR, null, message);
  }

  public void testArrowFunction() {
    assertNoWarning("var f = x => { return x+1; };");
    assertNoWarning("var odds = [1,2,3,4].filter((n) => n%2 == 1)");
    assertRedeclare("var f = x => {var x;}");
    assertParameterShadowed("var f = x => {let x;}");
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

  public void testClass() {
    assertNoWarning("class A { f() { return 1729; } }");
  }

  public void testRedeclareClassName() {
    assertNoWarning("var Clazz = class Foo {}; var Foo = 3;");
  }

  public void testClassExtend() {
    assertNoWarning("class A {} class C extends A {} C = class extends A {}");
  }

  public void testArrayPattern() {
    assertNoWarning("var [a] = [1];");
    assertNoWarning("var [a, b] = [1, 2];");
    assertEarlyReference("alert(a); var [a] = [1];");
    assertEarlyReference("alert(b); var [a, b] = [1, 2];");

    assertEarlyReference("[a] = [1]; var a;");
    assertEarlyReference("[a, b] = [1]; var b;");
  }

  public void testArrayPattern_defaultValue() {
    assertNoWarning("var [a = 1] = [2];");
    assertNoWarning("var [a = 1] = [];");
    assertEarlyReference("alert(a); var [a = 1] = [2];");
    assertEarlyReference("alert(a); var [a = 1] = [];");

    assertEarlyReference("alert(a); var [a = b] = [1];");
    assertEarlyReference("alert(a); var [a = b] = [];");
  }

  public void testObjectPattern() {
    assertNoWarning("var {a: b} = {a: 1};");
    assertNoWarning("var {a: b} = {};");
    assertNoWarning("var {a} = {a: 1};");

    // 'a' is not declared at all, so the 'a' passed to alert() references
    // the global variable 'a', and there is no warning.
    assertNoWarning("alert(a); var {a: b} = {};");

    assertEarlyReference("alert(b); var {a: b} = {a: 1};");
    assertEarlyReference("alert(a); var {a} = {a: 1};");

    assertEarlyReference("({a: b} = {}); var a, b;");
  }

  public void testObjectPattern_defaultValue() {
    assertEarlyReference("alert(b); var {a: b = c} = {a: 1};");
    assertEarlyReference("alert(b); var {a: b = c} = {};");
    assertEarlyReference("alert(a); var {a = c} = {a: 1};");
    assertEarlyReference("alert(a); var {a = c} = {};");
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
    assertNoWarning("function f(x=a) {}");
    assertNoWarning("function f(x=a) {} var a;");
    assertNoWarning("let b; function f(x=b) { var b; }");
    assertNoWarning("function f(y = () => x, x = 5) { return y(); }");
    assertNoWarning("function f(x = new foo.bar()) {}");
    assertNoWarning("var foo = {}; foo.bar = class {}; function f(x = new foo.bar()) {}");
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

    assertRedeclare(LINE_JOINER.join(
        "function f() { ",
        "  var obj = {a:1, b:2}; ",
        "  var {a:c, b:d} = obj; ",
        "  var c = b;",
        "}"));

    assertEarlyReference(LINE_JOINER.join(
        "function f() { ",
        "  var {a:c, b:d} = obj;",
        "  var obj = {a:1, b:2};",
        "}"));
    assertEarlyReference(LINE_JOINER.join(
        "function f() { ",
        "  var {a, b} = obj;",
        "  var obj = {a:1, b:2};",
        "}"));
    assertEarlyReference(LINE_JOINER.join(
        "function f() { ",
        "  var e = c;",
        "  var {a:c, b:d} = {a:1, b:2};",
        "}"));
  }

  public void testDestructuringInLoop() {
    testSame("for (let {length: x} in obj) {}");

    testSame("for (let [{length: z}, w] in obj) {}");
  }

  private void assertReassign(String js) {
    testError(js, VariableReferenceCheck.REASSIGNED_CONSTANT);
  }

  private void assertRedeclare(String js) {
    testWarning(js, VariableReferenceCheck.REDECLARED_VARIABLE);
  }

  private void assertRedeclareGlobal(String js) {
    testError(js, VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  private void assertRedeclareError(String js) {
    testError(js, VariableReferenceCheck.REDECLARED_VARIABLE_ERROR);
  }

  private void assertParameterShadowed(String js) {
    testError(js, VariableReferenceCheck.REDECLARED_VARIABLE_ERROR);
  }

  private void assertEarlyReference(String js) {
    testSame(js, VariableReferenceCheck.EARLY_REFERENCE);
  }

  private void assertEarlyReferenceError(String js) {
    testError(js, VariableReferenceCheck.EARLY_REFERENCE_ERROR);
  }

  /**
   * Expects the JS to generate no errors or warnings.
   */
  private void assertNoWarning(String js) {
    testSame(js);
  }
}
