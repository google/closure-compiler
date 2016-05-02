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

import com.google.javascript.rhino.Node;

/**
 * Tests for {@link NameAnalyzer}
 *
 */

public final class NameAnalyzerTest extends CompilerTestCase {

  private static String kExterns =
      "var window, top;" +
      "var document;" +
      "var Function;" +
      "var Array;" +
      "var externfoo; methods.externfoo;";

  public NameAnalyzerTest() {
    super(kExterns);
  }

  @Override
  protected void setUp() {
    super.enableNormalize();
  }

  @Override
  protected int getNumRepetitions() {
    // pass reaches steady state after 1 iteration.
    return 1;
  }

  public void testRemoveVarDeclaration1() {
    test("var foo = 3;", "");
  }

  public void testRemoveVarDeclaration2() {
    test("var foo = 3, bar = 4; externfoo = foo;",
         "var foo = 3; externfoo = foo;");
  }

  public void testRemoveVarDeclaration3() {
    test("var a = f(), b = 1, c = 2; b; c", "f();var b = 1, c = 2; b; c");
  }

  public void testRemoveVarDeclaration4() {
    test("var a = 0, b = f(), c = 2; a; c", "var a = 0;f();var c = 2; a; c");
  }

  public void testRemoveVarDeclaration5() {
    test("var a = 0, b = 1, c = f(); a; b", "var a = 0, b = 1; f(); a; b");
  }

  public void testRemoveVarDeclaration6() {
    test("var a = 0, b = a = 1; a", "var a = 0; a = 1; a");
  }

  public void testRemoveVarDeclaration7() {
    test("var a = 0, b = a = 1", "");
  }

  public void testRemoveVarDeclaration8() {
    test("var a;var b = 0, c = a = b = 1", "");
  }


  public void testRemoveDeclaration1() {
    test("var a;var b = 0, c = a = b = 1", "");
  }

  public void testRemoveDeclaration2() {
    test("var a,b,c; c = a = b = 1", "");
  }

  public void testRemoveDeclaration3() {
    test("var a,b,c; c = a = b = {}; a.x = 1;", "");
  }

  public void testRemoveDeclaration4() {
    testSame("var a,b,c; c = a = b = {}; a.x = 1;alert(c.x);");
  }

  public void testRemoveDeclaration5() {
    test("var a,b,c; c = a = b = null; use(b)", "var b;b=null;use(b)");
  }

  public void testRemoveDeclaration6() {
    test("var a,b,c; c = a = b = 'str';use(b)", "var b;b='str';use(b)");
  }

  public void testRemoveDeclaration7() {
    test("var a,b,c; c = a = b = true;use(b)", "var b;b=true;use(b)");
  }

  public void testRemoveFunction1() {
    test("var foo = function(){};", "");
  }

  public void testRemoveFunction2() {
    test("var foo; foo = function(){};", "");
  }

  public void testRemoveFunction3() {
    test("var foo = {}; foo.bar = function() {};", "");
  }

  public void testRemoveFunction4() {
    test("var a = {}; a.b = {}; a.b.c = function() {};", "");
  }

  public void testDontRemoveFunctionOnNamespaceThatEscapes() {
    testSame("var a = foo(); a.b = {}; a.b.c = function() {};");
  }

  public void testReferredToByWindow() {
    testSame("var foo = {}; foo.bar = function() {}; window['fooz'] = foo.bar");
  }

  public void testExtern() {
    testSame("externfoo = 5");
  }

  public void testRemoveNamedFunction() {
    test("function foo(){}", "");
  }

  public void testRemoveRecursiveFunction1() {
    test("function f(){f()}", "");
  }

  public void testRemoveRecursiveFunction2() {
    test("var f = function (){f()}", "");
  }

  public void testRemoveRecursiveFunction2a() {
    test("var f = function g(){g()}", "");
  }

  public void testRemoveRecursiveFunction3() {
    test("var f;f = function (){f()}", "");
  }

  public void testRemoveRecursiveFunction4() {
    // don't removed if name definition doesn't exist.
    testSame("f = function (){f()}");
  }

  public void testRemoveRecursiveFunction5() {
    test("function g(){f()}function f(){g()}", "");
  }

  public void testRemoveRecursiveFunction6() {
    test("var f=function(){g()};function g(){f()}", "");
  }

  public void testRemoveRecursiveFunction7() {
    test("var g = function(){f()};var f = function(){g()}", "");
  }

  public void testRemoveRecursiveFunction8() {
    test("var o = {};o.f = function(){o.f()}", "");
  }

  public void testRemoveRecursiveFunction9() {
    testSame("var o = {};o.f = function(){o.f()};o.f()");
  }

  public void testSideEffectClassification1() {
    test("foo();", "foo();");
  }

  public void testSideEffectClassification2() {
    test("var a = foo();", "foo();");
  }

  public void testSideEffectClassification3() {
    testSame("var a = foo();window['b']=a;");
  }

  public void testSideEffectClassification4() {
    testSame("function sef(){} sef();");
  }

  public void testSideEffectClassification5() {
    testSame("function nsef(){} var a = nsef();window['b']=a;");
  }

  public void testSideEffectClassification6() {
    test("function sef(){} sef();", "function sef(){} sef();");
  }

  public void testSideEffectClassification7() {
    testSame("function sef(){} var a = sef();window['b']=a;");
  }

  public void testNoSideEffectAnnotation1() {
    test("function f(){} var a = f();",
         "function f(){} f()");
  }

  public void testNoSideEffectAnnotation2() {
    test("/**@nosideeffects*/function f(){}", "var a = f();",
         "", null, null);
  }

  public void testNoSideEffectAnnotation3() {
    test("var f = function(){}; var a = f();",
         "var f = function(){}; f();");
  }

  public void testNoSideEffectAnnotation4() {
    test("var f = /**@nosideeffects*/function(){};", "var a = f();",
         "", null, null);
  }

  public void testNoSideEffectAnnotation5() {
    test("var f; f = function(){}; var a = f();",
         "var f; f = function(){}; f();");
  }

  public void testNoSideEffectAnnotation6() {
    test("f = /**@nosideeffects*/function(){};", "var a = f();",
         "", null, null);
  }

  public void testNoSideEffectAnnotation7() {
    test("f = /**@nosideeffects*/function(){};",
         "f = function(){};" +
         "var a = f();",
         "f = function(){}; f();", null, null);
  }

  public void testNoSideEffectAnnotation8() {
    test("f = function(){};" +
         "f = /**@nosideeffects*/function(){};",
         "var a = f();",
         "f();", null, null);
  }

  public void testNoSideEffectAnnotation9() {
    test("f = /**@nosideeffects*/function(){};" +
         "f = /**@nosideeffects*/function(){};",
         "var a = f();",
         "", null, null);

    test("f = /**@nosideeffects*/function(){};", "var a = f();",
         "", null, null);
  }

  public void testNoSideEffectAnnotation10() {
    test("var o = {}; o.f = function(){}; var a = o.f();",
         "var o = {}; o.f = function(){}; o.f();");
  }

  public void testNoSideEffectAnnotation11() {
    test("var o = {}; o.f = /**@nosideeffects*/function(){};",
         "var a = o.f();", "", null, null);
  }

  public void testNoSideEffectAnnotation12() {
    test("function c(){} var a = new c",
         "function c(){} new c");
  }

  public void testNoSideEffectAnnotation13() {
    test("/**@nosideeffects*/function c(){}", "var a = new c",
         "", null, null);
  }

  public void testNoSideEffectAnnotation14() {
    String common = "function c(){};" +
        "c.prototype.f = /**@nosideeffects*/function(){};";
    test(common, "var o = new c; var a = o.f()", "new c", null, null);
  }

  public void testNoSideEffectAnnotation15() {
    test("function c(){}; c.prototype.f = function(){}; var a = (new c).f()",
         "function c(){}; c.prototype.f = function(){}; (new c).f()");
  }

  public void testNoSideEffectAnnotation16() {
    test("/**@nosideeffects*/function c(){}" +
         "c.prototype.f = /**@nosideeffects*/function(){};",
         "var a = (new c).f()",
         "",
         null, null);
  }

  public void testFunctionPrototype() {
    testSame("var a = 5; Function.prototype.foo = function() {return a;}");
  }

  public void testTopLevelClass1() {
    test("var Point = function() {}; Point.prototype.foo = function() {}", "");
  }

  public void testTopLevelClass2() {
    testSame("var Point = {}; Point.prototype.foo = function() {};" +
             "externfoo = new Point()");
  }

  public void testTopLevelClass3() {
    test("function Point() {this.me_ = Point}", "");
  }

  public void testTopLevelClass4() {
    test("function f(){} function A(){} A.prototype = {x: function() {}}; f();",
         "function f(){} f();");
  }

  public void testTopLevelClass5() {
    testSame("function f(){} function A(){}" +
             "A.prototype = {x: function() { f(); }}; new A();");
  }

  public void testTopLevelClass6() {
    testSame("function f(){} function A(){}" +
             "A.prototype = {x: function() { f(); }}; new A().x();");
  }

  public void testTopLevelClass7() {
    test("A.prototype.foo = function(){}; function A() {}", "");
  }

  public void testNamespacedClass1() {
    test("var foo = {};foo.bar = {};foo.bar.prototype.baz = {}", "");
  }

  public void testNamespacedClass2() {
    testSame("var foo = {};foo.bar = {};foo.bar.prototype.baz = {};" +
             "window.z = new foo.bar()");
  }

  public void testNamespacedClass3() {
    test("var a = {}; a.b = function() {}; a.b.prototype = {x: function() {}};",
         "");
  }

  public void testNamespacedClass4() {
    testSame("function f(){} var a = {}; a.b = function() {};" +
             "a.b.prototype = {x: function() { f(); }}; new a.b();");
  }

  public void testNamespacedClass5() {
    testSame("function f(){} var a = {}; a.b = function() {};" +
             "a.b.prototype = {x: function() { f(); }}; new a.b().x();");
  }

  public void testAssignmentToThisPrototype() {
    testSame("Function.prototype.inherits = function(parentCtor) {" +
             "  function tempCtor() {};" +
             "  tempCtor.prototype = parentCtor.prototype;" +
             "  this.superClass_ = parentCtor.prototype;" +
             "  this.prototype = new tempCtor();" +
             "  this.prototype.constructor = this;" +
             "};");
  }

  public void testAssignmentToCallResultPrototype() {
    testSame("function f() { return function(){}; } f().prototype = {};");
  }

  public void testAssignmentToExternPrototype() {
    testSame("externfoo.prototype = {};");
  }

  public void testAssignmentToUnknownPrototype() {
    testSame(
        "/** @suppress {duplicate} */ var window;" +
        "window['a'].prototype = {};");
  }

  public void testBug2099540() {
    testSame(
        "/** @suppress {duplicate} */ var document;\n" +
        "/** @suppress {duplicate} */ var window;\n" +
        "var klass;\n" +
        "window[klass].prototype = " +
            "document.createElement(tagName)['__proto__'];");
  }

  public void testOtherGlobal() {
    testSame("goog.global.foo = bar(); function bar(){}");
  }

  public void testExternName1() {
    testSame("top.z = bar(); function bar(){}");
  }

  public void testExternName2() {
    testSame("top['z'] = bar(); function bar(){}");
  }

  public void testInherits1() {
    test("var a = {}; var b = {}; b.inherits(a)", "");
  }

  public void testInherits2() {
    test("var a = {}; var b = {}; var goog = {}; goog.inherits(b, a)", "");
  }

  public void testInherits3() {
    testSame("var a = {}; this.b = {}; b.inherits(a);");
  }

  public void testInherits4() {
    testSame("var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a);");
  }

  public void testInherits5() {
    test("this.a = {}; var b = {}; b.inherits(a);",
         "this.a = {}");
  }

  public void testInherits6() {
    test("this.a = {}; var b = {}; var goog = {}; goog.inherits(b, a);",
         "this.a = {}");
  }

  public void testInherits7() {
    testSame("var a = {}; this.b = {}; var goog = {};" +
        " goog.inherits = function() {}; goog.inherits(b, a);");
  }

  public void testInherits8() {
    // Make sure that exceptions aren't thrown if inherits() is used as
    // an R-value
    test("this.a = {}; var b = {}; var c = b.inherits(a);", "this.a = {};");
  }

  public void testMixin1() {
    testSame("var goog = {}; goog.mixin = function() {};" +
             "Function.prototype.mixin = function(base) {" +
             "  goog.mixin(this.prototype, base); " +
             "};");
  }

  public void testMixin2() {
    testSame("var a = {}; this.b = {}; var goog = {};" +
        " goog.mixin = function() {}; goog.mixin(b.prototype, a.prototype);");
  }

  public void testMixin3() {
    test("this.a = {}; var b = {}; var goog = {};" +
         " goog.mixin = function() {}; goog.mixin(b.prototype, a.prototype);",
         "this.a = {};");
  }

  public void testMixin4() {
    testSame("this.a = {}; var b = {}; var goog = {};" +
             "goog.mixin = function() {};" +
             "goog.mixin(b.prototype, a.prototype);" +
             "new b()");
  }

  public void testMixin5() {
    test("this.a = {}; var b = {}; var c = {}; var goog = {};" +
         "goog.mixin = function() {};" +
         "goog.mixin(b.prototype, a.prototype);" +
         "goog.mixin(c.prototype, a.prototype);" +
         "new b()",
         "this.a = {}; var b = {}; var goog = {};" +
         "goog.mixin = function() {};" +
         "goog.mixin(b.prototype, a.prototype);" +
         "new b()");
  }

  public void testMixin6() {
    testSame("this.a = {}; var b = {}; var c = {}; var goog = {};" +
             "goog.mixin = function() {};" +
             "goog.mixin(c.prototype, a.prototype) + " +
             "goog.mixin(b.prototype, a.prototype);" +
             "new b()");
  }

  public void testMixin7() {
    test("this.a = {}; var b = {}; var c = {}; var goog = {};" +
         "goog.mixin = function() {};" +
         "var d = goog.mixin(c.prototype, a.prototype) + " +
         "goog.mixin(b.prototype, a.prototype);" +
         "new b()",
         "this.a = {}; var b = {}; var goog = {};" +
         "goog.mixin = function() {};" +
         "goog.mixin(b.prototype, a.prototype);" +
         "new b()");
  }

  public void testConstants1() {
    testSame("var bar = function(){}; var EXP_FOO = true; if (EXP_FOO) bar();");
  }

  public void testConstants2() {
    test("var bar = function(){}; var EXP_FOO = true; var EXP_BAR = true;" +
         "if (EXP_FOO) bar();",
         "var bar = function(){}; var EXP_FOO = true; if (EXP_FOO) bar();");
  }

  public void testExpressions1() {
    test("var foo={}; foo.A='A'; foo.AB=foo.A+'B'; foo.ABC=foo.AB+'C'",
         "");
  }

  public void testExpressions2() {
    testSame("var foo={}; foo.A='A'; foo.AB=foo.A+'B'; this.ABC=foo.AB+'C'");
  }

  public void testExpressions3() {
    testSame("var foo = 2; window.bar(foo + 3)");
  }

  public void testSetCreatingReference() {
    testSame("var foo; var bar = function(){foo=6;}; bar();");
  }

  public void testAnonymous1() {
    testSame("function foo() {}; function bar() {}; foo(function() {bar()})");
  }

  public void testAnonymous2() {
    test("var foo;(function(){foo=6;})()", "(function(){})()");
  }

  public void testAnonymous3() {
    testSame("var foo; (function(){ if(!foo)foo=6; })()");
  }

  public void testAnonymous4() {
    testSame("var foo; (function(){ foo=6; })(); externfoo=foo;");
  }

  public void testAnonymous5() {
    testSame("var foo;" +
             "(function(){ foo=function(){ bar() }; function bar(){} })();" +
             "foo();");
  }

  public void testAnonymous6() {
    testSame("function foo(){}" +
             "function bar(){}" +
             "foo(function(){externfoo = bar});");
  }

  public void testAnonymous7() {
    testSame("var foo;" +
             "(function (){ function bar(){ externfoo = foo; } bar(); })();");
  }

  public void testAnonymous8() {
    testSame("var foo;" +
             "(function (){ var g=function(){ externfoo = foo; }; g(); })();");
  }

  public void testAnonymous9() {
    testSame("function foo(){}" +
             "function bar(){}" +
             "foo(function(){ function baz(){ externfoo = bar; } baz(); });");
  }

  public void testFunctions1() {
    testSame("var foo = null; function baz() {}" +
             "function bar() {foo=baz();} bar();");
  }

  public void testFunctions2() {
    testSame("var foo; foo = function() {var a = bar()};" +
             "var bar = function(){}; foo();");
  }

  public void testGetElem1() {
    testSame("var foo = {}; foo.bar = {}; foo.bar.baz = {a: 5, b: 10};" +
             "var fn = function() {window[foo.bar.baz.a] = 5;}; fn()");
  }

  public void testGetElem2() {
    testSame("var foo = {}; foo.bar = {}; foo.bar.baz = {a: 5, b: 10};" +
             "var fn = function() {this[foo.bar.baz.a] = 5;}; fn()");
  }

  public void testGetElem3() {
    testSame("var foo = {'i': 0, 'j': 1}; foo['k'] = 2; top.foo = foo;");
  }

  public void testIf1() {
    test("var foo = {};if(e)foo.bar=function(){};", "if(e);");
  }

  public void testIf2() {
    test("var e = false;var foo = {};if(e)foo.bar=function(){};",
         "var e = false;if(e);");
  }

  public void testIf3() {
    test("var e = false;var foo = {};if(e + 1)foo.bar=function(){};",
         "var e = false;if(e + 1);");
  }

  public void testIf4() {
    test("var e = false, f;var foo = {};if(f=e)foo.bar=function(){};",
         "var e = false;if(e);");
  }

  public void testIf4a() {
    // TODO(johnlenz): fix this.
    testSame("var e = [], f;if(f=e);f[0] = 1;");
  }

  public void testIf4b() {
    // TODO(johnlenz): fix this.
    test("var e = [], f;if(e=f);f[0] = 1;",
         "var f;if(f);f[0] = 1;");
  }

  public void testIf4c() {
    test("var e = [], f;if(f=e);e[0] = 1;",
         "var e = [];if(e);e[0] = 1;");
  }

  public void testIf5() {
    test("var e = false, f;var foo = {};if(f = e + 1)foo.bar=function(){};",
         "var e = false;if(e + 1);");
  }

  public void testIfElse() {
    test("var foo = {};if(e)foo.bar=function(){};else foo.bar=function(){};",
         "if(e);else;");
  }

  public void testWhile() {
    test("var foo = {};while(e)foo.bar=function(){};", "while(e);");
  }

  public void testFor() {
    test("var foo = {};for(e in x)foo.bar=function(){};", "for(e in x);");
  }

  public void testDo() {
    test("var cond = false;do {var a = 1} while (cond)",
         "var cond = false;do {} while (cond)");
  }

  public void testSetterInForStruct1() {
    test("var j = 0; for (var i = 1; i = 0; j++);",
         "var j = 0; for (; 0; j++);");
  }

  public void testSetterInForStruct2() {
    test("var Class = function() {}; " +
         "for (var i = 1; Class.prototype.property_ = 0; i++);",
         "for (var i = 1; 0; i++);");
  }

  public void testSetterInForStruct3() {
    test("var j = 0; for (var i = 1 + f() + g() + h(); i = 0; j++);",
         "var j = 0; f(); g(); h(); for (; 0; j++);");
  }

  public void testSetterInForStruct4() {
    test("var i = 0;var j = 0; for (i = 1 + f() + g() + h(); i = 0; j++);",
         "var j = 0; f(); g(); h(); for (; 0; j++);");
  }

  public void testSetterInForStruct5() {
    test("var i = 0, j = 0; for (i = f(), j = g(); 0;);",
         "for (f(), g(); 0;);");
  }

  public void testSetterInForStruct6() {
    test("var i = 0, j = 0, k = 0; for (i = f(), j = g(), k = h(); i = 0;);",
         "for (f(), g(), h(); 0;);");
  }

  public void testSetterInForStruct7() {
    test("var i = 0, j = 0, k = 0; for (i = 1, j = 2, k = 3; i = 0;);",
         "for (1, 2, 3; 0;);");
  }

  public void testSetterInForStruct8() {
    test("var i = 0, j = 0, k = 0; for (i = 1, j = i, k = 2; i = 0;);",
         "var i = 0; for(i = 1, i , 2; i = 0;);");
  }

  public void testSetterInForStruct9() {
    test("var Class = function() {}; " +
         "for (var i = 1; Class.property_ = 0; i++);",
         "for (var i = 1; 0; i++);");
  }

  public void testSetterInForStruct10() {
    test("var Class = function() {}; " +
         "for (var i = 1; Class.property_ = 0; i = 2);",
         "for (; 0;);");
  }

  public void testSetterInForStruct11() {
    test("var Class = function() {}; " +
         "for (;Class.property_ = 0;);",
         "for (;0;);");
  }

  public void testSetterInForStruct12() {
    test("var a = 1; var Class = function() {}; " +
         "for (;Class.property_ = a;);",
         "var a = 1; for (; a;);");
  }

  public void testSetterInForStruct13() {
    test("var a = 1; var Class = function() {}; " +
         "for (Class.property_ = a; 0 ;);",
         "for (; 0;);");
  }

  public void testSetterInForStruct14() {
    test("var a = 1; var Class = function() {}; " +
         "for (; 0; Class.property_ = a);",
         "for (; 0;);");
  }

  public void testSetterInForStruct15() {
    test("var Class = function() {}; " +
         "for (var i = 1; 0; Class.prototype.property_ = 0);",
         "for (; 0; 0);");
  }

  public void testSetterInForStruct16() {
    test("var Class = function() {}; " +
         "for (var i = 1; i = 0; Class.prototype.property_ = 0);",
         "for (; 0; 0);");
  }

  public void testSetterInForIn1() {
    test("var foo = {}; var bar; for(e in bar = foo.a);",
         "var foo = {}; for(e in foo.a);");
  }

  public void testSetterInForIn2() {
    testSame("var foo = {}; var bar; for(e in bar = foo.a); bar");
  }

  public void testSetterInForIn3() {
    testSame("var foo = {}; var bar; for(e in bar = foo.a); bar.b = 3");
  }

  public void testSetterInForIn4() {
    testSame("var foo = {}; var bar; for (e in bar = foo.a); bar.b = 3; foo.a");
  }

  public void testSetterInForIn5() {
    // TODO(user) Fix issue similar to b/2316773: bar should be preserved
    // but isn't due to missing references between e and foo.a
    test("var foo = {}; var bar; for (e in foo.a) { bar = e } bar.b = 3; foo.a",
         "var foo={};for(e in foo.a);foo.a");
  }

  public void testSetterInForIn6() {
    testSame("var foo = {};for(e in foo);");
  }

  public void testSetterInIfPredicate() {
    // TODO(user) Make NameAnalyzer smarter so it can remove "Class".
    testSame("var a = 1;" +
             "var Class = function() {}; " +
             "if (Class.property_ = a);");
  }

  public void testSetterInWhilePredicate() {
    test("var a = 1;" +
         "var Class = function() {}; " +
         "while (Class.property_ = a);",
         "var a = 1; for (;a;) {}");
  }

  public void testSetterInDoWhilePredicate() {
    // TODO(user) Make NameAnalyzer smarter so it can remove "Class".
    testSame("var a = 1;" +
             "var Class = function() {}; " +
             "do {} while(Class.property_ = a);");
  }

  public void testSetterInSwitchInput() {
    // TODO(user) Make NameAnalyzer smarter so it can remove "Class".
    testSame("var a = 1;" +
             "var Class = function() {}; " +
             "switch (Class.property_ = a) {" +
             "  default:" +
             "}");
  }

  public void testComplexAssigns() {
    // Complex assigns are not removed by the current pass.
    disableNormalize();
    testSame("var x = 0; x += 3; x *= 5;");
  }

  public void testNestedAssigns1() {
    test("var x = 0; var y = x = 3; window.alert(y);",
         "var y = 3; window.alert(y);");
  }

  public void testNestedAssigns2() {
    testSame("var x = 0; var y = x = {}; x.b = 3; window.alert(y);");
  }

  public void testComplexNestedAssigns1() {
    // TODO(nicksantos): Make NameAnalyzer smarter, so that we can eliminate y.
    disableNormalize();
    testSame("var x = 0; var y = 2; y += x = 3; window.alert(x);");
  }

  public void testComplexNestedAssigns2() {
    test("var x = 0; var y = 2; y += x = 3; window.alert(y);",
         "var y = 2; y += 3; window.alert(y);");
  }

  public void testComplexNestedAssigns3() {
    disableNormalize();
    test("var x = 0; var y = x += 3; window.alert(x);",
         "var x = 0; x += 3; window.alert(x);");
  }

  public void testComplexNestedAssigns4() {
    testSame("var x = 0; var y = x += 3; window.alert(y);");
  }

  public void testUnintendedUseOfInheritsInLocalScope1() {
    testSame("goog.mixin = function() {}; " +
             "(function() { var x = {}; var y = {}; goog.mixin(x, y); })();");
  }

  public void testUnintendedUseOfInheritsInLocalScope2() {
    testSame("goog.mixin = function() {}; " +
             "var x = {}; var y = {}; (function() { goog.mixin(x, y); })();");
  }

  public void testUnintendedUseOfInheritsInLocalScope3() {
    testSame("goog.mixin = function() {}; " +
             "var x = {}; var y = {}; (function() { goog.mixin(x, y); })(); " +
             "window.alert(x);");
  }

  public void testUnintendedUseOfInheritsInLocalScope4() {
    // Ensures that the "goog$mixin" variable doesn't get stripped out,
    // even when it's only used in a local scope.
    testSame("var goog$mixin = function() {}; " +
             "(function() { var x = {}; var y = {}; goog$mixin(x, y); })();");
  }

  public void testPrototypePropertySetInLocalScope1() {
    testSame("(function() { var x = function(){}; x.prototype.bar = 3; })();");
  }

  public void testPrototypePropertySetInLocalScope2() {
    testSame("var x = function(){}; (function() { x.prototype.bar = 3; })();");
  }

  public void testPrototypePropertySetInLocalScope3() {
    test("var x = function(){ x.prototype.bar = 3; };", "");
  }

  public void testPrototypePropertySetInLocalScope4() {
    test("var x = {}; x.foo = function(){ x.foo.prototype.bar = 3; };", "");
  }

  public void testPrototypePropertySetInLocalScope5() {
    test("var x = {}; x.prototype.foo = 3;", "");
  }

  public void testPrototypePropertySetInLocalScope6() {
    testSame("var x = {}; x.prototype.foo = 3; bar(x.prototype.foo)");
  }

  public void testPrototypePropertySetInLocalScope7() {
    testSame("var x = {}; x.foo = 3; bar(x.foo)");
  }

  public void testRValueReference1() {
    testSame("var a = 1; a");
  }

  public void testRValueReference2() {
    testSame("var a = 1; 1+a");
  }

  public void testRValueReference3() {
    testSame("var x = {}; x.prototype.foo = 3; var a = x.prototype.foo; 1+a");
  }

  public void testRValueReference4() {
    testSame("var x = {}; x.prototype.foo = 3; x.prototype.foo");
  }

  public void testRValueReference5() {
    testSame("var x = {}; x.prototype.foo = 3; 1+x.prototype.foo");
  }

  public void testRValueReference6() {
    testSame("var x = {}; var idx = 2; x[idx]");
  }

  public void testUnhandledTopNode() {
    testSame("function Foo() {}; Foo.prototype.isBar = function() {};" +
             "function Bar() {}; Bar.prototype.isFoo = function() {};" +
             "var foo = new Foo(); var bar = new Bar();" +
             // The boolean AND here is currently unhandled by this pass, but
             // it should not cause it to blow up.
             "var cond = foo.isBar() && bar.isFoo();" +
             "if (cond) {window.alert('hello');}");
  }

  public void testPropertyDefinedInGlobalScope() {
    testSame("function Foo() {}; var x = new Foo(); x.cssClass = 'bar';" +
             "window.alert(x);");
  }

  public void testConditionallyDefinedFunction1() {
    testSame("var g; externfoo.x || (externfoo.x = function() { g; })");
  }

  public void testConditionallyDefinedFunction2() {
    testSame("var g; 1 || (externfoo.x = function() { g; })");
  }

  public void testConditionallyDefinedFunction3() {
      testSame("var a = {};" +
           "rand() % 2 || (a.f = function() { externfoo = 1; } || alert());");
  }

  public void testGetElemOnThis() {
    testSame("var a = 3; this['foo'] = a;");
    testSame("this['foo'] = 3;");
  }

  public void testRemoveInstanceOfOnly() {
    test("function Foo() {}; Foo.prototype.isBar = function() {};" +
         "var x; if (x instanceof Foo) { window.alert(x); }",
         ";var x; if (false) { window.alert(x); }");
  }

  public void testRemoveLocalScopedInstanceOfOnly() {
    test("function Foo() {}; function Bar(x) { this.z = x instanceof Foo; };" +
        "externfoo.x = new Bar({});",
        ";function Bar(x) { this.z = false }; externfoo.x = new Bar({});");
  }

  public void testRemoveInstanceOfWithReferencedMethod() {
    test("function Foo() {}; Foo.prototype.isBar = function() {};" +
        "var x; if (x instanceof Foo) { window.alert(x.isBar()); }",
        ";var x; if (false) { window.alert(x.isBar()); }");
  }

  public void testDoNotChangeReferencedInstanceOf() {
    testSame("function Foo() {}; Foo.prototype.isBar = function() {};" +
             "var x = new Foo(); if (x instanceof Foo) { window.alert(x); }");
  }

  public void testDoNotChangeReferencedLocalScopedInstanceOf() {
    testSame("function Foo() {}; externfoo.x = new Foo();" +
        "function Bar() { if (x instanceof Foo) { window.alert(x); } };" +
        "externfoo.y = new Bar();");
  }

  public void testDoNotChangeLocalScopeReferencedInstanceOf() {
    testSame("function Foo() {}; Foo.prototype.isBar = function() {};" +
        "function Bar() { this.z = new Foo(); }; externfoo.x = new Bar();" +
        "if (x instanceof Foo) { window.alert(x); }");
  }

  public void testDoNotChangeLocalScopeReferencedLocalScopedInstanceOf() {
    testSame("function Foo() {}; Foo.prototype.isBar = function() {};" +
        "function Bar() { this.z = new Foo(); };" +
        "Bar.prototype.func = function(x) {" +
          "if (x instanceof Foo) { window.alert(x); }" +
        "}; new Bar().func();");
  }

  public void testDoNotChangeLocalScopeReferencedLocalScopedInstanceOf2() {
    test(
        "function Foo() {}" +
        "var createAxis = function(f) { return window.passThru(f); };" +
        "var axis = createAxis(function(test) {" +
        "  return test instanceof Foo;" +
        "});",
        "var createAxis = function(f) { return window.passThru(f); };" +
        "createAxis(function(test) {" +
        "  return false;" +
        "});");
  }

  public void testDoNotChangeInstanceOfGetElem() {
    testSame("var goog = {};" +
        "function f(obj, name) {" +
        "  if (obj instanceof goog[name]) {" +
        "    return name;" +
        "  }" +
        "}" +
        "window['f'] = f;");
  }

  public void testWeirdnessOnLeftSideOfPrototype() {
    // This checks a bug where 'x' was removed, but the function referencing
    // it was not, causing problems.
    testSame("var x = 3; " +
        "(function() { this.bar = 3; }).z = function() {" +
        "  return x;" +
        "};");
  }

  public void testDoNotChangeInstanceOfGetprop() {
    testSame(
        "function f(obj) {" +
        "  if (obj instanceof window.MouseEvent) obj.preventDefault();" +
        "}" +
        "window['f'] = f;");
  }

  public void testShortCircuit1() {
    test("var a = b() || 1", "b()");
  }

  public void testShortCircuit2() {
    test("var a = 1 || c()", "1 || c()");
  }

  public void testShortCircuit3() {
    test("var a = b() || c()", "b() || c()");
  }

  public void testShortCircuit4() {
    test("var a = b() || 3 || c()", "b() || 3 || c()");
  }

  public void testShortCircuit5() {
    test("var a = b() && 1", "b()");
  }

  public void testShortCircuit6() {
    test("var a = 1 && c()", "1 && c()");
  }

  public void testShortCircuit7() {
    test("var a = b() && c()", "b() && c()");
  }

  public void testShortCircuit8() {
    test("var a = b() && 3 && c()", "b() && 3 && c()");
  }

  public void testRhsReference1() {
    testSame("var a = 1; a");
  }

  public void testRhsReference2() {
    testSame("var a = 1; a || b()");
  }

  public void testRhsReference3() {
    testSame("var a = 1; 1 || a");
  }

  public void testRhsReference4() {
    test("var a = 1; var b = a || foo()", "var a = 1; a || foo()");
  }

  public void testRhsReference5() {
    test("var a = 1, b = 5; a; foo(b)", "var a = 1, b = 5; a; foo(b)");
  }

  public void testRhsAssign1() {
    test("var foo, bar; foo || (bar = 1)",
         "var foo; foo || 1");
  }

  public void testRhsAssign2() {
    test("var foo, bar, baz; foo || (baz = bar = 1)",
         "var foo; foo || 1");
  }

  public void testRhsAssign3() {
    testSame("var foo = null; foo || (foo = 1)");
  }

  public void testRhsAssign4() {
    test("var foo = null; foo = (foo || 1)", "");
  }

  public void testRhsAssign5() {
    test("var a = 3, foo, bar; foo || (bar = a)", "var a = 3, foo; foo || a");
  }

  public void testRhsAssign6() {
    test("function Foo(){} var foo = null;" +
         "var f = function () {foo || (foo = new Foo()); return foo}",
         "");
  }

  public void testRhsAssign7() {
    testSame("function Foo(){} var foo = null;" +
             "var f = function () {foo || (foo = new Foo())}; f()");
  }

  public void testRhsAssign8() {
    testSame("function Foo(){} var foo = null;" +
             "var f = function () {(foo = new Foo()) || g()}; f()");
  }

  public void testRhsAssign9() {
    test("function Foo(){} var foo = null;" +
         "var f = function () {1 + (foo = new Foo()); return foo}",
         "");
  }

  public void testAssignWithOr1() {
    testSame("var foo = null;" +
        "var f = window.a || function () {return foo}; f()");
  }

  public void testAssignWithOr2() {
    test("var foo = null;" +
        "var f = window.a || function () {return foo};",
        "var foo = null"); // why is this left?
  }

  public void testAssignWithAnd1() {
    testSame("var foo = null;" +
        "var f = window.a && function () {return foo}; f()");
  }

  public void testAssignWithAnd2() {
    test("var foo = null;" +
        "var f = window.a && function () {return foo};",
        "var foo = null;");  // why is this left?
  }

  public void testAssignWithHook1() {
    testSame("function Foo(){} var foo = null;" +
        "var f = window.a ? " +
        "    function () {return new Foo()} : function () {return foo}; f()");
  }

  public void testAssignWithHook2() {
    test("function Foo(){} var foo = null;" +
        "var f = window.a ? " +
        "    function () {return new Foo()} : function () {return foo};",
        "");
  }

  public void testAssignWithHook2a() {
    test("function Foo(){} var foo = null;" +
        "var f; f = window.a ? " +
        "    function () {return new Foo()} : function () {return foo};",
        "");
  }

  public void testAssignWithHook3() {
    testSame("function Foo(){} var foo = null; var f = {};" +
        "f.b = window.a ? " +
        "    function () {return new Foo()} : function () {return foo}; f.b()");
  }

  public void testAssignWithHook4() {
    test("function Foo(){} var foo = null; var f = {};" +
        "f.b = window.a ? " +
        "    function () {return new Foo()} : function () {return foo};",
        "");
  }

  public void testAssignWithHook5() {
    testSame("function Foo(){} var foo = null; var f = {};" +
        "f.b = window.a ? function () {return new Foo()} :" +
        "    window.b ? function () {return foo} :" +
        "    function() { return Foo }; f.b()");
  }

  public void testAssignWithHook6() {
    test("function Foo(){} var foo = null; var f = {};" +
        "f.b = window.a ? function () {return new Foo()} :" +
        "    window.b ? function () {return foo} :" +
        "    function() { return Foo };",
        "");
  }

  public void testAssignWithHook7() {
    testSame("function Foo(){} var foo = null;" +
        "var f = window.a ? new Foo() : foo;" +
        "f()");
  }

  public void testAssignWithHook8() {
    test("function Foo(){} var foo = null;" +
        "var f = window.a ? new Foo() : foo;",
        "function Foo(){}" +
        "window.a && new Foo()");
  }

  public void testAssignWithHook9() {
    test("function Foo(){} var foo = null; var f = {};" +
        "f.b = window.a ? new Foo() : foo;",
        "function Foo(){} window.a && new Foo()");
  }

  public void testAssign1() {
    test("function Foo(){} var foo = null; var f = {};" +
        "f.b = window.a;",
        "");
  }

  public void testAssign2() {
    test("function Foo(){} var foo = null; var f = {};" +
        "f.b = window;",
        "");
  }

  public void testAssign3() {
    test("var f = {};" +
        "f.b = window;",
        "");
  }

  public void testAssign4() {
    test("function Foo(){} var foo = null; var f = {};" +
        "f.b = new Foo();",
        "function Foo(){} new Foo()");
  }

  public void testAssign5() {
    test("function Foo(){} var foo = null; var f = {};" +
        "f.b = foo;",
        "");
  }

  public void testAssignWithCall() {
    test("var fun, x; (fun = function(){ x; })();",
        "var x; (function(){ x; })();");
  }

  // Currently this crashes the compiler because it erroneoursly removes var x
  // and later a sanity check fails.
  public void testAssignWithCall2() {
    test("var fun, x; (123, fun = function(){ x; })();",
        "(123, function(){ x; })();");
  }

  public void testNestedAssign1() {
    test("var a, b = a = 1, c = 2", "");
  }

  public void testNestedAssign2() {
    test("var a, b = a = 1; foo(b)",
         "var b = 1; foo(b)");
  }

  public void testNestedAssign3() {
    test("var a, b = a = 1; a = b = 2; foo(b)",
         "var b = 1; b = 2; foo(b)");
  }

  public void testNestedAssign4() {
    test("var a, b = a = 1; b = a = 2; foo(b)",
         "var b = 1; b = 2; foo(b)");
  }

  public void testNestedAssign5() {
    test("var a, b = a = 1; b = a = 2", "");
  }

  public void testNestedAssign15() {
    test("var a, b, c; c = b = a = 2", "");
  }

  public void testNestedAssign6() {
    testSame("var a, b, c; a = b = c = 1; foo(a, b, c)");
  }

  public void testNestedAssign7() {
    testSame("var a = 0; a = i[j] = 1; b(a, i[j])");
  }

  public void testNestedAssign8() {
    testSame("function f(){" +
             "this.lockedToken_ = this.lastToken_ = " +
             "SETPROP_value(this.hiddenInput_, a)}f()");
  }

  public void testRefChain1() {
    test("var a = 1; var b = a; var c = b; var d = c", "");
  }

  public void testRefChain2() {
    test("var a = 1; var b = a; var c = b; var d = c || f()",
         "var a = 1; var b = a; var c = b; c || f()");
  }

  public void testRefChain3() {
    test("var a = 1; var b = a; var c = b; var d = c + f()", "f()");
  }

  public void testRefChain4() {
    test("var a = 1; var b = a; var c = b; var d = f() || c",
         "f()");
  }

  public void testRefChain5() {
    test("var a = 1; var b = a; var c = b; var d = f() ? g() : c",
         "f() && g()");
  }

  public void testRefChain6() {
    test("var a = 1; var b = a; var c = b; var d = c ? f() : g()",
         "var a = 1; var b = a; var c = b; c ? f() : g()");
  }

  public void testRefChain7() {
    test("var a = 1; var b = a; var c = b; var d = (b + f()) ? g() : c",
         "var a = 1; var b = a; (b+f()) && g()");
  }

  public void testRefChain8() {
    test("var a = 1; var b = a; var c = b; var d = f()[b] ? g() : 0",
         "var a = 1; var b = a; f()[b] && g()");
  }

  public void testRefChain9() {
    test("var a = 1; var b = a; var c = 5; var d = f()[b+c] ? g() : 0",
         "var a = 1; var b = a; var c = 5; f()[b+c] && g()");
  }

  public void testRefChain10() {
    test("var a = 1; var b = a; var c = b; var d = f()[b] ? g() : 0",
         "var a = 1; var b = a; f()[b] && g()");
  }

  public void testRefChain11() {
    test("var a = 1; var b = a; var d = f()[b] ? g() : 0",
         "var a = 1; var b = a; f()[b] && g()");
  }

  public void testRefChain12() {
    testSame("var a = 1; var b = a; f()[b] ? g() : 0");
  }


  public void testRefChain13() {
    test("function f(){}var a = 1; var b = a; var d = f()[b] ? g() : 0",
         "function f(){}var a = 1; var b = a; f()[b] && g()");
  }

  public void testRefChain14() {
    testSame("function f(){}var a = 1; var b = a; f()[b] ? g() : 0");
  }

  public void testRefChain15() {
    test("function f(){}var a = 1, b = a; var c = f(); var d = c[b] ? g() : 0",
         "function f(){}var a = 1, b = a; var c = f(); c[b] && g()");
  }

  public void testRefChain16() {
    testSame("function f(){}var a = 1; var b = a; var c = f(); c[b] ? g() : 0");
  }

  public void testRefChain17() {
    test("function f(){}var a = 1; var b = a; var c = f(); var d = c[b]",
         "function f(){} f()");
  }

  public void testRefChain18() {
    testSame("var a = 1; f()[a] && g()");
  }


  public void testRefChain19() {
    test("var a = 1; var b = [a]; var c = b; b[f()] ? g() : 0",
         "var a=1; var b=[a]; b[f()] ? g() : 0");
  }

  public void testRefChain20() {
    test("var a = 1; var b = [a]; var c = b; var d = b[f()] ? g() : 0",
         "var a=1; var b=[a]; b[f()]&&g()");
  }

  public void testRefChain21() {
    testSame("var a = 1; var b = 2; var c = a + b; f(c)");
  }

  public void testRefChain22() {
    test("var a = 2; var b = a = 4; f(a)", "var a = 2; a = 4; f(a)");
  }

  public void testRefChain23() {
    test("var a = {}; var b = a[1] || f()", "var a = {}; a[1] || f()");
  }

  /**
   * Expressions that cannot be attributed to any enclosing dependency
   * scope should be treated as global references.
   * @bug 1739062
   */
  public void testAssignmentWithComplexLhs() {
    testSame("function f() { return this; }" +
             "var o = {'key': 'val'};" +
             "f().x_ = o['key'];");
  }

  public void testAssignmentWithComplexLhs2() {
    testSame("function f() { return this; }" +
             "var o = {'key': 'val'};" +
             "f().foo = function() {" +
             "  o" +
             "};");
  }

  public void testAssignmentWithComplexLhs3() {
    String source =
        "var o = {'key': 'val'};" +
        "function init_() {" +
        "  this.x = o['key']" +
        "}";

    test(source, "");
    testSame(source + ";init_()");
  }

  public void testAssignmentWithComplexLhs4() {
    testSame("function f() { return this; }" +
             "var o = {'key': 'val'};" +
             "f().foo = function() {" +
             "  this.x = o['key']" +
             "};");
  }

  /**
   * Do not "prototype" property of variables that are not being
   * tracked (because they are local).
   * @bug 1809442
   */
  public void testNoRemovePrototypeDefinitionsOutsideGlobalScope1() {
    testSame("function f(arg){}" +
             "" +
             "(function(){" +
             "  var O = {};" +
             "  O.prototype = 'foo';" +
             "  f(O);" +
             "})()");
  }

  public void testNoRemovePrototypeDefinitionsOutsideGlobalScope2() {
    testSame("function f(arg){}" +
             "(function h(){" +
             "  var L = {};" +
             "  L.prototype = 'foo';" +
             "  f(L);" +
             "})()");
  }

  public void testNoRemovePrototypeDefinitionsOutsideGlobalScope4() {
    testSame("function f(arg){}" +
             "function g(){" +
             "  var N = {};" +
             "  N.prototype = 'foo';" +
             "  f(N);" +
             "}" +
             "g()");
  }

  public void testNoRemovePrototypeDefinitionsOutsideGlobalScope5() {
    // function body not removed due to @bug 1898561
    testSame("function g(){ var R = {}; R.prototype = 'foo' } g()");
  }

  public void testRemovePrototypeDefinitionsInGlobalScope1() {
    testSame("function f(arg){}" +
             "var M = {};" +
             "M.prototype = 'foo';" +
             "f(M);");
  }

  public void testRemovePrototypeDefinitionsInGlobalScope2() {
    test("var Q = {}; Q.prototype = 'foo'", "");
  }

  public void testRemoveLabeledStatment() {
    test("LBL: var x = 1;", "LBL: {}");
  }

  public void testRemoveLabeledStatment2() {
    test("var x; LBL: x = f() + g()", "LBL: { f() ; g()}");
  }

  public void testRemoveLabeledStatment3() {
    test("var x; LBL: x = 1;", "LBL: {}");
  }

  public void testRemoveLabeledStatment4() {
    test("var a; LBL: a = f()", "LBL: f()");
  }

  public void testPreservePropertyMutationsToAlias1() {
    // Test for issue b/2316773 - property get case
    // Since a is referenced, property mutations via a's alias b must
    // be preserved.
    testSame("var a = {}; var b = a; b.x = 1; a");
  }

  public void testPreservePropertyMutationsToAlias2() {
    // Test for issue b/2316773 - property get case, don't keep 'c'
    test("var a = {}; var b = a; var c = a; b.x = 1; a",
         "var a = {}; var b = a; b.x = 1; a");
  }

  public void testPreservePropertyMutationsToAlias3() {
    // Test for issue b/2316773 - property get case, chain
    testSame("var a = {}; var b = a; var c = b; c.x = 1; a");
  }

 public void testPreservePropertyMutationsToAlias4() {
    // Test for issue b/2316773 - element get case
    testSame("var a = {}; var b = a; b['x'] = 1; a");
  }

  public void testPreservePropertyMutationsToAlias5() {
    // From issue b/2316773 description
    testSame("function testCall(o){}" +
             "var DATA = {'prop': 'foo','attr': {}};" +
             "var SUBDATA = DATA['attr'];" +
             "SUBDATA['subprop'] = 'bar';" +
             "testCall(DATA);");
  }

  public void testPreservePropertyMutationsToAlias6() {
    // Longer GETELEM chain
    testSame("function testCall(o){}" +
             "var DATA = {'prop': 'foo','attr': {}};" +
             "var SUBDATA = DATA['attr'];" +
             "var SUBSUBDATA = SUBDATA['subprop'];" +
             "SUBSUBDATA['subsubprop'] = 'bar';" +
             "testCall(DATA);");
  }

  public void testPreservePropertyMutationsToAlias7() {
    // Make sure that the base class does not depend on the derived class.
    test("var a = {}; var b = {}; b.x = 0;" +
         "var goog = {}; goog.inherits(b, a); a",
         "var a = {}; a");
  }

  public void testPreservePropertyMutationsToAlias8() {
    // Make sure that the derived classes don't end up depending on each other.
    test("var a = {};" +
         "var b = {}; b.x = 0;" +
         "var c = {}; c.y = 0;" +
         "var goog = {}; goog.inherits(b, a); goog.inherits(c, a); c",
         "var a = {}; var c = {}; c.y = 0;" +
         "var goog = {}; goog.inherits(c, a); c");
  }

  public void testPreservePropertyMutationsToAlias9() {
    testSame("var a = {b: {}};" +
         "var c = a.b; c.d = 3;" +
         "a.d = 3; a.d;");
  }

  public void testRemoveAlias() {
    test("var a = {b: {}};" +
         "var c = a.b;" +
         "a.d = 3; a.d;",
         "var a = {b: {}}; a.d = 3; a.d;");
  }

  public void testSingletonGetter1() {
    test("function Foo() {} goog.addSingletonGetter(Foo);", "");
  }

  public void testSingletonGetter2() {
    test("function Foo() {} goog$addSingletonGetter(Foo);", "");
  }

  public void testSingletonGetter3() {
    // addSingletonGetter adds a getInstance method to a class.
    testSame("function Foo() {} goog$addSingletonGetter(Foo);" +
        "this.x = Foo.getInstance();");
  }

  public void testObjectDefinePropertiesOnNamespaceThatEscapes() {
    testSame("var a = foo(); Object.defineProperties(a, {prop: {value: 5}});");
  }

  public void testObjectDefinePropertiesOnConstructorThatEscapes() {
    testSame("var Foo = fooFactory(); Object.defineProperties(Foo.prototype, {prop: {value: 5}});");
  }

  public void testRegularAssignPropOnNamespaceThatEscapes() {
    testSame("var a = foo(); a.prop = 5;");
  }

  public void testRegularAssignPropOnPropFromAVar() {
    testSame("var b = 5; var a = {}; a.prop = b; use(a.prop);");
  }

  public void testUnanalyzableObjectDefineProperties() {
    testSame("var a = {}; Object.defineProperties(a, props);");
    testSame("Object.defineProperties(foo(), {prop: {value: 5}});");
  }

  public void testObjectDefinePropertiesOnNamespace1() {
    testSame("var a = {}; Object.defineProperties(a, {prop: {value: 5}}); use(a.prop);");
    test("var a = {}; Object.defineProperties(a, {prop: {value: 5}});", "");
  }

  public void testObjectDefinePropertiesOnNamespace2() {
    // Note that since we try to remove the entire Object.defineProperties call whole hog,
    // we can't remove a single property from the object literal.
    testSame(
        "var a = {};"
        + "Object.defineProperties(a, {p1: {value: 5}, p2: {value: 3} });"
        + "use(a.p1);");

    test(
        "var a = {};"
        + "Object.defineProperties(a, {p1: {value: 5}, p2: {value: 3} });",
        "");
  }

  public void testNonAnalyzableObjectDefinePropertiesCall() {
    testSame(
        "var a = {};"
        + "var z = Object.defineProperties(a, {prop: {value: 5}});"
        + "use(z);");
  }

  public void testObjectDefinePropertiesOnNamespace3() {
    testSame(
        "var b = 5;"
        + "var a = {};"
        + "Object.defineProperties(a, {prop: {value: b}});"
        + "use(a.prop);");

    test(
        "var b = 5;"
        + "var a = {};"
        + "Object.defineProperties(a, {prop: {value: b}});"
        + "use(b);",
        "var b = 5; use(b);");
  }

  public void testObjectDefinePropertiesOnNamespace4() {
    test(
        "function b() { alert('hello'); };"
        + "var a = {};"
        + "Object.defineProperties(a, {prop: {value: b()}});",
        "function b() { alert('hello'); }; b()");
  }

  public void testObjectDefinePropertiesOnNamespace5() {
    test(
        "function b() { alert('hello'); };"
        + "function c() { alert('world'); };"
        + "var a = {};"
        + "Object.defineProperties(a, {p1: {value: b()}, p2: {value: c()}});",
        "function b() { alert('hello'); };"
        + "function c() { alert('world'); };"
        + "b(); c();");
  }

  public void testObjectDefinePropertiesOnConstructor() {
    testSame("function Foo() {} Object.defineProperties(Foo, {prop: {value: 5}}); use(Foo.prop);");
    test("function Foo() {} Object.defineProperties(Foo, {prop: {value: 5}});", "");
  }

  public void testObjectDefinePropertiesOnPrototype1() {
    testSame(
        "function Foo() {}"
        + "Object.defineProperties(Foo.prototype, {prop: {value: 5}});"
        + "use((new Foo).prop);");

    test("function Foo() {} Object.defineProperties(Foo.prototype, {prop: {value: 5}});", "");
  }

  public void testObjectDefinePropertiesOnPrototype2() {
    test("var b = 5;"
        + "function Foo() {}"
        + "Object.defineProperties(Foo.prototype, {prop: {value: b}});"
        + "use(b)", "var b = 5; use(b);");
  }

  public void testObjectDefinePropertiesOnPrototype3() {
    test("var b = function() {};"
        + "function Foo() {}"
        + "Object.defineProperties(Foo.prototype, {prop: {value: b()}});",
        "var b = function() {}; b();");
  }

  public void testObjectDefineGetters() {
    test("function Foo() {} Object.defineProperties(Foo, {prop: {get: function() {}}});", "");

    test(
        "function Foo() {} Object.defineProperties(Foo.prototype, {prop: {get: function() {}}});",
        "");
  }

  public void testObjectDefineSetters() {
    test("function Foo() {} Object.defineProperties(Foo, {prop: {set: function() {}}});", "");

    test(
        "function Foo() {} Object.defineProperties(Foo.prototype, {prop: {set: function() {}}});",
        "");
  }

  public void testNoRemoveWindowPropertyAlias1() {
     testSame(
         "var self_ = window.gbar;\n" +
         "self_.qs = function() {};");
  }

  public void testNoRemoveWindowPropertyAlias2() {
    testSame(
        "var self_ = window;\n" +
        "self_.qs = function() {};");
  }

  public void testNoRemoveWindowPropertyAlias3() {
    testSame(
        "var self_ = window;\n" +
        "self_['qs'] = function() {};");
  }

  public void testNoRemoveWindowPropertyAlias4() {
    // TODO(johnlenz): fix this. "self_" should remain.
    test(
        "var self_ = window['gbar'] || {};\n" +
        "self_.qs = function() {};",
        "");
 }

  public void testNoRemoveWindowPropertyAlias4a() {
    // TODO(johnlenz): fix this. "self_" should remain.
    test(
        "var self_; self_ = window.gbar || {};\n" +
        "self_.qs = function() {};",
        "");
 }

  public void testNoRemoveWindowPropertyAlias5() {
    // TODO(johnlenz): fix this. "self_" should remain.
    test(
        "var self_ = window || {};\n" +
        "self_['qs'] = function() {};",
        "");
  }

  public void testNoRemoveWindowPropertyAlias5a() {
    // TODO(johnlenz): fix this.
    test(
        "var self_; self_ = window || {};\n" +
        "self_['qs'] = function() {};",
        "");
  }

  public void testNoRemoveWindowPropertyAlias6() {
    testSame(
        "var self_ = (window.gbar = window.gbar || {});\n" +
        "self_.qs = function() {};");
  }

  public void testNoRemoveWindowPropertyAlias6a() {
    testSame(
        "var self_; self_ = (window.gbar = window.gbar || {});\n" +
        "self_.qs = function() {};");
  }

  public void testNoRemoveWindowPropertyAlias7() {
    testSame(
        "var self_ = (window = window || {});\n" +
        "self_['qs'] = function() {};");
  }

  public void testNoRemoveWindowPropertyAlias7a() {
    testSame(
        "var self_; self_ = (window = window || {});\n" +
        "self_['qs'] = function() {};");
  }

  public void testNoRemoveAlias0() {
    testSame(
        "var x = {}; function f() { return x; }; " +
        "f().style.display = 'block';" +
        "alert(x.style)");
  }

  public void testNoRemoveAlias1() {
    testSame(
        "var x = {}; function f() { return x; };" +
        "var map = f();\n" +
        "map.style.display = 'block';" +
        "alert(x.style)");
  }

  public void testNoRemoveAlias2() {
    testSame(
        "var x = {};" +
        "var map = (function () { return x; })();\n" +
        "map.style = 'block';" +
        "alert(x.style)");
  }

  public void testNoRemoveAlias3() {
    testSame(
        "var x = {}; function f() { return x; };" +
        "var map = {}\n" +
        "map[1] = f();\n" +
        "map[1].style.display = 'block';");
  }

  public void testNoRemoveAliasOfExternal0() {
    testSame(
        "document.getElementById('foo').style.display = 'block';");
  }

  public void testNoRemoveAliasOfExternal1() {
    testSame(
        "var map = document.getElementById('foo');\n" +
        "map.style.display = 'block';");
  }

  public void testNoRemoveAliasOfExternal2() {
    testSame(
        "var map = {}\n" +
        "map[1] = document.getElementById('foo');\n" +
        "map[1].style.display = 'block';");
  }

  public void testNoRemoveThrowReference1() {
    testSame(
      "var e = {}\n" +
      "throw e;");
  }

  public void testNoRemoveThrowReference2() {
    testSame(
      "function e() {}\n" +
      "throw new e();");
  }

  public void testClassDefinedInObjectLit1() {
    test(
      "var data = {Foo: function() {}};" +
      "data.Foo.prototype.toString = function() {};",
      "");
  }

  public void testClassDefinedInObjectLit2() {
    test(
      "var data = {}; data.bar = {Foo: function() {}};" +
      "data.bar.Foo.prototype.toString = function() {};",
      "");
  }

  public void testClassDefinedInObjectLit3() {
    test(
      "var data = {bar: {Foo: function() {}}};" +
      "data.bar.Foo.prototype.toString = function() {};",
      "");
  }

  public void testClassDefinedInObjectLit4() {
    test(
      "var data = {};" +
      "data.baz = {bar: {Foo: function() {}}};" +
      "data.baz.bar.Foo.prototype.toString = function() {};",
      "");
  }

  public void testVarReferencedInClassDefinedInObjectLit1() {
    testSame(
      "var ref = 3;" +
      "var data = {Foo: function() { this.x = ref; }};" +
      "window.Foo = data.Foo;");
  }

  public void testVarReferencedInClassDefinedInObjectLit2() {
    testSame(
      "var ref = 3;" +
      "var data = {Foo: function() { this.x = ref; }," +
      "            Bar: function() {}};" +
      "window.Bar = data.Bar;");
  }

  public void testArrayExt() {
    testSame(
      "Array.prototype.foo = function() { return 1 };" +
      "var y = [];" +
      "switch (y.foo()) {" +
      "}");
  }

  public void testArrayAliasExt() {
    testSame(
      "Array$X = Array;" +
      "Array$X.prototype.foo = function() { return 1 };" +
      "function Array$X() {}" +
      "var y = [];" +
      "switch (y.foo()) {" +
      "}");
  }

  public void testExternalAliasInstanceof1() {
    test(
      "Array$X = Array;" +
      "function Array$X() {}" +
      "var y = [];" +
      "if (y instanceof Array) {}",
      "var y = [];" +
      "if (y instanceof Array) {}"
      );
  }

  public void testExternalAliasInstanceof2() {
    testSame(
      "Array$X = Array;" +
      "function Array$X() {}" +
      "var y = [];" +
      "if (y instanceof Array$X) {}");
  }

  public void testExternalAliasInstanceof3() {
    testSame(
      "var b = Array;" +
      "var y = [];" +
      "if (y instanceof b) {}");
  }

  public void testAliasInstanceof4() {
    testSame(
      "function Foo() {};" +
      "var b = Foo;" +
      "var y = new Foo();" +
      "if (y instanceof b) {}");
  }

  public void testAliasInstanceof5() {
    // TODO(johnlenz): fix this. "b" should remain.
    test(
      "function Foo() {}" +
      "function Bar() {}" +
      "var b = x ? Foo : Bar;" +
      "var y = new Foo();" +
      "if (y instanceof b) {}",
      "function Foo() {}" +
      "var y = new Foo;" +
      "if (false){}");
  }

  // We cannot leave x.a.prototype there because it will
  // fail sanity var check.
  public void testBrokenNamespaceWithPrototypeAssignment() {
    test("var x = {}; x.a.prototype = 1", "");
  }

  public void testRemovePrototypeAliases() {
    test(
        "function g() {}" +
        "function F() {} F.prototype.bar = g;" +
        "window.g = g;",
        "function g() {}" +
        "window.g = g;");
  }

  public void testIssue284() {
    test(
        "var goog = {};" +
        "goog.inherits = function(x, y) {};" +
        "var ns = {};" +
        "/** @constructor */" +
        "ns.PageSelectionModel = function() {};" +
        "/** @constructor */" +
        "ns.PageSelectionModel.FooEvent = function() {};" +
        "/** @constructor */" +
        "ns.PageSelectionModel.SelectEvent = function() {};" +
        "goog.inherits(ns.PageSelectionModel.ChangeEvent," +
        "    ns.PageSelectionModel.FooEvent);",
        "");
  }

  public void testIssue838a() {
    testSame("var z = window['z'] || (window['z'] = {});\n" +
         "z['hello'] = 'Hello';\n" +
         "z['world'] = 'World';");
  }

  public void testIssue838b() {
    testSame(
         "var z;" +
         "window['z'] = z || (z = {});\n" +
         "z['hello'] = 'Hello';\n" +
         "z['world'] = 'World';");
  }


  public void testIssue874a() {
    testSame(
        "var a = a || {};\n" +
        "var b = a;\n" +
        "b.View = b.View || {}\n" +
        "var c = b.View;\n" +
        "c.Editor = function f(d, e) {\n" +
        "  return d + e\n" +
        "};\n" +
        "window.ImageEditor.View.Editor = a.View.Editor;");
  }

  public void testIssue874b() {
    testSame(
        "var b;\n" +
        "var c = b = {};\n" +
        "c.Editor = function f(d, e) {\n" +
        "  return d + e\n" +
        "};\n" +
        "window['Editor'] = b.Editor;");
  }

  public void testIssue874c() {
    testSame(
        "var b, c;\n" +
        "c = b = {};\n" +
        "c.Editor = function f(d, e) {\n" +
        "  return d + e\n" +
        "};\n" +
        "window['Editor'] = b.Editor;");
  }

  public void testIssue874d() {
    testSame(
        "var b = {}, c;\n" +
        "c = b;\n" +
        "c.Editor = function f(d, e) {\n" +
        "  return d + e\n" +
        "};\n" +
        "window['Editor'] = b.Editor;");
  }

  public void testIssue874e() {
    testSame(
        "var a;\n" +
        "var b = a || (a = {});\n" +
        "var c = b.View || (b.View = {});\n" +
        "c.Editor = function f(d, e) {\n" +
        "  return d + e\n" +
        "};\n" +
        "window.ImageEditor.View.Editor = a.View.Editor;");
  }

  public void testBug6575051() {
    testSame(
        "var hackhack = window['__o_o_o__'] = window['__o_o_o__'] || {};\n" +
        "window['__o_o_o__']['va'] = 1;\n" +
        "hackhack['Vb'] = 1;");
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new MarkNoSideEffectCallsAndNameAnalyzerRunner(compiler);
  }

  private class MarkNoSideEffectCallsAndNameAnalyzerRunner
      implements CompilerPass {
    MarkNoSideEffectCalls markNoSideEffectCalls;
    NameAnalyzer analyzer;
    MarkNoSideEffectCallsAndNameAnalyzerRunner(Compiler compiler) {
      this.markNoSideEffectCalls = new MarkNoSideEffectCalls(compiler);
      this.analyzer = new NameAnalyzer(compiler, true, null);
    }

    @Override
    public void process(Node externs, Node root) {
      markNoSideEffectCalls.process(externs, root);
      analyzer.process(externs, root);
    }
  }
}
