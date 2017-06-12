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

  public void testES6VarAliasClassDeclarationWithNew() {
    test(
        "class Foo{}; var /** @const */ alias = Foo; var x = new alias;",
        "class Foo{}; var /** @const */ alias = Foo; var x = new Foo;");
  }

  public void testES6VarAliasClassDeclarationWithoutNew() {
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

  public void testArrayDestructuringVarAssign() {
    test(
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var a = [5, A];",
            "var [one, two] = a;"),
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var a = [5, Foo];",
            "var [one, two] = a;"));
  }

  public void testArrayDestructuringFromFunction() {
    test(
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "function f() {",
            "  return [A, 3];",
            "}",
            "var a, b;",
            "[a, b] = f();"),
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "function f() {",
            "  return [Foo, 3];",
            "}",
            "var a, b;",
            "[a, b] = f();"));
  }

  public void testArrayDestructuringSwapIsNotInlined() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var temp = 3;",
            "[A, temp] = [temp, A];"));
  }

  public void testArrayDestructuringSwapIsNotInlinedWithClassDeclaration() {
    testSame(
        LINE_JOINER.join(
            "class Foo {};",
            "var /** @const */ A = Foo;",
            "var temp = 3;",
            "[A, temp] = [temp, A];"));
  }

  public void testArrayDestructuringAndRedefinedAliasesNotRenamed() {
    testSame("var x = 0; var /** @const */ alias = x; [x] = [5]; use(alias);");
  }

  public void testArrayDestructuringTwoVarsAndRedefinedAliasesNotRenamed() {
    testSame(
        LINE_JOINER.join(
            "var x = 0;",
            "var /** @const */ alias = x;",
            "var y = 5;",
            "[x] = [y];",
            "use(alias);"));
  }

  public void testObjectDestructuringBasicAssign() {
    test(
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var o = {p: A, q: 5};",
            "var {p, q} = o;"),
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var o = {p: Foo, q: 5};",
            "var {p, q} = o;"));
  }

  public void testObjectDestructuringAssignWithoutDeclaration() {
    test(
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "({a, b} = {a: A, b: A});"),
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "({a, b} = {a: Foo, b: Foo});"));
  }

  public void testObjectDestructuringAssignNewVarNames() {
    test(
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var o = {p: A, q: true};",
            "var {p: newName1, q: newName2} = o;"),
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var o = {p: Foo, q: true};",
            "var {p: newName1, q: newName2} = o;"));
  }

  public void testObjectDestructuringDefaultVals() {
    test(
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var {a = A, b = A} = {a: 13};"),
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "var {a = Foo, b = Foo} = {a: 13};"));
  }

  public void testArrayDestructuringWithParameter() {
    test(
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "function f([name, val]) {",
            "   console.log(name, val);",
            "}",
            "f([A, A]);"),
        LINE_JOINER.join(
            "var Foo = class {};",
            "var /** @const */ A = Foo;",
            "function f([name, val]) {",
            "   console.log(name, val);",
            "}",
            "f([Foo, Foo]);"));
  }

  public void testObjectDestructuringWithParameters() {
   test(
       LINE_JOINER.join(
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
       LINE_JOINER.join(
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

  public void testObjectDestructuringWithParametersAndStyleShortcut() {
   test(
       LINE_JOINER.join(
           "var Foo = class {};",
           "var /** @const */ A = Foo;",
           "function h({",
           "   name,",
           "   val",
           "}) {",
           "   console.log(name, val);",
           "}",
           "h({name: A, val: A});"),
       LINE_JOINER.join(
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

  /**
   * Tests using CONST to show behavior. Compiler inlining support not provided for CONST, may be
   * implemented later.
   */
  public void testSimpleConstAliasInJSDoc() {
    testSame("function Foo(){}; const /** @const */ alias = Foo; /** @type {alias} */ var x;");
  }

  public void testSimpleConstAliasInCode() {
    testSame("function Foo(){}; const /** @const */ alias = Foo; var x = new alias;");
  }

  public void testUnqualifiedHoistedConstAliasesInCode() {
    testSame(
        LINE_JOINER.join(
            "function Foo(){};",
            "function Bar(){ const x = alias; };",
            "const /** @const */ alias = Foo;"));
  }

  public void testTransitiveConstAliases() {
    testSame(
        LINE_JOINER.join(
            "/** @const */ const ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "/** @constructor */ ns.Foo.Bar = function() {};",
            "const /** @const */ alias = ns.Foo;",
            "const /** @const */ alias2 = alias.Bar;",
            "var x = new alias2"));
  }

  public void testUnqualifiedConstAliasChains() {
    testSame(
        LINE_JOINER.join(
            "/** @constructor */ var Foo = function() {};",
            "const /** @const */ alias1 = Foo;",
            "const /** @const */ alias2 = alias1;",
            "var x = new alias2"));
  }

  public void testQualifiedConstAliasChains() {
    testSame(
        LINE_JOINER.join(
            "/** @const */ const ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "const /** @const */ alias1 = ns.Foo;",
            "const /** @const */ alias2 = alias1;",
            "var x = new alias2"));
  }

  public void testConstAliasedEnums() {
    testSame("/** @enum {number} */ var E = { A : 1 }; const /** @const */ alias = E.A; alias;");
  }

  public void testES6ConstAliasClassDeclarationWithNew() {
    testSame("class Foo{}; const /** @const */ alias = Foo; var x = new alias;");
  }

  public void testES6ConstAliasClassDeclarationWithoutNew() {
    testSame("class Foo{}; const /** @const */ alias = Foo; var x = alias;");
  }

  public void testConstArrayDestructuringVarAssign() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "const /** @const */ A = Foo;",
            "var a = [5, A];",
            "var [one, two] = a;"));
  }

  public void testConstArrayDestructuringFromFunction() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "const /** @const */ A = Foo;",
            "function f() {",
            "  return [A, 3];",
            "}",
            "var a, b;",
            "[a, b] = f();"));
  }

  public void testConstObjectDestructuringBasicAssign() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "const /** @const */ A = Foo;",
            "var o = {p: A, q: 5};",
            "var {p, q} = o;"));
  }

  public void testConstObjectDestructuringAssignWithoutDeclaration() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};", "const /** @const */ A = Foo;", "({a, b} = {a: A, b: A});"));
  }

  public void testConstObjectDestructuringAssignNewVarNames() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "const /** @const */ A = Foo;",
            "var o = {p: A, q: true};",
            "var {p: newName1, q: newName2} = o;"));
  }

  public void testConstObjectDestructuringDefaultVals() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "const /** @const */ A = Foo;",
            "var {a = A, b = A} = {a: 13};"));
  }

  public void testConstArrayDestructuringWithParameters() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "const /** @const */ A = Foo;",
            "function f([name, val]) {",
            "   console.log(name, val);",
            "}",
            "f([A, A]);"));
  }

  public void testConstObjectDestructuringWithParameters() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "const /** @const */ A = Foo;",
            "function g({",
            "   name: n,",
            "   val: v",
            "}) {",
            "   console.log(n, v);",
            "}",
            "g({",
            "   name: A,",
            "   val: A",
            "});"));
  }

  public void testConstObjectDestructuringWithParametersAndStyleShortcut() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "const /** @const */ A = Foo;",
            "function h({",
            "   name,",
            "   val",
            "}) {",
            "   console.log(name, val);",
            "}",
            "h({name: A, val: A});"));
  }

  /**
   * Tests using LET to show behavior. Compiler inlining support not provided for LET, may be
   * implemented later.
   */
  public void testSimpleLetAliasInJSDoc() {
    testSame("function Foo(){}; let /** @const */ alias = Foo; /** @type {alias} */ var x;");
  }

  public void testSimpleLetAliasInCode() {
    testSame("function Foo(){}; let /** @const */ alias = Foo; var x = new alias;");
  }

  public void testUnqualifiedHoistedLetAliasesInCode() {
    testSame(
        LINE_JOINER.join(
            "function Foo(){};",
            "function Bar(){ var x = alias; };",
            "let /** @const */ alias = Foo;"));
  }

  public void testTransitiveLetAliases() {
    testSame(
        LINE_JOINER.join(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "/** @constructor */ ns.Foo.Bar = function() {};",
            "let /** @const */ alias = ns.Foo;",
            "let /** @const */ alias2 = alias.Bar;",
            "var x = new alias2"));
  }

  public void testUnqualifiedLetAliasChains() {
    testSame(
        LINE_JOINER.join(
            "/** @constructor */ var Foo = function() {};",
            "let /** @const */ alias1 = Foo;",
            "let /** @const */ alias2 = alias1;",
            "var x = new alias2"));
  }

  public void testQualifiedLetAliasChains() {
    testSame(
        LINE_JOINER.join(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Foo = function() {};",
            "let /** @const */ alias1 = ns.Foo;",
            "let /** @const */ alias2 = alias1;",
            "var x = new alias2"));
  }

  public void testLetAliasedEnums() {
    testSame("/** @enum {number} */ var E = { A : 1 }; let /** @const */ alias = E.A; alias;");
  }

  public void testES6LetAliasClassDeclarationWithoutNew() {
    testSame("class Foo{}; let /** @const */ alias = Foo; var x = alias;");
  }

  public void testArrayDestructuringLetAssign() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "let /** @const */ A = Foo;",
            "var a = [5, A];",
            "var [one, two] = a;"));
  }

  public void testLetArrayDestructuringFromFunction() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "let /** @const */ A = Foo;",
            "function f() {",
            "  return [A, 3];",
            "}",
            "var a, b;",
            "[a, b] = f();"));
  }

  public void testLetObjectDestructuringBasicAssign() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "let /** @const */ A = Foo;",
            "var o = {p: A, q: 5};",
            "var {p, q} = o;"));
  }

  public void testWithLetObjectDestructuringAssignWithoutDeclaration() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "let /** @const */ A = Foo;",
            "({a, b} = {a: A, b: A});"));
  }

  public void testLetObjectDestructuringAssignNewVarNames() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "let /** @const */ A = Foo;",
            "var o = {p: A, q: true};",
            "var {p: newName1, q: newName2} = o;"));
  }

  public void testLetObjectDestructuringDefaultVals() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "let /** @const */ A = Foo;", 
            "var {a = A, b = A} = {a: 13};"));
  }

  public void testLetArrayDestructuringWithParameter() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "let /** @const */ A = Foo;",
            "function f([name, val]) {",
            "   console.log(name, val);",
            "}",
            "f([A, A]);"));
  }

  public void testLetObjectDestructuringWithParameters() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "let /** @const */ A = Foo;",
            "function g({",
            "   name: n,",
            "   val: v",
            "}) {",
            "   console.log(n, v);",
            "}",
            "g({",
            "   name: A,",
            "   val: A",
            "});"));
  }

  public void testLetObjectDestructuringWithParametersAndStyleShortcut() {
    testSame(
        LINE_JOINER.join(
            "var Foo = class {};",
            "let /** @const */ A = Foo;",
            "function h({",
            "   name,",
            "   val",
            "}) {",
            "   console.log(name, val);",
            "}",
            "h({name: A, val: A});"));
  }
}
