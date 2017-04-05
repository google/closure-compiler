/*
 * Copyright 2004 The Closure Compiler Authors.
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

public final class RemoveUnusedVarsTest extends CompilerTestCase {

  private boolean removeGlobal;
  private boolean preserveFunctionExpressionNames;
  private boolean modifyCallSites;

  public RemoveUnusedVarsTest() {
    super("function alert() {}");
    enableNormalize();
  }

  @Override
  public void setUp() {
    removeGlobal = true;
    preserveFunctionExpressionNames = false;
    modifyCallSites = false;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        if (modifyCallSites) {
          DefinitionUseSiteFinder defFinder = new DefinitionUseSiteFinder(compiler);
          defFinder.process(externs, root);
          compiler.setDefinitionFinder(defFinder);
        }
        new RemoveUnusedVars(
            compiler, removeGlobal, preserveFunctionExpressionNames,
            modifyCallSites).process(externs, root);
      }
    };
  }

  public void testRemoveUnusedVars() {
    setAcceptedLanguage(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    // Test lots of stuff
    test("var a;var b=3;var c=function(){};var x=A();var y; var z;" +
         "function A(){B()} function B(){C(b)} function C(){} " +
         "function X(){Y()} function Y(z){Z(x)} function Z(){y} " +
         "P=function(){A()}; " +
         "try{0}catch(e){a}",

         "var a;var b=3;A();function A(){B()}" +
         "function B(){C(b)}" +
         "function C(){}" +
         "P=function(){A()}" +
         ";try{0}catch(e){a}");

    // Test removal from if {} blocks
    test("var i=0;var j=0;if(i>0){var k=1;}",
         "var i=0;if(i>0);");

    // Test with for loop
    test("for (var i in booyah) {" +
         "  if (i > 0) x += ', ';" +
         "  var arg = 'foo';" +
         "  if (arg.length > 40) {" +
         "    var unused = 'bar';" +   // this variable is unused
         "    arg = arg.substr(0, 40) + '...';" +
         "  }" +
         "  x += arg;" +
         "}",

         "for(var i in booyah){if(i>0)x+=\", \";" +
         "var arg=\"foo\";if(arg.length>40)arg=arg.substr(0,40)+\"...\";" +
         "x+=arg}");

    // Test with function expressions in another function call
    test("function A(){}" +
         "if(0){function B(){}}win.setTimeout(function(){A()})",
         "function A(){}" +
         "if(0);win.setTimeout(function(){A()})");

    // Test with recursive functions
    test("function A(){A()}function B(){B()}B()",
         "function B(){B()}B()");

    // Test with multiple var declarations.
    test("var x,y=2,z=3;A(x);B(z);var a,b,c=4;C()",
         "var x,z=3;A(x);B(z);C()");

    // Test with for loop declarations
    test("for(var i=0,j=0;i<10;){}" +
         "for(var x=0,y=0;;y++){}" +
         "for(var a,b;;){a}" +
         "for(var c,d;;);" +
         "for(var item in items){}",

         "for(var i=0;i<10;);" +
         "for(var y=0;;y++);" +
         "for(var a;;)a;" +
         "for(;;);" +
         "for(var item in items);");

    // Test multiple passes required
    test("var a,b,c,d;var e=[b,c];var x=e[3];var f=[d];print(f[0])",
         "var d;var f=[d];print(f[0])");

    // Test proper scoping (static vs dynamic)
    test("var x;function A(){var x;B()}function B(){print(x)}A()",
         "var x;function A(){B()}function B(){print(x)}A()");

    // Test closures in a return statement
    testSame("function A(){var x;return function(){print(x)}}A()");

    // Test other closures, multiple passes
    test("function A(){}function B(){" +
         "var c,d,e,f,g,h;" +
         "function C(){print(c)}" +
         "var handler=function(){print(d)};" +
         "var handler2=function(){handler()};" +
         "e=function(){print(e)};" +
         "if(1){function G(){print(g)}}" +
         "arr=[function(){print(h)}];" +
         "return function(){print(f)}}B()",

         "function B(){" +
         "var f,h;" +
         "if(1);" +
         "arr=[function(){print(h)}];" +
         "return function(){print(f)}}B()");

    // Test exported names
    test("var a,b=1; function _A1() {this.foo(a)}",
         "var a;function _A1(){this.foo(a)}");

    // Test undefined (i.e. externally defined) names
    test("undefinedVar = 1", "undefinedVar=1");

    // Test unused vars with side effects
    test("var a,b=foo(),c=i++,d;var e=boo();var f;print(d);",
         "foo(); i++; var d; boo(); print(d)");

    test("var a,b=foo()", "foo()");
    test("var b=foo(),a", "foo()");
    test("var a,b=foo(a)", "var a; foo(a);");
  }

  public void testFunctionArgRemoval() {
    // remove all function arguments
    test("var b=function(c,d){return};b(1,2)",
         "var b=function(){return};b(1,2)");

    // remove no function arguments
    testSame("var b=function(c,d){return c+d};b(1,2)");
    testSame("var b=function(e,f,c,d){return c+d};b(1,2)");

    // remove some function arguments
    test("var b=function(c,d,e,f){return c+d};b(1,2)",
         "var b=function(c,d){return c+d};b(1,2)");
    test("var b=function(e,c,f,d,g){return c+d};b(1,2)",
         "var b=function(e,c,f,d){return c+d};b(1,2)");
  }

  public void testFunctionArgRemovalFromCallSites() {
    this.modifyCallSites = true;

    // remove all function arguments
    test("var b=function(c,d){return};b(1,2)",
         "var b=function(){return};b()");

    // remove no function arguments
    testSame("var b=function(c,d){return c+d};b(1,2)");
    test("var b=function(e,f,c,d){return c+d};b(1,2)",
         "var b=function(c,d){return c+d};b()");

    // remove some function arguments
    test("var b=function(c,d,e,f){return c+d};b(1,2)",
         "var b=function(c,d){return c+d};b(1,2)");
    test("var b=function(e,c,f,d,g){return c+d};b(1,2)",
         "var b=function(c,d){return c+d};b(2)");
  }

  public void testFunctionsDeadButEscaped() {
    testSame("function b(a) { a = 1; print(arguments[0]) }; b(6)");
    testSame("function b(a) { var c = 2; a = c; print(arguments[0]) }; b(6)");
  }

  public void testVarInControlStructure() {
    test("if (true) var b = 3;", "if(true);");
    test("if (true) var b = 3; else var c = 5;", "if(true);else;");
    test("while (true) var b = 3;", "while(true);");
    test("for (;;) var b = 3;", "for(;;);");
    test("do var b = 3; while(true)", "do;while(true)");
    test("with (true) var b = 3;", "with(true);");
    test("f: var b = 3;","f:{}");
  }

  public void testRValueHoisting() {
    test("var x = foo();", "foo()");
    test("var x = {a: foo()};", "({a:foo()})");

    test("var x=function y(){}", "");
  }

  public void testModule() {
    test(createModules(
             "var unreferenced=1; function x() { foo(); }" +
             "function uncalled() { var x; return 2; }",
             "var a,b; function foo() { this.foo(a); } x()"),
         new String[] {
           "function x(){foo()}",
           "var a;function foo(){this.foo(a)}x()"
         });
  }

  public void testRecursiveFunction1() {
    testSame("(function x(){return x()})()");
  }

  public void testRecursiveFunction2() {
    test("var x = 3; (function x() { return x(); })();",
         "(function x$jscomp$1(){return x$jscomp$1()})()");
  }

  public void testFunctionWithName1() {
    test("var x=function f(){};x()",
         "var x=function(){};x()");

    preserveFunctionExpressionNames = true;
    testSame("var x=function f(){};x()");
  }

  public void testFunctionWithName2() {
    test("foo(function bar(){})",
         "foo(function(){})");

    preserveFunctionExpressionNames = true;
    testSame("foo(function bar(){})");
  }

  public void testRemoveGlobal1() {
    removeGlobal = false;
    testSame("var x=1");
    test("var y=function(x){var z;}", "var y=function(x){}");
  }

  public void testRemoveGlobal2() {
    removeGlobal = false;
    testSame("var x=1");
    test("function y(x){var z;}", "function y(x){}");
  }

  public void testRemoveGlobal3() {
    removeGlobal = false;
    testSame("var x=1");
    test("function x(){function y(x){var z;}y()}",
         "function x(){function y(x){}y()}");
  }

  public void testRemoveGlobal4() {
    removeGlobal = false;
    testSame("var x=1");
    test("function x(){function y(x){var z;}}",
         "function x(){}");
  }

  public void testIssue168a() {
    test("function _a(){" +
         "  (function(x){ _b(); })(1);" +
         "}" +
         "function _b(){" +
         "  _a();" +
         "}",
         "function _a(){(function(){_b()})(1)}" +
         "function _b(){_a()}");
  }

  public void testIssue168b() {
    removeGlobal = false;
    test("function a(){" +
         "  (function(x){ b(); })(1);" +
         "}" +
         "function b(){" +
         "  a();" +
         "}",
         "function a(){(function(x){b()})(1)}" +
         "function b(){a()}");
  }

  public void testUnusedAssign1() {
    test("var x = 3; x = 5;", "");
  }

  public void testUnusedAssign2() {
    test("function f(a) { a = 3; } this.x = f;",
         "function f(){} this.x=f");
  }

  public void testUnusedAssign3() {
    // e can't be removed, so we don't try to remove the dead assign.
    // We might be able to improve on this case.
    test("try { throw ''; } catch (e) { e = 3; }",
        "try{throw\"\";}catch(e){e=3}");
  }

  public void testUnusedAssign4() {
    test("function f(a, b) { this.foo(b); a = 3; } this.x = f;",
        "function f(a,b){this.foo(b);}this.x=f");
  }

  public void testUnusedAssign5() {
    test("var z = function f() { f = 3; }; z();",
         "var z=function(){};z()");
  }

  public void testUnusedAssign5b() {
    test("var z = function f() { f = alert(); }; z();",
         "var z=function(){alert()};z()");
  }

  public void testUnusedAssign6() {
    test("var z; z = 3;", "");
  }

  public void testUnusedAssign6b() {
    test("var z; z = alert();", "alert()");
  }

  public void testUnusedAssign7() {
    // This loop is normalized to "var i;for(i in..."
    test("var a = 3; for (var i in {}) { i = a; }",
         // TODO(johnlenz): "i = a" should be removed here.
         "var a = 3; var i; for (i in {}) {i = a;}");
  }

  public void testUnusedAssign8() {
    // This loop is normalized to "var i;for(i in..."
    test("var a = 3; for (var i in {}) { i = a; } alert(a);",
         // TODO(johnlenz): "i = a" should be removed here.
         "var a = 3; var i; for (i in {}) {i = a} alert(a);");
  }

  public void testUnusedAssign9() {
    test("function b(a) { a = 1; arguments=1; }; b(6)",
         "function b() { arguments=1; }; b(6)");
  }

  public void testUnusedPropAssign1() {
    test("var x = {}; x.foo = 3;", "");
  }

  public void testUnusedPropAssign1b() {
    test("var x = {}; x.foo = alert();", "alert()");
  }

  public void testUnusedPropAssign2() {
    test("var x = {}; x['foo'] = 3;", "");
  }

  public void testUnusedPropAssign2b() {
    test("var x = {}; x[alert()] = alert();", "alert(),alert()");
  }

  public void testUnusedPropAssign3() {
    test("var x = {}; x['foo'] = {}; x['bar'] = 3", "");
  }

  public void testUnusedPropAssign3b() {
    test("var x = {}; x[alert()] = alert(); x[alert() + alert()] = alert()",
         "alert(),alert();(alert() + alert()),alert()");
  }

  public void testUnusedPropAssign4() {
    test("var x = {foo: 3}; x['foo'] = 5;", "");
  }

  public void testUnusedPropAssign5() {
    test("var x = {foo: bar()}; x['foo'] = 5;",
         "var x={foo:bar()};x[\"foo\"]=5");
  }

  public void testUnusedPropAssign6() {
    test("var x = function() {}; x.prototype.bar = function() {};", "");
  }

  public void testUnusedPropAssign7() {
    test("var x = {}; x[x.foo] = x.bar;", "");
  }

  public void testUnusedPropAssign7b() {
    testSame("var x = {}; x[x.foo] = alert(x.bar);");
  }

  public void testUnusedPropAssign7c() {
    test("var x = {}; x[alert(x.foo)] = x.bar;",
         "var x={};x[alert(x.foo)]=x.bar");
  }

  public void testUsedPropAssign1() {
    test("function f(x) { x.bar = 3; } f({});",
         "function f(x){x.bar=3}f({})");
  }

  public void testUsedPropAssign2() {
    test("try { throw z; } catch (e) { e.bar = 3; }",
         "try{throw z;}catch(e){e.bar=3}");
  }

  public void testUsedPropAssign3() {
    // This pass does not do flow analysis.
    test("var x = {}; x.foo = 3; x = bar();",
         "var x={};x.foo=3;x=bar()");
  }

  public void testUsedPropAssign4() {
    test("var y = foo(); var x = {}; x.foo = 3; y[x.foo] = 5;",
         "var y=foo();var x={};x.foo=3;y[x.foo]=5");
  }

  public void testUsedPropAssign5() {
    test("var y = foo(); var x = 3; y[x] = 5;",
         "var y=foo();var x=3;y[x]=5");
  }

  public void testUsedPropAssign6() {
    test("var x = newNodeInDom(doc); x.innerHTML = 'new text';",
         "var x=newNodeInDom(doc);x.innerHTML=\"new text\"");
  }

  public void testUsedPropAssign7() {
    testSame("var x = {}; for (x in alert()) { x.foo = 3; }");
  }

  public void testUsedPropAssign8() {
    testSame("for (var x in alert()) { x.foo = 3; }");
  }

  public void testUsedPropAssign9() {
    testSame(
        "var x = {}; x.foo = newNodeInDom(doc); x.foo.innerHTML = 'new test';");
  }

  public void testDependencies1() {
    test("var a = 3; var b = function() { alert(a); };", "");
  }

  public void testDependencies1b() {
    test("var a = 3; var b = alert(function() { alert(a); });",
         "var a=3;alert(function(){alert(a)})");
  }

  public void testDependencies1c() {
    test("var a = 3; var _b = function() { alert(a); };",
         "var a=3;var _b=function(){alert(a)}");
  }

  public void testDependencies2() {
    test("var a = 3; var b = 3; b = function() { alert(a); };", "");
  }

  public void testDependencies2b() {
    test("var a = 3; var b = 3; b = alert(function() { alert(a); });",
         "var a=3;alert(function(){alert(a)})");
  }

  public void testDependencies2c() {
    testSame("var a=3;var _b=3;_b=function(){alert(a)}");
  }

  public void testGlobalVarReferencesLocalVar() {
    testSame("var a=3;function f(){var b=4;a=b}alert(a + f())");
  }

  public void testLocalVarReferencesGlobalVar1() {
    testSame("var a=3;function f(b, c){b=a; alert(b + c);} f();");
  }

  public void testLocalVarReferencesGlobalVar2() {
    test("var a=3;function f(b, c){b=a; alert(c);} f();",
         "function f(b, c) { alert(c); } f();");
    this.modifyCallSites = true;
    test("var a=3;function f(b, c){b=a; alert(c);} f();",
         "function f(c) { alert(c); } f();");
  }

  public void testNestedAssign1() {
    test("var b = null; var a = (b = 3); alert(a);",
         "var a = 3; alert(a);");
  }

  public void testNestedAssign2() {
    test("var a = 1; var b = 2; var c = (b = a); alert(c);",
         "var a = 1; var c = a; alert(c);");
  }

  public void testNestedAssign3() {
    test("var b = 0; var z; z = z = b = 1; alert(b);",
         "var b = 0; b = 1; alert(b);");
  }

  public void testCallSiteInteraction() {
    this.modifyCallSites = true;

    testSame("var b=function(){return};b()");
    testSame("var b=function(c){return c};b(1)");
    test("var b=function(c){};b.call(null, x)",
         "var b=function(){};b.call(null)");
    test("var b=function(c){};b.apply(null, x)",
         "var b=function(){};b.apply(null, x)");

    test("var b=function(c){return};b(1)",
         "var b=function(){return};b()");
    test("var b=function(c){return};b(1,2)",
         "var b=function(){return};b()");
    test("var b=function(c){return};b(1,2);b(3,4)",
         "var b=function(){return};b();b()");

    // Here there is a unknown reference to the function so we can't
    // change the signature.
    test("var b=function(c,d){return d};b(1,2);b(3,4);b.length",
         "var b=function(c,d){return d};b(0,2);b(0,4);b.length");

    test("var b=function(c){return};b(1,2);b(3,new x())",
         "var b=function(){return};b();b(new x())");

    test("var b=function(c){return};b(1,2);b(new x(),4)",
         "var b=function(){return};b();b(new x())");

    test("var b=function(c,d){return d};b(1,2);b(new x(),4)",
         "var b=function(c,d){return d};b(0,2);b(new x(),4)");
    test("var b=function(c,d,e){return d};b(1,2,3);b(new x(),4,new x())",
         "var b=function(c,d){return d};b(0,2);b(new x(),4,new x())");

    // Recursive calls are OK.
    test("var b=function(c,d){b(1,2);return d};b(3,4);b(5,6)",
         "var b=function(d){b(2);return d};b(4);b(6)");

    testSame("var b=function(c){return arguments};b(1,2);b(3,4)");

    // remove all function arguments
    test("var b=function(c,d){return};b(1,2)",
         "var b=function(){return};b()");

    // remove no function arguments
    testSame("var b=function(c,d){return c+d};b(1,2)");

    // remove some function arguments
    test("var b=function(e,f,c,d){return c+d};b(1,2)",
         "var b=function(c,d){return c+d};b()");
    test("var b=function(c,d,e,f){return c+d};b(1,2)",
         "var b=function(c,d){return c+d};b(1,2)");
    test("var b=function(e,c,f,d,g){return c+d};b(1,2)",
         "var b=function(c,d){return c+d};b(2)");

    // multiple definitions of "b", the parameters can be removed but
    // the call sites are left unmodified for now.
    test("var b=function(c,d){};var b=function(e,f){};b(1,2)",
         "var b=function(){};var b=function(){};b(1,2)");
  }

  public void testCallSiteInteraction_constructors() {
    this.modifyCallSites = true;
    // The third level tests that the functions which have already been looked
    // at get re-visited if they are changed by a call site removal.
    test("var Ctor1=function(a,b){return a};" +
        "var Ctor2=function(a,b){Ctor1.call(this,a,b)};" +
        "goog$inherits(Ctor2, Ctor1);" +
        "new Ctor2(1,2)",
        "var Ctor1=function(a){return a};" +
        "var Ctor2=function(a){Ctor1.call(this,a)};" +
        "goog$inherits(Ctor2, Ctor1);" +
        "new Ctor2(1)");
  }

  public void testFunctionArgRemovalCausingInconsistency() {
    this.modifyCallSites = true;
    // Test the case where an unused argument is removed and the argument
    // contains a call site in its subtree (will cause the call site's parent
    // pointer to be null).
    test("var a=function(x,y){};" +
        "var b=function(z){};" +
        "a(new b, b)",
        "var a=function(){};" +
        "var b=function(){};" +
        "a(new b)");
  }

  public void testRemoveUnusedVarsPossibleNpeCase() {
    this.modifyCallSites = true;
    test("var a = [];" +
        "var register = function(callback) {a[0] = callback};" +
        "register(function(transformer) {});" +
        "register(function(transformer) {});",
        "var register=function(){};register();register()");
  }

  public void testDoNotOptimizeJSCompiler_renameProperty() {
    this.modifyCallSites = true;

    // Only the function definition can be modified, none of the call sites.
    test("function JSCompiler_renameProperty(a) {};" +
         "JSCompiler_renameProperty('a');",
         "function JSCompiler_renameProperty() {};" +
         "JSCompiler_renameProperty('a');");
  }

  public void testDoNotOptimizeJSCompiler_ObjectPropertyString() {
    this.modifyCallSites = true;
    test("function JSCompiler_ObjectPropertyString(a, b) {};" +
         "JSCompiler_ObjectPropertyString(window,'b');",
         "function JSCompiler_ObjectPropertyString() {};" +
         "JSCompiler_ObjectPropertyString(window,'b');");
  }

  public void testDoNotOptimizeSetters() {
    testSame("({set s(a) {}})");
  }

  public void testRemoveSingletonClass1() {
    test("function goog$addSingletonGetter(a){}" +
        "/**@constructor*/function a(){}" +
        "goog$addSingletonGetter(a);",
        "");
  }

  public void testRemoveInheritedClass1() {
    test(
        LINE_JOINER.join(
            "function goog$inherits() {}",
            "/**@constructor*/ function a() {}",
            "/**@constructor*/ function b() {}",
            "goog$inherits(b, a);",
            "new a;"),
        LINE_JOINER.join("/**@constructor*/ function a() {}", "new a;"));
  }

  public void testRemoveInheritedClass2() {
    test("function goog$inherits(){}" +
        "function goog$mixin(){}" +
        "/**@constructor*/function a(){}" +
        "/**@constructor*/function b(){}" +
        "/**@constructor*/function c(){}" +
        "goog$inherits(b,a);" +
        "goog$mixin(c.prototype,b.prototype);",
        "");
  }

  public void testRemoveInheritedClass3() {
    testSame("/**@constructor*/function a(){}" +
        "/**@constructor*/function b(){}" +
        "goog$inherits(b,a); new b");
  }

  public void testRemoveInheritedClass4() {
    testSame("function goog$inherits(){}" +
        "/**@constructor*/function a(){}" +
        "/**@constructor*/function b(){}" +
        "goog$inherits(b,a);" +
        "/**@constructor*/function c(){}" +
        "goog$inherits(c,b); new c");
  }

  public void testRemoveInheritedClass5() {
    test(
        LINE_JOINER.join(
            "function goog$inherits() {}",
            "/**@constructor*/ function a() {}",
            "/**@constructor*/ function b() {}",
            "goog$inherits(b,a);",
            "/**@constructor*/ function c() {}",
            "goog$inherits(c,b); new b"),
        LINE_JOINER.join(
            "function goog$inherits(){}",
            "/**@constructor*/ function a(){}",
            "/**@constructor*/ function b(){}",
            "goog$inherits(b,a); new b"));
  }

  public void testRemoveInheritedClass6() {
    test(
        LINE_JOINER.join(
            "function goog$mixin(){}",
            "/** @constructor*/ function a(){}",
            "/** @constructor*/ function b(){}",
            "/** @constructor*/ function c(){}",
            "/** @constructor*/ function d(){}",
            "goog$mixin(b.prototype,a.prototype);",
            "goog$mixin(c.prototype,a.prototype); new c;",
            "goog$mixin(d.prototype,a.prototype)"),
        LINE_JOINER.join(
            "function goog$mixin(){}",
            "/** @constructor*/ function a(){}",
            "/** @constructor*/ function c(){}",
            "goog$mixin(c.prototype,a.prototype); new c"));
  }

  public void testRemoveInheritedClass7() {
    test(
        LINE_JOINER.join(
            "function goog$mixin(){}",
            "/**@constructor*/function a(){alert(goog$mixin(a, a))}",
            "/**@constructor*/function b(){}",
            "goog$mixin(b.prototype,a.prototype); new a"),
        LINE_JOINER.join(
            "function goog$mixin(){}",
            "/**@constructor*/function a(){alert(goog$mixin(a, a))} new a"));
  }

  public void testRemoveInheritedClass8() {
    test("/**@constructor*/function a(){}" +
        "/**@constructor*/function b(){}" +
        "/**@constructor*/function c(){}" +
        "b.inherits(a);c.mixin(b.prototype)",
        "");
  }

  public void testRemoveInheritedClass9() {
    testSame("/**@constructor*/function a(){}" +
        "/**@constructor*/function b(){}" +
        "/**@constructor*/function c(){}" +
        "b.inherits(a);c.mixin(b.prototype);new c");
  }

  public void testRemoveInheritedClass10() {
    test(
        LINE_JOINER.join(
            "function goog$inherits() {}",
            "/**@constructor*/ function a() {}",
            "/**@constructor*/ function b() {}",
            "goog$inherits(b,a); new a;",
            "var c = a; var d = a.g; new b"),
        LINE_JOINER.join(
            "function goog$inherits(){}",
            "/**@constructor*/ function a(){}",
            "/**@constructor*/ function b(){}",
            "goog$inherits(b,a); ",
            "new a; new b"));
  }

  public void testRemoveInheritedClass11() {
    testSame("function goog$inherits(){}" +
        "function goog$mixin(a,b){goog$inherits(a,b)}" +
        "/**@constructor*/function a(){}" +
        "/**@constructor*/function b(){}" +
        "goog$mixin(b.prototype,a.prototype);new b");
  }

  public void testRemoveInheritedClass12() {
    testSame("function goog$inherits(){}" +
        "/**@constructor*/function a(){}" +
        "var b = {};" +
        "goog$inherits(b.foo, a)");
  }

  public void testReflectedMethods() {
    this.modifyCallSites = true;
    testSame(
        "/** @constructor */" +
        "function Foo() {}" +
        "Foo.prototype.handle = function(x, y) { alert(y); };" +
        "var x = goog.reflect.object(Foo, {handle: 1});" +
        "for (var i in x) { x[i].call(x); }" +
        "window['Foo'] = Foo;");
  }

  public void testIssue618_1() {
    this.removeGlobal = false;
    testSame(
        "function f() {\n" +
        "  var a = [], b;\n" +
        "  a.push(b = []);\n" +
        "  b[0] = 1;\n" +
        "  return a;\n" +
        "}");
  }

  public void testIssue618_2() {
    this.removeGlobal = false;
    testSame(
        "var b;\n" +
        "a.push(b = []);\n" +
        "b[0] = 1;\n");
  }
}
