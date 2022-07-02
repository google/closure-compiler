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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StrictModeCheckTest extends CompilerTestCase {
  private static final String EXTERNS = DEFAULT_EXTERNS + "var arguments; function eval(str) {}";

  public StrictModeCheckTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new StrictModeCheck(compiler, CheckLevel.WARNING);
  }

  private void testSameEs6Strict(String js) {
    testSame(js);
  }

  @Test
  public void testUseOfWith1() {
    testWarning("var a; with(a){}", StrictModeCheck.USE_OF_WITH);
  }

  @Test
  public void testUseOfWith2() {
    testSame("var a;\n" + "/** @suppress {with} */" + "with(a){}");
  }

  @Test
  public void testUseOfWith3() {
    testSame(
        "function f(expr, context) {\n"
            + "  try {\n"
            + "    /** @suppress{with} */ with (context) {\n"
            + "      return eval('[' + expr + '][0]');\n"
            + "    }\n"
            + "  } catch (e) {\n"
            + "    return null;\n"
            + "  }\n"
            + "};\n");
  }

  @Test
  public void testEval2() {
    testWarning("function foo(eval) {}", StrictModeCheck.EVAL_DECLARATION);
  }

  @Test
  public void testEval3() {
    testSame("function foo() {} foo.eval = 3;");
  }

  @Test
  public void testEval4() {
    testWarning("function foo() { var eval = 3; }", StrictModeCheck.EVAL_DECLARATION);
  }

  @Test
  public void testEval5() {
    testWarning(
        "/** @suppress {duplicate} */ function eval() {}", StrictModeCheck.EVAL_DECLARATION);
  }

  @Test
  public void testEval6() {
    testWarning("try {} catch (eval) {}", StrictModeCheck.EVAL_DECLARATION);
  }

  @Test
  public void testEval7() {
    testSame("var o = {eval: 3};");
  }

  @Test
  public void testEval8() {
    testSame("var a; eval: while (true) { a = 3; }");
  }

  @Test
  public void testUnknownVariable3() {
    testSame("try {} catch (ex) { ex = 3; }");
  }

  @Test
  public void testUnknownVariable4() {
    disableTypeCheck();
    testSameEs6Strict("function foo(a) { let b; a = b; }");
    testSameEs6Strict("function foo(a) { const b = 42; a = b; }");
  }

  @Test
  public void testArguments() {
    testWarning("function foo(arguments) {}", StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  @Test
  public void testArguments2() {
    testWarning("function foo() { var arguments = 3; }", StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  @Test
  public void testArguments3() {
    testWarning(
        "/** @suppress {duplicate,checkTypes} */ function arguments() {}",
        StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  @Test
  public void testArguments4() {
    testWarning("try {} catch (arguments) {}", StrictModeCheck.ARGUMENTS_DECLARATION);
  }

  @Test
  public void testArguments5() {
    testSame("var o = {arguments: 3};");
  }

  @Test
  public void testArguments6() {
    disableTypeCheck();
    testSame("(() => arguments)();");
  }

  @Test
  public void testArgumentsCallee() {
    testWarning("function foo() {arguments.callee}", StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN);
  }

  @Test
  public void testArgumentsCaller() {
    testWarning("function foo() {arguments.caller}", StrictModeCheck.ARGUMENTS_CALLER_FORBIDDEN);
  }

  @Test
  public void testFunctionCallerProp() {
    enableTypeCheck();
    testWarning("function foo() {foo.caller}", StrictModeCheck.FUNCTION_CALLER_FORBIDDEN);
  }

  @Test
  public void testFunctionArgumentsProp() {
    enableTypeCheck();
    testWarning(
        "function foo() {foo.arguments}", StrictModeCheck.FUNCTION_ARGUMENTS_PROP_FORBIDDEN);
  }

  @Test
  public void testEvalAssignment() {
    testWarning(
        "/** @suppress {checkTypes} */ function foo() { eval = []; }",
        StrictModeCheck.EVAL_ASSIGNMENT);
  }

  @Test
  public void testAssignToArguments() {
    testWarning("function foo() { arguments = []; }", StrictModeCheck.ARGUMENTS_ASSIGNMENT);
  }

  @Test
  public void testDeleteVar() {
    testWarning("var a; delete a", StrictModeCheck.DELETE_VARIABLE);
  }

  @Test
  public void testDeleteFunction() {
    testWarning("function a() {} delete a", StrictModeCheck.DELETE_VARIABLE);
  }

  @Test
  public void testDeleteArgument() {
    testWarning("function b(a) { delete a; }", StrictModeCheck.DELETE_VARIABLE);
  }

  @Test
  public void testValidDelete() {
    testSame("var obj = { a: 0 }; delete obj.a;");
    testSame("var obj = { a: function() {} }; delete obj.a;");
    testSameEs6Strict("var obj = { a(){} }; delete obj.a;");
    testSameEs6Strict("var obj = { a }; delete obj.a;");
  }

  @Test
  public void testDeleteProperty() {
    testSame("/** @suppress {checkTypes} */ function f(obj) { delete obj.a; }");
  }

  @Test
  public void testAllowNumbersAsObjlitKeys() {
    testSame("var o = {1: 3, 2: 4};");
  }

  @Test
  public void testDuplicateObjectLiteralKey() {
    testSame("var o = {a: 1, b: 2, c: 3};");
    testSame("var x = { get a() {}, set a(p) {} };");

    testWarning("var o = {a: 1, b: 2, a: 3};", StrictModeCheck.DUPLICATE_MEMBER);
    testWarning("var x = { get a() {}, get a() {} };", StrictModeCheck.DUPLICATE_MEMBER);
    testWarning("var x = { get a() {}, a: 1 };", StrictModeCheck.DUPLICATE_MEMBER);
    testWarning("var x = { set a(p) {}, a: 1 };", StrictModeCheck.DUPLICATE_MEMBER);

    testSame(
        lines(
            "'use strict';",
            "/** @constructor */ function App() {}",
            "App.prototype = {",
            "  get appData() { return this.appData_; },",
            "  set appData(data) { this.appData_ = data; }",
            "};"));

    testWarning("var x = {a: 2, a(){}}", StrictModeCheck.DUPLICATE_MEMBER);
    testWarning("var x = {a, a(){}}", StrictModeCheck.DUPLICATE_MEMBER);
    testWarning("var x = {a(){}, a(){}}", StrictModeCheck.DUPLICATE_MEMBER);
  }

  @Test
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

  @Test
  public void testClass() {
    testSame(lines("class A {", "  method1() {}", "  method2() {}", "}"));

    // Duplicate class methods test
    testWarning(
        lines("class A {", "  method1() {}", "  method1() {}", "}"),
        StrictModeCheck.DUPLICATE_MEMBER);

    // Function declaration / call test.
    // The two following tests should have reported FUNCTION_CALLER_FORBIDDEN and
    // FUNCTION_ARGUMENTS_PROP_FORBIDDEN. Typecheck needed for them to work.
    // TODO(user): Add tests for these after typecheck supports class.
    testSame(lines("class A {", "  method() {this.method.caller}", "}"));
    testSame(lines("class A {", "  method() {this.method.arguments}", "}"));

    // Duplicate obj literal key in classes
    testWarning(
        lines("class A {", "  method() {", "    var obj = {a : 1, a : 2}", "  }", "}"),
        StrictModeCheck.DUPLICATE_MEMBER);

    // Delete test. Class methods are configurable, thus deletable.
    testSame(lines("class A {", "  methodA() {}", "  methodB() {delete this.methodA}", "}"));

    // Use of with test
    testWarning(
        lines(
            "class A {",
            "  constructor() {this.x = 1;}",
            "  method() {",
            "    with (this.x) {}",
            "  }",
            "}"),
        StrictModeCheck.USE_OF_WITH);

    // Eval errors test
    testWarning(lines("class A {", "  method(eval) {}", "}"), StrictModeCheck.EVAL_DECLARATION);
    testWarning(
        lines("class A {", "  method() {var eval = 1;}", "}"), StrictModeCheck.EVAL_DECLARATION);
    testWarning(lines("class A {", "  method() {eval = 1}", "}"), StrictModeCheck.EVAL_ASSIGNMENT);

    // Use of 'arguments'
    testWarning(
        lines("class A {", "  method(arguments) {}", "}"), StrictModeCheck.ARGUMENTS_DECLARATION);
    testWarning(
        lines("class A {", "  method() {var arguments = 1;}", "}"),
        StrictModeCheck.ARGUMENTS_DECLARATION);
    testWarning(
        lines("class A {", "  method() {arguments = 1}", "}"),
        StrictModeCheck.ARGUMENTS_ASSIGNMENT);
    testWarning(
        lines("class A {", "  method() {arguments.callee}", "}"),
        StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN);
    testWarning(
        lines("class A {", "  method() {arguments.caller}", "}"),
        StrictModeCheck.ARGUMENTS_CALLER_FORBIDDEN);
  }

  @Test
  public void testComputedPropInClass() {
    testSame(lines("class Example {", "  [computed()]() {}", "  [computed()]() {}", "}"));
  }

  @Test
  public void testStaticAndNonstaticMethodWithSameName() {
    testSame(lines("class Example {", "  foo() {}", "  static foo() {}", "}"));
  }

  @Test
  public void testStaticAndNonstaticGetterWithSameName() {
    testSame(lines("class Example {", "  get foo() {}", "  static get foo() {}", "}"));
  }

  @Test
  public void testStaticAndNonstaticSetterWithSameName() {
    testSame(lines("class Example {", "  set foo(x) {}", "  static set foo(x) {}", "}"));
  }

  @Test
  public void testClassWithEmptyMembers() {
    testWarning("class Foo { dup() {}; dup() {}; }", StrictModeCheck.DUPLICATE_MEMBER);
  }

  @Test
  public void testClassDuplicateFieldError() {
    testWarning(
        lines(
            "class Example {", //
            "  a;",
            "  a;",
            "}"),
        StrictModeCheck.DUPLICATE_MEMBER);
    testWarning(
        lines(
            "class Example {", //
            "  static a = 2;",
            "  static a = 3;",
            "}"),
        StrictModeCheck.DUPLICATE_MEMBER);
  }

  @Test
  public void testClassDuplicateStaticFieldError() {
    testWarning(
        lines(
            "class Example {", //
            "  static a = 2;",
            "  static a = 3;",
            "}"),
        StrictModeCheck.DUPLICATE_MEMBER);
    testWarning(
        lines(
            "class Example {", //
            "  static a;",
            "  static a;",
            "}"),
        StrictModeCheck.DUPLICATE_MEMBER);
  }

  @Test
  public void testClassFields_noError() {
    testSame(
        lines(
            "class Example {", //
            "  a;",
            "  static a;",
            "}"));
  }

  @Test
  public void testClassStaticBlocksDuplicates() {
    // testing that duplicates are ok in class static blocks
    testSame("class Foo {static {this.a; this.a;}}");

    // even if they are in 2 different static blocks
    testSame("class Foo {static {this.a} static {this.a}}");

    // testing that duplicates between class static blocks and public fields are ok
    testSame("class Foo {static x; static {this.x}}");

    // duplicate functions are also okay
    testSame("class Foo {static f() {}; static{this.f = function g() {}}}");

    // testing that duplicates in obj literals are still invalid
    testWarning("class Foo {static {var obj = {a : 1, a : 2};}}", StrictModeCheck.DUPLICATE_MEMBER);
  }

  @Test
  public void testClassStaticBlocksWith() {
    testWarning(
        lines("class A {", "  static x;", "  static {", "    with (this.x) {}", "  }", "}"),
        StrictModeCheck.USE_OF_WITH);
  }

  @Test
  public void testClassStaticBlocksEval() {
    testWarning("class A { static {var eval;}}", StrictModeCheck.EVAL_DECLARATION);
    testWarning("class A { static {function eval() {};}}", StrictModeCheck.EVAL_DECLARATION);
    testWarning(
        "class A { static {this.e = function eval() {};}}", StrictModeCheck.EVAL_DECLARATION);
    testWarning("class A { static {eval = 3;}}", StrictModeCheck.EVAL_ASSIGNMENT);

    // eval as a field is okay
    testSame("class A { static {this.eval;}}");
  }

  @Test
  public void testClassStaticBlocksArguments() {
    testWarning(
        lines("class A {", "  static {var arguments = 1;}", "}"),
        StrictModeCheck.ARGUMENTS_DECLARATION);
    testWarning(
        lines("class A {", "  static {arguments = 1}", "}"), StrictModeCheck.ARGUMENTS_ASSIGNMENT);
    testWarning(
        lines("class A {", "  static {arguments.callee}", "}"),
        StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN);
    testWarning(
        lines("class A {", "  static {arguments.caller}", "}"),
        StrictModeCheck.ARGUMENTS_CALLER_FORBIDDEN);
  }

  @Test
  public void testClassStaticBlockDelete() {
    testSame("class A {static{this.a; delete this.a;}}");
    testSame("class A {static a; static{delete this.a;}}");
    testWarning("class A {static a; static{var a; delete a;}}", StrictModeCheck.DELETE_VARIABLE);
    testSame("class A {static f() {}; static{delete this.f;}}");
  }

  private static String inFn(String body) {
    return "function func() {" + body + "}";
  }
}
