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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public final class StrictModeCheckTest extends Es6CompilerTestCase {
  private static final String EXTERNS = "var arguments; function eval(str) {}";

  public StrictModeCheckTest() {
    super(EXTERNS);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new StrictModeCheck(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  private void testSameEs6Strict(String js) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6_STRICT);
    test(js, js, null, null);
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

  public void testUnknownVariable3() {
    testSame("try {} catch (ex) { ex = 3; }");
  }

  public void testUnknownVariable4() {
    disableTypeCheck();
    testSameEs6Strict("function foo(a) { let b; a = b; }");
    testSameEs6Strict("function foo(a) { const b = 42; a = b; }");
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

  public void testValidDelete() {
    testSame("var obj = { a: 0 }; delete obj.a;");
    testSame("var obj = { a: function() {} }; delete obj.a;");
    disableTypeCheck();
    testSameEs6Strict("var obj = { a(){} }; delete obj.a;");
    testSameEs6Strict("var obj = { a }; delete obj.a;");
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

    disableTypeCheck();
    testWarningEs6("var x = {a: 2, a(){}}", StrictModeCheck.DUPLICATE_OBJECT_KEY);
    testWarningEs6("var x = {a, a(){}}", StrictModeCheck.DUPLICATE_OBJECT_KEY);
    testWarningEs6("var x = {a(){}, a(){}}", StrictModeCheck.DUPLICATE_OBJECT_KEY);
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

  public void testClass() {
    disableTypeCheck();
    testSameEs6(LINE_JOINER.join(
        "class A {",
        "  method1() {}",
        "  method2() {}",
        "}"));

    // Duplicate class methods test
    testErrorEs6(LINE_JOINER.join(
        "class A {",
        "  method1() {}",
        "  method1() {}",
        "}"), StrictModeCheck.DUPLICATE_CLASS_METHODS);

    // Function declaration / call test.
    testErrorEs6(LINE_JOINER.join(
        "class A {",
        "  method() {",
        "    for(;;) {",
        "      function a(){}",
        "    }",
        "  }",
        "}"), StrictModeCheck.BAD_FUNCTION_DECLARATION);
    // The two following tests should have reported FUNCTION_CALLER_FORBIDDEN and
    // FUNCTION_ARGUMENTS_PROP_FORBIDDEN. Typecheck needed for them to work.
    // TODO(user): Add tests for these after typecheck supports class.
    testSameEs6(LINE_JOINER.join(
        "class A {",
        "  method() {this.method.caller}",
        "}"));
    testSameEs6(LINE_JOINER.join(
        "class A {",
        "  method() {this.method.arguments}",
        "}"));

    // Duplicate obj literal key in classes
    testWarningEs6(LINE_JOINER.join(
        "class A {",
        "  method() {",
        "    var obj = {a : 1, a : 2}",
        "  }",
        "}"), StrictModeCheck.DUPLICATE_OBJECT_KEY);

    // Delete test. Class methods are configurable, thus deletable.
    testSameEs6(LINE_JOINER.join(
        "class A {",
        "  methodA() {}",
        "  methodB() {delete this.methodA}",
        "}"));

    // Use of with test
    testWarningEs6(LINE_JOINER.join(
        "class A {",
        "  constructor() {this.x = 1;}",
        "  method() {",
        "    with (this.x) {}",
        "  }",
        "}"), StrictModeCheck.USE_OF_WITH);

    // Eval errors test
    testWarningEs6(LINE_JOINER.join(
        "class A {",
        "  method(eval) {}",
        "}"), StrictModeCheck.EVAL_DECLARATION);
    testWarningEs6(LINE_JOINER.join(
        "class A {",
        "  method() {var eval = 1;}",
        "}"), StrictModeCheck.EVAL_DECLARATION);
    testWarningEs6(LINE_JOINER.join(
        "class A {",
        "  method() {eval = 1}",
        "}"), StrictModeCheck.EVAL_ASSIGNMENT);

    // Use of 'arguments'
    testWarningEs6(LINE_JOINER.join(
        "class A {",
        "  method(arguments) {}",
        "}"), StrictModeCheck.ARGUMENTS_DECLARATION);
    testWarningEs6(LINE_JOINER.join(
        "class A {",
        "  method() {var arguments = 1;}",
        "}"), StrictModeCheck.ARGUMENTS_DECLARATION);
    testWarningEs6(LINE_JOINER.join(
        "class A {",
        "  method() {arguments = 1}",
        "}"), StrictModeCheck.ARGUMENTS_ASSIGNMENT);
    testWarningEs6(LINE_JOINER.join(
        "class A {",
        "  method() {arguments.callee}",
        "}"), StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN);
    testWarningEs6(LINE_JOINER.join(
        "class A {",
        "  method() {arguments.caller}",
        "}"), StrictModeCheck.ARGUMENTS_CALLER_FORBIDDEN);
  }

  private static String inFn(String body) {
    return "function func() {" + body + "}";
  }
}
