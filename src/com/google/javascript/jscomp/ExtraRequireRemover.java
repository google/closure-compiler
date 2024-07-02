/*
 * Copyright 2024 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Walks the AST looking for usages of qualified names, and 'goog.require's of those names. Then,
 * reconciles the two lists, and removes any unnecessary require statements.
 */
//                Input js                    |     Generated i.js (with ExtraRequireRemover)
// --------------------------------------------------------------------------------------------
//     const Foo1 = goog.require('Foo');      |       const Foo1 = goog.require('Foo');
//     const Bar1 = goog.require('Bar');      |
//     /** @type {!Foo1} */                   |        /** @type {!Foo1} *\
//     let foo;                               |        let foo;
//     let a = A1();                          |        let a = A1();
//
// Note how `const Bar1 = goog.require('Bar');` is removed bc it is not used in the .i.js file.
public final class ExtraRequireRemover implements CompilerPass, NodeTraversal.Callback {
  private final AbstractCompiler compiler;

  // Keys are the local name of a required namespace. Values are the goog.require CALL node.
  private final Map<String, Node> requires = new LinkedHashMap<>();

  // Adding an entry to `usages` indicates that the name (either a fully qualified or local name)
  // is used and can be required. Note that since `usages` are name-based and not scoped, any usage
  // that shadows an unused require in that file will cause the extra require not to be removed.
  private final Set<String> usages = new LinkedHashSet<>();

  private static final ImmutableSet<String> DEFAULT_EXTRA_NAMESPACES =
      ImmutableSet.of(
          "goog.testing.asserts",
          "goog.testing.jsunit",
          "goog.testing.JsTdTestCaseAdapter",
          "goog.labs.testing.Environment");

  public ExtraRequireRemover(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    reset();
    NodeTraversal.traverse(compiler, root, this);
  }

  /**
   * Extracts the namespace from matching goog.require, goog.requireType, and goog.forwardDeclare
   * nodes.
   *
   * <p>Ex: `goog.require('foo.bar');` will return `foo.bar`
   */
  private @Nullable String extractNamespace(Node call, QualifiedName... primitiveNames) {
    Node callee = call.getFirstChild();
    if (!callee.isGetProp()) {
      return null;
    }
    for (QualifiedName primitiveName : primitiveNames) {
      if (primitiveName.matches(callee)) {
        Node target = callee.getNext();
        if (target != null && target.isStringLit()) {
          return target.getString();
        }
      }
    }
    return null;
  }

  private static final QualifiedName GOOG_REQUIRE = QualifiedName.of("goog.require");
  private static final QualifiedName GOOG_REQUIRE_TYPE = QualifiedName.of("goog.requireType");
  private static final QualifiedName GOOG_FORWARD_DECLARE = QualifiedName.of("goog.forwardDeclare");

  /** Extract namespace from goog.require and goog.requireType nodes */
  private String extractNamespaceIfRequire(Node call) {
    return extractNamespace(call, GOOG_REQUIRE, GOOG_REQUIRE_TYPE);
  }

  /** Extract namespace from goog.forwardDeclare nodes */
  private String extractNamespaceIfForwardDeclare(Node call) {
    return extractNamespace(call, GOOG_FORWARD_DECLARE);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()
        && n.getJSDocInfo() != null
        && n.getJSDocInfo().getSuppressions().contains("missingRequire")) {
      return false;
    }
    // Skip ES modules. We could support them in the future if desired.
    return !n.isModuleBody() || n.getParent().getBooleanProp(Node.GOOG_MODULE);
  }

  /** Visits each corresponding node and tracks all goog.requires as well as their usages */
  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    maybeAddJsDocUsages(n);
    switch (n.getToken()) {
      case NAME:
        if (!NodeUtil.isLValue(n) && !parent.isGetProp()) {
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
        removeExtraRequiresInScript();
        reset();
        break;
      default:
        break;
    }
  }

  private void reset() {
    this.usages.clear();
    this.requires.clear();
  }

  /**
   * For every goog.require, check that there is a usage and remove the import if the goog.require
   * is not found in `usages`
   */
  private void removeExtraRequiresInScript() {
    for (Map.Entry<String, Node> entry : requires.entrySet()) {
      String require = entry.getKey();
      Node call = entry.getValue();
      removeExtraRequire(call, require);
    }
  }

  /**
   * Reconciles the two lists (`usages` and `requires`) and removes requires that are used (found in
   * `usages`). There are different cases in which we determine to remove an unnecessary
   * goog.require:
   * <li>1) Extra requires with local-level suppressions should not be removed. (@suppress
   *     {extraRequire})
   *
   *     <p>2) Remove expression statements. ex: const x = goog.require('foo');
   *
   *     <p>3) Remove aliased goog.require imports: const bar = goog.require('foo.bar');
   *
   *     <p>4) Remove destructured goog.require imports if the identifier(s) are unused: const {x,
   *     unused} = goog.require('foo.bar');
   */
  private void removeExtraRequire(Node call, String require) {
    if (usages.contains(require)) {
      return;
    }

    // If a goog.require contains a /** @suppress {extraRequire} */ local-level suppression
    // (suppression right above the import line), we will NOT prune the goog.require. This
    // suppression indicates that the goog.require is actually needed even though it does not appear
    // to be referenced in the file, so don't prune it.
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(call);
    if (jsDoc != null && jsDoc.getSuppressions().contains("extraRequire")) {
      return;
    }

    // Case 1: remove if this is an expression statement.
    // ex: goog.require('foo');
    if (call.getParent().isExprResult()) {
      // all unaliased imports in goog.module are not pruned regardless of if they are used or
      // not
      if (!call.getGrandparent().isModuleBody()) {
        compiler.reportChangeToEnclosingScope(call.getParent());
        call.getParent().detach();
      }
      return;
    }

    // Case 2: this is a 'default' import esque thing remove the entire statement.
    // ex: Remove `const bar = goog.require('foo.bar');`
    if (call.getParent().isName()) {
      Node nameDeclaration = call.getGrandparent();
      checkState(NodeUtil.isNameDeclaration(call.getGrandparent()));
      compiler.reportChangeToEnclosingScope(nameDeclaration.getParent());
      nameDeclaration.detach();
      return;
    }

    // Case 3: this is part of a destructuring import.
    //   const {unused} = goog.require('completely.unused');
    //   const {x, unused} = goog.require('foo.bar');
    // Prune destructured imports that are partially unused by pruning the
    // unused identifier(s). If all the identifiers are unused, prune the entire
    // import.
    checkState(call.getParent().isStringKey(), call.getParent());
    Node objectPattern = call.getGrandparent();

    // the 'const {unused} = goog.require('completely.unused');' case
    if (objectPattern.hasOneChild()) {
      Node nameDeclaration = objectPattern.getGrandparent();
      checkState(NodeUtil.isNameDeclaration(nameDeclaration), nameDeclaration);
      compiler.reportChangeToEnclosingScope(nameDeclaration.getParent());
      nameDeclaration.detach();
      return;
    }

    // 'const {x, unused} = goog.require('foo.bar');' in this case just remove 'unused'.
    compiler.reportChangeToEnclosingScope(call.getGrandparent());
    call.getParent().detach();
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
   *
   * @param namespace The namespace being imported. May be identical to localName in the case of
   *     <code>goog.require('foo.bar');</code>
   */
  private void visitRequire(String localName, String namespace, Node node) {
    if (DEFAULT_EXTRA_NAMESPACES.contains(namespace)) {
      return;
    }
    requires.putIfAbsent(localName, node);
  }

  /** Visits and tracks goog.forwardDeclares */
  private void visitForwardDeclare(String namespace, Node forwardDeclareCall, Node parent) {
    if (!forwardDeclareCall.getParent().isName()) {
      visitGoogRequire(namespace, forwardDeclareCall, parent);
    } else {
      // aliased goog.forwardDeclare's will not be pruned
      return;
    }
  }

  /**
   * Visits local names and adds the name to `usages`.
   *
   * <p>Ex: `const A = goog.require('foo');` add `A` to `usages`
   *
   * <p>If it is a destructured import, add each identifier to `usages`.
   *
   * <p>Ex: `const {Foo, Bar, Baz} = goog.require('foo');` add `Foo`, `Bar`, and `Baz` to `usages`
   */
  private void visitGoogRequire(String namespace, Node googRequireCall, Node parent) {
    // local names
    if (parent.isName()) {
      visitRequire(parent.getString(), namespace, googRequireCall);
    } else if (parent.isDestructuringLhs() && parent.getFirstChild().isObjectPattern()) {
      // destructured imports
      if (parent.getFirstChild().hasChildren()) {
        // loop through identifiers in destructured import
        for (Node stringKey = parent.getFirstFirstChild();
            stringKey != null;
            stringKey = stringKey.getNext()) {
          Node importName = stringKey.getFirstChild();
          visitRequire(importName.getString(), namespace, importName);
        }
      } else {
        visitRequire(namespace, namespace, googRequireCall);
      }
    } else {
      visitRequire(namespace, namespace, googRequireCall);
    }
  }

  private static final QualifiedName GOOG_MODULE_GET = QualifiedName.of("goog.module.get");

  /**
   * Extract the namespace and check if the qualified name matches the callee of ‘goog.require’ ,
   * ’goog.requireType’, or ‘goog.forwardDeclare’. If so, we will visit those corresponding nodes
   * and add those usages.
   *
   * <p>Ex: Qualified name is 'goog.module.get('foo')' and callee is 'goog.require('foo')'
   */
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
    if (GOOG_MODULE_GET.matches(callee) && call.getSecondChild().isStringLit()) {
      usages.add(call.getSecondChild().getString());
    }
  }

  /**
   * For qualified name node, we want to add all usages for each prefix of the name, because one of
   * them might be required.
   *
   * <p>For 'foo.bar.baz.qux' add usages for 'foo.bar.baz.qux', 'foo.bar.baz', 'foo.bar', and 'foo'
   * because any of those might be a require that we need to include.
   */
  private void addUsagesOfAllPrefixes(QualifiedName qualifiedName) {
    usages.add(qualifiedName.join());

    while (!qualifiedName.isSimple()) {
      qualifiedName = qualifiedName.getOwner();
      usages.add(qualifiedName.join());
    }
  }

  private void visitQualifiedName(Node n) {
    checkArgument(n.isQualifiedName(), n);
    addUsagesOfAllPrefixes(n.getQualifiedNameObject());
  }

  /** If the node has corresponding jsdoc info then go through its type names and track usages */
  private void maybeAddJsDocUsages(Node n) {
    JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      return;
    }

    for (JSTypeExpression e : info.getTypeExpressions()) {
      for (String typeName : e.getAllTypeNames()) {
        addUsagesOfAllPrefixes(QualifiedName.of(typeName));
      }
    }
  }
}
