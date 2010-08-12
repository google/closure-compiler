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
      case Token.SCRIPT:
      case Token.BLOCK:
        return tryOptimizeBlock(subtree);
      case Token.EXPR_RESULT:
        return tryFoldHookExpr(subtree);
      case Token.HOOK:
        return tryFoldHook(subtree);
      case Token.SWITCH:
        return tryOptimizeSwitch(subtree);
      case Token.IF:
        return tryFoldIf(subtree);
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

  /**
   * Remove useless switches and cases.
   */
  private Node tryOptimizeSwitch(Node n) {
    Preconditions.checkState(n.getType() == Token.SWITCH);

    Node defaultCase = findDefaultCase(n);
    if (defaultCase != null && isUselessCase(defaultCase)) {
      NodeUtil.redeclareVarsInsideBranch(defaultCase);
      n.removeChild(defaultCase);
      reportCodeChange();
      defaultCase = null;
    }

    // Removing cases when there exists a default case is not safe.
    // TODO(johnlenz): Allow this if the same code is executed.
    if (defaultCase == null) {
      Node next = null;
      // The first child is the switch conditions skip it.
      for (Node c = n.getFirstChild().getNext(); c != null; c = next) {
        next = c.getNext();
        if (!mayHaveSideEffects(c.getFirstChild()) && isUselessCase(c)) {
          NodeUtil.redeclareVarsInsideBranch(c);
          n.removeChild(c);
          reportCodeChange();
        }
      }
    }

    if (n.hasOneChild()) {
      Node condition = n.removeFirstChild();
      Node parent = n.getParent();
      Node replacement = new Node(Token.EXPR_RESULT, condition)
                            .copyInformationFrom(n);
      parent.replaceChild(n, replacement);
      reportCodeChange();
      return replacement;
    }

    return null;
  }

  /**
   * @return the default case node or null.
   */
  private Node findDefaultCase(Node n) {
    for (Node c : n.children()) {
      if (c.getType() == Token.DEFAULT) {
        return c;
      }
    }
    return null;
  }

  /**
   * @return Whether the CASE or DEFAULT block does anything useful.
   */
  private boolean isUselessCase(Node caseNode) {
    Node executingCase = caseNode;
    while (executingCase != null) {
      Preconditions.checkState(executingCase.getType() == Token.DEFAULT
          || executingCase.getType() == Token.CASE);
      // We only expect a DEFAULT case if the case we are checking is the
      // DEFAULT case.  Otherwise we assume the DEFAULT case has already
      // been removed.
      Preconditions.checkState(caseNode == executingCase
          || executingCase.getType() != Token.DEFAULT);
      Node block = executingCase.getLastChild();
      Preconditions.checkState(block.getType() == Token.BLOCK);
      if (block.hasChildren()) {
        for (Node blockChild : block.children()) {
          int type = blockChild.getType();
          // If this is a block with a labelless break, it is useless.
          switch (blockChild.getType()) {
            case Token.BREAK:
              // A break to a different control structure isn't useless.
              return blockChild.getFirstChild() == null;
            case Token.VAR:
              if (blockChild.hasOneChild()
                  && blockChild.getFirstChild().getFirstChild() == null) {
                // Variable declarations without initializations are ok.
                continue;
              }
              return false;
            default:
              return false;
          }
        }
      } else {
        // Look at the fallthrough case
        executingCase = executingCase.getNext();
      }
    }
    return true;
  }

  private Node tryFoldComma(Node n) {
    // If the left side does nothing replace the comma with the result.

    Node parent = n.getParent();
    Node left = n.getFirstChild();
    Node right = left.getNext();

    if (!mayHaveSideEffects(left)) {
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
  Node tryOptimizeBlock(Node n) {
    // Remove any useless children
    for (Node c = n.getFirstChild(); c != null; ) {
      Node next = c.getNext();  // save c.next, since 'c' may be removed
      if (!mayHaveSideEffects(c)) {
        n.removeChild(c);  // lazy kids
        reportCodeChange();
      } else {
        tryOptimizeConditionalAfterAssign(c);
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

  // TODO(johnlenz): Consider moving this to a separate peephole pass.
  /**
   * Attempt to replace the condition of if or hook immediately that is a
   * reference to a name that is assigned immediately before.
   */
  private void tryOptimizeConditionalAfterAssign(Node n) {
    Node next = n.getNext();

    // Look for patterns like the following and replace the if-condition with
    // a constant value so it can later be folded:
    //   var a = /a/;
    //   if (a) {foo(a)}
    // or
    //   a = 0;
    //   a ? foo(a) : c;
    // or
    //   a = 0;
    //   a || foo(a);
    // or
    //   a = 0;
    //   a && foo(a)
    //
    // TODO(johnlenz): This would be better handled by control-flow sensitive
    // constant propagation. As the other case that I want to handle is:
    //   i=0; for(;i<0;i++){}
    // as right now nothing facilitates removing a loop like that.
    // This is here simply to remove the cruft left behind goog.userAgent and
    // similar cases.

    if (isSimpleAssignment(n) && isConditionalStatement(next)) {
      Node lhsAssign = getSimpleAssignmentName(n);

      Node condition = getConditionalStatementCondition(next);
      if (NodeUtil.isName(lhsAssign) && NodeUtil.isName(condition)
          && lhsAssign.getString().equals(condition.getString())) {
        Node rhsAssign = getSimpleAssignmentValue(n);
        TernaryValue value = NodeUtil.getExpressionBooleanValue(rhsAssign);
        if (value != TernaryValue.UNKNOWN) {
          int replacementConditionNodeType =
            (value.toBoolean(true)) ? Token.TRUE : Token.FALSE;
          condition.getParent().replaceChild(condition,
              new Node(replacementConditionNodeType));
          reportCodeChange();
        }
      }
    }
  }

  /**
   * @return whether the node is a assignment to a simple name, or simple var
   * declaration with initialization.
   */
  private boolean isSimpleAssignment(Node n) {
    // For our purposes we define a simple assignment to be a assignment
    // to a NAME node, or a VAR declaration with one child and a initializer.
    if (NodeUtil.isExprAssign(n)
        && NodeUtil.isName(n.getFirstChild().getFirstChild())) {
      return true;
    } else if (n.getType() == Token.VAR && n.hasOneChild() &&
        n.getFirstChild().getFirstChild() != null) {
      return true;
    }

    return false;
  }

  /**
   * @return The name being assigned to.
   */
  private Node getSimpleAssignmentName(Node n) {
    Preconditions.checkState(isSimpleAssignment(n));
    if (NodeUtil.isExprAssign(n)) {
      return n.getFirstChild().getFirstChild();
    } else {
      // A var declaration.
      return n.getFirstChild();
    }
  }

  /**
   * @return The value assigned in the simple assignment
   */
  private Node getSimpleAssignmentValue(Node n) {
    Preconditions.checkState(isSimpleAssignment(n));
    return n.getFirstChild().getLastChild();
  }

  /**
   * @return Whether the node is a conditional statement.
   */
  private boolean isConditionalStatement(Node n) {
    // We defined a conditional statement to be a IF or EXPR_RESULT rooted with
    // a HOOK, AND, or OR node.
    return n != null && (n.getType() == Token.IF || isExprConditional(n));
  }

  /**
   * @return Whether the node is a rooted with a HOOK, AND, or OR node.
   */
  private boolean isExprConditional(Node n) {
    if (n.getType() == Token.EXPR_RESULT) {
      switch (n.getFirstChild().getType()) {
        case Token.HOOK:
        case Token.AND:
        case Token.OR:
          return true;
      }
    }
    return false;
  }

  /**
   * @return The condition of a conditional statement.
   */
  private Node getConditionalStatementCondition(Node n) {
    if (n.getType() == Token.IF) {
      return NodeUtil.getConditionExpression(n);
    } else {
      Preconditions.checkState(isExprConditional(n));
      return n.getFirstChild().getFirstChild();
    }
  }

  /**
   * Try folding IF nodes by removing dead branches.
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldIf(Node n) {
    Preconditions.checkState(n.getType() == Token.IF);
    Node parent = n.getParent();
    Preconditions.checkNotNull(parent);
    int type = n.getType();
    Node cond = n.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();

    // if (x) { .. } else { } --> if (x) { ... }
    if (elseBody != null && !mayHaveSideEffects(elseBody)) {
      n.removeChild(elseBody);
      elseBody = null;
      reportCodeChange();
    }

    // if (x) { } else { ... } --> if (!x) { ... }
    if (!mayHaveSideEffects(thenBody) && elseBody != null) {
      n.removeChild(elseBody);
      n.replaceChild(thenBody, elseBody);
      Node notCond = new Node(Token.NOT);
      n.replaceChild(cond, notCond);
      notCond.addChildToFront(cond);
      cond = notCond;
      thenBody = cond.getNext();
      elseBody = null;
      reportCodeChange();
    }

    // if (x()) { }
    if (!mayHaveSideEffects(thenBody) && elseBody == null) {
      if (mayHaveSideEffects(cond)) {
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

    // Try transforms that apply to both IF and HOOK.
    TernaryValue condValue = NodeUtil.getExpressionBooleanValue(cond);
    if (condValue == TernaryValue.UNKNOWN) {
      return n;  // We can't remove branches otherwise!
    }

    if (mayHaveSideEffects(cond)) {
      // Transform "if (a = 2) {x =2}" into "if (true) {a=2;x=2}"
      boolean newConditionValue = condValue == TernaryValue.TRUE;
      // Add an elseBody if it is needed.
      if (!newConditionValue && elseBody == null) {
        elseBody = new Node(Token.BLOCK).copyInformationFrom(n);
        n.addChildToBack(elseBody);
      }
      Node newCond = new Node(newConditionValue ? Token.TRUE : Token.FALSE);
      n.replaceChild(cond, newCond);
      Node branchToKeep = newConditionValue ? thenBody : elseBody;
      branchToKeep.addChildToFront(
          new Node(Token.EXPR_RESULT, cond).copyInformationFrom(cond));
      reportCodeChange();
      cond = newCond;
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
        // Remove "if (false) { X }" completely.
        NodeUtil.redeclareVarsInsideBranch(n);
        NodeUtil.removeChild(parent, n);
        reportCodeChange();
        return null;
      }
    } else {
      // Replace "if (true) { X } else { Y }" with X, or
      // replace "if (false) { X } else { Y }" with Y.
      Node trueBranch = n.getFirstChild().getNext();
      Node falseBranch = trueBranch.getNext();
      Node branchToKeep = condTrue ? trueBranch : falseBranch;
      Node branchToRemove = condTrue ? falseBranch : trueBranch;
      NodeUtil.redeclareVarsInsideBranch(branchToRemove);
      n.removeChild(branchToKeep);
      parent.replaceChild(n, branchToKeep);
      reportCodeChange();
      return branchToKeep;
    }
  }

  /**
   * Transform HOOK (?:) to IF if this allows the removal of useless branches.
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldHookExpr(Node n) {
    Preconditions.checkState(NodeUtil.isExpressionNode(n));
    Node hook = n.getFirstChild();
    // Only looking at hooks here.
    if (hook.getType() != Token.HOOK) {
      return n;
    }

    Node parent = n.getParent();
    Preconditions.checkNotNull(parent);
    Node cond = hook.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();

    // Try to remove useless conditions and branches.
    if (!mayHaveSideEffects(thenBody)) {
      // x?void 0:y --> if(!x)y
      Node ifNode = new Node(Token.IF);
      if (cond.getType() == Token.NOT) {
        Node expr = cond.getFirstChild();
        cond.removeChild(expr);
        ifNode.addChildToBack(expr);
      } else {
        Node not = new Node(Token.NOT).copyInformationFrom(cond);
        hook.removeChild(cond);
        not.addChildToBack(cond);
        ifNode.addChildToBack(not);
      }

      hook.removeChild(elseBody);
      ifNode.addChildToBack(
          new Node(Token.BLOCK, NodeUtil.newExpr(elseBody))
              .copyInformationFrom(elseBody));

      parent.replaceChild(n, ifNode);
      reportCodeChange();
      return ifNode;
    } else if (!mayHaveSideEffects(elseBody)) {
      // x?y:void 0 --> if(x)y
      Node ifNode = new Node(Token.IF);
      hook.removeChild(cond);
      ifNode.addChildToBack(cond);
      hook.removeChild(thenBody);

      ifNode.addChildToBack(
          new Node(Token.BLOCK, NodeUtil.newExpr(thenBody))
              .copyInformationFrom(thenBody));

      parent.replaceChild(n, ifNode);
      reportCodeChange();
      return ifNode;
    }

    return n;
  }

  /**
   * Try folding HOOK (?:) if the condition results of the condition is known.
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldHook(Node n) {
    Preconditions.checkState(n.getType() == Token.HOOK);
    Node parent = n.getParent();
    Preconditions.checkNotNull(parent);
    Node cond = n.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();

    TernaryValue condValue = NodeUtil.getExpressionBooleanValue(cond);
    if (condValue == TernaryValue.UNKNOWN) {
      return n;  // We can't remove branches otherwise!
    }

    // Transform "(a = 2) ? x =2 : y" into "a=2,x=2"
    n.detachChildren();
    Node branchToKeep = condValue.toBoolean(true) ? thenBody : elseBody;
    Node replacement;
    if (mayHaveSideEffects(cond)) {
      replacement = new Node(Token.COMMA).copyInformationFrom(n);
      replacement.addChildToFront(cond);
      replacement.addChildToBack(branchToKeep);
    } else {
      replacement = branchToKeep;
    }

    parent.replaceChild(n, replacement);
    reportCodeChange();
    return replacement;
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
