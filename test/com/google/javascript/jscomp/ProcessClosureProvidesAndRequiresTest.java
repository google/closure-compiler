/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ProcessClosureProvidesAndRequires.ProvidedName;
import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link com.google.javascript.jscomp.ProcessClosureProvidesAndRequires} */
@RunWith(JUnit4.class)
public class ProcessClosureProvidesAndRequiresTest extends CompilerTestCase {

  public ProcessClosureProvidesAndRequiresTest() {
    super(MINIMAL_EXTERNS + new TestExternsBuilder().addClosureExterns().build());
  }

  private boolean preserveGoogProvidesAndRequires;

  private ProcessClosureProvidesAndRequires createClosureProcessor(Compiler compiler) {
    return new ProcessClosureProvidesAndRequires(compiler, preserveGoogProvidesAndRequires);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    preserveGoogProvidesAndRequires = false;
    enableTypeCheck();
    enableCreateModuleMap(); // necessary for the typechecker
    replaceTypesWithColors();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (Node externs, Node root) -> {
      verifyCollectProvidedNamesDoesntChangeAst(externs, root, compiler);
      ProcessClosureProvidesAndRequires processor = createClosureProcessor(compiler);
      processor.rewriteProvidesAndRequires(externs, root);
    };
  }

  @Test
  public void testTypedefProvides_withProvidedParent() {
    test(
        srcs(
            lines(
                "goog.provide('ns');",
                "goog.provide('ns.SomeType');",
                "goog.provide('ns.SomeType.EnumValue');",
                "goog.provide('ns.SomeType.defaultName');",
                "goog.provide('ns.SomeType.NestedType');",
                // subnamespace assignment happens before parent.
                "/** @enum {number} */",
                "ns.SomeType.EnumValue = { A: 1, B: 2 };",
                // parent namespace isn't ever actually assigned.
                // we're relying on goog.provide to provide it.
                "/** @typedef {{name: string, value: ns.SomeType.EnumValue}} */",
                "ns.SomeType;",
                "/** @const {string} */",
                "ns.SomeType.defaultName = 'foobarbaz';",
                "/** @typedef {number} */",
                "ns.SomeType.NestedType;")),
        expected(
            lines(
                // Created from goog.provide
                "/** @const */ var ns = {};",
                // Created from goog.provide.
                "/** @const */",
                "ns.SomeType = {};", // created from goog.provide
                "/** @const */",
                "ns.SomeType.NestedType = {};", // created from goog.provide
                "/** @enum {!JSDocSerializer_placeholder_type} */",
                "ns.SomeType.EnumValue = {A:1, B:2};",
                "ns.SomeType;",
                "/** @const */",
                "ns.SomeType.defaultName = 'foobarbaz';",
                "ns.SomeType.NestedType;")));
  }

  @Test
  public void testTypedefProvidesWithExplicitParentNamespace_errorInCollectProvidesMode() {
    testError(
        srcs(
            lines(
                "goog.provide('foo.bar');",
                "goog.provide('foo.bar.Type');",
                "",
                "foo.bar = function() {};",
                "/** @typedef {string} */ foo.bar.Type;")),
        error(ProcessClosureProvidesAndRequires.TYPEDEF_CHILD_OF_PROVIDE));
  }

  @Test
  public void testSimpleProvides() {
    test(srcs("goog.provide('foo');"), expected("/** @const */ var foo={};"));
    test(
        srcs("goog.provide('foo.bar');"),
        expected("/** @const */ var foo={}; /** @const */ foo.bar={};"));
    test(
        srcs("goog.provide('foo.bar.baz');"),
        expected(
            "/** @const */ var foo={}; /** @const */ foo.bar={}; /** @const */ foo.bar.baz={};"));
    test(
        srcs(
            "goog.provide('foo.bar.baz.boo');"),
        expected(
            lines(
                "/** @const */ var foo={};",
                "/** @const */ foo.bar={};",
                "/** @const */ foo.bar.baz={};",
                "/** @const */ foo.bar.baz.boo={};")));
    // goog is special-cased
    test(srcs("goog.provide('goog.bar');"), expected("/** @const */ goog.bar={};"));
  }

  @Test
  public void testMultipleProvides() {
    test(
        srcs(
            "goog.provide('foo.bar'); goog.provide('foo.baz');"),
        expected(
            "/** @const */ var foo={}; /** @const */ foo.bar={}; /** @const */ foo.baz={};"));

    test(
        srcs(
            "goog.provide('foo.bar.baz'); goog.provide('foo.boo.foo');"),
        expected(
            lines(
                "/** @const */",
                "var foo = {};",
                "/** @const */",
                "foo.bar={};",
                "/** @const */",
                "foo.bar.baz={};",
                "/** @const */",
                "foo.boo={};",
                "/** @const */",
                "foo.boo.foo={};")));

    test(
        srcs(
            "goog.provide('foo.bar.baz'); goog.provide('foo.bar.boo');"),
        expected(
            lines(
                "/** @const */",
                "var foo = {};",
                "/** @const */",
                "foo.bar = {};",
                "/** @const */",
                "foo.bar.baz = {};",
                "/** @const */",
                "foo.bar.boo={};")));

    test(
        srcs(
            "goog.provide('foo.bar.baz'); goog.provide('goog.bar.boo');"),
        expected(
            lines(
                "/** @const */",
                "var foo = {};",
                "/** @const */",
                "foo.bar={};",
                "/** @const */",
                "foo.bar.baz={};",
                "/** @const */",
                "goog.bar={};",
                "/** @const */",
                "goog.bar.boo = {};")));
  }

  @Test
  public void testRemovalOfProvidedObjLit() {
    test(srcs("goog.provide('foo'); foo = 0;"), expected("var foo = 0;"));
    test(srcs("goog.provide('foo'); foo = {a: 0};"), expected("var foo = {a: 0};"));

    test(srcs("goog.provide('foo'); foo = function(){};"), expected("var foo = function(){};"));

    test(srcs("goog.provide('foo'); foo = ()=>{};"), expected("var foo = ()=>{};"));

    test(srcs("goog.provide('foo'); var foo = 0;"), expected("var foo = 0;"));

    test(srcs("goog.provide('foo'); let foo = 0;"), expected("let foo = 0;"));

    test(srcs("goog.provide('foo'); const foo = 0;"), expected("const foo = 0;"));

    test(srcs("goog.provide('foo'); var foo = {a: 0};"), expected("var foo = {a: 0};"));

    test(srcs("goog.provide('foo'); var foo = function(){};"), expected("var foo = function(){};"));

    test(srcs("goog.provide('foo'); var foo = ()=>{};"), expected("var foo = ()=>{};"));

    test(
        srcs(

            "goog.provide('foo.bar.Baz'); foo.bar.Baz=function(){};"),
        expected(

            lines(
                "/** @const */",
                "var foo={};",
                "/** @const */",
                "foo.bar = {};",
                "foo.bar.Baz=function(){};")));
    test(
        srcs(

            "goog.provide('foo.bar.moo'); foo.bar.moo={E:1,S:2};"),
        expected(

            lines(
                "/** @const */",
                "var foo={};",
                "/** @const */",
                "foo.bar={};",
                "foo.bar.moo={E:1,S:2};")));

    test(
        srcs(
            "goog.provide('foo.bar.moo'); foo.bar.moo={E:1}; foo.bar.moo={E:2};"),
        expected(
            lines(
                "/** @const */",
                "var foo={};",
                "/** @const */",
                "foo.bar={};",
                "foo.bar.moo={E:1};",
                "foo.bar.moo={E:2};")));

    test(srcs("goog.provide('foo'); var foo = class {}"), expected("var foo = class {}"));
  }

  @Test
  public void testRemovalMultipleAssignment1() {
    test(srcs("goog.provide('foo'); foo = 0; foo = 1"), expected("var foo = 0; foo = 1;"));
  }

  @Test
  public void testRemovalMultipleAssignment2() {
    test(srcs("goog.provide('foo'); var foo = 0; foo = 1"), expected("var foo = 0; foo = 1;"));

    test(
        srcs("goog.provide('foo'); let foo = 0; let foo = 1"),
        expected("let foo = 0; let foo = 1;"));
  }

  @Test
  public void testRemovalMultipleAssignment3() {
    test(srcs("goog.provide('foo'); foo = 0; var foo = 1"), expected("foo = 0; var foo = 1;"));

    test(srcs("goog.provide('foo'); foo = 0; let foo = 1"), expected("foo = 0; let foo = 1;"));
  }

  @Test
  public void testRemovalMultipleAssignment4() {
    test(
        srcs(
            "goog.provide('foo.bar'); foo.bar = 0; foo.bar = 1"),
        expected(
            "/** @const */ var foo = {}; foo.bar = 0; foo.bar = 1"));
  }

  @Test
  public void testNoRemovalFunction1() {
    test(
        srcs(
            lines(
                "goog.provide('foo');",
                "function f(){",
                "/** @suppress {checkTypes} */",
                "  foo = 0;",
                "}")),
        expected(
            lines(
                "/** @const */ var foo = {};",
                "function f(){",
                "  foo = 0;",
                "}")));
  }

  @Test
  public void testNoRemovalFunction2() {
    test(
        srcs(
            "goog.provide('foo'); function f(){var foo = 0}"),
        expected(
            "/** @const */ var foo = {}; function f(){var foo = 0}"));
  }

  @Test
  public void testNoRemovalFunction3() {
    test(
        srcs(
            "goog.provide('foo'); function f(foo = 0){}"),
        expected(
            "/** @const */ var foo = {}; function f(foo = 0){}"));
  }

  @Test
  public void testRemovalMultipleAssignmentInIf1() {
    test(
        srcs(
            "goog.provide('foo'); if (true) { var foo = 0 } else { foo = 1 }"),
        expected(
            "if (true) { var foo = 0 } else { foo = 1 }"));
  }

  @Test
  public void testRemovalMultipleAssignmentInIf2() {
    test(
        srcs(
            "goog.provide('foo'); if (true) { foo = 0 } else { var foo = 1 }"),
        expected(
            "if (true) { foo = 0 } else { var foo = 1 }"));
  }

  @Test
  public void testRemovalMultipleAssignmentInIf3() {
    test(
        srcs(
            "goog.provide('foo'); if (true) { foo = 0 } else { foo = 1 }"),
        expected(
            "if (true) { var foo = 0 } else { foo = 1 }"));
  }

  @Test
  public void testRemovalMultipleAssignmentInIf4() {
    test(
        srcs(
            "goog.provide('foo.bar'); if (true) { foo.bar = 0 } else { foo.bar = 1 }"),
        expected(
            lines(
                "/** @const */ var foo = {};",
                "if (true) {",
                "  foo.bar = 0;",
                "} else {",
                "  foo.bar = 1;",
                "}")));
  }

  @Test
  public void testMultipleDeclarationError1() {
    String rest = "if (true) { foo.bar = 0 } else { foo.bar = 1 }";
    test(
        srcs(
            "goog.provide('foo.bar');" + "var foo = {};" + rest),
        expected(
            "var foo = {};" + "var foo = {};" + rest));
  }

  @Test
  public void testMultipleDeclarationError2() {
    test(
        srcs(
            lines(
                "goog.provide('foo.bar');",
                "if (true) { var foo = {}; foo.bar = 0 } else { foo.bar = 1 }")),
        expected(
            lines(
                "var foo = {};",
                "if (true) {",
                "  var foo = {}; foo.bar = 0",
                "} else {",
                "  foo.bar = 1",
                "}")),
        // TODO(b/149765184): this warning should not happen
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testMultipleDeclarationError3() {
    test(
        srcs(
            lines(
                "goog.provide('foo.bar');",
                "if (true) { foo.bar = 0 } else { var foo = {}; foo.bar = 1 }")),
        expected(
            lines(
                "var foo = {};",
                "if (true) {",
                "  foo.bar = 0",
                "} else {",
                "  var foo = {}; foo.bar = 1",
                "}")),
        // TODO(b/149765184): this warning should not happen
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testProvideAfterDeclarationError() {
    test(srcs("var x = 42; goog.provide('x');"), expected("var x = 42; /** @const */ var x = {}"));
  }

  @Test
  public void testProvideAfterDeclaration_noErrorInExterns() {
    test(
        externs(DEFAULT_EXTERNS, "var x = {};"),
        srcs("goog.provide('x');"),
        expected("/** @const */ var x = {}"));
  }

  @Test
  public void testDuplicateProvideDoesntCrash() {
    ignoreWarnings(ClosurePrimitiveErrors.DUPLICATE_NAMESPACE);
    test(
        srcs(
            "goog.provide('foo'); goog.provide('foo');"),
        expected(
            "/** @const */ var foo = {}; goog.provide('foo');"));

    test(
        srcs(
            "goog.provide('foo.bar'); goog.provide('foo'); goog.provide('foo');"),
        expected(
            lines(
                "/** @const */ var foo={};", //
                "/** @const */ foo.bar = {};",
                "goog.provide('foo');")));
  }

  @Test
  public void testRemovalOfRequires() {
    test(srcs("goog.provide('foo'); goog.require('foo');"), expected("/** @const */ var foo={};"));

    test(
        srcs(
            "goog.provide('foo.bar'); goog.require('foo.bar');"),
        expected(
            "/** @const */ var foo={}; /** @const */ foo.bar={};"));
    test(
        srcs(
            "goog.provide('foo.bar.baz'); goog.require('foo.bar.baz');"),
        expected(

            "/** @const */ var foo={}; /** @const */ foo.bar={}; /** @const */ foo.bar.baz={};"));
    test(
        srcs(
            "goog.provide('foo'); var x = 3; goog.require('foo'); something();"),
        expected(
            "/** @const */ var foo={}; var x = 3; something();"));
    testSame("foo.require('foo.bar');");
  }

  @Test
  public void testRemovalOfRequireType() {
    test(
        srcs("goog.provide('foo'); goog.requireType('foo');"),
        expected("/** @const */ var foo={};"));
    test(
        srcs(
            "goog.provide('foo.bar'); goog.requireType('foo.bar');"),
        expected(
            "/** @const */ var foo={}; /** @const */ foo.bar={};"));
    test(
        srcs(
            "goog.provide('foo.bar.baz'); goog.requireType('foo.bar.baz');"),
        expected(
            "/** @const */ var foo={}; /** @const */ foo.bar={}; /** @const */ foo.bar.baz={};"));
    test(
        srcs(
            "goog.provide('foo'); var x = 3; goog.requireType('foo'); something();"),
        expected(
            "/** @const */ var foo={}; var x = 3; something();"));
    testSame("foo.requireType('foo.bar');");
  }

  @Test
  public void testPreserveGoogRequire() {
    preserveGoogProvidesAndRequires = true;
    test(
        srcs(
            "goog.provide('foo'); goog.require('foo');"),
        expected(
            "/** @const */ var foo={}; goog.provide('foo'); goog.require('foo');"));
    test(
        srcs(
            "goog.provide('foo'); goog.require('foo'); var a = {};"),
        expected(
            "/** @const */ var foo = {}; goog.provide('foo'); goog.require('foo'); var a = {};"));
  }

  @Test
  public void testPreserveGoogRequireType() {
    preserveGoogProvidesAndRequires = true;

    test(
        srcs(
            "goog.provide('foo'); goog.requireType('foo');"),
        expected(
            "/** @const */ var foo={}; goog.provide('foo'); goog.requireType('foo');"));
    test(
        srcs(
            "goog.provide('foo'); goog.requireType('foo'); var a = {};"),
        expected(
            "/** @const */ var foo = {}; goog.provide('foo'); goog.requireType('foo'); var a ="
                + " {};"));
  }

  @Test
  public void testLateProvideForRequire() {
    // Error reported elsewhere
    test(
        srcs("goog.require('foo'); goog.provide('foo');"), expected("/** @const */ var foo = {};"));
  }

  @Test
  public void testLateProvideForRequireType() {
    testNoWarning("goog.requireType('foo'); goog.provide('foo');");
  }

  @Test
  public void testMissingProvideForRequire() {
    test(srcs("goog.require('foo');"), expected(""));
  }

  @Test
  public void testMissingProvideForRequireType() {
    test(srcs("goog.requireType('foo');"), expected(""));
  }

  @Test
  public void testProvideInExterns() {
    allowExternsChanges();

    test(
        externs(
            DEFAULT_EXTERNS,
            "/** @externs */ goog.provide('animals.Dog');"
                + "/** @constructor */ animals.Dog = function() {}"),
        srcs("goog.require('animals.Dog'); new animals.Dog()"),
        expected("new animals.Dog();"));
  }

  @Test
  public void testForwardDeclarations() {
    test(srcs("goog.forwardDeclare('A.B')"), expected(""));

    testWarning(
        "goog.forwardDeclare();",
        TypeCheck.WRONG_ARGUMENT_COUNT); // This pass isn't responsible for invalid declares but
    // the typechecker will warn.
    testWarning("goog.forwardDeclare('C', 'D');", TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  @Test
  public void testGoodCrossModuleRequire1() {
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("goog.provide('goog.ui');")
                .addChunk("")
                .addChunk("goog.require('goog.ui');")
                .build()),
        expected("/** @const */ goog.ui = {};", "", ""));
  }

  @Test
  public void testGoodCrossModuleRequire2() {
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("")
                .addChunk("")
                .addChunk("")
                .addChunk("goog.provide('goog.ui'); goog.require('goog.ui');")
                .build()),
        expected("", "", "", "/** @const */ goog.ui = {};"));
  }

  @Test
  public void testCrossModuleRequireType() {
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("")
                .addChunk("goog.requireType('goog.ui');")
                .addChunk("")
                .addChunk("goog.provide('goog.ui')")
                .build()),
        expected("", "", "", "/** @const */ goog.ui = {};"));
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("")
                .addChunk("")
                .addChunk("goog.provide('goog.ui');")
                .addChunk("goog.requireType('goog.ui');")
                .build()),
        expected("", "", "/** @const */ goog.ui = {};", ""));
  }

  @Test
  public void testNamespaceInExterns() {
    // Note: This style is not recommended but the compiler sort-of supports it.
    test(
        externs(DEFAULT_EXTERNS, "var root = {}; /** @type {number} */ root.someProperty;"),
        srcs(
            lines(
                "goog.provide('root.branch.Leaf')", //
                "root.branch.Leaf = class {};")),
        expected(
            lines(
                "/** @const */",
                "var root = {};",
                "/** @const */ root.branch = {};",
                "root.branch.Leaf = class {};")));
  }

  @Test
  public void testNamespaceInExterns_withExplicitNamespaceReinitialization() {
    // Note: This style is not recommended but the compiler sort-of supports it.
    test(
        externs(DEFAULT_EXTERNS, "var root = {}; /** @type {number} */ root.someProperty;"),
        srcs(
            lines(
                "goog.provide('root.branch.Leaf')", //
                "var root = {};",
                "root.branch.Leaf = class {};")),
        expected(
            lines(
                "var root = {};",
                "/** @const */ root.branch = {};",
                "var root = {};",
                "root.branch.Leaf = class {};")));
  }

  @Test
  public void testTypedefProvide_withChild() {
    test(
        srcs(
            lines(
                "goog.provide('foo.Bar');",
                "goog.provide('foo.Bar.Baz');",
                "/** @typedef {!Array<string>} */",
                "foo.Bar;",
                "foo.Bar.Baz = {};")),
        expected(
            lines(
                "/** @const */ var foo = {};",
                "/** @const */",
                "foo.Bar = {};",
                "foo.Bar;",
                "foo.Bar.Baz = {}")));
  }

  // Provide a name after the definition of the class providing the
  // parent namespace.
  @Test
  public void testProvideOrder3a() {
    test(
        srcs(
            lines(
                "goog.provide('a.b');",
                "a.b = function(x,y) {};",
                "goog.provide('a.b.c');",
                "a.b.c;")),
        expected(
            lines(
                "/** @const */",
                "var a = {};",
                "a.b = function(x,y) {};",
                "/** @const */",
                "a.b.c = {};",
                "a.b.c;")));
  }

  @Test
  public void testProvideOrder4a() {
    test(
        srcs(
            lines(
                "goog.provide('goog.a');",
                "goog.provide('goog.a.b');",
                "if (x) {",
                "  goog.a.b = 1;",
                "} else {",
                "  goog.a.b = 2;",
                "}")),
        expected(
            lines(
                "/** @const */", "goog.a={};", "if(x)", "  goog.a.b=1;", "else", "  goog.a.b=2;")));
  }

  @Test
  public void testInvalidRequireScope() {
    test(
        srcs(
            "goog.provide('a.b'); var x = x || goog.require('a.b');"),
        expected(
            "/** @const */ var a={};/** @const */ a.b={};var x=x||goog.require(\"a.b\")"));
    test(
        srcs(
            "goog.provide('a.b'); x = goog.require('a.b');"),
        expected(
            "/** @const */ var a={};/** @const */ a.b={}; x= goog.require(\"a.b\")"));
    test(
        srcs(
            "goog.provide('a.b'); function f() { goog.require('a.b'); }"),
        expected(
            "/** @const */ var a={};/** @const */ a.b={};function f(){goog.require('a.b')}"));
  }

  @Test
  public void testImplicitAndExplicitProvide() {
    test(
        srcs(

            "goog.provide('goog.foo.bar'); goog.provide('goog.foo');"),
        expected(

            lines("/** @const */", "goog.foo = {};", "/** @const */", "goog.foo.bar = {};")));
  }

  @Test
  public void testImplicitProvideInIndependentModules() {
    testModule(
        new String[] {"", "goog.provide('apps.A');", "goog.provide('apps.B');"},
        new String[] {
          "/** @const */ var apps = {};",
          "/** @const */ apps.A = {};",
          "/** @const */ apps.B = {};",
        });
  }

  @Test
  public void testImplicitProvideInIndependentModules2() {
    testModule(
        new String[] {
          "",
          "goog.provide('apps');", //
          "goog.provide('apps.foo.A');",
          "goog.provide('apps.foo.B');"
        },
        new String[] {
          "/** @const */ var apps = {}; /** @const */ apps.foo = {};",
          "", // empty file
          "/** @const */ apps.foo.A = {};",
          "/** @const */ apps.foo.B = {};",
        });
  }

  @Test
  public void testImplicitProvideInIndependentModules_onGoogNamespace() {
    test(
        externs(""), // exclude the default Closure externs
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk(
                    lines(
                        "/** @const */", //
                        "var goog = {};",
                        "goog.provide = function(ns) {}"))
                .addChunk("goog.provide('goog.foo.A');")
                .addChunk("goog.provide('goog.foo.B');")
                .build()),
        expected(
            lines(
                "/** @const */", //
                "var goog = {};",
                "/** @const */ goog.foo = {};",
                "goog.provide = function(ns) {}"),
            "/** @const */ goog.foo.A = {};",
            "/** @const */ goog.foo.B = {};"));
  }

  @Test
  public void testProvideInIndependentModules1() {
    testModule(
        new String[] {
          "", "goog.provide('apps');", "goog.provide('apps.foo');", "goog.provide('apps.foo.B');"
        },
        new String[] {
          "/** @const */ var apps = {}; /** @const */ apps.foo = {};",
          "",
          "",
          "/** @const */ apps.foo.B = {};",
        });
  }

  @Test
  public void testProvideInIndependentModules2() {
    // TODO(nicksantos): Make this an error.
    testModule(
        new String[] {
          "",
          "goog.provide('apps');",
          "goog.provide('apps.foo'); apps.foo = {};",
          "goog.provide('apps.foo.B');"
        },
        new String[] {
          "/** @const */ var apps = {};", "", "apps.foo = {};", "/** @const */ apps.foo.B = {};",
        });
  }

  @Test
  public void testProvideInIndependentModules2b() {
    // TODO(nicksantos): Make this an error.
    testModule(
        new String[] {
          "",
          "goog.provide('apps');",
          "goog.provide('apps.foo'); apps.foo = function() {};",
          "goog.provide('apps.foo.B');"
        },
        new String[] {
          "/** @const */ var apps = {};",
          "",
          "apps.foo = function() {};",
          "/** @const */ apps.foo.B = {};",
        });
  }

  @Test
  public void testProvideInIndependentModules3() {
    testModule(
        new String[] {
          "",
          "goog.provide('apps');",
          "goog.provide('apps.foo.B');",
          "goog.provide('apps.foo'); goog.require('apps.foo');"
        },
        new String[] {
          "/** @const */ var apps = {}; /** @const */ apps.foo = {};",
          "",
          "/** @const */ apps.foo.B = {};",
          "",
        });
  }

  @Test
  public void testProvideInIndependentModules3b() {
    // TODO(nicksantos): Make this an error.
    testModule(
        new String[] {
          "",
          "goog.provide('apps');",
          "goog.provide('apps.foo.B');",
          "goog.provide('apps.foo'); apps.foo = function() {}; " + "goog.require('apps.foo');"
        },
        new String[] {
          "/** @const */ var apps = {};",
          "",
          "/** @const */ apps.foo.B = {};",
          "apps.foo = function() {};",
        });
  }

  @Test
  public void testProvideInIndependentModules4() {
    // Regression test for bug 261:
    // http://blickly.github.io/closure-compiler-issues/#261
    testModule(
        new String[] {
          "",
          "goog.provide('apps');",
          "goog.provide('apps.foo.bar.B');",
          "goog.provide('apps.foo.bar.C');"
        },
        new String[] {
          "/** @const */ var apps={}; /** @const */ apps.foo={}; /** @const */"
              + " apps.foo.bar={}",
          "",
          "/** @const */ apps.foo.bar.B = {};",
          "/** @const */ apps.foo.bar.C = {};",
        });
  }

  @Test
  public void testSourcePositionPreservation() {
    test(
        srcs("goog.provide('foo.bar.baz');"),
        expected(
            "/** @const */ var foo = {};"
                + "/** @const */ foo.bar = {};"
                + "/** @const */ foo.bar.baz = {};"));

    Node root = getLastCompiler().getRoot();

    Node fooDecl = findQualifiedNameNode("foo", root);
    Node fooBarDecl = findQualifiedNameNode("foo.bar", root);
    Node fooBarBazDecl = findQualifiedNameNode("foo.bar.baz", root);

    assertThat(fooDecl.getLineno()).isEqualTo(1);
    assertThat(fooDecl.getCharno()).isEqualTo(14);

    assertThat(fooBarDecl.getLineno()).isEqualTo(1);
    assertThat(fooBarDecl.getCharno()).isEqualTo(18);

    assertThat(fooBarBazDecl.getLineno()).isEqualTo(1);
    assertThat(fooBarBazDecl.getCharno()).isEqualTo(22);
  }

  @Test
  public void testProvidedTypedef_noParentProvide() {
    test(srcs("goog.provide('x'); /** @typedef {number} */ var x;"), expected("var x;"));
  }

  @Test
  public void testProvidedTypedef_noParentProvide_doublyNestedNamespace() {
    test(
        srcs("goog.provide('x.y'); /** @typedef {number} */ x.y;"),
        expected("/** @const */ var x = {}; /** @const */ x.y = {}; x.y;"));
  }

  @Test
  public void testProvidedTypedef_noParentProvide_triplyNestedNamespace() {
    test(
        srcs("goog.provide('x.y.z'); /** @typedef {number} */ x.y.z;"),
        expected(
            "/** @const */ var x = {}; /** @const */ x.y = {}; /** @const */  x.y.z = {}; x.y.z;"));
  }

  @Test
  public void testProvideRequireSameFile() {
    test(srcs("goog.provide('x');\ngoog.require('x');"), expected("/** @const */ var x = {};"));

    test(srcs("goog.provide('x');\ngoog.requireType('x');"), expected("/** @const */ var x = {};"));
  }

  @Test
  public void testSimpleProvidedNameCollection() {
    Map<String, ProvidedName> providedNameMap =
        getProvidedNameCollection(
            lines(
                "goog.provide('a.b');", //
                "goog.provide('a.b.c');",
                "goog.provide('a.b.d');"));

    assertThat(providedNameMap.keySet()).containsExactly("goog", "a", "a.b", "a.b.c", "a.b.d");
  }

  @Test
  public void testLegacyGoogModule() {
    Map<String, ProvidedName> providedNameMap =
        getProvidedNameCollection(
            lines(
                "goog.module('a.b.c');", //
                "goog.module.declareLegacyNamespace();",
                "",
                "exports = class {};"));

    assertThat(providedNameMap.keySet()).containsExactly("goog", "a", "a.b", "a.b.c");
  }

  @Test
  public void testLegacyGoogModule_inLoadModuleCall() {
    Map<String, ProvidedName> providedNameMap =
        getProvidedNameCollection(
            lines(
                "goog.loadModule(function(exports) {",
                "  goog.module('a.b.c');", //
                "  goog.module.declareLegacyNamespace();",
                "  exports = class {};",
                "  return exports;",
                "});"));

    assertThat(providedNameMap.keySet()).containsExactly("goog", "a", "a.b", "a.b.c");
  }

  @Test
  public void testEsModule_ignored() {
    Map<String, ProvidedName> providedNameMap =
        getProvidedNameCollection("goog.declareModuleId('a.b.c'); export const x = 0;");

    assertThat(providedNameMap.keySet()).containsExactly("goog");
  }

  private Map<String, ProvidedName> getProvidedNameCollection(String js) {
    Compiler compiler = createCompiler();
    ProcessClosureProvidesAndRequires processor = createClosureProcessor(compiler);
    Node jsRoot = compiler.parseTestCode(js);
    Node scopeRoot = IR.root(IR.root(), IR.root(jsRoot));
    Map<String, ProvidedName> providedNameMap =
        processor.collectProvidedNames(scopeRoot.getFirstChild(), scopeRoot.getSecondChild());
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(compiler.getErrors()).isEmpty();
    return providedNameMap;
  }

  /**
   * Validates that running {@link ProcessClosureProvidesAndRequires#collectProvidedNames(Node,
   * Node)} does not modify the AST.
   *
   * <p>This is important because we want to call this method to gain information about
   * goog.provides but preserve the original AST structure for future checks.
   */
  private void verifyCollectProvidedNamesDoesntChangeAst(
      Node externs, Node root, Compiler compiler) {
    // Validate that this does not modify the AST at all!
    Node originalExterns = externs.cloneTree();
    Node originalRoot = root.cloneTree();
    ProcessClosureProvidesAndRequires processor = createClosureProcessor(compiler);
    processor.collectProvidedNames(externs, root);

    assertNode(externs).isEqualIncludingJsDocTo(originalExterns);
    assertNode(root).isEqualIncludingJsDocTo(originalRoot);
  }

  private void testModule(String[] moduleInputs, String[] expected) {
    test(srcs(JSChunkGraphBuilder.forStar().addChunks(moduleInputs).build()), expected(expected));
  }
}
