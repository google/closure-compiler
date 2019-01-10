/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.InlineAliases.ALIAS_CYCLE;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link InlineAliases}. */
@RunWith(JUnit4.class)
public class InlineAliasesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new InlineAliases(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguage(LanguageMode.ECMASCRIPT_2015);
    return options;
  }

  /**
   * Returns the number of times the pass should be run before results are
   * verified.
   *
   * This pass is not idempotent so we only run it once.
   */
  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testSimpleAliasInJSDoc() {
    test("function Foo(){}; var /** @const */ alias = Foo; /** @type {alias} */ var x;",
        "function Foo(){}; var /** @const */ alias = Foo; /** @type {Foo} */ var x;");

    test(
        lines(
            "var ns={};",
            "function Foo(){};",
            "/** @const */ ns.alias = Foo;",
            "/** @type {ns.alias} */ var x;"),
        lines(
            "var ns={};",
            "function Foo(){};",
            "/** @const */ ns.alias = Foo;",
            "/** @type {Foo} */ var x;"));

    test(
        lines(
            "var ns={};",
            "function Foo(){};",
            "/** @const */ ns.alias = Foo;",
            "/** @type {ns.alias.Subfoo} */ var x;"),
        lines(
            "var ns={};",
            "function Foo(){};",
            "/** @const */ ns.alias = Foo;",
            "/** @type {Foo.Subfoo} */ var x;"));
  }

  @Test
  public void testSimpleAliasInCode() {
    test("function Foo(){}; var /** @const */ alias = Foo; var x = new alias;",
        "function Foo(){}; var /** @const */ alias = Foo; var x = new Foo;");

    test("var ns={}; function Foo(){}; /** @const */ ns.alias = Foo; var x = new ns.alias;",
        "var ns={}; function Foo(){}; /** @const */ ns.alias = Foo; var x = new Foo;");

    test("var ns={}; function Foo(){}; /** @const */ ns.alias = Foo; var x = new ns.alias.Subfoo;",
        "var ns={}; function Foo(){}; /** @const */ ns.alias = Foo; var x = new Foo.Subfoo;");
  }

  @Test
  public void testAliasQualifiedName() {
    test(
        lines(
            "var ns = {};",
            "ns.Foo = function(){};",
            "/** @const */ ns.alias = ns.Foo;",
            "/** @type {ns.alias.Subfoo} */ var x;"),
        lines(
            "var ns = {};",
            "ns.Foo = function(){};",
            "/** @const */ ns.alias = ns.Foo;",
            "/** @type {ns.Foo.Subfoo} */ var x;"));

    test(
        lines(
            "var ns = {};",
            "ns.Foo = function(){};",
            "/** @const */ ns.alias = ns.Foo;",
            "var x = new ns.alias.Subfoo;"),
        lines(
            "var ns = {};",
            "ns.Foo = function(){};",
            "/** @const */ ns.alias = ns.Foo;",
            "var x = new ns.Foo.Subfoo;"));
  }

  @Test
  public void testHoistedAliasesInCode() {
    // Unqualified
    test(
        lines(
            "function Foo(){};",
            "function Bar(){ var x = alias; };",
            "var /** @const */ alias = Foo;"),
        lines(
            "function Foo(){};",
            "function Bar(){ var x = Foo; };",
            "var /** @const */ alias = Foo;"));

    // Qualified
    test(
        lines(
            "var ns = {};",
            "ns.Foo = function(){};",
            "function Bar(){ var x = ns.alias; };",
            "/** @const */ ns.alias = ns.Foo;"),
        lines(
            "var ns = {};",
            "ns.Foo = function(){};",
            "function Bar(){ var x = ns.Foo; };",
            "/** @const */ ns.alias = ns.Foo;"));
  }

  @Test
  public void testAliasCycleError() {
    testError(
        lines(
            "/** @const */ var x = y;",
            "/** @const */ var y = x;"),
        ALIAS_CYCLE);
  }

  @Test
  public void testTransitiveAliases() {
    test(
        lines(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "/** @constructor */ ns.Foo.Bar = function() {};",
            "var /** @const */ alias = ns.Foo;",
            "var /** @const */ alias2 = alias.Bar;",
            "var x = new alias2"),
        lines(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "/** @constructor */ ns.Foo.Bar = function() {};",
            "var /** @const */ alias = ns.Foo;",
            "var /** @const */ alias2 = ns.Foo.Bar;",
            // Note: in order to replace "alias2" with "ns.Foo.Bar", we would either have to do
            // multiple traversals of the AST in InlineAliases, or mark alias2 as an alias of
            // ns.Foo.Bar in the GlobalNamespace after replacing "alias2 = alias.Bar" with
            // "alias2 = ns.Foo.Bar"
            "var x = new alias2;"));
  }

  @Test
  public void testAliasChains() {
    // Unqualified
    test(
        lines(
            "/** @constructor */ var Foo = function() {};",
            "var /** @const */ alias1 = Foo;",
            "var /** @const */ alias2 = alias1;",
            "var x = new alias2"),
        lines(
            "/** @constructor */ var Foo = function() {};",
            "var /** @const */ alias1 = Foo;",
            "var /** @const */ alias2 = Foo;",
            "var x = new Foo;"));

    // Qualified
    test(
        lines(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "var /** @const */ alias1 = ns.Foo;",
            "var /** @const */ alias2 = alias1;",
            "var x = new alias2"),
        lines(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "var /** @const */ alias1 = ns.Foo;",
            "var /** @const */ alias2 = ns.Foo;",
            "var x = new ns.Foo;"));
  }

  @Test
  public void testAliasedEnums() {
    test(
        "/** @enum {number} */ var E = { A : 1 }; var /** @const */ alias = E.A; alias;",
        "/** @enum {number} */ var E = { A : 1 }; var /** @const */ alias = E.A; E.A;");
  }

  @Test
  public void testIncorrectConstAnnotationDoesntCrash() {
    testSame("var x = 0; var /** @const */ alias = x; alias = 5; use(alias);");
    testSame("var x = 0; var ns={}; /** @const */ ns.alias = x; ns.alias = 5; use(ns.alias);");
  }

  @Test
  public void testRedefinedAliasesNotRenamed() {
    testSame("var x = 0; var /** @const */ alias = x; x = 5; use(alias);");
  }

  @Test
  public void testDefinesAreNotInlined() {
    testSame("var ns = {}; var /** @define {boolean} */ alias = ns.Foo; var x = new alias;");
  }

  @Test
  public void testConstWithTypesAreNotInlined() {
    testSame(
        lines(
            "var /** @type {number} */ n = 5",
            "var /** @const {number} */ alias = n;",
            "var x = use(alias)"));
  }

  @Test
  public void testPrivateVariablesAreNotInlined() {
    testSame("/** @private */ var x = 0; var /** @const */ alias = x; var y = alias;");
    testSame("var x_ = 0; var /** @const */ alias = x_; var y = alias;");
  }

  @Test
  public void testShadowedAliasesNotRenamed() {
    testSame(
        lines(
            "var ns = {};",
            "ns.Foo = function(){};",
            "var /** @const */ alias = ns.Foo;",
            "function f(alias) {",
            "  var x = alias",
            "}"));

    testSame(
        lines(
            "var ns = {};",
            "ns.Foo = function(){};",
            "var /** @const */ alias = ns.Foo;",
            "function f() {",
            "  var /** @const */ alias = 5;",
            "  var x = alias",
            "}"));

    testSame(
        lines(
            "/** @const */",
            "var x = y;",
            "function f() {",
            "  var x = 123;",
            "  function g() {",
            "    return x;",
            "  }",
            "}"));
  }

  @Test
  public void testShadowedAliasesNotRenamed_withBlockScope() {
    testSame(
        lines(
            "var ns = {};",
            "ns.Foo = function(){};",
            "var /** @const */ alias = ns.Foo;",
            "if (true) {",
            "  const alias = 5;",
            "  var x = alias",
            "}"));

    testSame(
        lines(
            "/** @const */",
            "var x = y;",
            "if (true) {",
            "  const x = 123;",
            "  function g() {",
            "    return x;",
            "  }",
            "}"));
  }

  @Test
  public void testES6VarAliasClassDeclarationWithNew() {
    test(
        "class Foo{}; var /** @const */ alias = Foo; var x = new alias;",
        "class Foo{}; var /** @const */ alias = Foo; var x = new Foo;");
  }

  @Test
  public void testES6VarAliasClassDeclarationWithoutNew() {
    test(
        "class Foo{}; var /** @const */ alias = Foo; var x = alias;",
        "class Foo{}; var /** @const */ alias = Foo; var x = Foo;");
  }

  @Test
  public void testNoInlineAliasesInsideClassConstructor() {
    testSame(
        lines(
            "class Foo {",
            " /** @constructor */",
            " constructor(x) {",
            "     var /** @const */ alias1 = this.x;",
            "     var /** @const */ alias2 = alias1;",
            "     var z = new alias2;",
            " }",
            "}"));
  }

  @Test
  public void testArrayDestructuringVarAssign() {
    test(
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var a = [5, A];",
            "var [one, two] = a;"),
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var a = [5, Foo];",
            "var [one, two] = a;"));
  }

  @Test
  public void testArrayDestructuringFromFunction() {
    test(
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "function f() {",
            "  return [A, 3];",
            "}",
            "var a, b;",
            "[a, b] = f();"),
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "function f() {",
            "  return [Foo, 3];",
            "}",
            "var a, b;",
            "[a, b] = f();"));
  }

  @Test
  public void testArrayDestructuringSwapIsNotInlined() {
    testSame(
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var temp = 3;",
            "[A, temp] = [temp, A];"));
  }

  @Test
  public void testArrayDestructuringSwapIsNotInlinedWithClassDeclaration() {
    testSame(
        lines(
            "class Foo {};",
            "var /** @const */ A = Foo;",
            "var temp = 3;",
            "[A, temp] = [temp, A];"));
  }

  @Test
  public void testArrayDestructuringAndRedefinedAliasesNotRenamed() {
    testSame("var x = 0; var /** @const */ alias = x; [x] = [5]; use(alias);");
  }

  @Test
  public void testArrayDestructuringTwoVarsAndRedefinedAliasesNotRenamed() {
    testSame(
        lines(
            "var x = 0;",
            "var /** @const */ alias = x;",
            "var y = 5;",
            "[x] = [y];",
            "use(alias);"));
  }

  @Test
  public void testObjectDestructuringBasicAssign() {
    test(
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var o = {p: A, q: 5};",
            "var {p, q} = o;"),
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var o = {p: Foo, q: 5};",
            "var {p, q} = o;"));
  }

  @Test
  public void testObjectDestructuringAssignWithoutDeclaration() {
    test(
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "({a, b} = {a: A, b: A});"),
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "({a, b} = {a: Foo, b: Foo});"));
  }

  @Test
  public void testObjectDestructuringAssignNewVarNames() {
    test(
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var o = {p: A, q: true};",
            "var {p: newName1, q: newName2} = o;"),
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var o = {p: Foo, q: true};",
            "var {p: newName1, q: newName2} = o;"));
  }

  @Test
  public void testObjectDestructuringDefaultVals() {
    test(
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var {a = A, b = A} = {a: 13};"),
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var {a = Foo, b = Foo} = {a: 13};"));
  }

  @Test
  public void testArrayDestructuringWithParameter() {
    test(
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "function f([name, val]) {",
            "   console.log(name, val);",
            "}",
            "f([A, A]);"),
        lines(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "function f([name, val]) {",
            "   console.log(name, val);",
            "}",
            "f([Foo, Foo]);"));
  }

  @Test
  public void testObjectDestructuringWithParameters() {
   test(
       lines(
           "var Foo = class {};",
           "var /** @const */ A = Foo;",
           "function g({",
           "   name: n,",
           "   val: v",
           "}) {",
           "   console.log(n, v);",
           "}",
           "g({",
           "   name: A,",
           "   val: A",
           "});"),
       lines(
           "var Foo = class {};",
           "var /** @const */ A = Foo;",
           "function g({",
           "   name: n,",
           "   val: v",
           "}) {",
           "   console.log(n, v);",
           "}",
           "g({",
           "   name: Foo,",
           "   val: Foo",
           "});"));
  }

  @Test
  public void testObjectDestructuringWithParametersAndStyleShortcut() {
   test(
       lines(
           "var Foo = class {};",
           "var /** @const */ A = Foo;",
           "function h({",
           "   name,",
           "   val",
           "}) {",
           "   console.log(name, val);",
           "}",
           "h({name: A, val: A});"),
       lines(
           "var Foo = class {};",
           "var /** @const */ A = Foo;",
           "function h({",
           "   name,",
           "   val",
           "}) {",
           "   console.log(name, val);",
           "}",
           "h({name: Foo, val: Foo});"));
  }

  @Test
  public void testSimpleConstAliasInJSDoc() {
    test(
        "function Foo(){}; const alias = Foo; /** @type {alias} */ var x;",
        "function Foo(){}; const alias = Foo; /** @type {Foo} */ var x;");
  }

  @Test
  public void testSimpleConstAliasInCode() {
    test(
        "function Foo(){}; const alias = Foo; var x = new alias;",
        "function Foo(){}; const alias = Foo; var x = new Foo;");
  }

  /**
   * Note: having @const annotating a let is very strange style, but it's very little extra work to
   * support it.
   */
  @Test
  public void testSimpleLetAliasInJSDoc() {
    test(
        "function Foo(){}; let /** @const */ alias = Foo; /** @type {alias} */ var x;",
        "function Foo(){}; let /** @const */ alias = Foo; /** @type {Foo} */ var x;");
  }

  @Test
  public void testSimpleLetAliasInCode() {
    test(
        "function Foo(){}; let /** @const */ alias = Foo; var x = new alias;",
        "function Foo(){}; let /** @const */ alias = Foo; var x = new Foo;");
  }

  @Test
  public void testClassExtendsAlias1() {
    test(
        "function Foo() {} const alias = Foo; class Bar extends alias {}",
        "function Foo() {} const alias = Foo; class Bar extends Foo {}");
  }

  @Test
  public void testClassExtendsAlias2() {
    test(
        "var ns = {}; ns.Foo = function () {}; const alias = ns.Foo; class Bar extends alias {}",
        "var ns = {}; ns.Foo = function () {}; const alias = ns.Foo; class Bar extends ns.Foo {}");
  }

  @Test
  public void testBlockScopedAlias() {
    testSame("function Foo() {} if (true) { const alias = Foo; alias; }");
  }

  @Test
  public void testVarAliasDeclaredInBlockScope() {
    testSame("function Foo() {} { var /** @const */ alias = Foo; alias; }");
  }

  @Test
  public void testDontInlineEscapedQnameProperty() {
    testSame(
        externs("function use(obj) {}"),
        srcs(
            lines(
                "/** @const */",
                "var ns = {};",
                "ns.foo = 3;",
                "const alias = ns.foo;",
                "use(ns);",
                // "ns" escapes and we don't know if the value of "ns.foo" has also changed, so
                // we cannot replace "alias" with "ns.foo".
                "alert(alias);")));
  }

  @Test
  public void testDoInlineEscapedConstructorProperty() {
    // TODO(b/80429954): this is unsafe. The call to use(Foobar) could have changed the value of
    // Foobar.foo
    test(
        externs("function use(obj) {}"),
        srcs(
            lines(
                "/** @constructor */",
                "function Foobar() {}",
                "Foobar.foo = 3;",
                "const alias = Foobar.foo;",
                "use(Foobar);",
                "alert(alias);")),
        expected(
            lines(
                "/** @constructor */",
                "function Foobar() {}",
                "Foobar.foo = 3;",
                "const alias = Foobar.foo;",
                "use(Foobar);",
                "alert(Foobar.foo);")));
  }
}
