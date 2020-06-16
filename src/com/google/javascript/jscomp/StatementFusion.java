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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Tries to fuse all the statements in a block into a one statement by using COMMAs or statements.
 *
 * <p>Because COMMAs has the lowest precedence, we never need to insert extra () around. Once we
 * have only one statement in a block, we can then eliminate a pair of {}'s. Further more, we can
 * also fold a single statement IF into && or create further opportunities for all the other goodies
 * in {@link PeepholeMinimizeConditions}.
 *
 * <p>NOTE(user): The current compiler assumes that there are more ;'s than ,'s in a real
 * program, and so it makes sense to prefer fusing statements with semicolons rather than commas.
 * This assumption has never been validated on a real program.
 */
class StatementFusion extends AbstractPeepholeOptimization {

  @Override
  Node optimizeSubtree(Node n) {
    if (n.getParent().isFunction() || !canFuseIntoOneStatement(n)) {
      return n;
    }

    Node start = n.getFirstChild();
    Node end = n.getLastChild();
    Node result = fuseIntoOneStatement(n, start, end);
    fuseExpressionIntoControlFlowStatement(result, n.getLastChild());

    reportChangeToEnclosingScope(n);
    return n;
  }

  private boolean canFuseIntoOneStatement(Node block) {
    if (!block.isBlock()) {
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
    switch (n.getToken()) {
      case IF:
      case THROW:
      case SWITCH:
      case EXPR_RESULT:
        return true;
      case RETURN:
        // We don't want to add a new return value.
        return n.hasChildren();
      case FOR:
        // Avoid cases where we have for(var x;_;_) { ....
        return !NodeUtil.isNameDeclaration(n.getFirstChild());
      case FOR_IN:
        // Avoid cases where we have for(var x = foo() in a) { ....
        return !mayHaveSideEffects(n.getFirstChild());
      case LABEL:
        return isFusableControlStatement(n.getLastChild());
      case BLOCK:
        return (isASTNormalized() || NodeUtil.canMergeBlock(n))
            && !n.isSyntheticBlock()
            && isFusableControlStatement(n.getFirstChild());
      default:
        break;
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
    checkArgument(before.isExprResult(), "before must be expression result");

    // Now we are just left with two statements. The comma tree of the first
    // n - 1 statements (which can be used in an expression) and the last
    // statement. We perform specific fusion based on the last statement's type.
    switch (control.getToken()) {
      case IF:
      case RETURN:
      case THROW:
      case SWITCH:
      case EXPR_RESULT:
      case FOR:
        before.detach();
        fuseExpressionIntoFirstChild(before.removeFirstChild(), control);
        return;
      case FOR_IN:
        before.detach();
        fuseExpressionIntoSecondChild(before.removeFirstChild(), control);
        return;
      case LABEL:
        fuseExpressionIntoControlFlowStatement(before, control.getLastChild());
        return;
      case BLOCK:
        fuseExpressionIntoControlFlowStatement(before, control.getFirstChild());
        return;
      default:
        throw new IllegalStateException("Statement fusion missing.");
    }
  }

  // exp1, exp1
  static Node fuseExpressionIntoExpression(Node exp1, Node exp2) {
    if (exp2.isEmpty()) {
      return exp1;
    }
    Node comma = new Node(Token.COMMA, exp1);
    comma.useSourceInfoIfMissingFrom(exp2);

    // We can just join the new comma expression with another comma but
    // lets keep all the comma's in a straight line. That way we can use
    // tree comparison.
    if (exp2.isComma()) {
      Node leftMostChild = exp2;
      while (leftMostChild.isComma()) {
        leftMostChild = leftMostChild.getFirstChild();
      }
      Node parent = leftMostChild.getParent();
      comma.addChildToBack(leftMostChild.detach());
      parent.addChildToFront(comma);
      return exp2;
    } else {
      comma.addChildToBack(exp2);
      return comma;
    }
  }

  protected static void fuseExpressionIntoFirstChild(Node exp, Node stmt) {
    Node val = stmt.removeFirstChild();
    Node comma = fuseExpressionIntoExpression(exp, val);
    stmt.addChildToFront(comma);
  }

  protected static void fuseExpressionIntoSecondChild(Node exp, Node stmt) {
    Node val = stmt.getSecondChild().detach();
    Node comma = fuseExpressionIntoExpression(exp, val);
    stmt.addChildAfter(comma, stmt.getFirstChild());
  }
}
