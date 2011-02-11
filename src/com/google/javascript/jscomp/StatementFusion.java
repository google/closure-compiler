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
 * goodies in {@link PeepholeSubstituteAlternateSyntax}.
 *
 */
public class StatementFusion extends AbstractPeepholeOptimization {

  @Override
  Node optimizeSubtree(Node n) {
    // The block of a function body always need { }.
    if (!NodeUtil.isFunction(n.getParent()) && canFuseIntoOneStatement(n)) {
      fuseIntoOneStatement(n);
      reportCodeChange();
    }
    return n;
  }

  private static boolean canFuseIntoOneStatement(Node block) {
    // Fold only statement block. NOT scripts block.
    if (block.getType() != Token.BLOCK) {
      return false;
    }

    // Nothing to do here.
    if (!block.hasChildren() || block.hasOneChild()) {
      return false;
    }

    Node last = block.getLastChild();

    for (Node c = block.getFirstChild(); c != null; c = c.getNext()) {
      if (!NodeUtil.isExpressionNode(c) && c != last) {
        return false;
      }
    }

    // TODO(user): Support more control statement for fusion.
    // FOR, FOR-IN
    switch(last.getType()) {
      case Token.IF:
      case Token.THROW:
      case Token.SWITCH:
      case Token.EXPR_RESULT:
        return true;
      case Token.RETURN:
        // We don't want to add a new return value.
        return last.hasChildren();
    }

    return false;
  }

  private void fuseIntoOneStatement(Node block) {
    Node cur = block.removeFirstChild();

    // Starts building a tree.
    Node commaTree = cur.removeFirstChild();


    while (block.hasMoreThanOneChild()) {
      Node next = block.removeFirstChild().removeFirstChild();
      commaTree = fuseExpressionIntoExpression(commaTree, next);
    }

    Preconditions.checkState(block.hasOneChild());
    Node last = block.getLastChild();

    // Now we are just left with two statements. The comma tree of the first
    // n - 1 statements (which can be used in an expression) and the last
    // statement. We perform specific fusion based on the last statement's type.
    switch(last.getType()) {
      case Token.IF:
      case Token.RETURN:
      case Token.THROW:
      case Token.SWITCH:
      case Token.EXPR_RESULT:
        fuseExpresssonIntoFirstChild(commaTree, last);
        return;
      default:
        throw new IllegalStateException("Statement fusion missing.");
    }
  }

  // exp1, exp1
  private static Node fuseExpressionIntoExpression(Node exp1, Node exp2) {
    Node comma = new Node(Token.COMMA, exp1);
    comma.copyInformationFrom(exp2);

    // We can just join the new comma expression with another comma but
    // lets keep all the comma's in a straight line. That way we can use
    // tree comparison.
    if (exp2.getType() == Token.COMMA) {
      Node leftMostChild = exp2;
      while(leftMostChild.getType() == Token.COMMA) {
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

  private static void fuseExpresssonIntoFirstChild(Node exp, Node stmt) {
    Node val = stmt.removeFirstChild();
    Node comma = fuseExpressionIntoExpression(exp, val);
    stmt.addChildToFront(comma);
  }
}
