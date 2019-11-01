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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Predicate;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;
import java.util.ArrayDeque;
import javax.annotation.Nullable;

/**
 * Peephole optimization to remove useless code such as IF's with false
 * guard conditions, comma operator left hand sides with no side effects, etc.
 */
class PeepholeRemoveDeadCode extends AbstractPeepholeOptimization {

  private static final Predicate<Node> IS_UNNAMED_BREAK_PREDICATE = new Predicate<Node>() {
    @Override
    public boolean apply(Node node) {
      return node.isBreak() && !node.hasChildren();
    }
  };

  private static final Predicate<Node> IS_UNNAMED_CONTINUE_PREDICATE = new Predicate<Node>() {
    @Override
    public boolean apply(Node node) {
      return node.isContinue() && !node.hasChildren();
    }
  };

  private static final Predicate<Node> CAN_CONTAIN_BREAK_PREDICATE = new Predicate<Node>() {
    @Override
    public boolean apply(Node node) {
      return !IR.mayBeExpression(node) // Functions are not visited
          && !NodeUtil.isLoopStructure(node)
          && !node.isSwitch();
    }
  };

  private static final Predicate<Node> CAN_CONTAIN_CONTINUE_PREDICATE = new Predicate<Node>() {
    @Override
    public boolean apply(Node node) {
      return !IR.mayBeExpression(node) // Functions are not visited
          && !NodeUtil.isLoopStructure(node);
    }
  };

  // TODO(dcc): Some (all) of these can probably be better achieved
  // using the control flow graph (like CheckUnreachableCode).
  // There is an existing CFG pass (UnreachableCodeElimination) that
  // could be changed to use code from CheckUnreachableCode to do this.

  @Override
  Node optimizeSubtree(Node subtree) {
    switch (subtree.getToken()) {
      case ASSIGN:
        return tryFoldAssignment(subtree);
      case COMMA:
        return tryFoldComma(subtree);
      case SCRIPT:
      case BLOCK:
        return tryOptimizeBlock(subtree);
      case EXPR_RESULT:
        return tryFoldExpr(subtree);
      case HOOK:
        return tryFoldHook(subtree);
      case SWITCH:
        return tryOptimizeSwitch(subtree);
      case IF:
        return tryFoldIf(subtree);
      case WHILE:
        throw checkNormalization(false, "WHILE");
      case FOR:
        {
          Node condition = NodeUtil.getConditionExpression(subtree);
          if (condition != null) {
            tryFoldForCondition(condition);
          }
          return tryFoldFor(subtree);
        }
      case DO:
        Node foldedDo = tryFoldDoAway(subtree);
        if (foldedDo.isDo()) {
          return tryFoldEmptyDo(foldedDo);
        }
        return foldedDo;

      case TRY:
        return tryFoldTry(subtree);
      case LABEL:
        return tryFoldLabel(subtree);
      case ARRAY_PATTERN:
        return tryOptimizeArrayPattern(subtree);
      case OBJECT_PATTERN:
        return tryOptimizeObjectPattern(subtree);
      case VAR:
      case CONST:
      case LET:
        return tryOptimizeNameDeclaration(subtree);
      case DEFAULT_VALUE:
        return tryRemoveDefaultValue(subtree);
      default:
          return subtree;
    }
  }

  private Node tryRemoveDefaultValue(Node defaultValue) {
    checkArgument(defaultValue.isDefaultValue(), defaultValue);

    Node lValue = defaultValue.getFirstChild();
    Node val = defaultValue.getSecondChild();
    boolean removeVal = false;

    // If the default is `undefined` always remove the value
    if (val.isName() && val.getString().equals("undefined")) {
      removeVal = true;
    }

    // If the `void` application is pure, remove the value
    if (val.isVoid()) {
      Node voidArg = val.getFirstChild();
      removeVal = !mayHaveSideEffects(voidArg);
    }

    if (removeVal) {
      defaultValue.replaceWith(lValue.detach());
      reportChangeToEnclosingScope(lValue);
      return lValue;
    }

    return defaultValue;
  }

  private Node tryFoldLabel(Node n) {
    String labelName = n.getFirstChild().getString();
    Node stmt = n.getLastChild();
    if (stmt.isEmpty() || (stmt.isBlock() && !stmt.hasChildren())) {
      reportChangeToEnclosingScope(n);
      n.detach();
      return null;
    }

    Node child = getOnlyInterestingChild(stmt);
    if (child != null) {
      stmt = child;
    }
    if (stmt.isBreak() && stmt.getFirstChild().getString().equals(labelName)) {
      reportChangeToEnclosingScope(n);
      n.detach();
      return null;
    }
    return n;
  }

  /**
   * Return the only "interesting" child of {@code block}, if it has exactly one interesting child,
   * otherwise return null. For purposes of this method, a node is considered "interesting" unless
   * it is an empty synthetic block.
   */
  @Nullable
  private static Node getOnlyInterestingChild(Node block) {
    if (!block.isBlock()) {
      return null;
    }
    if (block.hasOneChild()) {
      return block.getOnlyChild();
    }

    Node ret = null;
    for (Node child : block.children()) {
      if (child.isSyntheticBlock() && !child.hasChildren()) {
        // Uninteresting child.
      } else if (ret != null) {
        // Found more than one interesting child.
        return null;
      } else {
        ret = child;
      }
    }
    return ret;
  }

  /**
   * Remove try blocks without catch blocks and with empty or not
   * existent finally blocks.
   * Or, only leave the finally blocks if try body blocks are empty
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldTry(Node n) {
    checkState(n.isTry(), n);
    Node body = n.getFirstChild();
    Node catchBlock = body.getNext();
    Node finallyBlock = catchBlock.getNext();

    // Removes TRYs that had its CATCH removed and/or empty FINALLY.
    if (!catchBlock.hasChildren() && (finallyBlock == null || !finallyBlock.hasChildren())) {
      n.removeChild(body);
      n.replaceWith(body);
      reportChangeToEnclosingScope(body);
      return body;
    }

    // Only leave FINALLYs if TRYs are empty
    if (!body.hasChildren()) {
      NodeUtil.redeclareVarsInsideBranch(catchBlock);
      reportChangeToEnclosingScope(n);
      if (finallyBlock != null) {
        n.removeChild(finallyBlock);
        n.replaceWith(finallyBlock);
      } else {
        n.detach();
      }
      return finallyBlock;
    }

    return n;
  }

  /**
   * Try removing identity assignments and empty destructuring pattern assignments
   *
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldAssignment(Node subtree) {
    checkState(subtree.isAssign());
    Node left = subtree.getFirstChild();
    Node right = subtree.getLastChild();
    if (left.isName()
        && right.isName()
        && left.getString().equals(right.getString())) {
      // Only names
      subtree.replaceWith(right.detach());
      reportChangeToEnclosingScope(right);
      return right;
    } else if (left.isDestructuringPattern() && !left.hasChildren()) {
      // `[] = <expr>` becomes `<expr>`
      // Note that this does potentially change behavior. If `<expr>` is not iterable and this
      // code originally threw, it will no longer throw.
      subtree.replaceWith(right.detach());
      reportChangeToEnclosingScope(right);
      return right;
    }
    return subtree;
  }

  /**
   * Try removing identity assignments and empty destructuring pattern assignments
   *
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryOptimizeNameDeclaration(Node subtree) {
    checkState(NodeUtil.isNameDeclaration(subtree));
    Node left = subtree.getFirstChild();
    if (left.isDestructuringLhs() && left.hasTwoChildren()) {
      Node pattern = left.getFirstChild();
      if (!pattern.hasChildren()) {
        // `var [] = foo();` becomes `foo();`
        Node value = left.getSecondChild();
        subtree.replaceWith(IR.exprResult(value.detach()).srcref(value));
        reportChangeToEnclosingScope(value);
      }
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
        subtree.detach();
        subtree = null;
      }
    }
    return subtree;
  }

  /**
   * Replaces {@code expression} with an expression that contains only side-effects of the original.
   *
   * <p>This replacement is made under the assumption that the result of {@code expression} is
   * unused and therefore it is correct to eliminate non-side-effectful nodes.
   *
   * @return The replacement expression, or {@code null} if there were no side-effects to preserve.
   */
  @Nullable
  private Node trySimplifyUnusedResult(Node expression) {
    ArrayDeque<Node> sideEffectRoots = new ArrayDeque<>();
    boolean atFixedPoint = trySimplifyUnusedResultInternal(expression, sideEffectRoots);

    if (atFixedPoint) {
      // `expression` is in a form that cannot be further optimized.
      return expression;
    } else if (sideEffectRoots.isEmpty()) {
      deleteNode(expression);
      return null;
    } else if (sideEffectRoots.peekFirst() == expression) {
      // Expression was a conditional that was transformed. There can't be any other side-effects,
      // but we also can't detach the transformed root.
      checkState(sideEffectRoots.size() == 1, sideEffectRoots);
      reportChangeToEnclosingScope(expression);
      return expression;
    } else {
      Node sideEffects = asDetachedExpression(sideEffectRoots.pollFirst());

      // Assemble a tree of comma expressions for all the side-effects. The tree must execute the
      // side-effects in FIFO order with respect to the queue. It must also be left leaning to match
      // the parser's preferred strucutre.
      while (!sideEffectRoots.isEmpty()) {
        Node next = asDetachedExpression(sideEffectRoots.pollFirst());
        sideEffects = IR.comma(sideEffects, next).srcref(next);
      }

      expression.getParent().addChildBefore(sideEffects, expression);
      deleteNode(expression);
      return sideEffects;
    }
  }

  /**
   * Collects any potentially side-effectful subtrees within {@code tree} into {@code
   * sideEffectRoots}.
   *
   * <p>When a node is determined to have side-effects its descendants are not explored. This method
   * assumes the entire subtree of such a node must be preserved. As a corollary, the contents of
   * {@code sideEffectRoots} are a forest.
   *
   * <p>This operation generally does not mutate {@code tree}; however, exceptions are made for
   * expressions that alter control-flow. Such expression will be pruned of their side-effectless
   * branches. Even in this case, {@code tree} is never detached.
   *
   * @param sideEffectRoots The roots of subtrees determined to have side-effects, in execution
   *     order.
   * @return {@code true} iff there is no code to be removed from within {@code tree}; it is already
   *     at a fixed point for code removal.
   */
  private boolean trySimplifyUnusedResultInternal(Node tree, ArrayDeque<Node> sideEffectRoots) {
    // Special cases for conditional expressions that may be using results.
    switch (tree.getToken()) {
      case HOOK:
        // Try to remove one or more of the conditional children and transform the HOOK to an
        // equivalent operation. Remember that if either value branch still exists, the result of
        // the predicate expression is being used, and so cannot be removed.
        //    x() ? foo() : 1 --> x() && foo()
        //    x() ? 1 : foo() --> x() || foo()
        //    x() ? 1 : 1 --> x()
        //    x ? 1 : 1 --> null

        Node trueNode = trySimplifyUnusedResult(tree.getSecondChild());
        Node falseNode = trySimplifyUnusedResult(tree.getLastChild());
        if (trueNode == null && falseNode != null) {
          checkState(tree.hasTwoChildren(), tree);

          tree.setToken(Token.OR);
          sideEffectRoots.addLast(tree);
          return false; // The node type was changed.
        } else if (trueNode != null && falseNode == null) {
          checkState(tree.hasTwoChildren(), tree);

          tree.setToken(Token.AND);
          sideEffectRoots.addLast(tree);
          return false; // The node type was changed.
        } else if (trueNode == null && falseNode == null) {
          // Don't bother adding true and false branch children to make the AST valid; this HOOK is
          // going to be deleted. We just need to collect any side-effects from the predicate
          // expression.
          trySimplifyUnusedResultInternal(tree.getOnlyChild(), sideEffectRoots);
          return false; // This HOOK must be cleaned up.
        } else {
          sideEffectRoots.addLast(tree);
          return hasFixedPointParent(tree);
        }

      case AND:
      case OR:
        // Try to remove the second operand from a AND or OR operations. Remember that if the second
        // child still exists, the result of the first expression is being used, and so cannot be
        // removed.
        //    x() || f --> x()
        //    x() && f --> x()

        Node conditionalResultNode = trySimplifyUnusedResult(tree.getLastChild());
        if (conditionalResultNode == null) {
          // Don't bother adding a second child to make the AST valid; this op is going to be
          // deleted. We just need to collect any side-effects from the predicate first child.
          trySimplifyUnusedResultInternal(tree.getOnlyChild(), sideEffectRoots);
          return false; // This op must be cleaned up.
        } else {
          sideEffectRoots.addLast(tree);
          return hasFixedPointParent(tree);
        }

      case FUNCTION:
        // Functions that aren't being invoked are dead. If they were invoked we'd see the CALL
        // before arriving here. We don't want to look at any children since they'll never execute.
        return false;

      default:
        // This is the meat of this function. It covers the general case of nodes which are unused
        if (nodeTypeMayHaveSideEffects(tree)) {
          sideEffectRoots.addLast(tree);
          return hasFixedPointParent(tree);
        } else if (!tree.hasChildren()) {
          return false; // A node must have children or side-effects to be at fixed-point.
        }

        boolean atFixedPoint = hasFixedPointParent(tree);
        for (Node child = tree.getFirstChild(); child != null; child = child.getNext()) {
          atFixedPoint &= trySimplifyUnusedResultInternal(child, sideEffectRoots);
        }
        return atFixedPoint;
    }
  }

  /**
   * Returns a expression executing {@code expr} which is legal in any expression context.
   *
   * @param expr An attached expression
   * @return A detached expression
   */
  private static Node asDetachedExpression(Node expr) {
    switch (expr.getToken()) {
      case ITER_SPREAD:
      case OBJECT_SPREAD:
        switch (expr.getParent().getToken()) {
          case ARRAYLIT:
          case NEW:
          case CALL:
            expr = IR.arraylit(expr.detach()).srcref(expr);
            break;
          case OBJECTLIT:
            expr = IR.objectlit(expr.detach()).srcref(expr);
            break;
          default:
            throw new IllegalStateException(expr.toStringTree());
        }
        break;
      default:
        break;
    }

    if (expr.getParent() != null) {
      expr.detach();
    }

    checkState(IR.mayBeExpression(expr), expr);
    return expr;
  }

  /**
   * Returns {@code true} iff {@code expr} is parented such that it is valid in a fixed-point
   * representation of an unused expression tree.
   *
   * <p>A fixed-point representation is one in which no futher nodes should be changed or removed
   * when removing unused code. This method assumes that the expression tree in question is unused,
   * so only side-effects are relevant.
   */
  private static boolean hasFixedPointParent(Node expr) {
    // Most kinds of nodes shouldn't be branches in the fixed-point tree of an unused
    // expression. Those listed below are the only valid kinds.
    switch (expr.getParent().getToken()) {
      case AND:
      case COMMA:
      case HOOK:
      case OR:
        return true;
      case ARRAYLIT:
      case OBJECTLIT:
        // Make a special allowance for SPREADs so they remain in a legal context. Parent types
        // other than ARRAYLIT and OBJECTLIT are not fixed-point because they are the tersest legal
        // parents and are known to be side-effect free.
        return expr.isSpread();
      default:
        // Statments are always fixed-point parents. All other expressions are not.
        return NodeUtil.isStatement(expr.getParent());
    }
  }

  /**
   * A predicate for matching anything except function nodes.
   */
  private static class MatchUnnamedBreak implements Predicate<Node>{
    @Override
    public boolean apply(Node n) {
      return n.isBreak() && !n.hasChildren();
    }
  }

  static final Predicate<Node> MATCH_UNNAMED_BREAK = new MatchUnnamedBreak();

  private void removeIfUnnamedBreak(Node maybeBreak) {
    if (maybeBreak != null && maybeBreak.isBreak() && !maybeBreak.hasChildren()) {
      reportChangeToEnclosingScope(maybeBreak);
      maybeBreak.detach();
    }
  }

  private Node tryRemoveSwitchWithSingleCase(Node n, boolean shouldHoistCondition) {
    Node caseBlock = n.getLastChild().getLastChild();
    removeIfUnnamedBreak(caseBlock.getLastChild());
    // Back off if the switch contains statements like "if (a) { break; }"
    if (NodeUtil.has(caseBlock, MATCH_UNNAMED_BREAK, NodeUtil.MATCH_NOT_FUNCTION)) {
      return n;
    }
    if (shouldHoistCondition) {
      Node switchBlock = caseBlock.getGrandparent();
      switchBlock.getParent().addChildAfter(
          IR.exprResult(n.removeFirstChild()).srcref(n), switchBlock.getPrevious());
    }
    n.replaceWith(caseBlock.detach());
    reportChangeToEnclosingScope(caseBlock);
    return caseBlock;
  }

  private Node tryRemoveSwitch(Node n) {
    if (n.hasOneChild()) {
      // Remove the switch if there are no remaining cases
      Node condition = n.removeFirstChild();
      Node replacement = IR.exprResult(condition).srcref(n);
      n.replaceWith(replacement);
      reportChangeToEnclosingScope(replacement);
      return replacement;
    } else if (n.hasTwoChildren() && n.getLastChild().isDefaultCase()) {
      if (n.getFirstChild().isCall()) {
        return tryRemoveSwitchWithSingleCase(n, true);
      } else {
        return tryRemoveSwitchWithSingleCase(n, false);
      }
    } else {
      return n;
    }
  }

  /**
   * Remove useless switches and cases.
   */
  private Node tryOptimizeSwitch(Node n) {
    checkState(n.isSwitch(), n);

    Node defaultCase = tryOptimizeDefaultCase(n);

    // Generally, it is unsafe to remove other cases when the default case is not the last one.
    if (defaultCase == null || n.getLastChild().isDefaultCase()) {
      Node cond = n.getFirstChild();
      Node prev = null;
      Node next = null;
      Node cur;

      for (cur = cond.getNext(); cur != null; cur = next) {
        next = cur.getNext();
        if (!mayHaveSideEffects(cur.getFirstChild()) && isUselessCase(cur, prev, defaultCase)) {
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
          caseMatches = PeepholeFoldConstants.evaluateComparison(this, Token.SHEQ, cond, caseLabel);
          if (caseMatches == TernaryValue.TRUE) {
            break;
          } else if (caseMatches == TernaryValue.UNKNOWN) {
            break;
          } else {
            removeCase(n, cur);
          }
        }
        if (cur != null && caseMatches == TernaryValue.TRUE) {
          // Skip cases until you find one whose last stm is a removable break
          Node matchingCase = cur;
          Node matchingCaseBlock = matchingCase.getLastChild();
          while (cur != null) {
            Node block = cur.getLastChild();
            Node lastStm = block.getLastChild();
            boolean isLastStmRemovableBreak = false;
            if (lastStm != null && isExit(lastStm)) {
              removeIfUnnamedBreak(lastStm);
              isLastStmRemovableBreak = true;
            }
            next = cur.getNext();
            // Remove the fallthrough case labels
            if (cur != matchingCase) {
              while (block.hasChildren()) {
                matchingCaseBlock.addChildToBack(block.getFirstChild().detach());
              }
              reportChangeToEnclosingScope(cur);
              cur.detach();
            }
            cur = next;
            if (isLastStmRemovableBreak) {
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
            return tryRemoveSwitchWithSingleCase(n, false);
          }
        }
      }
    }

    return tryRemoveSwitch(n);
  }

  /**
   * @return the default case node or null if there is no default case or
   *     if the default case is removed.
   */
  private Node tryOptimizeDefaultCase(Node n) {
    checkState(n.isSwitch(), n);

    Node lastNonRemovable = n.getFirstChild();  // The switch condition

    // The first child is the switch conditions skip it when looking for cases.
    for (Node c = n.getSecondChild(); c != null; c = c.getNext()) {
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
        if (isUselessCase(c, prevCase, c)) {
          removeCase(n, c);
          return null;
        }
        return c;
      } else {
        checkState(c.isCase());
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
    reportChangeToEnclosingScope(switchNode);
  }

  /**
   * The function assumes that when checking a CASE node there is no DEFAULT_CASE node in the
   * SWITCH, or the DEFAULT_CASE is the last case in the SWITCH.
   *
   * @return Whether the CASE or DEFAULT_CASE block does anything useful.
   */
  private boolean isUselessCase(
      Node caseNode, @Nullable Node previousCase, @Nullable Node defaultCase) {
    checkState(previousCase == null || previousCase.getNext() == caseNode);
    // A case isn't useless if a previous case falls through to it unless it happens to be the last
    // case in the switch.
    Node switchNode = caseNode.getParent();
    if (switchNode.getLastChild() != caseNode && previousCase != null) {
      Node previousBlock = previousCase.getLastChild();
      if (!previousBlock.hasChildren()
          || !isExit(previousBlock.getLastChild())) {
        return false;
      }
    }

    Node executingCase = caseNode;
    while (executingCase != null) {
      checkState(executingCase.isDefaultCase() || executingCase.isCase());
      // We only expect a DEFAULT case if the case we are checking is the
      // DEFAULT case.  Otherwise, we assume the DEFAULT case has already
      // been removed.
      checkState(caseNode == executingCase || !executingCase.isDefaultCase());
      if (!executingCase.isDefaultCase() && mayHaveSideEffects(executingCase.getFirstChild())) {
        // The case falls thru to a case whose condition has a potential side-effect,
        // removing the candidate case would skip that side-effect, so don't.
        return false;
      }
      Node block = executingCase.getLastChild();
      checkState(block.isBlock());
      if (block.hasChildren()) {
        for (Node blockChild : block.children()) {
          // If this is a block with a labelless break, it is useless.
          switch (blockChild.getToken()) {
            case BREAK:
              // A case with a single labelless break is useless if it is the default case or if
              // there is no default case. A break to a different control structure isn't useless.
              return !blockChild.hasChildren()
                  && (defaultCase == null || defaultCase == executingCase);
            case VAR:
              if (blockChild.hasOneChild()
                  && blockChild.getFirstFirstChild() == null) {
                // Variable declarations without initializations are OK.
                continue;
              }
              return false;
            default:
              return false;
          }
        }
      }
      // Look at the fallthrough case
      executingCase = executingCase.getNext();
    }
    return true;
  }

  /**
   * @return Whether the node is an obvious control flow exit.
   */
  private static boolean isExit(Node n) {
    switch (n.getToken()) {
      case BREAK:
      case CONTINUE:
      case RETURN:
      case THROW:
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
      reportChangeToEnclosingScope(parent);
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
        checkNormalization(!NodeUtil.isFunctionDeclaration(n), "function declaration");
        // TODO(johnlenz): determine what this is actually removing. Candidates
        //    include: EMPTY nodes, control structures without children
        //    (removing infinite loops), empty try blocks.  What else?
        n.removeChild(c);
        reportChangeToEnclosingScope(n);
        markFunctionsDeleted(c);
      } else {
        tryOptimizeConditionalAfterAssign(c);
      }
      c = next;
    }

    if (n.isSyntheticBlock() || n.isScript() || n.getParent() == null) {
      return n;
    }

    // Try to remove the block.
    Node parent = n.getParent();
    if (NodeUtil.tryMergeBlock(n, false)) {
      reportChangeToEnclosingScope(parent);
      return null;
    }

    return n;
  }

  /**
   * Some nodes that are unremovable don't have side effects so they aren't caught by
   * mayHaveSideEffects
   */
  private static boolean isUnremovableNode(Node n) {
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
        TernaryValue value = NodeUtil.getBooleanValue(rhsAssign);
        if (value != TernaryValue.UNKNOWN) {
          Node replacementConditionNode =
              NodeUtil.booleanNode(value.toBoolean(true));
          condition.replaceWith(replacementConditionNode);
          reportChangeToEnclosingScope(replacementConditionNode);
        }
      }
    }
  }

  /**
   * @return whether the node is a assignment to a simple name, or simple var
   * declaration with initialization.
   */
  private static boolean isSimpleAssignment(Node n) {
    // For our purposes we define a simple assignment to be a assignment
    // to a NAME node, or a VAR declaration with one child and a initializer.
    if (NodeUtil.isExprAssign(n)
        && n.getFirstFirstChild().isName()) {
      return true;
    } else if (NodeUtil.isNameDeclaration(n) && n.hasOneChild() && n.getFirstFirstChild() != null) {
      return true;
    }

    return false;
  }

  /**
   * @return The name being assigned to.
   */
  private Node getSimpleAssignmentName(Node n) {
    checkState(isSimpleAssignment(n));
    if (NodeUtil.isExprAssign(n)) {
      return n.getFirstFirstChild();
    } else {
      // A var declaration.
      return n.getFirstChild();
    }
  }

  /**
   * @return The value assigned in the simple assignment
   */
  private Node getSimpleAssignmentValue(Node n) {
    checkState(isSimpleAssignment(n));
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
  private static boolean isExprConditional(Node n) {
    if (n.isExprResult()) {
      switch (n.getFirstChild().getToken()) {
        case HOOK:
        case AND:
        case OR:
          return true;
        default:
          break;
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
      checkState(isExprConditional(n));
      return n.getFirstFirstChild();
    }
  }

  /**
   * Try folding IF nodes by removing dead branches.
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldIf(Node n) {
    checkState(n.isIf(), n);
    Node parent = n.getParent();
    checkNotNull(parent);
    Token type = n.getToken();
    Node cond = n.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();

    // if (x) { .. } else { } --> if (x) { ... }
    if (elseBody != null && !mayHaveSideEffects(elseBody)) {
      n.removeChild(elseBody);
      reportChangeToEnclosingScope(n);
      elseBody = null;
    }

    // if (x) { } else { ... } --> if (!x) { ... }
    if (!mayHaveSideEffects(thenBody) && elseBody != null) {
      n.removeChild(elseBody);
      n.replaceChild(thenBody, elseBody);
      Node notCond = new Node(Token.NOT);
      n.replaceChild(cond, notCond);
      reportChangeToEnclosingScope(n);
      notCond.addChildToFront(cond);
      cond = notCond;
      thenBody = cond.getNext();
      elseBody = null;
    }

    // if (x()) { }
    if (!mayHaveSideEffects(thenBody) && elseBody == null) {
      if (mayHaveSideEffects(cond)) {
        // x() has side effects, just leave the condition on its own.
        n.removeChild(cond);
        Node replacement = NodeUtil.newExpr(cond);
        parent.replaceChild(n, replacement);
        reportChangeToEnclosingScope(parent);
        return replacement;
      } else {
        // x() has no side effects, the whole tree is useless now.
        NodeUtil.removeChild(parent, n);
        reportChangeToEnclosingScope(parent);
        return null;
      }
    }

    // Try transforms that apply to both IF and HOOK.
    TernaryValue condValue = NodeUtil.getBooleanValue(cond);
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
      reportChangeToEnclosingScope(branchToKeep);
      cond = newCond;
    }

    boolean condTrue = condValue.toBoolean(true);
    if (n.hasTwoChildren()) {
      checkState(type == Token.IF);

      if (condTrue) {
        // Replace "if (true) { X }" with "X".
        Node thenStmt = n.getSecondChild();
        n.removeChild(thenStmt);
        parent.replaceChild(n, thenStmt);
        reportChangeToEnclosingScope(thenStmt);
        return thenStmt;
      } else {
        // Remove "if (false) { X }" completely.
        NodeUtil.redeclareVarsInsideBranch(n);
        NodeUtil.removeChild(parent, n);
        reportChangeToEnclosingScope(parent);
        markFunctionsDeleted(n);
        return null;
      }
    } else {
      // Replace "if (true) { X } else { Y }" with X, or
      // replace "if (false) { X } else { Y }" with Y.
      Node trueBranch = n.getSecondChild();
      Node falseBranch = trueBranch.getNext();
      Node branchToKeep = condTrue ? trueBranch : falseBranch;
      Node branchToRemove = condTrue ? falseBranch : trueBranch;
      NodeUtil.redeclareVarsInsideBranch(branchToRemove);
      n.removeChild(branchToKeep);
      parent.replaceChild(n, branchToKeep);
      reportChangeToEnclosingScope(branchToKeep);
      markFunctionsDeleted(n);
      return branchToKeep;
    }
  }

  /**
   * Try folding HOOK (?:) if the condition results of the condition is known.
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldHook(Node n) {
    checkState(n.isHook(), n);
    Node parent = n.getParent();
    checkNotNull(parent);
    Node cond = n.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();

    TernaryValue condValue = NodeUtil.getBooleanValue(cond);
    if (condValue == TernaryValue.UNKNOWN) {
      // If the result nodes are equivalent, then one of the nodes can be
      // removed and it doesn't matter which.
      if (!areNodesEqualForInlining(thenBody, elseBody)) {
        return n;  // We can't remove branches otherwise!
      }
    }

    // Transform "(a = 2) ? x =2 : y" into "a=2,x=2"
    Node branchToKeep;
    Node branchToRemove;
    if (condValue.toBoolean(true)) {
      branchToKeep = thenBody;
      branchToRemove = elseBody;
    } else {
      branchToKeep = elseBody;
      branchToRemove = thenBody;
    }

    Node replacement;
    boolean condHasSideEffects = mayHaveSideEffects(cond);
    // Must detach after checking for side effects, to ensure that the parents
    // of nodes are set correctly.
    n.detachChildren();
    if (condHasSideEffects) {
      replacement = IR.comma(cond, branchToKeep).srcref(n);
    } else {
      replacement = branchToKeep;
      markFunctionsDeleted(cond);
    }

    parent.replaceChild(n, replacement);
    reportChangeToEnclosingScope(replacement);
    markFunctionsDeleted(branchToRemove);
    return replacement;
  }

  /**
   * Removes FORs that always evaluate to false.
   */
  Node tryFoldFor(Node n) {
    checkArgument(n.isVanillaFor());

    Node init = n.getFirstChild();
    Node cond = init.getNext();
    Node increment = cond.getNext();

    if (!init.isEmpty() && !NodeUtil.isNameDeclaration(init)) {
      init = trySimplifyUnusedResult(init);
      if (init == null) {
        init = IR.empty().srcref(n);
        n.addChildToFront(init);
      }
    }

    if (!increment.isEmpty()) {
      increment = trySimplifyUnusedResult(increment);
      if (increment == null) {
        increment = IR.empty().srcref(n);
        n.addChildAfter(increment, cond);
      }
    }

    // There is an initializer skip it
    if (!n.getFirstChild().isEmpty()) {
      return n;
    }

    if (NodeUtil.getBooleanValue(cond) != TernaryValue.FALSE) {
      return n;
    }

    Node parent = n.getParent();
    NodeUtil.redeclareVarsInsideBranch(n);
    if (!mayHaveSideEffects(cond)) {
      NodeUtil.removeChild(parent, n);
    } else {
      Node statement = IR.exprResult(cond.detach())
          .useSourceInfoIfMissingFrom(cond);
      if (parent.isLabel()) {
        Node block = IR.block();
        block.useSourceInfoIfMissingFrom(statement);
        block.addChildToFront(statement);
        statement = block;
      }
      parent.replaceChild(n, statement);
    }
    reportChangeToEnclosingScope(parent);
    return null;
  }

  /**
   * Removes DOs that always evaluate to false. This leaves the
   * statements that were in the loop in a BLOCK node.
   * The block will be removed in a later pass, if possible.
   */
  Node tryFoldDoAway(Node n) {
    checkArgument(n.isDo());

    Node cond = NodeUtil.getConditionExpression(n);
    if (NodeUtil.getBooleanValue(cond) != TernaryValue.FALSE) {
      return n;
    }

    Node block = NodeUtil.getLoopCodeBlock(n);
    if (n.getParent().isLabel() || hasUnnamedBreakOrContinue(block)) {
      return n;
    }

    Node parent = n.getParent();
    n.replaceWith(block.detach());
    if (mayHaveSideEffects(cond)) {
      Node condStatement = IR.exprResult(cond.detach()).srcref(cond);
      parent.addChildAfter(condStatement, block);
    }
    reportChangeToEnclosingScope(parent);

    return block;
  }

  /**
   * Removes DOs that have empty bodies into FORs, which are
   * much easier for the CFA to analyze.
   */
  Node tryFoldEmptyDo(Node n) {
    checkArgument(n.isDo());

    Node body = NodeUtil.getLoopCodeBlock(n);
    if (body.isBlock() && !body.hasChildren()) {
      Node cond = NodeUtil.getConditionExpression(n);
      Node forNode =
          IR.forNode(IR.empty().srcref(n),
                     cond.detach(),
                     IR.empty().srcref(n),
                     body.detach());
      n.replaceWith(forNode);
      reportChangeToEnclosingScope(forNode);
      return forNode;
    }
    return n;
  }

  /** Removes string keys with an empty pattern as their child */
  Node tryOptimizeObjectPattern(Node pattern) {
    checkArgument(pattern.isObjectPattern(), pattern);

    if (pattern.hasChildren() && pattern.getLastChild().isRest()) {
      // don't remove any elements in `const {f: [], ...rest} = obj` because that affects what's
      // assigned to `rest`. only the last element can be object rest.
      return pattern;
    }

    // remove trailing EMPTY nodes and empty destructuring patterns
    for (Node child = pattern.getFirstChild(); child != null; ) {
      Node key = child;
      child = key.getNext(); // don't put this in the for loop since we might remove `child`

      if (!key.isStringKey()) {
        // don't try to remove rest or computed properties, since they might have side effects
        continue;
      }
      if (isRemovableDestructuringTarget(key.getOnlyChild())) {
        // e.g. `const {f: {}} = obj;`
        key.detach();
        reportChangeToEnclosingScope(pattern);
      }
    }
    return pattern;
  }

  /** Removes trailing EMPTY nodes and empty array patterns */
  Node tryOptimizeArrayPattern(Node pattern) {
    checkArgument(pattern.isArrayPattern(), pattern);

    for (Node lastChild = pattern.getLastChild(); lastChild != null; ) {
      if (lastChild.isEmpty() || isRemovableDestructuringTarget(lastChild)) {
        Node prev = lastChild.getPrevious();
        pattern.removeChild(lastChild);
        lastChild = prev;
        reportChangeToEnclosingScope(pattern);
      } else {
        // don't remove any non-trailing empty nodes because that will change the ordering of the
        // other assignments
        // note that this case also covers array pattern rest, which must be the final element
        break;
      }
    }
    return pattern;
  }

  private boolean isRemovableDestructuringTarget(Node destructruringElement) {
    Node target = destructruringElement;
    Node defaultValue = null;
    if (destructruringElement.isDefaultValue()) {
      target = destructruringElement.getFirstChild();
      defaultValue = destructruringElement.getSecondChild();
    }
    if (!target.isDestructuringPattern() || target.hasChildren()) {
      return false;
    }
    // only remove default values without side effects
    return defaultValue == null || !mayHaveSideEffects(defaultValue);
  }

  /**
   * Returns whether a node has any unhandled breaks or continue.
   */
  static boolean hasUnnamedBreakOrContinue(Node n) {
    return NodeUtil.has(n, IS_UNNAMED_BREAK_PREDICATE, CAN_CONTAIN_BREAK_PREDICATE)
        || NodeUtil.has(n, IS_UNNAMED_CONTINUE_PREDICATE, CAN_CONTAIN_CONTINUE_PREDICATE);
  }

  /**
   * Remove always true loop conditions.
   */
  private void tryFoldForCondition(Node forCondition) {
    if (getSideEffectFreeBooleanValue(forCondition) == TernaryValue.TRUE) {
      reportChangeToEnclosingScope(forCondition);
      forCondition.replaceWith(IR.empty());
    }
  }

  private static IllegalStateException checkNormalization(boolean condition, String feature) {
    checkState(condition, "Unexpected %s. AST should be normalized.", feature);
    return null;
  }
}
