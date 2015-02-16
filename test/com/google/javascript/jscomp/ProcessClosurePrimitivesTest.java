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

import static com.google.javascript.jscomp.ProcessClosurePrimitives.BASE_CLASS_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.CLOSURE_DEFINES_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.DUPLICATE_NAMESPACE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.EXPECTED_OBJECTLIT_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.FUNCTION_NAMESPACE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.GOOG_BASE_CLASS_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_ARGUMENT_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_CLOSURE_CALL_ERROR;
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

/**
 * Tests for {@link ProcessClosurePrimitives}.
 *
 */

public class ProcessClosurePrimitivesTest extends CompilerTestCase {
  private String additionalCode;
  private String additionalEndCode;
  private boolean addAdditionalNamespace;
  private boolean preserveGoogRequires;
  private boolean banGoogBase;

  public ProcessClosurePrimitivesTest() {
    enableLineNumberCheck(true);
  }

  @Override protected void setUp() {
    additionalCode = null;
    additionalEndCode = null;
    addAdditionalNamespace = false;
    preserveGoogRequires = false;
    banGoogBase = false;
    compareJsDoc = false;
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

  @Override public CompilerPass getProcessor(final Compiler compiler) {
    if ((additionalCode == null) && (additionalEndCode == null)) {
      return new ProcessClosurePrimitives(
          compiler, null, CheckLevel.ERROR, preserveGoogRequires);
    } else {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          // Process the original code.
          new ProcessClosurePrimitives(
              compiler, null, CheckLevel.OFF, preserveGoogRequires)
              .process(externs, root);

          // Inject additional code at the beginning.
          if (additionalCode != null) {
            SourceFile file =
                SourceFile.fromCode("additionalcode", additionalCode);
            Node scriptNode = root.getFirstChild();
            Node newScriptNode = new CompilerInput(file).getAstRoot(compiler);
            if (addAdditionalNamespace) {
              newScriptNode.getFirstChild()
                  .putBooleanProp(Node.IS_NAMESPACE, true);
            }
            while (newScriptNode.getLastChild() != null) {
              Node lastChild = newScriptNode.getLastChild();
              newScriptNode.removeChild(lastChild);
              scriptNode.addChildBefore(lastChild, scriptNode.getFirstChild());
            }
          }

          // Inject additional code at the end.
          if (additionalEndCode != null) {
            SourceFile file =
                SourceFile.fromCode("additionalendcode", additionalEndCode);
            Node scriptNode = root.getFirstChild();
            Node newScriptNode = new CompilerInput(file).getAstRoot(compiler);
            if (addAdditionalNamespace) {
              newScriptNode.getFirstChild()
                  .putBooleanProp(Node.IS_NAMESPACE, true);
            }
            while (newScriptNode.getFirstChild() != null) {
              Node firstChild = newScriptNode.getFirstChild();
              newScriptNode.removeChild(firstChild);
              scriptNode.addChildToBack(firstChild);
            }
          }

          // Process the tree a second time.
          new ProcessClosurePrimitives(
              compiler, null, CheckLevel.ERROR, preserveGoogRequires)
              .process(externs, root);
        }
      };
    }
  }

  @Override public int getNumRepetitions() {
    return 1;
  }

  public void testSimpleProvides() {
    test("goog.provide('foo');",
         "var foo={};");
    test("goog.provide('foo.bar');",
         "var foo={}; foo.bar={};");
    test("goog.provide('foo.bar.baz');",
         "var foo={}; foo.bar={}; foo.bar.baz={};");
    test("goog.provide('foo.bar.baz.boo');",
         "var foo={}; foo.bar={}; foo.bar.baz={}; foo.bar.baz.boo={};");
    test("goog.provide('goog.bar');",
         "goog.bar={};");  // goog is special-cased
  }

  public void testMultipleProvides() {
    test("goog.provide('foo.bar'); goog.provide('foo.baz');",
         "var foo={}; foo.bar={}; foo.baz={};");
    test("goog.provide('foo.bar.baz'); goog.provide('foo.boo.foo');",
         "var foo={}; foo.bar={}; foo.bar.baz={}; foo.boo={}; foo.boo.foo={};");
    test("goog.provide('foo.bar.baz'); goog.provide('foo.bar.boo');",
         "var foo={}; foo.bar={}; foo.bar.baz={}; foo.bar.boo={};");
    test("goog.provide('foo.bar.baz'); goog.provide('goog.bar.boo');",
         "var foo={}; foo.bar={}; foo.bar.baz={}; goog.bar={}; " +
         "goog.bar.boo={};");
  }

  public void testRemovalOfProvidedObjLit() {
    test("goog.provide('foo'); foo = 0;",
         "var foo = 0;");
    test("goog.provide('foo'); foo = {a: 0};",
         "var foo = {a: 0};");
    test("goog.provide('foo'); foo = function(){};",
         "var foo = function(){};");
    test("goog.provide('foo'); var foo = 0;",
         "var foo = 0;");
    test("goog.provide('foo'); var foo = {a: 0};",
         "var foo = {a: 0};");
    test("goog.provide('foo'); var foo = function(){};",
         "var foo = function(){};");
    test("goog.provide('foo.bar.Baz'); foo.bar.Baz=function(){};",
         "var foo={}; foo.bar={}; foo.bar.Baz=function(){};");
    test("goog.provide('foo.bar.moo'); foo.bar.moo={E:1,S:2};",
         "var foo={}; foo.bar={}; foo.bar.moo={E:1,S:2};");
    test("goog.provide('foo.bar.moo'); foo.bar.moo={E:1}; foo.bar.moo={E:2};",
         "var foo={}; foo.bar={}; foo.bar.moo={E:1}; foo.bar.moo={E:2};");
  }

  public void testProvidedDeclaredFunctionError() {
    testError("goog.provide('foo'); function foo(){}", FUNCTION_NAMESPACE_ERROR);
  }

  public void testRemovalMultipleAssignment1() {
    test("goog.provide('foo'); foo = 0; foo = 1",
         "var foo = 0; foo = 1;");
  }

  public void testRemovalMultipleAssignment2() {
    test("goog.provide('foo'); var foo = 0; foo = 1",
         "var foo = 0; foo = 1;");
  }

  public void testRemovalMultipleAssignment3() {
    test("goog.provide('foo'); foo = 0; var foo = 1",
         "foo = 0; var foo = 1;");
  }

  public void testRemovalMultipleAssignment4() {
    test("goog.provide('foo.bar'); foo.bar = 0; foo.bar = 1",
         "var foo = {}; foo.bar = 0; foo.bar = 1");
  }

  public void testNoRemovalFunction1() {
    test("goog.provide('foo'); function f(){foo = 0}",
         "var foo = {}; function f(){foo = 0}");
  }

  public void testNoRemovalFunction2() {
    test("goog.provide('foo'); function f(){var foo = 0}",
         "var foo = {}; function f(){var foo = 0}");
  }

  public void testRemovalMultipleAssignmentInIf1() {
    test("goog.provide('foo'); if (true) { var foo = 0 } else { foo = 1 }",
         "if (true) { var foo = 0 } else { foo = 1 }");
  }

  public void testRemovalMultipleAssignmentInIf2() {
    test("goog.provide('foo'); if (true) { foo = 0 } else { var foo = 1 }",
         "if (true) { foo = 0 } else { var foo = 1 }");
  }

  public void testRemovalMultipleAssignmentInIf3() {
    test("goog.provide('foo'); if (true) { foo = 0 } else { foo = 1 }",
         "if (true) { var foo = 0 } else { foo = 1 }");
  }

  public void testRemovalMultipleAssignmentInIf4() {
    test("goog.provide('foo.bar');" +
         "if (true) { foo.bar = 0 } else { foo.bar = 1 }",
         "var foo = {}; if (true) { foo.bar = 0 } else { foo.bar = 1 }");
  }

  public void testMultipleDeclarationError1() {
    String rest = "if (true) { foo.bar = 0 } else { foo.bar = 1 }";
    test("goog.provide('foo.bar');" + "var foo = {};" + rest,
         "var foo = {};" + "var foo = {};" + rest);
  }

  public void testMultipleDeclarationError2() {
    test("goog.provide('foo.bar');" +
         "if (true) { var foo = {}; foo.bar = 0 } else { foo.bar = 1 }",
         "var foo = {};" +
         "if (true) {" +
         "  var foo = {}; foo.bar = 0" +
         "} else {" +
         "  foo.bar = 1" +
         "}");
  }

  public void testMultipleDeclarationError3() {
    test("goog.provide('foo.bar');" +
         "if (true) { foo.bar = 0 } else { var foo = {}; foo.bar = 1 }",
         "var foo = {};" +
         "if (true) {" +
         "  foo.bar = 0" +
         "} else {" +
         "  var foo = {}; foo.bar = 1" +
         "}");
  }

  public void testProvideAfterDeclarationError() {
    test("var x = 42; goog.provide('x');",
         "var x = 42; var x = {}");
  }

  public void testProvideErrorCases() {
    testError("goog.provide();", NULL_ARGUMENT_ERROR);
    testError("goog.provide(5);", INVALID_ARGUMENT_ERROR);
    testError("goog.provide([]);", INVALID_ARGUMENT_ERROR);
    testError("goog.provide({});", INVALID_ARGUMENT_ERROR);
    testError("goog.provide('foo', 'bar');", TOO_MANY_ARGUMENTS_ERROR);
    testError("goog.provide('foo'); goog.provide('foo');", DUPLICATE_NAMESPACE_ERROR);
    testError("goog.provide('foo.bar'); goog.provide('foo'); goog.provide('foo');",
        DUPLICATE_NAMESPACE_ERROR);
  }

  public void testProvideErrorCases2() {
    test("goog.provide('foo'); /** @type {Object} */ var foo = {};",
         "var foo={};", null, WEAK_NAMESPACE_TYPE);
    test("goog.provide('foo'); /** @type {!Object} */ var foo = {};",
        "var foo={};", null, WEAK_NAMESPACE_TYPE);
    test("goog.provide('foo.bar'); /** @type {Object} */ foo.bar = {};",
        "var foo={};foo.bar={};", null, WEAK_NAMESPACE_TYPE);
    test("goog.provide('foo.bar'); /** @type {!Object} */ foo.bar = {};",
        "var foo={};foo.bar={};", null, WEAK_NAMESPACE_TYPE);

    test("goog.provide('foo'); /** @type {Object.<string>} */ var foo = {};",
        "var foo={};");
  }

  public void testProvideValidObjectType() {
    test("goog.provide('foo'); /** @type {Object.<string>} */ var foo = {};",
        "var foo={};");
  }

  public void testRemovalOfRequires() {
    test("goog.provide('foo'); goog.require('foo');",
         "var foo={};");
    test("goog.provide('foo.bar'); goog.require('foo.bar');",
         "var foo={}; foo.bar={};");
    test("goog.provide('foo.bar.baz'); goog.require('foo.bar.baz');",
         "var foo={}; foo.bar={}; foo.bar.baz={};");
    test("goog.provide('foo'); var x = 3; goog.require('foo'); something();",
         "var foo={}; var x = 3; something();");
    testSame("foo.require('foo.bar');");
  }

  public void testPreserveGoogRequires() {
    preserveGoogRequires = true;
    test("goog.provide('foo'); goog.require('foo');",
         "var foo={}; goog.require('foo');");
    test("goog.provide('foo'); goog.require('foo'); var a = {};",
        "var foo = {}; goog.require('foo'); var a = {};");
  }

  public void testRequireErrorCases() {
    testError("goog.require();", NULL_ARGUMENT_ERROR);
    testError("goog.require(5);", INVALID_ARGUMENT_ERROR);
    testError("goog.require([]);", INVALID_ARGUMENT_ERROR);
    testError("goog.require({});", INVALID_ARGUMENT_ERROR);
  }

  public void testLateProvides() {
    testError("goog.require('foo'); goog.provide('foo');", LATE_PROVIDE_ERROR);
    testError("goog.require('foo.bar'); goog.provide('foo.bar');", LATE_PROVIDE_ERROR);
    testError("goog.provide('foo.bar'); goog.require('foo'); goog.provide('foo');",
        LATE_PROVIDE_ERROR);
  }

  public void testMissingProvides() {
    testError("goog.require('foo');", MISSING_PROVIDE_ERROR);
    testError("goog.provide('foo'); goog.require('Foo');", MISSING_PROVIDE_ERROR);
    testError("goog.provide('foo'); goog.require('foo.bar');", MISSING_PROVIDE_ERROR);
    testError("goog.provide('foo'); var EXPERIMENT_FOO = true; "
        + "if (EXPERIMENT_FOO) {goog.require('foo.bar');}",
        MISSING_PROVIDE_ERROR);
  }

  public void testAddDependency() {
    test("goog.addDependency('x.js', ['A', 'B'], []);", "0");

    Compiler compiler = getLastCompiler();
    assertTrue(compiler.getTypeRegistry().isForwardDeclaredType("A"));
    assertTrue(compiler.getTypeRegistry().isForwardDeclaredType("B"));
    assertFalse(compiler.getTypeRegistry().isForwardDeclaredType("C"));
  }

  public void testForwardDeclarations() {
    test("goog.forwardDeclare('A.B')", "");

    Compiler compiler = getLastCompiler();
    assertTrue(compiler.getTypeRegistry().isForwardDeclaredType("A.B"));
    assertFalse(compiler.getTypeRegistry().isForwardDeclaredType("C.D"));

    testError("goog.forwardDeclare();",
        ProcessClosurePrimitives.INVALID_FORWARD_DECLARE);

    testError("goog.forwardDeclare('A.B', 'C.D');",
        ProcessClosurePrimitives.INVALID_FORWARD_DECLARE);
  }

  public void testValidSetCssNameMapping() {
    test("goog.setCssNameMapping({foo:'bar',\"biz\":'baz'});", "");
    CssRenamingMap map = getLastCompiler().getCssRenamingMap();
    assertNotNull(map);
    assertEquals("bar", map.get("foo"));
    assertEquals("baz", map.get("biz"));
  }

  public void testValidSetCssNameMappingWithType() {
    test("goog.setCssNameMapping({foo:'bar',\"biz\":'baz'}, 'BY_PART');", "");
    CssRenamingMap map = getLastCompiler().getCssRenamingMap();
    assertNotNull(map);
    assertEquals("bar", map.get("foo"));
    assertEquals("baz", map.get("biz"));

    test("goog.setCssNameMapping({foo:'bar',biz:'baz','biz-foo':'baz-bar'}," +
        " 'BY_WHOLE');", "");
    map = getLastCompiler().getCssRenamingMap();
    assertNotNull(map);
    assertEquals("bar", map.get("foo"));
    assertEquals("baz", map.get("biz"));
    assertEquals("baz-bar", map.get("biz-foo"));
  }

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

  public void testSetCssNameMappingValidity() {
    // Make sure that the keys don't have -'s
    test("goog.setCssNameMapping({'a': 'b', 'a-a': 'c'})", "", null,
        INVALID_CSS_RENAMING_MAP);

    // In full mode, we check that map(a-b)=map(a)-map(b)
    test("goog.setCssNameMapping({'a': 'b', 'a-a': 'c'}, 'BY_WHOLE')", "", null,
        INVALID_CSS_RENAMING_MAP);

    // Unknown mapping type
    testError("goog.setCssNameMapping({foo:'bar'}, 'UNKNOWN');",
        INVALID_STYLE_ERROR);
  }

  public void testBadCrossModuleRequire() {
    test(
        createModuleStar(
            "",
            "goog.provide('goog.ui');",
            "goog.require('goog.ui');"),
        new String[] {
          "",
          "goog.ui = {};",
          ""
        },
        null,
        XMODULE_REQUIRE_ERROR);
  }

  public void testGoodCrossModuleRequire1() {
    test(
        createModuleStar(
            "goog.provide('goog.ui');",
            "",
            "goog.require('goog.ui');"),
        new String[] {
            "goog.ui = {};",
            "",
            "",
        });
  }

  public void testGoodCrossModuleRequire2() {
    test(
        createModuleStar(
            "",
            "",
            "goog.provide('goog.ui'); goog.require('goog.ui');"),
        new String[] {
            "",
            "",
            "goog.ui = {};",
        });
  }

  // Tests providing additional code with non-overlapping var namespace.
  public void testSimpleAdditionalProvide() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    test("goog.provide('a.A'); a.A = {};",
         "var b={};b.B={};var a={};a.A={};");
  }

  // Same as above, but with the additional code added after the original.
  public void testSimpleAdditionalProvideAtEnd() {
    additionalEndCode = "goog.provide('b.B'); b.B = {};";
    test("goog.provide('a.A'); a.A = {};",
         "var a={};a.A={};var b={};b.B={};");
  }

  // Tests providing additional code with non-overlapping dotted namespace.
  public void testSimpleDottedAdditionalProvide() {
    additionalCode = "goog.provide('a.b.B'); a.b.B = {};";
    test("goog.provide('c.d.D'); c.d.D = {};",
         "var a={};a.b={};a.b.B={};var c={};c.d={};c.d.D={};");
  }

  // Tests providing additional code with overlapping var namespace.
  public void testOverlappingAdditionalProvide() {
    additionalCode = "goog.provide('a.B'); a.B = {};";
    test("goog.provide('a.A'); a.A = {};",
         "var a={};a.B={};a.A={};");
  }

  // Tests providing additional code with overlapping var namespace.
  public void testOverlappingAdditionalProvideAtEnd() {
    additionalEndCode = "goog.provide('a.B'); a.B = {};";
    test("goog.provide('a.A'); a.A = {};",
         "var a={};a.A={};a.B={};");
  }

  // Tests providing additional code with overlapping dotted namespace.
  public void testOverlappingDottedAdditionalProvide() {
    additionalCode = "goog.provide('a.b.B'); a.b.B = {};";
    test("goog.provide('a.b.C'); a.b.C = {};",
         "var a={};a.b={};a.b.B={};a.b.C={};");
  }

  // Tests that a require of additional code generates no error.
  public void testRequireOfAdditionalProvide() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    test("goog.require('b.B'); goog.provide('a.A'); a.A = {};",
         "var b={};b.B={};var a={};a.A={};");
  }

  // Tests that a require not in additional code generates (only) one error.
  public void testMissingRequireWithAdditionalProvide() {
    additionalCode = "goog.provide('b.B'); b.B = {};";
    testError("goog.require('b.C'); goog.provide('a.A'); a.A = {};",
         MISSING_PROVIDE_ERROR);
  }

  // Tests that a require in additional code generates no error.
  public void testLateRequire() {
    additionalEndCode = "goog.require('a.A');";
    test("goog.provide('a.A'); a.A = {};",
         "var a={};a.A={};");
  }

  // Tests a case where code is reordered after processing provides and then
  // provides are processed again.
  public void testReorderedProvides() {
    additionalCode = "a.B = {};";  // as if a.B was after a.A originally
    addAdditionalNamespace = true;
    test("goog.provide('a.A'); a.A = {};",
         "var a={};a.B={};a.A={};");
  }

  // Another version of above.
  public void testReorderedProvides2() {
    additionalEndCode = "a.B = {};";
    addAdditionalNamespace = true;
    test("goog.provide('a.A'); a.A = {};",
         "var a={};a.A={};a.B={};");
  }

  // Provide a name before the definition of the class providing the
  // parent namespace.
  public void testProvideOrder1() {
    additionalEndCode = "";
    addAdditionalNamespace = false;
    // TODO(johnlenz):  This test confirms that the constructor (a.b) isn't
    // improperly removed, but this result isn't really what we want as the
    // reassign of a.b removes the definition of "a.b.c".
    test("goog.provide('a.b');" +
         "goog.provide('a.b.c');" +
         "a.b.c;" +
         "a.b = function(x,y) {};",
         "var a = {};" +
         "a.b = {};" +
         "a.b.c = {};" +
         "a.b.c;" +
         "a.b = function(x,y) {};");
  }

  // Provide a name after the definition of the class providing the
  // parent namespace.
  public void testProvideOrder2() {
    additionalEndCode = "";
    addAdditionalNamespace = false;
    // TODO(johnlenz):  This test confirms that the constructor (a.b) isn't
    // improperly removed, but this result isn't really what we want as
    // namespace placeholders for a.b and a.b.c remain.
    test("goog.provide('a.b');" +
         "goog.provide('a.b.c');" +
         "a.b = function(x,y) {};" +
         "a.b.c;",
         "var a = {};" +
         "a.b = {};" +
         "a.b.c = {};" +
         "a.b = function(x,y) {};" +
         "a.b.c;");
  }

  // Provide a name after the definition of the class providing the
  // parent namespace.
  public void testProvideOrder3a() {
    test("goog.provide('a.b');" +
         "a.b = function(x,y) {};" +
         "goog.provide('a.b.c');" +
         "a.b.c;",
         "var a = {};" +
         "a.b = function(x,y) {};" +
         "a.b.c = {};" +
         "a.b.c;");
  }

  public void testProvideOrder3b() {
    additionalEndCode = "";
    addAdditionalNamespace = false;
    // This tests a cleanly provided name, below a function namespace.
    test("goog.provide('a.b');" +
         "a.b = function(x,y) {};" +
         "goog.provide('a.b.c');" +
         "a.b.c;",
         "var a = {};" +
         "a.b = function(x,y) {};" +
         "a.b.c = {};" +
         "a.b.c;");
  }

  public void testProvideOrder4a() {
    test("goog.provide('goog.a');" +
         "goog.provide('goog.a.b');" +
         "if (x) {" +
         "  goog.a.b = 1;" +
         "} else {" +
         "  goog.a.b = 2;" +
         "}",

         "goog.a={};" +
         "if(x)" +
         "  goog.a.b=1;" +
         "else" +
         "  goog.a.b=2;");
  }

  public void testProvideOrder4b() {
    additionalEndCode = "";
    addAdditionalNamespace = false;
    // This tests a cleanly provided name, below a namespace.
    test("goog.provide('goog.a');" +
         "goog.provide('goog.a.b');" +
         "if (x) {" +
         "  goog.a.b = 1;" +
         "} else {" +
         "  goog.a.b = 2;" +
         "}",

         "goog.a={};" +
         "if(x)" +
         "  goog.a.b=1;" +
         "else" +
         "  goog.a.b=2;");
  }

  public void testInvalidProvide() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    test("goog.provide('a.class');", "var a = {}; a.class = {};");
    testError("goog.provide('class.a');", INVALID_PROVIDE_ERROR);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT3);
    testError("goog.provide('a.class');", INVALID_PROVIDE_ERROR);
    testError("goog.provide('class.a');", INVALID_PROVIDE_ERROR);
  }

  public void testInvalidRequire() {
    test("goog.provide('a.b'); goog.require('a.b');", "var a = {}; a.b = {};");
    testError("goog.provide('a.b'); var x = x || goog.require('a.b');", INVALID_CLOSURE_CALL_ERROR);
    testError("goog.provide('a.b'); x = goog.require('a.b');", INVALID_CLOSURE_CALL_ERROR);
    testError(
        "goog.provide('a.b'); function f() { goog.require('a.b'); }", INVALID_CLOSURE_CALL_ERROR);
  }

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

  public void testInvalidGoogBase1() {
    testError("goog.base(this, 'method');", GOOG_BASE_CLASS_ERROR);
  }

  public void testInvalidGoogBase2() {
    testError("function Foo() {}" +
         "Foo.method = function() {" +
         "  goog.base(this, 'method');" +
         "};", GOOG_BASE_CLASS_ERROR);
  }

  public void testInvalidGoogBase3() {
    testError(String.format(METHOD_FORMAT, "goog.base();"),
         GOOG_BASE_CLASS_ERROR);
  }

  public void testInvalidGoogBase4() {
    testError(String.format(METHOD_FORMAT, "goog.base(this, 'bar');"),
         GOOG_BASE_CLASS_ERROR);
  }

  public void testInvalidGoogBase5() {
    testError(String.format(METHOD_FORMAT, "goog.base('foo', 'method');"),
         GOOG_BASE_CLASS_ERROR);
  }

  public void testInvalidGoogBase6() {
    testError(String.format(METHOD_FORMAT, "goog.base.call(null, this, 'method');"),
         GOOG_BASE_CLASS_ERROR);
  }

  public void testInvalidGoogBase7() {
    testError("function Foo() { goog.base(this); }", GOOG_BASE_CLASS_ERROR);
  }

  public void testInvalidGoogBase8() {
    testError("var Foo = function() { goog.base(this); }", GOOG_BASE_CLASS_ERROR);
  }

  public void testInvalidGoogBase9() {
    testError("var goog = {}; goog.Foo = function() { goog.base(this); }", GOOG_BASE_CLASS_ERROR);
  }

  public void testValidGoogBase1() {
    test(String.format(METHOD_FORMAT, "goog.base(this, 'method');"),
         String.format(METHOD_FORMAT, "Foo.superClass_.method.call(this)"));
  }

  public void testValidGoogBase2() {
    test(String.format(METHOD_FORMAT, "goog.base(this, 'method', 1, 2);"),
         String.format(METHOD_FORMAT,
             "Foo.superClass_.method.call(this, 1, 2)"));
  }

  public void testValidGoogBase3() {
    test(String.format(METHOD_FORMAT, "return goog.base(this, 'method');"),
         String.format(METHOD_FORMAT,
             "return Foo.superClass_.method.call(this)"));
  }

  public void testValidGoogBase4() {
    test("function Foo() { goog.base(this, 1, 2); }" + FOO_INHERITS,
         "function Foo() { BaseFoo.call(this, 1, 2); } " + FOO_INHERITS);
  }

  public void testValidGoogBase5() {
    test("var Foo = function() { goog.base(this, 1); };" + FOO_INHERITS,
         "var Foo = function() { BaseFoo.call(this, 1); }; " + FOO_INHERITS);
  }

  public void testValidGoogBase6() {
    test("var goog = {}; goog.Foo = function() { goog.base(this); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);",
         "var goog = {}; goog.Foo = function() { goog.BaseFoo.call(this); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);");
  }

  public void testBanGoogBase() {
    banGoogBase = true;
    testSame("function Foo() { goog.base(this, 1, 2); }" + FOO_INHERITS,
        ProcessClosurePrimitives.USE_OF_GOOG_BASE, true);
  }

  public void testInvalidBase1() {
    testError(
        "var Foo = function() {};" + FOO_INHERITS +
        "Foo.base(this, 'method');", BASE_CLASS_ERROR);
  }

  public void testInvalidBase2() {
    testError("function Foo() {}" + FOO_INHERITS +
        "Foo.method = function() {" +
        "  Foo.base(this, 'method');" +
        "};", BASE_CLASS_ERROR);
  }

  public void testInvalidBase3() {
    testError(String.format(FOO_INHERITS + METHOD_FORMAT, "Foo.base();"),
        BASE_CLASS_ERROR);
  }

  public void testInvalidBase4() {
    testError(String.format(FOO_INHERITS + METHOD_FORMAT, "Foo.base(this, 'bar');"),
        BASE_CLASS_ERROR);
  }

  public void testInvalidBase5() {
    testError(String.format(FOO_INHERITS + METHOD_FORMAT,
        "Foo.base('foo', 'method');"),
        BASE_CLASS_ERROR);
  }

  public void testInvalidBase7() {
    testError("function Foo() { Foo.base(this); };" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  public void testInvalidBase8() {
    testError("var Foo = function() { Foo.base(this); };" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  public void testInvalidBase9() {
    testError("var goog = {}; goog.Foo = function() { goog.Foo.base(this); };"
        + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }


  public void testInvalidBase10() {
    testError("function Foo() { Foo.base(this); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  public void testInvalidBase11() {
    testError("function Foo() { Foo.base(this, 'method'); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  public void testInvalidBase12() {
    testError("function Foo() { Foo.base(this, 1, 2); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  public void testInvalidBase13() {
    testError(
        "function Bar(){ Bar.base(this, 'constructor'); }" +
        "goog.inherits(Bar, Goo);" +
        "function Foo(){ Bar.base(this, 'constructor'); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  public void testValidBase1() {
    test(FOO_INHERITS
         + String.format(METHOD_FORMAT, "Foo.base(this, 'method');"),
         FOO_INHERITS
         + String.format(METHOD_FORMAT, "Foo.superClass_.method.call(this)"));
  }

  public void testValidBase2() {
    test(FOO_INHERITS
         + String.format(METHOD_FORMAT, "Foo.base(this, 'method', 1, 2);"),
         FOO_INHERITS
         + String.format(METHOD_FORMAT,
             "Foo.superClass_.method.call(this, 1, 2)"));
  }

  public void testValidBase3() {
    test(FOO_INHERITS
         + String.format(METHOD_FORMAT, "return Foo.base(this, 'method');"),
         FOO_INHERITS
         + String.format(METHOD_FORMAT,
             "return Foo.superClass_.method.call(this)"));
  }

  public void testValidBase4() {
    test("function Foo() { Foo.base(this, 'constructor', 1, 2); }"
         + FOO_INHERITS,
         "function Foo() { BaseFoo.call(this, 1, 2); } " + FOO_INHERITS);
  }

  public void testValidBase5() {
    test("var Foo = function() { Foo.base(this, 'constructor', 1); };"
         + FOO_INHERITS,
         "var Foo = function() { BaseFoo.call(this, 1); }; " + FOO_INHERITS);
  }

  public void testValidBase6() {
    test("var goog = {}; goog.Foo = function() {" +
         "goog.Foo.base(this, 'constructor'); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);",
         "var goog = {}; goog.Foo = function() { goog.BaseFoo.call(this); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);");
  }

  public void testValidBase7() {
    // No goog.inherits, so this is probably a different 'base' function.
    testSame(""
        + "var a = function() {"
        + "  a.base(this, 'constructor');"
        + "};");
  }

  public void testImplicitAndExplicitProvide() {
    test("var goog = {}; " +
         "goog.provide('goog.foo.bar'); goog.provide('goog.foo');",
         "var goog = {}; goog.foo = {}; goog.foo.bar = {};");
  }

  public void testImplicitProvideInIndependentModules() {
    test(
        createModuleStar(
            "",
            "goog.provide('apps.A');",
            "goog.provide('apps.B');"),
        new String[] {
            "var apps = {};",
            "apps.A = {};",
            "apps.B = {};",
        });
  }

  public void testImplicitProvideInIndependentModules2() {
    test(
        createModuleStar(
            "goog.provide('apps');",
            "goog.provide('apps.foo.A');",
            "goog.provide('apps.foo.B');"),
        new String[] {
            "var apps = {}; apps.foo = {};",
            "apps.foo.A = {};",
            "apps.foo.B = {};",
        });
  }

  public void testImplicitProvideInIndependentModules3() {
    test(
        createModuleStar(
            "var goog = {};",
            "goog.provide('goog.foo.A');",
            "goog.provide('goog.foo.B');"),
        new String[] {
            "var goog = {}; goog.foo = {};",
            "goog.foo.A = {};",
            "goog.foo.B = {};",
        });
  }

  public void testProvideInIndependentModules1() {
    test(
        createModuleStar(
            "goog.provide('apps');",
            "goog.provide('apps.foo');",
            "goog.provide('apps.foo.B');"),
        new String[] {
            "var apps = {}; apps.foo = {};",
            "",
            "apps.foo.B = {};",
        });
  }

  public void testProvideInIndependentModules2() {
    // TODO(nicksantos): Make this an error.
    test(
        createModuleStar(
            "goog.provide('apps');",
            "goog.provide('apps.foo'); apps.foo = {};",
            "goog.provide('apps.foo.B');"),
        new String[] {
            "var apps = {};",
            "apps.foo = {};",
            "apps.foo.B = {};",
        });
  }

  public void testProvideInIndependentModules2b() {
    // TODO(nicksantos): Make this an error.
    test(
        createModuleStar(
            "goog.provide('apps');",
            "goog.provide('apps.foo'); apps.foo = function() {};",
            "goog.provide('apps.foo.B');"),
        new String[] {
            "var apps = {};",
            "apps.foo = function() {};",
            "apps.foo.B = {};",
        });
  }

  public void testProvideInIndependentModules3() {
    test(
        createModuleStar(
            "goog.provide('apps');",
            "goog.provide('apps.foo.B');",
            "goog.provide('apps.foo'); goog.require('apps.foo');"),
        new String[] {
            "var apps = {}; apps.foo = {};",
            "apps.foo.B = {};",
            "",
        });
  }

  public void testProvideInIndependentModules3b() {
    // TODO(nicksantos): Make this an error.
    test(
        createModuleStar(
            "goog.provide('apps');",
            "goog.provide('apps.foo.B');",
            "goog.provide('apps.foo'); apps.foo = function() {}; " +
            "goog.require('apps.foo');"),
        new String[] {
            "var apps = {};",
            "apps.foo.B = {};",
            "apps.foo = function() {};",
        });
  }

  public void testProvideInIndependentModules4() {
    // Regression test for bug 261:
    // http://code.google.com/p/closure-compiler/issues/detail?id=261
    test(
        createModuleStar(
            "goog.provide('apps');",
            "goog.provide('apps.foo.bar.B');",
            "goog.provide('apps.foo.bar.C');"),
        new String[] {
            "var apps = {};apps.foo = {};apps.foo.bar = {}",
            "apps.foo.bar.B = {};",
            "apps.foo.bar.C = {};",
        });
  }

  public void testRequireOfBaseGoog() {
    testError("goog.require('goog');", MISSING_PROVIDE_ERROR);
  }

  public void testSourcePositionPreservation() {
    test("goog.provide('foo.bar.baz');",
         "var foo = {};" +
         "foo.bar = {};" +
         "foo.bar.baz = {};");

    Node root = getLastCompiler().getRoot();

    Node fooDecl = findQualifiedNameNode("foo", root);
    Node fooBarDecl = findQualifiedNameNode("foo.bar", root);
    Node fooBarBazDecl = findQualifiedNameNode("foo.bar.baz", root);

    assertEquals(1, fooDecl.getLineno());
    assertEquals(14, fooDecl.getCharno());

    assertEquals(1, fooBarDecl.getLineno());
    assertEquals(18, fooBarDecl.getCharno());

    assertEquals(1, fooBarBazDecl.getLineno());
    assertEquals(22, fooBarBazDecl.getCharno());
  }

  public void testNoStubForProvidedTypedef() {
    test("goog.provide('x'); /** @typedef {number} */ var x;", "var x;");
  }

  public void testNoStubForProvidedTypedef2() {
    test("goog.provide('x.y'); /** @typedef {number} */ x.y;",
         "var x = {}; x.y;");
  }

  public void testNoStubForProvidedTypedef4() {
    test("goog.provide('x.y.z'); /** @typedef {number} */ x.y.z;",
         "var x = {}; x.y = {}; x.y.z;");
  }

  public void testProvideRequireSameFile() {
    test("goog.provide('x');\ngoog.require('x');", "var x = {};");
  }

  public void testDefineCases() {
    String jsdoc = "/** @define {number} */\n";
    test(jsdoc + "goog.define('name', 1);", jsdoc + "var name = 1");
    test(jsdoc + "goog.define('ns.name', 1);", jsdoc + "ns.name = 1");
  }

  public void testDefineErrorCases() {
    String jsdoc = "/** @define {number} */\n";
    testError("goog.define('name', 1);", MISSING_DEFINE_ANNOTATION);
    testError(jsdoc + "goog.define('name.2', 1);", INVALID_DEFINE_NAME_ERROR);
    testError(jsdoc + "goog.define();", NULL_ARGUMENT_ERROR);
    testError(jsdoc + "goog.define('value');", NULL_ARGUMENT_ERROR);
    testError(jsdoc + "goog.define(5);", INVALID_ARGUMENT_ERROR);
  }

  public void testDefineValues() {
    testSame("var CLOSURE_DEFINES = {'FOO': 'string'};");
    testSame("var CLOSURE_DEFINES = {'FOO': true};");
    testSame("var CLOSURE_DEFINES = {'FOO': false};");
    testSame("var CLOSURE_DEFINES = {'FOO': 1};");
    testSame("var CLOSURE_DEFINES = {'FOO': 0xABCD};");
    testSame("var CLOSURE_DEFINESS = {'FOO': -1};");
  }

  public void testDefineValuesErrors() {
    testSame("var CLOSURE_DEFINES = {'FOO': a};",
        CLOSURE_DEFINES_ERROR, true);
    testSame("var CLOSURE_DEFINES = {'FOO': 0+1};",
        CLOSURE_DEFINES_ERROR, true);
    testSame("var CLOSURE_DEFINES = {'FOO': 'value' + 'value'};",
        CLOSURE_DEFINES_ERROR, true);
    testSame("var CLOSURE_DEFINES = {'FOO': !true};",
        CLOSURE_DEFINES_ERROR, true);
    testSame("var CLOSURE_DEFINES = {'FOO': -true};",
        CLOSURE_DEFINES_ERROR, true);
  }
}
