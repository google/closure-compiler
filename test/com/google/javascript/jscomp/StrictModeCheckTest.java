/*
 * Copyright 2009 The Closure Compiler Authors.
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

public class StrictModeCheckTest extends CompilerTestCase {
  private static final String EXTERNS = "var arguments; function eval(str) {}";

  private boolean noVarCheck;
  private boolean noCajaChecks;

  public StrictModeCheckTest() {
    super(EXTERNS);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    noVarCheck = false;
    noCajaChecks = false;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new StrictModeCheck(compiler, noVarCheck, noCajaChecks);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testEval() {
    test("function foo() { eval('a'); }", null,
         StrictModeCheck.EVAL_USE);
  }

  public void testEval2() {
    testSame("function foo(eval) {}",
         StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval3() {
    testSame("function foo() {} foo.eval = 3;");
  }

  public void testEval4() {
    testSame("function foo() { var eval = 3; }",
         StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval5() {
    testSame("function eval() {}", StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval6() {
    testSame("try {} catch (eval) {}", StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval7() {
    testSame("var o = {eval: 3};");
  }

  public void testEval8() {
    testSame("var a; eval: while (true) { a = 3; }");
  }

  public void testUnknownVariable() {
    testSame("function foo(a) { a = b; }", StrictModeCheck.UNKNOWN_VARIABLE);
  }

  public void testUnknownVariable2() {
    testSame("a: while (true) { a = 3; }", StrictModeCheck.UNKNOWN_VARIABLE);
  }

  public void testUnknownVariable3() {
    testSame("try {} catch (ex) { ex = 3; }");
  }

  public void testArguments() {
    testSame("function foo(arguments) {}",
         StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments2() {
    testSame("function foo() { var arguments = 3; }",
         StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments3() {
    testSame("function arguments() {}",
         StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments4() {
    testSame("try {} catch (arguments) {}",
         StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments5() {
    testSame("var o = {arguments: 3};");
  }

  public void testEvalAssignment() {
    noCajaChecks = true;
    testSame("function foo() { eval = []; }",
         StrictModeCheck.EVAL_ASSIGNMENT);
  }

  public void testEvalAssignment2() {
    test("function foo() { eval = []; }", null, StrictModeCheck.EVAL_USE);
  }

  public void testAssignToArguments() {
    testSame("function foo() { arguments = []; }",
         StrictModeCheck.ARGUMENTS_ASSIGNMENT);
  }

  public void testDeleteVar() {
    testSame("var a; delete a", StrictModeCheck.DELETE_VARIABLE);
  }

  public void testDeleteFunction() {
    testSame("function a() {} delete a", StrictModeCheck.DELETE_VARIABLE);
  }

  public void testDeleteArgument() {
    testSame("function b(a) { delete a; }",
        StrictModeCheck.DELETE_VARIABLE);
  }

  public void testDeleteProperty() {
    testSame("function f(obj) { delete obj.a; }");
  }

  public void testIllegalName() {
    test("var a__ = 3;", null, StrictModeCheck.ILLEGAL_NAME);
  }

  public void testIllegalName2() {
    test("function a__() {}", null, StrictModeCheck.ILLEGAL_NAME);
  }

  public void testIllegalName3() {
    test("function f(a__) {}", null, StrictModeCheck.ILLEGAL_NAME);
  }

  public void testIllegalName4() {
    test("try {} catch (a__) {}", null, StrictModeCheck.ILLEGAL_NAME);
  }

  public void testIllegalName5() {
    noVarCheck = true;
    test("var a = b__;", null, StrictModeCheck.ILLEGAL_NAME);
  }

  public void testIllegalName6() {
    test("function f(obj) { return obj.a__; }", null,
         StrictModeCheck.ILLEGAL_NAME);
  }

  public void testIllegalName7() {
    noCajaChecks = true;
    testSame("var a__ = 3;");
  }

  public void testIllegalName8() {
    test("var o = {a__: 3};", null, StrictModeCheck.ILLEGAL_NAME);
    test("var o = {b: 3, a__: 4};", null, StrictModeCheck.ILLEGAL_NAME);
    test("var o = {b: 3, get a__() {}};", null, StrictModeCheck.ILLEGAL_NAME);
    test("var o = {b: 3, set a__(c) {}};", null, StrictModeCheck.ILLEGAL_NAME);
  }

  public void testIllegalName9() {
    test("a__: while (true) { var b = 3; }", null,
         StrictModeCheck.ILLEGAL_NAME);
  }

  public void testIllegalName10() {
    // Validate that number as objlit key
    testSame("var o = {1: 3, 2: 4};");
  }

  public void testDuplicateObjectLiteralKey() {
    testSame("var o = {a: 1, b: 2, c: 3};");
    testSame("var x = { get a() {}, set a(p) {} };");

    testSame("var o = {a: 1, b: 2, a: 3};",
        StrictModeCheck.DUPLICATE_OBJECT_KEY);
    testSame("var x = { get a() {}, get a() {} };",
         StrictModeCheck.DUPLICATE_OBJECT_KEY);
    testSame("var x = { get a() {}, a: 1 };",
         StrictModeCheck.DUPLICATE_OBJECT_KEY);
    testSame("var x = { set a(p) {}, a: 1 };",
         StrictModeCheck.DUPLICATE_OBJECT_KEY);

    testSame(
        "'use strict';\n" +
        "function App() {}\n" +
        "App.prototype = {\n" +
        "  get appData() { return this.appData_; },\n" +
        "  set appData(data) { this.appData_ = data; }\n" +
        "};");
  }

  public void testFunctionDecl() {
    testSame("function g() {}");
    testSame("var g = function() {};");
    testSame("(function() {})();");
    testSame("(function() {});");
    testSame(inFn("function g() {}"));
    testSame(inFn("var g = function() {};"));
    testSame(inFn("(function() {})();"));
    testSame(inFn("(function() {});"));

    test("{function g() {}}", null, StrictModeCheck.BAD_FUNCTION_DECLARATION);
    testSame("{var g = function () {}}");
    testSame("{(function g() {})()}");

    test("var x;if (x) { function g(){} }", null,
        StrictModeCheck.BAD_FUNCTION_DECLARATION);
    testSame("var x;if (x) {var g = function () {}}");
    testSame("var x;if (x) {(function g() {})()}");
  }

  public void testFunctionDecl2() {
    test("{function g() {}}", null, StrictModeCheck.BAD_FUNCTION_DECLARATION);
  }

  private String inFn(String body) {
    return "function func() {" + body + "}";
  }
}
