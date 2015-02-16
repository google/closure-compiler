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

  public StrictModeCheckTest() {
    super(EXTERNS);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableTypeCheck(CheckLevel.OFF);
    noVarCheck = false;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new StrictModeCheck(compiler, noVarCheck);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testUseOfWith1() {
    testSame("var a; with(a){}", StrictModeCheck.USE_OF_WITH);
  }

  public void testUseOfWith2() {
    testSame("var a;\n" +
             "/** @suppress {with} */" +
             "with(a){}");
  }

  public void testUseOfWith3() {
    testSame(
        "function f(expr, context) {\n" +
        "  try {\n" +
        "    /** @suppress{with} */ with (context) {\n" +
        "      return eval('[' + expr + '][0]');\n" +
        "    }\n" +
        "  } catch (e) {\n" +
        "    return null;\n" +
        "  }\n" +
        "};\n");
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
    testSame("/** @suppress {duplicate} */ function eval() {}", StrictModeCheck.EVAL_DECLARATION);
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
    testSame("/** @suppress {duplicate,checkTypes} */ function arguments() {}",
         StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments4() {
    testSame("try {} catch (arguments) {}",
         StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments5() {
    testSame("var o = {arguments: 3};");
  }

  public void testArgumentsCallee() {
    testSame("function foo() {arguments.callee}",
         StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN);
  }

  public void testArgumentsCaller() {
    testSame("function foo() {arguments.caller}",
         StrictModeCheck.ARGUMENTS_CALLER_FORBIDDEN);
  }

  public void testFunctionCallerProp() {
    testSame("function foo() {foo.caller}",
         StrictModeCheck.FUNCTION_CALLER_FORBIDDEN);
  }

  public void testFunctionArgumentsProp() {
    testSame("function foo() {foo.arguments}",
         StrictModeCheck.FUNCTION_ARGUMENTS_PROP_FORBIDDEN);
  }


  public void testEvalAssignment() {
    testSame("/** @suppress {checkTypes} */ function foo() { eval = []; }",
         StrictModeCheck.EVAL_ASSIGNMENT);
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
    testSame("/** @suppress {checkTypes} */ function f(obj) { delete obj.a; }");
  }

  public void testAllowNumbersAsObjlitKeys() {
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

    testError("{function g() {}}", StrictModeCheck.BAD_FUNCTION_DECLARATION);
    testSame("{var g = function () {}}");
    testSame("{(function g() {})()}");

    testError("var x;if (x) { function g(){} }", StrictModeCheck.BAD_FUNCTION_DECLARATION);
    testSame("var x;if (x) {var g = function () {}}");
    testSame("var x;if (x) {(function g() {})()}");
  }

  public void testFunctionDecl2() {
    testError("{function g() {}}", StrictModeCheck.BAD_FUNCTION_DECLARATION);
  }

  private String inFn(String body) {
    return "function func() {" + body + "}";
  }
}
