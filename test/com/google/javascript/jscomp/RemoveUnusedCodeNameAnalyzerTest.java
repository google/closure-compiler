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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link RemoveUnusedCode} for functionality that was previously implemented in NameAnalyzer
 * aka smartNamePass, which has now been removed.
 */

@RunWith(JUnit4.class)
public final class RemoveUnusedCodeNameAnalyzerTest extends CompilerTestCase {

  private static final String EXTERNS =
      lines(
          "/**",
          " * @constructor",
          " * @param {*=} opt_value",
          " * @return {!Object}",
          " */",
          "function Object(opt_value) {}",
          "/** @type {Function} */",
          "Object.defineProperties = function() {};",
          "/**",
          " * @constructor",
          " * @param {string} message",
          " */",
          "function Error(message) {}",
          "var window, top, console;",
          "var document;",
          "var Function;",
          "var Array;",
          "var goog = {};",
          "goog.inherits = function(childClass, parentClass) {};",
          "goog.mixin = function(target, base) {};",
          "function goog$addSingletonGetter(a){}",
          "var externfoo = {};",
          "externfoo.externProp1 = 1;",
          "externfoo.externProp2 = 2;",
          "function doThing1() {}",
          "function doThing2() {}",
          "function use(something) {}",
          "function alert(something) {}");

  public RemoveUnusedCodeNameAnalyzerTest() {
    super(EXTERNS);
  }

  @Override
  protected int getNumRepetitions() {
    // pass reaches steady state after 1 iteration.
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new MarkNoSideEffectCallsAndRemoveUnusedCodeRunner(compiler);
  }

  private static class MarkNoSideEffectCallsAndRemoveUnusedCodeRunner implements CompilerPass {
    MarkNoSideEffectCalls markNoSideEffectCalls;
    RemoveUnusedCode removeUnusedCode;

    MarkNoSideEffectCallsAndRemoveUnusedCodeRunner(Compiler compiler) {
      this.markNoSideEffectCalls = new MarkNoSideEffectCalls(compiler);
      this.removeUnusedCode =
          new RemoveUnusedCode.Builder(compiler)
              .removeGlobals(true)
              .removeLocalVars(true)
              .removeUnusedPrototypeProperties(true)
              .removeUnusedThisProperties(true)
              .removeUnusedConstructorProperties(true)
              .removeUnusedObjectDefinePropertiesDefinitions(true)
              // Removal of function expression names isn't what these tests are about.
              // It just adds noise to the tests when we can't use testSame() because of it.
              .preserveFunctionExpressionNames(true)
              .build();
    }

    @Override
    public void process(Node externs, Node root) {
      markNoSideEffectCalls.process(externs, root);
      removeUnusedCode.process(externs, root);
    }
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    enableNormalize();
    enableGatherExternProperties();
  }

  @Test
  public void testDefaultingAssignmentWithAssignedProperty() {
    test("var x = function() {}; x.externProp1 = function() {}; var x = x || {};", "");
  }

  @Test
  public void testRemoveVarDeclaration1() {
    test("var foo = 3;", "");
  }

  @Test
  public void testRemoveVarDeclaration2() {
    test("var foo = 3, bar = 4; externfoo = foo;", "var foo = 3; externfoo = foo;");
  }

  @Test
  public void testRemoveVarDeclaration3() {
    // TODO(b/66971163): Don't block removal when the reference to a name is not actually used.
    // e.g. `b; c` below.
    test(
        "var a = doThing1(),     b = 1, c = 2; b; c", // preserve newline
        "        doThing1(); var b = 1, c = 2; b; c");
  }

  @Test
  public void testRemoveVarDeclaration4() {
    test(
        "var a = 0, b = doThing1(),     c = 2; a; c", // preserve newline
        "var a = 0;     doThing1(); var c = 2; a; c");
  }

  @Test
  public void testRemoveVarDeclaration5() {
    test(
        "var a = 0, b = 1, c = doThing1(); a; b", // preserve newline
        "var a = 0, b = 1;     doThing1(); a; b");
  }

  @Test
  public void testRemoveVarDeclaration6() {
    test("var a = 0, b = a = 1; a", "var a = 0; a = 1; a");
  }

  @Test
  public void testRemoveVarDeclaration7() {
    test("var a = 0, b = a = 1", "");
  }

  @Test
  public void testRemoveVarDeclaration8() {
    test("var a;var b = 0, c = a = b = 1", "");
  }

  @Test
  public void testRemoveLetDeclaration1() {
    test("let foo = 3;", "");
  }

  @Test
  public void testRemoveLetDeclaration2() {
    test("let foo = 3, bar = 4; externfoo = foo;", "let foo = 3; externfoo = foo;");
  }

  @Test
  public void testRemoveLetDeclaration3() {
    test(
        "let a = doThing1(),     b = 1, c = 2; b; c", // preserve newline
        "        doThing1(); let b = 1, c = 2; b; c");
  }

  @Test
  public void testRemoveLetDeclaration4() {
    test(
        "let a = 0, b = doThing1(),     c = 2; a; c", // preserve newline
        "let a = 0;     doThing1(); let c = 2; a; c");
  }

  @Test
  public void testRemoveLetDeclaration5() {
    test(
        "let a = 0, b = 1, c = doThing1(); a; b", // preserve newline
        "let a = 0, b = 1;     doThing1(); a; b");
  }

  @Test
  public void testRemoveLetDeclaration6() {
    test("let a = 0, b = a = 1; a", "let a = 0; a = 1; a");
  }

  @Test
  public void testRemoveLetDeclaration7() {
    test("let a = 0, b = a = 1", "");
  }

  @Test
  public void testRemoveLetDeclaration8() {
    test("let a;let b = 0, c = a = b = 1", "");
  }

  @Test
  public void testRemoveLetDeclaration9() {
    // The variable inside the block doesn't get removed (but does get renamed by Normalize).
    test("let x = 1; if (true) { let x = 2; x; }", "if (true) { let x$jscomp$1 = 2; x$jscomp$1; }");
  }

  @Test
  public void testRemoveLetInBlock1() {
    testSame(
        lines(
            "if (true) {", // preserve newline
            "  let x = 1; alert(x);",
            "}"));

    test(
        "if (true) { let x = 1; }", // preserve newline
        "if (true)              ;");
  }

  @Test
  public void testRemoveLetInBlock2() {
    testSame(
        lines(
            "if (true) {", // preserve newline
            "  let x = 1; alert(x);",
            "} else {",
            "  let x = 1; alert(x);",
            "}"));

    test(
        srcs(lines(
            "if (true) {", // preserve newline
            "  let x = 1;",
            "} else {",
            "  let x = 1;",
            "}")),
        expected("if (true); else;"));
  }

  @Test
  public void testRemoveConstDeclaration1() {
    test("const a = 4;", "");
  }

  @Test
  public void testRemoveConstDeclaration2() {
    testSame("const a = 4; window.x = a;");
  }

  @Test
  public void testRemoveConstDeclaration3() {
    // The variable inside the block doesn't get removed (but does get renamed by Normalize).
    test(
        "const x = 1; if (true) { const x = 2; x; }",
        "if (true) { const x$jscomp$1 = 2; x$jscomp$1; }");
  }

  @Test
  public void testRemoveDeclaration1() {
    test("var a;var b = 0, c = a = b = 1", "");
  }

  @Test
  public void testRemoveDeclaration2() {
    test("var a,b,c; c = a = b = 1", "");
  }

  // TODO(b/66971163): enable or remove
  public void disabledTestRemoveDeclaration3() {
    test("var a,b,c; c = a = b = {}; a.x = 1;", "");
  }

  @Test
  public void testRemoveDeclaration4() {
    test(
        "var a,b,c; c = a = b = {}; a.x = 1;alert(c.x);",
        "var a,  c; c = a     = {}; a.x = 1;alert(c.x);");
  }

  @Test
  public void testRemoveDeclaration5() {
    test("var a,b,c; c = a = b = null; use(b)", "var b;b=null;use(b)");
  }

  @Test
  public void testRemoveDeclaration6() {
    test("var a,b,c; c = a = b = 'str';use(b)", "var b;b='str';use(b)");
  }

  @Test
  public void testRemoveDeclaration7() {
    test("var a,b,c; c = a = b = true;use(b)", "var b;b=true;use(b)");
  }

  @Test
  public void testRemoveFunction1() {
    test("var foo = function(){};", "");
  }

  @Test
  public void testRemoveFunction2() {
    test("var foo; foo = function(){};", "");
  }

  @Test
  public void testRemoveFunction3() {
    test("var foo = {}; foo.bar = function() {};", "");
  }

  // TODO(b/66971163): enable or remove this test
  public void disabledTestRemoveFunction4() {
    test("var a = {}; a.b = {}; a.b.c = function() {};", "");
  }

  @Test
  public void testDontRemoveFunctionOnNamespaceThatEscapes() {
    testSame("var a = doThing1(); a.b = {}; a.b.c = function() {};");
  }

  @Test
  public void testReferredToByWindow() {
    testSame("var foo = {}; foo.bar = function() {}; window['fooz'] = foo.bar");
  }

  @Test
  public void testExtern() {
    testSame("externfoo = 5");
  }

  @Test
  public void testRemoveNamedFunction() {
    test("function foo(){}", "");
  }

  @Test
  public void testRemoveRecursiveFunction1() {
    test("function f(){f()}", "");
  }

  @Test
  public void testRemoveRecursiveFunction2() {
    test("var f = function (){f()}", "");
  }

  @Test
  public void testRemoveRecursiveFunction2a() {
    test("var f = function g(){g()}", "");
  }

  @Test
  public void testRemoveRecursiveFunction3() {
    test("var f;f = function (){f()}", "");
  }

  @Test
  public void testRemoveRecursiveFunction4() {
    // don't remove redefinition of an external variable
    testSame("doThing1 = function (){doThing1()}");
  }

  @Test
  public void testRemoveRecursiveFunction5() {
    test("function g(){f()}function f(){g()}", "");
  }

  @Test
  public void testRemoveRecursiveFunction6() {
    test("var f=function(){g()};function g(){f()}", "");
  }

  @Test
  public void testRemoveRecursiveFunction7() {
    test("var g = function(){f()};var f = function(){g()}", "");
  }

  @Test
  public void testRemoveRecursiveFunction8() {
    test("var o = {};o.f = function(){o.f()}", "");
  }

  @Test
  public void testRemoveRecursiveFunction9() {
    testSame("var o = {};o.f = function(){o.f()};o.f()");
  }

  @Test
  public void testSideEffectClassification1() {
    testSame("doThing1();");
  }

  @Test
  public void testSideEffectClassification2() {
    test("var a = doThing1();", "doThing1();");
  }

  @Test
  public void testSideEffectClassification3() {
    testSame("var a = doThing1();window['b']=a;");
  }

  @Test
  public void testSideEffectClassification4() {
    testSame("function sef(){} sef();");
  }

  @Test
  public void testSideEffectClassification5() {
    testSame("function nsef(){} var a = nsef();window['b']=a;");
  }

  @Test
  public void testSideEffectClassification6() {
    testSame("function sef(){} sef();");
  }

  @Test
  public void testSideEffectClassification7() {
    testSame("function sef(){} var a = sef();window['b']=a;");
  }

  @Test
  public void testNoSideEffectAnnotation1() {
    test("function f(){} var a = f();", "function f(){} f()");

    test("function f(){} let a = f();", "function f(){} f()");

    test("function f(){} const a = f();", "function f(){} f()");
  }

  @Test
  public void testNoSideEffectAnnotation2() {
    test(
        externs("/**@nosideeffects*/function f(){}"),
        srcs("var a = f();"),
        expected(""));
  }

  @Test
  public void testNoSideEffectAnnotation3() {
    test("var f = function(){}; var a = f();", "var f = function(){}; f();");
  }

  @Test
  public void testNoSideEffectAnnotation4() {
    test(
        externs("var f = /**@nosideeffects*/function(){};"),
        srcs("var a = f();"),
        expected(""));
  }

  @Test
  public void testNoSideEffectAnnotation5() {
    test("var f; f = function(){}; var a = f();", "var f; f = function(){}; f();");
  }

  @Test
  public void testNoSideEffectAnnotation6() {
    test(
        externs("f = /**@nosideeffects*/function(){};"),
        srcs("var a = f();"),
        expected(""));
  }

  @Test
  public void testNoSideEffectAnnotation7() {
    test(
        externs("var f = /**@nosideeffects*/function(){};"),
        srcs("f = function(){};var a = f();"),
        expected("f = function(){};        f();"));
  }

  @Test
  public void testNoSideEffectAnnotation8() {
    test(
        externs("var f = function(){}; f = /**@nosideeffects*/function(){};"), // preserve newline
        srcs("var a = f();"),
        expected("        f();"));
  }

  @Test
  public void testNoSideEffectAnnotation9() {
    test(
        externs(
            "f = /**@nosideeffects*/function(){};" + "f = /**@nosideeffects*/function(){};"),
        srcs("var a = f();"),
        expected(""));

    test(
        externs("f = /**@nosideeffects*/function(){};"),
        srcs("var a = f();"),
        expected(""));
  }

  @Test
  public void testNoSideEffectAnnotation10() {
    test(
        "var o = {}; o.f = function(){}; var a = o.f();", "var o = {}; o.f = function(){}; o.f();");
  }

  @Test
  public void testNoSideEffectAnnotation11() {
    test(
        externs("var o = {}; o.f = /**@nosideeffects*/function(){};"),
        srcs("var a = o.f();"),
        expected(""));
  }

  @Test
  public void testNoSideEffectAnnotation12() {
    test("function c(){} var a = new c", "function c(){} new c");
  }

  @Test
  public void testNoSideEffectAnnotation13() {
    test(
        externs("/**@nosideeffects*/function c(){}"),
        srcs("var a = new c"),
        expected(""));
  }

  @Test
  public void testNoSideEffectAnnotation14() {
    String externs = "function c(){};" + "c.prototype.f = /**@nosideeffects*/function(){};";
    test(
        externs(externs),
        srcs("var o = new c; var a = o.f()"),
        expected("new c"));
  }

  @Test
  public void testNoSideEffectAnnotation15() {
    test(
        "function c(){}; c.prototype.f = function(){}; var a = (new c).f()",
        "function c(){}; c.prototype.f = function(){}; (new c).f()");
  }

  @Test
  public void testNoSideEffectAnnotation16() {
    test(
        externs(
            "/**@nosideeffects*/function c(){}"
                + "c.prototype.f = /**@nosideeffects*/function(){};"),
        srcs("var a = (new c).f()"),
        expected(""));
  }

  @Test
  public void testFunctionPrototype() {
    test("var a = 5; Function.prototype.foo = function() {return a;}", "");
  }

  @Test
  public void testTopLevelClass1() {
    test("var Point = function() {}; Point.prototype.foo = function() {}", "");
  }

  @Test
  public void testTopLevelClass2() {
    test(
        "var Point = {}; Point.prototype.foo = function() {};externfoo = new Point()",
        "var Point = {};                                     externfoo = new Point()");
  }

  @Test
  public void testTopLevelClass3() {
    test("function Point() {this.me_ = Point}", "");
  }

  @Test
  public void testTopLevelClass4() {
    test(
        "function f(){} function A(){} A.prototype = {x: function() {}}; f();",
        "function f(){} f();");
  }

  @Test
  public void testTopLevelClass5() {
    test(
        "function f(){} function A(){} A.prototype = {x: function() { f(); }}; new A();",
        "               function A(){} A.prototype = {                      }; new A();");
  }

  @Test
  public void testTopLevelClass6() {
    testSame(
        "function f(){} function A(){}" + "A.prototype = {x: function() { f(); }}; new A().x();");
  }

  @Test
  public void testTopLevelClass7() {
    test("A.prototype.foo = function(){}; function A() {}", "");
  }

  @Test
  public void testNamespacedClass1() {
    test("var foo = {};foo.bar = {};foo.bar.prototype.baz = {}", "");
  }

  @Test
  public void testNamespacedClass2() {
    test(
        "var foo = {}; foo.bar = {}; foo.bar.prototype.baz = {}; window.z = new foo.bar()",
        "var foo = {}; foo.bar = {};                             window.z = new foo.bar()");
  }

  // TODO(b/66971163): enable or remove this
  public void disabledTestNamespacedClass3() {
    test("var a = {}; a.b = function() {}; a.b.prototype = {x: function() {}};", "");
  }

  @Test
  public void testNamespacedClass4() {
    test(
        lines(
            "function f(){}",
            "var a = {};",
            "a.b = function() {};",
            "a.b.prototype = {x: function() { f(); }};",
            "new a.b();"),
        lines(
            "              ",
            "var a = {};",
            "a.b = function() {};",
            "a.b.prototype = {                      };",
            "new a.b();"));
  }

  @Test
  public void testNamespacedClass5() {
    testSame(
        "function f(){} var a = {}; a.b = function() {};"
            + "a.b.prototype = {x: function() { f(); }}; new a.b().x();");
  }

  @Test
  public void testEs6Class() {
    test("class C {}", "");

    test("class C {constructor() {} }", "");

    testSame("class C {} var c = new C(); use(c);");

    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  add() {",
            "    this.x++",
            "  }",
            "}",
            "var c = new C;",
            "c.add();",
            "use(c.x);"));

    test("class C{} class D{} var d = new D;", "class D{} new D;");

    test("{class C{} }", "{}");
    testSame("{class C{} var c = new C; use(c)}");

    test(
        "function f() { return class C {} } doThing1();", // preserve newline
        "                                   doThing1();");
    testSame("export class C {}");

    // class expressions

    test("var c = class{}", "");
    testSame("var c = class{}; use(c);");

    test("var c = class C {}", "");
    test(
        "var c = class C {}; use(c);", // preserve newline
        "var c = class   {}; use(c);");
  }

  @Test
  public void testEs6ClassExtends() {
    testSame("class D {} class C extends D {} var c = new C; c.g();");
    test("class D {} class C extends D {}", "");
  }

  /** @bug 67430253 */
  @Test
  public void testEs6ClassExtendsQualifiedName1() {
    testSame("var ns = {}; ns.Class1 = class {}; class Class2 extends ns.Class1 {}; use(Class2);");
  }

  /** @bug 67430253 */
  @Test
  public void testEs6ClassExtendsQualifiedName2() {
    test(
        "var ns = {}; ns.Class1 = class {}; use(ns.Class1); class Class2 extends ns.Class1 {}",
        "var ns = {}; ns.Class1 = class {}; use(ns.Class1);");
  }

  @Test
  public void testAssignmentToThisPrototype() {
    testSame(
        lines(
            "Function.prototype.inherits = function(parentCtor) {",
            "  function tempCtor() {};",
            "  tempCtor.prototype = parentCtor.prototype;",
            "  this.superClass_ = parentCtor.prototype;",
            "  this.prototype = new tempCtor();",
            "  this.prototype.constructor = this;",
            "};",
            "/** @constructor */ function A() {}",
            "/** @constructor */ function B() {}",
            "B.inherits(A);",
            "use(B.superClass_);"));
  }

  @Test
  public void testAssignmentToCallResultPrototype() {
    testSame("function f() { return function(){}; } f().prototype = {};");
  }

  @Test
  public void testAssignmentToExternPrototype() {
    testSame("externfoo.prototype = {};");
  }

  @Test
  public void testAssignmentToUnknownPrototype() {
    testSame("/** @suppress {duplicate} */ var window;" + "window['a'].prototype = {};");
  }

  @Test
  public void testBug2099540() {
    testSame(
        lines(
            "/** @suppress {duplicate} */ var document;",
            "/** @suppress {duplicate} */ var window;",
            "var klass;",
            "window[klass].prototype = document.createElement('p')['__proto__'];"));
  }

  @Test
  public void testOtherGlobal() {
    testSame("goog.global.foo = bar(); function bar(){}");
  }

  @Test
  public void testExternName1() {
    testSame("top.z = bar(); function bar(){}");
  }

  @Test
  public void testExternName2() {
    testSame("top['z'] = bar(); function bar(){}");
  }

  @Test
  public void testInherits1() {
    test("var a = {}; var b = {}; goog.inherits(b, a)", "");
  }

  @Test
  public void testInherits2() {
    testSame("var a = {}; externfoo.inherits(a);");
  }

  @Test
  public void testInherits3() {
    testSame("var a = {}; goog.inherits(externfoo, a);");
  }

  @Test
  public void testInherits4() {
    test("var b = {}; goog.inherits(b, externfoo);", "");
  }

  @Test
  public void testInherits5() {
    testSame("var a = {}; goog.inherits(externfoo, a);");
  }

  @Test
  public void testMixin1() {
    testSame("Function.prototype.mixin = function(base) { goog.mixin(this.prototype, base); };");
  }

  @Test
  public void testMixin2() {
    testSame("var a = {}; goog.mixin(externfoo.prototype, a.prototype);");
  }

  @Test
  public void testMixin3() {
    test("var b = {}; goog.mixin(b.prototype, externfoo.prototype);", "");
  }

  @Test
  public void testMixin4() {
    testSame("var b = {}; goog.mixin(b.prototype, externfoo.prototype); new b()");
  }

  @Test
  public void testMixin5() {
    test(
        lines(
            "var b = {}; var c = {};",
            "goog.mixin(b.prototype, externfoo.prototype);",
            "goog.mixin(c.prototype, externfoo.prototype);",
            "new b()"),
        lines("var b = {};", "goog.mixin(b.prototype, externfoo.prototype);", "new b()"));
  }

  @Test
  public void testMixin6() {
    test(
        lines(
            "var b = {}; var c = {};",
            "goog.mixin(c.prototype, externfoo.prototype), ",
            "goog.mixin(b.prototype, externfoo.prototype);",
            "new b()"),
        lines(
            "var b = {};            ",
            "                                              ",
            "goog.mixin(b.prototype, externfoo.prototype);",
            "new b()"));
  }

  @Test
  public void testMixin7() {
    test(
        lines(
            "var b = {}; var c = {};",
            "var d = (goog.mixin(c.prototype, externfoo.prototype), ",
            "    goog.mixin(b.prototype, externfoo.prototype));",
            "new b()"),
        lines(
            "var b = {};", // preserve newline
            "goog.mixin(b.prototype, externfoo.prototype);",
            "new b()"));
  }

  @Test
  public void testConstants1() {
    testSame("var bar = function(){}; var EXP_FOO = true; if (EXP_FOO) bar();");
  }

  @Test
  public void testConstants2() {
    test(
        "var bar = function(){}; var EXP_FOO = true; var EXP_BAR = true;" + "if (EXP_FOO) bar();",
        "var bar = function(){}; var EXP_FOO = true; if (EXP_FOO) bar();");
  }

  @Test
  public void testExpressions1() {
    test("var foo={}; foo.A='A'; foo.AB=foo.A+'B'; foo.ABC=foo.AB+'C'", "");
  }

  @Test
  public void testExpressions2() {
    test("var foo={}; foo.A='A'; foo.AB=foo.A+'B'; this.ABC=foo.AB+'C'", "");
  }

  @Test
  public void testExpressions3() {
    testSame("var foo = 2; window.bar(foo + 3)");
  }

  @Test
  public void testSetCreatingReference() {
    test(
        "var foo; var bar = function(){foo=6;}; bar();",
        "         var bar = function(){      }; bar();");
  }

  @Test
  public void testAnonymous1() {
    testSame("function foo() {}; function bar() {}; foo(function() {bar()})");
  }

  @Test
  public void testAnonymous2() {
    test("var foo;(function(){foo=6;})()", "(function(){})()");
  }

  @Test
  public void testAnonymous3() {
    testSame("var foo; (function(){ if(!foo)foo=6; })()");
  }

  @Test
  public void testAnonymous4() {
    testSame("var foo; (function(){ foo=6; })(); externfoo=foo;");
  }

  @Test
  public void testAnonymous5() {
    testSame(
        "var foo;" + "(function(){ foo=function(){ bar() }; function bar(){} })();" + "foo();");
  }

  @Test
  public void testAnonymous6() {
    testSame("function foo(){}" + "function bar(){}" + "foo(function(){externfoo = bar});");
  }

  @Test
  public void testAnonymous7() {
    testSame("var foo;" + "(function (){ function bar(){ externfoo = foo; } bar(); })();");
  }

  @Test
  public void testAnonymous8() {
    testSame("var foo;" + "(function (){ var g=function(){ externfoo = foo; }; g(); })();");
  }

  @Test
  public void testAnonymous9() {
    testSame(
        "function foo(){}"
            + "function bar(){}"
            + "foo(function(){ function baz(){ externfoo = bar; } baz(); });");
  }

  @Test
  public void testFunctions1() {
    test(
        "var foo = null; function baz() {} function bar() {foo=baz();} bar();",
        "                function baz() {} function bar() {    baz();} bar();");
  }

  @Test
  public void testFunctions2() {
    test(
        "var foo; foo = function() {var a = bar()}; var bar = function(){}; foo();",
        "var foo; foo = function() {        bar()}; var bar = function(){}; foo();");
  }

  @Test
  public void testGetElem1() {
    testSame(
        "var foo = {}; foo.bar = {}; foo.bar.baz = {a: 5, b: 10};"
            + "var fn = function() {window[foo.bar.baz.a] = 5;}; fn()");
  }

  @Test
  public void testGetElem2() {
    testSame(
        "var foo = {}; foo.bar = {}; foo.bar.baz = {a: 5, b: 10};"
            + "var fn = function() {this[foo.bar.baz.a] = 5;}; fn()");
  }

  @Test
  public void testGetElem3() {
    testSame("var foo = {'i': 0, 'j': 1}; foo['k'] = 2; top.foo = foo;");
  }

  @Test
  public void testIf1() {
    test("var foo = {};if(externfoo)foo.bar=function(){};", "if(externfoo);");
  }

  @Test
  public void testIf2() {
    test("var e = false;var foo = {};if(e)foo.bar=function(){};", "var e = false;if(e);");
  }

  @Test
  public void testIf3() {
    test("var e = false;var foo = {};if(e + 1)foo.bar=function(){};", "var e = false;if(e + 1);");
  }

  @Test
  public void testIf4() {
    test("var e = false, f;var foo = {};if(f=e)foo.bar=function(){};", "var e = false;if(e);");
  }

  @Test
  public void testIf4a() {
    // TODO(johnlenz): fix this.
    testSame("var e = [], f;if(f=e);f[0] = 1;");
  }

  @Test
  public void testIf4b() {
    // TODO(johnlenz): fix this.
    test("var e = [], f;if(e=f);f[0] = 1;", "var f;if(f);f[0] = 1;");
  }

  @Test
  public void testIf4c() {
    test("var e = [], f;if(f=e);e[0] = 1;", "var e = [];if(e);e[0] = 1;");
  }

  @Test
  public void testIf5() {
    test(
        "var e = false, f;var foo = {};if(f = e + 1)foo.bar=function(){};",
        "var e = false;if(e + 1);");
  }

  @Test
  public void testIfElse() {
    test(
        "var foo = {}; if(externfoo) foo.bar = function(){}; else foo.bar = function(){};",
        "              if(externfoo)                       ; else                       ;");
  }

  @Test
  public void testWhile() {
    test(
        "var foo = {}; while(doThing1()) foo.bar=function(){};",
        "              while(doThing1())                     ;");
  }

  @Test
  public void testForIn() {
    test(
        "var foo = {};for(var e in externfoo)foo.bar=function(){};",
        "             for(var e in externfoo)                    ;");
  }

  @Test
  public void testForOf() {
    test(
        "var foo = {};for(var e of externfoo)foo.bar=function(){};",
        "             for(var e of externfoo)                    ;");
  }

  @Test
  public void testDo() {
    test("var cond = false;do {var a = 1} while (cond)", "var cond = false;do {} while (cond)");
  }

  @Test
  public void testSetterInForStruct1() {
    test("var j = 0; for (var i = 1; i = 0; j++);", "var j = 0; for (; 0; j++);");
  }

  @Test
  public void testSetterInForStruct2() {
    test(
        "var Class = function() {}; " + "for (var i = 1; Class.prototype.property_ = 0; i++);",
        "for (var i = 1; 0; i++);");
  }

  @Test
  public void testSetterInForStruct3() {
    test(
        externs("function f(){} function g() {} function h() {}"),
        srcs(
            "var j = 0;                      for (var i = 1 + f() + g() + h(); i = 0; j++);"),
        expected(
            "var j = 0; 1 + f() + g() + h(); for (                           ;     0; j++);"));
  }

  @Test
  public void testSetterInForStruct4() {
    test(
        externs("function f(){} function g() {} function h() {}"),
        srcs(
          "var i = 0; var j = 0;                      for (i = 1 + f() + g() + h(); i = 0; j++);"),
        expected(
          "           var j = 0; 1 + f() + g() + h(); for (                       ;     0; j++);"));
  }

  @Test
  public void testSetterInForStruct5() {
    test(
        externs("function f(){} function g() {} function h() {}"),
        srcs("var i = 0, j = 0; for (i = f(), j = g(); 0;);"),
        expected("                  for (    f(),     g(); 0;);"));
  }

  @Test
  public void testSetterInForStruct6() {
    test(
        externs("function f(){} function g() {} function h() {}"),
        srcs("var i = 0, j = 0, k = 0; for (i = f(), j = g(), k = h(); i = 0;);"),
        expected(
            "                         for (    f(),     g(),     h();     0;);"));
  }

  @Test
  public void testSetterInForStruct7() {
    test(
        externs("function f(){} function g() {} function h() {}"),
        srcs("var i = 0, j = 0, k = 0; for (i = 1, j = 2, k = 3; i = 0;);"),
        expected("                         for (                   ;     0;);"));
  }

  @Test
  public void testSetterInForStruct8() {
    test(
        externs("function f(){} function g() {} function h() {}"),
        srcs("var i = 0, j = 0, k = 0; for (i = 1, j = i, k = 2; i = 0;);"),
        expected("                         for (                   ;     0;);"));
  }

  @Test
  public void testSetterInForStruct9() {
    test(
        "var Class = function() {}; " + "for (var i = 1; Class.property_ = 0; i++);",
        "for (var i = 1; 0; i++);");
  }

  @Test
  public void testSetterInForStruct10() {
    test(
        "var Class = function() {}; for (var i = 1; Class.property_ = 0; i = 2);",
        "                           for (         ; 0                  ; 0    );");
  }

  @Test
  public void testSetterInForStruct11() {
    test("var Class = function() {}; " + "for (;Class.property_ = 0;);", "for (;0;);");
  }

  @Test
  public void testSetterInForStruct12() {
    test(
        "var a = 1; var Class = function() {}; " + "for (;Class.property_ = a;);",
        "var a = 1; for (; a;);");
  }

  @Test
  public void testSetterInForStruct13() {
    test(
        "var a = 1; var Class = function() {}; " + "for (Class.property_ = a; 0 ;);",
        "for (; 0;);");
  }

  @Test
  public void testSetterInForStruct14() {
    test(
        "var a = 1; var Class = function() {}; for (; 0; Class.property_ = a);",
        "                                      for (; 0; 0                  );");
  }

  @Test
  public void testSetterInForStruct15() {
    test(
        "var Class = function() {}; " + "for (var i = 1; 0; Class.prototype.property_ = 0);",
        "for (; 0; 0);");
  }

  @Test
  public void testSetterInForStruct16() {
    test(
        "var Class = function() {}; " + "for (var i = 1; i = 0; Class.prototype.property_ = 0);",
        "for (; 0; 0);");
  }

  @Test
  public void testSetterInForIn1() {
    test(
        "var foo = {}; var bar; for(var e in bar = foo.a);",
        "var foo = {};          for(var e in       foo.a);");
  }

  @Test
  public void testSetterInForIn2() {
    testSame("var foo = {}; var bar; for(var e in bar = foo.a); bar");
  }

  @Test
  public void testSetterInForIn3() {
    testSame("var foo = {}; var bar; for(var e in bar = foo.a); bar.b = 3");
  }

  @Test
  public void testSetterInForIn4() {
    testSame("var foo = {}; var bar; for (var e in bar = foo.a); bar.b = 3; foo.a");
  }

  @Test
  public void testSetterInForIn5() {
    testSame("var foo = {}; var bar; for (var e in foo.a) { bar = e } bar.b = 3; foo.a");
  }

  @Test
  public void testSetterInForIn6() {
    testSame("var foo = {}; for(var e in foo);");
  }

  @Test
  public void testSetterInForOf1() {
    test(
        "var foo = {}; var bar; for(var e of bar = foo.a);",
        "var foo = {};          for(var e of       foo.a);");
  }

  @Test
  public void testSetterInForOf2() {
    testSame("var foo = {}; var bar; for(var e of bar = foo.a); bar");
  }

  @Test
  public void testSetterInForOf3() {
    testSame("var foo = {}; var bar; for(var e of bar = foo.a); bar.b = 3");
  }

  @Test
  public void testSetterInForOf4() {
    testSame("var foo = {}; var bar; for (var e of bar = foo.a); bar.b = 3; foo.a");
  }

  @Test
  public void testSetterInForOf5() {
    testSame("var foo = {}; var bar; for (var e of foo.a) { bar = e } bar.b = 3; foo.a");
  }

  @Test
  public void testSetterInForOf6() {
    testSame("var foo = {}; for(var e of foo);");
  }

  @Test
  public void testSetterInIfPredicate() {
    test(
        "var a = 1; var Class = function() {}; if (Class.property_ = a);",
        "var a = 1;                            if (                  a);");
  }

  @Test
  public void testSetterInWhilePredicate() {
    test(
        "var a = 1;" + "var Class = function() {}; " + "while (Class.property_ = a);",
        "var a = 1; for (;a;) {}");
  }

  @Test
  public void testSetterInDoWhilePredicate() {
    test(
        "var a = 1; var Class = function() {}; do {} while (Class.property_ = a);",
        "var a = 1;                            do ;  while (                  a);");
  }

  @Test
  public void testSetterInSwitchInput() {
    test(
        "var a = 1;var Class = function() {}; switch (Class.property_ = a) {default:}",
        "var a = 1;                           switch (                  a) {default:}");
  }

  @Test
  public void testComplexAssigns() {
    test("var x = 0; x += 3; x *= 5;", "");
  }

  @Test
  public void testNestedAssigns1() {
    test("var x = 0; var y = x = 3; window.alert(y);", "var y = 3; window.alert(y);");
  }

  @Test
  public void testNestedAssigns2() {
    testSame("var x = 0; var y = x = {}; x.b = 3; window.alert(y);");
  }

  @Test
  public void testComplexNestedAssigns1() {
    // TODO(bradfordcsmith): Make RemoveUnusedCode smarter, so that we can eliminate y.
    testSame("var x = 0; var y = 2; y += x = 3; window.alert(x);");
  }

  @Test
  public void testComplexNestedAssigns2() {
    test(
        "var x = 0; var y = 2; y += x = 3; window.alert(y);",
        "var y = 2; y += 3; window.alert(y);");
  }

  @Test
  public void testComplexNestedAssigns3() {
    test("var x = 0; var y = x += 3; window.alert(x);", "var x = 0; x += 3; window.alert(x);");
  }

  @Test
  public void testComplexNestedAssigns4() {
    testSame("var x = 0; var y = x += 3; window.alert(y);");
  }

  @Test
  public void testUnintendedUseOfInheritsInLocalScope1() {
    testSame(
        "goog.mixin = function() {}; "
            + "(function() { var x = {}; var y = {}; goog.mixin(x, y); })();");
  }

  @Test
  public void testUnintendedUseOfInheritsInLocalScope2() {
    testSame(
        "goog.mixin = function() {}; "
            + "var x = {}; var y = {}; (function() { goog.mixin(x, y); })();");
  }

  @Test
  public void testUnintendedUseOfInheritsInLocalScope3() {
    testSame(
        "goog.mixin = function() {}; "
            + "var x = {}; var y = {}; (function() { goog.mixin(x, y); })(); "
            + "window.alert(x);");
  }

  @Test
  public void testUnintendedUseOfInheritsInLocalScope4() {
    // Ensures that the "goog$mixin" variable doesn't get stripped out,
    // even when it's only used in a local scope.
    testSame(
        "var goog$mixin = function() {}; "
            + "(function() { var x = {}; var y = {}; goog$mixin(x, y); })();");
  }

  @Test
  public void testPrototypePropertySetInLocalScope1() {
    test(
        "(function() { var x = function(){}; x.prototype.bar = 3; })();",
        "(function() {                                            })();");
  }

  @Test
  public void testPrototypePropertySetInLocalScope2() {
    test(
        "var x = function(){}; (function() { x.prototype.bar = 3; })();",
        "                      (function() {                      })();");
  }

  @Test
  public void testPrototypePropertySetInLocalScope3() {
    test("var x = function(){ x.prototype.bar = 3; };", "");
  }

  @Test
  public void testPrototypePropertySetInLocalScope4() {
    test("var x = {}; x.foo = function(){ x.foo.prototype.bar = 3; };", "");
  }

  @Test
  public void testPrototypePropertySetInLocalScope5() {
    test("var x = {}; x.prototype.foo = 3;", "");
  }

  @Test
  public void testPrototypePropertySetInLocalScope6() {
    testSame("var x = {}; x.prototype.foo = 3; use(x.prototype.foo)");
  }

  @Test
  public void testPrototypePropertySetInLocalScope7() {
    testSame("var x = {}; x.foo = 3; use(x.foo)");
  }

  @Test
  public void testRValueReference1() {
    testSame("var a = 1; a");
  }

  @Test
  public void testRValueReference2() {
    testSame("var a = 1; 1+a");
  }

  @Test
  public void testRValueReference3() {
    testSame("var x = {}; x.prototype.foo = 3; var a = x.prototype.foo; 1+a");
  }

  @Test
  public void testRValueReference4() {
    testSame("var x = {}; x.prototype.foo = 3; use(x.prototype.foo);");
  }

  @Test
  public void testRValueReference5() {
    testSame("var x = {}; x.prototype.foo = 3; 1+x.prototype.foo");
  }

  @Test
  public void testRValueReference6() {
    testSame("var x = {}; var idx = 2; x[idx]");
  }

  @Test
  public void testUnhandledTopNode() {
    testSame(
        "function Foo() {}; Foo.prototype.isBar = function() {};"
            + "function Bar() {}; Bar.prototype.isFoo = function() {};"
            + "var foo = new Foo(); var bar = new Bar();"
            +
            // The boolean AND here is currently unhandled by this pass, but
            // it should not cause it to blow up.
            "var cond = foo.isBar() && bar.isFoo();"
            + "if (cond) {window.alert('hello');}");
  }

  @Test
  public void testPropertyDefinedInGlobalScope() {
    testSame("function Foo() {}; var x = new Foo(); x.cssClass = 'bar';" + "window.alert(x);");
  }

  @Test
  public void testConditionallyDefinedFunction1() {
    testSame("var g; externfoo.x || (externfoo.x = function() { g; })");
  }

  @Test
  public void testConditionallyDefinedFunction2() {
    testSame("var g; 1 || (externfoo.x = function() { g; })");
  }

  @Test
  public void testConditionallyDefinedFunction3() {
    test(
        "var a = {}; doThing1() || (a.f = function() { externfoo = 1; } || doThing2());",
        "            doThing1() || (      function() { externfoo = 1; } || doThing2());");
  }

  @Test
  public void testGetElemOnThis() {
    testSame("var a = 3; this['foo'] = a;");
    testSame("this['foo'] = 3;");
  }

  @Test
  public void testRemoveInstanceOfOnly() {
    test(
        lines(
            "function Foo() {}",
            "Foo.prototype.isBar = function() {};",
            "var x;",
            "if (x instanceof Foo) { window.alert(x); }"),
        lines(
            "                 ",
            "                                    ",
            "var x;",
            "if (false           ) { window.alert(x); }"));
  }

  @Test
  public void testRemoveLocalScopedInstanceOfOnly() {
    test(
        lines(
            "function Foo() {}",
            "function Bar(x) { use(x instanceof Foo); }",
            "externfoo.x = new Bar({});"),
        lines(
            "                 ",
            "function Bar(x) { use(false           ); }",
            "externfoo.x = new Bar({});"));
  }

  @Test
  public void testRemoveInstanceOfWithReferencedMethod() {
    test(
        lines(
            "function Foo() {}",
            "Foo.prototype.isBar = function() {};",
            "var x;",
            "if (x instanceof Foo) { window.alert(x.isBar()); }"),
        lines(
            "                 ",
            "                                    ",
            "var x;",
            "if (false           ) { window.alert(x.isBar()); }"));
  }

  @Test
  public void testDoNotChangeReferencedInstanceOf() {
    testSame(
        lines(
            "function Foo() {}",
            "var x = new Foo();",
            "if (x instanceof Foo) { window.alert(x); }"));
  }

  @Test
  public void testDoNotChangeReferencedLocalScopedInstanceOf() {
    testSame(
        lines(
            "function Foo() {};",
            "externfoo.x = new Foo();",
            "function Bar() {",
            "  var x;",
            "  if (x instanceof Foo) { window.alert(x); }",
            "}",
            "externfoo.y = new Bar();"));
  }

  @Test
  public void testDoNotChangeLocalScopeReferencedInstanceOf() {
    testSame(
        lines(
            "function Foo() {}",
            "function Bar() { use(new Foo()); }",
            "externfoo.x = new Bar();",
            "var x;",
            "if (x instanceof Foo) { window.alert(x); }"));
  }

  @Test
  public void testDoNotChangeLocalScopeReferencedLocalScopedInstanceOf() {
    testSame(
        lines(
            "function Foo() {}",
            "function Bar() { new Foo(); }",
            "Bar.prototype.func = function(x) {",
            "  if (x instanceof Foo) { window.alert(x); }",
            "};",
            "new Bar().func();"));
  }

  @Test
  public void testDoNotChangeLocalScopeReferencedLocalScopedInstanceOf2() {
    test(
        lines(
            "function Foo() {}",
            "var createAxis = function(f) { return window.passThru(f); };",
            "var axis = createAxis(function(test) {",
            "  return test instanceof Foo;",
            "});"),
        lines(
            "var createAxis = function(f) { return window.passThru(f); };",
            "createAxis(function(test) {",
            "  return false;",
            "});"));
  }

  @Test
  public void testDoNotChangeInstanceOfGetElem() {
    testSame(
        "var goog = {};"
            + "function f(obj, name) {"
            + "  if (obj instanceof goog[name]) {"
            + "    return name;"
            + "  }"
            + "}"
            + "window['f'] = f;");
  }

  @Test
  public void testIssue2822() {
    testSame(
        lines(
            "var C = function C() {",
            "  if (!(this instanceof C)) {",
            "    throw new Error('not an instance');",
            "  }",
            "}",
            "use(new C());",
            ""));
  }

  @Test
  public void testWeirdnessOnLeftSideOfPrototype() {
    // This checks a bug where 'x' was removed, but the function referencing
    // it was not, causing problems.
    testSame("var x = 3; (function() {}).z = function() { return x; };");
  }

  @Test
  public void testDoNotChangeInstanceOfGetprop() {
    testSame(
        "function f(obj) {"
            + "  if (obj instanceof window.MouseEvent) obj.preventDefault();"
            + "}"
            + "window['f'] = f;");
  }

  // TODO(b/66971163): Enable or remove this test.
  public void disabledTestShortCircuit1() {
    test("var a = doThing1() || 1", "doThing1()");
  }

  @Test
  public void testShortCircuit2() {
    test("var a = 1 || doThing1()", "1 || doThing1()");
  }

  @Test
  public void testShortCircuit3() {
    test("var a = doThing1() || doThing2()", "doThing1() || doThing2()");
  }

  @Test
  public void testShortCircuit4() {
    test("var a = doThing1() || 3 || doThing2()", "doThing1() || 3 || doThing2()");
  }

  // TODO(b/66971163): Enable or remove this test.
  public void disabledTestShortCircuit5() {
    test("var a = doThing1() && 1", "doThing1()");
  }

  @Test
  public void testShortCircuit6() {
    test("var a = 1 && doThing1()", "1 && doThing1()");
  }

  @Test
  public void testShortCircuit7() {
    test("var a = doThing1() && doThing2()", "doThing1() && doThing2()");
  }

  @Test
  public void testShortCircuit8() {
    test("var a = doThing1() && 3 && doThing2()", "doThing1() && 3 && doThing2()");
  }

  @Test
  public void testRhsReference1() {
    testSame("var a = 1; a");
  }

  @Test
  public void testRhsReference2() {
    testSame("var a = 1; a || doThing1()");
  }

  @Test
  public void testRhsReference3() {
    testSame("var a = 1; 1 || a");
  }

  @Test
  public void testRhsReference4() {
    test("var a = 1; var b = a || doThing1()", "var a = 1; a || doThing1()");
  }

  @Test
  public void testRhsReference5() {
    testSame("var a = 1, b = 5; a; use(b)");
  }

  @Test
  public void testRhsAssign1() {
    test(
        "var foo, bar; foo || (bar = 1)", // preserve newline
        "var foo;      foo || 0        ");
  }

  @Test
  public void testRhsAssign2() {
    test(
        "var foo, bar, baz; foo || (baz = bar = 1)", // preserve newline
        "var foo;           foo || 0              ");
  }

  @Test
  public void testRhsAssign3() {
    testSame("var foo = null; foo || (foo = 1)");
  }

  @Test
  public void testRhsAssign4() {
    test("var foo = null; foo = (foo || 1)", "");
  }

  @Test
  public void testRhsAssign5() {
    test("var a = 3, foo, bar; foo || (bar = a)", "var        foo     ; foo || 0        ");
  }

  @Test
  public void testRhsAssign6() {
    test(
        "function Foo(){} var foo = null;"
            + "var f = function () {foo || (foo = new Foo()); return foo}",
        "");
  }

  @Test
  public void testRhsAssign7() {
    testSame(
        "function Foo(){} var foo = null;" + "var f = function () {foo || (foo = new Foo())}; f()");
  }

  @Test
  public void testRhsAssign8() {
    test(
        lines(
            "function Foo(){}",
            "var foo = null;",
            "var f = function () {",
            "  (foo = new Foo()) || doThing1();",
            "};",
            "f()"),
        lines(
            "function Foo(){}",
            "               ",
            "var f = function () {",
            "         new Foo()  || doThing1();",
            "};",
            "f()"));
  }

  @Test
  public void testRhsAssign9() {
    test(
        "function Foo(){} var foo = null;"
            + "var f = function () {1 + (foo = new Foo()); return foo}",
        "");
  }

  @Test
  public void testAssignWithOr1() {
    testSame("var foo = null;" + "var f = window.a || function () {return foo}; f()");
  }

  @Test
  public void testAssignWithOr2() {
    test("var foo = null; var f = window.a || function () {return foo};", "");
  }

  @Test
  public void testAssignWithAnd1() {
    testSame("var foo = null;" + "var f = window.a && function () {return foo}; f()");
  }

  @Test
  public void testAssignWithAnd2() {
    test("var foo = null; var f = window.a && function () {return foo};", "");
  }

  @Test
  public void testAssignWithHook1() {
    testSame(
        "function Foo(){} var foo = null;"
            + "var f = window.a ? "
            + "    function () {return new Foo()} : function () {return foo}; f()");
  }

  @Test
  public void testAssignWithHook2() {
    test(
        "function Foo(){} var foo = null;"
            + "var f = window.a ? "
            + "    function () {return new Foo()} : function () {return foo};",
        "");
  }

  @Test
  public void testAssignWithHook2a() {
    test(
        "function Foo(){} var foo = null;"
            + "var f; f = window.a ? "
            + "    function () {return new Foo()} : function () {return foo};",
        "");
  }

  @Test
  public void testAssignWithHook3() {
    testSame(
        "function Foo(){} var foo = null; var f = {};"
            + "f.b = window.a ? "
            + "    function () {return new Foo()} : function () {return foo}; f.b()");
  }

  @Test
  public void testAssignWithHook4() {
    test(
        "function Foo(){} var foo = null; var f = {};"
            + "f.b = window.a ? "
            + "    function () {return new Foo()} : function () {return foo};",
        "");
  }

  @Test
  public void testAssignWithHook5() {
    testSame(
        "function Foo(){} var foo = null; var f = {};"
            + "f.b = window.a ? function () {return new Foo()} :"
            + "    window.b ? function () {return foo} :"
            + "    function() { return Foo }; f.b()");
  }

  @Test
  public void testAssignWithHook6() {
    test(
        "function Foo(){} var foo = null; var f = {};"
            + "f.b = window.a ? function () {return new Foo()} :"
            + "    window.b ? function () {return foo} :"
            + "    function() { return Foo };",
        "");
  }

  @Test
  public void testAssignWithHook7() {
    testSame("function Foo(){} var foo = null;" + "var f = window.a ? new Foo() : foo;" + "f()");
  }

  // TODO(b/66971163): enable or remove this test
  public void disabledTestAssignWithHook8() {
    test(
        "function Foo(){} var foo = null; var f = window.a  ? new Foo() : foo;",
        "function Foo(){}                         window.a && new Foo()      ;");
  }

  // TODO(b/66971163): enable or remove this test
  public void disabledTestAssignWithHook9() {
    test(
        "function Foo(){} var foo = null; var f = {};f.b = window.a  ? new Foo() : foo;",
        "function Foo(){}                                  window.a && new Foo()      ;");
  }

  @Test
  public void testAssign1() {
    test("function Foo(){} var foo = null; var f = {};" + "f.b = window.a;", "");
  }

  @Test
  public void testAssign2() {
    test("function Foo(){} var foo = null; var f = {};" + "f.b = window;", "");
  }

  @Test
  public void testAssign3() {
    test("var f = {};" + "f.b = window;", "");
  }

  @Test
  public void testAssign4() {
    test(
        "function Foo(){} var foo = null; var f = {};" + "f.b = new Foo();",
        "function Foo(){} new Foo()");
  }

  @Test
  public void testAssign5() {
    test("function Foo(){} var foo = null; var f = {};" + "f.b = foo;", "");
  }

  @Test
  public void testAssignWithCall() {
    test("var fun, x; (fun = function(){ x; })();", "var x; (function(){ x; })();");
  }

  @Test
  public void testAssignWithCall2() {
    test(
        "var fun, x; (123, fun = function(){ x; })();",
        "var      x; (123,       function(){ x; })();");
  }

  @Test
  public void testNestedAssign1() {
    test("var a, b = a = 1, c = 2", "");
  }

  @Test
  public void testNestedAssign2() {
    test(
        "var a, b = a = 1; use(b)", // preserve newline
        "var b = 1;        use(b)");
  }

  @Test
  public void testNestedAssign3() {
    test(
        "var a, b = a = 1; a = b = 2; use(b)", // preserve newline
        "var    b =     1;     b = 2; use(b)");
  }

  @Test
  public void testNestedAssign4() {
    test(
        "var a, b = a = 1; b = a = 2; use(b)", // preserve newline
        "var    b     = 1; b =     2; use(b)");
  }

  @Test
  public void testNestedAssign5() {
    test("var a, b = a = 1; b = a = 2", "");
  }

  @Test
  public void testNestedAssign15() {
    test("var a, b, c; c = b = a = 2", "");
  }

  @Test
  public void testNestedAssign6() {
    testSame("var a, b, c; a = b = c = 1; use(a, b, c)");
  }

  @Test
  public void testNestedAssign7() {
    testSame("var a = 0, j = 0, i = []; a = i[j] = 1; use(a, i[j])");
  }

  @Test
  public void testNestedAssign8() {
    testSame(
        lines(
            "function f(){",
            "  this.externProp1 = this.externProp2 = use(this.hiddenInput_, externfoo);",
            "}",
            "f()"));
  }

  @Test
  public void testRefChain1() {
    test("var a = 1; var b = a; var c = b; var d = c", "");
  }

  @Test
  public void testRefChain2() {
    test(
        "var a = 1; var b = a; var c = b; var d = c || doThing1()",
        "var a = 1; var b = a; var c = b;         c || doThing1()");
  }

  // TODO(b/66971163): Enable or remove
  public void disabledTestRefChain3() {
    test(
        "var a = 1; var b = a; var c = b; var d = c + doThing1()",
        "                                             doThing1()");
  }

  // TODO(b/66971163): Enable or remove
  public void disabledTestRefChain4() {
    test(
        "var a = 1; var b = a; var c = b; var d = doThing1() || c",
        "                                         doThing1()     ");
  }

  // TODO(b/66971163): Enable or remove
  public void disabledTestRefChain5() {
    test(
        "var a = 1; var b = a; var c = b; var d = doThing1() ?  doThing2() : c",
        "                                         doThing1() && doThing2()    ");
  }

  @Test
  public void testRefChain6() {
    test(
        "var a = 1; var b = a; var c = b; var d = c ? doThing1() : doThing2()",
        "var a = 1; var b = a; var c = b;         c ? doThing1() : doThing2()");
  }

  // TODO(b/66971163): Enable or remove
  public void disabledTestRefChain7() {
    test(
        "var a = 1; var b = a; var c = b; var d = (b + doThing1())  ? doThing2() : c",
        "var a = 1; var b = a;                    (b + doThing1()) && doThing2()    ");
  }

  // TODO(b/66971163): Enable or remove
  public void disabledTestRefChain8() {
    test(
        "var a = 1; var b = a; var c = b; var d = doThing1()[b]  ? doThing2() : 0",
        "var a = 1; var b = a;                    doThing1()[b] && doThing2()    ");
  }

  // TODO(b/66971163): Enable or remove
  public void disabledTestRefChain9() {
    test(
        "var a = 1; var b = a; var c = 5; var d = doThing1()[b+c]  ? doThing2() : 0",
        "var a = 1; var b = a; var c = 5;         doThing1()[b+c] && doThing2()    ");
  }

  // TODO(b/66971163): Enable or remove
  public void disabledTestRefChain10() {
    test(
        "var a = 1; var b = a; var c = b; var d = doThing1()[b]  ? doThing2() : 0",
        "var a = 1; var b = a;                    doThing1()[b] && doThing2()    ");
  }

  // TODO(b/66971163): Enable or remove
  public void disabledTestRefChain11() {
    test(
        "var a = 1; var b = a; var d = doThing1()[b]  ? doThing2() : 0",
        "var a = 1; var b = a;         doThing1()[b] && doThing2()    ");
  }

  @Test
  public void testRefChain12() {
    testSame("var a = 1; var b = a; doThing1()[b] ? doThing2() : 0");
  }

  // TODO(b/66971163): Enable or remove
  public void disabledTestRefChain13() {
    test(
        "function f(){} var a = 1; var b = a; var d = f()[b]  ? doThing1() : 0",
        "function f(){} var a = 1; var b = a;         f()[b] && doThing1()    ");
  }

  @Test
  public void testRefChain14() {
    testSame("function f(){}var a = 1; var b = a; f()[b] ? doThing1() : 0");
  }

  // TODO(b/66971163): Enable or remove
  public void disabledTestRefChain15() {
    test(
        "function f(){} var a = 1, b = a; var c = f(); var d = c[b]  ? doThing1() : 0",
        "function f(){} var a = 1, b = a; var c = f();         c[b] && doThing1()    ");
  }

  @Test
  public void testRefChain16() {
    testSame("function f(){}var a = 1; var b = a; var c = f(); c[b] ? doThing1() : 0");
  }

  @Test
  public void testRefChain17() {
    test(
        "function f(){} var a = 1; var b = a; var c = f(); var d = c[b]",
        "function f(){}                               f()              ");
  }

  @Test
  public void testRefChain18() {
    testSame("var a = 1; doThing1()[a] && doThing2()");
  }

  @Test
  public void testRefChain19() {
    test(
        "var a = 1; var b = [a]; var c = b; b[doThing1()] ? doThing2() : 0",
        "var a = 1; var b = [a];            b[doThing1()] ? doThing2() : 0");
  }

  // TODO(b/66971163): enable or remove this case
  public void disabledTestRefChain20() {
    test(
        "var a = 1; var b = [a]; var c = b; var d = b[doThing1()]  ? doThing2() : 0",
        "var a = 1; var b = [a];                    b[doThing1()] && doThing2()    ");
  }

  @Test
  public void testRefChain21() {
    testSame("var a = 1; var b = 2; var c = a + b; use(c)");
  }

  @Test
  public void testRefChain22() {
    test(
        "var a = 2; var b = a = 4; use(a)", // preserve newline
        "var a = 2;         a = 4; use(a)");
  }

  @Test
  public void testRefChain23() {
    test(
        "var a = {}; var b = a[1] || doThing1()", // preserve newline
        "var a = {};         a[1] || doThing1()");
  }

  /**
   * Expressions that cannot be attributed to any enclosing dependency scope should be treated as
   * global references.
   *
   * @bug 1739062
   */
  @Test
  public void testAssignmentWithComplexLhs() {
    testSame("function f() { return this; }" + "var o = {'key': 'val'};" + "f().x_ = o['key'];");
  }

  @Test
  public void testAssignmentWithComplexLhs2() {
    testSame(
        "function f() { return this; }"
            + "var o = {'key': 'val'};"
            + "f().foo = function() {"
            + "  o"
            + "};");
  }

  @Test
  public void testAssignmentWithComplexLhs3() {
    String source =
        lines(
            "var o = {'key': 'val'};", // preserve newline
            "function init_() {",
            "  use(o['key'])",
            "}");

    test(source, "");
    testSame(source + ";init_()");
  }

  @Test
  public void testAssignmentWithComplexLhs4() {
    testSame(
        lines(
            "function f() { return this; }",
            "var o = {'key': 'val'};",
            "f().foo = function() {",
            "  use(o['key']);",
            "};"));
  }

  /**
   * Do not "prototype" property of variables that are not being tracked (because they are local).
   *
   * @bug 1809442
   */
  @Test
  public void testNoRemovePrototypeDefinitionsOutsideGlobalScope1() {
    testSame(
        lines(
            "function f(arg){ use(arg); }",
            "(function(){",
            "  function O() {}",
            "  O.prototype = { constructor: O };",
            "  f(O);",
            "})()"));
  }

  @Test
  public void testNoRemovePrototypeDefinitionsOutsideGlobalScope2() {
    testSame(
        lines(
            "function f(arg){ use(arg); }",
            "(function h(){",
            "  function L() {}",
            "  L.prototype = { constructor: L };",
            "  f(L);",
            "})()"));
  }

  @Test
  public void testNoRemovePrototypeDefinitionsOutsideGlobalScope4() {
    testSame(
        lines(
            "function f(arg){ use(arg); }",
            "function g(){",
            "  function N() {}",
            "  N.prototype = { constructor: N };",
            "  f(N);",
            "}",
            "g()"));
  }

  @Test
  public void testNoRemovePrototypeDefinitionsOutsideGlobalScope5() {
    test(
        "function g(){ function R() {} R.prototype = { constructor: R }; } g()",
        "function g(){                                               } g()");
  }

  @Test
  public void testRemovePrototypeDefinitionsInGlobalScope1() {
    testSame("function M() {} M.prototype = { constructor: M }; use(M);");
  }

  @Test
  public void testRemovePrototypeDefinitionsInGlobalScope2() {
    test("function Q() {} Q.prototype = { constructor: Q };", "");
  }

  @Test
  public void testRemoveLabeledStatement() {
    test("LBL: var x = 1;", "LBL: {}");
  }

  // TODO(b/66971163): enable or remove this
  public void disabledTestRemoveLabeledStatement2() {
    test(
        "var x; LBL: x = doThing1() + doThing2()  ", // preserve newline
        "       LBL:   { doThing1() ; doThing2() }");
  }

  @Test
  public void testRemoveLabeledStatement3() {
    test("var x; LBL: x = 1;", "LBL: {}");
  }

  @Test
  public void testRemoveLabeledStatement4() {
    test(
        "var a; LBL: a = doThing1()", // preserve newline
        "       LBL:     doThing1()");
  }

  @Test
  public void testPreservePropertyMutationsToAlias1() {
    // Test for issue b/2316773 - property get case
    // Since a is referenced, property mutations via a's alias b must
    // be preserved.
    testSame("var a = {}; var b = a; b.x = 1; a");
  }

  @Test
  public void testPreservePropertyMutationsToAlias2() {
    // Test for issue b/2316773 - property get case, don't keep 'c'
    test(
        "var a = {}; var b = a; var c = a; b.x = 1; a", // preserve newline
        "var a = {}; var b = a;            b.x = 1; a");
  }

  @Test
  public void testPreservePropertyMutationsToAlias3() {
    // Test for issue b/2316773 - property get case, chain
    testSame("var a = {}; var b = a; var c = b; c.x = 1; a");
  }

  @Test
  public void testPreservePropertyMutationsToAlias4() {
    // Test for issue b/2316773 - element get case
    testSame("var a = {}; var b = a; b['x'] = 1; a");
  }

  @Test
  public void testPreservePropertyMutationsToAlias5() {
    // From issue b/2316773 description
    testSame(
        lines(
            "function testCall(o){ use(o); }",
            "var DATA = {'prop': 'foo','attr': {}};",
            "var SUBDATA = DATA['attr'];",
            "SUBDATA['subprop'] = 'bar';",
            "testCall(DATA);"));
  }

  @Test
  public void testPreservePropertyMutationsToAlias6() {
    // Longer GETELEM chain
    testSame(
        lines(
            "function testCall(o){ use(o); }",
            "var DATA = {'prop': 'foo','attr': {}};",
            "var SUBDATA = DATA['attr'];",
            "var SUBSUBDATA = SUBDATA['subprop'];",
            "SUBSUBDATA['subsubprop'] = 'bar';",
            "testCall(DATA);"));
  }

  @Test
  public void testPreservePropertyMutationsToAlias7() {
    // Make sure that the base class does not depend on the derived class.
    test(
        "var a = {}; var b = {}; b.x = 0; goog.inherits(b, a); use(a);",
        "var a = {};                                           use(a);");
  }

  @Test
  public void testPreservePropertyMutationsToAlias8() {
    // Make sure that the derived classes don't end up depending on each other.
    test(
        lines(
            "var a = {};", // preserve newline
            "var b = {}; b.x = 0;",
            "var c = {}; c.y = 0;",
            "goog.inherits(b, a);",
            "goog.inherits(c, a);",
            "c"),
        lines(
            "var a = {};", // preserve newline
            "                    ",
            "var c = {}; c.y = 0;",
            "                    ",
            "goog.inherits(c, a);",
            "c"));
  }

  @Test
  public void testPreservePropertyMutationsToAlias9() {
    testSame(
        lines(
            "var a = {b: {}};", // preserve newline
            "var c = a.b; c.d = 3;",
            "a.d = 3; a.d;"));
  }

  @Test
  public void testRemoveAlias() {
    test(
        "var a = {b: {}}; var c = a.b; a.d = 3; a.d;",
        "var a = {b: {}};              a.d = 3; a.d;");
  }

  @Test
  public void testSingletonGetter1() {
    test("function Foo() {} goog.addSingletonGetter(Foo);", "");
  }

  @Test
  public void testSingletonGetter2() {
    test("function Foo() {} goog$addSingletonGetter(Foo);", "");
  }

  @Test
  public void testSingletonGetter3() {
    // addSingletonGetter adds a getInstance method to a class.
    testSame("function Foo() {} goog$addSingletonGetter(Foo); Foo.getInstance();");
  }

  @Test
  public void testObjectDefineProperty() {
    // TODO(bradfordcsmith): Remove Object.defineProperty() like we do Object.defineProperties().
    testSame("var a = {}; Object.defineProperty(a, 'prop', {value: 5});");
  }

  @Test
  public void testObjectDefinePropertiesOnNamespaceThatEscapes() {
    testSame("var a = doThing1(); Object.defineProperties(a, {'prop': {value: 5}});");
  }

  @Test
  public void testObjectDefinePropertiesOnConstructorThatEscapes() {
    testSame("var Foo = doThing1(); Object.defineProperties(Foo.prototype, {'prop': {value: 5}});");
    testSame("Object.defineProperties(doThing1(), {'prop': {value: 5}});");
  }

  @Test
  public void testRegularAssignPropOnNamespaceThatEscapes() {
    testSame("var a = doThing1(); a.prop = 5;");
  }

  @Test
  public void testRegularAssignPropOnPropFromAVar() {
    testSame("var b = 5; var a = {}; a.prop = b; use(a.prop);");
  }

  @Test
  public void testUnanalyzableObjectDefineProperties() {
    test("var a = {}; Object.defineProperties(a, externfoo);", "");
  }

  @Test
  public void testObjectDefinePropertiesOnNamespace1() {
    testSame("var a = {}; Object.defineProperties(a, {prop: {value: 5}}); use(a.prop);");
    test("var a = {}; Object.defineProperties(a, {prop: {value: 5}});", "");
  }

  @Test
  public void testObjectDefinePropertiesOnNamespace2() {
    test(
        lines(
            "var a = {};",
            "Object.defineProperties(a, {p1: {value: 5}, p2: {value: 3} });",
            "use(a.p1);"),
        lines(
            "var a = {};",
            "Object.defineProperties(a, {p1: {value: 5}                 });",
            "use(a.p1);"));

    test(
        lines(
            "var a = {};", // preserve newline
            "Object.defineProperties(a, {p1: {value: 5}, p2: {value: 3} });"),
        "");
  }

  @Test
  public void testNonAnalyzableObjectDefinePropertiesCall() {
    testSame("var a = {}; var z = Object.defineProperties(a, {'prop': {value: 5}}); use(z);");
  }

  @Test
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

  @Test
  public void testObjectDefinePropertiesOnNamespace4() {
    test(
        lines(
            "function b() { alert('hello'); };",
            "var a = {};",
            "Object.defineProperties(a, {prop: {value: b()}});"),
        "function b() { alert('hello'); }; ({prop: {value: b()}});");
  }

  @Test
  public void testObjectDefinePropertiesOnNamespace5() {
    test(
        lines(
            "function b() { alert('hello'); };", // preserve newline
            "function c() { alert('world'); };",
            "var a = {};",
            "Object.defineProperties(a, {p1: {value: b()}, p2: {value: c()}});"),
        lines(
            "function b() { alert('hello'); };", // preserve newline
            "function c() { alert('world'); };",
            "           ",
            "                          ({p1: {value: b()}, p2: {value: c()}});"));
  }

  @Test
  public void testObjectDefinePropertiesOnConstructor() {
    testSame("function Foo() {} Object.defineProperties(Foo, {prop: {value: 5}}); use(Foo.prop);");
    test("function Foo() {} Object.defineProperties(Foo, {prop: {value: 5}});", "");
  }

  @Test
  public void testObjectDefinePropertiesOnPrototype1() {
    testSame(
        "function Foo() {}"
            + "Object.defineProperties(Foo.prototype, {prop: {value: 5}});"
            + "use((new Foo).prop);");

    test("function Foo() {} Object.defineProperties(Foo.prototype, {prop: {value: 5}});", "");
  }

  @Test
  public void testObjectDefinePropertiesOnPrototype2() {
    test(
        lines(
            "var b = 5;",
            "function Foo() {}",
            "Object.defineProperties(Foo.prototype, {prop: {value: b}});",
            "use(b)"),
        "var b = 5; use(b);");
  }

  @Test
  public void testObjectDefinePropertiesOnPrototype3() {
    test(
        lines(
            "var b = function() {};",
            "function Foo() {}",
            "Object.defineProperties(Foo.prototype, {prop: {value: b()}});"),
        "var b = function() {}; ({prop: {value: b()}});");
  }

  @Test
  public void testObjectDefineGetters() {
    test("function Foo() {} Object.defineProperties(Foo, {prop: {get: function() {}}});", "");

    test(
        "function Foo() {} Object.defineProperties(Foo.prototype, {prop: {get: function() {}}});",
        "");
  }

  @Test
  public void testObjectDefineSetters() {
    test("function Foo() {} Object.defineProperties(Foo, {prop: {set: function() {}}});", "");

    test(
        "function Foo() {} Object.defineProperties(Foo.prototype, {prop: {set: function() {}}});",
        "");
  }

  @Test
  public void testObjectDefineSetters_global() {
    test(
        lines(
            "function Foo() {} ",
            "$jscomp.global.Object.defineProperties(Foo, {prop: {set: function() {}}});"),
        "");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias1() {
    testSame("var self_ = window.gbar; self_.qs = function() {};");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias2() {
    testSame("var self_ = window; self_.qs = function() {};");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias3() {
    testSame("var self_ = window; self_['qs'] = function() {};");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias4() {
    testSame("var self_ = window['gbar'] || {}; self_.qs = function() {};");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias4a() {
    testSame("var self_; self_ = window.gbar || {}; self_.qs = function() {};");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias5() {
    testSame("var self_ = window || {}; self_['qs'] = function() {};");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias5a() {
    testSame("var self_; self_ = window || {}; self_['qs'] = function() {};");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias6() {
    testSame("var self_ = (window.gbar = window.gbar || {}); self_.qs = function() {};");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias6a() {
    testSame("var self_; self_ = (window.gbar = window.gbar || {}); self_.qs = function() {};");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias7() {
    testSame("var self_ = (window = window || {}); self_['qs'] = function() {};");
  }

  @Test
  public void testNoRemoveWindowPropertyAlias7a() {
    testSame("var self_; self_ = (window = window || {}); self_['qs'] = function() {};");
  }

  @Test
  public void testNoRemoveAlias0() {
    testSame(
        lines(
            "var x = {}; function f() { return x; };",
            "f().style.display = 'block';",
            "alert(x.style)"));
  }

  @Test
  public void testNoRemoveAlias1() {
    testSame(
        lines(
            "var x = {}; function f() { return x; };",
            "var map = f();",
            "map.style.display = 'block';",
            "alert(x.style)"));
  }

  @Test
  public void testNoRemoveAlias2() {
    testSame(
        lines(
            "var x = {};",
            "var map = (function () { return x; })();",
            "map.style = 'block';",
            "alert(x.style)"));
  }

  @Test
  public void testNoRemoveAlias3() {
    testSame(
        lines(
            "var x = {}; function f() { return x; };",
            "var map = {};",
            "map[1] = f();",
            "map[1].style.display = 'block';"));
  }

  @Test
  public void testNoRemoveAliasOfExternal0() {
    testSame("document.getElementById('foo').style.display = 'block';");
  }

  @Test
  public void testNoRemoveAliasOfExternal1() {
    testSame("var map = document.getElementById('foo'); map.style.display = 'block';");
  }

  @Test
  public void testNoRemoveAliasOfExternal2() {
    testSame(
        lines(
            "var map = {}", // preserve newline
            "map[1] = document.getElementById('foo');",
            "map[1].style.display = 'block';"));
  }

  @Test
  public void testNoRemoveThrowReference1() {
    testSame("var e = {}; throw e;");
  }

  @Test
  public void testNoRemoveThrowReference2() {
    testSame("function e() {} throw new e();");
  }

  // TODO(b/66971163): enable or remove this
  public void disabledTestClassDefinedInObjectLit1() {
    test(
        lines(
            "var data = {Foo: function() {}};", // preserve newline
            "data.Foo.prototype.toString = function() {};"),
        "");
  }

  // TODO(b/66971163): enable or remove this
  public void disabledTestClassDefinedInObjectLit2() {
    test(
        lines(
            "var data = {}; data.bar = {Foo: function() {}};",
            "data.bar.Foo.prototype.toString = function() {};"),
        "");
  }

  // TODO(b/66971163): enable or remove this
  public void disabledTestClassDefinedInObjectLit3() {
    test(
        lines(
            "var data = {bar: {Foo: function() {}}};", // preserve newline
            "data.bar.Foo.prototype.toString = function() {};"),
        "");
  }

  // TODO(b/66971163): enable or remove this
  public void disabledTestClassDefinedInObjectLit4() {
    test(
        lines(
            "var data = {};",
            "data.baz = {bar: {Foo: function() {}}};",
            "data.baz.bar.Foo.prototype.toString = function() {};"),
        "");
  }

  @Test
  public void testVarReferencedInClassDefinedInObjectLit1() {
    testSame(
        lines(
            "var ref = 3;", // preserve newline
            "var data = {Foo: function() { use(ref); }};",
            "window.Foo = data.Foo;"));
  }

  @Test
  public void testVarReferencedInClassDefinedInObjectLit2() {
    testSame(
        lines(
            "var ref = 3;",
            "var data = {",
            "  Foo: function() { use(ref); },",
            "  Bar: function() {}",
            "};",
            "window.Bar = data.Bar;"));
  }

  @Test
  public void testArrayExt() {
    testSame(
        lines(
            "Array.prototype.foo = function() { return 1 };",
            "var y = [];",
            "switch (y.foo()) {",
            "}"));
  }

  @Test
  public void testArrayAliasExt() {
    testSame(
        lines(
            "Array$X = Array;",
            "Array$X.prototype.foo = function() { return 1 };",
            "function Array$X() {}",
            "var y = [];",
            "switch (y.foo()) {",
            "}"));
  }

  @Test
  public void testExternalAliasInstanceof1() {
    test(
        lines(
            "Array$X = Array;", // preserve newline
            "function Array$X() {}",
            "var y = [];",
            "if (y instanceof Array) {}"),
        lines(
            "var y = [];", // preserve newline
            "if (y instanceof Array) {}"));
  }

  @Test
  public void testExternalAliasInstanceof2() {
    testSame(
        lines(
            "Array$X = Array;",
            "function Array$X() {}",
            "var y = [];",
            "if (y instanceof Array$X) {}"));
  }

  @Test
  public void testExternalAliasInstanceof3() {
    testSame("var b = Array; var y = []; if (y instanceof b) {}");
  }

  @Test
  public void testAliasInstanceof4() {
    testSame("function Foo() {}; var b = Foo; var y = new Foo(); if (y instanceof b) {}");
  }

  @Test
  public void testAliasInstanceof5() {
    testSame(
        lines(
            "var x;",
            "function Foo() {}",
            "function Bar() {}",
            "var b = x ? Foo : Bar;",
            "var y = new Foo();",
            "if (y instanceof b) {}"));
  }

  @Test
  public void testRemovePrototypeAliases() {
    test(
        "function g() {} function F() {} F.prototype.bar = g; window.g = g;",
        "function g() {}                                      window.g = g;");
  }

  // TODO(b/66971163): Enable or remove this test.
  public void disabledTestIssue284() {
    test(
        lines(
            "var ns = {};",
            "/** @constructor */",
            "ns.PageSelectionModel = function() {};",
            "/** @constructor */",
            "ns.PageSelectionModel.FooEvent = function() {};",
            "/** @constructor */",
            "ns.PageSelectionModel.SelectEvent = function() {};",
            "goog.inherits(ns.PageSelectionModel.ChangeEvent, ns.PageSelectionModel.FooEvent);"),
        "");
  }

  @Test
  public void testIssue838a() {
    testSame(
        lines(
            "var z = window['z'] || (window['z'] = {});", // preserve newline
            "z['hello'] = 'Hello';",
            "z['world'] = 'World';"));
  }

  @Test
  public void testIssue838b() {
    testSame(
        lines(
            "var z;", // preserve newline
            "window['z'] = z || (z = {});",
            "z['hello'] = 'Hello';",
            "z['world'] = 'World';"));
  }

  @Test
  public void testIssue874a() {
    testSame(
        lines(
            "var a = a || {};",
            "var b = a;",
            "b.View = b.View || {}",
            "var c = b.View;",
            "c.Editor = function f(d, e) {",
            "  return d + e",
            "};",
            "window.ImageEditor.View.Editor = a.View.Editor;"));
  }

  @Test
  public void testIssue874b() {
    testSame(
        lines(
            "var b;",
            "var c = b = {};",
            "c.Editor = function f(d, e) {",
            "  return d + e",
            "};",
            "window['Editor'] = b.Editor;"));
  }

  @Test
  public void testIssue874c() {
    testSame(
        lines(
            "var b, c;",
            "c = b = {};",
            "c.Editor = function f(d, e) {",
            "  return d + e",
            "};",
            "window['Editor'] = b.Editor;"));
  }

  @Test
  public void testIssue874d() {
    testSame(
        lines(
            "var b = {}, c;",
            "c = b;",
            "c.Editor = function f(d, e) {",
            "  return d + e",
            "};",
            "window['Editor'] = b.Editor;"));
  }

  @Test
  public void testIssue874e() {
    testSame(
        lines(
            "var a;",
            "var b = a || (a = {});",
            "var c = b.View || (b.View = {});",
            "c.Editor = function f(d, e) {",
            "  return d + e",
            "};",
            "window.ImageEditor.View.Editor = a.View.Editor;"));
  }

  @Test
  public void testBug6575051() {
    testSame(
        lines(
            "var hackhack = window['__o_o_o__'] = window['__o_o_o__'] || {};",
            "window['__o_o_o__']['va'] = 1;",
            "hackhack['Vb'] = 1;"));
  }

  @Test
  public void testBug37975351a() {
    // The original repro case from the bug.
    testSame(
        lines(
            "function noop() {}",
            "var x = window['magic'];",
            "var FormData = window['FormData'] || noop;",
            "function f() { return x instanceof FormData; }",
            "console.log(f());"));
  }

  @Test
  public void testBug37975351b() {
    // The simplified repro that still repro'd the problem.
    testSame(
        lines(
            "var FormData = window['FormData'] || function() {};",
            "function f() { return window['magic'] instanceof FormData; }",
            "console.log(f());"));
  }

  @Test
  public void testBug37975351c() {
    // This simpliification did not reproduce the problematic behavior.
    testSame(
        lines(
            "var FormData = window['FormData'];",
            "function f() { return window['magic'] instanceof FormData; }",
            "console.log(f());"));
  }

  @Test
  public void testBug30868041() {
    testSame(
        lines(
            "function Base() {};",
            "/** @nosideeffects */",
            "Base.prototype.foo =  function() {",
            "}",
            "var x = new Base();",
            "x.foo()"));
  }

  @Test
  public void testGenerators() {
    test("function* g() {yield 1}", "");

    testSame("function* g() {yield 1} var g = g(); g.next().value()");
  }

  /**
   * Just check that we don't crash in this case.
   *
   * @bug 65489464
   */
  @Test
  public void testSpread() {
    test(
        lines("const ns = {};", "const X = [];", "ns.Y = [{}, ...X];"),
        lines("              ", "const X = [];", "       [{}, ...X];"));
  }

  // TODO(b/66971163): enable or remove this test
  public void disabledTestObjectDestructuring() {
    test(
        "var {a: a, x: y} = {a:1, x:2} ", // preserve newline
        "                   ({a:1,x:2})");

    test(
        "var {a: a, x: y} = {a:1, x:2}; f(a);", // preserve newline
        "var {a: a      } = {a:1, x:2}; f(a);");

    test(
        "var {a: a, x: y} = {a:1, x:2}; f(y);", // preserve newline
        "var {      x: y} = {a:1, x:2}; f(y);");

    test(
        "var {a: a, x: y = 3} = {a:1, x:2}; f(y);", // preserve newline
        "var {      x: y = 3} = {a:1, x:2}; f(y);");

    test(
        "var {a: a, x: y = 3} = {a:1}; f(y);", // preserve newline
        "var {      x: y = 3} = {a:1}; f(y);");

    test(
        "function f() {} var {a: a, x: y} = f();", // preserve newline
        "function f() {}                    f();");

    test(
        "function f() {} var {a: a, x: y} = f(); g(a)", // preserve newline
        "function f() {} var {a: a      } = f(); g(a)");

    test(
        "function f() {} var {a: a, x: y} = f(); g(y)", // preserve newline
        "function f() {} var {      x: y} = f(); g(y)");

    // complicated destructuring cases
    // TODO(blickly): Look into adding a pass to completely remove empty destructuring patterns
    test(
        "var {a: a, b: [{c: d}]} = o;", // preserve newline
        "var {      b: [{    }]} = o; ");

    test(
        "var {a: a, b: {c: d}} = o; f(d)", // preserve newline
        "var {      b: {c: d}} = o; f(d)");

    test(
        "var {a: a, b: [key]} = o;", // preserve newline
        "var {      b: [key]} = o;");

    test(
        "var {a: a, [key]: foo} = o;", // preserve newline
        "var {      [key]: foo} = o;");

    test(
        "var { a: a, b: { c: { d: y}}} = o", // preserve newline
        "var {       b: { c: {     }}} = o");

    test(
        "var {[foo()] : { p : x } } = o;", // preserve newline
        "var {[foo()] : {       } } = o;");

    testSame("var {x = foo()} = o;");

    testSame("var {p : x = foo()} = o;");
  }

  @Test
  public void testArrayDestructuring() {
    test(
        "var [a, b = 3, ...c] = [1, 2, 3]", // preserve newline
        "var [              ] = [1, 2, 3]");

    test(
        "var [a, b = 3, ...c] = [1, 2, 3]; use(b);", // preserve newline
        "var [ , b = 3      ] = [1, 2, 3]; use(b);");

    test(
        "var [a, b = 3, ...c] = [1, 2, 3]; use(c);", // preserve newline
        "var [ ,      , ...c] = [1, 2, 3]; use(c);");

    test(
        "var a, b, c; [a, b, ...c] = [1, 2, 3]", // preserve newline
        "             [          ] = [1, 2, 3]");

    test(
        "var [a, [b, [c, d]]] = [1, [2, [[[3, 4], 5], 6]]];", // preserve newline
        "var [ , [ , [    ]]] = [1, [2, [[[3, 4], 5], 6]]];");
  }

  @Test
  public void testBlock() {
    // Currently after normalization this becomes {var f = function f() {}}
    // Will no longer be able to be removed after that normalize change
    test("{function g() {}}", "{}");

    testSame("{function g() {} g()}");

    testSame("function g() {} {let a = g(); use(a)}");
  }

  @Test
  public void testTemplateLit() {
    test("let a = `hello`", "");
    test("var name = 'foo'; let a = `hello ${name}`", "");
    test(
        lines(
            "function Base() {}", // preserve newline
            "Base.prototype.foo =  `hello`;"),
        "");

    test(
        lines(
            "var bar = 'foo';",
            "function Base() {}", // preserve newline
            "Base.prototype.foo =  `foo ${bar}`;"),
        "");
  }
}
