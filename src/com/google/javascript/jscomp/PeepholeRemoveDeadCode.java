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
import com.google.common.base.Predicates;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

import javax.annotation.Nullable;

/**
 * Peephole optimization to remove useless code such as IF's with false
 * guard conditions, comma operator left hand sides with no side effects, etc.
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
      case Token.ASSIGN:
        return tryFoldAssignment(subtree);
      case Token.COMMA:
        return tryFoldComma(subtree);
      case Token.SCRIPT:
      case Token.BLOCK:
        return tryOptimizeBlock(subtree);
      case Token.EXPR_RESULT:
        subtree = tryFoldExpr(subtree);
        return subtree;
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
      case Token.TRY:
        return tryFoldTry(subtree);
      default:
          return subtree;
    }
  }

  /**
   * Remove try blocks without catch blocks and with empty or not
   * existent finally blocks.
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldTry(Node n) {
    // Removes TRYs that had its CATCH removed and/or empty FINALLY.
    Preconditions.checkState(n.getType() == Token.TRY);
    Node body = n.getFirstChild();
    Node catchBlock = body.getNext();
    Node finallyBlock = catchBlock.getNext();

    if (!catchBlock.hasChildren() &&
        (finallyBlock == null || !finallyBlock.hasChildren())) {
      n.removeChild(body);
      n.getParent().replaceChild(n, body);
      reportCodeChange();
      return body;
    }

    return n;
  }

  /**
   * Try removing identity assignments
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldAssignment(Node subtree) {
    Preconditions.checkState(subtree.getType() == Token.ASSIGN);
    Node left = subtree.getFirstChild();
    Node right = subtree.getLastChild();
    // Only names
    if (left.getType() == Token.NAME
        && right.getType() == Token.NAME
        && left.getString().equals(right.getString())) {
      subtree.getParent().replaceChild(subtree, right.detachFromParent());
      reportCodeChange();
      return right;
    }
    return subtree;
  }

  /**
   * Try folding EXPR_RESULT nodes by removing useless Ops and expressions.
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldExpr(Node subtree) {
    Node result = trySimpilifyUnusedResult(subtree.getFirstChild());
    if (result == null) {
      Node parent = subtree.getParent();
      // If the EXPR_RESULT no longer has any children, remove it as well.
      if (parent.getType() == Token.LABEL) {
        Node replacement = new Node(Token.BLOCK).copyInformationFrom(subtree);
        parent.replaceChild(subtree, replacement);
        subtree = replacement;
      } else {
        subtree.detachFromParent();
        subtree = null;
      }
    }
    return subtree;
  }

  /**
   * General cascading unused operation node removal.
   * @param n The root of the expression to simplify.
   * @return The replacement node, or null if the node was is not useful.
   */
  private Node trySimpilifyUnusedResult(Node n) {
    return trySimpilifyUnusedResult(n, true);
  }

  /**
   * General cascading unused operation node removal.
   * @param n The root of the expression to simplify.
   * @param removeUnused If true, the node is removed from the AST if
   *     it is not useful, otherwise it replaced with an EMPTY node.
   * @return The replacement node, or null if the node was is not useful.
   */
  private Node trySimpilifyUnusedResult(Node n, boolean removeUnused) {
    Node result = n;

    // Simplify the results of conditional expressions
    switch (n.getType()) {
      case Token.HOOK:
        Node trueNode = trySimpilifyUnusedResult(n.getFirstChild().getNext());
        Node falseNode = trySimpilifyUnusedResult(n.getLastChild());
        // If one or more of the conditional children were removed,
        // transform the HOOK to an equivalent operation:
        //    x() ? foo() : 1 --> x() && foo()
        //    x() ? 1 : foo() --> x() || foo()
        //    x() ? 1 : 1 --> x()
        //    x ? 1 : 1 --> null
        if (trueNode == null && falseNode != null) {
          n.setType(Token.OR);
          Preconditions.checkState(n.getChildCount() == 2);
        } else if (trueNode != null && falseNode == null) {
          n.setType(Token.AND);
          Preconditions.checkState(n.getChildCount() == 2);
        } else if (trueNode == null && falseNode == null) {
          result = trySimpilifyUnusedResult(n.getFirstChild());
        } else {
          // The structure didn't change.
          result = n;
        }
        break;
      case Token.AND:
      case Token.OR:
        // Try to remove the second operand from a AND or OR operations:
        //    x() || f --> x()
        //    x() && f --> x()
        Node conditionalResultNode = trySimpilifyUnusedResult(
            n.getLastChild());
        if (conditionalResultNode == null) {
          Preconditions.checkState(n.hasOneChild());
          // The conditionally executed code was removed, so
          // replace the AND/OR with its LHS or remove it if it isn't useful.
          result = trySimpilifyUnusedResult(n.getFirstChild());
        }
        break;
      case Token.FUNCTION:
        // A function expression isn't useful if it isn't used, remove it and
        // don't bother to look at its children.
        result = null;
        break;
      case Token.COMMA:
        // We rewrite other operations as COMMA expressions (which will later
        // get split into individual EXPR_RESULT statement, if possible), so
        // we special case COMMA (we don't want to rewrite COMMAs as new COMMAs
        // nodes.
        Node left = trySimpilifyUnusedResult(n.getFirstChild());
        Node right = trySimpilifyUnusedResult(n.getLastChild());
        if (left == null && right == null) {
          result = null;
        } else if (left == null) {
          result = right;
        } else if (right == null){
          result = left;
        } else {
          // The structure didn't change.
          result = n;
        }
        break;
      default:
        if (!NodeUtil.nodeTypeMayHaveSideEffects(n)) {
          // This is the meat of this function. The node itself doesn't generate
          // any side-effects but preserve any side-effects in the children.
          Node resultList = null;
          for (Node next, c = n.getFirstChild(); c != null; c = next) {
            next = c.getNext();
            c = trySimpilifyUnusedResult(c);
            if (c != null) {
              c.detachFromParent();
              if (resultList == null)  {
                // The first side-effect can be used stand-alone.
                resultList = c;
              } else {
                // Leave the side-effects in-place, simplifying it to a COMMA
                // expression.
                resultList = new Node(Token.COMMA, resultList, c)
                    .copyInformationFrom(c);
              }
            }
          }
          result = resultList;
        }
    }

    // Fix up the AST, replace or remove the an unused node (if requested).
    if (n != result) {
      Node parent = n.getParent();
      if (result == null) {
        if (removeUnused) {
          parent.removeChild(n);
        } else {
          result = new Node(Token.EMPTY).copyInformationFrom(n);
          parent.replaceChild(n, result);
        }
      } else {
        // A new COMMA expression may not have an existing parent.
        if (result.getParent() != null) {
          result.detachFromParent();
        }
        n.getParent().replaceChild(n, result);
      }
      reportCodeChange();
    }

    return result;
  }

  /**
   * Remove useless switches and cases.
   */
  private Node tryOptimizeSwitch(Node n) {
    Preconditions.checkState(n.getType() == Token.SWITCH);

    Node defaultCase = tryOptimizeDefaultCase(n);

    // Removing cases when there exists a default case is not safe.
    if (defaultCase == null) {
      Node next = null;
      Node prev = null;
      // The first child is the switch conditions skip it.
      for (Node c = n.getFirstChild().getNext(); c != null; c = next) {
        next = c.getNext();
        if (!mayHaveSideEffects(c.getFirstChild()) && isUselessCase(c, prev)) {
          removeCase(n, c);
        } else {
          prev = c;
        }
      }
    }

    // Remove the switch if there are no remaining cases.
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
   * @return the default case node or null if there is no default case or
   *     if the default case is removed.
   */
  private Node tryOptimizeDefaultCase(Node n) {
    Preconditions.checkState(n.getType() == Token.SWITCH);

    Node lastNonRemovable = n.getFirstChild();  // The switch condition

    // The first child is the switch conditions skip it when looking for cases.
    for (Node c = n.getFirstChild().getNext(); c != null; c = c.getNext()) {
      if (c.getType() == Token.DEFAULT) {
        // Remove cases that fall-through to the default case
        Node caseToRemove = lastNonRemovable.getNext();
        for (Node next; caseToRemove != c; caseToRemove = next) {
          next = caseToRemove.getNext();
          removeCase(n, caseToRemove);
        }

        // Don't use the switch condition as the previous case.
        Node prevCase = (lastNonRemovable == n.getFirstChild())
            ? null : lastNonRemovable;

        // Remove the default case if we can
        if (isUselessCase(c, prevCase)) {
          removeCase(n, c);
          return null;
        }
        return c;
      } else {
        Preconditions.checkState(c.getType() == Token.CASE);
        if (c.getLastChild().hasChildren()
            || mayHaveSideEffects(c.getFirstChild())) {
          lastNonRemovable = c;
        }
      }
    }
    return null;
  }

  /**
   * Remove the case from the switch redeclaring any variables declared in it.
   * @param caseNode The case to remove.
   */
  private void removeCase(Node switchNode, Node caseNode) {
    NodeUtil.redeclareVarsInsideBranch(caseNode);
    switchNode.removeChild(caseNode);
    reportCodeChange();
  }

  /**
   * The function assumes that when checking a CASE node there is no
   * DEFAULT node in the SWITCH.
   * @return Whether the CASE or DEFAULT block does anything useful.
   */
  private boolean isUselessCase(Node caseNode, @Nullable Node previousCase) {
    Preconditions.checkState(
        previousCase == null || previousCase.getNext() == caseNode);
    // A case isn't useless can't be useless if a previous case falls
    // through to it unless it happens to be the last case in the switch.
    Node switchNode = caseNode.getParent();
    if (switchNode.getLastChild() != caseNode
        && previousCase != null) {
      Node previousBlock = previousCase.getLastChild();
      if (!previousBlock.hasChildren()
          || !isExit(previousBlock.getLastChild())) {
        return false;
      }
    }

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

  /**
   * @return Whether the node is an obvious control flow exit.
   */
  private boolean isExit(Node n) {
    switch (n.getType()) {
      case Token.BREAK:
      case Token.CONTINUE:
      case Token.RETURN:
      case Token.THROW:
        return true;
      default:
        return false;
    }
  }

  private Node tryFoldComma(Node n) {
    // If the left side does nothing replace the comma with the result.

    Node parent = n.getParent();
    Node left = n.getFirstChild();
    Node right = left.getNext();

    left = trySimpilifyUnusedResult(left);
    if (left == null || !mayHaveSideEffects(left)) {
      // Fold it!
      n.removeChild(right);
      parent.replaceChild(n, right);
      reportCodeChange();
      return right;
    } else {
      if (parent.getType() == Token.EXPR_RESULT
          && parent.getParent().getType() != Token.LABEL) {
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
        // TODO(johnlenz): determine what this is actually removing. Candidates
        //    include: EMPTY nodes, control structures without children
        //    (removing infinite loops), empty try blocks.  What else?
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
    // If this is a FOR-IN loop skip it.
    if (NodeUtil.isForIn(n)) {
      return n;
    }

    Node init = n.getFirstChild();
    Node cond = init.getNext();
    Node increment = cond.getNext();

    if (init.getType() != Token.EMPTY && init.getType() != Token.VAR) {
      init = trySimpilifyUnusedResult(init, false);
    }

    if (increment.getType() != Token.EMPTY) {
      increment = trySimpilifyUnusedResult(increment, false);
    }

    // There is an initializer skip it
    if (n.getFirstChild().getType() != Token.EMPTY) {
      return n;
    }

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
