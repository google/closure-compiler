/*
 * Copyright 2006 The Closure Compiler Authors.
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
 * Unit test for {@link NameAnonymousFunctionsTest}.
 *
 */
@RunWith(JUnit4.class)
public final class NameAnonymousFunctionsTest extends CompilerTestCase {

  private static final String EXTERNS = "var document;";

  public NameAnonymousFunctionsTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new NameAnonymousFunctions(compiler);
  }

  @Test
  public void testSimpleVarAssignment() {
    test("var a = function() { return 1; }",
         "var a = function $a$() { return 1; }");
  }

  @Test
  public void testSimpleLetAssignment() {
    test("let a = function() { return 1; }", "let a = function $a$() { return 1; }");
  }

  @Test
  public void testSimpleConstAssignment() {
    test("const a = function() { return 1; }", "const a = function $a$() { return 1; }");
  }

  @Test
  public void testAssignmentToProperty() {
    test("var a = {}; a.b = function() { return 1; }",
         "var a = {}; a.b = function $a$b$() { return 1; }");
  }

  @Test
  public void testAssignmentToPrototype1() {
    test("function a() {} a.prototype.b = function() { return 1; };",
         "function a() {} " +
         "a.prototype.b = function $a$$b$() { return 1; };");
  }

  @Test
  public void testAssignmentToPrototype2() {
    test("var a = {}; " +
         "a.b = function() {}; " +
         "a.b.prototype.c = function() { return 1; };",
         "var a = {}; " +
         "a.b = function $a$b$() {}; " +
         "a.b.prototype.c = function $a$b$$c$() { return 1; };");
  }

  @Test
  public void testAssignmentToPrototype3() {
    test("function a() {} a.prototype['b'] = function() { return 1; };",
         "function a() {} " +
         "a.prototype['b'] = function $a$$b$() { return 1; };");
  }

  @Test
  public void testAssignmentToPrototype4() {
    test("function a() {} a['prototype']['b'] = function() { return 1; };",
         "function a() {} " +
         "a['prototype']['b'] = function $a$$b$() { return 1; };");
  }

  @Test
  public void testPrototypeInitializer() {
    test("function a(){} a.prototype = {b: function() { return 1; }};",
         "function a(){} " +
         "a.prototype = {b: function $a$$b$() { return 1; }};");
  }

  @Test
  public void testMultiplePrototypeInitializer() {
    test("function a(){} a.prototype = {b: function() { return 1; }, " +
         "c: function() { return 2; }};",
         "function a(){} " +
         "a.prototype = {b: function $a$$b$() { return 1; }," +
         "c: function $a$$c$() { return 2; }};");
  }

  @Test
  public void testRecursiveObjectLiteral() {
    test("function a(){} a.prototype = {b: {c: function() { return 1; }}}",
         "function a(){}a.prototype={b:{c:function $a$$b$c$(){return 1}}}");
  }

  @Test
  public void testAssignmentToPropertyOfCallReturnValue() {
    test("document.getElementById('x').onClick = function() {};",
         "document.getElementById('x').onClick = " +
         "function $document$getElementById$onClick$() {};");
  }

  @Test
  public void testAssignmentToPropertyOfArrayElement() {
    test("var a = {}; a.b = [{}]; a.b[0].c = function() {};",
         "var a = {}; a.b = [{}]; a.b[0].c = function $a$b$0$c$() {};");
    test("var a = {b: {'c': {}}}; a.b['c'].d = function() {};",
         "var a = {b: {'c': {}}}; a.b['c'].d = function $a$b$c$d$() {};");
    test("var a = {b: {'c': {}}}; a.b[x()].d = function() {};",
         "var a = {b: {'c': {}}}; a.b[x()].d = function $a$b$x$d$() {};");
  }

  @Test
  public void testAssignmentToObjectLiteralOnDeclaration() {
    testSame("var a = { b: function() {} }");
    testSame("var a = { b: { c: function() {} } }");
  }

  @Test
  public void testAssignmentToGetElem() {
    test("function f() {win['x' + this.id] = function(a){};}",
         "function f() {win['x' + this.id] = function $win$x$this$id$(a){};}");
  }

  @Test
  public void testGetElemWithDashes() {
    test("var foo = {}; foo['-'] = function() {};",
         "var foo = {}; foo['-'] = function $foo$__0$() {};");
  }

  @Test
  public void testWhatCausedIeToFail() {
    // If the function was given the name main, for some reason IE failed to
    // handle this case properly. That's why we give it the name $main$. FF
    // handled the case fine.
    test("var main;" +
        "(function() {" +
        "  main = function() {" +
        "    return 5;" +
        "  };" +
        "})();" +
        "" +
        "main();",
        "var main;(function(){main=function $main$(){return 5}})();main()");
  }

  @Test
  public void testIgnoreArrowFunctions() {
    testSame("var a = () => 1");
    testSame("var a = {b: () => 1};");
    testSame("function A() {} A.prototype.foo = () => 5");
  }

  @Test
  public void testComputedProperty() {
    test(
        "function A() {} A.prototype = {['foo']: function() {} };",
        "function A() {} A.prototype = {['foo']: function $A$$foo$() {} };");

    test(
        "function A() {} A.prototype = {[getName() + '_1']: function() {} };",
        "function A() {} A.prototype = {[getName() + '_1']: function $A$$getName$_1$() {} };");
  }

  @Test
  public void testGetter() {
    testSame("function A() {} A.prototype = { get foo() { return 5; } }");
  }

  @Test
  public void testSetter() {
    testSame("function A() {} A.prototype = { set foo(bar) {} }");
  }

  @Test
  public void testMethodDefinitionShorthand() {
    testSame("var obj = { b() {}, c() {} }");
    testSame("var obj; obj = { b() {}, c() {} }");
  }

  @Test
  public void testClasses() {
    testSame("class A { static foo() {} }");
    testSame("class A { constructor() {} foo() {} }");
  }

  @Test
  public void testExportedFunctions() {
    // Don't provide a name in the first case, since it would declare the function in the module
    // scope and potentially be unsafe.
    testSame("export default function() {}");
    // In this case, adding a name would be okay since this is a function expression.
    testSame("export default (function() {})");
    testSame("export default function foo() {}");
  }

  @Test
  public void testDefaultParameters() {
    test("function f(g = function() {}) {}", "function f(g = function $g$() {}) {}");
  }

  @Test
  public void testSimpleGeneratorAssignment() {
    test("var a = function *() { yield 1; }",
        "var a = function *$a$() { yield 1; }");
  }

  @Test
  public void testDestructuring() {
    test("var {a = function() {}} = {};", "var {a = function $a$() {}} = {};");
  }
}
