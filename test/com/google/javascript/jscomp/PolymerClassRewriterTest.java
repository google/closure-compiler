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

import com.google.common.base.Predicates;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.NodeSubject;
import com.google.javascript.rhino.Node;

public final class PolymerClassRewriterTest extends CompilerTypeTestCase {

  private static final String EXTERNS =
      LINE_JOINER.join(
          "/** @constructor */",
          "var HTMLElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var HTMLInputElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var PolymerElement = function() {};",
          "/** @type {!Object} */",
          "PolymerElement.prototype.$;",
          "PolymerElement.prototype.created = function() {};",
          "PolymerElement.prototype.ready = function() {};",
          "PolymerElement.prototype.attached = function() {};",
          "PolymerElement.prototype.domReady = function() {};",
          "PolymerElement.prototype.detached = function() {};",
          "/**",
          " * Call the callback after a timeout. Calling job again with the same name",
          " * resets the timer but will not result in additional calls to callback.",
          " *",
          " * @param {string} name",
          " * @param {Function} callback",
          " * @param {number} timeoutMillis The minimum delay in milliseconds before",
          " *     calling the callback.",
          " */",
          "PolymerElement.prototype.job = function(name, callback, timeoutMillis) {};",
          "/**",
          " * @param a {!Object}",
          " * @return {!function()}",
          " */",
          "var Polymer = function(a) {};",
          "var alert = function(msg) {};");

  private PolymerClassRewriter rewriter;
  private Node rootNode;
  private GlobalNamespace globalNamespace;
  private Node polymerCall;

  @Override
  protected void setUp() {
    super.setUp();
    polymerCall = null;
    rootNode = null;
  }

  // TODO(jlklein): Add tests for non-global definitions, interface externs, read-only setters, etc.

  public void testVarTarget() {
    test(
        LINE_JOINER.join(
            "var X = Polymer({",
            "  is: 'x-element',",
            "});"),
        LINE_JOINER.join(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "});"));
  }

  public void testDefaultTypeNameTarget() {
    test(
        LINE_JOINER.join(
            "Polymer({",
            "  is: 'x',",
            "});"),
        LINE_JOINER.join(
            "/**",
            " * @implements {PolymerXElementInterface}",
            " * @constructor @extends {PolymerElement}",
            " */",
            "var XElement = function() {};",
            "Polymer(/** @lends {XElement.prototype} */ {",
            "  is: 'x',",
            "});"));
  }

  public void testPathAssignmentTarget() {
    test(
        LINE_JOINER.join(
            "var x = {};",
            "x.Z = Polymer({",
            "  is: 'x-element',",
            "});"),
        LINE_JOINER.join(
            "var x = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface} */",
            "x.Z = function() {};",
            "x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
            "  is: 'x-element',",
            "});"));
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.MISPLACED_TYPE_ANNOTATION, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.INVALID_CASTS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    options.setCodingConvention(getCodingConvention());
    options.setPreserveTypeAnnotations(true);
    options.setPrettyPrint(true);
    return options;
  }

  private void test(String originalCode, String expectedResult) {
    parseAndRewrite(originalCode);
    Node expectedNode = compiler.parseSyntheticCode(expectedResult);
    NodeSubject.assertNode(expectedNode).isEqualTo(rootNode);
  }

  private void parseAndRewrite(String code) {
    rootNode = compiler.parseTestCode(code);
    globalNamespace =  new GlobalNamespace(compiler, rootNode);
    PolymerPassFindExterns findExternsCallback = new PolymerPassFindExterns();
    Node externs = compiler.parseTestCode(EXTERNS);
    NodeTraversal.traverseEs6(compiler, externs, findExternsCallback);

    rewriter = new PolymerClassRewriter(compiler, findExternsCallback.getPolymerElementExterns());

    NodeUtil.visitPostOrder(rootNode, new NodeUtil.Visitor() {
      @Override
      public void visit(Node node) {
        if (PolymerPass.isPolymerCall(node)) {
          polymerCall = node;
        }
      }
    }, Predicates.<Node>alwaysTrue());

    assertNotNull(polymerCall);
    PolymerClassDefinition classDef =
        PolymerClassDefinition.extractFromCallNode(polymerCall, compiler, globalNamespace);

    Node parent = polymerCall.getParent();
    Node grandparent = parent.getParent();
    if (NodeUtil.isNameDeclaration(grandparent) || parent.isAssign()) {
      rewriter.rewritePolymerClass(grandparent, classDef, true);
    } else {
      rewriter.rewritePolymerClass(parent, classDef, true);
    }
  }
}
