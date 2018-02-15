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

public final class StrictModeCheckTest extends TypeICompilerTestCase {
  private static final String EXTERNS = DEFAULT_EXTERNS + "var arguments; function eval(str) {}";

  public StrictModeCheckTest() {
    super(EXTERNS);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    this.mode = TypeInferenceMode.NTI_ONLY;
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
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testSame(js);
  }

  public void testUseOfWith1() {
    testWarning("var a; with(a){}", StrictModeCheck.USE_OF_WITH);
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
    testWarning("function foo(eval) {}", StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval3() {
    testSame("function foo() {} foo.eval = 3;");
  }

  public void testEval4() {
    testWarning("function foo() { var eval = 3; }", StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval5() {
    testWarning(
        "/** @suppress {duplicate} */ function eval() {}", StrictModeCheck.EVAL_DECLARATION);
  }

  public void testEval6() {
    testWarning("try {} catch (eval) {}", StrictModeCheck.EVAL_DECLARATION);
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
    this.mode = TypeInferenceMode.NEITHER;
    testSameEs6Strict("function foo(a) { let b; a = b; }");
    testSameEs6Strict("function foo(a) { const b = 42; a = b; }");
  }

  public void testArguments() {
    testWarning("function foo(arguments) {}", StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments2() {
    testWarning("function foo() { var arguments = 3; }", StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments3() {
    testWarning(
        "/** @suppress {duplicate,checkTypes} */ function arguments() {}",
        StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments4() {
    testWarning("try {} catch (arguments) {}", StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  public void testArguments5() {
    testSame("var o = {arguments: 3};");
  }

  public void testArguments6() {
    this.mode = TypeInferenceMode.NEITHER;
    testSame("(() => arguments)();");
  }

  public void testArgumentsCallee() {
    testWarning("function foo() {arguments.callee}", StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN);
  }

  public void testArgumentsCaller() {
    testWarning("function foo() {arguments.caller}", StrictModeCheck.ARGUMENTS_CALLER_FORBIDDEN);
  }

  public void testFunctionCallerProp() {
    testWarning("function foo() {foo.caller}", StrictModeCheck.FUNCTION_CALLER_FORBIDDEN);
  }

  public void testFunctionArgumentsProp() {
    testWarning(
        "function foo() {foo.arguments}", StrictModeCheck.FUNCTION_ARGUMENTS_PROP_FORBIDDEN);
  }


  public void testEvalAssignment() {
    testWarning(
        "/** @suppress {checkTypes} */ function foo() { eval = []; }",
        StrictModeCheck.EVAL_ASSIGNMENT);
  }

  public void testAssignToArguments() {
    testWarning("function foo() { arguments = []; }", StrictModeCheck.ARGUMENTS_ASSIGNMENT);
  }

  public void testDeleteVar() {
    testWarning("var a; delete a", StrictModeCheck.DELETE_VARIABLE);
  }

  public void testDeleteFunction() {
    testWarning("function a() {} delete a", StrictModeCheck.DELETE_VARIABLE);
  }

  public void testDeleteArgument() {
    testWarning("function b(a) { delete a; }", StrictModeCheck.DELETE_VARIABLE);
  }

  public void testValidDelete() {
    testSame("var obj = { a: 0 }; delete obj.a;");
    testSame("var obj = { a: function() {} }; delete obj.a;");
    this.mode = TypeInferenceMode.NEITHER;
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

    testError("var o = {a: 1, b: 2, a: 3};",
        StrictModeCheck.DUPLICATE_OBJECT_KEY);
    testError("var x = { get a() {}, get a() {} };",
         StrictModeCheck.DUPLICATE_OBJECT_KEY);
    testError("var x = { get a() {}, a: 1 };",
         StrictModeCheck.DUPLICATE_OBJECT_KEY);
    testError("var x = { set a(p) {}, a: 1 };",
         StrictModeCheck.DUPLICATE_OBJECT_KEY);

    testSame(
        lines(
            "'use strict';",
            "/** @constructor */ function App() {}",
            "App.prototype = {",
            "  get appData() { return this.appData_; },",
            "  set appData(data) { this.appData_ = data; }",
            "};"));

    this.mode = TypeInferenceMode.NEITHER;
    testError("var x = {a: 2, a(){}}", StrictModeCheck.DUPLICATE_OBJECT_KEY);
    testError("var x = {a, a(){}}", StrictModeCheck.DUPLICATE_OBJECT_KEY);
    testError("var x = {a(){}, a(){}}", StrictModeCheck.DUPLICATE_OBJECT_KEY);
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

    testSame("{var g = function () {}}");
    testSame("{(function g() {})()}");

    testSame("var x;if (x) {var g = function () {}}");
    testSame("var x;if (x) {(function g() {})()}");
  }

  public void testClass() {
    this.mode = TypeInferenceMode.NEITHER;
    testSame(
        lines(
            "class A {",
            "  method1() {}",
            "  method2() {}",
            "}"));

    // Duplicate class methods test
    testError(
        lines(
            "class A {",
            "  method1() {}",
            "  method1() {}",
            "}"),
        StrictModeCheck.DUPLICATE_CLASS_METHODS);

    // Function declaration / call test.
    // The two following tests should have reported FUNCTION_CALLER_FORBIDDEN and
    // FUNCTION_ARGUMENTS_PROP_FORBIDDEN. Typecheck needed for them to work.
    // TODO(user): Add tests for these after typecheck supports class.
    testSame(
        lines(
            "class A {",
            "  method() {this.method.caller}",
            "}"));
    testSame(
        lines(
            "class A {",
            "  method() {this.method.arguments}",
            "}"));

    // Duplicate obj literal key in classes
    testError(lines(
        "class A {",
        "  method() {",
        "    var obj = {a : 1, a : 2}",
        "  }",
        "}"), StrictModeCheck.DUPLICATE_OBJECT_KEY);

    // Delete test. Class methods are configurable, thus deletable.
    testSame(lines(
        "class A {",
        "  methodA() {}",
        "  methodB() {delete this.methodA}",
        "}"));

    // Use of with test
    testError(
        lines(
            "class A {",
            "  constructor() {this.x = 1;}",
            "  method() {",
            "    with (this.x) {}",
            "  }",
            "}"),
        StrictModeCheck.USE_OF_WITH);

    // Eval errors test
    testError(lines(
        "class A {",
        "  method(eval) {}",
        "}"), StrictModeCheck.EVAL_DECLARATION);
    testError(lines(
        "class A {",
        "  method() {var eval = 1;}",
        "}"), StrictModeCheck.EVAL_DECLARATION);
    testError(lines(
        "class A {",
        "  method() {eval = 1}",
        "}"), StrictModeCheck.EVAL_ASSIGNMENT);

    // Use of 'arguments'
    testError(lines(
        "class A {",
        "  method(arguments) {}",
        "}"), StrictModeCheck.ARGUMENTS_DECLARATION);
    testError(lines(
        "class A {",
        "  method() {var arguments = 1;}",
        "}"), StrictModeCheck.ARGUMENTS_DECLARATION);
    testError(lines(
        "class A {",
        "  method() {arguments = 1}",
        "}"), StrictModeCheck.ARGUMENTS_ASSIGNMENT);
    testError(lines(
        "class A {",
        "  method() {arguments.callee}",
        "}"), StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN);
    testError(lines(
        "class A {",
        "  method() {arguments.caller}",
        "}"), StrictModeCheck.ARGUMENTS_CALLER_FORBIDDEN);
  }

  public void testComputedPropInClass() {
    this.mode = TypeInferenceMode.NEITHER;
    testSame(
        lines(
            "class Example {",
            "  [computed()]() {}",
            "  [computed()]() {}",
            "}"));
  }

  public void testStaticAndNonstaticMethodWithSameName() {
    this.mode = TypeInferenceMode.NEITHER;
    testSame(
        lines(
            "class Example {",
            "  foo() {}",
            "  static foo() {}",
            "}"));
  }

  public void testStaticAndNonstaticGetterWithSameName() {
    this.mode = TypeInferenceMode.NEITHER;
    testSame(
        lines(
            "class Example {",
            "  get foo() {}",
            "  static get foo() {}",
            "}"));
  }

  public void testStaticAndNonstaticSetterWithSameName() {
    this.mode = TypeInferenceMode.NEITHER;
    testSame(
        lines(
            "class Example {",
            "  set foo(x) {}",
            "  static set foo(x) {}",
            "}"));
  }

  public void testClassWithEmptyMembers() {
    this.mode = TypeInferenceMode.NEITHER;
    testError("class Foo { dup() {}; dup() {}; }", StrictModeCheck.DUPLICATE_CLASS_METHODS);
  }

  /**
   * If the LanguageMode is ES2015 or higher, strict mode violations are automatically upgraded to
   * errors, so set it to ES5 to get a warning.
   */
  @Override
  public void testWarning(String js, DiagnosticType warning) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  private static String inFn(String body) {
    return "function func() {" + body + "}";
  }
}
