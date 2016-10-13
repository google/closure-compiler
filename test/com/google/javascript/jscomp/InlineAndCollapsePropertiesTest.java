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

import static com.google.javascript.jscomp.CollapseProperties.UNSAFE_NAMESPACE_WARNING;

import com.google.javascript.rhino.Node;

/**
 * Tests for {@link AggressiveInlineAliases} plus {@link CollapseProperties}.
 *
 */

public final class InlineAndCollapsePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS =
      "var window;\n"
      + "function alert(s) {}\n"
      + "function parseInt(s) {}\n"
      + "/** @constructor */ function String() {};\n"
      + "var arguments";

  public InlineAndCollapsePropertiesTest() {
    super(EXTERNS);
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      AggressiveInlineAliases aggressiveInlineAliases = new AggressiveInlineAliases(compiler);
      CollapseProperties collapseProperties = new CollapseProperties(compiler);

      @Override
      public void process(Node externs, Node root) {
        aggressiveInlineAliases.process(externs, root);
        collapseProperties.process(externs, root);
      }
    };
  }

  @Override
  public void setUp() {
    enableNormalize();
    compareJsDoc = false;
  }

  @Override public int getNumRepetitions() {
    return 1;
  }

  public void testCollapse() {
    test("var a = {}; a.b = {}; var c = a.b;",
        "var c = null");

    test("var a = {}; a.b = {}; var c = a.b; use(c);",
        "var a$b = {}; var c = null; use(a$b);");

    testSame("var a = {}; /** @nocollapse */ a.b;");
  }

  public void testObjLitDeclaration() {
    test("var a = {b: {}, c: {}}; var d = a.b; var e = a.c",
        "var d = null; var e = null;");

    test("var a = {b: {}, c: {}}; var d = a.b; var e = a.c; use(d, e);",
        "var a$b = {}; var a$c = {}; var d = null; var e = null; use(a$b, a$c);");

    test("var a = {b: {}, /** @nocollapse */ c: {}}; var d = a.b; var e = a.c",
        "var a = {c: {}}; var d = null; var e = null;");

    test("var a = {b: {}, /** @nocollapse */ c: {}}; var d = a.b; var e = a.c; use(d, e);",
        "var a$b = {}; var a = {c: {}}; var d = null; var e = null; use(a$b, a.c);");
  }

  public void testObjLitDeclarationWithGet1() {
    testSame("var a = {get b(){}};");
  }

  public void testObjLitDeclarationWithGet2() {
    test("var a = {b: {}, get c(){}}; var d = a.b; var e = a.c;",
         "var a = {get c() {}}; var d=null; var e = a.c");

    test("var a = {b: {}, get c(){}}; var d = a.b; var e = a.c; use(d);",
         "var a$b = {};var a = {get c(){}};var d = null; var e = a.c; use(a$b)");
  }

  public void testObjLitDeclarationWithGet3() {
    test("var a = {b: {get c() { return 3; }}};",
         "var a$b = {get c() { return 3; }};");
  }

  public void testObjLitDeclarationWithSet1() {
    testSame("var a = {set b(a){}};");
  }

  public void testObjLitDeclarationWithSet2() {
    test("var a = {b: {}, set c(a){}}; var d = a.b; var e = a.c",
         "var a = {set c(a$jscomp$1){}}; var d=null; var e = a.c");

    test("var a = {b: {}, set c(a){}}; var d = a.b; var e = a.c; use(d);",
         "var a$b = {}; var a = {set c(a$jscomp$1){}}; var d=null; var e = a.c; use(a$b);");
  }

  public void testObjLitDeclarationWithSet3() {
    test("var a = {b: {set c(d) {}}};",
         "var a$b = {set c(d) {}};");
  }

  public void testObjLitDeclarationWithGetAndSet1() {
    test("var a = {b: {get c() { return 3; },set c(d) {}}};",
         "var a$b = {get c() { return 3; },set c(d) {}};");
  }

  public void testObjLitAssignmentDepth3() {
    test("var a = {}; a.b = {}; a.b.c = {d: 1, e: 2}; var f = a.b.c.d",
         "var a$b$c$d = 1; var a$b$c$e = 2; var f = null");

    test("var a = {}; a.b = {}; a.b.c = {d: 1, e: 2}; var f = a.b.c.d; use(f);",
         "var a$b$c$d = 1; var a$b$c$e = 2; var f = null; use(a$b$c$d);");

    test("var a = {}; a.b = {}; a.b.c = {d: 1, /** @nocollapse */ e: 2}; "
        + "var f = a.b.c.d; var g = a.b.c.e",
        "var a$b$c$d = 1; var a$b$c = {e: 2}; var f = null; var g = null");

    test("var a = {}; a.b = {}; a.b.c = {d: 1, /** @nocollapse */ e: 2}; "
        + "var f = a.b.c.d; var g = a.b.c.e; use(f, g);",
        "var a$b$c$d = 1; var a$b$c = {e: 2}; var f = null; var g = null; use(a$b$c$d, a$b$c.e);");

    testSame("var a = {}; /** @nocollapse*/ a.b = {}; "
        + "a.b.c = {d: 1, e: 2}; "
        + "var f = null; var g = null;");
  }

  public void testObjLitAssignmentDepth4() {
    test("var a = {}; a.b = {}; a.b.c = {}; a.b.c.d = {e: 1, f: 2}; var g = a.b.c.d.e;",
         "var a$b$c$d$e = 1; var a$b$c$d$f = 2; var g = null;");

    test("var a = {}; a.b = {}; a.b.c = {}; a.b.c.d = {e: 1, f: 2}; var g = a.b.c.d.e; use(g);",
         "var a$b$c$d$e = 1; var a$b$c$d$f = 2; var g = null; use(a$b$c$d$e);");
  }

  public void testAliasCreatedForObjectDepth1_1() {
    // An object's properties are not collapsed if the object is referenced
    // in a such a way that an alias is created for it, if that alias is used.
    test("var a = {b: 0}; var c = a; c.b = 1; a.b == c.b;",
        "var a$b = 0; var c = null; a$b = 1; a$b == a$b;");

    test("var a = {b: 0}; var c = a; c.b = 1; a.b == c.b; use(c);",
        "var a={b:0}; var c=null; a.b=1; a.b == a.b; use(a);");
  }

  public void testAliasCreatedForObjectDepth1_2() {
    testSame("var a = {b: 0}; f(a); a.b;");
  }

  public void testAliasCreatedForObjectDepth1_3() {
    testSame("var a = {b: 0}; new f(a); a.b;");
  }

  public void testMisusedConstructorTag() {
    test("var a = {}; var d = a; a.b = function() {};"
         + "/** @constructor */ a.b.c = 0; a.b.c;",
        "var d=null; var a$b=function(){}; var a$b$c=0; a$b$c;");
  }

  public void testAliasCreatedForCtorDepth1_1() {
    // A constructor's properties *are* collapsed even if the function is
    // referenced in a such a way that an alias is created for it,
    // since a function with custom properties is considered a class and its
    // non-prototype properties are considered static methods and variables.
    // People don't typically iterate through static members of a class or
    // refer to them using an alias for the class name.
    test("/** @constructor */ var a = function(){}; a.b = 1; "
         + "var c = a; c.b = 2; a.b == c.b;",
         "var a = function(){}; var a$b = 1; var c = null; a$b = 2; a$b == a$b;");

    // Sometimes we want to prevent static members of a constructor from
    // being collapsed.
    test("/** @constructor */ var a = function(){};"
        + "/** @nocollapse */ a.b = 1; var c = a; c.b = 2; a.b == c.b;",
        "/** @constructor */ var a = function(){};"
        + "/** @nocollapse */ a.b = 1; var c = null; a.b = 2; a.b == a.b;");
  }

  public void testAliasCreatedForFunctionDepth2() {
    test(
        "var a = {}; a.b = function() {}; a.b.c = 1; var d = a.b; a.b.c != d.c;",
        "var a$b = function() {}; var a$b$c = 1; var d = null; a$b$c != a$b$c;");

    test("var a = {}; a.b = function() {}; /** @nocollapse */ a.b.c = 1;"
        + "var d = a.b; a.b.c == d.c;",
        "var a$b = function() {}; a$b.c = 1; var d = null; a$b.c == a$b.c;");
  }

  public void testAliasCreatedForCtorDepth2() {
    test("var a = {}; /** @constructor */ a.b = function() {}; "
         + "a.b.c = 1; var d = a.b;"
         + "a.b.c == d.c;",
         "var a$b = function() {}; var a$b$c = 1; var d = null;"
         + "a$b$c == a$b$c;");

    test("var a = {}; /** @constructor */ a.b = function() {}; "
        + "/** @nocollapse */ a.b.c = 1; var d = a.b;"
        + "a.b.c == d.c;",
        "var a$b = function() {}; a$b.c = 1; var d = null;"
        + "a$b.c == a$b.c;");
  }

  public void testAliasCreatedForClassDepth1_1() {
    // A class's name is always collapsed, even if one of its prefixes is
    // referenced in such a way that an alias is created for it.
    test("var a = {}; /** @constructor */ a.b = function(){};"
         + "var c = a; c.b = 0; a.b != c.b;",
         "var a$b = function(){}; var c = null; a$b = 0; a$b != a$b;");

    test("var a = {}; /** @constructor @nocollapse */ a.b = function(){};"
        + "var c = 1; c = a; c.b = 0; a.b == c.b;",
        "var a = {}; a.b = function(){}; var c = 1; c = a; c.b = 0; a.b == c.b;",
        null, UNSAFE_NAMESPACE_WARNING);

    test("var a = {}; /** @constructor @nocollapse */ a.b = function(){};"
        + "var c = a; c.b = 0; a.b == c.b;",
        "var a = {}; a.b = function(){}; var c = null; a.b = 0; a.b == a.b;");

    test("var a = {}; /** @constructor @nocollapse */ a.b = function(){};"
        + "var c = a; c.b = 0; a.b == c.b; use(c);",
        "var a = {}; a.b = function(){}; var c = null; a.b = 0; a.b == a.b; use(a);",
        null, UNSAFE_NAMESPACE_WARNING);
  }

  public void testObjLitDeclarationUsedInSameVarList() {
    // The collapsed properties must be defined in the same place in the var list
    // where they were originally defined (and not, for example, at the end).
    test("var a = {b: {}, c: {}}; var d = a.b; var e = a.c; use(d, e);",
        "var a$b = {}; var a$c = {}; var d = null; var e = null; use(a$b, a$c);");

    test("var a = {b: {}, /** @nocollapse */ c: {}}; var d = a.b; var e = a.c; use(d, e);",
        "var a$b = {}; var a = {c: {}}; var d = null; var e = null; use(a$b, a.c);");
  }

  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth1() {
    test("var a = {}; var c = a; use(c); (function() {a.b = 0;})(); a.b;",
        "var a={}; var c=null; use(a); (function(){ a.b = 0; })(); a.b;");

    test("var a = {}; var c = 1; c = a; (function() {a.b = 0;})(); a.b;",
        "var a = {}; var c=1; c = a; (function(){a.b = 0;})(); a.b;");
  }

  public void testAddPropertyToUncollapsibleNamedCtorInLocalScopeDepth1() {
    test(
        "/** @constructor */ function a() {} var a$b; var c = a; "
        + "(function() {a$b = 0;})(); a$b;",
        "/** @constructor */ function a() {} var a$b; var c = null; "
        + "(function() {a$b = 0;})(); a$b;");
  }

  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth1() {
    test("/** @constructor */ var a = function() {}; var c = a; "
         + "(function() {a.b = 0;})(); a.b;",
         "var a = function() {}; var a$b; "
         + "var c = null; (function() {a$b = 0;})(); a$b;");
  }

  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth2() {
    test("var a = {}; a.b = {}; var d = a.b; use(d);"
         + "(function() {a.b.c = 0;})(); a.b.c;",
         "var a$b = {}; var d = null; use(a$b);"
         + "(function() {a$b.c = 0;})(); a$b.c;");
  }

  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth2() {
    test("var a = {}; /** @constructor */ a.b = function (){}; var d = a.b;"
         + "(function() {a.b.c = 0;})(); a.b.c;",
         "var a$b = function (){}; var a$b$c; var d = null;"
         + "(function() {a$b$c = 0;})(); a$b$c;");
  }

  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth1() {
    test("var a = {}; var c = a; a.b = function (){}; a.b.x = 0; a.b.x;",
        "var c = null; var a$b=function() {}; var a$b$x = 0; a$b$x;");

    test("var a = {}; var c = a; a.b = function (){}; a.b.x = 0; a.b.x; use(c);",
        "var a = {}; var c = null; a.b=function() {}; a.b.x = 0; a.b.x; use(a);");
  }

  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth2() {
    test("var a = {}; a.b = {}; var c = a.b; a.b.c = function (){}; a.b.c.x = 0; a.b.c.x;",
         "var c=null; var a$b$c = function(){}; var a$b$c$x = 0; a$b$c$x");

    test("var a = {}; a.b = {}; var c = a.b; a.b.c = function (){}; a.b.c.x = 0; a.b.c.x; use(c);",
         "var a$b = {}; var c=null; a$b.c = function(){}; a$b.c.x=0; a$b.c.x; use(a$b);");
  }

  public void testAddPropertyToChildFuncOfUncollapsibleObjectInLocalScope() {
    test("var a = {}; a.b = function (){}; a.b.x = 0;"
             + "var c = a; (function() {a.b.y = 1;})(); a.b.x; a.b.y;",
         "var a$b=function() {}; var a$b$y; var a$b$x = 0; var c=null;"
         + "(function() { a$b$y=1; })(); a$b$x; a$b$y");
  }

  public void testAddPropertyToChildTypeOfUncollapsibleObjectInLocalScope() {
    test(
        LINE_JOINER.join(
            "var a = {};",
            "/** @constructor */",
            "a.b = function (){};",
            "a.b.x = 0;",
            "var c = a;",
            "(function() {a.b.y = 1;})();",
            "a.b.x;",
            "a.b.y;"),
        LINE_JOINER.join(
            "var a$b = function (){};",
            "var a$b$y;",
            "var a$b$x = 0;",
            "var c = null;",
            "(function() {a$b$y = 1;})();",
            "a$b$x;",
            "a$b$y;"));

    test(
        LINE_JOINER.join(
            "var a = {};",
            "/** @constructor */",
            "a.b = function (){};",
            "a.b.x = 0;",
            "var c = a;",
            "(function() {a.b.y = 1;})();",
            "a.b.x;",
            "a.b.y;",
            "use(c);"),
        LINE_JOINER.join(
            "var a = {};",
            "var a$b = function (){};",
            "var a$b$y;",
            "var a$b$x = 0;",
            "var c = null;",
            "(function() {a$b$y = 1;})();",
            "a$b$x;",
            "a$b$y;",
            "use(a);"),
        null, UNSAFE_NAMESPACE_WARNING);
  }

  public void testAddPropertyToChildOfUncollapsibleFunctionInLocalScope() {
    test(
        "function a() {} a.b = {x: 0}; var c = a; (function() {a.b.y = 0;})(); a.b.y;",
        "function a() {} var a$b$x=0; var a$b$y; var c=null; (function(){a$b$y=0})(); a$b$y");
  }

  public void testAddPropertyToChildOfUncollapsibleCtorInLocalScope() {
    test("/** @constructor */ var a = function() {}; a.b = {x: 0}; var c = a;"
         + "(function() {a.b.y = 0;})(); a.b.y;",
         "var a = function() {}; var a$b$x = 0; var a$b$y; var c = null;"
         + "(function() {a$b$y = 0;})(); a$b$y;");
  }

  public void testFunctionAlias2() {
    test("var a = {}; a.b = {}; a.b.c = function(){}; a.b.d = a.b.c;use(a.b.d)",
         "var a$b$c = function(){}; var a$b$d = null;use(a$b$c);");
  }

  public void testLocalAlias1() {
    test("var a = {b: 3}; function f() { var x = a; f(x.b); }",
         "var a$b = 3; function f() { var x = null; f(a$b); }");
  }

  public void testLocalAlias2() {
    test("var a = {b: 3, c: 4}; function f() { var x = a; f(x.b); f(x.c);}",
         "var a$b = 3; var a$c = 4; "
         + "function f() { var x = null; f(a$b); f(a$c);}");
  }

  public void testLocalAlias3() {
    test("var a = {b: 3, c: {d: 5}}; "
         + "function f() { var x = a; f(x.b); f(x.c); f(x.c.d); }",
         "var a$b = 3; var a$c = {d: 5}; "
         + "function f() { var x = null; f(a$b); f(a$c); f(a$c.d);}");
  }

  public void testLocalAlias4() {
    test("var a = {b: 3}; var c = {d: 5}; "
         + "function f() { var x = a; var y = c; f(x.b); f(y.d); }",
         "var a$b = 3; var c$d = 5; "
         + "function f() { var x = null; var y = null; f(a$b); f(c$d);}");
  }

  public void testLocalAlias5() {
    test("var a = {b: {c: 5}}; "
         + "function f() { var x = a; var y = x.b; f(a.b.c); f(y.c); }",
         "var a$b$c = 5; "
         + "function f() { var x = null; var y = null; f(a$b$c); f(a$b$c);}");
  }

  public void testLocalAlias6() {
    test("var a = {b: 3}; function f() { var x = a; if (x.b) { f(x.b); } }",
         "var a$b = 3; function f() { var x = null; if (a$b) { f(a$b); } }");
  }

  public void testLocalAlias7() {
    test("var a = {b: {c: 5}}; function f() { var x = a.b; f(x.c); }",
         "var a$b$c = 5; function f() { var x = null; f(a$b$c); }");
  }

  public void testGlobalWriteToAncestor() {
    testSame("var a = {b: 3}; function f() { var x = a; f(a.b); } a = 5;");
  }

  public void testGlobalWriteToNonAncestor() {
    test("var a = {b: 3}; function f() { var x = a; f(a.b); } a.b = 5;",
         "var a$b = 3; function f() { var x = null; f(a$b); } a$b = 5;");
  }

  public void testLocalWriteToAncestor() {
    testSame("var a = {b: 3}; function f() { a = 5; var x = a; f(a.b); } ");
  }

  public void testLocalWriteToNonAncestor() {
    test("var a = {b: 3}; "
         + "function f() { a.b = 5; var x = a; f(a.b); }",
         "var a$b = 3; function f() { a$b = 5; var x = null; f(a$b); } ");
  }

  public void testLocalAliasOfAncestor() {
    testSame("var a = {b: {c: 5}}; function g() { f(a); } "
             + "function f() { var x = a.b; f(x.c); }");
  }

  public void testGlobalAliasOfAncestor() {
    test("var a = {b: {c: 5}}; function f() { var x = a.b; f(x.c); }",
        "var a$b$c=5; function f() {var x=null; f(a$b$c); }");
  }

  public void testLocalAliasOfOtherName() {
    testSame("var foo = function() { return {b: 3}; };"
             + "var a = foo(); a.b = 5; "
             + "function f() { var x = a.b; f(x); }");
  }

  public void testLocalAliasOfFunction() {
    test("var a = function() {}; a.b = 5; "
         + "function f() { var x = a.b; f(x); }",
         "var a = function() {}; var a$b = 5; "
         + "function f() { var x = null; f(a$b); }");
  }

  public void testNoInlineGetpropIntoCall() {
    test("var b = x; function f() { var a = b; a(); }",
         "var b = x; function f() { var a = null; b(); }");
    test("var b = {}; b.c = x; function f() { var a = b.c; a(); }",
         "var b$c = x; function f() { var a = null; b$c(); }");
  }

  public void testCommaOperator() {
    test("var a = {}; a.b = function() {}, a.b();",
         "var a$b; a$b=function() {}, a$b();");

    test(
        "var ns = {};\n"
        + "ns.Foo = {};\n"
        + "var Baz = {};\n"
        + "Baz.Foo = ns.Foo;\n"
        + "(Baz.Foo.bar = 10, 123);",

        "var Baz$Foo=null;\n"
        + "var ns$Foo$bar;\n"
        + "(ns$Foo$bar = 10, 123);");

    test(
        "var ns = {};\n"
        + "ns.Foo = {};\n"
        + "var Baz = {};\n"
        + "Baz.Foo = ns.Foo;\n"
        + "function f() { (Baz.Foo.bar = 10, 123); }",

        "var ns$Foo$bar;\n"
        + "var Baz$Foo=null;\n"
        + "function f() { (ns$Foo$bar = 10, 123); }");
  }

  public void testTypeDefAlias1() {
    test(
        "/** @constructor */ var D = function() {};\n"
        + "/** @constructor */ D.L = function() {};\n"
        + "/** @type {D.L} */ D.L.A = new D.L();\n"
        + "\n"
        + "/** @const */ var M = {};\n"
        + "/** @typedef {D.L} */ M.L = D.L;\n"
        + "\n"
        + "use(M.L.A);",

        "var D = function() {};\n"
        + "var D$L = function() {};\n"
        + "var D$L$A = new D$L();\n"
        + "var M$L = null\n"
        + "use(D$L$A);");
  }

  public void testGlobalAliasWithProperties1() {
    test("var ns = {}; "
        + "/** @constructor */ ns.Foo = function() {};\n"
        + "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};"
        + "/** @constructor */ ns.Bar = ns.Foo;\n"
        + "var x = function() {use(ns.Bar.EventType.A)};\n"
        + "use(x);",
        "var ns$Foo = function(){};"
        + "var ns$Foo$EventType$A = 1;"
        + "var ns$Foo$EventType$B = 2;"
        + "var ns$Bar = null;"
        + "var x = function(){use(ns$Foo$EventType$A)};"
        + "use(x);");
  }

  public void testGlobalAliasWithProperties2() {
    // Reassignment of properties was necessary to prevent invalid code in
    // previous iterations of this optimization.  Verify we don't break
    // code like this.  Now it causes a back-off of the collapsing because
    // the value is assigned more than once.
    test("var ns = {}; "
        + "/** @constructor */ ns.Foo = function() {};\n"
        + "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};"
        + "/** @constructor */ ns.Bar = ns.Foo;\n"
        + "/** @enum {number} */ ns.Bar.EventType = ns.Foo.EventType;\n"
        + "var x = function() {use(ns.Bar.EventType.A)};\n"
        + "use(x)",
        "var ns$Foo = function(){};"
        + "var ns$Foo$EventType = {A:1, B:2};"
        + "var ns$Bar = null;"
        + "ns$Foo$EventType = ns$Foo$EventType;\n"
        + "var x = function(){use(ns$Foo$EventType.A)};"
        + "use(x);");
  }

  public void testGlobalAliasWithProperties3() {
    test("var ns = {}; "
        + "/** @constructor */ ns.Foo = function() {};\n"
        + "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};"
        + "/** @constructor */ ns.Bar = ns.Foo;\n"
        + "/** @enum {number} */ ns.Bar.Other = {X:1, Y:2};\n"
        + "var x = function() {use(ns.Bar.Other.X)};\n"
        + "use(x)",
        "var ns$Foo=function(){};"
        + "var ns$Foo$EventType$A=1;"
        + "var ns$Foo$EventType$B=2;"
        + "var ns$Bar=null;"
        + "var ns$Foo$Other$X=1;"
        + "var ns$Foo$Other$Y=2;"
        + "var x=function(){use(ns$Foo$Other$X)};"
        + "use(x)\n");
  }

  public void testGlobalAliasWithProperties4() {
    testSame(""
        + "var nullFunction = function(){};\n"
        + "var blob = {};\n"
        + "blob.init = nullFunction;\n"
        + "use(blob)");
  }

  public void testGlobalAliasWithProperties5() {
    testSame(
        "/** @constructor */ var blob = function() {}",
        "var nullFunction = function(){};\n"
        + "blob.init = nullFunction;\n"
        + "use(blob.init)",
        null);
  }

  public void testLocalAliasOfEnumWithInstanceofCheck() {
    test(
        "/** @constructor */\n"
        + "var Enums = function() {\n"
        + "};\n"
        + "\n"
        + "/** @enum {number} */\n"
        + "Enums.Fruit = {\n"
        + " APPLE: 1,\n"
        + " BANANA: 2,\n"
        + "};\n"
        + "\n"
        + "function foo(f) {\n"
        + " if (f instanceof Enums) { alert('what?'); return; }\n"
        + "\n"
        + " var Fruit = Enums.Fruit;\n"
        + " if (f == Fruit.APPLE) alert('apple');\n"
        + " if (f == Fruit.BANANA) alert('banana');\n"
        + "}",
        "var Enums = function() {};\n"
        + "var Enums$Fruit$APPLE = 1;\n"
        + "var Enums$Fruit$BANANA = 2;\n"
        + "function foo(f) {\n"
        + " if (f instanceof Enums) { alert('what?'); return; }\n"
        + " var Fruit = null;\n"
        + " if (f == Enums$Fruit$APPLE) alert('apple');\n"
        + " if (f == Enums$Fruit$BANANA) alert('banana');\n"
        + "}");
  }

  public void testCollapsePropertiesOfClass1() {
    test(
        "/** @constructor */\n"
        + "var namespace = function() {};\n"
        + "goog.inherits(namespace, Object);\n"
        + "\n"
        + "namespace.includeExtraParam = true;\n"
        + "\n"
        + "/** @enum {number} */\n"
        + "namespace.Param = {\n"
        + "  param1: 1,\n"
        + "  param2: 2\n"
        + "};\n"
        + "\n"
        + "if (namespace.includeExtraParam) {\n"
        + "  namespace.Param.optParam = 3;\n"
        + "}\n"
        + "\n"
        + "function f() {\n"
        + "  var Param = namespace.Param;\n"
        + "  log(namespace.Param.optParam);\n"
        + "  log(Param.optParam);\n"
        + "}",
        "var namespace = function() {};\n"
        + "goog.inherits(namespace, Object);\n"
        + "var namespace$includeExtraParam = true;\n"
        + "var namespace$Param$param1 = 1;\n"
        + "var namespace$Param$param2 = 2;\n"
        + "if (namespace$includeExtraParam) {\n"
        + "  var namespace$Param$optParam = 3;\n"
        + "}\n"
        + "function f() {\n"
        + "  var Param = null;\n"
        + "  log(namespace$Param$optParam);\n"
        + "  log(namespace$Param$optParam);\n"
        + "}");
  }

  public void testCollapsePropertiesOfClass2() {
    test(
        "var goog = goog || {};\n"
        + "goog.addSingletonGetter = function(cls) {};\n"
        + "\n"
        + "var a = {};\n"
        + "\n"
        + "/** @constructor */\n"
        + "a.b = function() {};\n"
        + "goog.addSingletonGetter(a.b);\n"
        + "a.b.prototype.get = function(key) {};\n"
        + "\n"
        + "/** @constructor */\n"
        + "a.b.c = function() {};\n"
        + "a.b.c.XXX = new a.b.c();\n"
        + "\n"
        + "function f() {\n"
        + "  var x = a.b.getInstance();\n"
        + "  var Key = a.b.c;\n"
        + "  x.get(Key.XXX);\n"
        + "}",

        "var goog = goog || {};\n"
        + "var goog$addSingletonGetter = function(cls) {};\n"
        + "var a$b = function() {};\n"
        + "goog$addSingletonGetter(a$b);\n"
        + "a$b.prototype.get = function(key) {};\n"
        + "var a$b$c = function() {};\n"
        + "var a$b$c$XXX = new a$b$c();\n"
        + "\n"
        + "function f() {\n"
        + "  var x = a$b.getInstance();\n"
        + "  var Key = null;\n"
        + "  x.get(a$b$c$XXX);\n"
        + "}");
  }

  public void test_b19179602() {
    test(
        "var a = {};\n"
        + "/** @constructor */ a.b = function() {};\n"
        + "a.b.staticProp = 5;\n"
        + "function f() {\n"
        + "  while (true) {\n"
        // b is declared inside a loop, so it is reassigned multiple times
        + "    var b = a.b;\n"
        + "    alert(b.staticProp);\n"
        + "  }\n"
        + "}\n",

        "var a$b = function() {};\n"
        + "var a$b$staticProp = 5;\n"
        + "\n"
        + "function f() {\n"
        + "  while (true) {\n"
        + "    var b = a$b;\n"
        + "    alert(b.staticProp);\n"
        + "  }\n"
        + "}",
        null, AggressiveInlineAliases.UNSAFE_CTOR_ALIASING);
  }

  public void test_b19179602_declareOutsideLoop() {
    test(
        "var a = {};\n"
        + "/** @constructor */ a.b = function() {};\n"
        + "a.b.staticProp = 5;\n"
        + "function f() {\n"
        // b is declared outside the loop
        + "  var b = a.b;\n"
        + "  while (true) {\n"
        + "    alert(b.staticProp);\n"
        + "  }\n"
        + "}",

        "var a$b = function() {};\n"
        + "var a$b$staticProp = 5;\n"
        + "\n"
        + "function f() {\n"
        + "  var b = null;\n"
        + "  while (true) {\n"
        + "    alert(a$b$staticProp);\n"
        + "  }\n"
        + "}");
  }

  public void testCtorManyAssignmentsDontInlineWarn() {
    test(
        "var a = {};\n"
        + "/** @constructor */ a.b = function() {};\n"
        + "a.b.staticProp = 5;\n"
        + "function f(y, z) {\n"
        + "  var x = a.b;\n"
        + "  if (y) {\n"
        + "    x = z;\n"
        + "  }\n"
        + "  return x.staticProp;\n"
        + "}",

        "var a$b = function() {};\n"
        + "var a$b$staticProp = 5;\n"
        + "function f(y, z) {\n"
        + "  var x = a$b;\n"
        + "  if (y) {\n"
        + "    x = z;\n"
        + "  }\n"
        + "  return x.staticProp;\n"
        + "}",
        null, AggressiveInlineAliases.UNSAFE_CTOR_ALIASING);
  }

  public void testCodeGeneratedByGoogModule() {
    // The static property is added to the exports object
    test(
        "var $jscomp = {};\n" +
        "$jscomp.scope = {};\n" +
        "/** @constructor */\n" +
        "$jscomp.scope.Foo = function() {};\n" +
        "var exports = $jscomp.scope.Foo;\n" +
        "exports.staticprop = {A:1};\n" +
        "var y = exports.staticprop.A;",

        "var $jscomp$scope$Foo = function() {}\n" +
        "var exports = null;\n" +
        "var $jscomp$scope$Foo$staticprop$A = 1;\n" +
        "var y = $jscomp$scope$Foo$staticprop$A;");

    // The static property is added to the constructor
    test(
        "var $jscomp = {};\n" +
        "$jscomp.scope = {};\n" +
        "/** @constructor */\n" +
        "$jscomp.scope.Foo = function() {};\n" +
        "$jscomp.scope.Foo.staticprop = {A:1};\n" +
        "var exports = $jscomp.scope.Foo;\n" +
        "var y = exports.staticprop.A;",

        "var $jscomp$scope$Foo = function() {}\n" +
        "var $jscomp$scope$Foo$staticprop$A = 1;\n" +
        "var exports = null;\n" +
        "var y = $jscomp$scope$Foo$staticprop$A;");
  }

  public void testInlineCtorInObjLit() {
    compareJsDoc = true;

    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "",
            "/** @constructor */",
            "var Bar = Foo;",
            "",
            "var objlit = {",
            "  'prop' : Bar",
            "};"),
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "/** @constructor */",
            "var Bar = null;",
            "var objlit$prop = Foo;"));
  }

  public void testNoCollapseExportedNode() {
    test(
        "var x = {}; x.y = {}; var dontExportMe = x.y; use(dontExportMe);",
        "var x$y = {}; var dontExportMe = null; use(x$y);");

    test(
        "var x = {}; x.y = {}; var _exportMe = x.y;",
        "var x$y = {}; var _exportMe = x$y;");
  }

  public void testDontCrashCtorAliasWithEnum() {
    test(
        "var ns = {};\n"
        + "/** @constructor */\n"
        + "ns.Foo = function () {};\n"
        + "var Bar = ns.Foo;\n"
        + "/** @const @enum */\n"
        + "Bar.prop = { A: 1 };",

        "var ns$Foo = function(){};\n"
        + "var Bar = null;\n"
        + "var ns$Foo$prop$A = 1");
  }

  public void testDontCrashNamespaceAliasAcrossScopes() {
    test(
        "var ns = {};\n"
        + "ns.VALUE = 0.01;\n"
        + "function f() {\n"
        + "    var constants = ns;\n"
        + "    (function() {\n"
        + "       var x = constants.VALUE;\n"
        + "    })();\n"
        + "}",
        null);
  }
}
