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

import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {#link {@link OptimizeCalls} */
@RunWith(JUnit4.class)
public final class OptimizeCallsIntegrationTest extends CompilerTestCase {

  public OptimizeCallsIntegrationTest() {
    super(
        lines(
            DEFAULT_EXTERNS,
            "var window;",
            "var goog = {};",
            "goog.reflect = {};",
            "goog.reflect.object = function(a, b) {};",
            "function goog$inherits(a, b) {}",
            "var alert;",
            "function use(x) {}"));
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    enableGatherExternProperties();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {

      @Override
      public void process(Node externs, Node root) {
        new PureFunctionIdentifier.Driver(compiler).process(externs, root);
        new RemoveUnusedCode.Builder(compiler)
            .removeLocalVars(true)
            .removeGlobals(true)
            .build()
            .process(externs, root);

        OptimizeCalls.builder()
            .setCompiler(compiler)
            .setConsiderExterns(false)
            .addPass(new OptimizeReturns(compiler))
            .addPass(new OptimizeParameters(compiler))
            .build()
            .process(externs, root);
      }
    };
  }

  @Test
  public void testInlineWindow() {
    test(
        lines(
            "function foo(window) {", //
            "  alert(window);",
            "}",
            "foo(window);",
            ""),
        lines(
            "function foo(      ) {", //
            "  var window$jscomp$1 = window;",
            "  alert(window$jscomp$1);",
            "}",
            "foo(      );",
            ""));
  }

  @Test
  public void testRemoveUnusedConstructorArgumentWithDefaultValues() {
    test(
        externs(new TestExternsBuilder().addConsole().build()),
        srcs(
            lines(
                "class C {",
                "  constructor(unusedValue = () => {}, value = 3) {",
                "    this.value = value;",
                "  }",
                "  getValue() {",
                "    return this.value;",
                "  }",
                "}",
                "console.log(new C(() => {}, 25).getValue());")),
        expected(
            lines(
                "class C {",
                "  constructor(                                 ) {",
                "    var value = 25;",
                "    this.value = value;",
                "  }",
                "  getValue() {",
                "    return this.value;",
                "  }",
                "}",
                "console.log(new C(            ).getValue());")));

    test(
        externs(new TestExternsBuilder().addConsole().build()),
        srcs(
            lines(
                "class C {",
                "  constructor(unusedValue = () => {}, value = 3) {",
                "    this.value = value;",
                "  }",
                "  getValue() {",
                "    return this.value;",
                "  }",
                "}",
                // 2 calls to the constructor with different used values,
                // so the second parameter cannot actually be removed.
                "console.log(new C(() => {}, 15).getValue());",
                "console.log(new C(() => {}, 25).getValue());")),
        expected(
            lines(
                "class C {",
                "  constructor(                        value = 3) {",
                "    this.value = value;",
                "  }",
                "  getValue() {",
                "    return this.value;",
                "  }",
                "}",
                "console.log(new C(          15).getValue());",
                "console.log(new C(          25).getValue());")));
  }

  @Test
  public void testRemoveUnusedConstructorArgumentWithSubClassWithoutConstructor() {
    test(
        externs(new TestExternsBuilder().addConsole().build()),
        srcs(
            lines(
                "class C {",
                "  constructor(unusedValue = () => {}, value = 3) {",
                "    this.value = value;",
                "  }",
                "  getValue() {",
                "    return this.value;",
                "  }",
                "}",
                "class SubC extends C {}", // no declared constructor
                "console.log(new SubC(() => {}, 25).getValue());",
                "console.log(new C(() => {}, 25).getValue());")),
        expected(
            lines(
                "class C {",
                // default value removed, but no arguments removed or inlined
                // due to subclass.
                "  constructor(unusedValue           , value = 3) {",
                "    this.value = value;",
                "  }",
                "  getValue() {",
                "    return this.value;",
                "  }",
                "}",
                "class SubC extends C {}",
                "console.log(new SubC(() => {}, 25).getValue());",
                "console.log(new C(() => {}, 25).getValue());")));
  }

  @Test
  public void testRemoveUnusedConstructorArgumentWithSubClassWithConstructor() {
    test(
        externs(new TestExternsBuilder().addConsole().build()),
        srcs(
            lines(
                "class C {",
                "  constructor(unusedValue = () => {}, value = 3) {",
                "    this.value = value;",
                "  }",
                "  getValue() {",
                "    return this.value;",
                "  }",
                "}",
                "class SubC extends C {",
                "  constructor(value) {",
                "    super(0, value);", // calls super constructor
                "  }",
                "}",
                "console.log(new SubC(25).getValue());",
                "console.log(new C(() => {}, 25).getValue());")),
        expected(
            lines(
                "class C {",
                // First parameter removed because it was never used.
                "  constructor(                        value = 3) {",
                "    this.value = value;",
                "  }",
                "  getValue() {",
                "    return this.value;",
                "  }",
                "}",
                "class SubC extends C {",
                // Parameter was inlined with the only value ever passed to it.
                "  constructor(     ) {",
                "    var value$jscomp$1 = 25;",
                // first parameter to super() was removed to match its removal in the
                // definition above.
                "    super(   value$jscomp$1);",
                "  }",
                "}",
                "console.log(new SubC(  ).getValue());",
                // unused parameter removed.
                "console.log(new C(          25).getValue());")));
    // Same test as above, but with class expressions instead of declarations.
    test(
        externs(new TestExternsBuilder().addConsole().build()),
        srcs(
            lines(
                "const C = class {", // class expression instead of declaration
                "  constructor(unusedValue = () => {}, value = 3) {",
                "    this.value = value;",
                "  }",
                "  getValue() {",
                "    return this.value;",
                "  }",
                "}",
                "const SubC = class extends C {", // class expression instead of declaration
                "  constructor(value) {",
                "    super(0, value);", // calls super constructor
                "  }",
                "}",
                "console.log(new SubC(25).getValue());",
                "console.log(new C(() => {}, 25).getValue());")),
        expected(
            lines(
                "const C = class {",
                // First parameter removed because it was never used.
                "  constructor(                        value = 3) {",
                "    this.value = value;",
                "  }",
                "  getValue() {",
                "    return this.value;",
                "  }",
                "}",
                "const SubC = class extends C {",
                // Parameter was inlined with the only value ever passed to it.
                "  constructor(     ) {",
                "    var value$jscomp$1 = 25;",
                // first parameter to super() was removed to match its removal in the
                // definition above.
                "    super(   value$jscomp$1);",
                "  }",
                "}",
                "console.log(new SubC(  ).getValue());",
                // unused parameter removed.
                "console.log(new C(          25).getValue());")));
  }

  @Test
  public void testAliasOfAFunction() {
    testSame(
        lines(
            "", //
            "function foo(arg1) {",
            "  return arg1",
            "}",
            "",
            // first definition of bar
            "let bar = foo;",
            // really calls foo(1)
            // the `1` cannot be inlined because bar is an alias of
            // `foo`
            "bar(1);", // return value unused
            // redefinition of bar with a function literal
            "bar = function(arg1) {",
            "  return arg1 + 1;",
            "};",
            "bar(1)", // return value unused & same argument
            ""));
  }

  @Test
  public void testAliasingAssignment() {
    testSame(
        lines(
            "", //
            "/** @constructor */",
            "function MyClass() {",
            "  this.myField = null;",
            "}",
            "",
            // This assignment creates an alias, so we can't know all of the callers and cannot
            // safely optimize away `myArgument`.
            "MyClass.prototype[\"myMethod\"] =",
            "    MyClass.prototype.myMethod = function (myArgument) {",
            "  if (undefined === myArgument) {",
            "      myArgument = this.myField;",
            "  }",
            "  return \"myMethod with argument: \" + myArgument;",
            "};",
            "",
            "function globalMyMethod(oMyClass) {",
            // One call to `myMethod` exists, and it doesn't use the optional argument.
            "  return oMyClass.myMethod();",
            "}",
            "",
            // These both escape, so they won't be removed as unused.
            "window[\"MyClass\"] = MyClass;",
            "window[\"globalMyMethod\"] = globalMyMethod;",
            ""));
  }

  @Test
  public void testSimpleRemoval() {
    // unused parameter value
    test(
        "var foo = (p1)=>{}; foo(1); foo(2)", //
        "var foo = (  )=>{}; foo( ); foo( )");
    test(
        "let foo = (p1)=>{}; foo(1); foo(2)", //
        "let foo = (  )=>{}; foo( ); foo( )");
    test(
        "const foo = (p1)=>{}; foo(1); foo(2)", //
        "const foo = (  )=>{}; foo( ); foo( )");
  }

  @Test
  public void testRemovingReturnCallToFunctionWithUnusedParams() {
    test(
        "function foo() {var x; return x = bar(1)} foo(); function bar(x) {}",
        "function foo() {bar();return} foo(); function bar() {1;}");
    test(
        "function foo() {return} foo(); function bar() {1;}", //
        "function foo() {return} foo()");
  }

  @Test
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
            "function f2(){}", //
            "function f3(){f1()}f3()"));
  }

  @Test
  public void testUnusedAssignOnFunctionWithUnusedParams() {
    test(
        "var foo = function(a){  }; function bar(){var x;x = foo} bar(); foo(1)",
        "var foo = function( ){1;}; function bar(){             } bar(); foo()");
  }

  @Test
  public void testCallSiteInteraction() {
    testSame("var b=function(){return};b()");
    test(
        "var b=function(c){              return c}; b(1)",
        "var b=function( ){var c = 1; c; return  }; b( )");

    test(
        "var b=function(c){};b.call(null, 1); b(2);", // preserve alignment
        "var b=function( ){};b.call(null   ); b( );");
    test(
        "var b=function(c){};b.apply(null, []);", // preserve alignment
        "var b=function( ){};b.apply(null, []);");

    test(
        "var b=function(c){return};b(1);b(2)", //
        "var b=function(){return};b();b();");
    test(
        "var b=function(c){return};b(1,2);b();", //
        "var b=function(){return};b();b();");
    test(
        "var b=function(c){return};b(1,2);b(3,4)", //
        "var b=function(){return};b();b()");

    // Here there is a unknown reference to the function so we can't
    // change the signature.
    // TODO(johnlenz): replace unused parameter values, even
    // if we don't know all the uses.
    testSame("var b=function(c,d){return d};b(1,2);b(3,4);b.f()");

    test(
        "var b=function(c){return};b(1,2);b(3,new use())",
        "var b=function( ){return};b(   );b(  new use())");

    test(
        "var b=function(c){return};b(1,2);b(new use(),4)",
        "var b=function( ){return};b(   );b(new use()  )");

    test(
        "var b=function(c,d){return d};b(1,2);use(b(new use(),4))",
        "var b=function(c,d){return d};b(0,2);use(b(new use(),4))");

    test(
        "var b=function(c,d,e){return d};b(1,2,3);use(b(new use(),4,new use()))",
        "var b=function(c,d  ){return d};b(0,2  );use(b(new use(),4,new use()))");

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

  @Test
  public void nullishCoalesce() {
    testSame("var b = function(c) { use(c) } ?? function(c) { use(c) }; b(1)");
    testSame("var b; b = function(c) { use(c) } ?? function(c) { use(c) }; b(1)");
    testSame("var b = function(c) { use(c) } ?? function(c) { use(c) }; b(1); b(2);");
    testSame("var b; b = function(c) { use(c) } ?? function(c) { use(c) }; b(1); b(2);");
    test(
        "var f = (function(){ return 1; }) ?? (function(...p2){}); f()",
        "var f = (function(){ return  ; }) ?? (function(     ){}); f()");
  }

  @Test
  public void testComplexDefinition1() {
    testSame("var x; var b = x ? function(c) { use(c) } : function(c) { use(c) }; b(1)");
    test(
        "var x; var b = (x, function(c) {            use(c) }); b(1)",
        "var x; var b = (x, function( ) { var c = 1; use(c) }); b()");
    testSame("var x; var b; b = x ? function(c) { use(c) } : function(c) { use(c) }; b(1)");
    test(
        "var x; var b; b = (x, function(c) {            use(c) }); b(1)",
        "var x; var b; b = (x, function( ) { var c = 1; use(c) }); b( )");
  }

  @Test
  public void testComplexDefinition2() {
    testSame("var x; var b = x ? function(c) { use(c) } : function(c) { use(c) }; b(1); b(2);");
    testSame("var x;var b = (x, function(c) { use(c) }); b(1); b(2);");
    testSame("var x; var b; b = x ? function(c) { use(c) } : function(c) { use(c) }; b(1); b(2);");
    testSame("var x; var b; b = (x, function(c) { use(c) }); b(1); b(2);");
  }

  @Test
  public void testCallSiteInteraction_constructors0() {
    // Unused parmeters to constructors invoked with .call
    // can be removed.
    test(
        lines(
            "var Ctor1=function(a,b){return a};", // preserve newlines
            "Ctor1.call(this, 1, 2);",
            "Ctor1(3, 4)"),
        lines(
            "var Ctor1=function(a  ){a; return};", // preserve newlines
            "Ctor1.call(this,1);",
            "Ctor1(3)"));
  }

  @Test
  public void testCallSiteInteraction_constructors1() {
    // NOTE: Ctor1 used trailing parameter is removed by
    // RemoveUnusedCode

    // For now, goog.inherits prevents optimizations
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
            "new Ctor2(1,2);new Ctor2(3,4);"));
  }

  @Test
  public void testCallSiteInteraction_constructors2() {
    // For now, goog.inherits prevents call site optimizations
    String code =
        lines(
            "var Ctor1=function(a,b){return a};",
            "var Ctor2=function(x,y){Ctor1.call(this,x,y)};",
            "goog$inherits(Ctor2, Ctor1);",
            "new Ctor2(1,2);new Ctor2(3,4)");

    String expected =
        lines(
            "var Ctor1=function(a){return a};",
            "var Ctor2=function(x,y){Ctor1.call(this,x,y)};",
            "goog$inherits(Ctor2, Ctor1);",
            "new Ctor2(1,2);new Ctor2(3,4)");

    test(code, expected);
  }

  @Test
  public void testFunctionArgRemovalCausingInconsistency() {
    // Test the case where an unused argument is removed and the argument
    // contains a call site in its subtree (will cause the call site's parent
    // pointer to be null).
    test(
        lines(
            "var a=function(x,y){};", //
            "var b=function(z){};",
            "a(new b, b)"),
        lines(
            "var a=function(){new b;b};", //
            "var b=function(){};",
            "a()"));
  }

  @Test
  public void testRemoveUnusedVarsPossibleNpeCase() {
    test(
        lines(
            "var a = [];",
            "var register = function(callback) {a[0] = callback};",
            "register(function(transformer) {});",
            "register(function(transformer) {});"),
        lines("var register=function(){};register();register()"));
  }

  @Test
  public void testDoNotOptimizeJSCompiler_renameProperty() {
    // Only the function definition can be modified, none of the call sites.
    test(
        lines(
            "function JSCompiler_renameProperty(a) {};", //
            "JSCompiler_renameProperty('a');"),
        lines(
            "function JSCompiler_renameProperty() {};", //
            "JSCompiler_renameProperty('a');"));
  }

  @Test
  public void testFunctionArgRemovalFromCallSites() {
    // remove all function arguments
    test(
        "var b=function(c,d){return};b(1,2);b(3,4)", //
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

  @Test
  public void testFunctionArgRemovalFromCallSitesSpread1() {
    test(
        "function f(a,b,c,d){};f(...[1,2,3,4]);f(4,3,2,1)", //
        "function f(){};f();f()");
    test(
        "function f(a,b,c,d){};f(...[1,2,3,4], alert());f(4,3,2,1)",
        "function f(){};f(alert());f()");
    test(
        "function f(a,b,c,d){use(c+d)};f(...[1,2,3,4]);f(4,3,2,1)",
        "function f(a,b,c,d){use(c+d)};f(...[1,2,3,4]);f(0,0,2,1)");
    test(
        "function f(a,b,c,d){use(c+d)};f(1,...[2,3,4,5]);f(4,3,2,1)",
        "function f(  b,c,d){use(c+d)};f(  ...[2,3,4,5]);f(  0,2,1)");
    test(
        "function f(a,b,c,d){use(c+d)};f(1,2,...[3,4,5]);f(4,3,2,1)",
        "function f(    c,d){use(c+d)};f(    ...[3,4,5]);f(    2,1)");
    test(
        "function f(a,b,c,d){use(c+d)}; f(...[],2,3);f(4,3,2,1)",
        "function f(a,b,c,d){use(c+d)}; f(...[],2,3);f(0,0,2,1)");
  }

  @Test
  public void testFunctionArgRemovalFromCallSitesSpread2() {
    test(
        "function f(a,b,c,d){};f(...[alert()]);f(4,3,2,1)", //
        "function f(){};f(...[alert()]);f()");
    test(
        "function f(a,b,c,d){};f(...[alert()], alert());f(4,3,2,1)",
        "function f(){};f(...[alert()], alert());f()");
    test(
        "function f(a,b,c,d){use(c+d)};f(...[alert()]);f(4,3,2,1)",
        "function f(a,b,c,d){use(c+d)};f(...[alert()]);f(0,0,2,1)");
    test(
        "function f(a,b,c,d){use(c+d)};f(1,...[alert()]);f(4,3,2,1)",
        "function f(  b,c,d){use(c+d)};f(  ...[alert()]);f(  0,2,1)");
    test(
        "function f(a,b,c,d){use(c+d)};f(1,2,...[alert()]);f(4,3,2,1)",
        "function f(    c,d){use(c+d)};f(    ...[alert()]);f(    2,1)");
    test(
        "function f(a,b,c,d){use(c+d)}; f(...[alert()],2,3);f(4,3,2,1)",
        "function f(a,b,c,d){use(c+d)}; f(...[alert()],2,3);f(0,0,2,1)");
  }

  @Test
  public void testFunctionArgRemovalFromCallSitesSpread3() {
    test(
        "function f(a,b,c,d){};f(...alert());f(4,3,2,1)", //
        "function f(){};f(...alert());f()");
    test(
        "function f(a,b,c,d){};f(...alert(), 1);f(4,3,2,1)", //
        "function f(){};f(...alert());f()");
    test(
        "function f(a,b,c,d){use(c+d)};f(...alert());f(4,3,2,1)",
        "function f(a,b,c,d){use(c+d)};f(...alert());f(0,0,2,1)");
    test(
        "function f(a,b,c,d){use(c+d)};f(1,...alert());f(4,3,2,1)",
        "function f(  b,c,d){use(c+d)};f(  ...alert());f(  0,2,1)");
    test(
        "function f(a,b,c,d){use(c+d)};f(1,2,...alert());f(4,3,2,1)",
        "function f(    c,d){use(c+d)};f(    ...alert());f(    2,1)");
    test(
        "function f(a,b,c,d){use(c+d)}; f(...[alert()],2,3);f(4,3,2,1)",
        "function f(a,b,c,d){use(c+d)}; f(...[alert()],2,3);f(0,0,2,1)");
  }

  @Test
  public void testFunctionArgRemovalFromCallSitesRest() {
    // remove all function arguments
    test(
        "var b=function(c,...d){return};b(1,2,3);b(4,5,6)",
        "var b=function(      ){return};b(     );b(     )");

    // remove no function arguments
    testSame("var b=function(c,...d){return c+d};b(1,2,3);use(b(4,5,6))");

    // remove some function arguments
    test(
        "var b=function(e,f,...c){return c};b(1,2,3,4);use(b(4,3,2,1))",
        "var b=function(    ...c){return c};b(    3,4);use(b(    2,1))");
  }

  @Test
  public void testFunctionArgRemovalFromCallSitesDefaultValue() {
    // remove all function arguments
    test(
        "function f(c = 1, d = 2){};f(1,2,3);f(4,5,6)",
        "function f(            ){};f(     );f(     )");
    testSame("function f(c = alert()){};f(undefined);f(4)");
    test(
        "function f(c = alert()){};f();f()", //
        "function f(){var c = alert();};f();f()");
    // TODO(johnlenz): handle this like the "no value" case above and
    // allow the default value to inlined into the body.
    testSame("function f(c = alert()){};f(undefined);f(undefined)");
  }

  @Test
  public void testFunctionArgRemovalFromCallSitesDestructuring() {
    // remove all function arguments
    test(
        "function f([a] = [1], [b] = [2]){} f(1, 2, 3); f(4, 5, 6)",
        "function f(                    ){} f(       ); f(       )");
    test(
        "function f(a, [b] = alert(), [c] = alert(), d){} f(1, 2, 3, 4); f(4, 5, 6, 7)",
        "function f(   [ ] = alert(), [ ] = alert()   ){} f(   2, 3   ); f(   5, 6   )");

    test(
        "function f(a, [b = alert()] = [], [c = alert()] = [], d){} f(1, 2, 3, 4);f(4, 5, 6, 7)",
        "function f(   [b = alert()] = [], [c = alert()] = []   ){} f(   2, 3   );f(   5, 6   )");

    test(
        "function f(a, [b = alert()], [c = alert()], d){} f(1, 2, 3, 4); f(4, 5, 6, 7);",
        "function f(   [b = alert()], [c = alert()]   ){} f(   2, 3   ); f(   5, 6   );");
  }

  @Test
  public void testLocalVarReferencesGlobalVar() {
    test(
        "var a=3;function f(b, c){b=a; alert(c);} f(1,2);f()",
        "function f(c) { alert(c); } f(2);f();");
  }

  @Test
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

  @Test
  public void testExistenceOfAGetter_preventsParamOptimization() {
    testSame(
        lines(
            "class Bar {",
            // TODO(nickreid): Use `declareAccessor rather than specifying in snippet. We can't do
            // that currently because `RemoveUnusedCode` unilaterally runs another collection.
            "  get foo() {",
            "    return (x) => x;",
            "  }",
            "}",
            "",
            "class Foo {", //
            "  foo() {}",
            "}",
            "",
            "new (Foo || Bar)().foo('onlyUsedByGetter');"));
  }

  @Test
  public void testExistenceOfASetter_preventsParamOptimization() {
    testSame(
        lines(
            "class Bar {",
            // TODO(nickreid): Use `declareAccessor rather than specifying in snippet. We can't do
            // that currently because `RemoveUnusedCode` unilaterally runs another collection.
            "  set foo(f) {",
            "    this.bar = f;",
            "  }",
            "}",
            "",
            // This is a defnition for `.bar`. It would be dangerous to optimize it as if it were a
            // definition of `.foo`
            "var x = new Bar();",
            "x.foo = function(param) { return param; };",
            "x.foo();"));
  }
}
