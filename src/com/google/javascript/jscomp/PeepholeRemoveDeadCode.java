/*
 * Copyright 2004 Google Inc.
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
import com.google.common.base.Predicates;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

/**
 * Peephole optimization to remove useless code such as IF's with false
 * guard conditions, comma operator left hand sides with no side effects, etc.
 *
*
*
 */
public class PeepholeRemoveDeadCode extends AbstractPeepholeOptimization {

  // TODO(dcc): Some (all) of these can probably be better achieved
  // using the control flow graph (like CheckUnreachableCode).
  // There is an existing CFG pass (UnreachableCodeElimination) that
  // could be changed to use code from CheckUnreachableCode to do this.

  @Override
  Node optimizeSubtree(Node subtree) {
    switch(subtree.getType()) {
      case Token.COMMA:
        return tryFoldComma(subtree);
      case Token.BLOCK:
        return tryFoldBlock(subtree);
      case Token.IF:
      case Token.HOOK:
        return tryFoldHookIf(subtree);
      case Token.WHILE:
        return tryFoldWhile(subtree);
       case Token.FOR: {
          Node condition = NodeUtil.getConditionExpression(subtree);
          if (condition != null) {
            tryFoldForCondition(condition);
          }
        }
        return tryFoldFor(subtree);
      case Token.DO:
        return tryFoldDo(subtree);
        default:
          return subtree;
    }
  }

  private Node tryFoldComma(Node n) {
    // If the left side does nothing replace the comma with the result.

    Node parent = n.getParent();
    Node left = n.getFirstChild();
    Node right = left.getNext();

    if (!NodeUtil.mayHaveSideEffects(left)) {
      // Fold it!
      n.removeChild(right);
      parent.replaceChild(n, right);
      reportCodeChange();
      return right;
    } else {
      if (parent.getType() == Token.EXPR_RESULT) {
        // split comma
        n.detachChildren();
        // Replace the original expression with the left operand.
        parent.replaceChild(n, left);
        // Add the right expression afterward.
        Node newStatement = new Node(Token.EXPR_RESULT, right);
        newStatement.copyInformationFrom(n);

        //This modifies outside the subtree, which is not
        //desirable in a peephole optimization.
        parent.getParent().addChildAfter(newStatement, parent);
        reportCodeChange();
        return left;
      }
    }

    return n;
  }

  /**
   * Try removing unneeded block nodes and their useless children
   */
  Node tryFoldBlock(Node n) {
    // TODO(dcc): Make sure this is also applied in the global scope
    // (i.e. with Token.SCRIPT) parents
    // Remove any useless children
    for (Node c = n.getFirstChild(); c != null; ) {
      Node next = c.getNext();  // save c.next, since 'c' may be removed
      if (!NodeUtil.mayHaveSideEffects(c)) {
        n.removeChild(c);  // lazy kids
        reportCodeChange();
      }
      c = next;
    }

    if (n.isSyntheticBlock() ||  n.getParent() == null) {
      return n;
    }

    // Try to remove the block.
    if (NodeUtil.tryMergeBlock(n)) {
      reportCodeChange();
      return null;
    }

    return n;
  }

  /**
   * Try folding :? (hook) and IF nodes by removing dead branches.
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldHookIf(Node n) {
    Node parent = n.getParent();
    int type = n.getType();
    Node cond = n.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();

    boolean changes = false;

    if (type == Token.IF) {
      // if (x) { .. } else { } --> if (x) { ... }
      if (elseBody != null && !NodeUtil.mayHaveSideEffects(elseBody)) {
        n.removeChild(elseBody);
        elseBody = null;
        reportCodeChange();
        changes = true;
      }

      // if (x) { } else { ... } --> if (!x) { ... }
      if (!NodeUtil.mayHaveSideEffects(thenBody) && elseBody != null) {
        n.removeChild(elseBody);
        n.replaceChild(thenBody, elseBody);
        Node notCond = new Node(Token.NOT);
        n.replaceChild(cond, notCond);
        notCond.addChildToFront(cond);
        cond = notCond;
        thenBody = cond.getNext();
        elseBody = null;
        reportCodeChange();
        changes = true;
      }

      // if (x()) { }
      if (!NodeUtil.mayHaveSideEffects(thenBody) && elseBody == null) {
        if (NodeUtil.mayHaveSideEffects(cond)) {
          // x() has side effects, just leave the condition on its own.
          n.removeChild(cond);
          Node replacement = NodeUtil.newExpr(cond);
          parent.replaceChild(n, replacement);
          reportCodeChange();
          return replacement;
        } else {
          // x() has no side effects, the whole tree is useless now.
          NodeUtil.removeChild(parent, n);
          reportCodeChange();
          return null;
        }
      }
    } else {
      Preconditions.checkState(type == Token.HOOK);
      if (NodeUtil.isExpressionNode(parent)) {
        // Try to remove useless nodes.
        if (!NodeUtil.mayHaveSideEffects(thenBody)) {
          // x?void 0:y --> if(!x)y
          Node ifNode = new Node(Token.IF);
          if (cond.getType() == Token.NOT) {
            Node expr = cond.getFirstChild();
            cond.removeChild(expr);
            ifNode.addChildToBack(expr);
          } else {
            Node not = new Node(Token.NOT).copyInformationFrom(cond);
            n.removeChild(cond);
            not.addChildToBack(cond);
            ifNode.addChildToBack(not);
          }

          n.removeChild(elseBody);
          ifNode.addChildToBack(
              new Node(Token.BLOCK, NodeUtil.newExpr(elseBody))
                  .copyInformationFrom(elseBody));

          //This modifies outside the subtree, which is not
          //desirable in a peephole optimization.
          parent.getParent().replaceChild(parent, ifNode);
          reportCodeChange();
          return ifNode;
        } else if (!NodeUtil.mayHaveSideEffects(elseBody)) {
          // x?y:void 0 --> if(x)y
          Node ifNode = new Node(Token.IF);
          n.removeChild(cond);
          ifNode.addChildToBack(cond);
          n.removeChild(thenBody);

          ifNode.addChildToBack(
              new Node(Token.BLOCK, NodeUtil.newExpr(thenBody))
                  .copyInformationFrom(thenBody));

          //This modifies outside the subtree, which is not
          //desirable in a peephole optimization.
          parent.getParent().replaceChild(parent, ifNode);
          reportCodeChange();
          return ifNode;
        }
      }
    }

    // Try transforms that apply to both IF and HOOK.
    if (!NodeUtil.isLiteralValue(cond)) {
      return n;  // We can't remove branches otherwise!
    }

    TernaryValue condValue = NodeUtil.getBooleanValue(cond);
    if (condValue == TernaryValue.UNKNOWN) {
      return n;  // We can't remove branches otherwise!
    }

    boolean condTrue = condValue.toBoolean(true);
    if (n.getChildCount() == 2) {
      Preconditions.checkState(type == Token.IF);

      if (condTrue) {
        // Replace "if (true) { X }" with "X".
        Node thenStmt = n.getFirstChild().getNext();
        n.removeChild(thenStmt);
        parent.replaceChild(n, thenStmt);
        reportCodeChange();
        return thenStmt;
      } else {
        // Replace "if (false) { X }" with empty node.
        NodeUtil.redeclareVarsInsideBranch(n);
        NodeUtil.removeChild(parent, n);
        reportCodeChange();
        return null;
      }
    } else {
      // Replace "if (true) { X } else { Y }" with X, or
      // replace "if (false) { X } else { Y }" with Y.
      Node firstBranch = n.getFirstChild().getNext();
      Node secondBranch = firstBranch.getNext();
      Node branch = condTrue ? firstBranch : secondBranch;
      Node notBranch = condTrue ? secondBranch : firstBranch;
      NodeUtil.redeclareVarsInsideBranch(notBranch);
      n.removeChild(branch);
      parent.replaceChild(n, branch);
      reportCodeChange();
      return branch;
    }
  }

  /**
   * Removes WHILEs that always evaluate to false.
   */
  Node tryFoldWhile(Node n) {
    Preconditions.checkArgument(n.getType() == Token.WHILE);
    Node cond = NodeUtil.getConditionExpression(n);
    if (NodeUtil.getBooleanValue(cond) != TernaryValue.FALSE) {
      return n;
    }
    NodeUtil.redeclareVarsInsideBranch(n);
    NodeUtil.removeChild(n.getParent(), n);
    reportCodeChange();

    return null;
  }

  /**
   * Removes FORs that always evaluate to false.
   */
  Node tryFoldFor(Node n) {
    Preconditions.checkArgument(n.getType() == Token.FOR);
    // This is not a FOR-IN loop
    if (n.getChildCount() != 4) {
      return n;
    }
    // There isn't an initializer
    if (n.getFirstChild().getType() != Token.EMPTY) {
      return n;
    }

    Node cond = NodeUtil.getConditionExpression(n);
    if (NodeUtil.getBooleanValue(cond) != TernaryValue.FALSE) {
      return n;
    }

    NodeUtil.redeclareVarsInsideBranch(n);
    NodeUtil.removeChild(n.getParent(), n);
    reportCodeChange();
    return null;
  }

  /**
   * Removes DOs that always evaluate to false. This leaves the
   * statements that were in the loop in a BLOCK node.
   * The block will be removed in a later pass, if possible.
   */
  Node tryFoldDo(Node n) {
    Preconditions.checkArgument(n.getType() == Token.DO);

    Node cond = NodeUtil.getConditionExpression(n);
    if (NodeUtil.getBooleanValue(cond) != TernaryValue.FALSE) {
      return n;
    }

    // TODO(johnlenz): The do-while can be turned into a label with
    // named breaks and the label optimized away (maybe).
    if (hasBreakOrContinue(n)) {
      return n;
    }

    Preconditions.checkState(
        NodeUtil.isControlStructureCodeBlock(n, n.getFirstChild()));
    Node block = n.removeFirstChild();

    n.getParent().replaceChild(n, block);
    reportCodeChange();

    return n;
  }

  /**
   *
   */
  boolean hasBreakOrContinue(Node n) {
    // TODO(johnlenz): This is overkill as named breaks may refer to outer
    // loops or labels, and any break my refer to an inner loop.
    // More generally, this check may be more expensive than we like.
    return NodeUtil.has(
        n,
        Predicates.<Node>or(
            new NodeUtil.MatchNodeType(Token.BREAK),
            new NodeUtil.MatchNodeType(Token.CONTINUE)),
        new NodeUtil.MatchNotFunction());
  }

  /**
   * Remove always true loop conditions.
   */
  private void tryFoldForCondition(Node forCondition) {
    if (NodeUtil.getBooleanValue(forCondition) == TernaryValue.TRUE) {
      forCondition.getParent().replaceChild(forCondition,
          new Node(Token.EMPTY));
      reportCodeChange();
    }
  }
}
