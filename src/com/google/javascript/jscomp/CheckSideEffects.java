/*
 * Copyright 2006 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Checks for non side effecting statements such as
 * <pre>
 * var s = "this string is "
 *         "continued on the next line but you forgot the +";
 * x == foo();  // should that be '='?
 * foo();;  // probably just a stray-semicolon. Doesn't hurt to check though
 * </p>
 * and generates warnings.
 *
 */
final class CheckSideEffects extends AbstractPostOrderCallback {

  static final DiagnosticType USELESS_CODE_ERROR = DiagnosticType.warning(
      "JSC_USELESS_CODE",
      "Suspicious code. {0}");

  private final CheckLevel level;

  CheckSideEffects(CheckLevel level) {
    this.level = level;
  }

  public void visit(NodeTraversal t, Node n, Node parent) {
    // VOID nodes appear when there are extra semicolons at the BLOCK level.
    // I've been unable to think of any cases where this indicates a bug,
    // and apparently some people like keeping these semicolons around,
    // so we'll allow it.
    if (n.getType() == Token.EMPTY ||
        n.getType() == Token.COMMA) {
      return;
    }

    if (parent == null)
      return;

    int pt = parent.getType();
    if (pt == Token.COMMA) {
      Node gramps = parent.getParent();
      if (gramps.getType() == Token.CALL &&
          parent == gramps.getFirstChild()) {
        // Semantically, a direct call to eval is different from an indirect
        // call to an eval. See Ecma-262 S15.1.2.1. So it's ok for the first
        // expression to a comma to be a no-op if it's used to indirect
        // an eval.
        if (n == parent.getFirstChild() &&
            parent.getChildCount() == 2 &&
            n.getNext().getType() == Token.NAME &&
            "eval".equals(n.getNext().getString())) {
          return;
        }
      }

      if (n == parent.getLastChild()) {
        for (Node an : parent.getAncestors()) {
          int ancestorType = an.getType();
          if (ancestorType == Token.COMMA)
            continue;
          if (ancestorType != Token.EXPR_RESULT &&
              ancestorType != Token.BLOCK)
            return;
          else
            break;
        }
      }
    } else if (pt != Token.EXPR_RESULT && pt != Token.BLOCK) {
      if (pt == Token.FOR && parent.getChildCount() == 4 &&
          (n == parent.getFirstChild() ||
           n == parent.getFirstChild().getNext().getNext())) {
        // Fall through and look for warnings for the 1st and 3rd child
        // of a for.
      } else {
        return;  // it might be ok to not have a side-effect
      }
    }
    if (NodeUtil.isSimpleOperatorType(n.getType()) ||
        !NodeUtil.mayHaveSideEffects(n, t.getCompiler())) {
      if (n.isQualifiedName() && n.getJSDocInfo() != null) {
        // This no-op statement was there so that JSDoc information could
        // be attached to the name. This check should not complain about it.
        return;
      } else if (NodeUtil.isExpressionNode(n)) {
        // we already reported the problem when we visited the child.
        return;
      }

      String msg = "This code lacks side-effects. Is there a bug?";
      if (n.getType() == Token.STRING) {
        msg = "Is there a missing '+' on the previous line?";
      }

      t.getCompiler().report(
          t.makeError(n, level, USELESS_CODE_ERROR, msg));
    }
  }
}
