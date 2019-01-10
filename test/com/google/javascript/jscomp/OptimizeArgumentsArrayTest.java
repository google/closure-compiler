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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link OptimizeArgumentsArray}.
 *
 */
@RunWith(JUnit4.class)
public final class OptimizeArgumentsArrayTest extends CompilerTestCase {

  public OptimizeArgumentsArrayTest() {
    /*
     * arguments is a builtin variable of the javascript language and
     * OptimizeArgumentsArray does not make any attempt to resolve it. However,
     * I am leaving "var arguments" in the externs to emulate the current
     * behavior we have for JS compilation where var arguments in defined in
     * externs/es3.js as extern.
     */
    super("var arguments, alert" /* Externs */);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new OptimizeArgumentsArray(compiler, "p");
  }

  @Test
  public void testSimple() {
    test(
      "function foo()   { alert(arguments[0]); }",
      "function foo(p0) { alert(          p0); }");
  }

  @Test
  public void testNoVarArgs() {
    testSame("function f(a,b,c) { alert(a + b + c) }");

    test(
        "function f(a,b,c) { alert(arguments[0]) }",
        "function f(a,b,c) { alert(           a) }");
  }

  @Test
  public void testMissingVarArgs() {
    testSame("function f() { alert(arguments[x]) }");
  }

  @Test
  public void testArgumentRefOnNamedParameter() {
    test("function f(a,b) { alert(arguments[0]) }",
         "function f(a,b) { alert(a) }");
  }

  @Test
  public void testTwoVarArgs() {
    test(
        "function foo(a)         { alert(arguments[1] + arguments[2]); }",
        "function foo(a, p0, p1) { alert(          p0 +           p1); }");
  }

  @Test
  public void testTwoFourArgsTwoUsed() {
    test("function foo() { alert(arguments[0] + arguments[3]); }",
         "function foo(p0, p1, p2, p3) { alert(p0 + p3); }");
  }

  @Test
  public void testOneRequired() {
    test("function foo(req0, var_args) { alert(req0 + arguments[1]); }",
         "function foo(req0, var_args) { alert(req0 + var_args); }");
  }

  @Test
  public void testTwoRequiredSixthVarArgReferenced() {
    test("function foo(r0, r1, var_args) {alert(r0 + r1 + arguments[5]);}",
         "function foo(r0, r1, var_args, p0, p1, p2) { alert(r0 + r1 + p2); }");
  }

  @Test
  public void testTwoRequiredOneOptionalFifthVarArgReferenced() {
    test("function foo(r0, r1, opt_1)"
       + "  {alert(r0 + r1 + opt_1 + arguments[4]);}",
         "function foo(r0, r1, opt_1, p0, p1)"
       + "  {alert(r0 + r1 + opt_1 + p1); }");
  }

  @Test
  public void testTwoRequiredTwoOptionalSixthVarArgReferenced() {
    test("function foo(r0, r1, opt_1, opt_2)"
       + "  {alert(r0 + r1 + opt_1 + opt_2 + arguments[5]);}",
         "function foo(r0, r1, opt_1, opt_2, p0, p1)"
       + "  {alert(r0 + r1 + opt_1 + opt_2 + p1); }");
  }

  @Test
  public void testInnerFunctions() {
    test("function f() { function b(  ) { arguments[0]  }}",
         "function f() { function b(p0) {            p0 }}");

    test("function f(  ) { function b() { }  arguments[0] }",
         "function f(p0) { function b() { }            p0 }");

    test("function f( )  { arguments[0]; function b(  ) { arguments[0] }}",
         "function f(p1) {           p1; function b(p0) {           p0 }}");
  }

  @Test
  public void testInnerFunctionsWithNamedArgumentInInnerFunction() {
    test("function f() { function b(x   ) { arguments[1] }}",
         "function f() { function b(x,p0) {           p0 }}");

    test("function f(  ) { function b(x) { }  arguments[0] }",
         "function f(p0) { function b(x) { }            p0 }");

    test("function f( )  { arguments[0]; function b(x   ) { arguments[1] }}",
         "function f(p1) {           p1; function b(x,p0) {           p0 }}");
  }

  @Test
  public void testInnerFunctionsWithNamedArgumentInOutterFunction() {
    test("function f(x) { function b(  ) { arguments[0] }}",
         "function f(x) { function b(p0) {           p0 }}");

    test("function f(x   ) { function b() { }  arguments[1] }",
         "function f(x,p0) { function b() { }            p0 }");

    test("function f(x   ) { arguments[1]; function b(  ) { arguments[0] }}",
         "function f(x,p1) {           p1; function b(p0) {           p0 }}");
  }

  @Test
  public void testInnerFunctionsWithNamedArgumentInInnerAndOutterFunction() {
    test("function f(x) { function b(x   ) { arguments[1] }}",
         "function f(x) { function b(x,p0) {           p0 }}");

    test("function f(x   ) { function b(x) { }  arguments[1] }",
         "function f(x,p0) { function b(x) { }            p0 }");

    test("function f(x   ) { arguments[1]; function b(x   ) { arguments[1] }}",
         "function f(x,p1) {           p1; function b(x,p0) {           p0 }}");
  }

  @Test
  public void testInnerFunctionsAfterArguments() {
    // This caused a bug earlier due to incorrect push and pop of the arguments
    // access stack.
    test("function f(  ) { arguments[0]; function b() { function c() { }} }",
         "function f(p0) {           p0; function b() { function c() { }} }");
  }

  @Test
  public void testNoOptimizationWhenGetProp() {
    testSame("function f() { arguments[0]; arguments.size }");
  }

  @Test
  public void testNoOptimizationWhenIndexIsNotNumberConstant() {
    testSame("function f() { arguments[0]; arguments['callee'].length}");
    testSame("function f() { arguments[0]; arguments.callee.length}");
    testSame("function f() { arguments[0]; var x = 'callee'; arguments[x].length}");
  }

  @Test
  public void testDecimalArgumentIndex() {
    testSame("function f() { arguments[0.5]; }");
  }

  @Test
  public void testArrowFunctions() {

    // simple
    test(
        "function f()   { ( ) => { alert(arguments[0]); } }",
        "function f(p0) { ( ) => { alert(          p0); } }");

    // no var args
    testSame("function f() { (a,b,c) => alert(a + b + c); }");

    test(
        "function f()   { (a,b,c) => alert(arguments[0]); }",
        "function f(p0) { (a,b,c) => alert(          p0); }");

    // two var args
    test(
        "function f()         { (a) => alert(arguments[1] + arguments[2]); }",
        "function f(p0,p1,p2) { (a) => alert(          p1 +           p2); }");

    // test with required params
    test(
        "function f()       { (req0, var_args) => alert(req0 + arguments[1]); }",
        "function f(p0, p1) { (req0, var_args) => alert(req0 +           p1); }");
  }

  @Test
  public void testArrowFunctionIsInnerFunction() {

    test(
        "function f()   { ( ) => { arguments[0] } }",
        "function f(p0) { ( ) => {           p0 } }");

    // Arrow function after argument
    test(
        "function f( )  { arguments[0]; ( ) => { arguments[0] } }",
        "function f(p0) {           p0; ( ) => {           p0 } }");
  }

  @Test
  public void testArrowFunctionDeclaration() {

    test(
        "function f()   { var f = ( ) => { alert(arguments[0]); } }",
        "function f(p0) { var f = ( ) => { alert(          p0); } }");
  }

  @Test
  public void testNestedFunctions() {
    //Arrow inside arrow inside vanilla function

    test(
        "function f()   { () => { () => { arguments[0]; } } }",
        "function f(p0) { () => { () => {           p0; } } }");

    test(
        "function f()   { () => { alert(arguments[0]); () => { arguments[0]; } } }",
        "function f(p0) { () => { alert(          p0); () => {           p0; } } }");

    test(
        "function f()       { () => { alert(arguments[0]); () => { arguments[1]; } } }",
        "function f(p0, p1) { () => { alert(          p0); () => {           p1; } } }");

  }

  @Test
  public void testNoOptimizationWhenArgumentIsUsedAsFunctionCall() {
    testSame("function f() {arguments[0]()}");
  }

  @Test
  public void testUnusualArgumentsUsage() {
    testSame("function f(x) { x[arguments]; }");
  }

  @Test
  public void testNegativeIndexNoCrash() {
    testSame("function badFunction() { arguments[-1]; }");
  }
}
