/*
 * Copyright 2016 The Closure Compiler Authors.
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

/** Tests for {@link AggressiveInlineAliases}. */
public class AggressiveInlineAliasesTest extends CompilerTestCase {

  private static final String EXTERNS =
      "var window;"
          + "function alert(s) {}"
          + "function parseInt(s) {}"
          + "function String() {};"
          + "var arguments";

  public AggressiveInlineAliasesTest() {
    super(EXTERNS);
  }

  @Override
  public int getNumRepetitions() {
    return 1;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new AggressiveInlineAliases(compiler);
  }

  @Override
  public void setUp() {
    enableNormalize();
    compareJsDoc = false;
  }

  public void test_b19179602() {
    test(
        "var a = {};"
            + "/** @constructor */ a.b = function() {};"
            + "a.b.staticProp = 5;"
            + "function f() { "
            + "  while (true) { "
            + "    var b = a.b;"
            + "    alert(b.staticProp); } }",
        "var a = {};"
            + "a.b = function() {};"
            + "a.b.staticProp = 5;"
            + "function f() {"
            + "  for(; true; ) {"
            + "    var b = a.b;"
            + "    alert(b.staticProp); } }",
        null,
        AggressiveInlineAliases.UNSAFE_CTOR_ALIASING);
  }

  public void test_b19179602_declareOutsideLoop() {
    test(
        "var a = {};"
            + "a.b = function() {};"
            + "a.b.staticProp = 5;"
            + "function f() { "
            + "  var b = a.b;"
            + "  while (true) { "
            + "    alert(b.staticProp);"
            + "  }"
            + "}",
        "var a = {};"
            + "a.b = function() {};"
            + "a.b.staticProp = 5;"
            + "function f() {"
            + "  var b = null;"
            + "  for (;true;)"
            + "    alert(a.b.staticProp);"
            + "}");
  }

  public void testAddPropertyToChildFuncOfUncollapsibleObjectInLocalScope() {
    test(
        "var a = {};"
            + "a.b = function () {};"
            + "a.b.x = 0;"
            + "var c = a;"
            + "(function() { a.b.y = 1; })();"
            + "a.b.x;"
            + "a.b.y;",
        "var a = {};"
            + "a.b = function() {};"
            + "a.b.x = 0;"
            + "var c = null;"
            + "(function() { a.b.y = 1; })();"
            + "a.b.x;"
            + "a.b.y;");
  }

  public void testAddPropertyToChildOfUncollapsibleCtorInLocalScope() {
    test(
        "var a = function() {};"
            + "a.b = { x: 0 };"
            + "var c = a;"
            + "(function() { a.b.y = 0; })();"
            + "a.b.y;",
        "var a = function() {};"
            + "a.b = { x: 0 };"
            + "var c = null;"
            + "(function() { a.b.y = 0; })();"
            + "a.b.y;");
  }

  public void testAddPropertyToChildOfUncollapsibleFunctionInLocalScope() {
    test(
        "function a() {} a.b = { x: 0 };"
            + "var c = a;"
            + "(function() { a.b.y = 0; })();"
            + "a.b.y;",
        "function a() {}a.b = { x: 0 };"
            + "var c = null;"
            + "(function() { a.b.y = 0; })();"
            + "a.b.y;");
  }

  public void testAddPropertyToChildTypeOfUncollapsibleObjectInLocalScope() {
    test(
        LINE_JOINER.join(
            "var a = {};",
            "a.b = function () {};",
            "a.b.x = 0;",
            "var c = a;",
            "(function() { a.b.y = 1; })();",
            "a.b.x;",
            "a.b.y;"),
        LINE_JOINER.join(
            "var a = {};",
            "a.b = function() {};",
            "a.b.x = 0;",
            "var c = null;",
            "(function() { a.b.y = 1; })();",
            "a.b.x;",
            "a.b.y;"));
  }

  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth1() {
    test(
        "var a = function() {};" + "var c = a;" + "(function() { a.b = 0;" + "})();" + "a.b;",
        "var a = function() {};" + "var c = null;" + "(function() { a.b = 0 })();" + "a.b;");
  }

  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth2() {
    test(
        "var a = {};"
            + "a.b = function () {};"
            + "var d = a.b;"
            + "(function() { a.b.c = 0; })();"
            + "a.b.c;",
        "var a = {};"
            + "a.b = function() {};"
            + "var d = null;"
            + "(function() { a.b.c = 0; })();"
            + "a.b.c;");
  }

  public void testAddPropertyToUncollapsibleNamedCtorInLocalScopeDepth1() {
    test(
        "function a() {} var a$b;" + "var c = a;" + "(function() { a$b = 0;" + "})();" + "a$b;",
        "function a() {} var a$b;" + "var c = null;" + "(function() { a$b = 0;" + "})();" + "a$b;");
  }

  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth1() {
    test(
        "var a = {};" + "var c = a;" + "use(c);" + "(function() { a.b = 0;" + "})();" + "a.b;",
        "var a = {};" + "var c = null;" + "use(a);" + "(function() { a.b = 0;" + "})();" + "a.b;");
  }

  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth2() {
    test(
        "var a = {};"
            + "a.b = {};"
            + "var d = a.b;"
            + "use(d);"
            + "(function() { a.b.c = 0; })();"
            + "a.b.c;",
        "var a = {};"
            + "a.b = {};"
            + "var d = null;"
            + "use(a.b);"
            + "(function() { a.b.c = 0; })();"
            + "a.b.c;");
  }

  public void testAliasCreatedForClassDepth1_1() {
    test(
        "var a = {};" + "a.b = function() {};" + "var c = a;" + "c.b = 0;" + "a.b != c.b;",
        "var a = {};" + "a.b = function() {};" + "var c = null;" + "a.b = 0;" + "a.b != a.b;");

    test(
        "var a = {};" + "a.b = function() {};" + "var c = a;" + "c.b = 0;" + "a.b == c.b;",
        "var a = {};" + "a.b = function() {};" + "var c = null;" + "a.b = 0;" + "a.b == a.b;");
  }

  public void testAliasCreatedForCtorDepth1_1() {
    test(
        "var a = function() {};" + "a.b = 1;" + "var c = a;" + "c.b = 2;" + "a.b == c.b;",
        "var a = function() {};" + "a.b = 1;" + "var c = null;" + "a.b = 2;" + "a.b == a.b;");

    test(
        "var a = function() {};" + "a.b = 1;" + "var c = a;" + "c.b = 2;" + "a.b == c.b;",
        "var a = function() {};" + "a.b = 1;" + "var c = null;" + "a.b = 2;" + "a.b == a.b;");
  }

  public void testAliasCreatedForCtorDepth2() {
    test(
        "var a = {};" + "a.b = function() {};" + "a.b.c = 1;" + "var d = a.b;" + "a.b.c == d.c;",
        "var a = {};" + "a.b = function() {};" + "a.b.c = 1;" + "var d = null;" + "a.b.c == a.b.c");
  }

  public void testAliasCreatedForFunctionDepth2() {
    test(
        "var a = {};" + "a.b = function() {};" + "a.b.c = 1;" + "var d = a.b;" + "a.b.c != d.c;",
        "var a = {};" + "a.b = function() {};" + "a.b.c = 1;" + "var d = null;" + "a.b.c != a.b.c");

    test(
        "var a = {};" + "a.b = function() {};" + "a.b.c = 1;" + "var d = a.b;" + "a.b.c == d.c;",
        "var a = {};" + "a.b = function() {};" + "a.b.c = 1;" + "var d = null;" + "a.b.c == a.b.c");
  }

  public void testAliasCreatedForObjectDepth1_1() {
    test(
        "var a = { b: 0 };" + "var c = a;" + "c.b = 1;" + "a.b == c.b;",
        "var a = { b: 0 };" + "var c = null;" + "a.b = 1;" + "a.b == a.b;");

    test(
        "var a = { b: 0 };" + "var c = a;" + "c.b = 1;" + "a.b == c.b;" + "use(c);",
        "var a = { b: 0 };" + "var c = null;" + "a.b = 1;" + "a.b == a.b;" + "use(a);");
  }

  public void testCodeGeneratedByGoogModule() {
    test(
        "var $jscomp = {};"
            + "$jscomp.scope = {};"
            + "$jscomp.scope.Foo = function() {};"
            + "var exports = $jscomp.scope.Foo;"
            + "exports.staticprop = { A: 1 };"
            + "var y = exports.staticprop.A;",
        "var $jscomp = {};"
            + "$jscomp.scope = {};"
            + "$jscomp.scope.Foo = function() {};"
            + "var exports = null;"
            + "$jscomp.scope.Foo.staticprop = { A: 1 };"
            + "var y = $jscomp.scope.Foo.staticprop.A");

    test(
        "var $jscomp = {};"
            + "$jscomp.scope = {};"
            + "$jscomp.scope.Foo = function() {};"
            + "$jscomp.scope.Foo.staticprop = { A: 1 };"
            + "var exports = $jscomp.scope.Foo;"
            + "var y = exports.staticprop.A;",
        "var $jscomp = {};"
            + "$jscomp.scope = {};"
            + "$jscomp.scope.Foo = function() {};"
            + "$jscomp.scope.Foo.staticprop = { A: 1 };"
            + "var exports = null;"
            + "var y = $jscomp.scope.Foo.staticprop.A");
  }

  public void testCollapse() {
    test(
        "var a = {};" + "a.b = {};" + "var c = a.b;", "var a = {};" + "a.b = {};" + "var c = null");

    test(
        "var a = {};" + "a.b = {};" + "var c = a.b;" + "use(c);",
        "var a = {};" + "a.b = {};" + "var c = null;" + "use(a.b)");

    testSame("var a = {};" + "a.b;");
  }

  public void testCollapsePropertiesOfClass1() {
    test(
        "var namespace = function() {};"
            + "goog.inherits(namespace, Object);"
            + "namespace.includeExtraParam = true;"
            + "/** @enum { number } */"
            + "namespace.Param = { param1: 1, param2: 2 };"
            + "if (namespace.includeExtraParam) namespace.Param.optParam = 3;"
            + "function f() { "
            + "  var Param = namespace.Param;"
            + "  log(namespace.Param.optParam);"
            + "  log(Param.optParam);"
            + "}",
        "var namespace = function() {};"
            + "goog.inherits(namespace,Object);"
            + "namespace.includeExtraParam = true;"
            + "namespace.Param = { param1: 1,param2: 2 };"
            + "if(namespace.includeExtraParam) namespace.Param.optParam = 3;"
            + "function f() {"
            + "  var Param = null;"
            + "  log(namespace.Param.optParam);"
            + "  log(namespace.Param.optParam);"
            + "}");
  }

  public void testCollapsePropertiesOfClass2() {
    test(
        "var goog = goog || {};"
            + "goog.addSingletonGetter = function(cls) {};"
            + "var a = {};"
            + "a.b = function() {};"
            + "goog.addSingletonGetter(a.b);"
            + "a.b.prototype.get = function(key) {};"
            + "a.b.c = function() {};"
            + "a.b.c.XXX = new a.b.c;"
            + "function f() { "
            + "  var x = a.b.getInstance();"
            + "  var Key = a.b.c;"
            + "  x.get(Key.XXX);"
            + "}",
        "var goog = goog || {};"
            + "goog.addSingletonGetter = function(cls) {};"
            + "var a = {};"
            + "a.b = function() {};"
            + "goog.addSingletonGetter(a.b);"
            + "a.b.prototype.get = function(key) {};"
            + "a.b.c = function() {};"
            + "a.b.c.XXX = new a.b.c;"
            + "function f() {"
            + "  var x = a.b.getInstance();"
            + "  var Key = null;"
            + "  x.get(a.b.c.XXX);"
            + "}");
  }

  public void testCommaOperator() {
    test(
        "var ns = {};"
            + "ns.Foo = {};"
            + "var Baz = {};"
            + "Baz.Foo = ns.Foo;"
            + "Baz.Foo.bar = 10, 123;",
        "var ns = {};"
            + "ns.Foo = {};"
            + "var Baz = {};"
            + "Baz.Foo = null;"
            + "ns.Foo.bar = 10, 123;");
  }

  public void testDontCrashCtorAliasWithEnum() {
    test(
        "var ns = {};"
            + "ns.Foo = function () {};"
            + "var Bar = ns.Foo;"
            + "/** @const @enum */"
            + "Bar.prop = { A: 1 };",
        "var ns = {};" + "ns.Foo = function() {};" + "var Bar = null;" + "ns.Foo.prop = { A: 1 }");
  }

  public void testFunctionAlias2() {
    test(
        "var a = {};" + "a.b = {};" + "a.b.c = function() {};" + "a.b.d = a.b.c;" + "use(a.b.d);",
        "var a = {};" + "a.b = {};" + "a.b.c = function() {};" + "a.b.d = null;" + "use(a.b.c);");
  }

  public void testGlobalAliasOfAncestor() {
    test(
        "var a = { b: { c: 5 } };" + "function f() { var x = a.b;" + "f(x.c);" + "}",
        "var a = { b: { c: 5 } };" + "function f() { var x = null;" + "f(a.b.c);" + "}");
  }

  public void testGlobalAliasWithProperties1() {
    test(
        "var ns = {};"
            + "ns.Foo = function() {};"
            + "/** @enum { number } */ ns.Foo.EventType = { A: 1, B: 2 };"
            + "ns.Bar = ns.Foo;"
            + "var x = function() { use(ns.Bar.EventType.A) };"
            + "use(x);",
        "var ns = {};"
            + "ns.Foo = function() {};"
            + "/** @enum { number } */ ns.Foo.EventType = { A: 1, B: 2 };"
            + "ns.Bar = null;"
            + "var x = function() { use(ns.Foo.EventType.A) };"
            + "use(x);");
  }

  public void testGlobalAliasWithProperties2() {
    test(
        "var ns = {};"
            + "ns.Foo = function() {};"
            + "/** @enum { number } */ ns.Foo.EventType = { A: 1, B: 2 };"
            + "ns.Bar = ns.Foo;"
            + "/** @enum { number } */ ns.Bar.EventType = ns.Foo.EventType;"
            + "var x = function() { use(ns.Bar.EventType.A) };"
            + "use(x)",
        "var ns = {};"
            + "ns.Foo = function() {};"
            + "/** @enum { number } */ ns.Foo.EventType = { A: 1, B: 2 };"
            + "ns.Bar = null;"
            + "/** @enum { number } */ ns.Foo.EventType = ns.Foo.EventType;"
            + "var x = function() { use(ns.Foo.EventType.A) };"
            + "use(x)");
  }

  public void testGlobalAliasWithProperties3() {
    test(
        "var ns = {};"
            + "ns.Foo = function() {};"
            + "/** @enum { number } */ ns.Foo.EventType = { A: 1, B: 2 };"
            + "ns.Bar = ns.Foo;"
            + "/** @enum { number } */ ns.Bar.Other = { X: 1, Y: 2 };"
            + "var x = function() { use(ns.Bar.Other.X) };"
            + "use(x)",
        "var ns = {};"
            + "ns.Foo = function() {};"
            + "/** @enum { number } */ ns.Foo.EventType = { A: 1, B: 2 };"
            + "ns.Bar = null;"
            + "/** @enum { number } */ ns.Foo.Other = { X: 1, Y: 2 };"
            + "var x = function() { use(ns.Foo.Other.X) };"
            + "use(x)");
  }

  public void testGlobalWriteToNonAncestor() {
    test(
        "var a = { b: 3 };" + "function f() { var x = a;" + "f(a.b);" + "} a.b = 5;",
        "var a = { b: 3 };" + "function f() { var x = null;" + "f(a.b);" + "} a.b = 5;");
  }

  public void testInlineCtorInObjLit() {
    compareJsDoc = true;

    test(
        LINE_JOINER.join("function Foo() {}", "var Bar = Foo;", "var objlit = { 'prop' : Bar };"),
        LINE_JOINER.join("function Foo() {}", "var Bar = null;", "var objlit = { 'prop': Foo };"));
  }

  public void testLocalAlias1() {
    test(
        "var a = { b: 3 };" + "function f() { var x = a;" + "f(x.b);" + "}",
        "var a = { b: 3 };" + "function f() { var x = null;" + "f(a.b);" + "}");
  }

  public void testLocalAlias2() {
    test(
        "var a = { b: 3, c: 4 };" + "function f() { var x = a;" + "f(x.b);" + "f(x.c);" + "}",
        "var a = { b: 3, c: 4 };" + "function f() { var x = null;" + "f(a.b);" + "f(a.c);" + "}");
  }

  public void testLocalAlias3() {
    test(
        "var a = { b: 3, c: { d: 5 } };"
            + "function f() {"
            + "  var x = a;"
            + "  f(x.b);"
            + "  f(x.c);"
            + "  f(x.c.d);"
            + "}",
        "var a = { b: 3, c: { d: 5 } };"
            + "function f() {"
            + "  var x = null;"
            + "  f(a.b);"
            + "  f(a.c);"
            + "  f(a.c.d)"
            + "}");
  }

  public void testLocalAlias4() {
    test(
        "var a = { b: 3 };"
            + "var c = { d: 5 };"
            + "function f() {"
            + "  var x = a;"
            + "  var y = c;"
            + "  f(x.b);"
            + "  f(y.d);"
            + "}",
        "var a = { b: 3 };"
            + "var c = { d: 5 };"
            + "function f() {"
            + "  var x = null;"
            + "  var y = null;"
            + "  f(a.b);"
            + "  f(c.d)"
            + "}");
  }

  public void testLocalAlias5() {
    test(
        "var a = { b: { c: 5 } };"
            + "function f() {"
            + "  var x = a;"
            + "  var y = x.b;"
            + "  f(a.b.c);"
            + "  f(y.c);"
            + "}",
        "var a = { b: { c: 5 } };"
            + "function f() {"
            + "  var x = null;"
            + "  var y = null;"
            + "  f(a.b.c);"
            + "  f(a.b.c);"
            + "}");
  }

  public void testLocalAlias6() {
    test(
        "var a = { b: 3 };" + "function f() { var x = a;" + "if (x.b) f(x.b);" + "}",
        "var a = { b: 3 };" + "function f() { var x = null;" + "if (a.b) f(a.b);" + "}");
  }

  public void testLocalAlias7() {
    test(
        "var a = { b: { c: 5 } };" + "function f() { var x = a.b;" + "f(x.c);" + "}",
        "var a = { b: { c: 5 } };" + "function f() { var x = null;" + "f(a.b.c);" + "}");
  }

  public void testLocalAliasOfEnumWithInstanceofCheck() {
    test(
        "var Enums = function() {};"
            + "/** @enum { number } */"
            + "Enums.Fruit = { APPLE: 1, BANANA: 2 };"
            + "function foo(f) {"
            + "if (f instanceof Enums) { alert('what?'); return; }"
            + "var Fruit = Enums.Fruit;"
            + "if (f == Fruit.APPLE) alert('apple');"
            + "if (f == Fruit.BANANA) alert('banana'); }",
        "var Enums = function() {};"
            + "Enums.Fruit = { APPLE: 1,BANANA: 2 };"
            + "function foo(f) {"
            + "if (f instanceof Enums) { alert('what?'); return; }"
            + "var Fruit = null;"
            + "if(f == Enums.Fruit.APPLE) alert('apple');"
            + "if(f == Enums.Fruit.BANANA) alert('banana'); }");
  }

  public void testLocalAliasOfFunction() {
    test(
        "var a = function() {};" + "a.b = 5;" + "function f() { var x = a.b;" + "f(x);" + "}",
        "var a = function() {};" + "a.b = 5;" + "function f() { var x = null;" + "f(a.b);" + "}");
  }

  public void testLocalWriteToNonAncestor() {
    test(
        "var a = { b: 3 };" + "function f() { a.b = 5;" + "var x = a;" + "f(a.b);" + "}",
        "var a = { b: 3 };" + "function f() { a.b = 5;" + "var x = null;" + "f(a.b);" + "}");
  }

  public void testMisusedConstructorTag() {
    test(
        "var a = {};" + "var d = a;" + "a.b = function() {};" + "a.b.c = 0;" + "a.b.c;",
        "var a = {};" + "var d = null;" + "a.b = function() {};" + "a.b.c = 0;" + "a.b.c;");
  }

  public void testNoInlineGetpropIntoCall() {
    test(
        "var b = x;" + "function f() { var a = b;" + "a();" + "}",
        "var b = x;" + "function f() { var a = null;" + "b()" + "}");
  }

  public void testObjLitAssignmentDepth3() {
    test(
        "var a = {};" + "a.b = {};" + "a.b.c = { d: 1, e: 2 };" + "var f = a.b.c.d;",
        "var a = {};" + "a.b = {};" + "a.b.c = { d: 1,e: 2 };" + "var f = null;");

    test(
        "var a = {};" + "a.b = {};" + "a.b.c = { d: 1, e: 2 };" + "var f = a.b.c.d;" + "use(f);",
        "var a = {};" + "a.b = {};" + "a.b.c = { d: 1,e: 2 };" + "var f = null;" + "use(a.b.c.d);");

    test(
        "var a = {};"
            + "a.b = {};"
            + "a.b.c = { d: 1, e: 2 };"
            + "var f = a.b.c.d;"
            + "var g = a.b.c.e;",
        "var a = {};"
            + "a.b = {};"
            + "a.b.c = { d: 1, e: 2 };"
            + "var f = null;"
            + "var g = null;");

    test(
        "var a = {};"
            + "a.b = {};"
            + "a.b.c = { d: 1, e: 2 };"
            + "var f = a.b.c.d;"
            + "var g = a.b.c.e;"
            + "use(f, g);",
        "var a = {};"
            + "a.b = {};"
            + "a.b.c = { d: 1, e: 2 };"
            + "var f = null;"
            + "var g = null;"
            + "use(a.b.c.d, a.b.c.e);");

    testSame(
        "var a = {};"
            + "a.b = {};"
            + "a.b.c = { d: 1, e: 2 };"
            + "var f = null;"
            + "var g = null;");
  }

  public void testObjLitAssignmentDepth4() {
    test(
        "var a = {};"
            + "a.b = {};"
            + "a.b.c = {};"
            + "a.b.c.d = { e: 1, f: 2 };"
            + "var g = a.b.c.d.e;",
        "var a = {};"
            + "a.b = {};"
            + "a.b.c = {};"
            + "a.b.c.d = { e: 1, f: 2 };"
            + "var g = null;");

    test(
        "var a = {};"
            + "a.b = {};"
            + "a.b.c = {};"
            + "a.b.c.d = { e: 1, f: 2 };"
            + "var g = a.b.c.d.e;"
            + "use(g);",
        "var a = {};"
            + "a.b = {};"
            + "a.b.c = {};"
            + "a.b.c.d = { e: 1, f: 2 };"
            + "var g = null;"
            + "use(a.b.c.d.e);");
  }

  public void testObjLitDeclaration() {
    test(
        "var a = { b: {}, c: {} };" + "var d = a.b;" + "var e = a.c;",
        "var a = { b: {}, c: {} };" + "var d = null;" + "var e = null;");

    test(
        "var a = { b: {}, c: {} };" + "var d = a.b;" + "var e = a.c;" + "use(d, e);",
        "var a = { b: {}, c: {} };" + "var d = null;" + "var e = null;" + "use(a.b,a.c);");

    test(
        "var a = { b: {}, c: {} };" + "var d = a.b;" + "var e = a.c;",
        "var a = { b: {}, c: {} };" + "var d = null;" + "var e = null;");

    test(
        "var a = { b: {}, c: {} };" + "var d = a.b;" + "var e = a.c;" + "use(d, e);",
        "var a = { b: {}, c: {} };" + "var d = null;" + "var e = null;" + "use(a.b, a.c);");
  }

  public void testObjLitDeclarationUsedInSameVarList() {
    test(
        "var a = { b: {}, c: {} };" + "var d = a.b;" + "var e = a.c;" + "use(d, e);",
        "var a = { b: {}, c: {} };" + "var d = null;" + "var e = null;" + "use(a.b, a.c)");
  }

  public void testObjLitDeclarationWithGet2() {
    test(
        "var a = { b: {}, get c() {} };" + "var d = a.b;" + "var e = a.c;",
        "var a = { b: {}, get c() {} };" + "var d = null;" + "var e = a.c;");

    test(
        "var a = { b: {}, get c() {} };" + "var d = a.b;" + "var e = a.c;" + "use(d);",
        "var a = { b: {}, get c() {} };" + "var d = null;" + "var e = a.c;" + "use(a.b);");
  }

  public void testObjLitDeclarationWithSet2() {
    test(
        "var a = { b: {}, set c(a) {} };" + "var d = a.b;" + "var e = a.c",
        "var a = { b: {}, set c(a$jscomp$1) {} };" + "var d = null;" + "var e = a.c;");

    test(
        "var a = { b: {}, set c(a) {} };" + "var d = a.b;" + "var e = a.c;" + "use(d);",
        "var a = { b: {}, set c(a$jscomp$1) {} };"
            + "var d = null;"
            + "var e = a.c;"
            + "use(a.b);");
  }

  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth1() {
    test(
        "var a = {};" + "var c = a;" + "a.b = function () {};" + "a.b.x = 0;" + "a.b.x;",
        "var a = {};" + "var c = null;" + "a.b = function() {};" + "a.b.x = 0;" + "a.b.x;");

    test(
        "var a = {};"
            + "var c = a;"
            + "a.b = function () {};"
            + "a.b.x = 0;"
            + "a.b.x;"
            + "use(c);",
        "var a = {};"
            + "var c = null;"
            + "a.b = function() {};"
            + "a.b.x = 0;"
            + "a.b.x;"
            + "use(a);");
  }

  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth2() {
    test(
        "var a = {};"
            + "a.b = {};"
            + "var c = a.b;"
            + "a.b.c = function () {};"
            + "a.b.c.x = 0;"
            + "a.b.c.x;",
        "var a = {};"
            + "a.b = {};"
            + "var c = null;"
            + "a.b.c = function() {};"
            + "a.b.c.x = 0;"
            + "a.b.c.x");

    test(
        "var a = {};"
            + "a.b = {};"
            + "var c = a.b;"
            + "a.b.c = function () {};"
            + "a.b.c.x = 0;"
            + "a.b.c.x;"
            + "use(c);",
        "var a = {};"
            + "a.b = {};"
            + "var c = null;"
            + "a.b.c = function() {};"
            + "a.b.c.x = 0;"
            + "a.b.c.x;"
            + "use(a.b)");
  }

  public void testTypeDefAlias1() {
    test(
        "var D = function() {};"
            + "D.L = function() {};"
            + "/** @type { D.L } */ D.L.A = new D.L();"
            + "/** @const */ var M = {};"
            + "/** @typedef { D.L } */ M.L = D.L;"
            + "use(M.L.A);",
        "var D = function() {};"
            + "D.L = function() {};"
            + "/** @type { D.L } */ D.L.A = new D.L;"
            + "/** @const */ var M = {};"
            + "/** @typedef { D.L } */ M.L = null;"
            + "use(D.L.A);");
  }
}
