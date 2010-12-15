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

  public void testWith() {
    test("var a; function foo(obj) { with (obj) { a = 3; }}", null,
         StrictModeCheck.WITH_DISALLOWED);
  }

  public void testEval() {
    test("function foo() { eval('a'); }", null,
         StrictModeCheck.EVAL_USE);
  }

  public void testEval2() {
    test("function foo(eval) {}", null,
         StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval3() {
    testSame("function foo() {} foo.eval = 3;");
  }

  public void testEval4() {
    test("function foo() { var eval = 3; }", null,
         StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval5() {
    test("function eval() {}", null, StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval6() {
    test("try {} catch (eval) {}", null, StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval7() {
    testSame("var o = {eval: 3};");
  }

  public void testEval8() {
    testSame("var a; eval: while (true) { a = 3; }");
  }

  public void testUnknownVariable() {
    test("function foo(a) { a = b; }", null, StrictModeCheck.UNKNOWN_VARIABLE);
  }

  public void testUnknownVariable2() {
    test("a: while (true) { a = 3; }", null, StrictModeCheck.UNKNOWN_VARIABLE);
  }

  public void testUnknownVariable3() {
    testSame("try {} catch (ex) { ex = 3; }");
  }

  public void testArguments() {
    test("function foo(arguments) {}", null,
         StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments2() {
    test("function foo() { var arguments = 3; }", null,
         StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments3() {
    test("function arguments() {}", null,
         StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments4() {
    test("try {} catch (arguments) {}", null,
         StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments5() {
    testSame("var o = {arguments: 3};");
  }

  public void testEvalAssignment() {
    noCajaChecks = true;
    test("function foo() { eval = []; }", null,
         StrictModeCheck.EVAL_ASSIGNMENT);
  }

  public void testEvalAssignment2() {
    test("function foo() { eval = []; }", null, StrictModeCheck.EVAL_USE);
  }

  public void testAssignToArguments() {
    test("function foo() { arguments = []; }", null,
         StrictModeCheck.ARGUMENTS_ASSIGNMENT);
  }

  public void testDeleteVar() {
    test("var a; delete a", null, StrictModeCheck.DELETE_VARIABLE);
  }

  public void testDeleteFunction() {
    test("function a() {} delete a", null, StrictModeCheck.DELETE_VARIABLE);
  }

  public void testDeleteArgument() {
    test("function b(a) { delete a; }", null, StrictModeCheck.DELETE_VARIABLE);
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
}
