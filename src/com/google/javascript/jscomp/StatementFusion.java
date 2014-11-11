/*
 * Copyright 2011 The Closure Compiler Authors.
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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Tries to fuse all the statements in a block into a one statement by using
 * COMMAs.
 *
 * Because COMMAs has the lowest precedence, we never need to insert
 * extra () around. Once we have only one statement in a block, we can then
 * eliminate a pair of {}'s. Further more, we can also fold a single
 * statement IF into && or create further opportunities for all the other
 * goodies in {@link PeepholeMinimizeConditions}.
 *
 */
class StatementFusion extends AbstractPeepholeOptimization {
  // TODO(user): We probably need to test this more. The current compiler
  // assumes that there are more ;'s than ,'s in a real program. However,
  // this assumption may be incorrect. We can probably do a quick traverse
  // to check this assumption if that's necessary.
  public static final boolean SHOULD_FAVOR_COMMA_OVER_SEMI_COLON = false;

  private final boolean favorsCommaOverSemiColon;

  public StatementFusion() {
    this(SHOULD_FAVOR_COMMA_OVER_SEMI_COLON);
  }

  public StatementFusion(boolean favorsCommaOverSemiColon) {
    this.favorsCommaOverSemiColon = favorsCommaOverSemiColon;
  }

  @Override
  Node optimizeSubtree(Node n) {
    // TODO(user): It is much cleaner to have two algorithms depending
    // on favorsCommaOverSemiColon. If we decided the less aggressive one is
    // no longer useful, delete it.
    if (favorsCommaOverSemiColon) {
      return tryFuseStatementsAggressively(n);
    } else {
      return tryFuseStatements(n);
    }
  }

  Node tryFuseStatements(Node n) {
    if (!n.getParent().isFunction() && canFuseIntoOneStatement(n)) {
      Node start = n.getFirstChild();
      Node end = n.getLastChild();
      Node result = fuseIntoOneStatement(n, start, end);
      fuseExpressionIntoControlFlowStatement(result, n.getLastChild());
      reportCodeChange();
    }
    return n;
  }

  Node tryFuseStatementsAggressively(Node n) {
    if (!NodeUtil.isStatementBlock(n)) {
      return n;
    }

    Node cur = n.getFirstChild();
    while (cur != null) {
      if (!cur.isExprResult()) {
        cur = cur.getNext();
        continue;
      }
      Node next = cur.getNext();
      while (next != null && next.isExprResult()) {
        next = next.getNext();
      }
      if (cur.getNext() != next) {
        cur = fuseIntoOneStatement(n, cur, next);
        reportCodeChange();
      }
      if (cur.isExprResult() &&
          next != null && isFusableControlStatement(next)) {
        fuseExpressionIntoControlFlowStatement(cur, next);
        reportCodeChange();
        next = next.getNext();
      }
      cur = next;
    }

    return n;
  }

  private boolean canFuseIntoOneStatement(Node block) {
    // If we are favoring semi-colon, we shouldn't fuse script blocks.
    if (!favorsCommaOverSemiColon && !block.isBlock()) {
      return false;
    }

    // Nothing to do here.
    if (!block.hasChildren() || block.hasOneChild()) {
      return false;
    }

    Node last = block.getLastChild();

    for (Node c = block.getFirstChild(); c != null; c = c.getNext()) {
      if (!c.isExprResult() && c != last) {
        return false;
      }
    }

    return isFusableControlStatement(last);
  }

  private boolean isFusableControlStatement(Node n) {
    switch(n.getType()) {
      case Token.IF:
      case Token.THROW:
      case Token.SWITCH:
      case Token.EXPR_RESULT:
        return true;
      case Token.RETURN:
        // We don't want to add a new return value.
        return n.hasChildren();
      case Token.FOR:
        if (NodeUtil.isForIn(n)) {
          // Avoid cases where we have for(var x = foo() in a) { ....
          return !mayHaveSideEffects(n.getFirstChild());
        } else {
          // Avoid cases where we have for(var x;_;_) { ....
          return !n.getFirstChild().isVar();
        }
      case Token.LABEL:
        return isFusableControlStatement(n.getLastChild());
      case Token.BLOCK:
        return !n.isSyntheticBlock() &&
            isFusableControlStatement(n.getFirstChild());
    }
    return false;
  }

  /**
   * Given a block, fuse a list of statements with comma's.
   *
   * @param parent The parent that contains the statements.
   * @param first The first statement to fuse (inclusive)
   * @param last The last statement to fuse (exclusive)
   * @return A single statement that contains all the fused statement as one.
   */
  private static Node fuseIntoOneStatement(Node parent, Node first, Node last) {
    // Nothing to fuse if there is only one statement.
    if (first.getNext() == last) {
      return first;
    }

    // Step one: Create a comma tree that contains all the statements.
    Node commaTree = first.removeFirstChild();

    Node next = null;
    for (Node cur = first.getNext(); cur != last; cur = next) {
      commaTree = fuseExpressionIntoExpression(
          commaTree, cur.removeFirstChild());
      next = cur.getNext();
      parent.removeChild(cur);
    }

    // Step two: The last EXPR_RESULT will now hold the comma tree with all
    // the fused statements.
    first.addChildToBack(commaTree);
    return first;
  }

  private static void fuseExpressionIntoControlFlowStatement(
      Node before, Node control) {
    Preconditions.checkArgument(before.isExprResult(),
        "before must be expression result");

    // Now we are just left with two statements. The comma tree of the first
    // n - 1 statements (which can be used in an expression) and the last
    // statement. We perform specific fusion based on the last statement's type.
    switch(control.getType()) {
      case Token.IF:
      case Token.RETURN:
      case Token.THROW:
      case Token.SWITCH:
      case Token.EXPR_RESULT:
        before.getParent().removeChild(before);
        fuseExpresssonIntoFirstChild(before.removeFirstChild(), control);
        return;
      case Token.FOR:
        before.getParent().removeChild(before);
        if (NodeUtil.isForIn(control)) {
          fuseExpresssonIntoSecondChild(before.removeFirstChild(), control);
        } else {
          fuseExpresssonIntoFirstChild(before.removeFirstChild(), control);
        }
        return;
      case Token.LABEL:
        fuseExpressionIntoControlFlowStatement(before, control.getLastChild());
        return;
      case Token.BLOCK:
        fuseExpressionIntoControlFlowStatement(before, control.getFirstChild());
        return;
      default:
        throw new IllegalStateException("Statement fusion missing.");
    }
  }

  // exp1, exp1
  protected static Node fuseExpressionIntoExpression(Node exp1, Node exp2) {
    if (exp2.isEmpty()) {
      return exp1;
    }
    Node comma = new Node(Token.COMMA, exp1);
    comma.copyInformationFrom(exp2);

    // We can just join the new comma expression with another comma but
    // lets keep all the comma's in a straight line. That way we can use
    // tree comparison.
    if (exp2.isComma()) {
      Node leftMostChild = exp2;
      while (leftMostChild.isComma()) {
        leftMostChild = leftMostChild.getFirstChild();
      }
      Node parent = leftMostChild.getParent();
      comma.addChildToBack(leftMostChild.detachFromParent());
      parent.addChildToFront(comma);
      return exp2;
    } else {
      comma.addChildToBack(exp2);
      return comma;
    }
  }

  protected static void fuseExpresssonIntoFirstChild(Node exp, Node stmt) {
    Node val = stmt.removeFirstChild();
    Node comma = fuseExpressionIntoExpression(exp, val);
    stmt.addChildToFront(comma);
  }

  protected static void fuseExpresssonIntoSecondChild(Node exp, Node stmt) {
    Node val = stmt.removeChildAfter(stmt.getFirstChild());
    Node comma = fuseExpressionIntoExpression(exp, val);
    stmt.addChildAfter(comma, stmt.getFirstChild());
  }
}
