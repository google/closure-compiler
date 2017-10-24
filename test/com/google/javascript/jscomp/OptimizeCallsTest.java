/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;

/**
 * Unit tests for {#link {@link OptimizeCalls}
 *
 */
public final class OptimizeCallsTest extends CompilerTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableNormalize();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {

      @Override
      public void process(Node externs, Node root) {
        NameBasedDefinitionProvider defFinder = new NameBasedDefinitionProvider(compiler, true);
        defFinder.process(externs, root);

        new PureFunctionIdentifier(compiler, defFinder).process(externs, root);
        new RemoveUnusedVars(compiler, true, false).process(externs, root);

        final OptimizeCalls passes = new OptimizeCalls(compiler);
        passes.addPass(new OptimizeReturns(compiler));
        passes.addPass(new OptimizeParameters(compiler));

        passes.process(externs, root);
      }
    };
  }

  public void testRemovingReturnCallToFunctionWithUnusedParams() {
    test(
        "function foo() {var x; return x = bar(1)} foo(); function bar(x) {}",
        "function foo() {bar();return} foo(); function bar() {1;}");
    test(
        "function foo() {return} foo(); function bar() {1;}",
        "function foo(){return}foo()");
  }

  public void testNestingFunctionCallWithUnsedParams() {
    test(
        lines(
            "function f1(x) { }",
            "function f2(x) { }",
            "function f3(x) { }",
            "function f4(x) { }",
            "f3(f1(f2()));"),
        lines(
            "function f1(){f2()}",
            "function f2(){}",
            "function f3(){f1()}f3()"));
  }

  public void testUnusedAssignOnFunctionWithUnusedParams() {
    test("var foo = function(a){  }; function bar(){var x;x = foo} bar(); foo(1)",
         "var foo = function( ){1;}; function bar(){             } bar(); foo()");
  }

  public void testCallSiteInteraction() {
    testSame("var b=function(){return};b()");
    testSame(
        "var b=function(c){return c};b(1)",
        "var b=function(){var c$jscomp$1 = 1; c$jscomp$1; return}; b()");
    test(
        "var b=function(c){};b.call(null, x)",
        "var b=function(){};b.call(null)");
    test("var b=function(c){};b.apply(null, x)",
        "var b=function(){};b.apply(null, x)");

    test(
        "var b=function(c){return};b(1);b(2)",
        "var b=function(){return};b();b();");
    test(
        "var b=function(c){return};b(1,2);b();",
        "var b=function(){return};b();b();");
    test(
        "var b=function(c){return};b(1,2);b(3,4)",
        "var b=function(){return};b();b()");

    // Here there is a unknown reference to the function so we can't
    // change the signature.
    // TODO(johnlenz): replace unused parameter values, even
    // if we don't know all the uses.
    testSame(
        "var b=function(c,d){return d};b(1,2);b(3,4);b.length");

    test("var b=function(c){return};b(1,2);b(3,new x())",
        "var b=function(){return};b();b(new x())");

    test("var b=function(c){return};b(1,2);b(new x(),4)",
        "var b=function(){return};b();b(new x())");

    test(
        "var b=function(c,d){return d};b(1,2);use(b(new x(),4))",
        "var b=function(c,d){return d};b(0,2);use(b(new x(),4))");

    test(
        "var b=function(c,d,e){return d};b(1,2,3);use(b(new x(),4,new x()))",
        "var b=function(c,d){return d};b(0,2);use(b(new x(),4,new x()))");

    // Recursive calls are OK.
    test(
        "var b=function(c,d){b(1,2);return d};b(3,4);use(b(5,6))",
        "var b=function(d){b(2);return d};b(4);use(b(6))");

    testSame("var b=function(c){return arguments};b(1,2);use(b(3,4))");

    // remove all function arguments
    test(
        "var b=function(c,d){return};b();b(1);b(1,2);b(1,2,3)",
        "var b=function(){return};b();b();b();b();");

    // remove no function arguments
    testSame("var b=function(c,d){use(c+d)};b(2,3);b(1,2)");

    // remove some function arguments
    test(
        "var b=function(e,f,c,d){use(c+d)};b(1,2,3,4);b(3,4,5,6)",
        "var b=function(c,d){use(c+d)};b(3,4);b(5,6)");
    test(
        "var b=function(c,d,e,f){use(c+d)};b(1,2);b(3,4)",
        "var b=function(c,d){use(c+d)};b(1,2);b(3,4)");
    test(
        "var b=function(e,c,f,d,g){use(c+d)};b(1,2);b(3,4)",
        "var b=function(c){var f;var d;use(c+d)};b(2);b(4)");
    test(
        "var b=function(c,d){};var b=function(e,f){};b(1,2)",
        "var b=function(){};var b=function(){};b()");
  }

  public void testCallSiteInteraction_constructors0() {
    // Unused parmeters to constructors invoked with .call
    // can be removed.
    test(
        lines(
            "var Ctor1=function(a,b){return a};",
            "Ctor1.call(this,a,b);"),
        lines(
            "var Ctor1=function(a){a; return};",
            "Ctor1.call(this,a);"));
  }

  public void testCallSiteInteraction_constructors1() {
    // NOTE: Ctor1 used trailing parameter is removed by
    // RemoveUnusedVars, but the OptimizeParameters
    // leaves it alone because it is never invoked.
    //
    // Consider optimizing uninvoked constructors, this
    // will be less common with ES6 classes which
    // must call their constructors.

    // verify goog.inherits doesn't prevent optimizations
    test(
        lines(
            "var Ctor1=function(a,b){use(a)};",
            "var Ctor2=function(x,y){};",
            "goog$inherits(Ctor2, Ctor1);",
            "new Ctor2(1,2);new Ctor2(3,4);"),
        lines(
            "var Ctor1=function(a){use(a)};",
            "var Ctor2=function(){};",
            "goog$inherits(Ctor2, Ctor1);",
            "new Ctor2();new Ctor2();"));
  }

  public void testCallSiteInteraction_constructors2() {
    // This test requires multiple iterations of the passes.
    // "remove unused vars" must be run the after the call to
    // Ctor1 has been modified so that the parameters to Ctor2
    // to become unused.

    String code = lines(
        "var Ctor1=function(a,b){return a};",
        "var Ctor2=function(x,y){Ctor1.call(this,x,y)};",
        "goog$inherits(Ctor2, Ctor1);",
        "new Ctor2(1,2);new Ctor2(3,4)");

    String expectedfirstPassResult = lines(
        "var Ctor1=function(a){return a};",
        "var Ctor2=function(x,y){Ctor1.call(this,x)};",
        "goog$inherits(Ctor2, Ctor1);",
        "new Ctor2(1,2);new Ctor2(3,4)");

    String expectedSecondPassResult = lines(
        "var Ctor1=function(a){return a};",
        "var Ctor2=function(x){Ctor1.call(this,x)};",
        "goog$inherits(Ctor2, Ctor1);",
        "new Ctor2(1);new Ctor2(3)");

    test(code, expectedfirstPassResult);
    test(expectedfirstPassResult, expectedSecondPassResult);
  }

  public void testFunctionArgRemovalCausingInconsistency() {
    // Test the case where an unused argument is removed and the argument
    // contains a call site in its subtree (will cause the call site's parent
    // pointer to be null).
    test(
        lines(
            "var a=function(x,y){};",
            "var b=function(z){};",
            "a(new b, b)"),
        lines(
            "var a=function(){new b;b};",
            "var b=function(){};",
            "a()"));
  }

  public void testRemoveUnusedVarsPossibleNpeCase() {
    test(
        lines(
            "var a = [];",
            "var register = function(callback) {a[0] = callback};",
            "register(function(transformer) {});",
            "register(function(transformer) {});"),
        lines(
            "var register=function(){};register();register()"));
  }

  public void testDoNotOptimizeJSCompiler_renameProperty() {
    // Only the function definition can be modified, none of the call sites.
    test(
        lines(
            "function JSCompiler_renameProperty(a) {};",
            "JSCompiler_renameProperty('a');"),
        lines(
            "function JSCompiler_renameProperty() {};",
            "JSCompiler_renameProperty('a');"));
  }

  public void testDoNotOptimizeJSCompiler_ObjectPropertyString() {
    test(
        lines(
            "function JSCompiler_ObjectPropertyString(a, b) {};",
            "JSCompiler_ObjectPropertyString(window,'b');"),
        lines("function JSCompiler_ObjectPropertyString() {};",
            "JSCompiler_ObjectPropertyString(window,'b');"));
  }

  public void testFunctionArgRemovalFromCallSites() {
    // remove all function arguments
    test(
        "var b=function(c,d){return};b(1,2);b(3,4)",
        "var b=function(){return};b();b()");

    // remove no function arguments
    testSame("var b=function(c,d){return c+d};b(1,2);use(b(3,4))");
    test(
        "var b=function(e,f,c,d){return c+d};b(1,2,3,4);use(b(4,3,2,1))",
        "var b=function(c,d){return c+d};b(3,4);use(b(2,1))");

    // remove some function arguments
    test(
        "var b=function(c,d,e,f){use(c+d)};b(1,2);b();",
        "var b=function(c,d){use(c+d)};b(1,2);b();");
    test(
        "var b=function(e,c,f,d,g){use(c+d)};b(1,2);b(3,4,5,6)",
        "var b=function(c,d){use(c+d)};b(2);b(4,6)");
  }

  public void testLocalVarReferencesGlobalVar() {
    test(
        "var a=3;function f(b, c){b=a; alert(c);} f(1,2);f()",
        "function f(c) { alert(c); } f(2);f();");
  }

  public void testReflectedMethods() {
    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prototype.handle = function(x, y) { alert(y); };",
            "var x = goog.reflect.object(Foo, {handle: 1});",
            "for (var i in x) { x[i].call(x); }",
            "window['Foo'] = Foo;"));
  }
}
