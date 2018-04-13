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
package com.google.javascript.jscomp.lint;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Ordering;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks that goog.require() and goog.provide() calls are sorted alphabetically.
 */
public final class CheckRequiresAndProvidesSorted extends AbstractShallowCallback
    implements HotSwapCompilerPass {
  public static final DiagnosticType REQUIRES_NOT_SORTED =
      DiagnosticType.warning("JSC_REQUIRES_NOT_SORTED",
      "goog.require() statements are not sorted."
          + " The correct order is:\n\n{0}\n");

  public static final DiagnosticType PROVIDES_NOT_SORTED =
      DiagnosticType.warning("JSC_PROVIDES_NOT_SORTED",
          "goog.provide() statements are not sorted."
              + " The correct order is:\n\n{0}\n");

  public static final DiagnosticType PROVIDES_AFTER_REQUIRES =
      DiagnosticType.warning(
          "JSC_PROVIDES_AFTER_REQUIRES",
          "goog.provide() statements should be before goog.require() statements.");

  public static final DiagnosticType DUPLICATE_REQUIRE =
      DiagnosticType.warning("JSC_DUPLICATE_REQUIRE", "''{0}'' required more than once.");

  private final List<Node> requires;
  private final List<Node> provides;

  private final AbstractCompiler compiler;

  public CheckRequiresAndProvidesSorted(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.requires = new ArrayList<>();
    this.provides = new ArrayList<>();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  public static final String getSortKey(Node n) {
    String key = null;
    boolean isForwardDeclare = false;
    if (NodeUtil.isNameDeclaration(n)) {
      if (n.getFirstChild().isName()) {
        // Case 1:
        //   var x = goog.require('w.x');
        // or
        //   var x = goog.forwardDeclare('w.x');
        key = n.getFirstChild().getString();
        if (n.getFirstFirstChild().getFirstChild().matchesQualifiedName("goog.forwardDeclare")) {
              isForwardDeclare = true;
            }
      } else if (n.getFirstChild().isDestructuringLhs()) {
        // Case 2: var {y} = goog.require('w.x');
        // All case 2 nodes should come after all case 1 nodes. ('{' sorts after a-z)
        Node pattern = n.getFirstFirstChild();
        checkState(pattern.isObjectPattern(), pattern);
        Node call = n.getFirstChild().getLastChild();
        checkState(call.isCall(), call);
        checkState(call.getFirstChild().matchesQualifiedName("goog.require"), call.getFirstChild());
        if (!pattern.hasChildren()) {
          key = "{";
        } else {
          key = "{" + pattern.getFirstChild().getString();
        }
      }
    } else if (n.isExprResult()) {
      // Case 3, one of:
      //   goog.provide('a.b.c');
      //   goog.require('a.b.c');
      //   goog.forwardDeclare('a.b.c');
      // All case 3 nodes should come after case 1 and 2 nodes, so prepend
      // '|' which sorts after '{'
      key = "|" + n.getFirstChild().getLastChild().getString();
      if (n.getFirstFirstChild().matchesQualifiedName("goog.forwardDeclare")) {
        isForwardDeclare = true;
      }
    } else {
      throw new IllegalArgumentException("Unexpected node " + n);
    }
    // Make sure all forwardDeclares come after all requires.
    return (isForwardDeclare ? "z" : "a") + checkNotNull(key);
  }

  private static final String getNamespace(Node requireStatement) {
    if (requireStatement.isExprResult()) {
      // goog.require('a.b.c');
      return requireStatement.getFirstChild().getLastChild().getString();
    } else if (NodeUtil.isNameDeclaration(requireStatement)) {
      if (requireStatement.getFirstChild().isName()) {
        // const x = goog.require('a.b.c');
        return requireStatement.getFirstFirstChild().getLastChild().getString();
      } else if (requireStatement.getFirstChild().isDestructuringLhs()) {
        // const {x} = goog.require('a.b.c');
        return requireStatement.getFirstChild().getLastChild().getLastChild().getString();
      }
    }
    throw new IllegalArgumentException("Unexpected node " + requireStatement);
  }

  private final Ordering<Node> alphabetical =
      Ordering.natural().onResultOf(CheckRequiresAndProvidesSorted::getSortKey);

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT:
        // Duplicate provides are already checked in ProcessClosurePrimitives.
        checkForDuplicates(requires);
        reportIfOutOfOrder(requires, REQUIRES_NOT_SORTED);
        reportIfOutOfOrder(provides, PROVIDES_NOT_SORTED);
        reset();
        break;
      case CALL:
        Node callee = n.getFirstChild();
        if (!callee.matchesQualifiedName("goog.require")
            && !callee.matchesQualifiedName("goog.forwardDeclare")
            && !callee.matchesQualifiedName("goog.provide")
            && !callee.matchesQualifiedName("goog.module")) {
          return;
        }

        if (parent.isExprResult() && NodeUtil.isTopLevel(parent.getParent())) {
          Node namespaceNode = n.getLastChild();
          if (!namespaceNode.isString()) {
            return;
          }
          String namespace = namespaceNode.getString();
          if (namespace == null) {
            return;
          }
          if (callee.matchesQualifiedName("goog.require")
              || callee.matchesQualifiedName("goog.forwardDeclare")) {
            requires.add(parent);
          } else {
            if (!requires.isEmpty()) {
              t.report(n, PROVIDES_AFTER_REQUIRES);
            }
            if (callee.matchesQualifiedName("goog.provide")) {
              provides.add(parent);
            }
          }
        } else if (NodeUtil.isNameDeclaration(parent.getParent())
            && (callee.matchesQualifiedName("goog.require")
                || callee.matchesQualifiedName("goog.forwardDeclare"))) {
          requires.add(parent.getParent());
        }
        break;
      default:
        break;
    }
  }

  private void reportIfOutOfOrder(List<Node> requiresOrProvides, DiagnosticType warning) {
    if (!alphabetical.isOrdered(requiresOrProvides)) {
      StringBuilder correctOrder = new StringBuilder();
      for (Node n : alphabetical.sortedCopy(requiresOrProvides)) {
        correctOrder.append(compiler.toSource(n));
      }
      compiler.report(
          JSError.make(requiresOrProvides.get(0), warning, correctOrder.toString()));
    }
  }

  private void checkForDuplicates(List<Node> requires) {
    Set<String> namespaces = new HashSet<>();
    for (Node require : requires) {
      String namespace = getNamespace(require);
      if (!namespaces.add(namespace)) {
        compiler.report(JSError.make(require, DUPLICATE_REQUIRE, namespace));
      }
    }
  }

  private void reset() {
    requires.clear();
    provides.clear();
  }
}
