/*
 * Copyright 2004 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashSet;
import java.util.Set;

/**
 * Verifies that constants are only assigned a value once.
 * e.g. var XX = 5;
 * XX = 3;    // error!
 * XX++;      // error!
 *
 */
class ConstCheck extends AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType CONST_REASSIGNED_VALUE_ERROR =
      DiagnosticType.error(
          "JSC_CONSTANT_REASSIGNED_VALUE_ERROR",
          "constant {0} assigned a value more than once.\n" +
          "Original definition at {1}");

  private final AbstractCompiler compiler;
  private final Set<Var> initializedConstants;

  /**
   * Creates an instance.
   */
  public ConstCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.initializedConstants = new HashSet<>();
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());
    NodeTraversal.traverseRoots(compiler, this, externs, root);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.NAME:
        if (parent != null &&
            parent.isVar()) {
          String name = n.getString();
          Var var = t.getScope().getVar(name);
          if (isConstant(var)) {
            // If a constant is declared in externs, add it to initializedConstants to indicate
            // that it is initialized externally.
            if (n.isFromExterns()) {
              initializedConstants.add(var);
            } else if (n.hasChildren()) {
              if (!initializedConstants.add(var)) {
                reportError(t, n, var, name);
              }
            }
          }
        }
        break;

      case Token.ASSIGN:
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD: {
        Node lhs = n.getFirstChild();
        if (lhs.isName()) {
          String name = lhs.getString();
          Var var = t.getScope().getVar(name);
          if (isConstant(var)) {
            if (!initializedConstants.add(var)) {
              reportError(t, n, var, name);
            }
          }
        }
        break;
      }

      case Token.INC:
      case Token.DEC: {
        Node lhs = n.getFirstChild();
        if (lhs.isName()) {
          String name = lhs.getString();
          Var var = t.getScope().getVar(name);
          if (isConstant(var)) {
            reportError(t, n, var, name);
          }
        }
        break;
      }
    }
  }

  /**
   * Gets whether a variable is a constant initialized to a literal value at
   * the point where it is declared.
   */
  private static boolean isConstant(Var var) {
    return var != null && var.isInferredConst();
  }

  /**
   * Reports a reassigned constant error.
   */
  void reportError(NodeTraversal t, Node n, Var var, String name) {
    JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
    if (info == null || !info.getSuppressions().contains("const")) {
      Node declNode = var.getNode();
      String declaredPosition = declNode.getSourceFileName() + ":" + declNode.getLineno();
      compiler.report(t.makeError(n, CONST_REASSIGNED_VALUE_ERROR, name, declaredPosition));
    }
  }
}
