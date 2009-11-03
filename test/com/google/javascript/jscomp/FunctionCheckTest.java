/*
 * Copyright 2004 Google Inc.
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

public class FunctionCheckTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new FunctionCheck(compiler, CheckLevel.ERROR);
  }

  @Override
  protected int getNumRepetitions() {
    // Cannot repeat tests multiple times because var_args parameter is removed.
    return 1;
  }

  public void testFunctions() {
    // ok
    testSame("function foo() {} foo();");
    testSame("function foo(a) {} foo('a');");
    testSame("function foo(a,b) {} foo(10, 20);");

    // optional args
    testSame("function foo(a,b,opt_c) {} foo(1,2);");
    testSame("function foo(a,b,opt_c) {} foo(1,2,3);");
    testSame("function foo(a,opt_b,opt_c) {} foo(1);");

    // var_args
    testSame("function foo(var_args) {} foo();");
    testSame("function foo(var_args) {} foo(1,2);");
    testSame("function foo(a,b,var_args) {} foo(1,2);");
    testSame("function foo(a,b,var_args) {} foo(1,2,3);");
    testSame("function foo(a,b,var_args) {} foo(1,2,3,4,5);");
    testSame("function foo(a,opt_b,var_args) {} foo(1);");
    testSame("function foo(a,opt_b,var_args) {} foo(1,2);");
    testSame("function foo(a,opt_b,var_args) {} foo(1,2,3);");
    testSame("function foo(a,opt_b,var_args) {} foo(1,2,3,4,5);");
    // this test ensures that we can have local functions with var_args without
    // triggering the VAR_ARGS_USED_ERROR
    testSame("function f(var_args) { function g(var_args) {} g(1,2,3); } f();");

    // error
    test("function foo(a,b,opt_c) {} foo(1);", null,
         FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
    test("function foo(a,b,opt_c) {} foo(1,2,3,4);", null,
         FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
    test("function foo(a,b) {} foo(1, 2, 3);", null,
         FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
    test("function foo() {} foo(1, 2, 3);", null,
         FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
    test("function foo(a,b,c,d) {} foo(1, 2, 3);", null,
         FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
    test("function foo(a,b,var_args) {} foo(1);", null,
         FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
    test("function foo(a,b,opt_c,var_args) {} foo(1);", null,
         FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
    test("function foo(a,b,var_args,c) {} foo(1,2,3,4);", null,
         FunctionCheck.VAR_ARGS_ERROR);
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
    test("/** @param {...*} b */ var foo = function(a, b) {}; foo();",
         null, FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
  }
}
