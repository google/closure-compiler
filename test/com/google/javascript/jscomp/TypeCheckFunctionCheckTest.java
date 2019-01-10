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

import static com.google.javascript.jscomp.FunctionTypeBuilder.OPTIONAL_ARG_AT_END;
import static com.google.javascript.jscomp.FunctionTypeBuilder.VAR_ARGS_MUST_BE_LAST;
import static com.google.javascript.jscomp.TypeCheck.WRONG_ARGUMENT_COUNT;

import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for function and method arity checking in TypeCheck.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class TypeCheckFunctionCheckTest extends CompilerTestCase {

  private CodingConvention convention = null;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
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
  @Before
  public void setUp() throws Exception {
    super.setUp();
    convention = new GoogleCodingConvention();
    enableParseTypeInfo();
    enableTypeCheck();
  }

  @Test
  public void testFunctionAritySimple() {
    assertOk("", "");
    assertOk("a", "'a'");
    assertOk("a,b", "10, 20");
  }

  @Test
  public void testFunctionArityWithOptionalArgs() {
    assertOk("a,b,opt_c", "1,2");
    assertOk("a,b,opt_c", "1,2,3");
    assertOk("a,opt_b,opt_c", "1");
  }

  @Test
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

  @Test
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

  @Test
  public void testVarArgsLast() {
    assertWarning("a,b,var_args,c", "1,2,3,4",
        VAR_ARGS_MUST_BE_LAST);
  }

  @Test
  public void testOptArgsLast() {
    assertWarning("a,b,opt_d,c", "1, 2, 3",
        OPTIONAL_ARG_AT_END);
    assertWarning("a,b,opt_d,c", "1, 2",
        OPTIONAL_ARG_AT_END);
  }

  @Test
  public void testFunctionsWithJsDoc1() {
    testSame("/** @param {*=} c */ function foo(a,b,c) {} foo(1,2);");
  }

  @Test
  public void testFunctionsWithJsDoc2() {
    testSame("/** @param {*=} c */ function foo(a,b,c) {} foo(1,2,3);");
  }

  @Test
  public void testFunctionsWithJsDoc3() {
    testSame("/** @param {*=} c \n * @param {*=} b */ " +
             "function foo(a,b,c) {} foo(1);");
  }

  @Test
  public void testFunctionsWithJsDoc4() {
    testSame("/** @param {...*} a */ var foo = function(a) {}; foo();");
  }

  @Test
  public void testFunctionsWithJsDoc5() {
    testSame("/** @param {...*} a */ var foo = function(a) {}; foo(1,2);");
  }

  @Test
  public void testFunctionsWithJsDoc6() {
    testWarning(
        "/** @param {...*} b */ var foo = function(a, b) {}; foo();", WRONG_ARGUMENT_COUNT);
  }

  @Test
  public void testFunctionsWithJsDoc7() {
    String fooDfn = "/** @param {*} [b] */ var foo = function(b) {};";
    testSame(fooDfn + "foo();");
    testSame(fooDfn + "foo(1);");
    testWarning(fooDfn + "foo(1, 2);", WRONG_ARGUMENT_COUNT);
  }

  @Test
  public void testFunctionWithDefaultCodingConvention() {
    convention = CodingConventions.getDefault();
    testWarning("var foo = function(x) {}; foo(1, 2);", WRONG_ARGUMENT_COUNT);
    testWarning("var foo = function(opt_x) {}; foo(1, 2);", WRONG_ARGUMENT_COUNT);
    testWarning("var foo = function(var_args) {}; foo(1, 2);", WRONG_ARGUMENT_COUNT);
  }

  @Test
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
    testWarning(
        METHOD_DEFS + "var f = new Foo();f.prototypeMethod(1, 2, 3);",
        TypeCheck.WRONG_ARGUMENT_COUNT);
    // Prototype method with too few arguments.
    testWarning(
        METHOD_DEFS + "var f = new Foo();f.prototypeMethod(1);", TypeCheck.WRONG_ARGUMENT_COUNT);

    // Static method with too many arguments.
    testWarning(METHOD_DEFS + "Foo.staticMethod(1, 2, 3);", TypeCheck.WRONG_ARGUMENT_COUNT);
    // Static method with too few arguments.
    testWarning(METHOD_DEFS + "Foo.staticMethod(1);", TypeCheck.WRONG_ARGUMENT_COUNT);

    // Constructor calls require "new" keyword
    testWarning(METHOD_DEFS + "Foo();", TypeCheck.CONSTRUCTOR_NOT_CALLABLE);

    // Constructors with explicit return type can be called without
    // the "new" keyword
    testSame(METHOD_DEFS + "Bar();");

    // Extern constructor calls require "new" keyword
    test(externs(METHOD_DEFS), srcs("Foo();"), warning(TypeCheck.CONSTRUCTOR_NOT_CALLABLE));

    // Extern constructor with explicit return type can be called without
    // the "new" keyword
    testSame(externs(METHOD_DEFS), srcs("Bar();"));
  }

  public void assertOk(String params, String arguments) {
    testSame("function foo(" + params + ") {} foo(" + arguments + ");");
  }

  public void assertWarning(String params, String arguments, DiagnosticType type) {
    testWarning("function foo(" + params + ") {} foo(" + arguments + ");", type);
  }
}
