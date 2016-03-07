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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Checks that goog.module() is used correctly.
 *
 * Note that this file only does checks that can be done per-file. Whole program
 * checks happen during goog.module rewriting, in {@link ClosureRewriteModule}.
 */
public final class ClosureCheckModule implements Callback, HotSwapCompilerPass {
  static final DiagnosticType MULTIPLE_MODULES_IN_FILE =
      DiagnosticType.error(
          "JSC_MULTIPLE_MODULES_IN_FILE",
          "There should only be a single goog.module() statement per file.");

  static final DiagnosticType MODULE_AND_PROVIDES =
      DiagnosticType.error(
          "JSC_MODULE_AND_PROVIDES",
          "A file using goog.module() may not also use goog.provide() statements.");

  static final DiagnosticType GOOG_MODULE_REFERENCES_THIS = DiagnosticType.error(
      "JSC_GOOG_MODULE_REFERENCES_THIS",
      "The body of a goog.module cannot reference 'this'.");

  static final DiagnosticType GOOG_MODULE_USES_THROW = DiagnosticType.error(
      "JSC_GOOG_MODULE_USES_THROW",
      "The body of a goog.module cannot use 'throw'.");

  static final DiagnosticType REQUIRE_NOT_AT_TOP_LEVEL =
      DiagnosticType.error(
          "JSC_REQUIRE_NOT_AT_TOP_LEVEL",
          "goog.require() must be called at file scope.");

  static final DiagnosticType ONE_REQUIRE_PER_DECLARATION =
      DiagnosticType.error(
          "JSC_ONE_REQUIRE_PER_DECLARATION",
          "There may only be one goog.require() per var/let/const declaration.");

  private final AbstractCompiler compiler;

  private Node currentModule = null;

  public ClosureCheckModule(AbstractCompiler compiler) {
    this.compiler = compiler;
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
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      return NodeUtil.isModuleFile(n);
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.CALL:
        Node callee = n.getFirstChild();
        if (callee.matchesQualifiedName("goog.module")) {
          if (currentModule == null) {
            currentModule = n;
          } else {
            t.report(n, MULTIPLE_MODULES_IN_FILE);
          }
        } else if (callee.matchesQualifiedName("goog.provide")) {
          t.report(n, MODULE_AND_PROVIDES);
        } else if (callee.matchesQualifiedName("goog.require")) {
          checkRequire(t, n);
        }
        break;
      case Token.THIS:
        if (t.inGlobalHoistScope()) {
          t.report(n, GOOG_MODULE_REFERENCES_THIS);
        }
        break;
      case Token.THROW:
        if (t.inGlobalHoistScope()) {
          t.report(n, GOOG_MODULE_USES_THROW);
        }
        break;
      case Token.SCRIPT:
        currentModule = null;
        break;
    }
  }

  private void checkRequire(NodeTraversal t, Node n) {
    Node statement = NodeUtil.getEnclosingStatement(n);
    if (statement.isExprResult()) {
      return;
    }
    if (NodeUtil.isNameDeclaration(statement)) {
      if (statement.getChildCount() != 1) {
        t.report(statement, ONE_REQUIRE_PER_DECLARATION);
        return;
      }
      Node rhs = statement.getFirstChild().getLastChild();
      if (n == rhs) {
        // var foo = goog.require('ns.foo'); no error.
        return;
      } else if (rhs.isGetProp() && n == rhs.getFirstChild()) {
        // var bar = goog.require('ns.foo').bar; no error
        return;
      }
    }
    t.report(n, REQUIRE_NOT_AT_TOP_LEVEL);
  }
}
