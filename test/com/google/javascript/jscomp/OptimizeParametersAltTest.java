/*
 * Copyright 2009 Google Inc.
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
// TODO(dhans): Tests which does not work are commented out
// Some of them may indicate bugs, but some are not supposed to work with
// the new implementation
/**
 * Tests for {@link OptimizeParameters}
 *
 */
public class OptimizeParametersAltTest extends CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new OptimizeParametersAlt(compiler);
  }

  @Override
  public void setUp() {
    super.enableLineNumberCheck(false);
  }

  public void testNoRemoval() {
    testSame("function foo(p1) { } foo(1); foo(2)");
    testSame("function foo(p1) { } foo(1,2); foo(3,4)");
  }

  public void testNotAFunction() {
    testSame("var x = 1; x; x = 2");
  }

  public void testRemoveOneOptionalNamedFunction() {
    test("function foo(p1) { } foo()", "function foo() {var p1} foo()");
  }

  // TODO(dhans): add more test cases for that
  public void testDifferentScopesDoesNotInterfere() {
    test("function f(a, b) {} f(1, 2); f(1, 3); " +
        "function h() {function f(a) {} f(4); f(5);} f(1, 2);",
        "function f(b) {var a = 1} f(2); f(3); " +
        "function h() {function f(a) {} f(4); f(5);} f(2);");
  }

  public void testOptimizeOnlyImmutableValues() {
    test("function foo(a) {}; foo(undefined);",
         "function foo() {var a = undefined}; foo()");
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

  public void testRemoveOneOptionalVarAssignment() {
    test("var foo = function (p1) { }; foo()",
        "var foo = function () {var p1}; foo()");
  }

  public void testDoOptimizeCall() {
    testSame("var foo = function () {}; foo(); foo.call();");
    testSame("var foo = function () {}; foo(); foo.call(this);");
    test("var foo = function (a, b) {}; foo(1); foo.call(this, 1);",
         "var foo = function () {var a = 1; var b;}; foo(); foo.call(this)");
  }

  public void testRemoveOneOptionalExpressionAssign() {
    test("var foo; foo = function (p1) { }; foo()",
        "var foo; foo = function () {var p1}; foo()");
  }

  public void testRemoveOneOptionalOneRequired() {
    test("function foo(p1, p2) { } foo(1); foo(2)",
        "function foo(p1) {var p2} foo(1); foo(2)");
  }

  public void testRemoveOneOptionalMultipleCalls() {
    test( "function foo(p1, p2) { } foo(1); foo(2); foo()",
        "function foo(p1) {var p2} foo(1); foo(2); foo()");
  }

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

  public void testRemoveVarArg() {
    test("function foo(p1, var_args) { } foo(1); foo(2)",
        "function foo(p1) { var var_args } foo(1); foo(2)");
  }

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

  public void testAliasMethodsDontGetOptimize2() {
    String src =
        "var foo = function(a, b) {};" +
        "var bar = foo;" +
        "foo(1);" +
        "bar(2,3);";
    testSame(src);
  }

  public void testAliasMethodsDontGetOptimize3() {
    String src =
        "var array = {};" +
        "array[0] = function(a, b) {};" +
        "var foo = array[0];" + // foo should be marked as aliased.
        "foo(1);";
    testSame(src);
  }

  public void testAliasMethodsDontGetOptimize4() {
    String src =
        "function foo(bar) {};" +
        "baz = function(a) {};" +
        "baz(1);" +
        "foo(baz);"; // Baz should be aliased.
    testSame(src);
  }

  public void testMethodsDefinedInArraysDontGetOptimized() {
    String src =
        "var array = [true, function (a) {}];" +
        "array[1](1)";
    testSame(src);
  }

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
  }

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

  public void testNoOptimizationForExternsFunctions() {
    testSame("function _foo(x, y, z){}; _foo(1);");
  }

  public void testNoOptimizationForGoogExportSymbol() {
    testSame("goog.exportSymbol('foo', foo);" +
             "function foo(x, y, z){}; foo(1);");
  }

  public void testNoArgumentRemovalNonEqualNodes() {
    testSame("function foo(a){}; foo('bar'); foo('baz');");
    testSame("function foo(a){}; foo(1.0); foo(2.0);");
    testSame("function foo(a){}; foo(true); foo(false);");
    testSame("var a = 1, b = 2; function foo(a){}; foo(a); foo(b);");
    testSame("function foo(a){}; foo(/&/g); foo(/</g);");
  }

  public void testCallIsIgnore() {
    testSame("var goog;" +
        "goog.foo = function(a, opt) {};" +
        "var bar = function(){goog.foo.call(this, 1)};" +
        "goog.foo(1);");
  }

  public void testApplyIsIgnore() {
    testSame("var goog;" +
        "goog.foo = function(a, opt) {};" +
        "var bar = function(){goog.foo.apply(this, 1)};" +
        "goog.foo(1);");
  }

  public void testFunctionWithReferenceToArgumentsShouldNotBeOptimize() {
    testSame("function foo(a,b,c) { return arguments.size; };" +
             "foo(1);");
    testSame("var foo = function(a,b,c) { return arguments.size }; foo(1);");
    testSame("var foo = function bar(a,b,c) { return arguments.size }; " +
             "foo(2); bar(2);");
  }

  public void testFunctionWithTwoNames() {
    test("var foo = function bar(a,b) {};",
         "var foo = function bar() {var a; var b;}");
    test("var foo = function bar(a,b) {}; foo(1)",
         "var foo = function bar() {var a = 1; var b;}; foo()");
    test("var foo = function bar(a,b) {}; bar(1);",
         "var foo = function bar() {var a = 1; var b;}; bar()");
    test("var foo = function bar(a,b) {}; foo(1); foo(2)",
         "var foo = function bar(a) {var b}; foo(1); foo(2)");
    test("var foo = function bar(a,b) {}; foo(1); bar(1)",
         "var foo = function bar() {var a = 1; var b}; foo(); bar()");
    test("var foo = function bar(a,b) {}; foo(1); bar(2)",
         "var foo = function bar(a) {var b}; foo(1); bar(2)");
    testSame("var foo = function bar(a,b) {}; foo(1,2); bar(2,1)");
  }
  public void testConstantArgumentsToConstructorCanBeOptimized() {
    String src = "function foo(a) {};" +
        "var bar = new foo(1);";
    String expected = "function foo() {var a=1;};" +
        "var bar = new foo();";
    test(src, expected);
  }

  public void testOptionalArgumentsToConstructorCanBeOptimized() {
    String src = "function foo(a) {};" +
        "var bar = new foo();";
    String expected = "function foo() {var a;};" +
        "var bar = new foo();";
    test(src, expected);
  }

  public void testRegexesCanBeInlined() {
    test("function foo(a) {}; foo(/abc/);",
        "function foo() {var a = /abc/}; foo();");
  }

  public void testConstructorUsedAsFunctionCanBeOptimized() {
    String src = "function foo(a) {};" +
        "var bar = new foo(1);" +
        "foo(1);";
    String expected = "function foo() {var a=1;};" +
        "var bar = new foo();" +
        "foo();";
    test(src, expected);
  }

  public void testDoNotOptimizeConstructorWhenArgumentsAreNotEqual() {
    testSame("function Foo(a) {};" +
        "var bar = new Foo(1);" +
        "var baz = new Foo(2);");
  }

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

  public void testOptimizeThis() {
    String src = "function foo() {" +
        "var bar = function (a, b) {};" +
        "this.bar(2);" +
        "bar(2);}";
    String expected = "function foo() {" +
        "var bar = function () {var a = 2; var b};" +
        "this.bar();" +
        "bar();}";
    test(src, expected);
  }

  public void testDoNotOptimizeWhenArgumentsPassedAsParameter() {
    testSame("function foo(a) {}; foo(arguments)");
    testSame("function foo(a) {}; foo(arguments[0])");

    test("function foo(a, b) {}; foo(arguments, 1)",
         "function foo(a) {var b = 1}; foo(arguments)");

    test("function foo(a, b) {}; foo(arguments)",
    "function foo(a) {var b}; foo(arguments)");
  }

  public void testDoNotOptimizeGoogExportFunctions() {
    testSame("function foo(a, b) {}; foo(); goog.export_function(foo);");
  }
}
