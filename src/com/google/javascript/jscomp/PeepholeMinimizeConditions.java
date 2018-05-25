/*
 * Copyright 2010 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.MinimizedCondition.MeasuredNode;
import com.google.javascript.jscomp.MinimizedCondition.MinimizationStyle;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

/**
 * A peephole optimization that minimizes conditional expressions
 * according to De Morgan's laws.
 * Also rewrites conditional statements as expressions by replacing them
 * with HOOKs and short-circuit binary operators.
 *
 * Based on PeepholeSubstituteAlternateSyntax
 */
class PeepholeMinimizeConditions
  extends AbstractPeepholeOptimization {

  private static final int AND_PRECEDENCE = NodeUtil.precedence(Token.AND);

  private final boolean late;

  /**
   * @param late When late is false, this mean we are currently running before
   * most of the other optimizations. In this case we would avoid optimizations
   * that would make the code harder to analyze (such as using string splitting,
   * merging statements with commas, etc). When this is true, we would
   * do anything to minimize for size.
   */
  PeepholeMinimizeConditions(boolean late) {
    this.late = late;
  }

  /**
   * Tries to apply our various peephole minimizations on the passed in node.
   */
  @Override
  @SuppressWarnings("fallthrough")
  public Node optimizeSubtree(Node node) {
    switch (node.getToken()) {
      case THROW:
      case RETURN: {
        Node result = tryRemoveRedundantExit(node);
        if (result != node) {
          return result;
        }
        return tryReplaceExitWithBreak(node);
      }

      // TODO(johnlenz): Maybe remove redundant BREAK and CONTINUE. Overlaps
      // with MinimizeExitPoints.

      case NOT:
        tryMinimizeCondition(node.getFirstChild());
        return tryMinimizeNot(node);

      case IF:
        performConditionSubstitutions(node.getFirstChild());
        return tryMinimizeIf(node);

      case EXPR_RESULT:
        performConditionSubstitutions(node.getFirstChild());
        return tryMinimizeExprResult(node);

      case HOOK:
        performConditionSubstitutions(node.getFirstChild());
        return tryMinimizeHook(node);

      case WHILE:
      case DO:
        tryMinimizeCondition(NodeUtil.getConditionExpression(node));
        return node;

      case FOR:
        tryJoinForCondition(node);
        tryMinimizeCondition(NodeUtil.getConditionExpression(node));
        return node;

      case BLOCK:
        return tryReplaceIf(node);

      default:
        return node; //Nothing changed
    }
  }

  private void tryJoinForCondition(Node n) {
    if (!late) {
      return;
    }

    Node block = n.getLastChild();
    Node maybeIf = block.getFirstChild();
    if (maybeIf != null && maybeIf.isIf()) {
      Node thenBlock = maybeIf.getSecondChild();
      Node maybeBreak = thenBlock.getFirstChild();
      if (maybeBreak != null && maybeBreak.isBreak()
          && !maybeBreak.hasChildren()) {

        // Preserve the IF ELSE expression is there is one.
        if (maybeIf.getChildCount() == 3) {
          block.replaceChild(maybeIf,
              maybeIf.getLastChild().detach());
        } else {
          NodeUtil.redeclareVarsInsideBranch(thenBlock);
          block.removeFirstChild();
        }

        Node ifCondition = maybeIf.removeFirstChild();
        Node fixedIfCondition = IR.not(ifCondition)
            .srcref(ifCondition);

        // OK, join the IF expression with the FOR expression
        Node forCondition = NodeUtil.getConditionExpression(n);
        if (forCondition.isEmpty()) {
          n.replaceChild(forCondition, fixedIfCondition);
          compiler.reportChangeToEnclosingScope(fixedIfCondition);
        } else {
          Node replacement = new Node(Token.AND);
          n.replaceChild(forCondition, replacement);
          replacement.addChildToBack(forCondition);
          replacement.addChildToBack(fixedIfCondition);
          compiler.reportChangeToEnclosingScope(replacement);
        }
      }
    }
  }

  /**
   * Use "return x?1:2;" in place of "if(x)return 1;return 2;"
   */
  private Node tryReplaceIf(Node n) {

    Node next = null;
    for (Node child = n.getFirstChild();
         child != null; child = next){
      next = child.getNext();
      if (child.isIf()){
        Node cond = child.getFirstChild();
        Node thenBranch = cond.getNext();
        Node elseBranch = thenBranch.getNext();
        Node nextNode = child.getNext();

        if (nextNode != null && elseBranch == null
            && isReturnBlock(thenBranch)
            && nextNode.isIf()) {
          Node nextCond = nextNode.getFirstChild();
          Node nextThen = nextCond.getNext();
          Node nextElse = nextThen.getNext();
          if (thenBranch.isEquivalentToTyped(nextThen)) {
            // Transform
            //   if (x) return 1; if (y) return 1;
            // to
            //   if (x||y) return 1;
            child.detach();
            child.detachChildren();
            Node newCond = new Node(Token.OR, cond);
            nextNode.replaceChild(nextCond, newCond);
            newCond.addChildToBack(nextCond);
            compiler.reportChangeToEnclosingScope(newCond);
          } else if (nextElse != null
              && thenBranch.isEquivalentToTyped(nextElse)) {
            // Transform
            //   if (x) return 1; if (y) foo() else return 1;
            // to
            //   if (!x&&y) foo() else return 1;
            child.detach();
            child.detachChildren();
            Node newCond = new Node(Token.AND,
                IR.not(cond).srcref(cond));
            nextNode.replaceChild(nextCond, newCond);
            newCond.addChildToBack(nextCond);
            compiler.reportChangeToEnclosingScope(newCond);
          }
        } else if (nextNode != null && elseBranch == null
            && isReturnBlock(thenBranch) && isReturnExpression(nextNode)) {
          Node thenExpr = null;
          // if(x)return; return 1 -> return x?void 0:1
          if (isReturnExpressBlock(thenBranch)) {
            thenExpr = getBlockReturnExpression(thenBranch);
            thenExpr.detach();
          } else {
            thenExpr = NodeUtil.newUndefinedNode(child);
          }

          Node elseExpr = nextNode.getFirstChild();

          cond.detach();
          elseExpr.detach();

          Node returnNode = IR.returnNode(
                                IR.hook(cond, thenExpr, elseExpr)
                                    .srcref(child));
          n.replaceChild(child, returnNode);
          n.removeChild(nextNode);
          compiler.reportChangeToEnclosingScope(n);
          // everything else in the block is dead code.
          break;
        } else if (elseBranch != null && statementMustExitParent(thenBranch)) {
          child.removeChild(elseBranch);
          n.addChildAfter(elseBranch, child);
          compiler.reportChangeToEnclosingScope(n);
        }
      }
    }
    return n;
  }

  private static boolean statementMustExitParent(Node n) {
    switch (n.getToken()) {
      case THROW:
      case RETURN:
        return true;
      case BLOCK:
        if (n.hasChildren()) {
          Node child = n.getLastChild();
          return statementMustExitParent(child);
        }
        return false;
      // TODO(johnlenz): handle TRY/FINALLY
      case FUNCTION:
      default:
        return false;
    }
  }

  /**
   * Replace duplicate exits in control structures.  If the node following
   * the exit node expression has the same effect as exit node, the node can
   * be replaced or removed.
   * For example:
   *   "while (a) {return f()} return f();" ==> "while (a) {break} return f();"
   *   "while (a) {throw 'ow'} throw 'ow';" ==> "while (a) {break} throw 'ow';"
   *
   * @param n An follow control exit expression (a THROW or RETURN node)
   * @return The replacement for n, or the original if no change was made.
   */
  private Node tryReplaceExitWithBreak(Node n) {
    Node result = n.getFirstChild();

    // Find the enclosing control structure, if any, that a "break" would exit
    // from.
    Node breakTarget = n;
    for (; !ControlFlowAnalysis.isBreakTarget(breakTarget, null /* no label */);
        breakTarget = breakTarget.getParent()) {
      if (breakTarget.isFunction() || breakTarget.isScript()) {
        // No break target.
        return n;
      }
    }

    Node follow = ControlFlowAnalysis.computeFollowNode(breakTarget);

    // Skip pass all the finally blocks because both the break and return will
    // also trigger all the finally blocks. However, the order of execution is
    // slightly changed. Consider:
    //
    // return a() -> finally { b() } -> return a()
    //
    // which would call a() first. However, changing the first return to a
    // break will result in calling b().

    Node prefinallyFollows = follow;
    follow = skipFinallyNodes(follow);

    if (prefinallyFollows != follow) {
      // There were finally clauses
      if (!isPure(result)) {
        // Can't defer the exit
        return n;
      }
    }

    if (follow == null && (n.isThrow() || result != null)) {
      // Can't complete remove a throw here or a return with a result.
      return n;
    }

    // When follow is null, this mean the follow of a break target is the
    // end of a function. This means a break is same as return.
    if (follow == null || areMatchingExits(n, follow)) {
      Node replacement = IR.breakNode();
      n.replaceWith(replacement);
      compiler.reportChangeToEnclosingScope(replacement);
      return replacement;
    }

    return n;
  }

  /**
   * Remove duplicate exits.  If the node following the exit node expression
   * has the same effect as exit node, the node can be removed.
   * For example:
   *   "if (a) {return f()} return f();" ==> "if (a) {} return f();"
   *   "if (a) {throw 'ow'} throw 'ow';" ==> "if (a) {} throw 'ow';"
   *
   * @param n An follow control exit expression (a THROW or RETURN node)
   * @return The replacement for n, or the original if no change was made.
   */
  private Node tryRemoveRedundantExit(Node n) {
    Node exitExpr = n.getFirstChild();

    Node follow = ControlFlowAnalysis.computeFollowNode(n);

    // Skip pass all the finally blocks because both the fall through and return
    // will also trigger all the finally blocks.
    Node prefinallyFollows = follow;
    follow = skipFinallyNodes(follow);
    if (prefinallyFollows != follow) {
      // There were finally clauses
      if (!isPure(exitExpr)) {
        // Can't replace the return
        return n;
      }
    }

    if (follow == null && (n.isThrow() || exitExpr != null)) {
      // Can't complete remove a throw here or a return with a result.
      return n;
    }

    // When follow is null, this mean the follow of a break target is the
    // end of a function. This means a break is same as return.
    if (follow == null || areMatchingExits(n, follow)) {
      compiler.reportChangeToEnclosingScope(n);
      n.detach();
      return null;
    }

    return n;
  }

  /**
   * @return Whether the expression does not produces and can not be affected
   * by side-effects.
   */
  boolean isPure(Node n) {
    return n == null
        || (!NodeUtil.canBeSideEffected(n)
            && !mayHaveSideEffects(n));
  }

  /**
   * @return n or the node following any following finally nodes.
   */
  static Node skipFinallyNodes(Node n) {
    while (n != null && NodeUtil.isTryFinallyNode(n.getParent(), n)) {
      n = ControlFlowAnalysis.computeFollowNode(n);
    }
    return n;
  }

  /**
   * Check whether one exit can be replaced with another. Verify:
   * 1) They are identical expressions
   * 2) If an exception is possible that the statements, the original
   * and the potential replacement are in the same exception handler.
   */
  boolean areMatchingExits(Node nodeThis, Node nodeThat) {
    return nodeThis.isEquivalentTo(nodeThat)
        && (!isExceptionPossible(nodeThis)
            || getExceptionHandler(nodeThis) == getExceptionHandler(nodeThat));
  }

  static boolean isExceptionPossible(Node n) {
    // TODO(johnlenz): maybe use ControlFlowAnalysis.mayThrowException?
    checkState(n.isReturn() || n.isThrow(), n);
    return n.isThrow()
        || (n.hasChildren()
            && !NodeUtil.isLiteralValue(n.getLastChild(), true));
  }

  static Node getExceptionHandler(Node n) {
    return ControlFlowAnalysis.getExceptionHandler(n);
  }

  /**
   * Try to minimize NOT nodes such as !(x==y).
   *
   * Returns the replacement for n or the original if no change was made
   */
  private Node tryMinimizeNot(Node n) {
    checkArgument(n.isNot());
    Node parent = n.getParent();

    Node notChild = n.getFirstChild();
    // negative operator of the current one : == -> != for instance.
    Token complementOperator;
    switch (notChild.getToken()) {
      case EQ:
        complementOperator = Token.NE;
        break;
      case NE:
        complementOperator = Token.EQ;
        break;
      case SHEQ:
        complementOperator = Token.SHNE;
        break;
      case SHNE:
        complementOperator = Token.SHEQ;
        break;
      // GT, GE, LT, LE are not handled in this because !(x<NaN) != x>=NaN.
      default:
        return n;
    }
    Node newOperator = n.removeFirstChild();
    newOperator.setToken(complementOperator);
    parent.replaceChild(n, newOperator);
    compiler.reportChangeToEnclosingScope(parent);
    return newOperator;
  }


  /**
   * Try to remove leading NOTs from EXPR_RESULTS.
   *
   * Returns the replacement for n or the original if no replacement was
   * necessary.
   */
  private Node tryMinimizeExprResult(Node n) {
    Node originalExpr = n.getFirstChild();
    MinimizedCondition minCond = MinimizedCondition.fromConditionNode(originalExpr);
    MeasuredNode mNode =
        minCond.getMinimized(MinimizationStyle.ALLOW_LEADING_NOT);
    if (mNode.isNot()) {
      // Remove the leading NOT in the EXPR_RESULT.
      replaceNode(originalExpr, mNode.withoutNot());
    } else {
      replaceNode(originalExpr, mNode);
    }
    return n;
  }

  /**
   * Try flipping HOOKs that have negated conditions.
   *
   * Returns the replacement for n or the original if no replacement was
   * necessary.
   */
  private Node tryMinimizeHook(Node n) {
    Node originalCond = n.getFirstChild();
    MinimizedCondition minCond = MinimizedCondition.fromConditionNode(originalCond);
    MeasuredNode mNode =
        minCond.getMinimized(MinimizationStyle.ALLOW_LEADING_NOT);
    if (mNode.isNot()) {
      // Swap the HOOK
      Node thenBranch = n.getSecondChild();
      replaceNode(originalCond, mNode.withoutNot());
      n.removeChild(thenBranch);
      n.addChildToBack(thenBranch);
      compiler.reportChangeToEnclosingScope(n);
    } else {
      replaceNode(originalCond, mNode);
    }
    return n;
  }

  /**
   * Try turning IF nodes into smaller HOOKs
   *
   * Returns the replacement for n or the original if no replacement was
   * necessary.
   */
  private Node tryMinimizeIf(Node n) {

    Node parent = n.getParent();

    Node originalCond = n.getFirstChild();

    /* If the condition is a literal, we'll let other
     * optimizations try to remove useless code.
     */
    if (NodeUtil.isLiteralValue(originalCond, true)) {
      return n;
    }

    Node thenBranch = originalCond.getNext();
    Node elseBranch = thenBranch.getNext();

    MinimizedCondition minCond = MinimizedCondition.fromConditionNode(originalCond);

    // Compute two minimized representations. The first representation counts
    // a leading NOT node, and the second ignores a leading NOT node.
    // If we can fold the if statement into a HOOK or boolean operation,
    // then the NOT node does not matter, and we prefer the second condition.
    // If we cannot fold the if statement, then we prefer the first condition.
    MeasuredNode unnegatedCond = minCond.getMinimized(MinimizationStyle.PREFER_UNNEGATED);
    MeasuredNode shortCond = minCond.getMinimized(MinimizationStyle.ALLOW_LEADING_NOT);

    if (elseBranch == null) {
      if (isFoldableExpressBlock(thenBranch)) {
        Node expr = getBlockExpression(thenBranch);
        if (!late && isPropertyAssignmentInExpression(expr)) {
          // Keep opportunities for CollapseProperties such as
          // a.longIdentifier || a.longIdentifier = ... -> var a = ...;
          // until CollapseProperties has been run.
          replaceNode(originalCond, unnegatedCond);
          return n;
        }

        if (shortCond.isNot()) {
          // if(!x)bar(); -> x||bar();
          Node replacementCond = replaceNode(originalCond, shortCond.withoutNot()).detach();
          Node or = IR.or(
              replacementCond,
              expr.removeFirstChild()).srcref(n);
          Node newExpr = NodeUtil.newExpr(or);
          parent.replaceChild(n, newExpr);
          compiler.reportChangeToEnclosingScope(parent);

          return newExpr;
        }
        // True, but removed for performance reasons.
        // Preconditions.checkState(shortCond.isEquivalentTo(unnegatedCond));

        // if(x)foo(); -> x&&foo();
        if (shortCond.isLowerPrecedenceThan(AND_PRECEDENCE)
            && isLowerPrecedence(expr.getFirstChild(), AND_PRECEDENCE)) {
          // One additional set of parentheses is worth the change even if
          // there is no immediate code size win. However, two extra pair of
          // {}, we would have to think twice. (unless we know for sure the
          // we can further optimize its parent.
          replaceNode(originalCond, shortCond);
          return n;
        }

        Node replacementCond = replaceNode(originalCond, shortCond).detach();
        Node and = IR.and(replacementCond, expr.removeFirstChild()).srcref(n);
        Node newExpr = NodeUtil.newExpr(and);
        parent.replaceChild(n, newExpr);
        compiler.reportChangeToEnclosingScope(parent);

        return newExpr;
      } else {

        // Try to combine two IF-ELSE
        if (NodeUtil.isStatementBlock(thenBranch) && thenBranch.hasOneChild()) {
          Node innerIf = thenBranch.getFirstChild();

          if (innerIf.isIf()) {
            Node innerCond = innerIf.getFirstChild();
            Node innerThenBranch = innerCond.getNext();
            Node innerElseBranch = innerThenBranch.getNext();

            if (innerElseBranch == null
                 && !(unnegatedCond.isLowerPrecedenceThan(AND_PRECEDENCE)
                   && isLowerPrecedence(innerCond, AND_PRECEDENCE))) {
              Node replacementCond = replaceNode(originalCond, unnegatedCond).detach();
              n.detachChildren();
              n.addChildToBack(
                  IR.and(
                      replacementCond,
                      innerCond.detach())
                      .srcref(originalCond));
              n.addChildToBack(innerThenBranch.detach());
              compiler.reportChangeToEnclosingScope(n);
              // Not worth trying to fold the current IF-ELSE into && because
              // the inner IF-ELSE wasn't able to be folded into && anyways.
              return n;
            }
          }
        }
      }
      replaceNode(originalCond, unnegatedCond);
      return n;
    }

    /* TODO(dcc) This modifies the siblings of n, which is undesirable for a
     * peephole optimization. This should probably get moved to another pass.
     */
    tryRemoveRepeatedStatements(n);

    // if(!x)foo();else bar(); -> if(x)bar();else foo();
    // An additional set of curly braces isn't worth it.
    if (shortCond.isNot() && !consumesDanglingElse(elseBranch)) {
      replaceNode(originalCond, shortCond.withoutNot());
      n.removeChild(thenBranch);
      n.addChildToBack(thenBranch);
      compiler.reportChangeToEnclosingScope(n);
      return n;
    }

    // if(x)return 1;else return 2; -> return x?1:2;
    if (isReturnExpressBlock(thenBranch) && isReturnExpressBlock(elseBranch)) {
      Node thenExpr = getBlockReturnExpression(thenBranch);
      Node elseExpr = getBlockReturnExpression(elseBranch);

      Node replacementCond = replaceNode(originalCond, shortCond).detach();
      thenExpr.detach();
      elseExpr.detach();

      // note - we ignore any cases with "return;", technically this
      // can be converted to "return undefined;" or some variant, but
      // that does not help code size.
      Node returnNode = IR.returnNode(
                            IR.hook(replacementCond, thenExpr, elseExpr)
                                .srcref(n));
      parent.replaceChild(n, returnNode);
      compiler.reportChangeToEnclosingScope(returnNode);
      return returnNode;
    }

    boolean thenBranchIsExpressionBlock = isFoldableExpressBlock(thenBranch);
    boolean elseBranchIsExpressionBlock = isFoldableExpressBlock(elseBranch);

    if (thenBranchIsExpressionBlock && elseBranchIsExpressionBlock) {
      Node thenOp = getBlockExpression(thenBranch).getFirstChild();
      Node elseOp = getBlockExpression(elseBranch).getFirstChild();
      if (thenOp.getToken() == elseOp.getToken()) {
        // if(x)a=1;else a=2; -> a=x?1:2;
        if (NodeUtil.isAssignmentOp(thenOp)) {
          Node lhs = thenOp.getFirstChild();
          if (areNodesEqualForInlining(lhs, elseOp.getFirstChild())
              // if LHS has side effects, don't proceed [since the optimization
              // evaluates LHS before cond]
              // NOTE - there are some circumstances where we can
              // proceed even if there are side effects...
              && !mayEffectMutableState(lhs)
              && (!mayHaveSideEffects(originalCond)
                  || (thenOp.isAssign() && thenOp.getFirstChild().isName()))) {

            Node replacementCond = replaceNode(originalCond, shortCond).detach();
            Node assignName = thenOp.removeFirstChild();
            Node thenExpr = thenOp.removeFirstChild();
            Node elseExpr = elseOp.getLastChild();
            elseOp.removeChild(elseExpr);

            Node hookNode = IR.hook(replacementCond, thenExpr, elseExpr)
                .srcref(n);
            Node assign = new Node(thenOp.getToken(), assignName, hookNode).srcref(thenOp);
            Node expr = NodeUtil.newExpr(assign);
            parent.replaceChild(n, expr);
            compiler.reportChangeToEnclosingScope(parent);

            return expr;
          }
        }
      }
      // if(x)foo();else bar(); -> x?foo():bar()
      Node replacementCond = replaceNode(originalCond, shortCond).detach();
      thenOp.detach();
      elseOp.detach();
      Node expr = IR.exprResult(
          IR.hook(replacementCond, thenOp, elseOp).srcref(n));
      parent.replaceChild(n, expr);
      compiler.reportChangeToEnclosingScope(parent);
      return expr;
    }

    boolean thenBranchIsVar = isVarBlock(thenBranch);
    boolean elseBranchIsVar = isVarBlock(elseBranch);

    // if(x)var y=1;else y=2  ->  var y=x?1:2
    if (thenBranchIsVar && elseBranchIsExpressionBlock
        && getBlockExpression(elseBranch).getFirstChild().isAssign()) {

      Node var = getBlockVar(thenBranch);
      Node elseAssign = getBlockExpression(elseBranch).getFirstChild();

      Node name1 = var.getFirstChild();
      Node maybeName2 = elseAssign.getFirstChild();

      if (name1.hasChildren()
          && maybeName2.isName()
          && name1.getString().equals(maybeName2.getString())) {
        checkState(name1.hasOneChild());
        Node thenExpr = name1.removeFirstChild();
        Node elseExpr = elseAssign.getLastChild().detach();
        Node replacementCond = replaceNode(originalCond, shortCond).detach();
        Node hookNode = IR.hook(replacementCond, thenExpr, elseExpr).srcref(n);
        var.detach();
        name1.addChildToBack(hookNode);
        parent.replaceChild(n, var);
        compiler.reportChangeToEnclosingScope(parent);
        return var;
      }

    // if(x)y=1;else var y=2  ->  var y=x?1:2
    } else if (elseBranchIsVar && thenBranchIsExpressionBlock
        && getBlockExpression(thenBranch).getFirstChild().isAssign()) {

      Node var = getBlockVar(elseBranch);
      Node thenAssign = getBlockExpression(thenBranch).getFirstChild();

      Node maybeName1 = thenAssign.getFirstChild();
      Node name2 = var.getFirstChild();

      if (name2.hasChildren()
          && maybeName1.isName()
          && maybeName1.getString().equals(name2.getString())) {
        Node thenExpr = thenAssign.getLastChild().detach();
        checkState(name2.hasOneChild());
        Node elseExpr = name2.removeFirstChild();
        Node replacementCond = replaceNode(originalCond, shortCond).detach();
        Node hookNode = IR.hook(replacementCond, thenExpr, elseExpr).srcref(n);
        var.detach();
        name2.addChildToBack(hookNode);
        parent.replaceChild(n, var);
        compiler.reportChangeToEnclosingScope(parent);

        return var;
      }
    }

    replaceNode(originalCond, unnegatedCond);
    return n;
  }

  /**
   * Try to remove duplicate statements from IF blocks. For example:
   *
   * if (a) {
   *   x = 1;
   *   return true;
   * } else {
   *   x = 2;
   *   return true;
   * }
   *
   * becomes:
   *
   * if (a) {
   *   x = 1;
   * } else {
   *   x = 2;
   * }
   * return true;
   *
   * @param n The IF node to examine.
   */
  private void tryRemoveRepeatedStatements(Node n) {
    // Only run this if variable names are guaranteed to be unique. Otherwise bad things can happen:
    // see PeepholeMinimizeConditionsTest#testDontRemoveDuplicateStatementsWithoutNormalization
    if (!isASTNormalized()) {
      return;
    }
    checkState(n.isIf(), n);

    Node parent = n.getParent();
    if (!NodeUtil.isStatementBlock(parent)) {
      // If the immediate parent is something like a label, we
      // can't move the statement, so bail.
      return;
    }

    Node cond = n.getFirstChild();
    Node trueBranch = cond.getNext();
    Node falseBranch = trueBranch.getNext();
    checkNotNull(trueBranch);
    checkNotNull(falseBranch);

    while (true) {
      Node lastTrue = trueBranch.getLastChild();
      Node lastFalse = falseBranch.getLastChild();
      if (lastTrue == null || lastFalse == null
          || !areNodesEqualForInlining(lastTrue, lastFalse)) {
        break;
      }
      lastTrue.detach();
      lastFalse.detach();
      parent.addChildAfter(lastTrue, n);
      compiler.reportChangeToEnclosingScope(parent);
    }
  }

  /**
   * @return Whether the node is a block with a single statement that is
   *     an expression.
   */
  private static boolean isFoldableExpressBlock(Node n) {
    if (n.isBlock()) {
      if (n.hasOneChild()) {
        Node maybeExpr = n.getFirstChild();
        if (maybeExpr.isExprResult()) {
          // IE has a bug where event handlers behave differently when
          // their return value is used vs. when their return value is in
          // an EXPR_RESULT. It's pretty freaking weird. See:
          // http://blickly.github.io/closure-compiler-issues/#291
          // We try to detect this case, and not fold EXPR_RESULTs
          // into other expressions.
          if (maybeExpr.getFirstChild().isCall()) {
            Node calledFn = maybeExpr.getFirstFirstChild();

            // We only have to worry about methods with an implicit 'this'
            // param, or this doesn't happen.
            if (calledFn.isGetElem()) {
              return false;
            } else if (calledFn.isGetProp()
                       && calledFn.getLastChild().getString().startsWith("on")) {
              return false;
            }
          }

          return true;
        }
        return false;
      }
    }

    return false;
  }

  /**
   * @return The expression node.
   */
  private static Node getBlockExpression(Node n) {
    checkState(isFoldableExpressBlock(n));
    return n.getFirstChild();
  }

  /**
   * @return Whether the node is a block with a single statement that is
   *     an return with or without an expression.
   */
  private static boolean isReturnBlock(Node n) {
    if (n.isBlock()) {
      if (n.hasOneChild()) {
        Node first = n.getFirstChild();
        return first.isReturn();
      }
    }

    return false;
  }

  /**
   * @return Whether the node is a block with a single statement that is
   *     an return.
   */
  private static boolean isReturnExpressBlock(Node n) {
    if (n.isBlock()) {
      if (n.hasOneChild()) {
        Node first = n.getFirstChild();
        if (first.isReturn()) {
          return first.hasOneChild();
        }
      }
    }

    return false;
  }

  /**
   * @return Whether the node is a single return statement.
   */
  private static boolean isReturnExpression(Node n) {
    if (n.isReturn()) {
      return n.hasOneChild();
    }
    return false;
  }

  /**
   * @return The expression that is part of the return.
   */
  private static Node getBlockReturnExpression(Node n) {
    checkState(isReturnExpressBlock(n));
    return n.getFirstFirstChild();
  }

  /**
   * @return Whether the node is a block with a single statement that is
   *     a VAR declaration of a single variable.
   */
  private static boolean isVarBlock(Node n) {
    if (n.isBlock()) {
      if (n.hasOneChild()) {
        Node first = n.getFirstChild();
        if (first.isVar()) {
          return first.hasOneChild();
        }
      }
    }

    return false;
  }

  /**
   * @return The var node.
   */
  private static Node getBlockVar(Node n) {
    checkState(isVarBlock(n));
    return n.getFirstChild();
  }

  /**
   * Does a statement consume a 'dangling else'? A statement consumes
   * a 'dangling else' if an 'else' token following the statement
   * would be considered by the parser to be part of the statement.
   */
  private static boolean consumesDanglingElse(Node n) {
    while (true) {
      switch (n.getToken()) {
        case IF:
          if (n.getChildCount() < 3) {
            return true;
          }
          // This IF node has no else clause.
          n = n.getLastChild();
          continue;
        case BLOCK:
          if (!n.hasOneChild()) {
            return false;
          }
          // This BLOCK has no curly braces.
          n = n.getLastChild();
          continue;
        case WITH:
        case WHILE:
        case FOR:
        case FOR_IN:
          n = n.getLastChild();
          continue;
        default:
          return false;
      }
    }
  }

  /**
   * Whether the node type has lower precedence than "precedence"
   */
  static boolean isLowerPrecedence(Node n, int precedence) {
    return NodeUtil.precedence(n.getToken()) < precedence;
  }

  /**
   * Does the expression contain a property assignment?
   */
  private static boolean isPropertyAssignmentInExpression(Node n) {
    Predicate<Node> isPropertyAssignmentInExpressionPredicate =
        new Predicate<Node>() {
      @Override
      public boolean apply(Node input) {
        return (input.isGetProp()
            && input.getParent().isAssign());
      }
    };

    return NodeUtil.has(n, isPropertyAssignmentInExpressionPredicate,
        NodeUtil.MATCH_NOT_FUNCTION);
  }

  /**
   * Try to minimize condition expression, as there are additional
   * assumptions that can be made when it is known that the final result
   * is a boolean.
   *
   * @return The replacement for n, or the original if no change was made.
   */
  private Node tryMinimizeCondition(Node n) {
    n = performConditionSubstitutions(n);
    MinimizedCondition minCond = MinimizedCondition.fromConditionNode(n);
    return replaceNode(
        n,
        minCond.getMinimized(MinimizationStyle.PREFER_UNNEGATED));
  }

  private Node replaceNode(Node original, MeasuredNode measuredNodeReplacement) {
    if (measuredNodeReplacement.willChange(original)) {
      Node replacement = measuredNodeReplacement.applyTo(original);
      compiler.reportChangeToEnclosingScope(replacement);
      return replacement;
    }
    return original;
  }

  /**
   * Try to minimize the given condition by applying local substitutions.
   *
   * The following types of transformations are performed:
   *   x || true        --> true
   *   x && true        --> x
   *   x ? false : true --> !x
   *   x ? true : y     --> x || y
   *   x ? x : y        --> x || y
   *
   *   Returns the replacement for n, or the original if no change was made
   */
  private Node performConditionSubstitutions(Node n) {
    Node parent = n.getParent();

    switch (n.getToken()) {
      case OR:
      case AND: {
        Node left = n.getFirstChild();
        Node right = n.getLastChild();

        // Because the expression is in a boolean context minimize
        // the children, this can't be done in the general case.
        left = performConditionSubstitutions(left);
        right = performConditionSubstitutions(right);

        // Remove useless conditionals
        // Handle the following cases:
        //   x || false --> x
        //   x && true --> x
        // This works regardless of whether x has side effects.
        //
        // If x does not have side effects:
        //   x || true  --> true
        //   x && false  --> false
        //
        // If x may have side effects:
        //   x || true  --> x,true
        //   x && false  --> x,false
        //
        // In the last two cases, code size may increase slightly (adding
        // some parens because the comma operator has a low precedence) but
        // the new AST is easier for other passes to handle.
        TernaryValue rightVal = NodeUtil.getPureBooleanValue(right);
        if (NodeUtil.getPureBooleanValue(right) != TernaryValue.UNKNOWN) {
            Token type = n.getToken();
          Node replacement = null;
          boolean rval = rightVal.toBoolean(true);

          // (x || FALSE) => x
          // (x && TRUE) => x
          if ((type == Token.OR && !rval) || (type == Token.AND && rval)) {
            replacement = left;
          } else if (!mayHaveSideEffects(left)) {
            replacement = right;
          } else {
            // expr_with_sideeffects || true  =>  expr_with_sideeffects, true
            // expr_with_sideeffects && false  =>  expr_with_sideeffects, false
            n.detachChildren();
            replacement = IR.comma(left, right);
          }

          if (replacement != null) {
            n.detachChildren();
            parent.replaceChild(n, replacement);
            compiler.reportChangeToEnclosingScope(parent);
            return replacement;
          }
        }
        return n;
      }

      case HOOK: {
        Node condition = n.getFirstChild();
        Node trueNode = n.getSecondChild();
        Node falseNode = n.getLastChild();

        // Because the expression is in a boolean context minimize
        // the result children, this can't be done in the general case.
        // The condition is handled in the general case in #optimizeSubtree
        trueNode = performConditionSubstitutions(trueNode);
        falseNode = performConditionSubstitutions(falseNode);

        // Handle five cases:
        //   x ? true : false --> x
        //   x ? false : true --> !x
        //   x ? true : y     --> x || y
        //   x ? y : false    --> x && y

        //   Only when x is NAME, hence x does not have side effects
        //   x ? x : y        --> x || y
        Node replacement = null;
        TernaryValue trueNodeVal = NodeUtil.getPureBooleanValue(trueNode);
        TernaryValue falseNodeVal = NodeUtil.getPureBooleanValue(falseNode);
        if (trueNodeVal == TernaryValue.TRUE
            && falseNodeVal == TernaryValue.FALSE) {
          // Remove useless conditionals, keep the condition
          condition.detach();
          replacement = condition;
        } else if (trueNodeVal == TernaryValue.FALSE
            && falseNodeVal == TernaryValue.TRUE) {
          // Remove useless conditionals, keep the condition
          condition.detach();
          replacement = IR.not(condition);
        } else if (trueNodeVal == TernaryValue.TRUE) {
          // Remove useless true case.
          n.detachChildren();
          replacement = IR.or(condition, falseNode);
        } else if (falseNodeVal == TernaryValue.FALSE) {
          // Remove useless false case
          n.detachChildren();
          replacement = IR.and(condition, trueNode);
        } else if (!mayHaveSideEffects(condition)
            && !mayHaveSideEffects(trueNode)
            && condition.isEquivalentTo(trueNode)) {
          // Remove redundant condition
          n.detachChildren();
          replacement = IR.or(trueNode, falseNode);
        }

        if (replacement != null) {
          parent.replaceChild(n, replacement);
          compiler.reportChangeToEnclosingScope(replacement);
          n = replacement;
        }

        return n;
      }

      default:
        // while(true) --> while(1)
        TernaryValue nVal = NodeUtil.getPureBooleanValue(n);
        if (nVal != TernaryValue.UNKNOWN) {
          boolean result = nVal.toBoolean(true);
          int equivalentResult = result ? 1 : 0;
          return maybeReplaceChildWithNumber(n, parent, equivalentResult);
        }
        // We can't do anything else currently.
        return n;
    }
  }

  /**
   * Replaces a node with a number node if the new number node is not equivalent
   * to the current node.
   *
   * Returns the replacement for n if it was replaced, otherwise returns n.
   */
  private Node maybeReplaceChildWithNumber(Node n, Node parent, int num) {
    Node newNode = IR.number(num);
    if (!newNode.isEquivalentTo(n)) {
      parent.replaceChild(n, newNode);
      compiler.reportChangeToEnclosingScope(newNode);
      NodeUtil.markFunctionsDeleted(n, compiler);

      return newNode;
    }

    return n;
  }
}
