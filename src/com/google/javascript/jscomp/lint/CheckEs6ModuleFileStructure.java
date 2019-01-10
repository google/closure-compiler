/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.Set;
import java.util.TreeSet;

/** Checks the file structure of ES6 modules. */
public final class CheckEs6ModuleFileStructure extends AbstractPreOrderCallback
    implements CompilerPass {

  public static final DiagnosticType MUST_COME_BEFORE =
      DiagnosticType.warning(
          "JSC_MUST_COME_BEFORE_IN_ES6_MODULE", "In ES6 modules, {0} should come before {1}.");

  /** Statements that must appear in a certain order within ES6 modules (for the sake of style). */
  private enum OrderedStatement {
    IMPORT("import statements"),
    DECLARE_MODULE_ID("a call to goog.declareModuleId()"),
    GOOG_REQUIRE("calls to goog.require()"),
    OTHER("other statements");

    final String description;

    OrderedStatement(String description) {
      this.description = description;
    }

    @Override
    public String toString() {
      return description;
    }
  }

  private final AbstractCompiler compiler;
  private final TreeSet<OrderedStatement> orderedStatements = new TreeSet<>();

  public CheckEs6ModuleFileStructure(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case ROOT:
        return true;
      case SCRIPT:
        return n.getBooleanProp(Node.ES6_MODULE);
      case MODULE_BODY:
        orderedStatements.clear();
        return true;
      case IMPORT:
        visitImport(t, n);
        return false;
      case EXPR_RESULT:
        return visitExprResult(t, n, parent);
      case VAR:
      case LET:
      case CONST:
        return visitDeclaration(t, n, parent);
      default:
        if (parent != null && parent.isModuleBody()) {
          checkOrder(t, n, OrderedStatement.OTHER);
        }
        return false;
    }
  }

  private void checkOrder(NodeTraversal t, Node n, OrderedStatement statement) {
    orderedStatements.add(statement);
    Set<OrderedStatement> outOfOrder = orderedStatements.tailSet(statement, /* inclusive= */ false);
    if (!outOfOrder.isEmpty()) {
      t.report(n, MUST_COME_BEFORE, statement.toString(), Joiner.on(", ").join(outOfOrder));
    }
  }

  private boolean visitExprResult(NodeTraversal t, Node exprResult, Node parent) {
    if (parent.isModuleBody() && exprResult.getFirstChild().isCall()) {
      Node call = exprResult.getFirstChild();
      // TODO(johnplaisted): Remove declareNamespace.
      if (call.getFirstChild().matchesQualifiedName("goog.module.declareNamespace")
          || call.getFirstChild().matchesQualifiedName("goog.declareModuleId")) {
        checkOrder(t, call, OrderedStatement.DECLARE_MODULE_ID);
        return false;
      } else if (call.getFirstChild().matchesQualifiedName("goog.require")) {
        checkOrder(t, call, OrderedStatement.GOOG_REQUIRE);
        return false;
      }
    }

    checkOrder(t, exprResult, OrderedStatement.OTHER);
    return true;
  }

  private boolean visitDeclaration(NodeTraversal t, Node declaration, Node parent) {
    if (parent.isModuleBody()
        && declaration.hasOneChild()
        && declaration.getFirstChild().hasOneChild()
        && declaration.getFirstFirstChild().isCall()) {
      Node call = declaration.getFirstFirstChild();
      if (call.getFirstChild().matchesQualifiedName("goog.require")) {
        checkOrder(t, call, OrderedStatement.GOOG_REQUIRE);
        return false;
      }
    }

    checkOrder(t, declaration, OrderedStatement.OTHER);
    return true;
  }

  private void visitImport(NodeTraversal t, Node importNode) {
    checkOrder(t, importNode, OrderedStatement.IMPORT);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }
}
