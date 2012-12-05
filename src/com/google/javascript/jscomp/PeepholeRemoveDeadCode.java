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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

import javax.annotation.Nullable;

/**
 * Peephole optimization to remove useless code such as IF's with false
 * guard conditions, comma operator left hand sides with no side effects, etc.
 *
 */
class PeepholeRemoveDeadCode extends AbstractPeepholeOptimization {

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
   * Or, only leave the finally blocks if try body blocks are empty
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldTry(Node n) {
    Preconditions.checkState(n.isTry());
    Node body = n.getFirstChild();
    Node catchBlock = body.getNext();
    Node finallyBlock = catchBlock.getNext();

    // Removes TRYs that had its CATCH removed and/or empty FINALLY.
    if (!catchBlock.hasChildren() &&
        (finallyBlock == null || !finallyBlock.hasChildren())) {
      n.removeChild(body);
      n.getParent().replaceChild(n, body);
      reportCodeChange();
      return body;
    }

    // Only leave FINALLYs if TRYs are empty
    if (!body.hasChildren()) {
      NodeUtil.redeclareVarsInsideBranch(catchBlock);
      if (finallyBlock != null) {
        n.removeChild(finallyBlock);
        n.getParent().replaceChild(n, finallyBlock);
      } else {
        n.getParent().removeChild(n);
      }
      reportCodeChange();
      return finallyBlock;
    }

    return n;
  }

  /**
   * Try removing identity assignments
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldAssignment(Node subtree) {
    Preconditions.checkState(subtree.isAssign());
    Node left = subtree.getFirstChild();
    Node right = subtree.getLastChild();
    // Only names
    if (left.isName()
        && right.isName()
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
    Node result = trySimplifyUnusedResult(subtree.getFirstChild());
    if (result == null) {
      Node parent = subtree.getParent();
      // If the EXPR_RESULT no longer has any children, remove it as well.
      if (parent.isLabel()) {
        Node replacement = IR.block().srcref(subtree);
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
  private Node trySimplifyUnusedResult(Node n) {
    return trySimplifyUnusedResult(n, true);
  }

  /**
   * General cascading unused operation node removal.
   * @param n The root of the expression to simplify.
   * @param removeUnused If true, the node is removed from the AST if
   *     it is not useful, otherwise it replaced with an EMPTY node.
   * @return The replacement node, or null if the node was is not useful.
   */
  private Node trySimplifyUnusedResult(Node n, boolean removeUnused) {
    Node result = n;

    // Simplify the results of conditional expressions
    switch (n.getType()) {
      case Token.HOOK:
        Node trueNode = trySimplifyUnusedResult(n.getFirstChild().getNext());
        Node falseNode = trySimplifyUnusedResult(n.getLastChild());
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
          result = trySimplifyUnusedResult(n.getFirstChild());
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
        Node conditionalResultNode = trySimplifyUnusedResult(
            n.getLastChild());
        if (conditionalResultNode == null) {
          Preconditions.checkState(n.hasOneChild());
          // The conditionally executed code was removed, so
          // replace the AND/OR with its LHS or remove it if it isn't useful.
          result = trySimplifyUnusedResult(n.getFirstChild());
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
        Node left = trySimplifyUnusedResult(n.getFirstChild());
        Node right = trySimplifyUnusedResult(n.getLastChild());
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
        if (!nodeTypeMayHaveSideEffects(n)) {
          // This is the meat of this function. The node itself doesn't generate
          // any side-effects but preserve any side-effects in the children.
          Node resultList = null;
          for (Node next, c = n.getFirstChild(); c != null; c = next) {
            next = c.getNext();
            c = trySimplifyUnusedResult(c);
            if (c != null) {
              c.detachFromParent();
              if (resultList == null)  {
                // The first side-effect can be used stand-alone.
                resultList = c;
              } else {
                // Leave the side-effects in-place, simplifying it to a COMMA
                // expression.
                resultList = IR.comma(resultList, c).srcref(c);
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
          result = IR.empty().srcref(n);
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
    Preconditions.checkState(n.isSwitch());

    Node defaultCase = tryOptimizeDefaultCase(n);

    // Removing cases when there exists a default case is not safe.
    if (defaultCase == null) {
      Node cond = n.getFirstChild(), prev = null, next = null, cur;

      for (cur = cond.getNext(); cur != null; cur = next) {
        next = cur.getNext();
        if (!mayHaveSideEffects(cur.getFirstChild()) &&
            isUselessCase(cur, prev)) {
          removeCase(n, cur);
        } else {
          prev = cur;
        }
      }

      // Optimize switches with constant condition
      if (NodeUtil.isLiteralValue(cond, false)) {
        Node caseLabel;
        TernaryValue caseMatches = TernaryValue.TRUE;
        // Remove cases until you find one that may match
        for (cur = cond.getNext(); cur != null; cur = next) {
          next = cur.getNext();
          caseLabel = cur.getFirstChild();
          caseMatches = PeepholeFoldConstants.evaluateComparison(
              Token.SHEQ, cond, caseLabel);
          if (caseMatches == TernaryValue.TRUE) {
            break;
          } else if (caseMatches == TernaryValue.UNKNOWN) {
            break;
          } else {
            removeCase(n, cur);
          }
        }
        if (caseMatches != TernaryValue.UNKNOWN) {
          Node block, lastStm;
          // Skip cases until you find one whose last stm is a break
          while (cur != null) {
            block = cur.getLastChild();
            lastStm = block.getLastChild();
            cur = cur.getNext();
            if (lastStm != null && lastStm.isBreak()) {
              block.removeChild(lastStm);
              reportCodeChange();
              break;
            }
          }
          // Remove any remaining cases
          for (; cur != null; cur = next) {
            next = cur.getNext();
            removeCase(n, cur);
          }
          // If there is one case left, we may be able to fold it
          cur = cond.getNext();
          if (cur != null && cur.getNext() == null) {
            block = cur.getLastChild();
            if (!(NodeUtil.containsType(block, Token.BREAK,
                NodeUtil.MATCH_NOT_FUNCTION))) {
              cur.removeChild(block);
              n.getParent().replaceChild(n, block);
              reportCodeChange();
              return block;
            }
          }
        }
      }
    }

    // Remove the switch if there are no remaining cases.
    if (n.hasOneChild()) {
      Node condition = n.removeFirstChild();
      Node replacement = IR.exprResult(condition).srcref(n);
      n.getParent().replaceChild(n, replacement);
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
    Preconditions.checkState(n.isSwitch());

    Node lastNonRemovable = n.getFirstChild();  // The switch condition

    // The first child is the switch conditions skip it when looking for cases.
    for (Node c = n.getFirstChild().getNext(); c != null; c = c.getNext()) {
      if (c.isDefaultCase()) {
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
        Preconditions.checkState(c.isCase());
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
      Preconditions.checkState(executingCase.isDefaultCase()
          || executingCase.isCase());
      // We only expect a DEFAULT case if the case we are checking is the
      // DEFAULT case.  Otherwise, we assume the DEFAULT case has already
      // been removed.
      Preconditions.checkState(caseNode == executingCase
          || !executingCase.isDefaultCase());
      Node block = executingCase.getLastChild();
      Preconditions.checkState(block.isBlock());
      if (block.hasChildren()) {
        for (Node blockChild : block.children()) {
          // If this is a block with a labelless break, it is useless.
          switch (blockChild.getType()) {
            case Token.BREAK:
              // A break to a different control structure isn't useless.
              return blockChild.getFirstChild() == null;
            case Token.VAR:
              if (blockChild.hasOneChild()
                  && blockChild.getFirstChild().getFirstChild() == null) {
                // Variable declarations without initializations are OK.
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

    left = trySimplifyUnusedResult(left);
    if (left == null || !mayHaveSideEffects(left)) {
      // Fold it!
      n.removeChild(right);
      parent.replaceChild(n, right);
      reportCodeChange();
      return right;
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
      if (!isUnremovableNode(c) && !mayHaveSideEffects(c)) {
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

  /**
   * Some nodes unremovable node don't have side-effects.
   */
  private boolean isUnremovableNode(Node n) {
    return (n.isBlock() && n.isSyntheticBlock()) || n.isScript();
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
      if (lhsAssign.isName() && condition.isName()
          && lhsAssign.getString().equals(condition.getString())) {
        Node rhsAssign = getSimpleAssignmentValue(n);
        TernaryValue value = NodeUtil.getImpureBooleanValue(rhsAssign);
        if (value != TernaryValue.UNKNOWN) {
          Node replacementConditionNode =
              NodeUtil.booleanNode(value.toBoolean(true));
          condition.getParent().replaceChild(condition,
              replacementConditionNode);
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
        && n.getFirstChild().getFirstChild().isName()) {
      return true;
    } else if (n.isVar() && n.hasOneChild() &&
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
    return n != null && (n.isIf() || isExprConditional(n));
  }

  /**
   * @return Whether the node is a rooted with a HOOK, AND, or OR node.
   */
  private boolean isExprConditional(Node n) {
    if (n.isExprResult()) {
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
    if (n.isIf()) {
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
    Preconditions.checkState(n.isIf());
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
    TernaryValue condValue = NodeUtil.getImpureBooleanValue(cond);
    if (condValue == TernaryValue.UNKNOWN) {
      return n;  // We can't remove branches otherwise!
    }

    if (mayHaveSideEffects(cond)) {
      // Transform "if (a = 2) {x =2}" into "if (true) {a=2;x=2}"
      boolean newConditionValue = condValue == TernaryValue.TRUE;
      // Add an elseBody if it is needed.
      if (!newConditionValue && elseBody == null) {
        elseBody = IR.block().srcref(n);
        n.addChildToBack(elseBody);
      }
      Node newCond = NodeUtil.booleanNode(newConditionValue);
      n.replaceChild(cond, newCond);
      Node branchToKeep = newConditionValue ? thenBody : elseBody;
      branchToKeep.addChildToFront(IR.exprResult(cond).srcref(cond));
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
    Preconditions.checkState(n.isHook());
    Node parent = n.getParent();
    Preconditions.checkNotNull(parent);
    Node cond = n.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();

    TernaryValue condValue = NodeUtil.getImpureBooleanValue(cond);
    if (condValue == TernaryValue.UNKNOWN) {
      // If the result nodes are equivalent, then one of the nodes can be
      // removed and it doesn't matter which.
      if (!areNodesEqualForInlining(thenBody, elseBody)) {
        return n;  // We can't remove branches otherwise!
      }
    }

    // Transform "(a = 2) ? x =2 : y" into "a=2,x=2"
    n.detachChildren();
    Node branchToKeep = condValue.toBoolean(true) ? thenBody : elseBody;
    Node replacement;
    if (mayHaveSideEffects(cond)) {
      replacement = IR.comma(cond, branchToKeep).srcref(n);
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
    Preconditions.checkArgument(n.isWhile());
    Node cond = NodeUtil.getConditionExpression(n);
    if (NodeUtil.getPureBooleanValue(cond) != TernaryValue.FALSE) {
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
    Preconditions.checkArgument(n.isFor());
    // If this is a FOR-IN loop skip it.
    if (NodeUtil.isForIn(n)) {
      return n;
    }

    Node init = n.getFirstChild();
    Node cond = init.getNext();
    Node increment = cond.getNext();

    if (!init.isEmpty() && !init.isVar()) {
      init = trySimplifyUnusedResult(init, false);
    }

    if (!increment.isEmpty()) {
      increment = trySimplifyUnusedResult(increment, false);
    }

    // There is an initializer skip it
    if (!n.getFirstChild().isEmpty()) {
      return n;
    }

    if (NodeUtil.getImpureBooleanValue(cond) != TernaryValue.FALSE) {
      return n;
    }

    NodeUtil.redeclareVarsInsideBranch(n);
    if (!mayHaveSideEffects(cond)) {
      NodeUtil.removeChild(n.getParent(), n);
    } else {
      Node statement = IR.exprResult(cond.detachFromParent())
          .copyInformationFrom(cond);
      n.getParent().replaceChild(n, statement);
    }
    reportCodeChange();
    return null;
  }

  /**
   * Removes DOs that always evaluate to false. This leaves the
   * statements that were in the loop in a BLOCK node.
   * The block will be removed in a later pass, if possible.
   */
  Node tryFoldDo(Node n) {
    Preconditions.checkArgument(n.isDo());

    Node cond = NodeUtil.getConditionExpression(n);
    if (NodeUtil.getImpureBooleanValue(cond) != TernaryValue.FALSE) {
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

    Node parent =  n.getParent();
    parent.replaceChild(n, block);
    if (mayHaveSideEffects(cond)) {
      Node condStatement = IR.exprResult(cond.detachFromParent())
          .srcref(cond);
      parent.addChildAfter(condStatement, block);
    }
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
        NodeUtil.MATCH_NOT_FUNCTION);
  }

  /**
   * Remove always true loop conditions.
   */
  private void tryFoldForCondition(Node forCondition) {
    if (NodeUtil.getPureBooleanValue(forCondition) == TernaryValue.TRUE) {
      forCondition.getParent().replaceChild(forCondition,
          IR.empty());
      reportCodeChange();
    }
  }
}
