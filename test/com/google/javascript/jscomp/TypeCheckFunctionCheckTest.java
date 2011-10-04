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

import static com.google.javascript.jscomp.TypeCheck.WRONG_ARGUMENT_COUNT;
import static com.google.javascript.jscomp.FunctionTypeBuilder.OPTIONAL_ARG_AT_END;
import static com.google.javascript.jscomp.FunctionTypeBuilder.VAR_ARGS_MUST_BE_LAST;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.rhino.Node;

/**
 * Tests for function and method arity checking in TypeCheck.
 * @author nicksantos@google.com (Nick Santos)
 */
public class TypeCheckFunctionCheckTest extends CompilerTestCase {

  private CodingConvention convention = null;

  public TypeCheckFunctionCheckTest() {
    parseTypeInfo = true;
    enableTypeCheck(CheckLevel.ERROR);
  }

  @Override protected CompilerPass getProcessor(Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {}
    };
  }

  @Override
  protected CodingConvention getCodingConvention() {
    return convention;
  }

  @Override
  protected int getNumRepetitions() {
    // TypeCheck will only run once, regardless of what this returns.
    // We return 1 so that the framework only expects 1 warning.
    return 1;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    convention = new GoogleCodingConvention();
  }

  public void testFunctionAritySimple() {
    assertOk("", "");
    assertOk("a", "'a'");
    assertOk("a,b", "10, 20");
  }

  public void testFunctionArityWithOptionalArgs() {
    assertOk("a,b,opt_c", "1,2");
    assertOk("a,b,opt_c", "1,2,3");
    assertOk("a,opt_b,opt_c", "1");
  }

  public void testFunctionArityWithVarArgs() {
    assertOk("var_args", "");
    assertOk("var_args", "1,2");
    assertOk("a,b,var_args", "1,2");
    assertOk("a,b,var_args", "1,2,3");
    assertOk("a,b,var_args", "1,2,3,4,5");
    assertOk("a,opt_b,var_args", "1");
    assertOk("a,opt_b,var_args", "1,2");
    assertOk("a,opt_b,var_args", "1,2,3");
    assertOk("a,opt_b,var_args", "1,2,3,4,5");
  }

  public void testWrongNumberOfArgs() {
    assertWarning("a,b,opt_c", "1",
        WRONG_ARGUMENT_COUNT);
    assertWarning("a,b,opt_c", "1,2,3,4",
        WRONG_ARGUMENT_COUNT);
    assertWarning("a,b", "1, 2, 3",
        WRONG_ARGUMENT_COUNT);
    assertWarning("", "1, 2, 3",
        WRONG_ARGUMENT_COUNT);
    assertWarning("a,b,c,d", "1, 2, 3",
        WRONG_ARGUMENT_COUNT);
    assertWarning("a,b,var_args", "1",
        WRONG_ARGUMENT_COUNT);
    assertWarning("a,b,opt_c,var_args", "1",
        WRONG_ARGUMENT_COUNT);
  }

  public void testVarArgsLast() {
    assertWarning("a,b,var_args,c", "1,2,3,4",
        VAR_ARGS_MUST_BE_LAST);
  }

  public void testOptArgsLast() {
    assertWarning("a,b,opt_d,c", "1, 2, 3",
        OPTIONAL_ARG_AT_END);
    assertWarning("a,b,opt_d,c", "1, 2",
        OPTIONAL_ARG_AT_END);
  }

  public void testFunctionsWithJsDoc1() {
    testSame("/** @param {*=} c */ function foo(a,b,c) {} foo(1,2);");
  }

  public void testFunctionsWithJsDoc2() {
    testSame("/** @param {*=} c */ function foo(a,b,c) {} foo(1,2,3);");
  }

  public void testFunctionsWithJsDoc3() {
    testSame("/** @param {*=} c \n * @param {*=} b */ " +
             "function foo(a,b,c) {} foo(1);");
  }

  public void testFunctionsWithJsDoc4() {
    testSame("/** @param {...*} a */ var foo = function(a) {}; foo();");
  }

  public void testFunctionsWithJsDoc5() {
    testSame("/** @param {...*} a */ var foo = function(a) {}; foo(1,2);");
  }

  public void testFunctionsWithJsDoc6() {
    testSame("/** @param {...*} b */ var foo = function(a, b) {}; foo();",
             WRONG_ARGUMENT_COUNT);
  }

  public void testFunctionsWithJsDoc7() {
    String fooDfn = "/** @param {*} [b] */ var foo = function(b) {};";
    testSame(fooDfn + "foo();");
    testSame(fooDfn + "foo(1);");
    testSame(fooDfn + "foo(1, 2);", WRONG_ARGUMENT_COUNT);
  }

  public void testFunctionWithDefaultCodingConvention() {
    convention = CodingConventions.getDefault();
    testSame("var foo = function(x) {}; foo(1, 2);");
    testSame("var foo = function(opt_x) {}; foo(1, 2);");
    testSame("var foo = function(var_args) {}; foo(1, 2);");
  }

  public void testMethodCalls() {
    final String METHOD_DEFS =
      "/** @constructor */\n" +
      "function Foo() {}" +
      // Methods defined in a separate functions and then added via assignment
      "function twoArg(arg1, arg2) {};" +
      "Foo.prototype.prototypeMethod = twoArg;" +
      "Foo.staticMethod = twoArg;" +
      // Constructor that specifies a return type
      "/**\n * @constructor\n * @return {Bar}\n */\n" +
      "function Bar() {}";

    // Prototype method with too many arguments.
    testSame(METHOD_DEFS +
        "var f = new Foo();f.prototypeMethod(1, 2, 3);",
        TypeCheck.WRONG_ARGUMENT_COUNT);
    // Prototype method with too few arguments.
    testSame(METHOD_DEFS +
        "var f = new Foo();f.prototypeMethod(1);",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    // Static method with too many arguments.
    testSame(METHOD_DEFS +
        "Foo.staticMethod(1, 2, 3);",
        TypeCheck.WRONG_ARGUMENT_COUNT);
    // Static method with too few arguments.
    testSame(METHOD_DEFS +
        "Foo.staticMethod(1);",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    // Constructor calls require new keyword
    testSame(METHOD_DEFS + "Bar();", TypeCheck.CONSTRUCTOR_NOT_CALLABLE);

    // Extern constructor calls require new keyword
    testSame(METHOD_DEFS, "Foo();", TypeCheck.CONSTRUCTOR_NOT_CALLABLE);

    // Extern constructor call without new keyword
    testSame(METHOD_DEFS, "Bar();", null);
  }

  public void assertOk(String params, String arguments) {
    assertWarning(params, arguments, null);
  }

  public void assertWarning(String params, String arguments,
      DiagnosticType type) {
    testSame("function foo(" + params + ") {} foo(" + arguments + ");",
        type);
  }
}
