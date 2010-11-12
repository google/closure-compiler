/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.List;

/**
 * Tests for {@link ScopedAliases}
 *
 * @author robbyw@google.com (Robby Walker)
 */
public class ScopedAliasesTest extends CompilerTestCase {

  private static String EXTERNS = "var window;";

  public ScopedAliasesTest() {
    super(EXTERNS);
  }

  private void testScoped(String code, String expected) {
    test("goog.scope(function() {" + code + "});", expected);
  }

  private void testScopedNoChanges(String aliases, String code) {
    testScoped(aliases + code, code);
  }

  public void testOneLevel() {
    testScoped("var g = goog;g.dom.createElement(g.dom.TagName.DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testTwoLevel() {
    testScoped("var d = goog.dom;d.createElement(d.TagName.DIV);",
               "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testTransitive() {
    testScoped("var d = goog.dom;var DIV = d.TagName.DIV;d.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testTransitiveInSameVar() {
    testScoped("var d = goog.dom, DIV = d.TagName.DIV;d.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testMultipleTransitive() {
    testScoped(
        "var g=goog;var d=g.dom;var t=d.TagName;var DIV=t.DIV;" +
            "d.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testFourLevel() {
    testScoped("var DIV = goog.dom.TagName.DIV;goog.dom.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testWorksInClosures() {
    testScoped(
        "var DIV = goog.dom.TagName.DIV;" +
            "goog.x = function() {goog.dom.createElement(DIV);};",
        "goog.x = function() {goog.dom.createElement(goog.dom.TagName.DIV);};");
  }

  public void testOverridden() {
    // Test that the alias doesn't get unaliased when it's overriden by a
    // parameter.
    testScopedNoChanges(
        "var g = goog;", "goog.x = function(g) {g.z()};");
    // Same for a local.
    testScopedNoChanges(
        "var g = goog;", "goog.x = function() {var g = {}; g.z()};");
  }

  public void testTwoScopes() {
    test(
        "goog.scope(function() {var g = goog;g.method()});" +
        "goog.scope(function() {g.method();});",

        "goog.method();g.method();");
  }

  public void testTwoSymbolsInTwoScopes() {
    test(
        "var goog = {};" +
        "goog.scope(function() { var g = goog; g.Foo = function() {}; });" +
        "goog.scope(function() { " +
        "  var Foo = goog.Foo; goog.bar = function() { return new Foo(); };" +
        "});",
        "var goog = {};" +
        "goog.Foo = function() {};" +
        "goog.bar = function() { return new goog.Foo(); };");
  }

  public void testAliasOfSymbolInGoogScope() {
    test(
        "var goog = {};" +
        "goog.scope(function() {" +
        "  var g = goog;" +
        "  g.Foo = function() {};" +
        "  var Foo = g.Foo;" +
        "  Foo.prototype.bar = function() {};" +
        "});",
        "var goog = {}; goog.Foo = function() {};" +
        "goog.Foo.prototype.bar = function() {};");
  }

  public void testScopedFunctionReturnThis() {
    test("goog.scope(function() { " +
         "  var g = goog; g.f = function() { return this; };" +
         "});",
         "goog.f = function() { return this; };");
  }

  public void testScopedFunctionAssignsToVar() {
    test("goog.scope(function() { " +
         "  var g = goog; g.f = function(x) { x = 3; return x; };" +
         "});",
         "goog.f = function(x) { x = 3; return x; };");
  }

  public void testScopedFunctionThrows() {
    test("goog.scope(function() { " +
         "  var g = goog; g.f = function() { throw 'error'; };" +
         "});",
         "goog.f = function() { throw 'error'; };");
  }

  public void testPropertiesNotChanged() {
    testScopedNoChanges("var x = goog.dom;", "y.x();");
  }

  public void testShadowedVar() {
    test("var Popup = {};" +
         "var OtherPopup = {};" +
         "goog.scope(function() {" +
         "  var Popup = OtherPopup;" +
         "  Popup.newMethod = function() { return new Popup(); };" +
         "});",
         "var Popup = {};" +
         "var OtherPopup = {};" +
         "OtherPopup.newMethod = function() { return new OtherPopup(); };");
  }

  private void testTypes(String aliases, String code) {
    testScopedNoChanges(aliases, code);
    Compiler lastCompiler = getLastCompiler();
    new TypeVerifyingPass(lastCompiler).process(lastCompiler.externsRoot,
        lastCompiler.jsRoot);
  }

  public void testJsDocType() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {x} */ types.actual;"
        + "/** @type {goog.Timer} */ types.expected;");
  }

  public void testJsDocParameter() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @param {x} a */ types.actual;"
        + "/** @param {goog.Timer} a */ types.expected;");
  }

  public void testJsDocExtends() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @extends {x} */ types.actual;"
        + "/** @extends {goog.Timer} */ types.expected;");
  }

  public void testJsDocImplements() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @implements {x} */ types.actual;"
        + "/** @implements {goog.Timer} */ types.expected;");
  }

  public void testJsDocEnum() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @enum {x} */ types.actual;"
        + "/** @enum {goog.Timer} */ types.expected;");
  }

  public void testJsDocReturn() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @return {x} */ types.actual;"
        + "/** @return {goog.Timer} */ types.expected;");
  }

  public void testJsDocThis() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @this {x} */ types.actual;"
        + "/** @this {goog.Timer} */ types.expected;");
  }

  public void testJsDocThrows() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @throws {x} */ types.actual;"
        + "/** @throws {goog.Timer} */ types.expected;");
  }

  public void testJsDocSubType() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {x.Enum} */ types.actual;"
        + "/** @type {goog.Timer.Enum} */ types.expected;");
  }

  public void testJsDocTypedef() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @typedef {x} */ types.actual;"
        + "/** @typedef {goog.Timer} */ types.expected;");
  }

  public void testArrayJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {Array.<x>} */ types.actual;"
        + "/** @type {Array.<goog.Timer>} */ types.expected;");
  }

  public void testObjectJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {{someKey: x}} */ types.actual;"
        + "/** @type {{someKey: goog.Timer}} */ types.expected;");
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {{x: number}} */ types.actual;"
        + "/** @type {{x: number}} */ types.expected;");
  }

  public void testUnionJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {x|Object} */ types.actual;"
        + "/** @type {goog.Timer|Object} */ types.expected;");
  }

  public void testFunctionJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {function(x) : void} */ types.actual;"
        + "/** @type {function(goog.Timer) : void} */ types.expected;");
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {function() : x} */ types.actual;"
        + "/** @type {function() : goog.Timer} */ types.expected;");
  }

  public void testTestTypes() {
    try {
      testTypes(
          "var x = goog.Timer;",
          ""
          + "/** @type {function() : x} */ types.actual;"
          + "/** @type {function() : wrong.wrong} */ types.expected;");
      fail("Test types should fail here.");
    } catch (AssertionError e) {
    }
  }

  public void testNullType() {
    testTypes(
        "var x = goog.Timer;",
        "/** @param draggable */ types.actual;"
        + "/** @param draggable */ types.expected;");
  }

  // TODO(robbyw): What if it's recursive?  var goog = goog.dom;

  // FAILURE CASES

  private void testFailure(String code, DiagnosticType expectedError) {
    test(code, null, expectedError);
  }

  private void testScopedFailure(String code, DiagnosticType expectedError) {
    test("goog.scope(function() {" + code + "});", null, expectedError);
  }

  public void testScopedThis() {
    testScopedFailure("this.y = 10;", ScopedAliases.GOOG_SCOPE_REFERENCES_THIS);
    testScopedFailure("var x = this;",
        ScopedAliases.GOOG_SCOPE_REFERENCES_THIS);
    testScopedFailure("fn(this);", ScopedAliases.GOOG_SCOPE_REFERENCES_THIS);
  }

  public void testAliasRedefinition() {
    testScopedFailure("var x = goog.dom; x = goog.events;",
        ScopedAliases.GOOG_SCOPE_ALIAS_REDEFINED);
  }

  public void testAliasNonRedefinition() {
    test("var y = {}; goog.scope(function() { goog.dom = y; });",
         "var y = {}; goog.dom = y;");
  }

  public void testScopedReturn() {
    testScopedFailure("return;", ScopedAliases.GOOG_SCOPE_USES_RETURN);
    testScopedFailure("var x = goog.dom; return;",
        ScopedAliases.GOOG_SCOPE_USES_RETURN);
  }

  public void testScopedThrow() {
    testScopedFailure("throw 'error';", ScopedAliases.GOOG_SCOPE_USES_THROW);
  }

  public void testUsedImproperly() {
    testFailure("var x = goog.scope(function() {});",
        ScopedAliases.GOOG_SCOPE_USED_IMPROPERLY);
  }

  public void testBadParameters() {
    testFailure("goog.scope()", ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testFailure("goog.scope(10)", ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testFailure("goog.scope(function() {}, 10)",
        ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testFailure("goog.scope(function z() {})",
        ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testFailure("goog.scope(function(a, b, c) {})",
        ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
  }

  public void testNonAliasLocal() {
    testScopedFailure("var x = 10", ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
    testScopedFailure("var x = goog.dom + 10",
        ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
    testScopedFailure("var x = goog['dom']",
        ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
    testScopedFailure("var x = goog.dom, y = 10",
        ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
  }

  @Override
  protected ScopedAliases getProcessor(Compiler compiler) {
    return new ScopedAliases(compiler);
  }

  private static class TypeVerifyingPass
      implements CompilerPass, NodeTraversal.Callback {
    private final Compiler compiler;
    private List<String> actualTypes = null;

    public TypeVerifyingPass(Compiler compiler) {
      this.compiler = compiler;
    }

    public void process(Node externs, Node root) {
      NodeTraversal.traverse(compiler, root, this);
    }

    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      return true;
    }

    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        Collection<Node> typeNodes = info.getTypeNodes();
        if (typeNodes.size() > 0) {
          if (actualTypes != null) {
            List<String> expectedTypes = Lists.newArrayList();
            for (Node typeNode : info.getTypeNodes()) {
              expectedTypes.add(typeNode.toStringTree());
            }
            assertEquals(expectedTypes, actualTypes);
          } else {
            actualTypes = Lists.newArrayList();
            for (Node typeNode : info.getTypeNodes()) {
              actualTypes.add(typeNode.toStringTree());
            }
          }
        }
      }
    }
  }
}
