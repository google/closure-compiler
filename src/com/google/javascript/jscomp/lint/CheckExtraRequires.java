/*
 * Copyright 2008 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Walks the AST looking for usages of qualified names, and 'goog.require's of those names. Then,
 * reconciles the two lists, and reports warning for any unnecessary require statements.
 */
public class CheckExtraRequires extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  // Keys are the local name of a required namespace. Values are the goog.require CALL node.
  private final Map<String, Node> requires = new HashMap<>();

  // Adding an entry to usages indicates that the name (either a fully qualified or local name)
  // is used and can be required.  Note that since usages are name-based and not scoped, any usage
  // that shadows an unused require in that file will cause the extra require warning to be missed.
  private final Set<String> usages = new HashSet<>();

  public static final DiagnosticType EXTRA_REQUIRE_WARNING =
      DiagnosticType.disabled(
          "JSC_EXTRA_REQUIRE_WARNING", "extra require: ''{0}'' is never referenced in this file");

  // TODO(b/130215517): This should eventually be removed and exceptions supressed
  private static final ImmutableSet<String> DEFAULT_EXTRA_NAMESPACES =
      ImmutableSet.of(
          "goog.testing.asserts", "goog.testing.jsunit", "goog.testing.JsTdTestCaseAdapter");

  public CheckExtraRequires(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    reset();
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    process(null, scriptRoot);
  }

  private String extractNamespace(Node call, String... primitiveNames) {
    Node callee = call.getFirstChild();
    if (!callee.isGetProp()) {
      return null;
    }
    for (String primitiveName : primitiveNames) {
      if (callee.matchesQualifiedName(primitiveName)) {
        Node target = callee.getNext();
        if (target != null && target.isString()) {
          return target.getString();
        }
      }
    }
    return null;
  }

  private String extractNamespaceIfRequire(Node call) {
    return extractNamespace(call, "goog.require", "goog.requireType");
  }

  private String extractNamespaceIfForwardDeclare(Node call) {
    return extractNamespace(call, "goog.forwardDeclare");
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    maybeAddJsDocUsages(n);
    switch (n.getToken()) {
      case NAME:
        if (!NodeUtil.isLValue(n) && !parent.isGetProp() && !parent.isImportSpec()) {
          visitQualifiedName(n);
        }
        break;
      case GETPROP:
        // If parent is a GETPROP, they will handle all the usages.
        if (!parent.isGetProp() && n.isQualifiedName()) {
          visitQualifiedName(n);
        }
        break;
      case CALL:
        visitCallNode(n, parent);
        break;
      case SCRIPT:
        visitScriptNode();
        reset();
        break;
      case IMPORT:
        visitImportNode(n);
        break;
      default:
        break;
    }
  }

  private void reset() {
    this.usages.clear();
    this.requires.clear();
  }

  private void visitScriptNode() {
    // For every goog.require, check that there is a usage and warn if there is not.
    for (Map.Entry<String, Node> entry : requires.entrySet()) {
      String require = entry.getKey();
      Node call = entry.getValue();
      if (!usages.contains(require)) {
        reportExtraRequireWarning(call, require);
      }
    }
  }

  private void reportExtraRequireWarning(Node call, String require) {
    if (DEFAULT_EXTRA_NAMESPACES.contains(require)) {
      return;
    }
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(call);
    if (jsDoc != null && jsDoc.getSuppressions().contains("extraRequire")) {
      // There is a @suppress {extraRequire} on the call node or its enclosing statement.
      // This is one of the acceptable places for a @suppress, per
      // https://github.com/google/closure-compiler/wiki/@suppress-annotations
      return;
    }
    compiler.report(JSError.make(call, EXTRA_REQUIRE_WARNING, require));
  }

  /**
   * @param localName The name that should be used in this file.
   *     <pre>
   * Require style                        | localName
   * -------------------------------------|----------
   * goog.require('foo.bar');             | foo.bar
   * var bar = goog.require('foo.bar');   | bar
   * var {qux} = goog.require('foo.bar'); | qux
   * import {qux} from 'foo.bar';         | qux
   * </pre>
   */
  private void visitRequire(String localName, Node node) {
    if (!requires.containsKey(localName)) {
      requires.put(localName, node);
    }
  }

  private void visitImportNode(Node importNode) {
    Node defaultImport = importNode.getFirstChild();
    if (defaultImport.isName()) {
      visitRequire(defaultImport.getString(), importNode);
    }
    Node namedImports = defaultImport.getNext();
    if (namedImports.isImportSpecs()) {
      for (Node importSpec : namedImports.children()) {
        visitRequire(importSpec.getLastChild().getString(), importNode);
      }
    }
  }

  private void visitForwardDeclare(String namespace, Node forwardDeclareCall, Node parent) {
    visitGoogRequire(namespace, forwardDeclareCall, parent);
  }

  private void visitGoogRequire(String namespace, Node googRequireCall, Node parent) {
    if (parent.isName()) {
      visitRequire(parent.getString(), googRequireCall);
    } else if (parent.isDestructuringLhs() && parent.getFirstChild().isObjectPattern()) {
      if (parent.getFirstChild().hasChildren()) {
        for (Node stringKey : parent.getFirstChild().children()) {
          Node importName = stringKey.getFirstChild();
          if (!importName.isName()) {
            // invalid reported elsewhere
            continue;
          }
          visitRequire(importName.getString(), importName);
        }
      } else {
        visitRequire(namespace, googRequireCall);
      }
    } else {
      visitRequire(namespace, googRequireCall);
    }
  }

  private void visitCallNode(Node call, Node parent) {
    String required = extractNamespaceIfRequire(call);
    if (required != null) {
      visitGoogRequire(required, call, parent);
      return;
    }
    String declare = extractNamespaceIfForwardDeclare(call);
    if (declare != null) {
      visitForwardDeclare(declare, call, parent);
      return;
    }
    Node callee = call.getFirstChild();
    if (callee.matchesQualifiedName("goog.module.get") && call.getSecondChild().isString()) {
      usages.add(call.getSecondChild().getString());
    }
  }

  private void addUsagesOfAllPrefixes(QualifiedName qualifiedName) {
    usages.add(qualifiedName.join());
    // For "foo.bar.baz.qux" add usages for "foo.bar.baz.qux", "foo.bar.baz",
    // "foo.bar", and "foo" because any of those might be a require that
    // we need to include.
    while (!qualifiedName.isSimple()) {
      qualifiedName = qualifiedName.getOwner();
      usages.add(qualifiedName.join());
    }
  }

  private void visitQualifiedName(Node n) {
    checkArgument(n.isQualifiedName(), n);
    addUsagesOfAllPrefixes(n.getQualifiedNameObject());
  }

  private void maybeAddJsDocUsages(Node n) {
    JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      return;
    }
    info.getTypeExpressions().stream()
        .flatMap(e -> e.getAllTypeNames().stream())
        .map(QualifiedName::of)
        .forEach(this::addUsagesOfAllPrefixes);
  }
}
