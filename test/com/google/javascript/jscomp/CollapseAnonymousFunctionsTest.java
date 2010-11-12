/*
 * Copyright 2008 The Closure Compiler Authors.
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


/**
 * Tests for {@link CollapseAnonymousFunctions}
 *
 */
public class CollapseAnonymousFunctionsTest extends CompilerTestCase {
  public CollapseAnonymousFunctionsTest() {
    this.enableNormalize();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CollapseAnonymousFunctions(compiler);
  }

  public void testGlobalScope() {
    test("var f = function(){}", "function f(){}");
  }

  public void testLocalScope1() {
    test("function f(){ var x = function(){}; x() }",
         "function f(){ function x(){} x() }");
  }

  public void testLocalScope2() {
    test("function f(){ var x = function(){}; return x }",
         "function f(){ function x(){} return x }");
  }

  public void testVarNotImmediatelyBelowScriptOrBlock1() {
    testSame("if (x) var f = function(){}");
  }

  public void testVarNotImmediatelyBelowScriptOrBlock2() {
    testSame("var x = 1;" +
             "if (x == 1) {" +
             "  var f = function () { alert('b')}" +
             "} else {" +
             "  f = function() { alert('c')}" +
             "}" +
             "f();");
  }

  public void testVarNotImmediatelyBelowScriptOrBlock3() {
    testSame("var x = 1; if (x) {var f = function(){return x}; f(); x--;}");
  }

  public void testMultipleVar() {
    test("var f = function(){}; var g = f", "function f(){} var g = f");
  }

  public void testMultipleVar2() {
    test("var f = function(){}; var g = f; var h = function(){}",
         "function f(){}var g = f;function h(){}");
  }

  public void testBothScopes() {
    test("var x = function() { var y = function(){} }",
         "function x() { function y(){} }");
  }

  public void testLocalScopeOnly1() {
    test("if (x) var f = function(){ var g = function(){} }",
         "if (x) var f = function(){ function g(){} }");
  }

  public void testLocalScopeOnly2() {
    test("if (x) var f = function(){ var g = function(){} };",
         "if (x) var f = function(){ function g(){} }");
  }

  public void testReturn() {
    test("var f = function(x){return 2*x}; var g = f(2);",
         "function f(x){return 2*x} var g = f(2)");
  }

  public void testAlert() {
    test("var x = 1; var f = function(){alert(x)};",
         "var x = 1; function f(){alert(x)}");
  }

  public void testRecursiveInternal1() {
    testSame("var f = function foo() { foo() }");
  }

  public void testRecursiveInternal2() {
    testSame("var f = function foo() { function g(){foo()} g() }");
  }

  public void testRecursiveExternal1() {
    test("var f = function foo() { f() }",
         "function f() { f() }");
  }

  public void testRecursiveExternal2() {
    test("var f = function foo() { function g(){f()} g() }",
         "function f() { function g(){f()} g() }");
  }

  public void testConstantFunction1() {
    test("var FOO = function(){};FOO()",
         "function FOO(){}FOO()");
  }

  public void testInnerFunction1() {
    test(
        "function f() { " +
        "  var x = 3; var y = function() { return 4; }; return x + y();" +
        "}",
        "function f() { " +
        "  function y() { return 4; } var x = 3; return x + y();" +
        "}");
  }
}
