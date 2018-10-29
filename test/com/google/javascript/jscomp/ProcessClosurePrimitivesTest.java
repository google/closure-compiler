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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.BASE_CLASS_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.CLASS_NAMESPACE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.CLOSURE_DEFINES_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.DUPLICATE_NAMESPACE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.EXPECTED_OBJECTLIT_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.FUNCTION_NAMESPACE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.GOOG_BASE_CLASS_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_ARGUMENT_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_CSS_RENAMING_MAP;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_DEFINE_NAME_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_PROVIDE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_STYLE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.LATE_PROVIDE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.MISSING_DEFINE_ANNOTATION;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.MISSING_PROVIDE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.NULL_ARGUMENT_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.TOO_MANY_ARGUMENTS_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.WEAK_NAMESPACE_TYPE;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.XMODULE_REQUIRE_ERROR;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ProcessClosurePrimitives}.
 *
 */

@RunWith(JUnit4.class)
public final class ProcessClosurePrimitivesTest extends CompilerTestCase {
  private String additionalCode;
  private String additionalEndCode;
  private boolean addAdditionalNamespace;
  private boolean preserveGoogProvidesAndRequires;
  private boolean banGoogBase;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    additionalCode = null;
    additionalEndCode = null;
    addAdditionalNamespace = false;
    preserveGoogProvidesAndRequires = false;
    banGoogBase = false;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();

    if (banGoogBase) {
      options.setWarningLevel(
          DiagnosticGroups.USE_OF_GOOG_BASE, CheckLevel.ERROR);
    }

    return options;
  }

  @Override protected CompilerPass getProcessor(final Compiler compiler) {
    if ((additionalCode == null) && (additionalEndCode == null)) {
      return new ProcessClosurePrimitives(
          compiler, null, CheckLevel.ERROR, preserveGoogProvidesAndRequires);
    } else {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          // Process the original code.
          new ProcessClosurePrimitives(
                  compiler, null, CheckLevel.OFF, preserveGoogProvidesAndRequires)
              .process(externs, root);

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

          // Process the tree a second time.
          new ProcessClosurePrimitives(
                  compiler, null, CheckLevel.ERROR, preserveGoogProvidesAndRequires)
              .process(externs, root);
        }
      };
    }
  }

  @Override protected int getNumRepetitions() {
    return 1;
  }

  private void testModule(String[] moduleInputs, String[] expected) {
    test(createModuleStar(moduleInputs), expected);
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
    test("goog.provide('foo'); foo = 0;",
         "var foo = 0;");
    test("goog.provide('foo'); foo = {a: 0};",
         "var foo = {a: 0};");
    test("goog.provide('foo'); foo = function(){};",
         "var foo = function(){};");
    test("goog.provide('foo'); foo = ()=>{};",
            "var foo = ()=>{};");

    test("goog.provide('foo'); var foo = 0;",
         "var foo = 0;");
    test("goog.provide('foo'); let foo = 0;",
            "let foo = 0;");
    test("goog.provide('foo'); const foo = 0;",
            "const foo = 0;");

    test("goog.provide('foo'); var foo = {a: 0};",
         "var foo = {a: 0};");
    test("goog.provide('foo'); var foo = function(){};",
         "var foo = function(){};");
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
    test("goog.provide('foo'); foo = 0; foo = 1",
         "var foo = 0; foo = 1;");
  }

  @Test
  public void testRemovalMultipleAssignment2() {
    test("goog.provide('foo'); var foo = 0; foo = 1",
         "var foo = 0; foo = 1;");
    test("goog.provide('foo'); let foo = 0; let foo = 1",
        "let foo = 0; let foo = 1;");
  }

  @Test
  public void testRemovalMultipleAssignment3() {
    test("goog.provide('foo'); foo = 0; var foo = 1",
         "foo = 0; var foo = 1;");
    test("goog.provide('foo'); foo = 0; let foo = 1",
        "foo = 0; let foo = 1;");
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
    test("goog.provide('foo'); if (true) { var foo = 0 } else { foo = 1 }",
         "if (true) { var foo = 0 } else { foo = 1 }");
  }

  @Test
  public void testRemovalMultipleAssignmentInIf2() {
    test("goog.provide('foo'); if (true) { foo = 0 } else { var foo = 1 }",
         "if (true) { foo = 0 } else { var foo = 1 }");
  }

  @Test
  public void testRemovalMultipleAssignmentInIf3() {
    test("goog.provide('foo'); if (true) { foo = 0 } else { foo = 1 }",
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
    test("goog.provide('foo.bar');" + "var foo = {};" + rest,
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
    test("var x = 42; goog.provide('x');",
        "var x = 42; /** @const */ var x = {}");
  }

  @Test
  public void testProvideErrorCases() {
    testError("goog.provide();", NULL_ARGUMENT_ERROR);
    testError("goog.provide(5);", INVALID_ARGUMENT_ERROR);
    testError("goog.provide([]);", INVALID_ARGUMENT_ERROR);
    testError("goog.provide({});", INVALID_ARGUMENT_ERROR);
    testError("goog.provide('foo', 'bar');", TOO_MANY_ARGUMENTS_ERROR);
    testError("goog.provide('foo'); goog.provide('foo');", DUPLICATE_NAMESPACE_ERROR);
    testError("goog.provide('foo.bar'); goog.provide('foo'); goog.provide('foo');",
        DUPLICATE_NAMESPACE_ERROR);

    testError("goog.provide(`template`);", INVALID_ARGUMENT_ERROR);
    testError("goog.provide(tagged`template`);", INVALID_ARGUMENT_ERROR);
    testError("goog.provide(`${template}Sub`);", INVALID_ARGUMENT_ERROR);
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

    test("goog.provide('foo'); /** @type {Object<string>} */ var foo = {};",
        "/** @type {Object<string>} */ var foo={};");
  }

  @Test
  public void testProvideInESModule() {
    testError("import {x} from 'y'; goog.provide('z');", INVALID_CLOSURE_CALL_ERROR);
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
    test("goog.provide('foo.bar.baz'); goog.require('foo.bar.baz');",
         "/** @const */ var foo={}; /** @const */ foo.bar={}; /** @const */ foo.bar.baz={};");
    test("goog.provide('foo'); var x = 3; goog.require('foo'); something();",
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
  public void testRequireBadArguments() {
    testError("goog.require();", NULL_ARGUMENT_ERROR);
    testError("goog.require(5);", INVALID_ARGUMENT_ERROR);
    testError("goog.require([]);", INVALID_ARGUMENT_ERROR);
    testError("goog.require({});", INVALID_ARGUMENT_ERROR);
    testError("goog.require(`template`);", INVALID_ARGUMENT_ERROR);
    testError("goog.require(tagged`template`);", INVALID_ARGUMENT_ERROR);
    testError("goog.require(`${template}Sub`);", INVALID_ARGUMENT_ERROR);
  }

  @Test
  public void testRequireTypeBadArguments() {
    testError("goog.requireType();", NULL_ARGUMENT_ERROR);
    testError("goog.requireType(5);", INVALID_ARGUMENT_ERROR);
    testError("goog.requireType([]);", INVALID_ARGUMENT_ERROR);
    testError("goog.requireType({});", INVALID_ARGUMENT_ERROR);
    testError("goog.requireType(`template`);", INVALID_ARGUMENT_ERROR);
    testError("goog.requireType(tagged`template`);", INVALID_ARGUMENT_ERROR);
    testError("goog.requireType(`${template}Sub`);", INVALID_ARGUMENT_ERROR);
  }

  @Test
  public void testLateProvideForRequire() {
    testError("goog.require('foo'); goog.provide('foo');", LATE_PROVIDE_ERROR);
    testError("goog.require('foo.bar'); goog.provide('foo.bar');", LATE_PROVIDE_ERROR);
    testError("goog.provide('foo.bar'); goog.require('foo'); goog.provide('foo');",
        LATE_PROVIDE_ERROR);
  }

  @Test
  public void testLateProvideForRequireType() {
    testNoWarning("goog.requireType('foo'); goog.provide('foo');");
    testNoWarning("goog.requireType('foo.bar'); goog.provide('foo.bar');");
    testNoWarning("goog.provide('foo.bar'); goog.requireType('foo'); goog.provide('foo');");
  }

  @Test
  public void testMissingProvideForRequire() {
    testError("goog.require('foo');", MISSING_PROVIDE_ERROR);
    testError("goog.provide('foo'); goog.require('Foo');", MISSING_PROVIDE_ERROR);
    testError("goog.provide('foo'); goog.require('foo.bar');", MISSING_PROVIDE_ERROR);
    testError("goog.provide('foo'); var EXPERIMENT_FOO = true; "
        + "if (EXPERIMENT_FOO) {goog.require('foo.bar');}",
        MISSING_PROVIDE_ERROR);
  }

  @Test
  public void testMissingProvideForRequireType() {
    testError("goog.requireType('foo');", MISSING_PROVIDE_ERROR);
    testError("goog.provide('foo'); goog.requireType('Foo');", MISSING_PROVIDE_ERROR);
    testError("goog.provide('foo'); goog.requireType('foo.bar');", MISSING_PROVIDE_ERROR);
    testError(
        "goog.provide('foo'); var EXPERIMENT_FOO = true; "
            + "if (EXPERIMENT_FOO) {goog.requireType('foo.bar');}",
        MISSING_PROVIDE_ERROR);
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
  public void testAddDependency() {
    test("goog.addDependency('x.js', ['A', 'B'], []);", "0");

    Compiler compiler = getLastCompiler();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("A")).isTrue();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("B")).isTrue();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("C")).isFalse();
  }

  @Test
  public void testForwardDeclarations() {
    test("goog.forwardDeclare('A.B')", "");

    Compiler compiler = getLastCompiler();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("A.B")).isTrue();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("C.D")).isFalse();

    testError("goog.forwardDeclare();",
        ProcessClosurePrimitives.INVALID_FORWARD_DECLARE);

    testError("goog.forwardDeclare('A.B', 'C.D');",
        ProcessClosurePrimitives.INVALID_FORWARD_DECLARE);

    testError("goog.forwardDeclare(`template`);",
        ProcessClosurePrimitives.INVALID_FORWARD_DECLARE);
    testError("goog.forwardDeclare(`${template}Sub`);",
        ProcessClosurePrimitives.INVALID_FORWARD_DECLARE);
  }

  @Test
  public void testValidSetCssNameMapping() {
    test("goog.setCssNameMapping({foo:'bar',\"biz\":'baz'});", "");
    CssRenamingMap map = getLastCompiler().getCssRenamingMap();
    assertThat(map).isNotNull();
    assertThat(map.get("foo")).isEqualTo("bar");
    assertThat(map.get("biz")).isEqualTo("baz");
  }

  @Test
  public void testValidSetCssNameMappingWithType() {
    test("goog.setCssNameMapping({foo:'bar',\"biz\":'baz'}, 'BY_PART');", "");
    CssRenamingMap map = getLastCompiler().getCssRenamingMap();
    assertThat(map).isNotNull();
    assertThat(map.get("foo")).isEqualTo("bar");
    assertThat(map.get("biz")).isEqualTo("baz");

    test("goog.setCssNameMapping({foo:'bar',biz:'baz','biz-foo':'baz-bar'}," +
        " 'BY_WHOLE');", "");
    map = getLastCompiler().getCssRenamingMap();
    assertThat(map).isNotNull();
    assertThat(map.get("foo")).isEqualTo("bar");
    assertThat(map.get("biz")).isEqualTo("baz");
    assertThat(map.get("biz-foo")).isEqualTo("baz-bar");
  }

  @Test
  public void testSetCssNameMappingByShortHand() {
    testError("goog.setCssNameMapping({shortHandFirst, shortHandSecond});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
  }

  @Test
  public void testSetCssNameMappingByTemplate() {
    testError("goog.setCssNameMapping({foo: `bar`});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
    testError("goog.setCssNameMapping({foo: `${vari}bar`});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
  }

  @Test
  public void testSetCssNameMappingNonStringValueReturnsError() {
    // Make sure the argument is an object literal.
    testError("var BAR = {foo:'bar'}; goog.setCssNameMapping(BAR);", EXPECTED_OBJECTLIT_ERROR);
    testError("goog.setCssNameMapping([]);", EXPECTED_OBJECTLIT_ERROR);
    testError("goog.setCssNameMapping(false);", EXPECTED_OBJECTLIT_ERROR);
    testError("goog.setCssNameMapping(null);", EXPECTED_OBJECTLIT_ERROR);
    testError("goog.setCssNameMapping(undefined);", EXPECTED_OBJECTLIT_ERROR);

    // Make sure all values of the object literal are string literals.
    testError("var BAR = 'bar'; goog.setCssNameMapping({foo:BAR});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
    testError("goog.setCssNameMapping({foo:6});", NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
    testError("goog.setCssNameMapping({foo:false});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
    testError("goog.setCssNameMapping({foo:null});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
    testError("goog.setCssNameMapping({foo:undefined});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
  }

  @Test
  public void testSetCssNameMappingValidity() {
    // Make sure that the keys don't have -'s
    test("goog.setCssNameMapping({'a': 'b', 'a-a': 'c'})", "", warning(INVALID_CSS_RENAMING_MAP));

    // In full mode, we check that map(a-b)=map(a)-map(b)
    test(
        "goog.setCssNameMapping({'a': 'b', 'a-a': 'c'}, 'BY_WHOLE')",
        "",
        warning(INVALID_CSS_RENAMING_MAP));

    // Unknown mapping type
    testError("goog.setCssNameMapping({foo:'bar'}, 'UNKNOWN');",
        INVALID_STYLE_ERROR);
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
    test("goog.require('b.B'); goog.provide('a.A'); a.A = {};",
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

  // Tests that a require not in additional code generates (only) one error.
  @Test
  public void testMissingRequireWithAdditionalProvide() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    testError("goog.require('b.C'); goog.provide('a.A'); a.A = {};",
         MISSING_PROVIDE_ERROR);
  }

  // Tests that a requireType not in additional code generates (only) one error.
  @Test
  public void testMissingRequireTypeWithAdditionalProvide() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    testError("goog.requireType('b.C'); goog.provide('a.A'); a.A = {};", MISSING_PROVIDE_ERROR);
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
    additionalCode = "a.B = {};";  // as if a.B was after a.A originally
    addAdditionalNamespace = true;
    test(
        "goog.provide('a.A'); a.A = {};",
        lines(
            "/** @const */",
            "var a = {};",
            "a.B = {};",
            "a.A = {};"));
  }

  // Another version of above.
  @Test
  public void testReorderedProvides2() {
    additionalEndCode = "a.B = {};";
    addAdditionalNamespace = true;
    test("goog.provide('a.A'); a.A = {};",
         "/** @const */ var a={};a.A={};a.B={};");
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
            "goog.provide('a.b');",
            "goog.provide('a.b.c');",
            "a.b.c;",
            "a.b = function(x,y) {};"),
        lines(
            "/** @const */",
            "var a = {};",
            "/** @const */",
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
            "goog.provide('a.b');",
            "goog.provide('a.b.c');",
            "a.b = function(x,y) {};",
            "a.b.c;"),
        lines(
            "/** @const */",
            "var a = {};",
            "/** @const */",
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
            "goog.provide('a.b');",
            "a.b = function(x,y) {};",
            "goog.provide('a.b.c');",
            "a.b.c;"),
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
            "goog.provide('a.b');",
            "a.b = function(x,y) {};",
            "goog.provide('a.b.c');",
            "a.b.c;"),
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
        lines(
            "/** @const */",
            "goog.a={};",
            "if(x)",
            "  goog.a.b=1;",
            "else",
            "  goog.a.b=2;"));
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
        lines(
            "/** @const */",
            "goog.a={};",
            "if(x)",
            "  goog.a.b=1;",
            "else",
            "  goog.a.b=2;"));
  }

  @Test
  public void testInvalidProvide() {
    test(
        "goog.provide('a.class');",
        "/** @const */ var a = {}; /** @const */ a.class = {};");
    testError("goog.provide('class.a');", INVALID_PROVIDE_ERROR);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT3);
    testError("goog.provide('a.class');", INVALID_PROVIDE_ERROR);
    testError("goog.provide('class.a');", INVALID_PROVIDE_ERROR);
  }

  @Test
  public void testInvalidRequire() {
    testError("goog.provide('a.b'); var x = x || goog.require('a.b');",
        INVALID_CLOSURE_CALL_ERROR);
    testError("goog.provide('a.b'); x = goog.require('a.b');",
        INVALID_CLOSURE_CALL_ERROR);
    testError("goog.provide('a.b'); function f() { goog.require('a.b'); }",
        INVALID_CLOSURE_CALL_ERROR);
  }

  @Test
  public void testInvalidRequireType() {
    testError(
        "goog.provide('a.b'); var x = x || goog.requireType('a.b');", INVALID_CLOSURE_CALL_ERROR);
    testError("goog.provide('a.b'); x = goog.requireType('a.b');", INVALID_CLOSURE_CALL_ERROR);
    testError(
        "goog.provide('a.b'); function f() { goog.requireType('a.b'); }",
        INVALID_CLOSURE_CALL_ERROR);
  }

  @Test
  public void testValidGoogMethod() {
    testSame("function f() { goog.isDef('a.b'); }");
    testSame("function f() { goog.inherits(a, b); }");
    testSame("function f() { goog.exportSymbol(a, b); }");
    test("function f() { goog.setCssNameMapping({}); }", "function f() {}");
    testSame("x || goog.isDef('a.b');");
    testSame("x || goog.inherits(a, b);");
    testSame("x || goog.exportSymbol(a, b);");
    testSame("x || void 0");
  }

  private static final String METHOD_FORMAT =
      "function Foo() {} Foo.prototype.method = function() { %s };";

  private static final String FOO_INHERITS =
      "goog.inherits(Foo, BaseFoo);";

  @Test
  public void testInvalidGoogBase1() {
    testError("goog.base(this, 'method');", GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase2() {
    testError("function Foo() {}" +
         "Foo.method = function() {" +
         "  goog.base(this, 'method');" +
         "};", GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase3() {
    testError(String.format(METHOD_FORMAT, "goog.base();"),
         GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase4() {
    testError(String.format(METHOD_FORMAT, "goog.base(this, 'bar');"),
         GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase5() {
    testError(String.format(METHOD_FORMAT, "goog.base('foo', 'method');"),
         GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase6() {
    testError(String.format(METHOD_FORMAT, "goog.base.call(null, this, 'method');"),
         GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase6b() {
    testError(String.format(METHOD_FORMAT, "goog.base.call(this, 'method');"),
         GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase7() {
    testError("function Foo() { goog.base(this); }", GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase8() {
    testError("var Foo = function() { goog.base(this); }", GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase9() {
    testError("var goog = {}; goog.Foo = function() { goog.base(this); }",
        GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase10() {
    testError("class Foo extends BaseFoo { constructor() { goog.base(this); } }",
        GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase11() {
    testError("class Foo extends BaseFoo { someMethod() { goog.base(this, 'someMethod'); } }",
        GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testValidGoogBase1() {
    test(String.format(METHOD_FORMAT, "goog.base(this, 'method');"),
         String.format(METHOD_FORMAT, "Foo.superClass_.method.call(this)"));
  }

  @Test
  public void testValidGoogBase2() {
    test(String.format(METHOD_FORMAT, "goog.base(this, 'method', 1, 2);"),
         String.format(METHOD_FORMAT,
             "Foo.superClass_.method.call(this, 1, 2)"));
  }

  @Test
  public void testValidGoogBase3() {
    test(String.format(METHOD_FORMAT, "return goog.base(this, 'method');"),
         String.format(METHOD_FORMAT,
             "return Foo.superClass_.method.call(this)"));
  }

  @Test
  public void testValidGoogBase4() {
    test("function Foo() { goog.base(this, 1, 2); }" + FOO_INHERITS,
         "function Foo() { BaseFoo.call(this, 1, 2); } " + FOO_INHERITS);
  }

  @Test
  public void testValidGoogBase5() {
    test("var Foo = function() { goog.base(this, 1); };" + FOO_INHERITS,
         "var Foo = function() { BaseFoo.call(this, 1); }; " + FOO_INHERITS);
  }

  @Test
  public void testValidGoogBase6() {
    test("var goog = {}; goog.Foo = function() { goog.base(this); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);",
         "var goog = {}; goog.Foo = function() { goog.BaseFoo.call(this); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);");
  }

  @Test
  public void testBanGoogBase() {
    banGoogBase = true;
    testError(
        "function Foo() { goog.base(this, 1, 2); }" + FOO_INHERITS,
        ProcessClosurePrimitives.USE_OF_GOOG_BASE);
  }

  @Test
  public void testInvalidBase1() {
    testError(
        "var Foo = function() {};" + FOO_INHERITS +
        "Foo.base(this, 'method');", BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase2() {
    testError("function Foo() {}" + FOO_INHERITS +
        "Foo.method = function() {" +
        "  Foo.base(this, 'method');" +
        "};", BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase3() {
    testError(String.format(FOO_INHERITS + METHOD_FORMAT, "Foo.base();"),
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase4() {
    testError(String.format(FOO_INHERITS + METHOD_FORMAT, "Foo.base(this, 'bar');"),
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase5() {
    testError(String.format(FOO_INHERITS + METHOD_FORMAT,
        "Foo.base('foo', 'method');"),
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase7() {
    testError("function Foo() { Foo.base(this); };" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase8() {
    testError("var Foo = function() { Foo.base(this); };" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase9() {
    testError("var goog = {}; goog.Foo = function() { goog.Foo.base(this); };"
        + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase10() {
    testError("function Foo() { Foo.base(this); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase11() {
    testError("function Foo() { Foo.base(this, 'method'); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase12() {
    testError("function Foo() { Foo.base(this, 1, 2); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase13() {
    testError(
        "function Bar(){ Bar.base(this, 'constructor'); }" +
        "goog.inherits(Bar, Goo);" +
        "function Foo(){ Bar.base(this, 'constructor'); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase14() {
    testError("class Foo extends BaseFoo { constructor() { Foo.base(this); } }",
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase14b() {
    testError("class Foo extends BaseFoo { method() { Foo.base(this, 'method'); } }",
        BASE_CLASS_ERROR);
  }

  @Test
  public void testValidBase1() {
    test(FOO_INHERITS
         + String.format(METHOD_FORMAT, "Foo.base(this, 'method');"),
         FOO_INHERITS
         + String.format(METHOD_FORMAT, "Foo.superClass_.method.call(this)"));
  }

  @Test
  public void testValidBase2() {
    test(FOO_INHERITS
         + String.format(METHOD_FORMAT, "Foo.base(this, 'method', 1, 2);"),
         FOO_INHERITS
         + String.format(METHOD_FORMAT,
             "Foo.superClass_.method.call(this, 1, 2)"));
  }

  @Test
  public void testValidBase3() {
    test(FOO_INHERITS
         + String.format(METHOD_FORMAT, "return Foo.base(this, 'method');"),
         FOO_INHERITS
         + String.format(METHOD_FORMAT,
             "return Foo.superClass_.method.call(this)"));
  }

  @Test
  public void testValidBase4() {
    test("function Foo() { Foo.base(this, 'constructor', 1, 2); }"
         + FOO_INHERITS,
         "function Foo() { BaseFoo.call(this, 1, 2); } " + FOO_INHERITS);
  }

  @Test
  public void testValidBase5() {
    test("var Foo = function() { Foo.base(this, 'constructor', 1); };"
         + FOO_INHERITS,
         "var Foo = function() { BaseFoo.call(this, 1); }; " + FOO_INHERITS);
  }

  @Test
  public void testValidBase6() {
    test("var goog = {}; goog.Foo = function() {" +
         "goog.Foo.base(this, 'constructor'); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);",
         "var goog = {}; goog.Foo = function() { goog.BaseFoo.call(this); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);");
  }

  @Test
  public void testValidBase7() {
    // No goog.inherits, so this is probably a different 'base' function.
    testSame(""
        + "var a = function() {"
        + "  a.base(this, 'constructor');"
        + "};");
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
            "/** @const */ var apps = {};",
            "apps.foo = {};",
            "/** @const */ apps.foo.B = {};",
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
            "goog.provide('apps.foo'); apps.foo = function() {}; " +
            "goog.require('apps.foo');"
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
  public void testRequireOfBaseGoog() {
    testError("goog.require('goog');", MISSING_PROVIDE_ERROR);
    testError("goog.requireType('goog');", MISSING_PROVIDE_ERROR);
  }

  @Test
  public void testSourcePositionPreservation() {
    test("goog.provide('foo.bar.baz');",
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
  public void testDefineCases() {
    String jsdoc = "/** @define {number} */\n";
    test(jsdoc + "goog.define('name', 1);", jsdoc + "var name = 1");
    test(jsdoc + "goog.define('ns.name', 1);", jsdoc + "ns.name = 1");
  }

  @Test
  public void testDefineErrorCases() {
    String jsdoc = "/** @define {number} */\n";
    testError("goog.define('name', 1);", MISSING_DEFINE_ANNOTATION);
    testError(jsdoc + "goog.define('name.2', 1);", INVALID_DEFINE_NAME_ERROR);
    testError(jsdoc + "goog.define();", NULL_ARGUMENT_ERROR);
    testError(jsdoc + "goog.define('value');", NULL_ARGUMENT_ERROR);
    testError(jsdoc + "goog.define(5);", INVALID_ARGUMENT_ERROR);

    testError(jsdoc + "goog.define(`templateName`, 1);", INVALID_ARGUMENT_ERROR);
    testError(jsdoc + "goog.define(`${template}Name`, 1);", INVALID_ARGUMENT_ERROR);
  }

  @Test
  public void testDefineInExterns() {
    String jsdoc = "/** @define {number} */\n";
    allowExternsChanges();
    testErrorExterns(jsdoc + "goog.define('value');");

    testErrorExterns("goog.define('name');", MISSING_DEFINE_ANNOTATION);
    testErrorExterns(jsdoc + "goog.define('name.2');", INVALID_DEFINE_NAME_ERROR);
    testErrorExterns(jsdoc + "goog.define();", NULL_ARGUMENT_ERROR);
    testErrorExterns(jsdoc + "goog.define(5);", INVALID_ARGUMENT_ERROR);

    testErrorExterns("/** @define {!number} */ goog.define('name');");
  }

  private void testErrorExterns(String externs) {
    testNoWarning(externs, "");
  }

  private void testErrorExterns(String externs, DiagnosticType error) {
    testError(externs, "", error);
  }

  @Test
  public void testDefineValues() {
    testSame("var CLOSURE_DEFINES = {'FOO': 'string'};");
    testSame("var CLOSURE_DEFINES = {'FOO': true};");
    testSame("var CLOSURE_DEFINES = {'FOO': false};");
    testSame("var CLOSURE_DEFINES = {'FOO': 1};");
    testSame("var CLOSURE_DEFINES = {'FOO': 0xABCD};");
    testSame("var CLOSURE_DEFINES = {'FOO': -1};");
    testSame("let CLOSURE_DEFINES = {'FOO': 'string'};");
    testSame("const CLOSURE_DEFINES = {'FOO': 'string'};");
  }

  @Test
  public void testDefineValuesErrors() {
    testError("var CLOSURE_DEFINES = {'FOO': a};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'FOO': 0+1};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'FOO': 'value' + 'value'};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'FOO': !true};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'FOO': -true};", CLOSURE_DEFINES_ERROR);

    testError("var CLOSURE_DEFINES = {SHORTHAND};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'TEMPLATE': `template`};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'TEMPLATE': `${template}Sub`};", CLOSURE_DEFINES_ERROR);
  }

  @Test
  public void testOtherBaseCall() {
    testSame("class Foo extends BaseFoo { method() { baz.base('arg'); } }");
  }
}
