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
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new AggressiveInlineAliases(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableNormalize();
  }

  public void test_b19179602() {
    test(
        lines(
            "var a = {};",
            "/** @constructor */ a.b = function() {};",
            "a.b.staticProp = 5;",
            "/** @constructor */",
            "function f() { ",
            "  while (true) { ",
            "    var b = a.b;",
            "    alert(b.staticProp); } }"),
        lines(
            "var a = {};",
            "/** @constructor */ a.b = function() {};",
            "a.b.staticProp = 5;",
            "/** @constructor */",
            "function f() {",
            "  for(; true; ) {",
            "    var b = a.b;",
            "    alert(b.staticProp); } }"),
        warning(AggressiveInlineAliases.UNSAFE_CTOR_ALIASING));
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

  public void testCtorAliasedMultipleTimesNoWarning1() {
    // We can't inline the alias, but there are no unsafe property accesses.
    testSame(
        lines(
            "/** @constructor */",
            "function a() {}",
            "function f() {",
            "  var alias = a;",
            "  use(alias.prototype);", // The prototype is not collapsible.
            "  alias.apply();", // Can't collapse externs properties.
            "  alias = function() {}",
            "}"));
  }

  public void testCtorAliasedMultipleTimesNoWarning2() {
    testSame(
        lines(
            "/** @constructor */",
            "function a() {}",
            "/** @nocollapse */",
            "a.staticProp = 5;", // Explicitly forbid collapsing a.staticProp.
            "function f() {",
            "  var alias = a;",
            "  use(alias.staticProp);", // Safe because we don't collapse a.staticProp
            "  alias = function() {}",
            "}"));
  }

  public void testCtorAliasedMultipleTimesWarning1() {
    testSame(
        lines(
            "/** @constructor */",
            "function a() {}",
            "a.staticProp = 5;",
            "function f() {",
            "  var alias = a;",
            "  use(alias.staticProp);", // Unsafe because a.staticProp becomes a$staticProp.
            "  alias = function() {}",
            "}"),
        AggressiveInlineAliases.UNSAFE_CTOR_ALIASING);
  }

  public void testCtorAliasedMultipleTimesWarning2() {
    testSame(
        lines(
            "/** @constructor */",
            "function a() {}",
            "a.b = {};",
            "a.b.staticProp = 5;",
            "function f() {",
            "  var alias = a;",
            "  use(alias.b.staticProp);", // Unsafe because a.b.staticProp becomes a$b$staticProp.
            "  alias = function() {}",
            "}"),
        AggressiveInlineAliases.UNSAFE_CTOR_ALIASING);
  }

  public void testCtorAliasedMultipleTimesWarning3() {
    testSame(
        lines(
            "/** @constructor */",
            "function a() {}",
            "a.staticProp = 5;",
            "function f(alias) {",
            "  alias();",
            "  alias = a;",
            "  use(alias.staticProp);", // Unsafe because a.staticProp becomes a$staticProp.
            "}"),
        AggressiveInlineAliases.UNSAFE_CTOR_ALIASING);
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
        lines(
            "var a = {};",
            "a.b = function () {};",
            "a.b.x = 0;",
            "var c = a;",
            "(function() { a.b.y = 1; })();",
            "a.b.x;",
            "a.b.y;"),
        lines(
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

  public void testLocalNonCtorAliasCreatedAfterVarDeclaration1() {
    // We only inline non-constructor local aliases if they are assigned upon declaration.
    // TODO(lharker): We should be able to inline these. InlineVariables does, and it also
    // uses ReferenceCollectingCallback to track references.
    testSame(
        lines(
            "var Main = {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  var tmp;",
            "  tmp = Main;",
            "  tmp.doSomething(5);",
            "}"));
  }

  public void testLocalCtorAliasCreatedAfterVarDeclaration1() {
    test(
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  var tmp;",
            "  tmp = Main;",
            "  tmp.doSomething(5);",
            "}"),
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  var tmp;",
            "  tmp = null;",
            "  Main.doSomething(5);",
            "}"));
  }

  public void testLocalCtorAliasCreatedAfterVarDeclaration2() {
    test(
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  tmp = Main;",
            "  tmp.doSomething(5);",
            "  if (true) {",
            "    tmp.doSomething(6);",
            "  }",
            "  var tmp;",
            "}"),
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  tmp = null;",
            "  Main.doSomething(5);",
            "  if (true) {",
            "    Main.doSomething(6);",
            "  }",
            "  var tmp;",
            "}"));
  }

  public void testLocalCtorAliasCreatedAfterVarDeclaration3() {
    test(
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  if (tmp = Main) {",
            "    tmp.doSomething(6);",
            "  }",
            "  var tmp;",
            "}"),
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  if (tmp = Main) {", // Don't set "tmp = null" because that would change control flow.
            "    Main.doSomething(6);",
            "  }",
            "  var tmp;",
            "}"));
  }

  public void testLocalCtorAliasCreatedAfterVarDeclaration4() {
    test(
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  var a = tmp = Main;",
            "  tmp.doSomething(6);",
            "  a.doSomething(7);",
            "  var tmp;",
            "}"),
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  var a = tmp = Main;", // Don't set "tmp = null" because that would mess up a's value.
            "  Main.doSomething(6);",
            "  a.doSomething(7);", // Main doesn't get inlined here, which makes collapsing unsafe.
            "  var tmp;",
            "}"));
  }

  public void testLocalCtorAliasAssignedInLoop1() {
    test(
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  var tmp;",
            "  for (let i = 0; i < n(); i++) {",
            "    tmp = Main;",
            "    tmp.doSomething(5);",
            "    use(tmp);",
            "  }",
            "  use(tmp);",
            "  use(tmp.doSomething);",
            "}"),
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  var tmp;",
            "  for (let i = 0; i < n(); i++) {",
            "    tmp = Main;",
            "    Main.doSomething(5);",
            "    use(Main);",
            "  }",
            "  use(tmp);",
            "  use(tmp.doSomething);", // This line may break if Main$doSomething is collapsed.
            "}"));
  }


  public void testLocalCtorAliasAssignedInLoop2() {
    // Test when the alias is assigned in a loop after being used.
    testSame(
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  var tmp;",
            "  for (let i = 0; i < n(); i++) {",
            "    use(tmp);", // Don't inline tmp here since it changes between loop iterations.
            "    tmp = Main;",
            "  }",
            "  use(tmp);",
            "  use(tmp.doSomething);",
            "}"));
  }

  public void testLocalCtorAliasAssignedInSwitchCase() {
    // This mimics how the async generator polyfill behaves.
    test(
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  function g() {",
            "    for (;;) {",
            "      switch(state) {",
            "        case 0:",
            "          tmp1 = Main;",
            "          tmp2 = tmp1.doSomething;",
            "          state = 1;",
            "          return;",
            "         case 1:",
            "           return tmp2.call(tmp1, 3);",
            "      }",
            "    }",
            "  }",
            "  var tmp1, tmp2, state = 0;",
            "  g();",
            "  return g();",
            "}"),
        lines(
            "/** @constructor @struct */ var Main = function() {};",
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  function g() {",
            "    for (;;) {",
            "      switch(state) {",
            "        case 0:",
            "          tmp1 = Main;",
            "          tmp2 = Main.doSomething;",
            "          state = 1;",
            "          return;",
            "         case 1:",
            "           return tmp2.call(tmp1, 3);",
            "      }",
            "    }",
            "  }",
            "  var tmp1, tmp2, state = 0;",
            "  g();",
            "  return g();",
            "}"));
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
            + "var y = null;");

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
            + "var y = null;");
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
        lines(
            "/** @constructor */ var namespace = function() {};",
            "goog.inherits(namespace, Object);",
            "namespace.includeExtraParam = true;",
            "/** @enum { number } */",
            "namespace.Param = { param1: 1, param2: 2 };",
            "if (namespace.includeExtraParam) namespace.Param.optParam = 3;",
            "/** @constructor */",
            "function f() { ",
            "  var Param = namespace.Param;",
            "  log(namespace.Param.optParam);",
            "  log(Param.optParam);",
            "}"),
        lines(
            "/** @constructor */ var namespace = function() {};",
            "goog.inherits(namespace,Object);",
            "namespace.includeExtraParam = true;",
            "/** @enum { number } */",
            "namespace.Param = { param1: 1,param2: 2 };",
            "if(namespace.includeExtraParam) namespace.Param.optParam = 3;",
            "/** @constructor */",
            "function f() {",
            "  var Param = null;",
            "  log(namespace.Param.optParam);",
            "  log(namespace.Param.optParam);",
            "}"));
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
        lines(
            "var ns = {};",
            "/** @constructor */ ns.Foo = function () {};",
            "var Bar = ns.Foo;",
            "/** @const @enum */",
            "Bar.prop = { A: 1 };"),
        lines(
            "var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "var Bar = null;",
            "/** @const @enum */",
            "ns.Foo.prop = { A: 1 }"));
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
    test(
        lines("function Foo() {}", "var Bar = Foo;", "var objlit = { 'prop' : Bar };"),
        lines("function Foo() {}", "var Bar = null;", "var objlit = { 'prop': Foo };"));
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

  public void testLocalAlias8() {
    testSame(
        "var a = { b: 3 };" + "function f() { if (true) { var x = a; f(x.b); } x = { b : 4}; }");
  }

  public void testLocalAliasOfEnumWithInstanceofCheck() {
    test(
        lines(
            "/** @constructor */ var Enums = function() {};",
            "/** @enum { number } */",
            "Enums.Fruit = { APPLE: 1, BANANA: 2 };",
            "/** @constructor */ function foo(f) {",
            "if (f instanceof Enums) { alert('what?'); return; }",
            "var Fruit = Enums.Fruit;",
            "if (f == Fruit.APPLE) alert('apple');",
            "if (f == Fruit.BANANA) alert('banana'); }"),
        lines(
            "/** @constructor */ var Enums = function() {};",
            "/** @enum { number } */",
            "Enums.Fruit = { APPLE: 1,BANANA: 2 };",
            "/** @constructor */ function foo(f) {",
            "if (f instanceof Enums) { alert('what?'); return; }",
            "var Fruit = null;",
            "if(f == Enums.Fruit.APPLE) alert('apple');",
            "if(f == Enums.Fruit.BANANA) alert('banana'); }"));
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

  public void testGlobalAliasWithConst() {
    test("const a = {}; a.b = {}; const c = a.b;", "const a = {}; a.b = {}; const c = null");

    test(
        "const a = {}; a.b = {}; const c = a.b; use(c);",
        "const a = {}; a.b = {}; const c = null; use(a.b)");

    testSame("const a = {}; a.b;");
  }

  public void testGlobalAliasWithLet1() {
    test("let a = {}; a.b = {}; let c = a.b;", "let a = {}; a.b = {}; let c = null");

    test(
        "let a = {}; a.b = {}; let c = a.b; use(c);",
        "let a = {}; a.b = {}; let c = null; use(a.b)");

    testSame("let a = {}; a.b;");

    test(
        "let a = {}; if (true) { a.b = 5; } let c = a.b; use(c);",
        "let a = {}; if (true) { a.b = 5; } let c = null; use(a.b);");
  }

  public void testGlobalAliasWithLet2() {
    // Inlining ns is unsafe because ns2 may or may not be equal to ns.
    testSame(
        lines(
            "let ns = {};",
            "ns.foo = 'bar';",
            "let ns2;",
            "if (true) {",
            "  ns2 = ns;",
            "}",
            "use(ns2.foo);"));

    // In this case, it would be safe to inline ns, as long as all subsequent references
    // to ns2 are inside the if block, but the algorithm is not complex enough to know that.
    testSame(
        lines(
            "let ns = {};",
            "ns.foo = 'bar';",
            "let ns2;",
            "if (true) {",
            "  ns2 = ns;",
            "use(ns2.foo);",
            "}"));
  }

  public void testLocalAliasWithLet1() {
    test(
        lines(
            "var a = {};",
            "a.b = {};",
            "function f() {",
            "  if (true) {",
            "    let c = a.b;",
            "    alert(c);",
            "  }",
            "}"),
        lines(
            "var a = {};",
            "a.b = {};",
            "function f() {",
            "  if (true) {",
            "    let c = null;",
            "    alert(a.b);",
            "  }",
            "}"));
  }

  public void testLocalAliasWithLet2() {
    test(
        "var a = {}; a.b = {}; if (true) { let c = a.b;  alert(c); }",
        "var a = {}; a.b = {}; if (true) { let c = null;  alert(a.b); }");
  }

  public void testLocalAliasWithLet3() {
    test(
        "let ns = {a: 1}; { let y = ns; use(y.a); }",
        "let ns = {a: 1}; { let y = null; use(ns.a); }");
  }

  public void testLocalAliasInsideClass() {
    test(
        "var a = {x: 5}; class A { fn() { var b = a; use(b.x); } }",
        "var a = {x: 5}; class A { fn() { var b = null; use(a.x); } }");
  }

  public void testGlobalClassAlias1() {
    test(
        "class A {} A.foo = 5; const B = A; use(B.foo);",
        "class A {} A.foo = 5; const B = null; use(A.foo)");
  }

  public void testGlobalClassAlias2() {
    test(
        "class A { static fn() {} } const B = A; B.fn();",
        "class A { static fn() {} } const B = null; A.fn();");
  }

  public void testGlobalClassAlias3() {
    test(
        "class A {} const B = A; B.prototype.fn = () => 5;",
        "class A {} const B = null; A.prototype.fn = () => 5;");
  }

  public void testDestructuringAlias1() {
    // CollapseProperties backs off on destructuring, so it's okay not to inline here.
    testSame("var a = {x: 5}; var [b] = [a]; use(b.x);");
  }

  public void testDestructuringAlias2() {
    testSame("var a = {x: 5}; var {b} = {b: a}; use(b.x);");
  }

  public void testDefaultParamAlias() {
    test(
        "var a = {b: 5}; var b = a; function f(x=b) { alert(x.b); }",
        "var a = {b: 5}; var b = null; function f(x=a) { alert(x.b); }");
  }

  public void testComputedPropertyNames() {
    // We don't support computed properties.
    testSame(
        lines(
            "var foo = {['ba' + 'r']: {}};",
            "var foobar = foo.bar;",
            "foobar.baz = 5;",
            "use(foo.bar.baz);"));
  }

  public void testAliasInTemplateString() {
    test(
        "const a = {b: 5}; const c = a; alert(`${c.b}`);",
        "const a = {b: 5}; const c = null; alert(`${a.b}`);");
  }

  public void testClassStaticInheritance_method() {
    test(
        "class A { static s() {} } class B extends A {} const C = B; C.s();",
        "class A { static s() {} } class B extends A {} const C = null; B.s();");

    testSame("class A { static s() {} } class B extends A {} B.s();");
    testSame("class A {} A.s = function() {}; class B extends A {} B.s();");
  }

  public void testClassStaticInheritance_propertyAlias() {
    testSame("class A {} A.staticProp = 6; class B extends A {} let b = new B;");

    test(
        "class A {} A.staticProp = 6; class B extends A {} use(B.staticProp);",
        "class A {} A.staticProp = 6; class B extends A {} use(A.staticProp);");
  }

  public void testClassStaticInheritance_classExpression() {
    test(
        "var A = class {}; A.staticProp = 6; var B = class extends A {}; use(B.staticProp);",
        "var A = class {}; A.staticProp = 6; var B = class extends A {}; use(A.staticProp);");

    test(
        "var A; A = class {}; A.staticProp = 6; var B = class extends A {}; use(B.staticProp);",
        "var A; A = class {}; A.staticProp = 6; var B = class extends A {}; use(A.staticProp);");
    test(
        "let A = class {}; A.staticProp = 6; let B = class extends A {}; use(B.staticProp);",
        "let A = class {}; A.staticProp = 6; let B = class extends A {}; use(A.staticProp);");

    test(
        "const A = class {}; A.staticProp = 6; const B = class extends A {}; use(B.staticProp);",
        "const A = class {}; A.staticProp = 6; const B = class extends A {}; use(A.staticProp);");
  }

  public void testClassStaticInheritance_propertyWithSubproperty() {
    test(
        "class A {} A.ns = {foo: 'bar'}; class B extends A {} use(B.ns.foo);",
        "class A {} A.ns = {foo: 'bar'}; class B extends A {} use(A.ns.foo);");

    test(
        "class A {} A.ns = {foo: 'bar'}; class B extends A {} B.ns.foo = 'baz'; use(B.ns.foo);",
        "class A {} A.ns = {foo: 'bar'}; class B extends A {} A.ns.foo = 'baz'; use(A.ns.foo);");
  }

  public void testClassStaticInheritance_propertyWithShadowing() {
    testSame("class A {} A.staticProp = 6; class B extends A {} B.staticProp = 7;");

    // Here, B.staticProp is a different property from A.staticProp, so don't rewrite.
    testSame(
        "class A {} A.staticProp = 6; class B extends A {} B.staticProp = 7; use(B.staticProp);");

    // At the time use() is called, B.staticProp is still the same as A.staticProp, so we
    // *could* rewrite it. But instead we back off because of the shadowing afterwards.
    testSame(
        "class A {} A.staticProp = 6; class B extends A {} use(B.staticProp); B.staticProp = 7;");
  }

  public void testClassStaticInheritance_propertyMultiple() {
    test(
        "class A {} A.foo = 5; A.bar = 6; class B extends A {} use(B.foo); use(B.bar);",
        "class A {} A.foo = 5; A.bar = 6; class B extends A {} use(A.foo); use(A.bar);");

    testSame("class A {} A.foo = {bar: 5}; A.baz = 6; class B extends A {} B.baz = 7;");

    test(
        "class A {} A.foo = {}; A.baz = 6; class B extends A {}  B.foo.bar = 5; B.baz = 7;",
        "class A {} A.foo = {}; A.baz = 6; class B extends A {} A.foo.bar = 5; B.baz = 7;");
  }

  public void testClassStaticInheritance_property_chainedSubclasses() {
    test(
        "class A {} A.foo = 5; class B extends A {} class C extends B {} use(C.foo);",
        "class A {} A.foo = 5; class B extends A {} class C extends B {} use(A.foo);");
  }

  public void testClassStaticInheritance_namespacedClass() {
    test(
        lines(
            "var ns1 = {}, ns2 = {};",
            "ns1.A = class {};",
            "ns1.A.staticProp = {foo: 'bar'};",
            "ns2.B = class extends ns1.A {}",
            "use(ns2.B.staticProp.bar);"),
        lines(
            "var ns1 = {}, ns2 = {};",
            "ns1.A = class {};",
            "ns1.A.staticProp = {foo: 'bar'};",
            "ns2.B = class extends ns1.A {}",
            "use(ns1.A.staticProp.bar);"));
  }

  public void testClassStaticInheritance_es5Class() {
    // ES6 classes do not inherit static properties of ES5 class constructors.
    testSame(
        lines(
            "/** @constructor */",
            "function A() {}",
            "A.staticProp = 5;",
            "class B extends A {}",
            "use(B.staticProp);")); // undefined
  }

  public void testClassStaticInheritance_cantDetermineSuperclass() {
    // Currently we only inline inherited properties when the extends clause contains a simple or
    // qualified name.
    testSame(
        lines(
            "class A {}",
            "A.foo = 5;",
            "class B {}",
            "B.foo = 6;",
            "function getSuperclass() { return A; }",
            "class C extends getSuperclass() {}",
            "use(C.foo);"));
  }

  public void testAliasInsideGenerator() {
    test(
        "const a = {b: 5}; const c = a;    function *f() { yield c.b; }",
        "const a = {b: 5}; const c = null; function *f() { yield a.b; }");
  }

  public void testAliasInsideModuleScope() {
    // CollapseProperties currently only handles global variables, so we don't handle aliasing in
    // module bodies here.
    testSame("const a = {b: 5}; const c = a; export default function() {};");
  }

  public void testAliasForSuperclassNamespace() {
    test(
        lines(
            "var ns = {};",
            "class Foo {}",
            "ns.clazz = Foo;",
            "var Bar = class extends ns.clazz.Baz {}"),
        lines(
            "var ns = {};",
            "class Foo {}",
            "ns.clazz = null;",
            "var Bar = class extends Foo.Baz {}"));

    test(
        lines(
            "var ns = {};",
            "class Foo {}",
            "Foo.Builder = class {}",
            "ns.clazz = Foo;",
            "var Bar = class extends ns.clazz.Builder {}"),
        lines(
            "var ns = {};",
            "class Foo {}",
            "Foo.Builder = class {}",
            "ns.clazz = null;",
            "var Bar = class extends Foo.Builder {}"));
  }

  public void testAliasForSuperclass_withStaticInheritance() {
    test(
        lines(
            "var ns = {};",
            "class Foo {}",
            "Foo.baz = 3;",
            "ns.clazz = Foo;",
            "var Bar = class extends ns.clazz {}",
            "use(Bar.baz);"),
        lines(
            "var ns = {};",
            "class Foo {}",
            "Foo.baz = 3;",
            "ns.clazz = null;",
            "var Bar = class extends Foo {}",
            "use(Foo.baz);"));
  }

  public void testStaticInheritance_superclassIsStaticProperty() {
    test(
        lines(
            "class Foo {}",
            "Foo.Builder = class {}",
            "Foo.Builder.baz = 3;",
            "var Bar = class extends Foo.Builder {}",
            "use(Bar.baz);"),
        lines(
            "class Foo {}",
            "Foo.Builder = class {}",
            "Foo.Builder.baz = 3;",
            "var Bar = class extends Foo.Builder {}",
            "use(Foo.Builder.baz);"));
  }

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
            "var ns = {};",
            "class Foo {}",
            "Foo.Builder = class {}",
            "Foo.Builder.baz = 3;",
            "ns.clazz = null;",
            "var Bar = class extends Foo.Builder {}",
            "use(Foo.Builder.baz);"));
  }

  public void testGithubIssue2754() {
    testSame(
        lines(
            "var ns = {};",
            "/** @constructor */",
            "ns.Bean = function() {}",
            "",
            "ns.Bean.x = function(a=null) {",
            "  if (a == null || a === undefined) {",
            "    a = ns.Bean;",
            "  }",
            "  return new a();",
            "}"));
  }

  public void testParameterDefaultIsAlias() {
    testSame(
        lines(
            "/** @constructor */",
            "function a() {}",
            "a.staticProp = 5;",
            "",
            "function f(param = a) {",
            "  return new param();",
            "}"));
  }
}
