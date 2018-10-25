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

import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link AggressiveInlineAliases} plus {@link CollapseProperties}.
 *
 */

@RunWith(JUnit4.class)
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
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      AggressiveInlineAliases aggressiveInlineAliases = new AggressiveInlineAliases(compiler);
      CollapseProperties collapseProperties =
          new CollapseProperties(compiler, PropertyCollapseLevel.ALL);

      @Override
      public void process(Node externs, Node root) {
        aggressiveInlineAliases.process(externs, root);
        collapseProperties.process(externs, root);
      }
    };
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
  }

  @Override protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testCollapse() {
    test("var a = {}; a.b = {}; var c = a.b;",
        "var c = null");

    test("var a = {}; a.b = {}; var c = a.b; use(c);",
        "var a$b = {}; var c = null; use(a$b);");

    testSame("var a = {}; /** @nocollapse */ a.b;");
  }

  @Test
  public void testObjLitDeclaration() {
    test("var a = {b: {}, c: {}}; var d = a.b; var e = a.c",
        "var d = null; var e = null;");

    test("var a = {b: {}, c: {}}; var d = a.b; var e = a.c; use(d, e);",
        "var a$b = {}; var a$c = {}; var d = null; var e = null; use(a$b, a$c);");

    test("var a = {b: {}, /** @nocollapse */ c: {}}; var d = a.b; var e = a.c",
        "var a = {/** @nocollapse */ c: {}}; var d = null; var e = null;");

    test("var a = {b: {}, /** @nocollapse */ c: {}}; var d = a.b; var e = a.c; use(d, e);",
        "var a$b = {};var a = {/** @nocollapse */ c: {}};var d = null;var e = null;use(a$b, a.c);");
  }

  @Test
  public void testObjLitDeclarationWithGet1() {
    testSame("var a = {get b(){}};");
  }

  @Test
  public void testObjLitDeclarationWithGet2() {
    test("var a = {b: {}, get c(){}}; var d = a.b; var e = a.c;",
         "var a = {get c() {}}; var d=null; var e = a.c");

    test("var a = {b: {}, get c(){}}; var d = a.b; var e = a.c; use(d);",
         "var a$b = {};var a = {get c(){}};var d = null; var e = a.c; use(a$b)");
  }

  @Test
  public void testObjLitDeclarationWithGet3() {
    test("var a = {b: {get c() { return 3; }}};",
         "var a$b = {get c() { return 3; }};");
  }

  @Test
  public void testObjLitDeclarationWithSet1() {
    testSame("var a = {set b(a){}};");
  }

  @Test
  public void testObjLitDeclarationWithSet2() {
    test("var a = {b: {}, set c(a){}}; var d = a.b; var e = a.c",
         "var a = {set c(a$jscomp$1){}}; var d=null; var e = a.c");

    test("var a = {b: {}, set c(a){}}; var d = a.b; var e = a.c; use(d);",
         "var a$b = {}; var a = {set c(a$jscomp$1){}}; var d=null; var e = a.c; use(a$b);");
  }

  @Test
  public void testObjLitDeclarationWithSet3() {
    test("var a = {b: {set c(d) {}}};",
         "var a$b = {set c(d) {}};");
  }

  @Test
  public void testObjLitDeclarationWithGetAndSet1() {
    test("var a = {b: {get c() { return 3; },set c(d) {}}};",
         "var a$b = {get c() { return 3; },set c(d) {}};");
  }

  @Test
  public void testObjLitAssignmentDepth3() {
    test("var a = {}; a.b = {}; a.b.c = {d: 1, e: 2}; var f = a.b.c.d",
         "var a$b$c$d = 1; var a$b$c$e = 2; var f = null");

    test("var a = {}; a.b = {}; a.b.c = {d: 1, e: 2}; var f = a.b.c.d; use(f);",
         "var a$b$c$d = 1; var a$b$c$e = 2; var f = null; use(a$b$c$d);");

    test("var a = {}; a.b = {}; a.b.c = {d: 1, /** @nocollapse */ e: 2}; "
        + "var f = a.b.c.d; var g = a.b.c.e",
        "var a$b$c$d = 1; var a$b$c = {/** @nocollapse */ e: 2}; var f = null; var g = null;");

    test("var a = {}; a.b = {}; a.b.c = {d: 1, /** @nocollapse */ e: 2}; "
        + "var f = a.b.c.d; var g = a.b.c.e; use(f, g);",
        "var a$b$c$d = 1; var a$b$c = { /** @nocollapse */ e: 2};"
        + "var f = null; var g = null; use(a$b$c$d, a$b$c.e);");

    testSame("var a = {}; /** @nocollapse*/ a.b = {}; "
        + "a.b.c = {d: 1, e: 2}; "
        + "var f = null; var g = null;");
  }

  @Test
  public void testObjLitAssignmentDepth4() {
    test("var a = {}; a.b = {}; a.b.c = {}; a.b.c.d = {e: 1, f: 2}; var g = a.b.c.d.e;",
         "var a$b$c$d$e = 1; var a$b$c$d$f = 2; var g = null;");

    test("var a = {}; a.b = {}; a.b.c = {}; a.b.c.d = {e: 1, f: 2}; var g = a.b.c.d.e; use(g);",
         "var a$b$c$d$e = 1; var a$b$c$d$f = 2; var g = null; use(a$b$c$d$e);");
  }

  @Test
  public void testHasOwnProperty() {
    test(
        "var a = {'b': 1, 'c': 1}; var alias = a;    alert(alias.hasOwnProperty('c'));",
        "var a = {'b': 1, 'c': 1}; var alias = null; alert(a.hasOwnProperty('c'));");
  }

  @Test
  public void testAliasCreatedForObjectDepth1_1() {
    // An object's properties are not collapsed if the object is referenced
    // in a such a way that an alias is created for it, if that alias is used.
    test("var a = {b: 0}; var c = a; c.b = 1; a.b == c.b;",
        "var a$b = 0; var c = null; a$b = 1; a$b == a$b;");

    test("var a = {b: 0}; var c = a; c.b = 1; a.b == c.b; use(c);",
        "var a={b:0}; var c=null; a.b=1; a.b == a.b; use(a);");
  }

  @Test
  public void testAliasCreatedForObjectDepth1_2() {
    testSame("var a = {b: 0}; f(a); a.b;");
  }

  @Test
  public void testAliasCreatedForObjectDepth1_3() {
    testSame("var a = {b: 0}; new f(a); a.b;");
  }

  @Test
  public void testMisusedConstructorTag() {
    test("var a = {}; var d = a; a.b = function() {};"
         + "/** @constructor */ a.b.c = 0; a.b.c;",
        "var d=null; var a$b=function(){}; /** @constructor */ var a$b$c=0; a$b$c;");
  }

  @Test
  public void testAliasCreatedForCtorDepth1_1() {
    // A constructor's properties *are* collapsed even if the function is
    // referenced in a such a way that an alias is created for it,
    // since a function with custom properties is considered a class and its
    // non-prototype properties are considered static methods and variables.
    // People don't typically iterate through static members of a class or
    // refer to them using an alias for the class name.
    test("/** @constructor */ var a = function(){}; a.b = 1; "
         + "var c = a; c.b = 2; a.b == c.b;",
         "/** @constructor */ var a = function(){}; var a$b = 1;"
         + "var c = null; a$b = 2; a$b == a$b;");

    // Sometimes we want to prevent static members of a constructor from
    // being collapsed.
    test("/** @constructor */ var a = function(){};"
        + "/** @nocollapse */ a.b = 1; var c = a; c.b = 2; a.b == c.b;",
        "/** @constructor */ var a = function(){};"
        + "/** @nocollapse */ a.b = 1; var c = null; a.b = 2; a.b == a.b;");
  }

  @Test
  public void testAliasCreatedForFunctionDepth2() {
    test(
        "var a = {}; a.b = function() {}; a.b.c = 1; var d = a.b; a.b.c != d.c;",
        "var a$b = function() {}; var a$b$c = 1; var d = null; a$b$c != a$b$c;");

    test("var a = {}; a.b = function() {}; /** @nocollapse */ a.b.c = 1;"
        + "var d = a.b; a.b.c == d.c;",
        "var a$b = function() {}; /** @nocollapse */ a$b.c = 1; var d = null; a$b.c == a$b.c;");
  }

  @Test
  public void testAliasCreatedForCtorDepth2() {
    test("var a = {}; /** @constructor */ a.b = function() {}; a.b.c = 1; var d = a.b;"
         + "a.b.c == d.c;",
         "/** @constructor */ var a$b = function() {}; var a$b$c = 1; var d = null;"
         + "a$b$c == a$b$c;");

    test("var a = {}; /** @constructor */ a.b = function() {}; "
        + "/** @nocollapse */ a.b.c = 1; var d = a.b;"
        + "a.b.c == d.c;",
        "/** @constructor */ var a$b = function() {}; /** @nocollapse */ a$b.c = 1; var d = null;"
        + "a$b.c == a$b.c;");
  }

  @Test
  public void testAliasCreatedForClassDepth1_1() {
    // A class's name is always collapsed, even if one of its prefixes is
    // referenced in such a way that an alias is created for it.
    test("var a = {}; /** @constructor */ a.b = function(){};"
         + "var c = a; c.b = 0; a.b != c.b;",
         "/** @constructor */ var a$b = function(){}; var c = null; a$b = 0; a$b != a$b;");

    test(
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};",
            "var c = 1; c = a; c.b = 0; a.b == c.b;"),
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};",
            "var c = 1; c = a; c.b = 0; a.b == c.b;"),
        warning(UNSAFE_NAMESPACE_WARNING));

    test("var a = {}; /** @constructor @nocollapse */ a.b = function(){};"
        + "var c = a; c.b = 0; a.b == c.b;",
        "var a = {}; /** @constructor @nocollapse */ a.b = function(){};"
        + "var c = null; a.b = 0; a.b == a.b;");

    test(
        "var a = {}; /** @constructor @nocollapse */ a.b = function(){};"
        + "var c = a; c.b = 0; a.b == c.b; use(c);",
        "var a = {}; /** @constructor @nocollapse */ a.b = function(){};"
        + "var c = null; a.b = 0; a.b == a.b; use(a);",
        warning(UNSAFE_NAMESPACE_WARNING));
  }

  @Test
  public void testObjLitDeclarationUsedInSameVarList() {
    // The collapsed properties must be defined in the same place in the var list
    // where they were originally defined (and not, for example, at the end).
    test("var a = {b: {}, c: {}}; var d = a.b; var e = a.c; use(d, e);",
        "var a$b = {}; var a$c = {}; var d = null; var e = null; use(a$b, a$c);");

    test("var a = {b: {}, /** @nocollapse */ c: {}}; var d = a.b; var e = a.c; use(d, e);",
        "var a$b = {};var a = {/** @nocollapse */ c: {}};var d = null;var e = null;use(a$b, a.c);");
  }

  @Test
  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth1() {
    test("var a = {}; var c = a; use(c); (function() {a.b = 0;})(); a.b;",
        "var a={}; var c=null; use(a); (function(){ a.b = 0; })(); a.b;");

    test("var a = {}; var c = 1; c = a; (function() {a.b = 0;})(); a.b;",
        "var a = {}; var c=1; c = a; (function(){a.b = 0;})(); a.b;");
  }

  @Test
  public void testAddPropertyToUncollapsibleNamedCtorInLocalScopeDepth1() {
    test(
        "/** @constructor */ function a() {} var a$b; var c = a; "
        + "(function() {a$b = 0;})(); a$b;",
        "/** @constructor */ function a() {} var a$b; var c = null; "
        + "(function() {a$b = 0;})(); a$b;");
  }

  @Test
  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth1() {
    test("/** @constructor */ var a = function() {}; var c = a; "
         + "(function() {a.b = 0;})(); a.b;",
         "/** @constructor */ var a = function() {}; var a$b; "
         + "var c = null; (function() {a$b = 0;})(); a$b;");
  }

  @Test
  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth2() {
    test("var a = {}; a.b = {}; var d = a.b; use(d);"
         + "(function() {a.b.c = 0;})(); a.b.c;",
         "var a$b = {}; var d = null; use(a$b);"
         + "(function() {a$b.c = 0;})(); a$b.c;");
  }

  @Test
  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth2() {
    test("var a = {}; /** @constructor */ a.b = function (){}; var d = a.b;"
         + "(function() {a.b.c = 0;})(); a.b.c;",
         "/** @constructor */ var a$b = function (){}; var a$b$c; var d = null;"
         + "(function() {a$b$c = 0;})(); a$b$c;");
  }

  @Test
  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth1() {
    test("var a = {}; var c = a; a.b = function (){}; a.b.x = 0; a.b.x;",
        "var c = null; var a$b=function() {}; var a$b$x = 0; a$b$x;");

    test("var a = {}; var c = a; a.b = function (){}; a.b.x = 0; a.b.x; use(c);",
        "var a = {}; var c = null; a.b=function() {}; a.b.x = 0; a.b.x; use(a);");
  }

  @Test
  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth2() {
    test("var a = {}; a.b = {}; var c = a.b; a.b.c = function (){}; a.b.c.x = 0; a.b.c.x;",
         "var c=null; var a$b$c = function(){}; var a$b$c$x = 0; a$b$c$x");

    test("var a = {}; a.b = {}; var c = a.b; a.b.c = function (){}; a.b.c.x = 0; a.b.c.x; use(c);",
         "var a$b = {}; var c=null; a$b.c = function(){}; a$b.c.x=0; a$b.c.x; use(a$b);");
  }

  @Test
  public void testAddPropertyToChildFuncOfUncollapsibleObjectInLocalScope() {
    test("var a = {}; a.b = function (){}; a.b.x = 0;"
             + "var c = a; (function() {a.b.y = 1;})(); a.b.x; a.b.y;",
         "var a$b=function() {}; var a$b$y; var a$b$x = 0; var c=null;"
         + "(function() { a$b$y=1; })(); a$b$x; a$b$y");
  }

  @Test
  public void testAddPropertyToChildTypeOfUncollapsibleObjectInLocalScope() {
    test(
        lines(
            "var a = {};",
            "/** @constructor */",
            "a.b = function (){};",
            "a.b.x = 0;",
            "var c = a;",
            "(function() {a.b.y = 1;})();",
            "a.b.x;",
            "a.b.y;"),
        lines(
            "/** @constructor */",
            "var a$b = function (){};",
            "var a$b$y;",
            "var a$b$x = 0;",
            "var c = null;",
            "(function() {a$b$y = 1;})();",
            "a$b$x;",
            "a$b$y;"));

    test(
        lines(
            "var a = {};",
            "/** @constructor */",
            "a.b = function (){};",
            "a.b.x = 0;",
            "var c = a;",
            "(function() {a.b.y = 1;})();",
            "a.b.x;",
            "a.b.y;",
            "use(c);"),
        lines(
            "var a = {};",
            "/** @constructor */",
            "var a$b = function (){};",
            "var a$b$y;",
            "var a$b$x = 0;",
            "var c = null;",
            "(function() {a$b$y = 1;})();",
            "a$b$x;",
            "a$b$y;",
            "use(a);"),
        warning(UNSAFE_NAMESPACE_WARNING));
  }

  @Test
  public void testAddPropertyToChildOfUncollapsibleFunctionInLocalScope() {
    test(
        "function a() {} a.b = {x: 0}; var c = a; (function() {a.b.y = 0;})(); a.b.y;",
        "function a() {} var a$b$x=0; var a$b$y; var c=null; (function(){a$b$y=0})(); a$b$y");
  }

  @Test
  public void testAddPropertyToChildOfUncollapsibleCtorInLocalScope() {
    test("/** @constructor */ var a = function() {}; a.b = {x: 0}; var c = a;"
         + "(function() {a.b.y = 0;})(); a.b.y;",
         "/** @constructor */ var a = function() {}; var a$b$x = 0; var a$b$y; var c = null;"
         + "(function() {a$b$y = 0;})(); a$b$y;");
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration1() {
    test(
        lines(
            "var a = {b: 3};",
            "function f() {",
            "  var tmp;",
            "  tmp = a;",
            "  use(tmp.b);",
            "}"),
        lines(
            "var a$b = 3",
            "function f() {",
            "  var tmp;",
            "  tmp = null;",
            "  use(a$b);",
            "}"));
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration2() {
    test(
        lines(
            "var a = {b: 3}",
            "function f() {",
            "  var tmp;",
            "  if (true) {",
            "    tmp = a;",
            "    use(tmp.b);",
            "  }",
            "}"),
        lines(
            "var a$b = 3;",
            "function f() {",
            "  var tmp;",
            "  if (true) {",
            "    tmp = null;",
            "    use(a$b);",
            "  }",
            "}"));
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration3() {
    testSame(
        lines(
            "var a = { b : 3 };",
            "function f() {",
            "  var tmp;",
            "  if (true) {",
            "    tmp = a;",
            "  }",
            "  use(tmp);",
            "}"));
  }

  @Test
  public void testPartialLocalCtorAlias() {
    testWarning(
        lines(
            "/** @constructor */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  var tmp;",
            "  if (g()) {",
            "    use(tmp.doSomething);",
            "    tmp = Main;",
            "    tmp.doSomething(5);",
            "  }",
            "  use(tmp.doSomething);", // can't inline this use of tmp.
            "}"),
        AggressiveInlineAliases.UNSAFE_CTOR_ALIASING);
  }

  @Test
  public void testFunctionAlias2() {
    test("var a = {}; a.b = {}; a.b.c = function(){}; a.b.d = a.b.c;use(a.b.d)",
         "var a$b$c = function(){}; var a$b$d = null;use(a$b$c);");
  }

  @Test
  public void testLocalAlias1() {
    test("var a = {b: 3}; function f() { var x = a; f(x.b); }",
         "var a$b = 3; function f() { var x = null; f(a$b); }");
  }

  @Test
  public void testLocalAlias2() {
    test("var a = {b: 3, c: 4}; function f() { var x = a; f(x.b); f(x.c);}",
         "var a$b = 3; var a$c = 4; "
         + "function f() { var x = null; f(a$b); f(a$c);}");
  }

  @Test
  public void testLocalAlias3() {
    test("var a = {b: 3, c: {d: 5}}; "
         + "function f() { var x = a; f(x.b); f(x.c); f(x.c.d); }",
         "var a$b = 3; var a$c = {d: 5}; "
         + "function f() { var x = null; f(a$b); f(a$c); f(a$c.d);}");
  }

  @Test
  public void testLocalAlias4() {
    test("var a = {b: 3}; var c = {d: 5}; "
         + "function f() { var x = a; var y = c; f(x.b); f(y.d); }",
         "var a$b = 3; var c$d = 5; "
         + "function f() { var x = null; var y = null; f(a$b); f(c$d);}");
  }

  @Test
  public void testLocalAlias5() {
    test("var a = {b: {c: 5}}; "
         + "function f() { var x = a; var y = x.b; f(a.b.c); f(y.c); }",
         "var a$b$c = 5; "
         + "function f() { var x = null; var y = null; f(a$b$c); f(a$b$c);}");
  }

  @Test
  public void testLocalAlias6() {
    test("var a = {b: 3}; function f() { var x = a; if (x.b) { f(x.b); } }",
         "var a$b = 3; function f() { var x = null; if (a$b) { f(a$b); } }");
  }

  @Test
  public void testLocalAlias7() {
    test("var a = {b: {c: 5}}; function f() { var x = a.b; f(x.c); }",
         "var a$b$c = 5; function f() { var x = null; f(a$b$c); }");
  }

  @Test
  public void testLocalAlias8() {
    testSame(
        "var a = { b: 3 }; function f() { if (true) { var x = a; f(x.b); } x = { b : 4}; }");
  }

  @Test
  public void testGlobalWriteToAncestor() {
    testSame("var a = {b: 3}; function f() { var x = a; f(a.b); } a = 5;");
  }

  @Test
  public void testGlobalWriteToNonAncestor() {
    test("var a = {b: 3}; function f() { var x = a; f(a.b); } a.b = 5;",
         "var a$b = 3; function f() { var x = null; f(a$b); } a$b = 5;");
  }

  @Test
  public void testLocalWriteToAncestor() {
    testSame("var a = {b: 3}; function f() { a = 5; var x = a; f(a.b); } ");
  }

  @Test
  public void testLocalWriteToNonAncestor() {
    test("var a = {b: 3}; "
         + "function f() { a.b = 5; var x = a; f(a.b); }",
         "var a$b = 3; function f() { a$b = 5; var x = null; f(a$b); } ");
  }

  @Test
  public void testLocalAliasOfAncestor() {
    testSame("var a = {b: {c: 5}}; function g() { f(a); } "
             + "function f() { var x = a.b; f(x.c); }");
  }

  @Test
  public void testGlobalAliasOfAncestor() {
    test("var a = {b: {c: 5}}; function f() { var x = a.b; f(x.c); }",
        "var a$b$c=5; function f() {var x=null; f(a$b$c); }");
  }

  @Test
  public void testLocalAliasOfOtherName() {
    testSame("var foo = function() { return {b: 3}; };"
             + "var a = foo(); a.b = 5; "
             + "function f() { var x = a.b; f(x); }");
  }

  @Test
  public void testLocalAliasOfFunction() {
    test("var a = function() {}; a.b = 5; "
         + "function f() { var x = a.b; f(x); }",
         "var a = function() {}; var a$b = 5; "
         + "function f() { var x = null; f(a$b); }");
  }

  @Test
  public void testNoInlineGetpropIntoCall() {
    test("var b = x; function f() { var a = b; a(); }",
         "var b = x; function f() { var a = null; b(); }");
    test("var b = {}; b.c = x; function f() { var a = b.c; a(); }",
         "var b$c = x; function f() { var a = null; b$c(); }");
  }

  @Test
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

  @Test
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

        "/** @constructor */ var D = function() {};\n"
        + "/** @constructor */ var D$L = function() {};\n"
        + "/** @type {D.L} */ var D$L$A = new D$L();\n"
        + "/** @typedef {D.L} */ var M$L = null\n"
        + "use(D$L$A);");
  }

  @Test
  public void testGlobalAliasWithProperties1() {
    test("var ns = {}; "
        + "/** @constructor */ ns.Foo = function() {};\n"
        + "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};"
        + "/** @constructor */ ns.Bar = ns.Foo;\n"
        + "var x = function() {use(ns.Bar.EventType.A)};\n"
        + "use(x);",

        "/** @constructor */ var ns$Foo = function(){};"
        + "var ns$Foo$EventType$A = 1;"
        + "var ns$Foo$EventType$B = 2;"
        + "/** @constructor */ var ns$Bar = null;"
        + "var x = function(){use(ns$Foo$EventType$A)};"
        + "use(x);");
  }

  @Test
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

        "/** @constructor */ var ns$Foo = function(){};"
        + "/** @enum {number} */ var ns$Foo$EventType = {A:1, B:2};"
        + "/** @constructor */ var ns$Bar = null;"
        + "/** @enum {number} */ ns$Foo$EventType = ns$Foo$EventType;\n"
        + "var x = function(){use(ns$Foo$EventType.A)};"
        + "use(x);");
  }

  @Test
  public void testGlobalAliasWithProperties3() {
    test("var ns = {}; "
        + "/** @constructor */ ns.Foo = function() {};\n"
        + "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};"
        + "/** @constructor */ ns.Bar = ns.Foo;\n"
        + "/** @enum {number} */ ns.Bar.Other = {X:1, Y:2};\n"
        + "var x = function() {use(ns.Bar.Other.X)};\n"
        + "use(x)",

        "/** @constructor */ var ns$Foo=function(){};"
        + "var ns$Foo$EventType$A=1;"
        + "var ns$Foo$EventType$B=2;"
        + "/** @constructor */ var ns$Bar=null;"
        + "var ns$Foo$Other$X=1;"
        + "var ns$Foo$Other$Y=2;"
        + "var x=function(){use(ns$Foo$Other$X)};"
        + "use(x)\n");
  }

  @Test
  public void testGlobalAliasWithProperties4() {
    testSame(""
        + "var nullFunction = function(){};\n"
        + "var blob = {};\n"
        + "blob.init = nullFunction;\n"
        + "use(blob)");
  }

  @Test
  public void testGlobalAlias_propertyOnExternedConstructor_isNotChanged() {
    testSame(
        externs("/** @constructor */ var blob = function() {}"),
        srcs(
            "var nullFunction = function(){};\n"
                + "blob.init = nullFunction;\n"
                + "use(blob.init)"));
  }

  @Test
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
        "/** @constructor */\n"
        + "var Enums = function() {};\n"
        + "var Enums$Fruit$APPLE = 1;\n"
        + "var Enums$Fruit$BANANA = 2;\n"
        + "function foo(f) {\n"
        + " if (f instanceof Enums) { alert('what?'); return; }\n"
        + " var Fruit = null;\n"
        + " if (f == Enums$Fruit$APPLE) alert('apple');\n"
        + " if (f == Enums$Fruit$BANANA) alert('banana');\n"
        + "}");
  }

  @Test
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
        "/** @constructor */\n"
        + "var namespace = function() {};\n"
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

  @Test
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
        + "/** @constructor */\n"
        + "var a$b = function() {};\n"
        + "goog$addSingletonGetter(a$b);\n"
        + "a$b.prototype.get = function(key) {};\n"
        + "/** @constructor */\n"
        + "var a$b$c = function() {};\n"
        + "var a$b$c$XXX = new a$b$c();\n"
        + "\n"
        + "function f() {\n"
        + "  var x = a$b.getInstance();\n"
        + "  var Key = null;\n"
        + "  x.get(a$b$c$XXX);\n"
        + "}");
  }

  @Test
  public void test_b19179602() {
    // Note that this only collapses a.b.staticProp because a.b is a constructor.
    // Normally AggressiveInlineAliases would not inline "b" inside the loop.
    test(
        lines(
            "var a = {};",
            "/** @constructor */ a.b = function() {};",
            "a.b.staticProp = 5;",
            "function f() {",
            "  while (true) {",
            // b is declared inside a loop, so it is reassigned multiple times
            "    var b = a.b;",
            "    alert(b.staticProp);",
            "  }",
            "}"),
        lines(
            "/** @constructor */ var a$b = function() {};",
            "var a$b$staticProp = 5;",
            "function f() {",
            "  while (true) {",
            "    var b = null;",
            "    alert(a$b$staticProp);",
            "  }",
            "}"));
  }

  @Test
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

        "/** @constructor */"
        + "var a$b = function() {};\n"
        + "var a$b$staticProp = 5;\n"
        + "\n"
        + "function f() {\n"
        + "  var b = null;\n"
        + "  while (true) {\n"
        + "    alert(a$b$staticProp);\n"
        + "  }\n"
        + "}");
  }

  @Test
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
        + "}", "/** @constructor */ var a$b = function() {};\n"
        + "var a$b$staticProp = 5;\n"
        + "function f(y, z) {\n"
        + "  var x = a$b;\n"
        + "  if (y) {\n"
        + "    x = z;\n"
        + "  }\n"
        + "  return x.staticProp;\n"
        + "}",
        warning(AggressiveInlineAliases.UNSAFE_CTOR_ALIASING));
  }

  @Test
  public void testCodeGeneratedByGoogModule() {
    // The static property is added to the exports object
    test(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "/** @constructor */",
            "$jscomp.scope.Foo = function() {};",
            "var exports = $jscomp.scope.Foo;",
            "exports.staticprop = {A:1};",
            "var y = exports.staticprop.A;"),
        lines(
            "/** @constructor */",
            "var $jscomp$scope$Foo = function() {}",
            "var exports = null;",
            "var $jscomp$scope$Foo$staticprop$A = 1;",
            "var y = null;"));

    // The static property is added to the constructor
    test(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "/** @constructor */",
            "$jscomp.scope.Foo = function() {};",
            "$jscomp.scope.Foo.staticprop = {A:1};",
            "var exports = $jscomp.scope.Foo;",
            "var y = exports.staticprop.A;"),
        lines(
            "/** @constructor */",
            "var $jscomp$scope$Foo = function() {}",
            "var $jscomp$scope$Foo$staticprop$A = 1;",
            "var exports = null;",
            "var y = null;"));
  }

  @Test
  public void testInlineCtorInObjLit() {
    test(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "",
            "/** @constructor */",
            "var Bar = Foo;",
            "",
            "var objlit = {",
            "  'prop' : Bar",
            "};"),
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "/** @constructor */",
            "var Bar = null;",
            "var objlit$prop = Foo;"));
  }

  @Test
  public void testNoCollapseExportedNode() {
    test(
        "var x = {}; x.y = {}; var dontExportMe = x.y; use(dontExportMe);",
        "var x$y = {}; var dontExportMe = null; use(x$y);");

    test(
        "var x = {}; x.y = {}; var _exportMe = x.y;",
        "var x$y = {}; var _exportMe = x$y;");
  }

  @Test
  public void testDontCrashCtorAliasWithEnum() {
    test(
        "var ns = {};\n"
        + "/** @constructor */\n"
        + "ns.Foo = function () {};\n"
        + "var Bar = ns.Foo;\n"
        + "/** @const @enum */\n"
        + "Bar.prop = { A: 1 };",

        "/** @constructor */\n"
        + "var ns$Foo = function(){};\n"
        + "var Bar = null;\n"
        + "var ns$Foo$prop$A = 1");
  }

  @Test
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

  @Test
  public void testGlobalAliasWithLet() {
    test(
        "let ns = {}; ns.foo = 'bar'; let ns2 = ns; use(ns2.foo);",
        "var ns$foo = 'bar'; let ns2 = null; use(ns$foo);");
  }

  @Test
  public void testGlobalAliasForLet2() {
    // We don't do any sort of branch prediction, so can't collapse here.
    testSame(
        lines(
            "let ns = {};",
            "ns.foo = 'bar';",
            "let ns2;",
            "if (true) {",
            "  ns2 = ns;",
            "}",
            "use(ns2.foo);"));
  }

  @Test
  public void testGlobalAliasWithLet3() {
    // Back off since in general we don't check that ns2 is only ever accessed within the if block.
    testSame(
        lines(
            "let ns = {};",
            "ns.foo = 'bar';",
            "let ns2;",
            "if (true) {",
            "  ns2 = ns;",
            "  use(ns2.foo);",
            "}"));
  }

  @Test
  public void testLocalAliasWithLet1() {
    test(
        "let ns = {}; ns.foo = 'bar'; function f() { let ns2 = ns; use(ns2.foo); }",
        "var ns$foo = 'bar'; function f() { let ns2 = null; use(ns$foo); }");
  }

  @Test
  public void testLocalAliasWithLet2() {
    test(
        lines(
            "let ns = {};",
            "ns.foo = 'bar';",
            "function f() {",
            "  if (true) {",
            "    let ns2 = ns;",
            "    use(ns2.foo);",
            "  }",
            "}"),
        lines(
            "var ns$foo = 'bar';",
            "function f() {",
            "  if (true) {",
            "    let ns2 = null;",
            "    use(ns$foo);",
            "  }",
            "}"));
  }

  @Test
  public void testLocalAliasWithLet3() {
    test(
        lines(
            "let ns = {};",
            "ns.foo = 'bar';",
            "if (true) {",
            "  let ns2 = ns;",
            "  use(ns2.foo);",
            "}"),
        lines(
            "var ns$foo = 'bar';",
            "if (true) {",
            "  let ns2 = null;",
            "  use(ns$foo);", "}"));
  }

  @Test
  public void testLocalAliasWithLet4() {
    test(
        lines(
            "let ns = {};",
            "ns.foo = 'bar';",
            "if (true) {",
            "  let baz = ns.foo;",
            "  use(baz);",
            "}"),
        lines(
            "var ns$foo = 'bar';",
            "if (true) {",
            "  let baz = null;",
            "  use(ns$foo);", "}"));
  }

  @Test
  public void testLocalAliasWithLet5() {
    // For local variables (VAR, CONST, or LET) we only handle cases where the alias is a variable,
    // not a property.
    testSame(
        lines(
            "let ns = {};",
            "ns.foo = 'bar';",
            "if (true) {",
            "  let ns2 = {};",
            "  ns2.baz = ns;",
            "  use(ns2.baz.foo);",
            "}"));
  }

  @Test
  public void testGlobalAliasWithConst() {
    test(
        "const ns = {}; ns.foo = 'bar'; const ns2 = ns; use(ns2.foo);",
        "var ns$foo = 'bar'; const ns2 = null; use(ns$foo);");
  }

  @Test
  public void testLocalAliasWithConst1() {
    test(
        lines(
            "const ns = {};",
            "ns.foo = 'bar';",
            "function f() { const ns2 = ns;",
            "use(ns2.foo); }"),
        lines(
            "var ns$foo = 'bar';",
            "function f() {",
            "const ns2 = null;",
            "use(ns$foo);", "}"));
  }

  @Test
  public void testLocalAliasWithConst2() {
    test(
        lines(
            "const ns = {};",
            "ns.foo = 'bar';",
            "function f() {",
            "  if (true) {",
            "    const ns2 = ns;",
            "    use(ns2.foo);",
            "  }",
            "}"),
        lines(
            "var ns$foo = 'bar';",
            "function f() {",
            "  if (true) {",
            "    const ns2 = null;",
            "    use(ns$foo);",
            "  }",
            "}"));
  }

  @Test
  public void testLocalAliasWithConst3() {
    test(
        lines(
            "const ns = {};",
            "ns.foo = 'bar';",
            "if (true) {",
            "  const ns2 = ns;",
            "  use(ns2.foo);",
            "}"),
        lines(
            "var ns$foo = 'bar';",
            "if (true) {",
            "  const ns2 = null;",
            "  use(ns$foo);",
            "}"));
  }

  @Test
  public void testLocalAliasWithConst4() {
    test(
        lines(
            "const ns = {};",
            "ns.foo = 'bar';",
            "if (true) {",
            "  const baz = ns.foo;",
            "  use(baz);",
            "}"),
        lines(
            "var ns$foo = 'bar';",
            "if (true) {",
            "  const baz = null;",
            "  use(ns$foo);",
            "}"));
  }

  @Test
  public void testLocalAliasWithConst5() {
    // For local variables (VAR, CONST, or LET) we only handle cases where the alias is a variable,
    // not a property.
    testSame(
        lines(
            "const ns = {};",
            "ns.foo = 'bar';",
            "if (true) {",
            "  const ns2 = {};",
            "  ns2.baz = ns;",
            "  use(ns2.baz.foo);",
            "}"));
  }

  @Test
  public void testLocalAliasInsideClass() {
    test(
        "var a = {b: 5}; class A { fn() { var c = a; use(a.b); } }",
        "var a$b = 5; class A { fn() { var c = null; use(a$b); } }");
  }

  @Test
  public void testEs6ClassStaticProperties() {
    // Collapsing static properties (A.foo in this case) is known to be unsafe.
    test(
        lines(
            "class A {",
            "  static useFoo() {",
            "    alert(this.foo);",
            "  }",
            "}",
            "A.foo = 'bar';",
            "const B = A;",
            "B.foo = 'baz';",
            "B.useFoo();"),
        lines(
            "class A {",
            "static useFoo() {",
            "alert(this.foo);",
            "}",
            "}",
            "var A$foo = 'bar';",
            "const B = null;",
            "A$foo = 'baz';",
            "A.useFoo();"));

    // Adding @nocollapse makes this safe.
    test(
        lines(
            "class A {",
            "  static useFoo() {",
            "    alert(this.foo);",
            "  }",
            "}",
            "/** @nocollapse */",
            "A.foo = 'bar';",
            "const B = A;",
            "B.foo = 'baz';",
            "B.useFoo();"),
        lines(
            "class A {",
            "static useFoo() {",
            "alert(this.foo);",
            "}",
            "}",
            "/** @nocollapse */",
            "A.foo = 'bar';",
            "const B = null;",
            "A.foo = 'baz';",
            "A.useFoo();"));
  }

  @Test
  public void testClassStaticInheritance_method() {
    test(
        "class A { static s() {} } class B extends A {} const C = B;    C.s();",
        "class A { static s() {} } class B extends A {} const C = null; B.s();");

    testSame("class A { static s() {} } class B extends A {} B.s();");

    // Currently we unsafely collapse A.s because we don't detect it is a class static method.
    test(
        "class A {}     A.s = function() {}; class B extends A {} B.s();",
        "class A {} var A$s = function() {}; class B extends A {} B.s();");
  }

  @Test
  public void testClassStaticInheritance_propertyAlias() {
    test(
        "class A {}     A.staticProp = 6; class B extends A {} let b = new B;",
        "class A {} var A$staticProp = 6; class B extends A {} let b = new B;");

    test(
        "class A {}     A.staticProp = 6; class B extends A {} use(B.staticProp);",
        "class A {} var A$staticProp = 6; class B extends A {} use(A$staticProp);");
  }

  @Test
  public void testClassStaticInheritance_propertyWithSubproperty() {
    test(
        "class A {}     A.ns = {foo: 'bar'}; class B extends A {} use(B.ns.foo);",
        "class A {} var A$ns$foo = 'bar'; class B extends A {} use(A$ns$foo);");

    test(
        "class A {} A.ns = {}; class B extends A {} B.ns.foo = 'baz'; use(B.ns.foo);",
        "class A {} class B extends A {} var A$ns$foo = 'baz'; use(A$ns$foo);");
  }

  @Test
  public void testClassStaticInheritance_propertyWithShadowing() {
    test(
        "class A {}     A.staticProp = 6; class B extends A {}     B.staticProp = 7;",
        "class A {} var A$staticProp = 6; class B extends A {} var B$staticProp = 7;");

    // At the time use() is called, B.staticProp is still the same as A.staticProp, but we back off
    // rewriting it because of the shadowing afterwards. This makes CollapseProperties unsafely
    // collapse in this case - the same issue occurs when transpiling down to ES5.
    test(
        "class A {}     A.foo = 6; class B extends A {} use(B.foo); B.foo = 7;",
        "class A {} var A$foo = 6; class B extends A {} use(B$foo); var B$foo = 7;");
  }

  @Test
  public void testClassStaticInheritance_cantDetermineSuperclass() {
    // Here A.foo and B.foo are not collapsed because getSuperclass() creates an alias for them.
    testSame(
        lines(
            "class A {}",
            "A.foo = 5;",
            "class B {}",
            "B.foo = 6;",
            "function getSuperclass() { return 1 < 2 ? A : B; }",
            "class C extends getSuperclass() {}",
            "use(C.foo);"));
  }

  @Test
  public void testAliasForSuperclassNamespace() {
    test(
        lines(
            "var ns = {};",
            "class Foo {}",
            "ns.clazz = Foo;",
            "var Bar = class extends ns.clazz.Baz {}"),
        lines(
            "class Foo {}",
            "var ns$clazz = null;",
            "var Bar = class extends Foo.Baz {}"));

    test(
        lines(
            "var ns = {};",
            "class Foo {}",
            "Foo.Builder = class {}",
            "ns.clazz = Foo;",
            "var Bar = class extends ns.clazz.Builder {}"),
        lines(
            "class Foo {}",
            "var Foo$Builder = class {}",
            "var ns$clazz = null;",
            "var Bar = class extends Foo$Builder {}"));
  }

  @Test
  public void testAliasForSuperclassNamespace_withStaticInheritance() {
    test(
        lines(
            "var ns = {};",
            "class Foo {}",
            "Foo.Builder = class {}",
            "Foo.Builder.baz = 3;",
            "ns.clazz = Foo;",
            "var Bar = class extends ns.clazz.Builder {}",
            "use(Bar.baz);"),
        lines(
            "class Foo {}",
            "var Foo$Builder = class {}",
            "var Foo$Builder$baz = 3;",
            "var ns$clazz = null;",
            "var Bar = class extends Foo$Builder {}",
            "use(Foo$Builder$baz);"));
  }

  @Test
  public void testDestructuringAlias1() {
    testSame("var a = { x: 5 }; var [b] = [a]; use(b.x);");
  }

  @Test
  public void testDestructuringAlias2() {
    testSame("var a = { x: 5 }; var {b} = {b: a}; use(b.x);");
  }

  @Test
  public void testDestructingAliasWithConstructor() {
    // TODO(b/69293284): Make this not warn and correctly replace "new ctor" with "new ns$ctor".
    test(
        lines(
            "var ns = {};",
            "/** @constructor */",
            "ns.ctor = function() {}",
            "const {ctor} = ns;",
            "let c = new ctor;"),
        lines(
            "var ns = {};",
            "/** @constructor */",
            "var ns$ctor = function() {}",
            "const {ctor} = ns;",
            "let c = new ctor;"),
        warning(CollapseProperties.UNSAFE_NAMESPACE_WARNING));
  }

  @Test
  public void testDefaultParamAlias1() {
    test(
        "var a = {b: 5}; var b = a; function f(x=b) { alert(x.b); }",
        "var a = {b: 5}; var b = null; function f(x=a) { alert(x.b); }");
  }

  @Test
  public void testDefaultParamAlias2() {
    test(
        "var a = {b: {c: 5}}; var b = a; function f(x=b.b) { alert(x.c); }",
        "var a$b = {c: 5}; var b = null; function f(x=a$b) { alert(x.c); }");
  }

  @Test
  public void testAliasPropertyOnUnsafelyRedefinedNamespace() {
    testSame("var obj = {foo: 3}; var foo = obj.foo; obj = {}; alert(foo);");
  }

  @Test
  public void testAliasPropertyOnSafelyRedefinedNamespace() {
    // non-constructor property doesn't get collapsed
    test(
        "var obj = {foo: 3}; var foo = obj.foo; obj = obj || {}; alert(foo);",
        "var obj = {foo: 3}; var foo = null   ; obj = obj || {}; alert(obj.foo);");

    // constructor property does get collapsed
    test(
        lines(
            "var ns = {};",
            "/** @constructor */ ns.ctor = function() {};",
            "ns.ctor.foo = 3;",
            "var foo = ns.ctor.foo;",
            "ns = ns || {};",
            "alert(foo);"),
        lines(
            "var ns = {};",
            "/** @constructor */ var ns$ctor = function() {};",
            "var ns$ctor$foo = 3;",
            "var foo = null;",
            "ns = ns || {};",
            "alert(ns$ctor$foo);"));

    // NOTE(lharker): this mirrors current code in Closure library
    test(
        lines(
            "var goog = {};",
            "goog.module = function() {};",
            "/** @constructor */ goog.module.ModuleManager = function() {};",
            "goog.module.ModuleManager.getInstance = function() {};",
            "goog.module = goog.module || {};",
            "var ModuleManager = goog.module.ModuleManager;",
            "alert(ModuleManager.getInstance());"),
        lines(
            "var goog$module = function() {};",
            "/** @constructor */ var goog$module$ModuleManager = function() {};",
            "var goog$module$ModuleManager$getInstance = function() {};",
            "goog$module = goog$module || {};",
            "var ModuleManager = null;",
            "alert(goog$module$ModuleManager$getInstance());"));
  }

  @Test
  public void testClassInObjectLiteral() {
    test("var obj = {foo: class {}};", "var obj$foo = class {};");

    // TODO(lharker): this is unsafe, obj$foo.foo is undefined now that A$foo is collapsed
    test(
        "class A {}     A.foo = 3; var obj = {foo: class extends A {}}; use(obj.foo.foo);",
        "class A {} var A$foo = 3; var obj$foo =   class extends A{};   use(obj$foo.foo)");
  }

  @Test
  public void testLoopInAliasChainOfTypedefConstructorProperty() {
    test(
        lines(
            "/** @constructor */ var Item = function() {};",
            "/** @typedef {number} */ Item.Models;",
            "Item.Models = Item.Models;"),
        lines(
            "/** @constructor */ var Item = function() {};",
            "Item$Models;",
            "var Item$Models = Item$Models;"));
  }

  @Test
  public void testDontInlinePropertiesOnEscapedNamespace() {
    testSame(
        externs("function use(obj) {}"),
        srcs(
            lines(
                "function Foo() {}",
                "Foo.Bar = {};",
                "Foo.Bar.baz = {A: 1, B: 2};",
                "",
                "var $jscomp$destructuring$var1 = Foo.Bar;",
                // This call could potentially have changed the value of Foo.Bar, so don't
                // collapse Foo.Bar.baz.A/Foo.Bar.baz.B
                "use(Foo);",
                "var baz = $jscomp$destructuring$var1.baz;",
                "use(baz.A);")));
  }

  @Test
  public void testInliningPropertiesOnEscapedNamespace_withDeclaredType() {
    test(
        externs("function use(obj) {}"),
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "/** @constructor */",
                "Foo.Bar = function() {};",
                "/** @enum {number} */",
                "Foo.Bar.baz = {A: 1, B: 2};",
                "",
                "var $jscomp$destructuring$var1 = Foo.Bar;",
                "use(Foo);",
                "var baz = $jscomp$destructuring$var1.baz;",
                "use(baz.A);")),
        expected(
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "/** @constructor */",
                "var Foo$Bar = function() {};",
                "var Foo$Bar$baz$A = 1;",
                "var Foo$Bar$baz$B = 2;",
                "",
                "var $jscomp$destructuring$var1 = null;",
                // This call could potentially read Foo.Bar, which will now be broken.
                // AggressiveInlineAliases/CollapseProperties intentionally generate unsafe code
                // in this case. The main motivation is to collapse long namespaces generated by
                // goog.provide or goog.module.declareLegacyNamespace()
                // https://developers.google.com/closure/compiler/docs/limitations#implications-of-object-property-flattening
                "use(Foo);",
                "var baz = null;",
                "use(Foo$Bar$baz$A);")));
  }

  @Test
  public void testDontInlinePropertiesOnNamespace_withNoCollapse() {
    testSame(
        externs("function use(obj) {}"),
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "/** @constructor @nocollapse */",
                "Foo.Bar = function() {};",
                "/** @enum {number} @nocollapse */",
                "Foo.Bar.baz = {A: 1, B: 2};",
                "",
                "var $jscomp$destructuring$var1 = Foo.Bar;",
                // This call could potentially have changed the value of Foo.Bar, and without
                // @nocollapse on Foo.Bar and Foo.Bar.baz the compiler would generate unsafe code.
                "use(Foo);",
                "var baz = $jscomp$destructuring$var1.baz;",
                "use(baz.A);")));
  }

  @Test
  public void testInlinePropertyOnAliasedConstructor() {
    // TODO(b/117905881): as long as we unsafely collapse Foo.prop -> Foo$prop, there's no way to
    // safely rewrite the "use(ns.alias.prop)" call in the compiler
    // Either
    // a) we replace "ns.alias.prop" with "Foo$prop" or
    // b) we leave "ns.alias.prop" as is.

    // Option a) can break code if the "use(ns)" call actually reassigns ns.alias (which is not
    // declared const).
    // Option b) breaks code if "use(ns)" does NOT reassign ns.alias, since now
    // ns.alias.prop is Foo.prop, which is undefined.
    // but we might want to consider movign to (b) instead of (a), since CollapseProperties will
    // also break case (a)
    test(
        externs("function use(ctor) {}"),
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "Foo.prop = 2;",
                "var ns = {};",
                "ns.alias = Foo;",
                "use(ns);",
                "use(ns.alias.prop);")),
        expected(
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "var Foo$prop = 2;",
                "var ns = {};",
                "ns.alias = Foo;",
                "use(ns);",
                "use(ns.alias.prop);")));
  }
}
