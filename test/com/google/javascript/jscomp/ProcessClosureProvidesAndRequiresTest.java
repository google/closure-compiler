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
import static com.google.javascript.jscomp.ProcessClosurePrimitives.CLASS_NAMESPACE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.FUNCTION_NAMESPACE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_PROVIDE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.WEAK_NAMESPACE_TYPE;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.XMODULE_REQUIRE_ERROR;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ProcessClosureProvidesAndRequires.ProvidedName;
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
  private String additionalCode;
  private String additionalEndCode;
  private boolean addAdditionalNamespace;
  private boolean preserveGoogProvidesAndRequires;

  private ProcessClosureProvidesAndRequires createClosureProcessor(
      Compiler compiler, CheckLevel requireCheckLevel) {
    return new ProcessClosureProvidesAndRequires(
        compiler, null, requireCheckLevel, preserveGoogProvidesAndRequires);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    additionalCode = null;
    additionalEndCode = null;
    addAdditionalNamespace = false;
    preserveGoogProvidesAndRequires = false;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (Node externs, Node root) -> {
      if ((additionalCode == null) && (additionalEndCode == null)) {
        verifyCollectProvidedNamesDoesntChangeAst(externs, root, CheckLevel.ERROR, compiler);
        ProcessClosureProvidesAndRequires processor =
            createClosureProcessor(compiler, CheckLevel.ERROR);
        processor.rewriteProvidesAndRequires(externs, root);
      } else {
        // Process the original code.
        verifyCollectProvidedNamesDoesntChangeAst(externs, root, CheckLevel.OFF, compiler);
        ProcessClosureProvidesAndRequires processor =
            createClosureProcessor(compiler, CheckLevel.OFF);
        processor.rewriteProvidesAndRequires(externs, root);

        // Inject additional code at the beginning.
        if (additionalCode != null) {
          SourceFile file = SourceFile.fromCode("additionalcode", additionalCode);
          Node scriptNode = root.getFirstChild();
          Node newScriptNode = new CompilerInput(file).getAstRoot(compiler);
          if (addAdditionalNamespace) {
            newScriptNode.getFirstChild().putBooleanProp(Node.IS_NAMESPACE, true);
          }
          while (newScriptNode.getLastChild() != null) {
            Node lastChild = newScriptNode.getLastChild();
            newScriptNode.removeChild(lastChild);
            scriptNode.addChildBefore(lastChild, scriptNode.getFirstChild());
          }
        }

        // Inject additional code at the end.
        if (additionalEndCode != null) {
          SourceFile file = SourceFile.fromCode("additionalendcode", additionalEndCode);
          Node scriptNode = root.getFirstChild();
          Node newScriptNode = new CompilerInput(file).getAstRoot(compiler);
          if (addAdditionalNamespace) {
            newScriptNode.getFirstChild().putBooleanProp(Node.IS_NAMESPACE, true);
          }
          while (newScriptNode.hasChildren()) {
            Node firstChild = newScriptNode.getFirstChild();
            newScriptNode.removeChild(firstChild);
            scriptNode.addChildToBack(firstChild);
          }
        }

        verifyCollectProvidedNamesDoesntChangeAst(externs, root, CheckLevel.ERROR, compiler);
        processor = createClosureProcessor(compiler, CheckLevel.ERROR);
        processor.rewriteProvidesAndRequires(externs, root);
      }
    };
  }

  @Test
  public void testTypedefProvides() {
    test(
        lines(
            "goog.provide('ns');",
            "goog.provide('ns.SomeType');",
            "goog.provide('ns.SomeType.EnumValue');",
            "goog.provide('ns.SomeType.defaultName');",
            // subnamespace assignment happens before parent.
            "/** @enum {number} */",
            "ns.SomeType.EnumValue = { A: 1, B: 2 };",
            // parent namespace isn't ever actually assigned.
            // we're relying on goog.provide to provide it.
            "/** @typedef {{name: string, value: ns.SomeType.EnumValue}} */",
            "ns.SomeType;",
            "/** @const {string} */",
            "ns.SomeType.defaultName = 'foobarbaz';"),
        lines(
            // Created from goog.provide
            "/** @const */ var ns = {};",
            // Created from goog.provide.
            // Cast to unknown is necessary, because the type checker does not expect a symbol
            // used as a typedef to have a value.
            "ns.SomeType = /** @type {?} */ ({});", // created from goog.provide
            "/** @enum {number} */",
            "ns.SomeType.EnumValue = {A:1, B:2};",
            "/** @typedef {{name: string, value: ns.SomeType.EnumValue}} */",
            "ns.SomeType;",
            "/** @const {string} */",
            "ns.SomeType.defaultName = 'foobarbaz';"));
  }

  @Test
  public void testSimpleProvides() {
    test("goog.provide('foo');", "/** @const */ var foo={};");
    test("goog.provide('foo.bar');", "/** @const */ var foo={}; /** @const */ foo.bar={};");
    test(
        "goog.provide('foo.bar.baz');",
        "/** @const */ var foo={}; /** @const */ foo.bar={}; /** @const */ foo.bar.baz={};");
    test(
        "goog.provide('foo.bar.baz.boo');",
        lines(
            "/** @const */ var foo={};",
            "/** @const */ foo.bar={};",
            "/** @const */ foo.bar.baz={};",
            "/** @const */ foo.bar.baz.boo={};"));
    test("goog.provide('goog.bar');", "/** @const */ goog.bar={};"); // goog is special-cased
  }

  @Test
  public void testMultipleProvides() {
    test(
        "goog.provide('foo.bar'); goog.provide('foo.baz');",
        "/** @const */ var foo={}; /** @const */ foo.bar={}; /** @const */ foo.baz={};");

    test(
        "goog.provide('foo.bar.baz'); goog.provide('foo.boo.foo');",
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
            "foo.boo.foo={};"));

    test(
        "goog.provide('foo.bar.baz'); goog.provide('foo.bar.boo');",
        lines(
            "/** @const */",
            "var foo = {};",
            "/** @const */",
            "foo.bar = {};",
            "/** @const */",
            "foo.bar.baz = {};",
            "/** @const */",
            "foo.bar.boo={};"));

    test(
        "goog.provide('foo.bar.baz'); goog.provide('goog.bar.boo');",
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
            "goog.bar.boo = {};"));
  }

  @Test
  public void testRemovalOfProvidedObjLit() {
    test("goog.provide('foo'); foo = 0;", "var foo = 0;");
    test("goog.provide('foo'); foo = {a: 0};", "var foo = {a: 0};");
    test("goog.provide('foo'); foo = function(){};", "var foo = function(){};");
    test("goog.provide('foo'); foo = ()=>{};", "var foo = ()=>{};");

    test("goog.provide('foo'); var foo = 0;", "var foo = 0;");
    test("goog.provide('foo'); let foo = 0;", "let foo = 0;");
    test("goog.provide('foo'); const foo = 0;", "const foo = 0;");

    test("goog.provide('foo'); var foo = {a: 0};", "var foo = {a: 0};");
    test("goog.provide('foo'); var foo = function(){};", "var foo = function(){};");
    test("goog.provide('foo'); var foo = ()=>{};", "var foo = ()=>{};");

    test(
        "goog.provide('foo.bar.Baz'); foo.bar.Baz=function(){};",
        lines(
            "/** @const */",
            "var foo={};",
            "/** @const */",
            "foo.bar = {};",
            "foo.bar.Baz=function(){};"));
    test(
        "goog.provide('foo.bar.moo'); foo.bar.moo={E:1,S:2};",
        lines(
            "/** @const */",
            "var foo={};",
            "/** @const */",
            "foo.bar={};",
            "foo.bar.moo={E:1,S:2};"));

    test(
        "goog.provide('foo.bar.moo'); foo.bar.moo={E:1}; foo.bar.moo={E:2};",
        lines(
            "/** @const */",
            "var foo={};",
            "/** @const */",
            "foo.bar={};",
            "foo.bar.moo={E:1};",
            "foo.bar.moo={E:2};"));

    test("goog.provide('foo'); var foo = class {}", "var foo = class {}");
  }

  @Test
  public void testProvidedDeclaredFunctionError() {
    testError("goog.provide('foo'); function foo(){}", FUNCTION_NAMESPACE_ERROR);
  }

  @Test
  public void testProvidedDeclaredClassError() {
    testError("goog.provide('foo'); class foo {}", CLASS_NAMESPACE_ERROR);
  }

  @Test
  public void testRemovalMultipleAssignment1() {
    test("goog.provide('foo'); foo = 0; foo = 1", "var foo = 0; foo = 1;");
  }

  @Test
  public void testRemovalMultipleAssignment2() {
    test("goog.provide('foo'); var foo = 0; foo = 1", "var foo = 0; foo = 1;");
    test("goog.provide('foo'); let foo = 0; let foo = 1", "let foo = 0; let foo = 1;");
  }

  @Test
  public void testRemovalMultipleAssignment3() {
    test("goog.provide('foo'); foo = 0; var foo = 1", "foo = 0; var foo = 1;");
    test("goog.provide('foo'); foo = 0; let foo = 1", "foo = 0; let foo = 1;");
  }

  @Test
  public void testRemovalMultipleAssignment4() {
    test(
        "goog.provide('foo.bar'); foo.bar = 0; foo.bar = 1",
        "/** @const */ var foo = {}; foo.bar = 0; foo.bar = 1");
  }

  @Test
  public void testNoRemovalFunction1() {
    test(
        "goog.provide('foo'); function f(){foo = 0}",
        "/** @const */ var foo = {}; function f(){foo = 0}");
  }

  @Test
  public void testNoRemovalFunction2() {
    test(
        "goog.provide('foo'); function f(){var foo = 0}",
        "/** @const */ var foo = {}; function f(){var foo = 0}");
  }

  @Test
  public void testNoRemovalFunction3() {
    test(
        "goog.provide('foo'); function f(foo = 0){}",
        "/** @const */ var foo = {}; function f(foo = 0){}");
  }

  @Test
  public void testRemovalMultipleAssignmentInIf1() {
    test(
        "goog.provide('foo'); if (true) { var foo = 0 } else { foo = 1 }",
        "if (true) { var foo = 0 } else { foo = 1 }");
  }

  @Test
  public void testRemovalMultipleAssignmentInIf2() {
    test(
        "goog.provide('foo'); if (true) { foo = 0 } else { var foo = 1 }",
        "if (true) { foo = 0 } else { var foo = 1 }");
  }

  @Test
  public void testRemovalMultipleAssignmentInIf3() {
    test(
        "goog.provide('foo'); if (true) { foo = 0 } else { foo = 1 }",
        "if (true) { var foo = 0 } else { foo = 1 }");
  }

  @Test
  public void testRemovalMultipleAssignmentInIf4() {
    test(
        "goog.provide('foo.bar'); if (true) { foo.bar = 0 } else { foo.bar = 1 }",
        lines(
            "/** @const */ var foo = {};",
            "if (true) {",
            "  foo.bar = 0;",
            "} else {",
            "  foo.bar = 1;",
            "}"));
  }

  @Test
  public void testMultipleDeclarationError1() {
    String rest = "if (true) { foo.bar = 0 } else { foo.bar = 1 }";
    test(
        "goog.provide('foo.bar');" + "var foo = {};" + rest,
        "var foo = {};" + "var foo = {};" + rest);
  }

  @Test
  public void testMultipleDeclarationError2() {
    test(
        lines(
            "goog.provide('foo.bar');",
            "if (true) { var foo = {}; foo.bar = 0 } else { foo.bar = 1 }"),
        lines(
            "var foo = {};",
            "if (true) {",
            "  var foo = {}; foo.bar = 0",
            "} else {",
            "  foo.bar = 1",
            "}"));
  }

  @Test
  public void testMultipleDeclarationError3() {
    test(
        lines(
            "goog.provide('foo.bar');",
            "if (true) { foo.bar = 0 } else { var foo = {}; foo.bar = 1 }"),
        lines(
            "var foo = {};",
            "if (true) {",
            "  foo.bar = 0",
            "} else {",
            "  var foo = {}; foo.bar = 1",
            "}"));
  }

  @Test
  public void testProvideAfterDeclarationError() {
    test("var x = 42; goog.provide('x');", "var x = 42; /** @const */ var x = {}");
  }

  @Test
  public void testProvideAfterDeclaration_noErrorInExterns() {
    test(externs("var x = {};"), srcs("goog.provide('x');"), expected("/** @const */ var x = {}"));
  }

  @Test
  public void testDuplicateProvideDoesntCrash() {
    // The compiler emits a warning elsewhere for this code.
    test(
        "goog.provide('foo'); goog.provide('foo');",
        "/** @const */ var foo = {}; goog.provide('foo');");
    test(
        "goog.provide('foo.bar'); goog.provide('foo'); goog.provide('foo');",
        lines(
            "/** @const */ var foo={};", //
            "/** @const */ foo.bar = {};",
            "goog.provide('foo');"));
  }

  @Test
  public void testPreserveGoogProvidesAndRequires_withSecondPassRun_noDuplicateNamespaceWarning() {
    additionalCode = "";
    preserveGoogProvidesAndRequires = true;

    test(
        lines(
            "goog.provide('foo');", //
            "foo.baz = function() {};"),
        lines(
            "/** @const */ var foo = {};", //
            "goog.provide('foo');",
            "foo.baz = function() {};"));
  }

  @Test
  public void testProvideErrorCases2() {
    test(
        "goog.provide('foo'); /** @type {Object} */ var foo = {};",
        "/** @type {Object} */ var foo={};",
        warning(WEAK_NAMESPACE_TYPE));
    test(
        "goog.provide('foo'); /** @type {!Object} */ var foo = {};",
        "/** @type {!Object} */ var foo={};",
        warning(WEAK_NAMESPACE_TYPE));
    test(
        "goog.provide('foo.bar'); /** @type {Object} */ foo.bar = {};",
        "/** @const */ var foo = {}; /** @type {Object} */ foo.bar = {};",
        warning(WEAK_NAMESPACE_TYPE));
    test(
        "goog.provide('foo.bar'); /** @type {!Object} */ foo.bar = {};",
        "/** @const */ var foo={}; /** @type {!Object} */ foo.bar={};",
        warning(WEAK_NAMESPACE_TYPE));

    test(
        "goog.provide('foo'); /** @type {Object<string>} */ var foo = {};",
        "/** @type {Object<string>} */ var foo={};");
  }

  @Test
  public void testProvideValidObjectType() {
    test(
        "goog.provide('foo'); /** @type {Object<string>} */ var foo = {};",
        "/** @type {Object<string>} */ var foo = {};");
  }

  @Test
  public void testRemovalOfRequires() {
    test("goog.provide('foo'); goog.require('foo');", "/** @const */ var foo={};");
    test(
        "goog.provide('foo.bar'); goog.require('foo.bar');",
        "/** @const */ var foo={}; /** @const */ foo.bar={};");
    test(
        "goog.provide('foo.bar.baz'); goog.require('foo.bar.baz');",
        "/** @const */ var foo={}; /** @const */ foo.bar={}; /** @const */ foo.bar.baz={};");
    test(
        "goog.provide('foo'); var x = 3; goog.require('foo'); something();",
        "/** @const */ var foo={}; var x = 3; something();");
    testSame("foo.require('foo.bar');");
  }

  @Test
  public void testRemovalOfRequireType() {
    test("goog.provide('foo'); goog.requireType('foo');", "/** @const */ var foo={};");
    test(
        "goog.provide('foo.bar'); goog.requireType('foo.bar');",
        "/** @const */ var foo={}; /** @const */ foo.bar={};");
    test(
        "goog.provide('foo.bar.baz'); goog.requireType('foo.bar.baz');",
        "/** @const */ var foo={}; /** @const */ foo.bar={}; /** @const */ foo.bar.baz={};");
    test(
        "goog.provide('foo'); var x = 3; goog.requireType('foo'); something();",
        "/** @const */ var foo={}; var x = 3; something();");
    testSame("foo.requireType('foo.bar');");
  }

  @Test
  public void testPreserveGoogRequire() {
    preserveGoogProvidesAndRequires = true;
    test(
        "goog.provide('foo'); goog.require('foo');",
        "/** @const */ var foo={}; goog.provide('foo'); goog.require('foo');");
    test(
        "goog.provide('foo'); goog.require('foo'); var a = {};",
        "/** @const */ var foo = {}; goog.provide('foo'); goog.require('foo'); var a = {};");
  }

  @Test
  public void testPreserveGoogRequireType() {
    preserveGoogProvidesAndRequires = true;

    test(
        "goog.provide('foo'); goog.requireType('foo');",
        "/** @const */ var foo={}; goog.provide('foo'); goog.requireType('foo');");
    test(
        "goog.provide('foo'); goog.requireType('foo'); var a = {};",
        "/** @const */ var foo = {}; goog.provide('foo'); goog.requireType('foo'); var a = {};");
  }

  @Test
  public void testLateProvideForRequire() {
    // Error reported elsewhere
    test("goog.require('foo'); goog.provide('foo');", "/** @const */ var foo = {};");
  }

  @Test
  public void testLateProvideForRequireType() {
    testNoWarning("goog.requireType('foo'); goog.provide('foo');");
  }

  @Test
  public void testMissingProvideForRequire() {
    test("goog.require('foo');", "");
  }

  @Test
  public void testMissingProvideForRequireType() {
    test("goog.requireType('foo');", "");
  }

  @Test
  public void testProvideInExterns() {
    allowExternsChanges();

    test(
        externs(
            "/** @externs */ goog.provide('animals.Dog');"
                + "/** @constructor */ animals.Dog = function() {}"),
        srcs("goog.require('animals.Dog'); new animals.Dog()"),
        expected("new animals.Dog();"));
  }

  @Test
  public void testForwardDeclarations() {
    test("goog.forwardDeclare('A.B')", "");

    testNoWarning("goog.forwardDeclare();"); // This pass isn't responsible for invalid declares
    testNoWarning("goog.forwardDeclare('C', 'D');");
  }

  @Test
  public void testBadCrossModuleRequire() {
    test(
        createModuleStar("", "goog.provide('goog.ui');", "goog.require('goog.ui');"),
        new String[] {"", "/** @const */ goog.ui = {};", ""},
        warning(XMODULE_REQUIRE_ERROR));
  }

  @Test
  public void testGoodCrossModuleRequire1() {
    test(
        createModuleStar("goog.provide('goog.ui');", "", "goog.require('goog.ui');"),
        new String[] {
          "/** @const */ goog.ui = {};", "", "",
        });
  }

  @Test
  public void testGoodCrossModuleRequire2() {
    test(
        createModuleStar("", "", "goog.provide('goog.ui'); goog.require('goog.ui');"),
        new String[] {
          "", "", "/** @const */ goog.ui = {};",
        });
  }

  @Test
  public void testCrossModuleRequireType() {
    test(
        createModuleStar("goog.requireType('goog.ui');", "", "goog.provide('goog.ui')"),
        new String[] {"", "", "/** @const */ goog.ui = {};"});
    test(
        createModuleStar("", "goog.provide('goog.ui');", "goog.requireType('goog.ui');"),
        new String[] {"", "/** @const */ goog.ui = {};", ""});
  }

  // Tests providing additional code with non-overlapping var namespace.
  @Test
  public void testSimpleAdditionalProvide() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    test("goog.provide('a.A'); a.A = {};",
         "/** @const */ var b={};b.B={}; /** @const */ var a={};a.A={};");
  }

  // Same as above, but with the additional code added after the original.
  @Test
  public void testSimpleAdditionalProvideAtEnd() {
    additionalEndCode = "goog.provide('b.B'); b.B = {};";
    test("goog.provide('a.A'); a.A = {};",
        "/** @const */ var a={}; a.A={}; /** @const */ var b={};b.B={};");
  }

  @Test
  public void testSimpleAdditionalProvide_withPreserveGoogProvidesAndRequires() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    preserveGoogProvidesAndRequires = true;

    test(
        "goog.provide('a.A'); a.A = {};",
        lines(
            "/** @const */ var b={};",
            "goog.provide('b.B');",
            "b.B={};",
            "/** @const */ var a={};",
            "/** @const */ a.A={};",
            "goog.provide('a.A'); "));
  }

  @Test
  public void testNonNamespaceAdditionalProvide_withPreserveGoogProvidesAndRequires() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    preserveGoogProvidesAndRequires = true;

    test(
        "goog.provide('a.A'); a.A = function() {}",
        lines(
            "/** @const */ var b={};",
            "goog.provide('b.B');",
            "b.B={};",
            "/** @const */ var a={};",
            "goog.provide('a.A');",
            "a.A = function() {};"));
  }

  @Test
  public void testNamespaceInExterns() {
    // Note: This style is not recommended but the compiler sort-of supports it.
    test(
        externs("var root = {}; /** @type {number} */ root.someProperty;"),
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
        externs("var root = {}; /** @type {number} */ root.someProperty;"),
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

  // Tests providing additional code with non-overlapping dotted namespace.
  @Test
  public void testSimpleDottedAdditionalProvide() {
    additionalCode = "goog.provide('a.b.B'); a.b.B = {};";
    test("goog.provide('c.d.D'); c.d.D = {};",
        lines(
            "/** @const */",
            "var a={};",
            "/** @const */",
            "a.b={};",
            "a.b.B={};",
            "/** @const */",
            "var c={};",
            "/** @const */",
            "c.d={};",
            "c.d.D={};"));
  }

  @Test
  public void testAdditionalCode_onSingleNameNamespaceWithoutVar() {
    additionalEndCode = "goog.provide('Name.child'); Name.child = 1;";

    test(
        lines(
            "goog.provide('Name');", //
            "Name = class {};"),
        lines(
            "var Name = class {};", //
            "Name.child = 1;"));
  }

  @Test
  public void testTypedefProvide() {
    test(
        lines(
            "goog.provide('foo.Bar');",
            "goog.provide('foo.Bar.Baz');",
            "/** @typedef {!Array<string>} */",
            "foo.Bar;",
            "foo.Bar.Baz = {};"),
        lines(
            "/** @const */ var foo = {};", //
            // Cast to unknown to support also having @typedef. We want the type system to treat
            // this as a typedef, but need an actual namespace to hang foo.Bar.Baz on.
            "foo.Bar = /** @type {?} */ ({});",
            "/** @typedef {!Array<string>} */ foo.Bar;",
            "foo.Bar.Baz = {}"));
  }

  @Test
  public void testTypedefAdditionalProvide_noChildNamespace() {
    additionalEndCode = "goog.require('foo.Cat'); goog.require('foo.Bar');";

    test(
        lines(
            "goog.provide('foo.Cat');",
            "goog.provide('foo.Bar');",
            "/** @typedef {!Array<string>} */",
            "foo.Bar;",
            "foo.Cat={};"),
        lines(
            "/** @const */ var foo={};", //
            "/** @typedef {!Array<string>} */ foo.Bar;", //
            "foo.Cat = {};"));
  }

  @Test
  public void testTypedefAdditionalProvide_withChildNamespace() {
    additionalEndCode = "goog.require('foo.Cat'); goog.require('foo.Bar');";

    test(
        lines(
            "goog.provide('foo.Cat');",
            "goog.provide('foo.Bar');",
            "goog.provide('foo.Bar.Baz');",
            "/** @typedef {!Array<string>} */",
            "foo.Bar;",
            "foo.Cat={};",
            "foo.Bar.Baz = {};"),
        lines(
            "/** @const */ var foo={};", //
            "foo.Bar = /** @type {?} */ ({});", //
            "/** @typedef {!Array<string>} */ foo.Bar;", //
            "foo.Cat = {};",
            "foo.Bar.Baz = {};"));
  }

  // Tests providing additional code with overlapping var namespace.
  @Test
  public void testOverlappingAdditionalProvide() {
    additionalCode = "goog.provide('a.B'); a.B = {};";
    test("goog.provide('a.A'); a.A = {};",
         "/** @const */ var a={};a.B={};a.A={};");
  }

  // Tests providing additional code with overlapping var namespace.
  @Test
  public void testOverlappingAdditionalProvideAtEnd() {
    additionalEndCode = "goog.provide('a.B'); a.B = {};";
    test("goog.provide('a.A'); a.A = {};",
         "/** @const */ var a={};a.A={};a.B={};");
  }

  // Tests providing additional code with overlapping dotted namespace.
  @Test
  public void testOverlappingDottedAdditionalProvide() {
    additionalCode = "goog.provide('a.b.B'); a.b.B = {};";
    test(
        "goog.provide('a.b.C'); a.b.C = {};",
        lines(
            "/** @const */",
            "var a={};",
            "/** @const */ a.b={};",
            "a.b.B={};",
            "a.b.C={};"));
  }

  // Tests that a require of additional code generates no error.
  @Test
  public void testRequireOfAdditionalProvide() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    test(
        "goog.require('b.B'); goog.provide('a.A'); a.A = {};",
        "/** @const */ var b={};b.B={}; /** @const */ var a={};a.A={};");
  }

  // Tests that a requireType of additional code generates no error.
  @Test
  public void testRequireTypeOfAdditionalProvide() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    test(
        "goog.requireType('b.B'); goog.provide('a.A'); a.A = {};",
        "/** @const */ var b={};b.B={}; /** @const */ var a={};a.A={};");
  }

  // Tests that a require not in additional code generates no errors (it's reported elsewhere)
  @Test
  public void testMissingRequireWithAdditionalProvide() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    test(
        "goog.require('b.C'); goog.provide('a.A'); a.A = {};",
        "/** @const */ var b={};b.B={};/** @const */ var a={};a.A={}");
  }

  // Tests that a requireType not in additional code generates no errors (it's reported elsewhere)
  @Test
  public void testMissingRequireTypeWithAdditionalProvide() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    test(
        "goog.requireType('b.C'); goog.provide('a.A'); a.A = {};",
        "/** @const */ var b={};b.B={};/** @const */ var a={};a.A={};");
  }

  // Tests that a require in additional code generates no error.
  @Test
  public void testLateRequire() {
    additionalEndCode = "goog.require('a.A');";
    test("goog.provide('a.A'); a.A = {};", "/** @const */ var a={}; a.A={};");
  }

  // Tests that a requireType in additional code generates no error.
  @Test
  public void testLateRequireType() {
    additionalEndCode = "goog.requireType('a.A');";
    test("goog.provide('a.A'); a.A = {};", "/** @const */ var a={}; a.A={};");
  }

  // Tests a case where code is reordered after processing provides and then
  // provides are processed again.
  @Test
  public void testReorderedProvides() {
    additionalCode = "a.B = {};"; // as if a.B was after a.A originally
    addAdditionalNamespace = true;
    test(
        "goog.provide('a.A'); a.A = {};",
        lines("/** @const */", "var a = {};", "a.B = {};", "a.A = {};"));
  }

  // Another version of above.
  @Test
  public void testReorderedProvides2() {
    additionalEndCode = "a.B = {};";
    addAdditionalNamespace = true;
    test("goog.provide('a.A'); a.A = {};", "/** @const */ var a={};a.A={};a.B={};");
  }

  // Provide a name before the definition of the class providing the
  // parent namespace.
  @Test
  public void testProvideOrder1() {
    additionalEndCode = "";
    addAdditionalNamespace = false;
    // TODO(johnlenz):  This test confirms that the constructor (a.b) isn't
    // improperly removed, but this result isn't really what we want as the
    // reassign of a.b removes the definition of "a.b.c".
    test(
        lines(
            "goog.provide('a.b');", "goog.provide('a.b.c');", "a.b.c;", "a.b = function(x,y) {};"),
        lines(
            "/** @const */",
            "var a = {};",
            "a.b = {};",
            "/** @const */",
            "a.b.c = {};",
            "a.b.c;",
            "a.b = function(x,y) {};"));
  }

  // Provide a name after the definition of the class providing the
  // parent namespace.
  @Test
  public void testProvideOrder2() {
    additionalEndCode = "";
    addAdditionalNamespace = false;
    // TODO(johnlenz):  This test confirms that the constructor (a.b) isn't
    // improperly removed, but this result isn't really what we want as
    // namespace placeholders for a.b and a.b.c remain.
    test(
        lines(
            "goog.provide('a.b');", "goog.provide('a.b.c');", "a.b = function(x,y) {};", "a.b.c;"),
        lines(
            "/** @const */",
            "var a = {};",
            "a.b = {};",
            "/** @const */",
            "a.b.c = {};",
            "a.b = function(x,y) {};",
            "a.b.c;"));
  }

  // Provide a name after the definition of the class providing the
  // parent namespace.
  @Test
  public void testProvideOrder3a() {
    test(
        lines(
            "goog.provide('a.b');", "a.b = function(x,y) {};", "goog.provide('a.b.c');", "a.b.c;"),
        lines(
            "/** @const */",
            "var a = {};",
            "a.b = function(x,y) {};",
            "/** @const */",
            "a.b.c = {};",
            "a.b.c;"));
  }

  @Test
  public void testProvideOrder3b() {
    additionalEndCode = "";
    addAdditionalNamespace = false;
    // This tests a cleanly provided name, below a function namespace.
    test(
        lines(
            "goog.provide('a.b');", "a.b = function(x,y) {};", "goog.provide('a.b.c');", "a.b.c;"),
        lines(
            "/** @const */",
            "var a = {};",
            "a.b = function(x,y) {};",
            "/** @const */",
            "a.b.c = {};",
            "a.b.c;"));
  }

  @Test
  public void testProvideOrder4a() {
    test(
        lines(
            "goog.provide('goog.a');",
            "goog.provide('goog.a.b');",
            "if (x) {",
            "  goog.a.b = 1;",
            "} else {",
            "  goog.a.b = 2;",
            "}"),
        lines("/** @const */", "goog.a={};", "if(x)", "  goog.a.b=1;", "else", "  goog.a.b=2;"));
  }

  @Test
  public void testProvideOrder4b() {
    additionalEndCode = "";
    addAdditionalNamespace = false;
    // This tests a cleanly provided name, below a namespace.
    test(
        lines(
            "goog.provide('goog.a');",
            "goog.provide('goog.a.b');",
            "if (x) {",
            "  goog.a.b = 1;",
            "} else {",
            "  goog.a.b = 2;",
            "}"),
        lines("/** @const */", "goog.a={};", "if(x)", "  goog.a.b=1;", "else", "  goog.a.b=2;"));
  }

  @Test
  public void testInvalidProvide() {
    test("goog.provide('a.class');", "/** @const */ var a = {}; /** @const */ a.class = {};");
    testError("goog.provide('class.a');", INVALID_PROVIDE_ERROR);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT3);
    testError("goog.provide('a.class');", INVALID_PROVIDE_ERROR);
    testError("goog.provide('class.a');", INVALID_PROVIDE_ERROR);
  }

  @Test
  public void testInvalidRequireScope() {
    test(
        "goog.provide('a.b'); var x = x || goog.require('a.b');",
        "/** @const */ var a={};/** @const */ a.b={};var x=x||goog.require(\"a.b\")");
    test(
        "goog.provide('a.b'); x = goog.require('a.b');",
        "/** @const */ var a={};/** @const */ a.b={}; x= goog.require(\"a.b\")");
    test(
        "goog.provide('a.b'); function f() { goog.require('a.b'); }",
        "/** @const */ var a={};/** @const */ a.b={};function f(){goog.require('a.b')}");
  }

  @Test
  public void testImplicitAndExplicitProvide() {
    test(
        "var goog = {}; goog.provide('goog.foo.bar'); goog.provide('goog.foo');",
        lines(
            "var goog = {};",
            "/** @const */",
            "goog.foo = {};",
            "/** @const */",
            "goog.foo.bar = {};"));
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
          "goog.provide('apps');", "goog.provide('apps.foo.A');", "goog.provide('apps.foo.B');"
        },
        new String[] {
          "/** @const */ var apps = {}; /** @const */ apps.foo = {};",
          "/** @const */ apps.foo.A = {};",
          "/** @const */ apps.foo.B = {};",
        });
  }

  @Test
  public void testImplicitProvideInIndependentModules3() {
    testModule(
        new String[] {
          "var goog = {};", "goog.provide('goog.foo.A');", "goog.provide('goog.foo.B');"
        },
        new String[] {
          "var goog = {}; /** @const */ goog.foo = {};",
          "/** @const */ goog.foo.A = {};",
          "/** @const */ goog.foo.B = {};",
        });
  }

  @Test
  public void testProvideInIndependentModules1() {
    testModule(
        new String[] {
          "goog.provide('apps');", "goog.provide('apps.foo');", "goog.provide('apps.foo.B');"
        },
        new String[] {
          "/** @const */ var apps = {}; /** @const */ apps.foo = {};",
          "",
          "/** @const */ apps.foo.B = {};",
        });
  }

  @Test
  public void testProvideInIndependentModules2() {
    // TODO(nicksantos): Make this an error.
    testModule(
        new String[] {
          "goog.provide('apps');",
          "goog.provide('apps.foo'); apps.foo = {};",
          "goog.provide('apps.foo.B');"
        },
        new String[] {
          "/** @const */ var apps = {};", "apps.foo = {};", "/** @const */ apps.foo.B = {};",
        });
  }

  @Test
  public void testProvideInIndependentModules2b() {
    // TODO(nicksantos): Make this an error.
    testModule(
        new String[] {
          "goog.provide('apps');",
          "goog.provide('apps.foo'); apps.foo = function() {};",
          "goog.provide('apps.foo.B');"
        },
        new String[] {
          "/** @const */ var apps = {};",
          "apps.foo = function() {};",
          "/** @const */ apps.foo.B = {};",
        });
  }

  @Test
  public void testProvideInIndependentModules3() {
    testModule(
        new String[] {
          "goog.provide('apps');",
          "goog.provide('apps.foo.B');",
          "goog.provide('apps.foo'); goog.require('apps.foo');"
        },
        new String[] {
          "/** @const */ var apps = {}; /** @const */ apps.foo = {};",
          "/** @const */ apps.foo.B = {};",
          "",
        });
  }

  @Test
  public void testProvideInIndependentModules3b() {
    // TODO(nicksantos): Make this an error.
    testModule(
        new String[] {
          "goog.provide('apps');",
          "goog.provide('apps.foo.B');",
          "goog.provide('apps.foo'); apps.foo = function() {}; " + "goog.require('apps.foo');"
        },
        new String[] {
          "/** @const */ var apps = {};",
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
          "goog.provide('apps');",
          "goog.provide('apps.foo.bar.B');",
          "goog.provide('apps.foo.bar.C');"
        },
        new String[] {
          "/** @const */ var apps={}; /** @const */ apps.foo={}; /** @const */ apps.foo.bar={}",
          "/** @const */ apps.foo.bar.B = {};",
          "/** @const */ apps.foo.bar.C = {};",
        });
  }

  @Test
  public void testSourcePositionPreservation() {
    test(
        "goog.provide('foo.bar.baz');",
        "/** @const */ var foo = {};"
            + "/** @const */ foo.bar = {};"
            + "/** @const */ foo.bar.baz = {};");

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
  public void testNoStubForProvidedTypedef() {
    test("goog.provide('x'); /** @typedef {number} */ var x;", "/** @typedef {number} */ var x;");
  }

  @Test
  public void testNoStubForProvidedTypedef2() {
    test(
        "goog.provide('x.y'); /** @typedef {number} */ x.y;",
        "/** @const */ var x = {}; /** @typedef {number} */ x.y;");
  }

  @Test
  public void testNoStubForProvidedTypedef4() {
    test(
        "goog.provide('x.y.z'); /** @typedef {number} */ x.y.z;",
        "/** @const */ var x = {}; /** @const */ x.y = {}; /** @typedef {number} */ x.y.z;");
  }

  @Test
  public void testProvideRequireSameFile() {
    test("goog.provide('x');\ngoog.require('x');", "/** @const */ var x = {};");
    test("goog.provide('x');\ngoog.requireType('x');", "/** @const */ var x = {};");
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
    ProcessClosureProvidesAndRequires processor =
        createClosureProcessor(compiler, CheckLevel.ERROR);
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
      Node externs, Node root, CheckLevel requireCheckLevel, Compiler compiler) {
    // Validate that this does not modify the AST at all!
    Node originalExterns = externs.cloneTree();
    Node originalRoot = root.cloneTree();
    ProcessClosureProvidesAndRequires processor =
        createClosureProcessor(compiler, requireCheckLevel);
    processor.collectProvidedNames(externs, root);

    assertNode(externs).isEqualIncludingJsDocTo(originalExterns);
    assertNode(root).isEqualIncludingJsDocTo(originalRoot);
  }

  private void testModule(String[] moduleInputs, String[] expected) {
    test(createModuleStar(moduleInputs), expected);
  }
}
