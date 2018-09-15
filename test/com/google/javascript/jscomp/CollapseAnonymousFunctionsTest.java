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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CollapseAnonymousFunctions}
 *
 */
@RunWith(JUnit4.class)
public final class CollapseAnonymousFunctionsTest extends CompilerTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    this.enableNormalize();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CollapseAnonymousFunctions(compiler);
  }

  @Test
  public void testGlobalScope() {
    test("var f = function(){}", "function f(){}");
  }

  @Test
  public void testLocalScope1() {
    test("function f(){ var x = function(){}; x() }",
         "function f(){ function x(){} x() }");
  }

  @Test
  public void testLocalScope2() {
    test("function f(){ var x = function(){}; return x }",
         "function f(){ function x(){} return x }");
  }

  @Test
  public void testLet() {
    test(
        "let f = function() {};",
        "function f() {}");
  }

  @Test
  public void testConst() {
    test(
        "let f = function() {};",
        "function f() {}");
  }

  @Test
  public void testArrow() {
    testSame("function f() { var g = () => this; }");
    // It would be safe to collapse this one because it doesn't reference 'this' or 'arguments'
    // but (at least for now) we don't.
    testSame("let f = () => { console.log(0); };");
  }

  @Test
  public void testDestructuring() {
    testSame("var {name} = function() {};");
    testSame("let {name} = function() {};");
    testSame("const {name} = function() {};");

    testSame("var [x] = function() {};");
    testSame("let [x] = function() {};");
    testSame("const [x] = function() {};");
  }

  @Test
  public void testVarNotImmediatelyBelowScriptOrBlock1() {
    testSame("if (x) var f = function(){}");
  }

  @Test
  public void testVarNotImmediatelyBelowScriptOrBlock2() {
    testSame(
        lines(
            "var x = 1;",
            "if (x == 1) {",
            "  var f = function () { alert('b')}",
            "} else {",
            "  f = function() { alert('c')}",
            "}",
            "f();"));
  }

  @Test
  public void testVarNotImmediatelyBelowScriptOrBlock3() {
    testSame("var x = 1; if (x) {var f = function(){return x}; f(); x--;}");
  }

  // TODO(tbreisacher): We could collapse in this case, but we probably need to tweak Normalize.
  @Test
  public void testLetNotImmediatelyBelowScriptOrBlock1() {
    testSame("if (x) let f = function() {};");
  }

  // TODO(tbreisacher): We could collapse in this case, but we probably need to tweak Normalize.
  @Test
  public void testLetNotImmediatelyBelowScriptOrBlock2() {
    testSame("let x = 1; if (x) { let f = function() {return x}; f(); x--;}");
  }

  @Test
  public void testMultipleVar() {
    test("var f = function(){}; var g = f", "function f(){} var g = f");
  }

  @Test
  public void testMultipleVar2() {
    test("var f = function(){}; var g = f; var h = function(){}",
         "function f(){}var g = f;function h(){}");
  }

  @Test
  public void testBothScopes() {
    test("var x = function() { var y = function(){} }",
         "function x() { function y(){} }");
  }

  @Test
  public void testLocalScopeOnly1() {
    test("if (x) var f = function(){ var g = function(){} }",
         "if (x) var f = function(){ function g(){} }");
  }

  @Test
  public void testLocalScopeOnly2() {
    test("if (x) var f = function(){ var g = function(){} };",
         "if (x) var f = function(){ function g(){} }");
  }

  @Test
  public void testLocalScopeOnly3() {
    test("if (x){ var f = function(){ var g = function(){} };}",
         "if (x){ var f = function(){ function g(){} }}");
  }

  @Test
  public void testReturn() {
    test("var f = function(x){return 2*x}; var g = f(2);",
         "function f(x){return 2*x} var g = f(2)");
  }

  @Test
  public void testAlert() {
    test("var x = 1; var f = function(){alert(x)};",
         "var x = 1; function f(){alert(x)}");
  }

  @Test
  public void testRecursiveInternal1() {
    testSame("var f = function foo() { foo() }");
  }

  @Test
  public void testRecursiveInternal2() {
    testSame("var f = function foo() { function g(){foo()} g() }");
  }

  @Test
  public void testRecursiveExternal1() {
    test("var f = function foo() { f() }",
         "function f() { f() }");
  }

  @Test
  public void testRecursiveExternal2() {
    test("var f = function foo() { function g(){f()} g() }",
         "function f() { function g(){f()} g() }");
  }

  @Test
  public void testConstantFunction1() {
    test("var FOO = function(){};FOO()",
         "function FOO(){}FOO()");
  }

  @Test
  public void testInnerFunction1() {
    test(
        lines(
            "function f() { ",
            "  var x = 3;",
            "  var y = function() { return 4; };",
            "  return x + y();",
            "}"),
        lines(
            "function f() { ",
            "  function y() { return 4; }",
            "  var x = 3;",
            "  return x + y();",
            "}"));
  }
}
