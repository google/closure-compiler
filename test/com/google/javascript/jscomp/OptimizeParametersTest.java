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

/**
 * Tests for {@link OptimizeParameters}. Note: interaction with {@link RemoveUnusedCode} is tested
 * in {@link OptimizeCallsTest}.
 */
@RunWith(JUnit4.class)
public final class OptimizeParametersTest extends CompilerTestCase {

  public OptimizeParametersTest() {
    super(lines(DEFAULT_EXTERNS, "var alert;"));
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new OptimizeParameters(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    enableGatherExternProperties();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testNoRemoval1() {
    testSame("function foo(p1) { } foo(1); foo(2)"); // required "remove unused vars"
    testSame("function foo(p1) { } foo(this);");
    testSame("function foo(p1) { } function g() {foo(arguments)}; g();");
    // Can't move a reference to a local.
    testSame("function foo(p1) { use(p1); } function g() {var x = 1; foo(x);}; g();");
  }

  @Test
  public void testNoRemoval2() {
    testSame("var foo = (p1)=>{ }; foo(1); foo(2)"); // required "remove unused vars"
    testSame("var foo = (p1)=>{ }; foo(this);");
    testSame("var foo = (p1)=>{ }; function g() {foo(arguments)}; g();");
    // Can't move a reference to a local.
    testSame("var foo = (p1)=>{ use(p1); }; function g() {var x = 1; foo(x);}; g();");
  }

  @Test
  public void testNoRemovalSpread() {
    // TODO(johnlenz): make spread removable
    testSame("function f(p1) {} f(...x);");
    testSame("function f(...p1) {} f(...x);");
    test("function f(p1, ...p2) {} f(1, ...x);", "function f(...p2) {var p1 = 1;} f(...x);");
  }

  @Test
  public void testRemovalRest1() {
    // rest as the first parameter
    test(
        "function f(...p1){          } f();",
        "function f(     ){var p1=[];} f()");
    test(
        "function f(...p1){           } f(1);",
        "function f(     ){var p1=[1];} f( )");
    test(
        "function f(...p1){            use(p1)} f(1);",
        "function f(     ){var p1=[1]; use(p1)} f( )");
    test(
        "function f(...p1){                 } f(alert());",
        "function f(     ){var p1=[alert()];} f(       );");
  }

  @Test
  public void testRemovalRest2() {
    // rest as the second parameter
    test(
        "function f(p1, ...p2){          } f(x); f(y);",
        "function f(p1,      ){var p2=[];} f(x); f(y);");
    test(
        "function f(p1, ...p2){           } f(x, 1); f(y, 1);",
        "function f(p1,      ){var p2=[1];} f(x   ); f(y   );");
    test(
        "function f(p1, ...p2){            use(p2)} f(x, 1); f(y, 1);",
        "function f(p1,      ){var p2=[1]; use(p2)} f(x   ); f(y   );");
    test(
        "function f(p1, ...p2){                 } f(x, alert()); f(y, alert());",
        "function f(p1,      ){var p2=[alert()];} f(x         ); f(y         );");
  }

  @Test
  public void testRemovalRest3() {
    testSame(
        "function f(...p1){} function _g(x) { f(x); f(x); }");
  }

  @Test
  public void testRemovalRestWithDestructuring1() {
    test(
        "function f(...[a]){          } f();",
        "function f(      ){var [a]=[];} f()");
    test(
        "function f(...[a]){          } f(1);",
        "function f(      ){var [a]=[1];} f()");
    test(
        "function f(...{a}){          } f();",
        "function f(      ){var {a}=[];} f()");
    test(
        "function f(...{a}){            } f(1);",
        "function f(      ){var {a}=[1];} f( )");
  }

  @Test
  public void testRemoveParamWithDefault1() {
    test(
        "function f(a = 1){          } f();",
        "function f(     ){var a = 1;} f()");
    test(
        "function f(a = []){           } f();",
        "function f(      ){var a = [];} f()");
    test(
        "function f(a = alert()){                } f();",
        "function f(           ){var a = alert();} f()");
    test(
        "function f([a] = []){             } f();",
        "function f(        ){var [a] = [];} f()");
    test(
        "function f([a = 1] = []){                 } f();",
        "function f(            ){var [a = 1] = [];} f()");
    test(
        "function f([a = 1]){                 } f([]);",
        "function f(       ){var [a = 1] = [];} f(  );");
  }

  @Test
  public void testRemoveParamWithDefault2() {
    test(
        "function f(a = 0){          } f(1);",
        "function f(     ){var a = 1;} f( )");
    test(
        "function f({a} = 0){           } f([]);",
        "function f(       ){var {a}=[];} f(  )");
    test(
        "function f({a = 1} = 0){               } f([]);",
        "function f(           ){var {a = 1}=[];} f(  )");
    test(
        "function f({a = 1}){               } f({});",
        "function f(       ){var {a = 1}={};} f(  )");
    test(
        "function f({a: a = 1}){               } f({});",
        "function f(          ){var {a = 1}={};} f(  )");
  }

  @Test
  public void testNoRemoveParamWithDefault() {
    // different scopes
    testSame(
        "function f(p = 1){} function _g(x) { f(x); f(x); }");

    // TODO(johnlenz): add logic for adding an undefined check to the body of the function
    // so that this can be inlined into the function body
    testSame(
        "function f(a = 2){} f(alert);");
  }

  @Test
  public void testSimpleRemoval0() {
    test("function foo(p1) {       } foo(); foo()",
         "function foo(  ) {var p1;} foo(); foo()");
    test("function foo(p1) {           } foo(1); foo(1)",
         "function foo(  ) {var p1 = 1;} foo( ); foo( )");
    test("function foo(p1) {           } foo(1,2); foo(1,4)",
         "function foo(  ) {var p1 = 1;} foo(   ); foo(   )");
    test("function foo(p1) { } foo(1,2); foo(3,4);",
         "function foo(p1) { } foo(1  ); foo(3  );");
    test("function foo(p1) {           } foo(1,x()); foo(1,y())",
         "function foo(  ) {var p1 = 1;} foo(  x()); foo(  y())");
  }

  @Test
  public void testSimpleRemoval1() {
    // parameter never supplied
    test("var foo = (p1)=>{       }; foo(); foo()",
         "var foo = (  )=>{var p1;}; foo(); foo()");
    test("let foo = (p1)=>{       }; foo(); foo()",
         "let foo = (  )=>{var p1;}; foo(); foo()");
    test("const foo = (p1)=>{       }; foo(); foo()",
         "const foo = (  )=>{var p1;}; foo(); foo()");

    // constant parameter
    test("var foo = (p1)=>{           }; foo(1); foo(1)",
         "var foo = (  )=>{var p1 = 1;}; foo( ); foo( )");
    test("let foo = (p1)=>{           }; foo(1); foo(1)",
         "let foo = (  )=>{var p1 = 1;}; foo( ); foo( )");
    test("const foo = (p1)=>{           }; foo(1); foo(1)",
         "const foo = (  )=>{var p1 = 1;}; foo( ); foo( )");
  }

  @Test
  public void testSimpleRemoval2() {
    test("function f(p1) {       } new f(); new f()",
         "function f(  ) {var p1;} new f(); new f()");
    test("function f(p1) {           } new f(1); new f(1)",
         "function f(  ) {var p1 = 1;} new f( ); new f( )");
    test("function f(p1) {           } new f(1,2); new f(1,4)",
         "function f(  ) {var p1 = 1;} new f(   ); new f(   )");
    test("function f(p1) { } new f(1,2); new f(3,4);",
         "function f(p1) { } new f(1  ); new f(3  );");
    test("function f(p1) {           } new f(1,x()); new f(1,y())",
         "function f(  ) {var p1 = 1;} new f(  x()); new f(  y())");
  }

  @Test
  public void testSimpleRemovalInstanceof() {
    test("function f(p1) {       } x instanceof f; new f(); new f()",
         "function f(  ) {var p1;} x instanceof f; new f(); new f()");
    test("function f(p1) {           } x instanceof f; new f(1); new f(1)",
         "function f(  ) {var p1 = 1;} x instanceof f; new f( ); new f( )");
  }

  @Test
  public void testSimpleRemovalTypeof() {
    test("function f(p1) {       } typeof f != 'undefined' && f();",
         "function f(  ) {var p1;} typeof f != 'undefined' && f();");
    test("function f(p1) {           } typeof f != 'undefined'; f(1);",
         "function f(  ) {var p1 = 1;} typeof f != 'undefined'; f();");
  }

  @Test
  public void testSimpleRemoval4() {
    test("function f(p1) {       } f.prop = 1; new f(); new f()",
         "function f(  ) {var p1;} f.prop = 1; new f(); new f()");
    test("function f(p1) {           } f.prop = 1; new f(1); new f(1)",
         "function f(  ) {var p1 = 1;} f.prop = 1; new f( ); new f( )");
    test("function f(p1) {       } f['prop'] = 1; new f(); new f()",
         "function f(  ) {var p1;} f['prop'] = 1; new f(); new f()");
   test("function f(p1) {           } f['prop'] = 1; new f(1); new f(1)",
        "function f(  ) {var p1 = 1;} f['prop'] = 1; new f( ); new f( )");
  }

  @Test
  public void testSimpleRemovalAsync() {
    // parameter never supplied
    test("var f = async function (p1) {       }; f(); f()",
         "var f = async function (  ) {var p1;}; f(); f()");
    test("let f = async function (p1) {       }; f(); f()",
         "let f = async function (  ) {var p1;}; f(); f()");
    test("const f = async function (p1) {       }; f(); f()",
         "const f = async function (  ) {var p1;}; f(); f()");

    // constant parameter
    test("var f = async function (p1) {          }; f(1); f(1)",
         "var f = async function (  ) {var p1 = 1;}; f( ); f( )");
    test("let f = async function (p1) {           }; f(1); f(1)",
         "let f = async function (  ) {var p1 = 1;}; f( ); f( )");
    test("const f = async function (p1) {          }; f(1); f(1)",
         "const f = async function (  ) {var p1 = 1;}; f( ); f( )");
  }

  @Test
  public void testSimpleRemovalGenerator() {
    // parameter never supplied
    test("var f = function * (p1) {       }; f(); f()",
         "var f = function * (  ) {var p1;}; f(); f()");
    test("let f = function * (p1) {       }; f(); f()",
         "let f = function * (  ) {var p1;}; f(); f()");
    test("const f = function * (p1) {       }; f(); f()",
         "const f = function * (  ) {var p1;}; f(); f()");

    // constant parameter
    test("var f = function * (p1) {          }; f(1); f(1)",
         "var f = function * (  ) {var p1 = 1;}; f( ); f( )");
    test("let f = function * (p1) {           }; f(1); f(1)",
         "let f = function * (  ) {var p1 = 1;}; f( ); f( )");
    test("const f = function * (p1) {          }; f(1); f(1)",
         "const f = function * (  ) {var p1 = 1;}; f( ); f( )");
  }

  @Test
  public void testNotAFunction() {
    testSame("var x = 1; x; x = 2");
  }

  @Test
  public void testRemoveOneOptionalNamedFunction() {
    test("function foo(p1) { } foo()", "function foo() {var p1} foo()");
  }

  @Test
  public void testDifferentScopes() {
    test("function f(a, b) {} f(1, 2); f(1, 3); " +
        "function h() {function g(a) {} g(4); g(5);} f(1, 2);",
        "function f(b) {var a = 1} f(2); f(3); " +
        "function h() {function g(a) {} g(4); g(5);} f(2);");
  }

  @Test
  public void testOptimizeOnlyImmutableValues1() {
    test(
        "function foo(a) {}; foo(undefined);",
        "function foo() {var a = undefined}; foo()");
  }

  @Test
  public void testOptimizeOnlyImmutableValues2() {
    test("function foo(a) {}; foo(null);",
        "function foo() {var a = null}; foo()");
    test("function foo(a) {}; foo(1);",
         "function foo() {var a = 1}; foo()");
    test("function foo(a) {}; foo('abc');",
        "function foo() {var a = 'abc'}; foo()");

    test("var foo = function(a) {}; foo(undefined);",
         "var foo = function() {var a = undefined}; foo()");
    test("var foo = function(a) {}; foo(null);",
         "var foo = function() {var a = null}; foo()");
    test("var foo = function(a) {}; foo(1);",
         "var foo = function() {var a = 1}; foo()");
    test("var foo = function(a) {}; foo('abc');",
         "var foo = function() {var a = 'abc'}; foo()");
  }

  @Test
  public void testOptimizeOnlyImmutableValues3() {
    // "var a = null;" gets inserted after the declaration of 'goo' so the tree stays normalized.
    test(
        lines(
            "function foo(a) {",
            "  function goo() {}",
            "  goo(a);",
            "};",
            "foo(null);"),
        lines(
            "function foo() {",
            "  function goo() {}",
            "  var a = null;",
            "  goo(a);",
            "};",
            "foo();"));

    test(
        lines(
            "function foo(a) {",
            "  function goo() {}",
            "  function boo() {}",
            "  goo(a);",
            "};",
            "foo(null);"),
        lines(
            "function foo() {",
            "  function goo() {}",
            "  function boo() {}",
            "  var a = null;",
            "  goo(a);",
            "};",
            "foo();"));
  }

  @Test
  public void testOptimizeOnlyImmutableValues4() {
    test(
        lines(
            "function foo(a) {",
            "  function goo() { return a; }",
            "};",
            "foo(null);"),
        lines(
            "function foo() {",
            "  function goo() { return a; }",
            "  var a = null;",
            "};",
            "foo();"));
  }

  @Test
  public void testRemoveOneOptionalVarAssignment() {
    test("var foo = function (p1) { }; foo()",
        "var foo = function () {var p1}; foo()");
  }

  @Test
  public void testDoOptimizeCall() {
    testSame("var foo = function () {}; foo(); foo.call();");
    // TODO(johnlenz): support removing unused "this" from .call
    testSame("var foo = function () {}; foo(); foo.call(this);");
    test(
        "var foo = function (a, b) {}; foo(1); foo.call(this, 1);",
        "var foo = function () {var a = 1;var b;}; foo(); foo.call(this);");
    testSame("var foo = function () {}; foo(); foo.call(null);");
    test(
        "var foo = function (a, b) {}; foo(1); foo.call(null, 1);",
        "var foo = function () {var a = 1;var b;}; foo(); foo.call(null);");

    testSame("var foo = function () {}; foo.call();");

    testSame("var foo = function () {}; foo.call(this);");
    test(
        "var foo = function (a) {}; foo.call(this, 1);",
        "var foo = function () {var a = 1;}; foo.call(this);");
    testSame("var foo = function () {}; foo.call(null);");
    test(
        "var foo = function (a) {}; foo.call(null, 1);",
        "var foo = function () {var a = 1;}; foo.call(null);");
  }

  @Test
  public void testDoOptimizeApply() {
    testSame("var foo = function () {}; foo(); foo.apply();");
    testSame("var foo = function () {}; foo(); foo.apply(this);");
    testSame("var foo = function (a, b) {}; foo(1); foo.apply(this, 1);");
    testSame("var foo = function () {}; foo(); foo.apply(null);");
    testSame("var foo = function (a, b) {}; foo(1); foo.apply(null, []);");

    testSame("var foo = function () {}; foo.apply();");
    testSame("var foo = function () {}; foo.apply(this);");
    testSame("var foo = function (a, b) {}; foo.apply(this, 1);");
    testSame("var foo = function () {}; foo.apply(null);");
    testSame("var foo = function (a, b) {}; foo.apply(null, []);");
  }

  @Test
  public void testRemoveOneOptionalExpressionAssign() {
    test(
        "var foo; foo = function (p1) { }; foo()",
        "var foo; foo = function () { var p1; }; foo()");
  }

  @Test
  public void testRemoveOneOptionalOneRequired() {
    test(
        "function foo(p1, p2) { } foo(1); foo(2)",
        "function foo(p1) {var p2} foo(1); foo(2)");
  }

  @Test
  public void testRemoveOneOptionalMultipleCalls() {
    test(
        "function foo(p1, p2) { } foo(1); foo(2); foo()",
        "function foo(p1) {var p2} foo(1); foo(2); foo()");
  }

  @Test
  public void testRemoveOneOptionalMultiplePossibleDefinition() {
    String src = lines(
        "var goog = {};",
        "goog.foo = function (p1, p2) { };",
        "goog.foo = function (q1, q2) { };",
        "goog.foo = function (r1, r2) { };",
        "goog.foo(1); goog.foo(2); goog.foo()");
    String result = lines(
        "var goog = {};",
        "goog.foo = function (p1) { var p2; };",
        "goog.foo = function (q1) { var q2; };",
        "goog.foo = function (r1) { var r2; };",
        "goog.foo(1); goog.foo(2); goog.foo()");
    test(src, result);
  }

  @Test
  public void testRemoveTwoOptionalMultiplePossibleDefinition() {
    String src = lines(
        "var goog = {};",
        "goog.foo = function (p1, p2, p3, p4) { };",
        "goog.foo = function (q1, q2, q3, q4) { };",
        "goog.foo = function (r1, r2, r3, r4) { };",
        "goog.foo(1,0); goog.foo(2,1); goog.foo()");
    String result = lines(
        "var goog = {};",
        "goog.foo = function (p1, p2) { var p3; var p4 };",
        "goog.foo = function (q1, q2) { var q3; var q4 };",
        "goog.foo = function (r1, r2) { var r3; var r4 };",
        "goog.foo(1,0); goog.foo(2,1); goog.foo()");
    test(src, result);
  }

  @Test
  public void testMultipleCalls() {
    String src = lines(
        "function f(p1, p2, p3, p4) { };",
        "f(1,0); f(2,1); f()");
    String result = lines(
        "function f(p1, p2) { var p3; var p4 };",
        "f(1,0); f(2,1); f()");
    test(src, result);
  }

  @Test
  public void testConstructorOptArgsNotRemoved() {
    String src =
        "/** @constructor */" +
        "var goog = function(){};" +
        "goog.prototype.foo = function(a,b) {};" +
        "goog.prototype.bar = function(a) {};" +
        "goog.bar.inherits(goog.foo);" +
        "new goog.foo(2,3);" +
        "new goog.foo(1,2);";
    testSame(src);
  }

  @Test
  public void testMultipleUnknown() {
    String src = lines(
        "var goog1 = {};",
        "goog1.foo = function () { };",
        "var goog2 = {};",
        "goog2.foo = function (p1) { };",
        "var x = getGoog();",
        "x.foo()");
    String result = lines(
        "var goog1 = {};",
        "goog1.foo = function () { };",
        "var goog2 = {};",
        "goog2.foo = function () { var p1; };",
        "var x = getGoog();",
        "x.foo()");
    test(src, result);
  }

  @Test
  public void testSingleUnknown() {
    String src =
        "var goog2 = {};" +
        "goog2.foo = function (p1) { };" +
        "var x = getGoog();" +
        "x.foo()";

    String expected =
        "var goog2 = {};" +
        "goog2.foo = function () { var p1 };" +
        "var x = getGoog();" +
        "x.foo()";
    test(src, expected);
  }

  @Test
  public void testRemoveVarArg() {
    test("function foo(p1, var_args) { } foo(1); foo(2)",
        "function foo(p1) { var var_args } foo(1); foo(2)");
  }

  @Test
  public void testAliasMethodsDontGetOptimize() {
    String src =
        "var foo = function(a, b) {};" +
        "var goog = {};" +
        "goog.foo = foo;" +
        "goog.prototype.bar = goog.foo;" +
        "new goog().bar(1,2);" +
        "foo(2);";
    testSame(src);
  }

  @Test
  public void testAliasMethodsDontGetOptimize2() {
    String src =
        "var foo = function(a, b) {};" +
        "var bar = foo;" +
        "foo(1);" +
        "bar(2,3);";
    testSame(src);
  }

  @Test
  public void testAliasMethodsDontGetOptimize3() {
    String src =
        "var array = {};" +
        "array[0] = function(a, b) {};" +
        "var foo = array[0];" + // foo should be marked as aliased.
        "foo(1);";
    testSame(src);
  }

  @Test
  public void testAliasMethodsDontGetOptimize4() {
    // Don't change the call to baz as it has been aliased.

    test(
        lines(
            "function foo(bar) {};",
            "var baz = function(a) {};",
            "baz(1);",
            "foo(baz);"),
        lines(
            "function foo() {var bar = baz};",
            "var baz = function(a) {};",
            "baz(1);",
            "foo();"));
  }

  @Test
  public void testMethodsDefinedInArraysDontGetOptimized() {
    String src =
        "var array = [true, function (a) {}];" +
        "array[1](1)";
    testSame(src);
  }

  @Test
  public void testMethodsDefinedInObjectDontGetOptimized() {
    String src =
      "var object = { foo: function bar() {} };" +
      "object.foo(1)";
    testSame(src);
    src =
      "var object = { foo: function bar() {} };" +
      "object['foo'](1)";
    testSame(src);
  }

  @Test
  public void testRemoveConstantArgument() {
    // Remove only one parameter
    test("function foo(p1, p2) {}; foo(1,2); foo(2,2);",
         "function foo(p1) {var p2 = 2}; foo(1); foo(2)");

    // Remove nothing
    testSame("function foo(p1, p2) {}; foo(1); foo(2,3);");

    // Remove middle parameter
    test("function foo(a,b,c){}; foo(1, 2, 3); foo(1, 2, 4); foo(2, 2, 3)",
         "function foo(a,c){var b=2}; foo(1, 3); foo(1, 4); foo(2, 3)");

    // Number are equals
    test("function foo(a) {}; foo(1); foo(1.0);",
         "function foo() {var a = 1;}; foo(); foo();");

    // A more OO test
    test(
        lines(
            "/** @constructor */",
            "function Person() {}; Person.prototype.run = function(a, b) {};",
            "Person.run(1, 'a'); Person.run(2, 'a');"),
        lines(
            "/** @constructor */",
            "function Person() {}; Person.prototype.run = function(a) {var b = 'a'};",
            "Person.run(1); Person.run(2);"));
  }

  @Test
  public void testCanDeleteArgumentsAtAnyPosition() {
    // Argument removed in middle and end
    String src =
        "function foo(a,b,c,d,e) {};" +
        "foo(1,2,3,4,5);" +
        "foo(2,2,4,4,5);";
    String expected =
        "function foo(a,c) {var b=2; var d=4; var e=5;};" +
        "foo(1,3);" +
        "foo(2,4);";
    test(src, expected);
  }

  @Test
  public void testNoOptimizationForExternsFunctions() {
    testSame("function _foo(x, y, z){}; _foo(1);");
  }

  @Test
  public void testNoOptimizationForGoogExportSymbol() {
    testSame("goog.exportSymbol('foo', foo);" +
             "function foo(x, y, z){}; foo(1);");
  }

  @Test
  public void testNoArgumentRemovalNonEqualNodes() {
    testSame("function foo(a){}; foo('bar'); foo('baz');");
    testSame("function foo(a){}; foo(1.0); foo(2.0);");
    testSame("function foo(a){}; foo(true); foo(false);");
    testSame("var a = 1, b = 2; function foo(a){}; foo(a); foo(b);");
    testSame("function foo(a){}; foo(/&/g); foo(/</g);");
  }

  @Test
  public void testFunctionPassedAsParam() {
    test(
        lines(
            "/** @constructor */ function person() {};",
            "person.prototype.run = function(a, b) {};",
            "person.prototype.walk = function() {};",
            "person.prototype.foo = function() { this.run(this.walk, 0.1); };",
            "person.foo();"),
        lines(
            "/** @constructor */ function person() {};",
            "person.prototype.run = function(a) { var b = 0.1; };",
            "person.prototype.walk = function() {};",
            "person.prototype.foo = function() { this.run(this.walk); };",
            "person.foo();"));
  }

  @Test
  public void testCallIsIgnore() {
    test(
        lines(
            "var goog;",
            "goog.foo = function(a, opt) {};",
            "var bar = function(){goog.foo.call(this, 1)};",
            "goog.foo(1);"),
        lines(
            "var goog;",
            "goog.foo = function() {var a = 1;var opt;};",
            "var bar = function(){goog.foo.call(this)};",
            "goog.foo();"));
  }

  @Test
  public void testApplyIsIgnore() {
    testSame("var goog;" +
        "goog.foo = function(a, opt) {};" +
        "var bar = function(){goog.foo.apply(this, 1)};" +
        "goog.foo(1);");
  }

  @Test
  public void testFunctionWithReferenceToArgumentsShouldNotBeOptimize() {
    testSame("function foo(a,b,c) { return arguments.size; }; foo(1);");
    testSame("var foo = function(a,b,c) { return arguments.size }; foo(1);");
    testSame("var foo = function bar(a,b,c) { return arguments.size }; foo(2); bar(2);");
  }

  @Test
  public void testFunctionWithTwoNames() {
    testSame("var foo = function bar(a,b) {};");
    testSame("var foo = function bar(a,b) {}; foo(1)");
    testSame("var foo = function bar(a,b) {}; bar(1);");
    testSame("var foo = function bar(a,b) {}; foo(1); foo(2)");
    testSame("var foo = function bar(a,b) {}; foo(1); bar(1)");
    testSame("var foo = function bar(a,b) {}; foo(1); bar(2)");
    testSame("var foo = function bar(a,b) {}; foo(1,2); bar(2,1)");
  }

  @Test
  public void testRecursion() {
    test("var foo = function (a,b) {foo(1, b)}; foo(1, 2)",
         "var foo = function (b) {var a=1; foo(b)}; foo(2)");
  }

  @Test
  public void testConstantArgumentsToConstructorCanBeOptimized() {
    String src = "function foo(a) {};" +
        "var bar = new foo(1);";
    String expected = "function foo() {var a=1;};" +
        "var bar = new foo();";
    test(src, expected);
  }

  @Test
  public void testOptionalArgumentsToConstructorCanBeOptimized() {
    String src = "function foo(a) {};" +
        "var bar = new foo();";
    String expected = "function foo() {var a;};" +
        "var bar = new foo();";
    test(src, expected);
  }

  @Test
  public void testRegexesCanBeInlined() {
    test("function foo(a) {}; foo(/abc/);",
         "function foo() {var a = /abc/}; foo();");
  }

  @Test
  public void testConstructorUsedAsFunctionCanBeOptimized() {
    String src = "function foo(a) {};" +
        "var bar = new foo(1);" +
        "foo(1);";
    String expected = "function foo() {var a=1;};" +
        "var bar = new foo();" +
        "foo();";
    test(src, expected);
  }

  @Test
  public void testDoNotOptimizeConstructorWhenArgumentsAreNotEqual() {
    testSame("function Foo(a) {};" +
        "var bar = new Foo(1);" +
        "var baz = new Foo(2);");
  }

  @Test
  public void testDoNotOptimizeArrayElements() {
    testSame("var array = [function (a, b) {}];");
    testSame("var array = [function f(a, b) {}]");

    testSame("var array = [function (a, b) {}];" +
        "array[0](1, 2);" +
        "array[0](1);");

    testSame("var array = [];" +
        "function foo(a, b) {};" +
        "array[0] = foo;");
  }

  @Test
  public void testOptimizeThis1() {
    String src = lines(
        "var bar = function (a, b) {};",
        "function foo() {",
        "  this.bar = function (a, b) {};",
        "  this.bar(3);",
        "  bar(2);",
        "}");
    String expected =  lines(
        "var bar = function () {var a = 2;var b;};",
        "function foo() {",
        "  this.bar = function () {var a = 3;var b;};",
        "  this.bar();",
        "  bar();",
        "}");
    test(src, expected);
  }

  @Test
  public void testOptimizeThis2() {
    String src = lines(
        "function foo() {",
        "  var bar = function (a, b) {};",
        "  this.bar = function (a, b) {};",
        "  this.bar(3);",
        "  bar(2);",
        "}");
    String expected =  lines(
        "function foo() {",
        "  var bar = function (a, b) {};",
        "  this.bar = function () {var a = 3;var b;};",
        "  this.bar();",
        "  bar(2);",
        "}");
    test(src, expected);
  }

  @Test
  public void testDoNotOptimizeWhenArgumentsPassedAsParameter() {
    testSame("function foo(a) {}; foo(arguments)");
    testSame("function foo(a) {}; foo(arguments[0])");

    test("function foo(a, b) {}; foo(arguments, 1)",
         "function foo(a) {var b = 1}; foo(arguments)");

    test("function foo(a, b) {}; foo(arguments)",
         "function foo(a) {var b}; foo(arguments)");
  }

  @Test
  public void testDoNotOptimizeGoogExportFunctions() {
    testSame("function foo(a, b) {}; foo(); goog.export_function(foo);");
  }

  @Test
  public void testDoNotOptimizeJSCompiler_renameProperty() {
    testSame("function JSCompiler_renameProperty(a) {return a};" +
             "JSCompiler_renameProperty('a');");
  }

  @Test
  public void testDoNotOptimizeJSCompiler_ObjectPropertyString() {
    testSame("function JSCompiler_ObjectPropertyString(a, b) {return a[b]};" +
             "JSCompiler_renameProperty(window,'b');");
  }

  @Test
  public void testMutableValues1() {
    test("function foo(p1) {} foo()",
         "function foo() {var p1} foo()");
    test("function foo(p1) {} foo(1)",
         "function foo() {var p1=1} foo()");
    test("function foo(p1) {} foo([])",
         "function foo() {var p1=[]} foo()");
    test("function foo(p1) {} foo({})",
         "function foo() {var p1={}} foo()");
    test("var x;function foo(p1) {} foo(x)",
         "var x;function foo() {var p1=x} foo()");
    test("var x;function foo(p1) {} foo(x())",
         "var x;function foo() {var p1=x()} foo()");
    test("var x;function foo(p1) {} foo(new x())",
         "var x;function foo() {var p1=new x()} foo()");
    test("var x;function foo(p1) {} foo('' + x)",
         "var x;function foo() {var p1='' + x} foo()");

    testSame("function foo(p1) {} foo(this)");
    testSame("function foo(p1) {} foo(arguments)");
    testSame("function foo(p1) {} foo(function(){})");
    testSame("function foo(p1) {} (function () {var x;foo(x)})()");
  }

  @Test
  public void testMutableValues2() {
    test("function foo(p1, p2) {} foo(1, 2)",
         "function foo() {var p1=1; var p2 = 2} foo()");
    test("var x; var y; function foo(p1, p2) {} foo(x(), y())",
         "var x; var y; function foo() {var p1=x(); var p2 = y()} foo()");
  }

  @Test
  public void testMutableValues3() {
    test(
        "var x; var y; var z;" +
        "function foo(p1, p2) {}" +
        "foo(x(), y()); foo(x(),y())",
        "var x; var y; var z;" +
        "function foo() {var p1=x(); var p2=y()}" +
        "foo(); foo()");
  }

  @Test
  public void testMutableValues4() {
    // Preserve the ordering of side-effects.
    // If z(), can't be moved into the function then z() may change the value
    // of x and y.
    testSame(
        "var x; var y; var z;" +
        "function foo(p1, p2, p3) {}" +
        "foo(x(), y(), z()); foo(x(),y(),3)");

    // If z(), can't be moved into the function then z() may change the value
    // of x and y.
    testSame(
        "var x; var y; var z;" +
        "function foo(p1, p2, p3) {}" +
        "foo(x, y(), z()); foo(x,y(),3)");

    // Mutable object that can not be effect by side-effects are movable,
    // however.
    test(
        "var x; var y; var z;" +
        "function foo(p1, p2, p3) {}" +
        "foo([], y(), z()); foo([],y(),3)",
        "var x; var y; var z;" +
        "function foo(p2, p3) {var p1=[]}" +
        "foo(y(), z()); foo(y(),3)");
  }

  @Test
  public void testMutableValues5() {
    test(
        "var x; var y; var z;" +
        "function foo(p1, p2) {}" +
        "new foo(new x(), y()); new foo(new x(),y())",
        "var x; var y; var z;" +
        "function foo() {var p1=new x(); var p2=y()}" +
        "new foo(); new foo()");

    test(
        "var x; var y; var z;" +
        "function foo(p1, p2) {}" +
        "new foo(x(), y()); new foo(x(),y())",
        "var x; var y; var z;" +
        "function foo() {var p1=x(); var p2=y()}" +
        "new foo(); new foo()");

    testSame(
        "var x; var y; var z;" +
        "function foo(p1, p2, p3) {}" +
        "new foo(x(), y(), z()); new foo(x(),y(),3)");

    testSame(
        "var x; var y; var z;" +
        "function foo(p1, p2, p3) {}" +
        "new foo(x, y(), z()); new foo(x,y(),3)");

    test(
        "var x; var y; var z;" +
        "function foo(p1, p2, p3) {}" +
        "new foo([], y(), z()); new foo([],y(),3)",
        "var x; var y; var z;" +
        "function foo(p2, p3) {var p1=[]}" +
        "new foo(y(), z()); new foo(y(),3)");
  }

  @Test
  public void testShadows() {
    testSame("function foo(a) {}" +
             "var x;" +
             "function f() {" +
             "  var x;" +
             "  function g() {" +
             "    foo(x());" +
             "  }" +
             "};" +
             "foo(x())");
  }

  @Test
  public void testNoCrash() {
    test(
        "function foo(a) {}" +
        "foo({o:1});" +
        "foo({o:1})",
        "function foo() {var a = {o:1}}" +
        "foo();" +
        "foo()");
  }

  @Test
  public void testGlobalCatch() {
    testSame("function foo(a) {} try {} catch (e) {foo(e)}");
  }

  @Test
  public void testNamelessParameter1() {
    test(
        externs("var g;"),
        srcs("f(g()); function f(){}"),
        expected("f(); function f(){g()}"));
  }

  @Test
  public void testNamelessParameter2() {
    test(
        externs("var g, h;"),
        srcs("f(g(),h()); function f(){}"),
        expected("f(); function f(){g();h()}"));
  }

  @Test
  public void testNoRewriteUsedClassConstructor1() {
    testSame(lines(
        "class C { constructor(a) { use(a); } }",
        "var c = new C();"));
  }

  @Test
  public void testNoRewriteUsedClassConstructor2() {
    // `constructor` aliases the class constructor
    testSame(lines(
        "class C { constructor(a) { use(a); } }",
        "var c = new C();",
        "new c.constructor(1);"));
  }

  @Test
  public void testNoRewriteUsedClassConstructor3() {
    // `super` aliases the super type constructor
    testSame(lines(
        "class C { constructor(a) { use(a); } }",
        "class D extends C { constructor() { super(1); } }",
        "var d = new D(); new C();"));
  }

  @Test
  public void testNoRewriteUsedClassConstructor4() {
    // `new.target` aliases self and subtype constructors
    testSame(lines(
        "class C { constructor() { var x = new.target(1); } }",
        "class D extends C { constructor(a) { super(); } }",
        "var d = new D(); new C();"));
  }

  @Test
  public void testNoRewriteUsedClassConstructor5() {
    // Static class methods "this" values can alias constructors.
    testSame(lines(
        "class C { constructor(a) { use(a); }; static create(a) { new this(1); } }",
        "var c = new C();",
        "C.create();"));
  }

  @Test
  public void testNoRewriteUsedClassMethodParam1() {
    testSame(lines(
        "class C { method(a) { use(a); } }",
        "var c = new C(); c.method(1); c.method(2)"));
  }

  @Test
  public void testNoRewriteUnusedClassComputedMethodParam1() {
    testSame(lines(
        "class C { [method](a) { } }",
        "var c = new C(); c[method](1); c[method](2)"));
  }

  @Test
  public void testRewriteUsedClassMethodParam1() {
    test(
        "class C { method(a) {          }} new C().method(1)",
        "class C { method( ) {var a = 1;}} new C().method( )");
  }

  @Test
  public void testNoRewriteUnsedObjectMethodParam() {
    testSame("var o = { method(a) {          }}; o.method(1)");
  }

  @Test
  public void testNoRewriteDestructured1() {
    testSame(lines(
        "class C { m(a) {}};",
        "var c = new C();",
        "({m} = c);",
        "c.m(1)"));
    testSame(lines(
        "class C { m(a) {}};",
        "var c = new C();",
        "({m:x} = c);",
        "c.m(1)"));
    testSame(lines(
        "class C { m(a) {}};",
        "var c = new C();",
        "({xx:C.prototype.m} = {xx:function(a) {}});",
        "c.m(1)"));
  }

  @Test
  public void testNoRewriteDestructured2() {
    testSame(lines(
        "var x = function(a) {};",
        "({x} = {})",
        "x(1)"));
    testSame(lines(
        "var x = function(a) {};",
        "({x:x} = {})",
        "x(1)"));
    testSame(lines(
        "var x = function(a) {};",
        "({x:x = function(a) {}} = {})",
        "x(1)"));
  }

  @Test
  public void testNoRewriteDestructured3() {
    testSame(lines(
        "var x = function(a) {};",
        "[x = function() {}] = []",
        "x(1)"));
    testSame(lines(
        "class C { method() { return 1 }}",
        "var c = new C();",
        "var y = C.prototype;",
        "[y.method] = []",
        "c.method()"));
    testSame(lines(
        "class C { method() { return 1 }}",
        "[x.method] = []",
        "y.method()"));
  }

  @Test
  public void testNoRewriteTagged1() {
    // Optimizing methods called though tagged template literal requires
    // specific knowledge of how tagged templated is supplied to the method.
    testSame(lines(
        "var f = function(a, b, c) {};",
        "f`tagged`"));

    testSame(lines(
        "var f = function(a, b, c) {};",
        "f`tagged`",
        "f()"));
  }

  @Test
  public void testArrow() {
    // Optimizing methods called though tagged template literal requires
    // specific knowledge of how tagged templated is supplied to the method.
    testSame(lines(
        "var f = (a)=>{};",
        "f`tagged`"));

    testSame(lines(
        "var f = function(a, b, c) {};",
        "f`tagged`",
        "f()"));
  }

}
