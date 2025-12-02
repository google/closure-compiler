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
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Peephole optimization to remove useless code such as IF's with false guard conditions, comma
 * operator's left hand sides with no side effects, etc.
 */
class PeepholeRemoveDeadCode extends AbstractPeepholeOptimization {

  @Override
  Node optimizeSubtree(Node subtree) {
    switch (subtree.getToken()) {
      case ASSIGN -> {
        return tryFoldAssignment(subtree);
      }
      case COMMA -> {
        return tryFoldComma(subtree);
      }
      case SCRIPT, BLOCK -> {
        return tryOptimizeBlock(subtree);
      }
      case EXPR_RESULT -> {
        return tryFoldExpr(subtree);
      }
      case HOOK -> {
        return tryFoldHook(subtree);
      }
      case SWITCH -> {
        return tryOptimizeSwitch(subtree);
      }
      case IF -> {
        return tryFoldIf(subtree);
      }
      case WHILE -> {
        // This pass gets run both before and after denormalize. Hence, the AST could potentially
        // contain WHILE (denormalized).
        // TODO: Ideally, we should optimize this case instead of returning
        return subtree;
      }
      case FOR -> {
        Node condition = NodeUtil.getConditionExpression(subtree);
        if (condition != null) {
          tryFoldForCondition(condition);
        }
        return tryFoldFor(subtree);
      }
      case DO -> {
        Node foldedDo = tryFoldDoAway(subtree);
        if (foldedDo.isDo()) {
          return tryFoldEmptyDo(foldedDo);
        }
        return foldedDo;
      }
      case TRY -> {
        return tryFoldTry(subtree);
      }
      case LABEL -> {
        return tryFoldLabel(subtree);
      }
      case ARRAY_PATTERN -> {
        return tryOptimizeArrayPattern(subtree);
      }
      case OBJECT_PATTERN -> {
        return tryOptimizeObjectPattern(subtree);
      }
      case VAR, CONST, LET -> {
        return tryOptimizeNameDeclaration(subtree);
      }
      case DEFAULT_VALUE -> {
        return tryRemoveDefaultValue(subtree);
      }
      case OPTCHAIN_GETPROP, OPTCHAIN_CALL, OPTCHAIN_GETELEM -> {
        return tryRemoveOptionalChaining(subtree);
      }
      default -> {
        return subtree;
      }
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

  private @Nullable Node tryFoldLabel(Node n) {
    String labelName = n.getFirstChild().getString();
    Node stmt = n.getLastChild();
    if (stmt.isEmpty()) {
      reportChangeToEnclosingScope(n);
      n.detach();
      return null;
    }

    if (stmt.isBlock() && !stmt.hasChildren()) {
      reportChangeToEnclosingScope(n);
      if (n.getParent().isLabel()) {
        // If the parent is itself a label, replace this label
        // with its contained block to keep the AST in a valid state.
        n.replaceWith(stmt.detach());
      } else {
        n.detach();
      }
      return null;
    }

    Node child = getOnlyInterestingChild(stmt);
    if (child != null) {
      stmt = child;
    }
    if (stmt.isBreak() && stmt.getFirstChild().getString().equals(labelName)) {
      if (n.getParent().isLabel()) {
        Node replacement = IR.block().srcref(n);
        n.replaceWith(replacement);
        reportChangeToEnclosingScope(replacement);
        return replacement;
      } else {
        Node parent = n.getParent();
        n.detach();
        reportChangeToEnclosingScope(parent);
        return null;
      }
    }
    return n;
  }

  /**
   * Return the only "interesting" child of {@code block}, if it has exactly one interesting child,
   * otherwise return null. For purposes of this method, a node is considered "interesting" unless
   * it is an empty synthetic block.
   */
  private static @Nullable Node getOnlyInterestingChild(Node block) {
    if (!block.isBlock()) {
      return null;
    }
    if (block.hasOneChild()) {
      return block.getOnlyChild();
    }

    Node ret = null;
    for (Node child = block.getFirstChild(); child != null; child = child.getNext()) {
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
   * Remove try blocks without catch blocks and with empty or not existent finally blocks. Or, only
   * leave the finally blocks if try body blocks are empty
   *
   * @return the replacement node, if changed, or the original if not
   */
  private @Nullable Node tryFoldTry(Node n) {
    checkState(n.isTry(), n);
    Node body = n.getFirstChild();
    Node catchBlock = body.getNext();
    Node finallyBlock = catchBlock.getNext();

    // Removes TRYs that had its CATCH removed and/or empty FINALLY.
    if (!catchBlock.hasChildren() && (finallyBlock == null || !finallyBlock.hasChildren())) {
      checkState(!n.getParent().isLabel());
      body.detach();
      n.replaceWith(body);
      reportChangeToEnclosingScope(body);
      return body;
    }

    // Only leave FINALLYs if TRYs are not empty
    if (!body.hasChildren()) {
      NodeUtil.redeclareVarsInsideBranch(catchBlock);
      reportChangeToEnclosingScope(n);
      if (finallyBlock != null) {
        finallyBlock.detach();
        checkState(!n.getParent().isLabel());
        n.replaceWith(finallyBlock);
        return finallyBlock;
      } else {
        checkState(!n.getParent().isLabel());
        n.detach();
        return null;
      }
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
    if (left.isName() && right.isName() && left.getString().equals(right.getString())) {
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
   *
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldExpr(Node subtree) {
    Node result = trySimplifyUnusedResult(subtree.getFirstChild());
    if (result == null) {
      Node parent = subtree.getParent();
      // If the EXPR_RESULT no longer has any children, remove it as well.
      if (parent.isLabel()) {
        Node replacement = IR.block().srcref(subtree);
        subtree.replaceWith(replacement);
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
  private @Nullable Node trySimplifyUnusedResult(Node expression) {
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
      // the parser's preferred structure.
      while (!sideEffectRoots.isEmpty()) {
        Node next = asDetachedExpression(sideEffectRoots.pollFirst());
        sideEffects = IR.comma(sideEffects, next).srcref(next);
      }

      sideEffects.insertBefore(expression);
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
      case HOOK -> {
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
      }
      case AND, OR, COALESCE -> {
        // Try to remove the second operand from a AND, OR, and COALESCE operations. Remember that
        // if the second
        // child still exists, the result of the first expression is being used, and so cannot be
        // removed.
        //    x() ?? f --> x()
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
      }
      case FUNCTION -> {
        // Functions that aren't being invoked are dead. If they were invoked we'd see the CALL
        // before arriving here. We don't want to look at any children since they'll never execute.
        return false;
      }
      default -> {
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
  }

  /**
   * Returns an expression executing {@code expr} which is legal in any expression context.
   *
   * @param expr An attached expression
   * @return A detached expression
   */
  private static Node asDetachedExpression(Node expr) {
    switch (expr.getToken()) {
      case ITER_SPREAD, OBJECT_SPREAD -> {
        switch (expr.getParent().getToken()) {
          case ARRAYLIT:
          case NEW:
          case CALL: // `Math.sin(...c)`
          case OPTCHAIN_CALL: // `Math?.sin(...c)`
            expr = IR.arraylit(expr.detach()).srcref(expr);
            break;
          case OBJECTLIT:
            expr = IR.objectlit(expr.detach()).srcref(expr);
            break;
          default:
            throw new IllegalStateException(expr.toStringTree());
        }
      }
      default -> {}
    }

    if (expr.hasParent()) {
      expr.detach();
    }

    checkState(IR.mayBeExpression(expr), expr);
    return expr;
  }

  /**
   * Returns {@code true} iff {@code expr} is parented such that it is valid in a fixed-point
   * representation of an unused expression tree.
   *
   * <p>A fixed-point representation is one in which no further nodes should be changed or removed
   * when removing unused code. This method assumes that the expression tree in question is unused,
   * so only side-effects are relevant.
   */
  private static boolean hasFixedPointParent(Node expr) {
    // Most kinds of nodes shouldn't be branches in the fixed-point tree of an unused
    // expression. Those listed below are the only valid kinds.
    return switch (expr.getParent().getToken()) {
      case AND, COMMA, HOOK, OR, COALESCE -> true;
      case ARRAYLIT, OBJECTLIT ->
          // Make a special allowance for SPREADs so they remain in a legal context. Parent types
          // other than ARRAYLIT and OBJECTLIT are not fixed-point because they are the tersest
          // legal
          // parents and are known to be side-effect free.
          expr.isSpread();
      default ->
          // Statments are always fixed-point parents. All other expressions are not.
          NodeUtil.isStatement(expr.getParent());
    };
  }

  /** A predicate for matching anything except function nodes. */
  private static class MatchUnnamedBreak implements Predicate<Node> {
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

  private Node tryRemoveSwitchWithSingleCase(Node switchNode, boolean shouldHoistCondition) {
    Node switchBody = switchNode.getSecondChild();
    Node caseBlock = switchBody.getOnlyChild().getLastChild();
    removeIfUnnamedBreak(caseBlock.getLastChild());
    // Back off if the switch contains statements like "if (a) { break; }"
    if (NodeUtil.has(caseBlock, MATCH_UNNAMED_BREAK, NodeUtil.MATCH_NOT_FUNCTION)) {
      return switchNode;
    }
    if (shouldHoistCondition) {
      IR.exprResult(switchNode.removeFirstChild()).srcref(switchNode).insertBefore(switchNode);
    }
    switchNode.replaceWith(caseBlock.detach());
    reportChangeToEnclosingScope(caseBlock);
    return caseBlock;
  }

  private Node tryRemoveSwitch(Node n) {
    Node switchBody = n.getSecondChild();
    if (!switchBody.hasChildren()) {
      // Remove the switch if there are no remaining cases
      Node condition = n.removeFirstChild();
      Node replacement = IR.exprResult(condition).srcref(n);
      n.replaceWith(replacement);
      reportChangeToEnclosingScope(replacement);
      return replacement;
    } else if (switchBody.hasOneChild() && switchBody.getOnlyChild().isDefaultCase()) {
      if (n.getFirstChild().isCall() || n.getFirstChild().isOptChainCall()) {
        // Before removing switch, we must preserve the switch condition if it is a call
        return tryRemoveSwitchWithSingleCase(n, true);
      } else {
        return tryRemoveSwitchWithSingleCase(n, false);
      }
    } else {
      return n;
    }
  }

  /** Remove useless switches and cases. */
  private Node tryOptimizeSwitch(Node n) {
    checkState(n.isSwitch(), n);

    Node switchBody = n.getSecondChild();
    Node defaultCase = tryOptimizeDefaultCase(n);

    // Generally, it is unsafe to remove other cases when the default case is not the last one.
    if ((defaultCase == null || switchBody.getLastChild().isDefaultCase())
        && areAllCaseTagsLiterals(switchBody)) {
      Node cond = n.getFirstChild();
      Node prev = null;
      Node next = null;
      Node cur;

      // First, remove empty cases where possible: always empty default cases; or when there is
      // no default case, other empty cases that are not the first matching case, may be
      // removable as well.
      boolean foundMatchingCase = false;
      for (cur = switchBody.getFirstChild(); cur != null; cur = next) {
        next = cur.getNext();
        Node firstChild = cur.getFirstChild();
        foundMatchingCase = isFirstSwitchMatch(foundMatchingCase, cond, firstChild);
        if (!foundMatchingCase
            && !mayHaveSideEffects(firstChild)
            && isUselessCase(cur, prev, defaultCase)) {
          removeCase(n, cur);
        } else {
          prev = cur;
        }
      }

      // Next, optimize switches with constant condition
      if (NodeUtil.isLiteralValue(cond, false)) {
        Node caseLabel;
        Tri caseMatches = Tri.TRUE;
        // Remove cases until you find one that may match
        for (cur = switchBody.getFirstChild(); cur != null; cur = next) {
          next = cur.getNext();
          caseLabel = cur.getFirstChild();
          caseMatches = PeepholeFoldConstants.evaluateComparison(this, Token.SHEQ, cond, caseLabel);
          if (caseMatches == Tri.TRUE) {
            break;
          } else if (caseMatches == Tri.UNKNOWN) {
            break;
          } else {
            removeCase(n, cur);
          }
        }
        if (cur != null && caseMatches == Tri.TRUE) {
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
                matchingCaseBlock.addChildToBack(block.removeFirstChild());
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

    // Last, try to remove the entire switch if possible
    return tryRemoveSwitch(n);
  }

  /**
   * @return the default case node or null if there is no default case or if the default case is
   *     removed.
   */
  private @Nullable Node tryOptimizeDefaultCase(Node n) {
    checkState(n.isSwitch(), n);

    Node switchBody = n.getSecondChild();
    Node lastNonRemovable = null; // the most recently iterated case known to not be removable.

    for (Node c = switchBody.getFirstChild(); c != null; c = c.getNext()) {
      if (c.isDefaultCase()) {
        // Remove any cases that fall-through to the default case
        Node caseToRemove =
            lastNonRemovable != null ? lastNonRemovable.getNext() : switchBody.getFirstChild();
        for (Node next; caseToRemove != c; caseToRemove = next) {
          next = caseToRemove.getNext();
          removeCase(n, caseToRemove);
        }

        // Remove the default case if we can
        if (isUselessCase(c, lastNonRemovable, c)) {
          removeCase(n, c);
          return null;
        }
        return c;
      } else {
        checkState(c.isCase());
        if (c.getLastChild().hasChildren() || mayHaveSideEffects(c.getFirstChild())) {
          lastNonRemovable = c;
        }
      }
    }
    return null;
  }

  /**
   * Remove the case from the switch redeclaring any variables declared in it.
   *
   * @param caseNode The case to remove.
   */
  private void removeCase(Node switchNode, Node caseNode) {
    NodeUtil.redeclareVarsInsideBranch(caseNode);
    caseNode.detach();
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
    Node switchBody = caseNode.getParent();
    if (switchBody.getLastChild() != caseNode && previousCase != null) {
      Node previousBlock = previousCase.getLastChild();
      if (!previousBlock.hasChildren() || !isExit(previousBlock.getLastChild())) {
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
        for (Node blockChild = block.getFirstChild();
            blockChild != null;
            blockChild = blockChild.getNext()) {
          // If this is a block with a labelless break, it is useless.
          switch (blockChild.getToken()) {
            case BREAK -> {
              // A case with a single labelless break is useless if it is the default case or if
              // there is no default case. A break to a different control structure isn't useless.
              return !blockChild.hasChildren()
                  && (defaultCase == null || defaultCase == executingCase);
            }
            case VAR -> {
              if (blockChild.hasOneChild() && blockChild.getFirstFirstChild() == null) {
                // Variable declarations without initializations are OK.
                continue;
              }
              return false;
            }
            default -> {
              return false;
            }
          }
        }
      }
      // Look at the fallthrough case
      executingCase = executingCase.getNext();
    }
    return true;
  }

  private boolean isFirstSwitchMatch(boolean foundMatchingCase, Node condition, Node tag) {
    if (foundMatchingCase) {
      return false;
    }
    return PeepholeFoldConstants.evaluateComparison(this, Token.SHEQ, condition, tag) == Tri.TRUE;
  }

  private boolean areAllCaseTagsLiterals(Node switchBody) {
    for (Node caseNode = switchBody.getFirstChild();
        caseNode != null;
        caseNode = caseNode.getNext()) {
      if (caseNode.isDefaultCase()) {
        continue;
      }
      Node caseTag = caseNode.getFirstChild();
      if (!NodeUtil.isLiteralValue(caseTag, false)) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return Whether the node is a control flow exit from the current block.
   */
  private static boolean isExit(Node n) {
    return switch (n.getToken()) {
      case BREAK, CONTINUE, RETURN, THROW -> true;
      case SWITCH -> isSwitchExit(n);
      case TRY -> isTryExit(n);
      default -> false;
    };
  }

  /**
   * @return Whether the block is a control flow exit from the block containing current switch or
   *     try..catch statement.
   */
  private static boolean isUnconditionalBlockExit(Node n) {
    checkState(n.isBlock(), n);
    checkState(!n.getParent().isLabel(), n);

    // Last statement must lead out of the block.
    Node lastStm = n.getLastChild();
    if (lastStm == null) {
      return false;
    }
    switch (lastStm.getToken()) {
      case BREAK -> {
        if (!lastStm.hasChildren()) {
          return false;
        }
        // Last statement is OK - continue with checking others.
      }
      case RETURN, THROW -> {
        // Last statement is OK - continue with checking others.
      }
      default -> {
        return false;
      }
    }

    // Other statements can be anything except for unlabeled "break". But for simplicity, don't go
    // into inner blocks and complex constructs - instead, allow only the simplest statements.
    for (Node child = n.getFirstChild(); child != lastStm; child = child.getNext()) {
      switch (child.getToken()) {
        case BREAK -> {
          if (!child.hasChildren()) {
            return false;
          }
          // This break is OK - continue with checking others.
        }
        case RETURN, THROW, FUNCTION, VAR, LET, CONST, EXPR_RESULT -> {
          // This statement is OK - continue with checking others.
        }
        default -> {
          return false;
        }
      }
    }

    return true;
  }

  /** Return true if the switch always "exits" (return, throw, etc). */
  private static boolean isSwitchExit(Node n) {
    checkState(n.isSwitch(), n);

    boolean hasDefaultCase = false;

    Node switchBody = n.getSecondChild();
    for (Node switchCase = switchBody.getFirstChild();
        switchCase != null;
        switchCase = switchCase.getNext()) {
      if (switchCase.isDefaultCase()) {
        hasDefaultCase = true;
      }
      Node block = switchCase.getLastChild();
      if ((block.hasChildren() || switchCase.getNext() == null)
          && !isUnconditionalBlockExit(block)) {
        return false;
      }
    }

    return hasDefaultCase;
  }

  /** Return true if the try..catch always "exits" (return, throw, etc). */
  private static boolean isTryExit(Node n) {
    checkState(n.isTry(), n);

    // finally - regardless of the behavior of the other blocks,
    // an exit from the finally with guarantee that behavior.
    if (n.hasXChildren(3)) {
      if (isUnconditionalBlockExit(n.getLastChild())) {
        return true;
      }
    }
    // try
    if (!isUnconditionalBlockExit(n.getFirstChild())) {
      return false;
    }
    // catch
    Node catches = n.getSecondChild();
    return !catches.hasChildren()
        || isUnconditionalBlockExit(catches.getOnlyChild().getLastChild());
  }

  private Node tryFoldComma(Node n) {
    // If the left side does nothing replace the comma with the result.
    Node parent = n.getParent();
    Node left = n.getFirstChild();
    Node right = left.getNext();

    left = trySimplifyUnusedResult(left);
    if (left == null || !mayHaveSideEffects(left)) {
      // Fold it!
      right.detach();
      n.replaceWith(right);
      reportChangeToEnclosingScope(parent);
      return right;
    }
    return n;
  }

  /** Try removing unneeded block nodes and their useless children */
  @Nullable Node tryOptimizeBlock(Node n) {
    // Remove any useless children
    for (Node c = n.getFirstChild(); c != null; ) {
      Node next = c.getNext(); // save c.next, since 'c' may be removed
      if (!isUnremovableNode(c) && !mayHaveSideEffects(c)) {
        // TODO(johnlenz): determine what this is actually removing. Candidates
        //    include: EMPTY nodes, control structures without children
        //    (removing infinite loops), empty try blocks.  What else?
        c.detach();
        reportChangeToEnclosingScope(n);
        markFunctionsDeleted(c);
      } else if (isExit(c)) {
        removeFollowingNodes(c);
        break;
      } else {
        tryOptimizeConditionalAfterAssign(c);
      }
      c = next;
    }

    if (n.isSyntheticBlock() || n.isScript() || n.getParent() == null) {
      return n;
    }

    // Try to merge the block with its parent, or remove it if it is an empty class static block.
    Node parent = n.getParent();
    if (NodeUtil.tryMergeBlock(n, isASTNormalized())) {
      reportChangeToEnclosingScope(parent);
      return null;
    } else if (parent.isClassMembers() && !n.hasChildren()) {
      n.detach();
      reportChangeToEnclosingScope(parent);
      return null;
    }

    return n;
  }

  private void removeFollowingNodes(Node n) {
    Node parent = n.getParent();
    if (parent.getLastChild() != n) {
      boolean changed = false;
      while (n.getNext() != null) {
        Node dead = n.getNext();
        if (NodeUtil.isFunctionDeclaration(dead)) {
          // Don't remove function declarations as they are hoisted
          n = dead;
          continue;
        }
        changed = true;
        NodeUtil.redeclareVarsInsideBranch(dead);
        redeclareIfBlockScopedVar(dead, parent);
        dead.detach();
        markFunctionsDeleted(dead);
      }
      if (changed) {
        reportChangeToEnclosingScope(parent);
      }
    }
  }

  /**
   * Redeclares the given node's names in the parent scope if they are block-scoped declarations;
   * otherwise does nothing.
   */
  private void redeclareIfBlockScopedVar(Node decl, Node parent) {
    // This isn't called on function declarations ever: this method is only used in
    // removeFollowingNodes, which preserves function declarations as they're hoisted. This
    // preconditions check is to make sure nothing else tries reusing this code, since in that case
    // maybe they do want to handle function declarations.
    checkState(!NodeUtil.isFunctionDeclaration(decl), "Unexpected function declaration %s", decl);
    if (!NodeUtil.isBlockScopedDeclaration(decl)) {
      return;
    }
    List<Node> blockScopedVars = new ArrayList<>();
    if (decl.isClass()) {
      blockScopedVars.add(decl.getFirstChild());
    } else {
      NodeUtil.visitLhsNodesInNode(
          decl,
          (Node n) -> {
            if (n.isName()) {
              blockScopedVars.add(n);
            }
          });
    }

    Node addBefore = NodeUtil.getInsertionPointAfterAllInnerFunctionDeclarations(parent);
    for (Node nameNode : blockScopedVars) {
      Node var = IR.let(IR.name(nameNode.getString()).srcref(nameNode)).srcref(nameNode);
      NodeUtil.copyNameAnnotations(nameNode, var.getFirstChild());
      if (addBefore != null) {
        var.insertBefore(addBefore);
      } else {
        parent.addChildToBack(var);
      }
    }
    this.addFeatureToEnclosingScript(parent, Feature.LET_DECLARATIONS);
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
   * Attempt to replace the condition of if or hook immediately that is a reference to a name that
   * is assigned immediately before.
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
    // or
    //   a = 0;
    //   a ?? foo(a)
    // TODO(johnlenz): This would be better handled by control-flow sensitive
    // constant propagation. As the other case that I want to handle is:
    //   i=0; for(;i<0;i++){}
    // as right now nothing facilitates removing a loop like that.
    // This is here simply to remove the cruft left behind goog.userAgent and
    // similar cases.

    if (!isSimpleAssignment(n)) {
      return;
    }
    Node conditionalRoot = getConditionalRoot(next);
    if (conditionalRoot == null) {
      return;
    }
    Node lhsAssign = getSimpleAssignmentName(n);

    Node condition = getConditionalStatementCondition(next);
    if (!lhsAssign.matchesName(condition)) {
      return;
    }
    Node rhsAssign = getSimpleAssignmentValue(n);
    switch (conditionalRoot.getToken()) {
      case AND, OR, IF, HOOK -> {
        // conditionals that coerce their condition to a boolean
        Tri value = NodeUtil.getBooleanValue(rhsAssign);
        if (value != Tri.UNKNOWN) {
          Node replacementConditionNode = NodeUtil.booleanNode(value.toBoolean(true));
          condition.replaceWith(replacementConditionNode);
          reportChangeToEnclosingScope(replacementConditionNode);
        }
        return;
      }
      case COALESCE -> {
        // conditional that checks whether its operand is nullish
        NodeUtil.ValueType valueType = NodeUtil.getKnownValueType(rhsAssign);
        switch (valueType) {
          case NULL:
          case VOID:
            condition.replaceWith(NodeUtil.newUndefinedNode(condition));
            reportChangeToEnclosingScope(conditionalRoot);
            break;
          case NUMBER:
          case BIGINT:
          case STRING:
          case BOOLEAN:
          case OBJECT:
            // semi-arbitrarily use '0' as a short non-nullish conditional
            condition.replaceWith(IR.number(0).srcref(condition));
            reportChangeToEnclosingScope(conditionalRoot);
            break;
          case UNDETERMINED:
            break;
        }
        return;
      }
      default -> throw new AssertionError("Unhandled condition " + conditionalRoot);
    }
  }

  /**
   * @return whether the node is a assignment to a simple name, or simple var declaration with
   *     initialization.
   */
  private static boolean isSimpleAssignment(Node n) {
    // For our purposes we define a simple assignment to be a assignment
    // to a NAME node, or a VAR declaration with one child and a initializer.
    if (NodeUtil.isExprAssign(n) && n.getFirstFirstChild().isName()) {
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
   * @return the root node if a conditional statement or else null
   */
  private @Nullable Node getConditionalRoot(Node n) {
    // We defined a conditional statement to be a IF or EXPR_RESULT rooted with
    // a HOOK, AND, or OR node.
    if (n == null) {
      return null;
    }
    if (n.isIf()) {
      return n;
    } else if (isExprConditional(n)) {
      return n.getFirstChild();
    }
    return null;
  }

  /**
   * @return Whether the node is a rooted with a HOOK, AND, OR, or COALESCE node.
   */
  private static boolean isExprConditional(Node n) {
    if (n.isExprResult()) {
      switch (n.getFirstChild().getToken()) {
        case HOOK, AND, OR, COALESCE -> {
          return true;
        }
        default -> {}
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
   *
   * @return the replacement node, if changed, or the original if not
   */
  private @Nullable Node tryFoldIf(Node n) {
    checkState(n.isIf(), n);
    Node parent = n.getParent();
    checkNotNull(parent);
    Token type = n.getToken();
    Node cond = n.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();

    // if (x) { .. } else { } --> if (x) { ... }
    if (elseBody != null && !mayHaveSideEffects(elseBody)) {
      elseBody.detach();
      reportChangeToEnclosingScope(n);
      elseBody = null;
    }

    // if (x) { } else { ... } --> if (!x) { ... }
    if (!mayHaveSideEffects(thenBody) && elseBody != null) {
      elseBody.detach();
      thenBody.replaceWith(elseBody);
      Node notCond = new Node(Token.NOT);
      cond.replaceWith(notCond);
      reportChangeToEnclosingScope(n);
      notCond.addChildToFront(cond);
      cond = notCond;
      thenBody = cond.getNext();
      elseBody = null;
    }

    // `if (x()) { }` or `if (x?.()) { }`
    if (!mayHaveSideEffects(thenBody) && elseBody == null) {
      if (mayHaveSideEffects(cond)) {
        // `x()` or `x?.()` has side effects, just leave the condition on its own.
        cond.detach();
        Node replacement = NodeUtil.newExpr(cond);
        n.replaceWith(replacement);
        reportChangeToEnclosingScope(parent);
        return replacement;
      } else {
        // `x()` or `x?.()` has no side effects, the whole tree is useless now.
        NodeUtil.removeChild(parent, n);
        reportChangeToEnclosingScope(parent);
        return null;
      }
    }

    // Try transforms that apply to both IF and HOOK.
    Tri condValue = NodeUtil.getBooleanValue(cond);
    if (condValue == Tri.UNKNOWN) {
      return n; // We can't remove branches otherwise!
    }

    if (mayHaveSideEffects(cond)) {
      // Transform "if (a = 2) {x =2}" into "if (true) {a=2;x=2}"
      boolean newConditionValue = condValue == Tri.TRUE;
      // Add an elseBody if it is needed.
      if (!newConditionValue && elseBody == null) {
        elseBody = IR.block().srcref(n);
        n.addChildToBack(elseBody);
      }
      Node newCond = NodeUtil.booleanNode(newConditionValue);
      cond.replaceWith(newCond);
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
        thenStmt.detach();
        n.replaceWith(thenStmt);
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
      branchToKeep.detach();
      n.replaceWith(branchToKeep);
      reportChangeToEnclosingScope(branchToKeep);
      markFunctionsDeleted(n);
      return branchToKeep;
    }
  }

  /**
   * Try folding HOOK (?:) if the condition results of the condition is known.
   *
   * @return the replacement node, if changed, or the original if not
   */
  private Node tryFoldHook(Node n) {
    checkState(n.isHook(), n);
    Node parent = n.getParent();
    checkNotNull(parent);
    Node cond = n.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();

    Tri condValue = NodeUtil.getBooleanValue(cond);
    if (condValue == Tri.UNKNOWN) {
      // If the result nodes are equivalent, then one of the nodes can be
      // removed and it doesn't matter which.
      if (!areNodesEqualForInlining(thenBody, elseBody)) {
        return n; // We can't remove branches otherwise!
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

    n.replaceWith(replacement);
    reportChangeToEnclosingScope(replacement);
    markFunctionsDeleted(branchToRemove);
    return replacement;
  }

  /** Removes FORs that always evaluate to false. */
  @Nullable Node tryFoldFor(Node n) {
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
        increment.insertAfter(cond);
      }
    }

    // There is an initializer skip it
    if (!n.getFirstChild().isEmpty()) {
      return n;
    }

    if (NodeUtil.getBooleanValue(cond) != Tri.FALSE) {
      return n;
    }

    Node parent = n.getParent();
    NodeUtil.redeclareVarsInsideBranch(n);

    if (!mayHaveSideEffects(cond)) {
      // Remove the entire loop and any associated labels.
      while (parent.isLabel()) {
        n = parent;
        parent = parent.getParent();
      }
      n.detach();
    } else {
      Node statement = IR.exprResult(cond.detach()).srcrefIfMissing(cond);
      if (parent.isLabel()) {
        Node block = IR.block();
        block.srcrefIfMissing(statement);
        block.addChildToFront(statement);
        statement = block;
      }
      n.replaceWith(statement);
    }
    reportChangeToEnclosingScope(parent);

    return null;
  }

  /**
   * Removes DOs that always evaluate to false. This leaves the statements that were in the loop in
   * a BLOCK node. The block will be removed in a later pass, if possible.
   */
  Node tryFoldDoAway(Node n) {
    checkArgument(n.isDo());

    Node cond = NodeUtil.getConditionExpression(n);
    if (NodeUtil.getBooleanValue(cond) != Tri.FALSE) {
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
      condStatement.insertAfter(block);
    }
    reportChangeToEnclosingScope(parent);

    return block;
  }

  /** Removes DOs that have empty bodies into FORs, which are much easier for the CFA to analyze. */
  Node tryFoldEmptyDo(Node n) {
    checkArgument(n.isDo());

    Node body = NodeUtil.getLoopCodeBlock(n);
    if (body.isBlock() && !body.hasChildren()) {
      Node cond = NodeUtil.getConditionExpression(n);
      Node forNode =
          IR.forNode(IR.empty().srcref(n), cond.detach(), IR.empty().srcref(n), body.detach());
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
        lastChild.detach();
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

  /** Returns whether a node has any unhandled breaks or continue. */
  static boolean hasUnnamedBreakOrContinue(Node n) {
    return NodeUtil.has(
            n,
            // Check for unlabeled breaks
            (Node node) -> node.isBreak() && !node.hasChildren(),
            // ...inside contexts that can contain breaks.
            (Node node) ->
                !IR.mayBeExpression(node) // Functions are not visited
                    && !NodeUtil.isLoopStructure(node)
                    && !node.isSwitch())
        || NodeUtil.has(
            n,
            // Check for unlabeled continues
            (Node node) -> node.isContinue() && !node.hasChildren(),
            // ...inside contexts that can contain continues.
            (Node node) ->
                !IR.mayBeExpression(node) // Functions are not visited
                    && !NodeUtil.isLoopStructure(node));
  }

  /** Remove always true loop conditions. */
  private void tryFoldForCondition(Node forCondition) {
    if (getSideEffectFreeBooleanValue(forCondition) == Tri.TRUE) {
      reportChangeToEnclosingScope(forCondition);
      forCondition.replaceWith(IR.empty());
    }
  }

  private Node tryRemoveOptionalChaining(Node optionalChain) {
    Node callee = optionalChain.getFirstChild();
    if (!NodeUtil.isNullOrUndefined(callee)) {
      return optionalChain;
    }
    final Node result;
    if (this.mayHaveSideEffects(callee)) {
      // Simplify `(void sideEffectFunction())?.()` to `(void sideEffectFunction())`
      // The optional chain call won't execute but sideEffectFunction() is still evaluated.
      optionalChain.replaceWith(callee.detach());
      result = callee;
    } else {
      // Remove `(void 0)?.()` and (null)?.() and simplify `(void 0)?.x and null?.x` to void 0
      result = NodeUtil.newUndefinedNode(callee);
      optionalChain.replaceWith(result);
    }
    this.markFunctionsDeleted(optionalChain);
    this.reportChangeToEnclosingScope(result);
    return result;
  }
}
