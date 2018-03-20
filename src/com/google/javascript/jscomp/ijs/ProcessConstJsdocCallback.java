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
package com.google.javascript.jscomp.ijs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;

/**
 * A callback that calls the abstract method on every "inferrable const". This is a constant
 * declaration for which there is no declared type and an RHS is present. This is useful for giving
 * warnings like the CONSTANT_WITHOUT_EXPLICIT_TYPE diagnostic.
 *
 * As a side effect, this callback also populates the given FileInfo (assumed empty) with all of
 * the declarations found throughout the compilation.
 */
abstract class ProcessConstJsdocCallback extends NodeTraversal.AbstractPostOrderCallback {

  private final FileInfo currentFile;

  ProcessConstJsdocCallback(FileInfo currentFile) {
    this.currentFile = currentFile;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case CLASS:
        if (NodeUtil.isStatementParent(parent)) {
          currentFile.recordNameDeclaration(n.getFirstChild());
        }
        break;
      case FUNCTION:
        if (NodeUtil.isStatementParent(parent)) {
          currentFile.recordNameDeclaration(n.getFirstChild());
        } else if (ClassUtil.isClassMethod(n) && ClassUtil.hasNamedClass(n)) {
          currentFile.recordMethod(n);
        }
        break;
      case EXPR_RESULT:
        Node expr = n.getFirstChild();
        switch (expr.getToken()) {
          case CALL:
            Node callee = expr.getFirstChild();
            if (callee.matchesQualifiedName("goog.provide")) {
              currentFile.markProvided(expr.getLastChild().getString());
            } else if (callee.matchesQualifiedName("goog.require")) {
              currentFile.recordImport(expr.getLastChild().getString());
            } else if (callee.matchesQualifiedName("goog.define")) {
              currentFile.recordDefine(expr);
            }
            break;
          case ASSIGN:
            Node lhs = expr.getFirstChild();
            currentFile.recordNameDeclaration(lhs);
            processDeclarationWithRhs(t, lhs);
            break;
          case GETPROP:
            currentFile.recordNameDeclaration(expr);
            break;
          default:
            throw new RuntimeException("Unexpected declaration: " + expr);
        }
        break;
      case VAR:
      case CONST:
      case LET:
        checkState(n.hasOneChild(), n);
        recordNameDeclaration(n);
        if (n.getFirstChild().isName() && n.getFirstChild().hasChildren()) {
          processDeclarationWithRhs(t, n.getFirstChild());
        }
        break;
      case STRING_KEY:
        if (parent.isObjectLit() && n.hasOneChild()) {
          processDeclarationWithRhs(t, n);
          currentFile.recordStringKeyDeclaration(n);
        }
        break;
      default:
        break;
    }
  }

  private void recordNameDeclaration(Node decl) {
    checkArgument(NodeUtil.isNameDeclaration(decl));
    Node rhs = decl.getFirstChild().getLastChild();
    boolean isImport = PotentialDeclaration.isImportRhs(rhs);
    for (Node name : NodeUtil.findLhsNodesInNode(decl)) {
      if (isImport) {
        currentFile.recordImport(name.getString());
      } else {
        currentFile.recordNameDeclaration(name);
      }
    }
  }

  private void processDeclarationWithRhs(NodeTraversal t, Node lhs) {
    checkArgument(
        lhs.isQualifiedName() || lhs.isStringKey() || lhs.isDestructuringLhs(),
        lhs);
    checkState(NodeUtil.getRValueOfLValue(lhs) != null, lhs);
    if (!PotentialDeclaration.isConstToBeInferred(lhs)) {
      return;
    }
    processConstWithRhs(t, lhs);
  }

  protected abstract void processConstWithRhs(NodeTraversal t, Node lhs);
}
