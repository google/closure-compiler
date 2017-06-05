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

/** Unit tests for {@link InlineAliases}. */
public class InlineAliasesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new InlineAliases(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguage(LanguageMode.ECMASCRIPT_2015);
    options.setJ2clPass(CompilerOptions.J2clPassMode.ON);
    return options;
  }

  public void testSimpleAliasInJSDoc() {
    test("function Foo(){}; var /** @const */ alias = Foo; /** @type {alias} */ var x;",
        "function Foo(){}; var /** @const */ alias = Foo; /** @type {Foo} */ var x;");

    test(
        LINE_JOINER.join(
            "var ns={};",
            "function Foo(){};",
            "/** @const */ ns.alias = Foo;",
            "/** @type {ns.alias} */ var x;"),
        LINE_JOINER.join(
            "var ns={};",
            "function Foo(){};",
            "/** @const */ ns.alias = Foo;",
            "/** @type {Foo} */ var x;"));

    test(
        LINE_JOINER.join(
            "var ns={};",
            "function Foo(){};",
            "/** @const */ ns.alias = Foo;",
            "/** @type {ns.alias.Subfoo} */ var x;"),
        LINE_JOINER.join(
            "var ns={};",
            "function Foo(){};",
            "/** @const */ ns.alias = Foo;",
            "/** @type {Foo.Subfoo} */ var x;"));
  }

  public void testSimpleAliasInCode() {
    test("function Foo(){}; var /** @const */ alias = Foo; var x = new alias;",
        "function Foo(){}; var /** @const */ alias = Foo; var x = new Foo;");

    test("var ns={}; function Foo(){}; /** @const */ ns.alias = Foo; var x = new ns.alias;",
        "var ns={}; function Foo(){}; /** @const */ ns.alias = Foo; var x = new Foo;");

    test("var ns={}; function Foo(){}; /** @const */ ns.alias = Foo; var x = new ns.alias.Subfoo;",
        "var ns={}; function Foo(){}; /** @const */ ns.alias = Foo; var x = new Foo.Subfoo;");
  }

  public void testAliasQualifiedName() {
    test(
        LINE_JOINER.join(
            "var ns = {};",
            "ns.Foo = function(){};",
            "/** @const */ ns.alias = ns.Foo;",
            "/** @type {ns.alias.Subfoo} */ var x;"),
        LINE_JOINER.join(
            "var ns = {};",
            "ns.Foo = function(){};",
            "/** @const */ ns.alias = ns.Foo;",
            "/** @type {ns.Foo.Subfoo} */ var x;"));

    test(
        LINE_JOINER.join(
            "var ns = {};",
            "ns.Foo = function(){};",
            "/** @const */ ns.alias = ns.Foo;",
            "var x = new ns.alias.Subfoo;"),
        LINE_JOINER.join(
            "var ns = {};",
            "ns.Foo = function(){};",
            "/** @const */ ns.alias = ns.Foo;",
            "var x = new ns.Foo.Subfoo;"));
  }

  public void testHoistedAliasesInCode() {
    // Unqualified
    test(
        LINE_JOINER.join(
            "function Foo(){};",
            "function Bar(){ var x = alias; };",
            "var /** @const */ alias = Foo;"),
        LINE_JOINER.join(
            "function Foo(){};",
            "function Bar(){ var x = Foo; };",
            "var /** @const */ alias = Foo;"));

    // Qualified
    test(
        LINE_JOINER.join(
            "var ns = {};",
            "ns.Foo = function(){};",
            "function Bar(){ var x = ns.alias; };",
            "/** @const */ ns.alias = ns.Foo;"),
        LINE_JOINER.join(
            "var ns = {};",
            "ns.Foo = function(){};",
            "function Bar(){ var x = ns.Foo; };",
            "/** @const */ ns.alias = ns.Foo;"));
  }

  public void testAliasCycleError() {
    testError(
        LINE_JOINER.join(
            "/** @const */ var x = y;",
            "/** @const */ var y = x;"),
        ALIAS_CYCLE);
  }

  public void testTransitiveAliases() {
    test(
        LINE_JOINER.join(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "/** @constructor */ ns.Foo.Bar = function() {};",
            "var /** @const */ alias = ns.Foo;",
            "var /** @const */ alias2 = alias.Bar;",
            "var x = new alias2"),
        LINE_JOINER.join(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "/** @constructor */ ns.Foo.Bar = function() {};",
            "var /** @const */ alias = ns.Foo;",
            "var /** @const */ alias2 = ns.Foo.Bar;",
            "var x = new ns.Foo.Bar;"));
  }

  public void testAliasChains() {
    // Unqualified
    test(
        LINE_JOINER.join(
            "/** @constructor */ var Foo = function() {};",
            "var /** @const */ alias1 = Foo;",
            "var /** @const */ alias2 = alias1;",
            "var x = new alias2"),
        LINE_JOINER.join(
            "/** @constructor */ var Foo = function() {};",
            "var /** @const */ alias1 = Foo;",
            "var /** @const */ alias2 = Foo;",
            "var x = new Foo;"));

    // Qualified
    test(
        LINE_JOINER.join(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "var /** @const */ alias1 = ns.Foo;",
            "var /** @const */ alias2 = alias1;",
            "var x = new alias2"),
        LINE_JOINER.join(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "var /** @const */ alias1 = ns.Foo;",
            "var /** @const */ alias2 = ns.Foo;",
            "var x = new ns.Foo;"));
  }

  public void testAliasedEnums() {
    test(
        "/** @enum {number} */ var E = { A : 1 }; var /** @const */ alias = E.A; alias;",
        "/** @enum {number} */ var E = { A : 1 }; var /** @const */ alias = E.A; E.A;");
  }

  public void testIncorrectConstAnnotationDoesntCrash() {
    testSame("var x = 0; var /** @const */ alias = x; alias = 5; use(alias);");
    testSame("var x = 0; var ns={}; /** @const */ ns.alias = x; ns.alias = 5; use(ns.alias);");
  }

  public void testRedefinedAliasesNotRenamed() {
    testSame("var x = 0; var /** @const */ alias = x; x = 5; use(alias);");
  }

  public void testDefinesAreNotInlined() {
    testSame("var ns = {}; var /** @define {boolean} */ alias = ns.Foo; var x = new alias;");
  }

  public void testConstWithTypesAreNotInlined() {
    testSame(
        LINE_JOINER.join(
            "var /** @type {number} */ n = 5",
            "var /** @const {number} */ alias = n;",
            "var x = use(alias)"));
  }

  public void testPrivateVariablesAreNotInlined() {
    testSame("/** @private */ var x = 0; var /** @const */ alias = x; var y = alias;");
    testSame("var x_ = 0; var /** @const */ alias = x_; var y = alias;");
  }

  public void testShadowedAliasesNotRenamed() {
    testSame(
        LINE_JOINER.join(
            "var ns = {};",
            "ns.Foo = function(){};",
            "var /** @const */ alias = ns.Foo;",
            "function f(alias) {",
            "  var x = alias",
            "}"));

    testSame(
        LINE_JOINER.join(
            "var ns = {};",
            "ns.Foo = function(){};",
            "var /** @const */ alias = ns.Foo;",
            "function f() {",
            "  var /** @const */ alias = 5;",
            "  var x = alias",
            "}"));

    testSame(
        LINE_JOINER.join(
            "/** @const */",
            "var x = y;",
            "function f() {",
            "  var x = 123;",
            "  function g() {",
            "    return x;",
            "  }",
            "}"));
  }

  public void testES6AliasClassDeclarationWithNew() {
    test(
        "class Foo{}; var /** @const */ alias = Foo; var x = new alias;",
        "class Foo{}; var /** @const */ alias = Foo; var x = new Foo;");
  }

  public void testES6AliasClassDeclarationWithoutNew() {
    test(
        "class Foo{}; var /** @const */ alias = Foo; var x = alias;",
        "class Foo{}; var /** @const */ alias = Foo; var x = Foo;");
  }

  public void testNoInlineAliasesInsideClassConstructor() {
    testSame(
        LINE_JOINER.join(
            "class Foo {",
            " /** @constructor */",
            " constructor(x) {",
            "     var /** @const */ alias1 = this.x;",
            "     var /** @const */ alias2 = alias1;",
            "     var z = new alias2;",
            " }",
            "}"));
  }

  public void testArrayDestructuringSwap() {
    testSame("var a = 1; var b = 3; [a, b] = [b, a];");
  }

  public void testArrayDestructuringVarAssign() {
    testSame("var foo = [1, 2, 3]; var [one, two, three] = foo;");
  }

  public void testArrayDestructuringFromFunction() {
    testSame(
        LINE_JOINER.join(
            "function f() {",
            "  return [1, 2];",
            "}",
            "var a, b;",
            "[a, b] = f();"));
  }

  public void testObjectDestructuringBasicAssign() {
    testSame("var o = {p: 42, q: true}; var {p, q} = o;");
  }

  public void testObjectDestructuringAssignWithoutDeclaration() {
    testSame("var a, b; ({a, b} = {a: 1, b: 2});");
  }

  public void testObjectDestructuringAssignNewVarNames() {
    testSame("var o = {p: 42, q: true}; var {p: foo, q: bar} = o;");
  }

  public void testObjectDestructuringDefaultVals() {
    testSame("var {a = 10, b = 5} = {a: 3};");
  }

  public void testArrayDestructuringWithParameter() {
    testSame(
        LINE_JOINER.join(
            "function f([name, val]) {",
            "   console.log(name, val);",
            "}",
            "f(['bar', 42]);"));
  }

  public void testObjectDestructuringWithParameters() {
   testSame(
       LINE_JOINER.join(
           "function g({",
           "   name: n,",
           "   val: v",
           "}) {",
           "   console.log(n, v);",
           "}",
           "g({",
           "   name: 'foo',",
           "   val: 7",
           "});"));
  }

  public void testObjectDestructuringWithParametersAndStyleShortcut() {
   testSame(
       LINE_JOINER.join(
           "function h({",
           "   name,",
           "   val",
           "}) {",
           "   console.log(name, val);",
           "}",
           "f({name: 'bar', val: 42});"));
  }

}
