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

import com.google.common.collect.Ordering;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that goog.require() and goog.provide() calls are sorted alphabetically.
 */
public final class CheckRequiresAndProvidesSorted extends AbstractShallowCallback
    implements HotSwapCompilerPass {
  public static final DiagnosticType REQUIRES_NOT_SORTED =
      DiagnosticType.warning(
          "JSC_REQUIRES_NOT_SORTED", "goog.require() statements are not sorted.");

  public static final DiagnosticType PROVIDES_NOT_SORTED =
      DiagnosticType.warning(
          "JSC_PROVIDES_NOT_SORTED", "goog.provide() statements are not sorted.");

  public static final DiagnosticType PROVIDES_AFTER_REQUIRES =
      DiagnosticType.warning(
          "JSC_PROVIDES_AFTER_REQUIRES",
          "goog.provide() statements should be before goog.require() statements.");

  public static final DiagnosticType MULTIPLE_MODULES_IN_FILE =
      DiagnosticType.warning(
          "JSC_MULTIPLE_MODULES_IN_FILE",
          "There should only be a single goog.module() statement per file.");

  public static final DiagnosticType MODULE_AND_PROVIDES =
      DiagnosticType.warning(
          "JSC_MODULE_AND_PROVIDES",
          "A file using goog.module() may not also use goog.provide() statements.");

  private List<String> requiredNamespaces;
  private List<String> providedNamespaces;
  private List<String> moduleNamespaces;
  private boolean containsShorthandRequire = false;

  private final AbstractCompiler compiler;

  public CheckRequiresAndProvidesSorted(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.requiredNamespaces = new ArrayList<>();
    this.providedNamespaces = new ArrayList<>();
    this.moduleNamespaces = new ArrayList<>();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.SCRIPT:
        // For now, don't report any sorting-related warnings if there are
        // "var x = goog.require('goog.x');" style requires.
        if (!containsShorthandRequire) {
          if (!Ordering.natural().isOrdered(requiredNamespaces)) {
            t.report(n, REQUIRES_NOT_SORTED);
          }
          if (!Ordering.natural().isOrdered(providedNamespaces)) {
            t.report(n, PROVIDES_NOT_SORTED);
          }
        }
        if (!moduleNamespaces.isEmpty() && !providedNamespaces.isEmpty()) {
          t.report(n, MODULE_AND_PROVIDES);
        }
        if (moduleNamespaces.size() > 1) {
          t.report(n, MULTIPLE_MODULES_IN_FILE);
        }

        requiredNamespaces.clear();
        providedNamespaces.clear();
        moduleNamespaces.clear();
        containsShorthandRequire = false;
        break;
      case Token.CALL:
        Node callee = n.getFirstChild();
        if (!callee.matchesQualifiedName("goog.require")
            && !callee.matchesQualifiedName("goog.provide")
            && !callee.matchesQualifiedName("goog.module")) {
          return;
        }

        if (parent.isExprResult() && parent.getParent().isScript()) {
          String namespace = n.getLastChild().getString();
          if (namespace == null) {
            return;
          }
          if (callee.matchesQualifiedName("goog.require")) {
            requiredNamespaces.add(namespace);
          } else {
            if (!requiredNamespaces.isEmpty()) {
              t.report(n, PROVIDES_AFTER_REQUIRES);
            }
            if (callee.matchesQualifiedName("goog.module")) {
              moduleNamespaces.add(namespace);
            } else {
              providedNamespaces.add(namespace);
            }
          }
        } else if (NodeUtil.isNameDeclaration(parent.getParent())
            && callee.matchesQualifiedName("goog.require")) {
          containsShorthandRequire = true;
        }
        break;
    }
  }
}
