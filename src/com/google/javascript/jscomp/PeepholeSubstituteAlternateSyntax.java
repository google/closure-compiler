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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CodingConvention.Bind;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

import java.util.regex.Pattern;

/**
 * A peephole optimization that minimizes code by simplifying conditional
 * expressions, replacing IFs with HOOKs, replacing object constructors
 * with literals, and simplifying returns.
 *
 */
class PeepholeSubstituteAlternateSyntax
  extends AbstractPeepholeOptimization {

  private static final int AND_PRECEDENCE = NodeUtil.precedence(Token.AND);
  private static final int OR_PRECEDENCE = NodeUtil.precedence(Token.OR);
  private static final int NOT_PRECEDENCE = NodeUtil.precedence(Token.NOT);
  private static final CodeGenerator REGEXP_ESCAPER =
      CodeGenerator.forCostEstimation(
          null /* blow up if we try to produce code */);

  private final boolean late;

  private final int STRING_SPLIT_OVERHEAD = ".split('.')".length();

  static final DiagnosticType INVALID_REGULAR_EXPRESSION_FLAGS =
    DiagnosticType.warning(
        "JSC_INVALID_REGULAR_EXPRESSION_FLAGS",
        "Invalid flags to RegExp constructor: {0}");

  static final Predicate<Node> DONT_TRAVERSE_FUNCTIONS_PREDICATE
      = new Predicate<Node>() {
    @Override
    public boolean apply(Node input) {
      return !input.isFunction();
    }
  };

  /**
   * @param late When late is false, this mean we are currently running before
   * most of the other optimizations. In this case we would avoid optimizations
   * that would make the code harder to analyze (such as using string splitting,
   * merging statements with commas, etc). When this is true, we would
   * do anything to minimize for size.
   */
  PeepholeSubstituteAlternateSyntax(boolean late) {
    this.late = late;
  }

  /**
   * Tries apply our various peephole minimizations on the passed in node.
   */
  @Override
  @SuppressWarnings("fallthrough")
  public Node optimizeSubtree(Node node) {
    switch(node.getType()) {
      case Token.RETURN: {
        Node result = tryRemoveRedundantExit(node);
        if (result != node) {
          return result;
        }
        result = tryReplaceExitWithBreak(node);
        if (result != node) {
          return result;
        }
        return tryReduceReturn(node);
      }

      case Token.THROW: {
        Node result = tryRemoveRedundantExit(node);
        if (result != node) {
          return result;
        }
        return tryReplaceExitWithBreak(node);
      }

      // TODO(johnlenz): Maybe remove redundant BREAK and CONTINUE. Overlaps
      // with MinimizeExitPoints.

      case Token.NOT:
        tryMinimizeCondition(node.getFirstChild());
        return tryMinimizeNot(node);

      case Token.IF:
        tryMinimizeCondition(node.getFirstChild());
        return tryMinimizeIf(node);

      case Token.EXPR_RESULT:
        tryMinimizeCondition(node.getFirstChild());
        return node;

      case Token.HOOK:
        tryMinimizeCondition(node.getFirstChild());
        return node;

      case Token.WHILE:
      case Token.DO:
        tryMinimizeCondition(NodeUtil.getConditionExpression(node));
        return node;

      case Token.FOR:
        if (!NodeUtil.isForIn(node)) {
          tryJoinForCondition(node);
          tryMinimizeCondition(NodeUtil.getConditionExpression(node));
        }
        return node;

      case Token.TRUE:
      case Token.FALSE:
        return reduceTrueFalse(node);

      case Token.NEW:
        node = tryFoldStandardConstructors(node);
        if (!node.isCall()) {
          return node;
        }
        // Fall through on purpose because tryFoldStandardConstructors() may
        // convert a NEW node into a CALL node
      case Token.CALL:
        Node result =  tryFoldLiteralConstructor(node);
        if (result == node) {
          result = tryFoldSimpleFunctionCall(node);
          if (result == node) {
            result = tryFoldImmediateCallToBoundFunction(node);
          }
        }
        return result;

      case Token.COMMA:
        return trySplitComma(node);

      case Token.NAME:
        return tryReplaceUndefined(node);

      case Token.BLOCK:
        return tryReplaceIf(node);

      case Token.ARRAYLIT:
        return tryMinimizeArrayLiteral(node);

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
      Node maybeBreak = maybeIf.getChildAtIndex(1).getFirstChild();
      if (maybeBreak != null && maybeBreak.isBreak()
          && !maybeBreak.hasChildren()) {

        // Preserve the IF ELSE expression is there is one.
        if (maybeIf.getChildCount() == 3) {
          block.replaceChild(maybeIf,
              maybeIf.getLastChild().detachFromParent());
        } else {
          block.removeFirstChild();
        }

        Node ifCondition = maybeIf.removeFirstChild();
        Node fixedIfCondition = IR.not(ifCondition)
            .srcref(ifCondition);

        // OK, join the IF expression with the FOR expression
        Node forCondition = NodeUtil.getConditionExpression(n);
        if (forCondition.isEmpty()) {
          n.replaceChild(forCondition, fixedIfCondition);
        } else {
          Node replacement = new Node(Token.AND);
          n.replaceChild(forCondition, replacement);
          replacement.addChildToBack(forCondition);
          replacement.addChildToBack(fixedIfCondition);
        }

        reportCodeChange();
      }
    }
  }

  private Node tryFoldSimpleFunctionCall(Node n) {
    Preconditions.checkState(n.isCall());
    Node callTarget = n.getFirstChild();
    if (callTarget != null && callTarget.isName() &&
          callTarget.getString().equals("String")) {
      // Fold String(a) to '' + (a) on immutable literals,
      // which allows further optimizations
      //
      // We can't do this in the general case, because String(a) has
      // slightly different semantics than '' + (a). See
      // http://code.google.com/p/closure-compiler/issues/detail?id=759
      Node value = callTarget.getNext();
      if (value != null && value.getNext() == null &&
          NodeUtil.isImmutableValue(value)) {
        Node addition = IR.add(
            IR.string("").srcref(callTarget),
            value.detachFromParent());
        n.getParent().replaceChild(n, addition);
        reportCodeChange();
        return addition;
      }
    }
    return n;
  }

  private Node tryFoldImmediateCallToBoundFunction(Node n) {
    // Rewriting "(fn.bind(a,b))()" to "fn.call(a,b)" makes it inlinable
    Preconditions.checkState(n.isCall());
    Node callTarget = n.getFirstChild();
    Bind bind = getCodingConvention().describeFunctionBind(callTarget, false);
    if (bind != null) {
      // replace the call target
      bind.target.detachFromParent();
      n.replaceChild(callTarget, bind.target);
      callTarget = bind.target;

      // push the parameters
      addParameterAfter(bind.parameters, callTarget);

      // add the this value before the parameters if necessary
      if (bind.thisValue != null && !NodeUtil.isUndefined(bind.thisValue)) {
        // rewrite from "fn(a, b)" to "fn.call(thisValue, a, b)"
        Node newCallTarget = IR.getprop(
            callTarget.cloneTree(),
            IR.string("call").srcref(callTarget));
        n.replaceChild(callTarget, newCallTarget);
        n.addChildAfter(bind.thisValue.cloneTree(), newCallTarget);
        n.putBooleanProp(Node.FREE_CALL, false);
      } else {
        n.putBooleanProp(Node.FREE_CALL, true);
      }
      reportCodeChange();
    }
    return n;
  }

  private void addParameterAfter(Node parameterList, Node after) {
    if (parameterList != null) {
      // push the last parameter to the head of the list first.
      addParameterAfter(parameterList.getNext(), after);
      after.getParent().addChildAfter(parameterList.cloneTree(), after);
    }
  }

  private Node trySplitComma(Node n) {
    if (late) {
      return n;
    }
    Node parent = n.getParent();
    Node left = n.getFirstChild();
    Node right = n.getLastChild();

    if (parent.isExprResult()
        && !parent.getParent().isLabel()) {
      // split comma
      n.detachChildren();
      // Replace the original expression with the left operand.
      parent.replaceChild(n, left);
      // Add the right expression afterward.
      Node newStatement = IR.exprResult(right);
      newStatement.copyInformationFrom(n);

      //This modifies outside the subtree, which is not
      //desirable in a peephole optimization.
      parent.getParent().addChildAfter(newStatement, parent);
      reportCodeChange();
      return left;
    } else {
      return n;
    }
  }

  /**
   * Use "return x?1:2;" in place of "if(x)return 1;return 2;"
   */
  private Node tryReplaceIf(Node n) {

    for (Node child = n.getFirstChild();
         child != null; child = child.getNext()){
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
            child.detachFromParent();
            child.detachChildren();
            Node newCond = new Node(Token.OR, cond);
            nextNode.replaceChild(nextCond, newCond);
            newCond.addChildToBack(nextCond);
            reportCodeChange();
          } else if (nextElse != null
              && thenBranch.isEquivalentToTyped(nextElse)) {
            // Transform
            //   if (x) return 1; if (y) foo() else return 1;
            // to
            //   if (!x&&y) foo() else return 1;
            child.detachFromParent();
            child.detachChildren();
            Node newCond = new Node(Token.AND,
                IR.not(cond).srcref(cond));
            nextNode.replaceChild(nextCond, newCond);
            newCond.addChildToBack(nextCond);
            reportCodeChange();
          }
        } else if (nextNode != null && elseBranch == null &&
            isReturnBlock(thenBranch) && isReturnExpression(nextNode)) {
          Node thenExpr = null;
          // if(x)return; return 1 -> return x?void 0:1
          if (isReturnExpressBlock(thenBranch)) {
            thenExpr = getBlockReturnExpression(thenBranch);
            thenExpr.detachFromParent();
          } else {
            thenExpr = NodeUtil.newUndefinedNode(child);
          }

          Node elseExpr = nextNode.getFirstChild();

          cond.detachFromParent();
          elseExpr.detachFromParent();

          Node returnNode = IR.returnNode(
                                IR.hook(cond, thenExpr, elseExpr)
                                    .srcref(child));
          n.replaceChild(child, returnNode);
          n.removeChild(nextNode);
          reportCodeChange();
        } else if (elseBranch != null && statementMustExitParent(thenBranch)) {
          child.removeChild(elseBranch);
          n.addChildAfter(elseBranch, child);
          reportCodeChange();
        }
      }
    }
    return n;
  }

  private boolean statementMustExitParent(Node n) {
    switch (n.getType()) {
      case Token.THROW:
      case Token.RETURN:
        return true;
      case Token.BLOCK:
        if (n.hasChildren()) {
          Node child = n.getLastChild();
          return statementMustExitParent(child);
        }
        return false;
      // TODO(johnlenz): handle TRY/FINALLY
      case Token.FUNCTION:
      default:
        return false;
    }
  }

  /**
   * Use "void 0" in place of "undefined"
   */
  private Node tryReplaceUndefined(Node n) {
    // TODO(johnlenz): consider doing this as a normalization.
    if (isASTNormalized()
        && NodeUtil.isUndefined(n)
        && !NodeUtil.isLValue(n)) {
      Node replacement = NodeUtil.newUndefinedNode(n);
      n.getParent().replaceChild(n, replacement);
      reportCodeChange();
      return replacement;
    }
    return n;
  }

  /**
   * Reduce "return undefined" or "return void 0" to simply "return".
   *
   * @return The original node, maybe simplified.
   */
  private Node tryReduceReturn(Node n) {
    Node result = n.getFirstChild();

    if (result != null) {
      switch (result.getType()) {
        case Token.VOID:
          Node operand = result.getFirstChild();
          if (!mayHaveSideEffects(operand)) {
            n.removeFirstChild();
            reportCodeChange();
          }
          break;
        case Token.NAME:
          String name = result.getString();
          if (name.equals("undefined")) {
            n.removeFirstChild();
            reportCodeChange();
          }
          break;
      }
    }

    return n;
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
    for (;!ControlFlowAnalysis.isBreakTarget(breakTarget, null /* no label */);
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
      n.getParent().replaceChild(n, replacement);
      this.reportCodeChange();
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
      n.detachFromParent();
      reportCodeChange();
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
  Node skipFinallyNodes(Node n) {
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

  boolean isExceptionPossible(Node n) {
    // TODO(johnlenz): maybe use ControlFlowAnalysis.mayThrowException?
    Preconditions.checkState(n.isReturn()
        || n.isThrow());
    return n.isThrow()
        || (n.hasChildren()
            && !NodeUtil.isLiteralValue(n.getLastChild(), true));
  }

  Node getExceptionHandler(Node n) {
    return ControlFlowAnalysis.getExceptionHandler(n);
  }

  /**
   * Try to minimize NOT nodes such as !(x==y).
   *
   * Returns the replacement for n or the original if no change was made
   */
  private Node tryMinimizeNot(Node n) {
    Node parent = n.getParent();

    Node notChild = n.getFirstChild();
    // negative operator of the current one : == -> != for instance.
    int complementOperator;
    switch (notChild.getType()) {
      case Token.EQ:
        complementOperator = Token.NE;
        break;
      case Token.NE:
        complementOperator = Token.EQ;
        break;
      case Token.SHEQ:
        complementOperator = Token.SHNE;
        break;
      case Token.SHNE:
        complementOperator = Token.SHEQ;
        break;
      // GT, GE, LT, LE are not handled in this because !(x<NaN) != x>=NaN.
      default:
        return n;
    }
    Node newOperator = n.removeFirstChild();
    newOperator.setType(complementOperator);
    parent.replaceChild(n, newOperator);
    reportCodeChange();
    return newOperator;
  }

  /**
   * Try turning IF nodes into smaller HOOKs
   *
   * Returns the replacement for n or the original if no replacement was
   * necessary.
   */
  private Node tryMinimizeIf(Node n) {

    Node parent = n.getParent();

    Node cond = n.getFirstChild();

    /* If the condition is a literal, we'll let other
     * optimizations try to remove useless code.
     */
    if (NodeUtil.isLiteralValue(cond, true)) {
      return n;
    }

    Node thenBranch = cond.getNext();
    Node elseBranch = thenBranch.getNext();

    if (elseBranch == null) {
      if (isFoldableExpressBlock(thenBranch)) {
        Node expr = getBlockExpression(thenBranch);
        if (!late && isPropertyAssignmentInExpression(expr)) {
          // Keep opportunities for CollapseProperties such as
          // a.longIdentifier || a.longIdentifier = ... -> var a = ...;
          // until CollapseProperties has been run.
          return n;
        }

        if (cond.isNot()) {
          // if(!x)bar(); -> x||bar();
          if (isLowerPrecedenceInExpression(cond, OR_PRECEDENCE) &&
              isLowerPrecedenceInExpression(expr.getFirstChild(),
                  OR_PRECEDENCE)) {
            // It's not okay to add two sets of parentheses.
            return n;
          }

          Node or = IR.or(
              cond.removeFirstChild(),
              expr.removeFirstChild()).srcref(n);
          Node newExpr = NodeUtil.newExpr(or);
          parent.replaceChild(n, newExpr);
          reportCodeChange();

          return newExpr;
        }

        // if(x)foo(); -> x&&foo();
        if (isLowerPrecedenceInExpression(cond, AND_PRECEDENCE) &&
            isLowerPrecedenceInExpression(expr.getFirstChild(),
                AND_PRECEDENCE)) {
          // One additional set of parentheses is worth the change even if
          // there is no immediate code size win. However, two extra pair of
          // {}, we would have to think twice. (unless we know for sure the
          // we can further optimize its parent.
          return n;
        }

        n.removeChild(cond);
        Node and = IR.and(cond, expr.removeFirstChild()).srcref(n);
        Node newExpr = NodeUtil.newExpr(and);
        parent.replaceChild(n, newExpr);
        reportCodeChange();

        return newExpr;
      } else {

        // Try to combine two IF-ELSE
        if (NodeUtil.isStatementBlock(thenBranch) &&
            thenBranch.hasOneChild()) {
          Node innerIf = thenBranch.getFirstChild();

          if (innerIf.isIf()) {
            Node innerCond = innerIf.getFirstChild();
            Node innerThenBranch = innerCond.getNext();
            Node innerElseBranch = innerThenBranch.getNext();

            if (innerElseBranch == null &&
                 !(isLowerPrecedenceInExpression(cond, AND_PRECEDENCE) &&
                   isLowerPrecedenceInExpression(innerCond, AND_PRECEDENCE))) {
              n.detachChildren();
              n.addChildToBack(
                  IR.and(
                      cond,
                      innerCond.detachFromParent())
                      .srcref(cond));
              n.addChildrenToBack(innerThenBranch.detachFromParent());
              reportCodeChange();
              // Not worth trying to fold the current IF-ELSE into && because
              // the inner IF-ELSE wasn't able to be folded into && anyways.
              return n;
            }
          }
        }
      }

      return n;
    }

    /* TODO(dcc) This modifies the siblings of n, which is undesirable for a
     * peephole optimization. This should probably get moved to another pass.
     */
    tryRemoveRepeatedStatements(n);

    // if(!x)foo();else bar(); -> if(x)bar();else foo();
    // An additional set of curly braces isn't worth it.
    if (cond.isNot() && !consumesDanglingElse(elseBranch)) {
      n.replaceChild(cond, cond.removeFirstChild());
      n.removeChild(thenBranch);
      n.addChildToBack(thenBranch);
      reportCodeChange();
      return n;
    }

    // if(x)return 1;else return 2; -> return x?1:2;
    if (isReturnExpressBlock(thenBranch) && isReturnExpressBlock(elseBranch)) {
      Node thenExpr = getBlockReturnExpression(thenBranch);
      Node elseExpr = getBlockReturnExpression(elseBranch);
      n.removeChild(cond);
      thenExpr.detachFromParent();
      elseExpr.detachFromParent();

      // note - we ignore any cases with "return;", technically this
      // can be converted to "return undefined;" or some variant, but
      // that does not help code size.
      Node returnNode = IR.returnNode(
                            IR.hook(cond, thenExpr, elseExpr)
                                .srcref(n));
      parent.replaceChild(n, returnNode);
      reportCodeChange();
      return returnNode;
    }

    boolean thenBranchIsExpressionBlock = isFoldableExpressBlock(thenBranch);
    boolean elseBranchIsExpressionBlock = isFoldableExpressBlock(elseBranch);

    if (thenBranchIsExpressionBlock && elseBranchIsExpressionBlock) {
      Node thenOp = getBlockExpression(thenBranch).getFirstChild();
      Node elseOp = getBlockExpression(elseBranch).getFirstChild();
      if (thenOp.getType() == elseOp.getType()) {
        // if(x)a=1;else a=2; -> a=x?1:2;
        if (NodeUtil.isAssignmentOp(thenOp)) {
          Node lhs = thenOp.getFirstChild();
          if (areNodesEqualForInlining(lhs, elseOp.getFirstChild()) &&
              // if LHS has side effects, don't proceed [since the optimization
              // evaluates LHS before cond]
              // NOTE - there are some circumstances where we can
              // proceed even if there are side effects...
              !mayEffectMutableState(lhs)) {

            n.removeChild(cond);
            Node assignName = thenOp.removeFirstChild();
            Node thenExpr = thenOp.removeFirstChild();
            Node elseExpr = elseOp.getLastChild();
            elseOp.removeChild(elseExpr);

            Node hookNode = IR.hook(cond, thenExpr, elseExpr).srcref(n);
            Node assign = new Node(thenOp.getType(), assignName, hookNode)
                              .srcref(thenOp);
            Node expr = NodeUtil.newExpr(assign);
            parent.replaceChild(n, expr);
            reportCodeChange();

            return expr;
          }
        }
      }
      // if(x)foo();else bar(); -> x?foo():bar()
      n.removeChild(cond);
      thenOp.detachFromParent();
      elseOp.detachFromParent();
      Node expr = IR.exprResult(
          IR.hook(cond, thenOp, elseOp).srcref(n));
      parent.replaceChild(n, expr);
      reportCodeChange();
      return expr;
    }

    boolean thenBranchIsVar = isVarBlock(thenBranch);
    boolean elseBranchIsVar = isVarBlock(elseBranch);

    // if(x)var y=1;else y=2  ->  var y=x?1:2
    if (thenBranchIsVar && elseBranchIsExpressionBlock &&
        getBlockExpression(elseBranch).getFirstChild().isAssign()) {

      Node var = getBlockVar(thenBranch);
      Node elseAssign = getBlockExpression(elseBranch).getFirstChild();

      Node name1 = var.getFirstChild();
      Node maybeName2 = elseAssign.getFirstChild();

      if (name1.hasChildren()
          && maybeName2.isName()
          && name1.getString().equals(maybeName2.getString())) {
        Node thenExpr = name1.removeChildren();
        Node elseExpr = elseAssign.getLastChild().detachFromParent();
        cond.detachFromParent();
        Node hookNode = IR.hook(cond, thenExpr, elseExpr)
                            .srcref(n);
        var.detachFromParent();
        name1.addChildrenToBack(hookNode);
        parent.replaceChild(n, var);
        reportCodeChange();
        return var;
      }

    // if(x)y=1;else var y=2  ->  var y=x?1:2
    } else if (elseBranchIsVar && thenBranchIsExpressionBlock &&
        getBlockExpression(thenBranch).getFirstChild().isAssign()) {

      Node var = getBlockVar(elseBranch);
      Node thenAssign = getBlockExpression(thenBranch).getFirstChild();

      Node maybeName1 = thenAssign.getFirstChild();
      Node name2 = var.getFirstChild();

      if (name2.hasChildren()
          && maybeName1.isName()
          && maybeName1.getString().equals(name2.getString())) {
        Node thenExpr = thenAssign.getLastChild().detachFromParent();
        Node elseExpr = name2.removeChildren();
        cond.detachFromParent();
        Node hookNode = IR.hook(cond, thenExpr, elseExpr)
                            .srcref(n);
        var.detachFromParent();
        name2.addChildrenToBack(hookNode);
        parent.replaceChild(n, var);
        reportCodeChange();

        return var;
      }
    }

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
    Preconditions.checkState(n.isIf());

    Node parent = n.getParent();
    if (!NodeUtil.isStatementBlock(parent)) {
      // If the immediate parent is something like a label, we
      // can't move the statement, so bail.
      return;
    }

    Node cond = n.getFirstChild();
    Node trueBranch = cond.getNext();
    Node falseBranch = trueBranch.getNext();
    Preconditions.checkNotNull(trueBranch);
    Preconditions.checkNotNull(falseBranch);

    while (true) {
      Node lastTrue = trueBranch.getLastChild();
      Node lastFalse = falseBranch.getLastChild();
      if (lastTrue == null || lastFalse == null
          || !areNodesEqualForInlining(lastTrue, lastFalse)) {
        break;
      }
      lastTrue.detachFromParent();
      lastFalse.detachFromParent();
      parent.addChildAfter(lastTrue, n);
      reportCodeChange();
    }
  }

  /**
   * @return Whether the node is a block with a single statement that is
   *     an expression.
   */
  private boolean isFoldableExpressBlock(Node n) {
    if (n.isBlock()) {
      if (n.hasOneChild()) {
        Node maybeExpr = n.getFirstChild();
        if (maybeExpr.isExprResult()) {
          // IE has a bug where event handlers behave differently when
          // their return value is used vs. when their return value is in
          // an EXPR_RESULT. It's pretty freaking weird. See:
          // http://code.google.com/p/closure-compiler/issues/detail?id=291
          // We try to detect this case, and not fold EXPR_RESULTs
          // into other expressions.
          if (maybeExpr.getFirstChild().isCall()) {
            Node calledFn = maybeExpr.getFirstChild().getFirstChild();

            // We only have to worry about methods with an implicit 'this'
            // param, or this doesn't happen.
            if (calledFn.isGetElem()) {
              return false;
            } else if (calledFn.isGetProp() &&
                       calledFn.getLastChild().getString().startsWith("on")) {
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
  private Node getBlockExpression(Node n) {
    Preconditions.checkState(isFoldableExpressBlock(n));
    return n.getFirstChild();
  }

  /**
   * @return Whether the node is a block with a single statement that is
   *     an return with or without an expression.
   */
  private boolean isReturnBlock(Node n) {
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
  private boolean isReturnExpressBlock(Node n) {
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
  private boolean isReturnExpression(Node n) {
    if (n.isReturn()) {
      return n.hasOneChild();
    }
    return false;
  }

  /**
   * @return The expression that is part of the return.
   */
  private Node getBlockReturnExpression(Node n) {
    Preconditions.checkState(isReturnExpressBlock(n));
    return n.getFirstChild().getFirstChild();
  }

  /**
   * @return Whether the node is a block with a single statement that is
   *     a VAR declaration of a single variable.
   */
  private boolean isVarBlock(Node n) {
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
  private Node getBlockVar(Node n) {
    Preconditions.checkState(isVarBlock(n));
    return n.getFirstChild();
  }

  /**
   * Does a statement consume a 'dangling else'? A statement consumes
   * a 'dangling else' if an 'else' token following the statement
   * would be considered by the parser to be part of the statement.
   */
  private boolean consumesDanglingElse(Node n) {
    while (true) {
      switch (n.getType()) {
        case Token.IF:
          if (n.getChildCount() < 3) {
            return true;
          }
          // This IF node has no else clause.
          n = n.getLastChild();
          continue;
        case Token.WITH:
        case Token.WHILE:
        case Token.FOR:
          n = n.getLastChild();
          continue;
        default:
          return false;
      }
    }
  }

  /**
   * Does the expression contain an operator with lower precedence than
   * the argument?
   */
  private boolean isLowerPrecedenceInExpression(Node n,
      final int precedence) {
    Predicate<Node> isLowerPrecedencePredicate = new Predicate<Node>() {
      @Override
      public boolean apply(Node input) {
        return NodeUtil.precedence(input.getType()) < precedence;
      }
    };

    return NodeUtil.has(n, isLowerPrecedencePredicate,
        DONT_TRAVERSE_FUNCTIONS_PREDICATE);
  }

  /**
   * Whether the node type has lower precedence than "precedence"
   */
  private boolean isLowerPrecedence(Node n, final int precedence) {
    return NodeUtil.precedence(n.getType()) < precedence;
  }

  /**
   * Whether the node type has higher precedence than "precedence"
   */
  private boolean isHigherPrecedence(Node n, final int precedence) {
    return NodeUtil.precedence(n.getType()) > precedence;
  }
  /**
   * Does the expression contain a property assignment?
   */
  private boolean isPropertyAssignmentInExpression(Node n) {
    Predicate<Node> isPropertyAssignmentInExpressionPredicate =
        new Predicate<Node>() {
      @Override
      public boolean apply(Node input) {
        return (input.isGetProp() &&
            input.getParent().isAssign());
      }
    };

    return NodeUtil.has(n, isPropertyAssignmentInExpressionPredicate,
        DONT_TRAVERSE_FUNCTIONS_PREDICATE);
  }

  /**
   * Try to minimize conditions expressions, as there are additional
   * assumptions that can be made when it is known that the final result
   * is a boolean.
   *
   * The following transformations are done recursively:
   *   !(x||y) --> !x&&!y
   *   !(x&&y) --> !x||!y
   *   !!x     --> x
   * Thus:
   *   !(x&&!y) --> !x||!!y --> !x||y
   *
   *   Returns the replacement for n, or the original if no change was made
   */
  private Node tryMinimizeCondition(Node n) {
    Node parent = n.getParent();

    switch (n.getType()) {
      case Token.NOT:
        Node first = n.getFirstChild();
        switch (first.getType()) {
          case Token.NOT: {
              Node newRoot = first.removeFirstChild();
              parent.replaceChild(n, newRoot);
              reportCodeChange();
              // No need to traverse, tryMinimizeCondition is called on the
              // NOT children are handled below.
              return newRoot;
            }
          case Token.AND:
          case Token.OR: {
              // !(!x && !y) --> x || y
              // !(!x || !y) --> x && y
              // !(!x && y) --> x || !y
              // !(!x || y) --> x && !y
              // !(x && !y) --> !x || y
              // !(x || !y) --> !x && y
              // !(x && y) --> !x || !y
              // !(x || y) --> !x && !y
              Node leftParent = first.getFirstChild();
              Node rightParent = first.getLastChild();
              Node left, right;

              // Check special case when such transformation cannot reduce
              // due to the added ()
              // It only occurs when both of expressions are not NOT expressions
              if (!leftParent.isNot()
                  && !rightParent.isNot()) {
                // If an expression has higher precedence than && or ||,
                // but lower precedence than NOT, an additional () is needed
                // Thus we do not preceed
                int op_precedence = NodeUtil.precedence(first.getType());
                if ((isLowerPrecedence(leftParent, NOT_PRECEDENCE)
                    && isHigherPrecedence(leftParent, op_precedence))
                    || (isLowerPrecedence(rightParent, NOT_PRECEDENCE)
                    && isHigherPrecedence(rightParent, op_precedence))) {
                  return n;
                }
              }

              if (leftParent.isNot()) {
                left = leftParent.removeFirstChild();
              } else {
                leftParent.detachFromParent();
                left = IR.not(leftParent).srcref(leftParent);
              }
              if (rightParent.isNot()) {
                right = rightParent.removeFirstChild();
              } else {
                rightParent.detachFromParent();
                right = IR.not(rightParent).srcref(rightParent);
              }

              int newOp = (first.isAnd()) ? Token.OR : Token.AND;
              Node newRoot = new Node(newOp, left, right);
              parent.replaceChild(n, newRoot);
              reportCodeChange();
              // No need to traverse, tryMinimizeCondition is called on the
              // AND and OR children below.
              return newRoot;
            }

           default:
             TernaryValue nVal = NodeUtil.getPureBooleanValue(first);
             if (nVal != TernaryValue.UNKNOWN) {
               boolean result = nVal.not().toBoolean(true);
               int equivalentResult = result ? 1 : 0;
               return maybeReplaceChildWithNumber(n, parent, equivalentResult);
             }
        }
        // No need to traverse, tryMinimizeCondition is called on the NOT
        // children in the general case in the main post-order traversal.
        return n;

      case Token.OR:
      case Token.AND: {
        Node left = n.getFirstChild();
        Node right = n.getLastChild();

        // Because the expression is in a boolean context minimize
        // the children, this can't be done in the general case.
        left = tryMinimizeCondition(left);
        right = tryMinimizeCondition(right);

        // Remove useless conditionals
        // Handle four cases:
        //   x || false --> x
        //   x || true  --> true
        //   x && true --> x
        //   x && false  --> false
        TernaryValue rightVal = NodeUtil.getPureBooleanValue(right);
        if (NodeUtil.getPureBooleanValue(right) != TernaryValue.UNKNOWN) {
          int type = n.getType();
          Node replacement = null;
          boolean rval = rightVal.toBoolean(true);

          // (x || FALSE) => x
          // (x && TRUE) => x
          if (type == Token.OR && !rval ||
              type == Token.AND && rval) {
            replacement = left;
          } else if (!mayHaveSideEffects(left)) {
            replacement = right;
          }

          if (replacement != null) {
            n.detachChildren();
            parent.replaceChild(n, replacement);
            reportCodeChange();
            return replacement;
          }
        }
        return n;
      }

      case Token.HOOK: {
        Node condition = n.getFirstChild();
        Node trueNode = n.getFirstChild().getNext();
        Node falseNode = n.getLastChild();

        // Because the expression is in a boolean context minimize
        // the result children, this can't be done in the general case.
        // The condition is handled in the general case in #optimizeSubtree
        trueNode = tryMinimizeCondition(trueNode);
        falseNode = tryMinimizeCondition(falseNode);

        // Handle four cases:
        //   x ? true : false --> x
        //   x ? false : true --> !x
        //   x ? true : y     --> x || y
        //   x ? y : false    --> x && y
        Node replacement = null;
        TernaryValue trueNodeVal = NodeUtil.getPureBooleanValue(trueNode);
        TernaryValue falseNodeVal = NodeUtil.getPureBooleanValue(falseNode);
        if (trueNodeVal == TernaryValue.TRUE
            && falseNodeVal == TernaryValue.FALSE) {
          // Remove useless conditionals, keep the condition
          condition.detachFromParent();
          replacement = condition;
        } else if (trueNodeVal == TernaryValue.FALSE
            && falseNodeVal == TernaryValue.TRUE) {
          // Remove useless conditionals, keep the condition
          condition.detachFromParent();
          replacement = IR.not(condition);
        } else if (trueNodeVal == TernaryValue.TRUE) {
          // Remove useless true case.
          n.detachChildren();
          replacement = IR.or(condition, falseNode);
        } else if (falseNodeVal == TernaryValue.FALSE) {
          // Remove useless false case
          n.detachChildren();
          replacement = IR.and(condition, trueNode);
        }

        if (replacement != null) {
          parent.replaceChild(n, replacement);
          n = replacement;
          reportCodeChange();
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
      reportCodeChange();

      return newNode;
    }

    return n;
  }

  private static final ImmutableSet<String> STANDARD_OBJECT_CONSTRUCTORS =
    // String, Number, and Boolean functions return non-object types, whereas
    // new String, new Number, and new Boolean return object types, so don't
    // include them here.
    ImmutableSet.of(
      "Object",
      "Array",
      "RegExp",
      "Error"
      );

  /**
   * Fold "new Object()" to "Object()".
   */
  private Node tryFoldStandardConstructors(Node n) {
    Preconditions.checkState(n.isNew());

    // If name normalization has been run then we know that
    // new Object() does in fact refer to what we think it is
    // and not some custom-defined Object().
    if (isASTNormalized()) {
      if (n.getFirstChild().isName()) {
        String className = n.getFirstChild().getString();
        if (STANDARD_OBJECT_CONSTRUCTORS.contains(className)) {
          n.setType(Token.CALL);
          n.putBooleanProp(Node.FREE_CALL, true);
          reportCodeChange();
        }
      }
    }

    return n;
  }

  /**
   * Replaces a new Array or Object node with an object literal, unless the
   * call to Array or Object is to a local function with the same name.
   */
  private Node tryFoldLiteralConstructor(Node n) {
    Preconditions.checkArgument(n.isCall()
        || n.isNew());

    Node constructorNameNode = n.getFirstChild();

    Node newLiteralNode = null;

    // We require the AST to be normalized to ensure that, say,
    // Object() really refers to the built-in Object constructor
    // and not a user-defined constructor with the same name.

    if (isASTNormalized() && Token.NAME == constructorNameNode.getType()) {

      String className = constructorNameNode.getString();

      if ("RegExp".equals(className)) {
        // "RegExp("boo", "g")" --> /boo/g
        return tryFoldRegularExpressionConstructor(n);
      } else {
        boolean constructorHasArgs = constructorNameNode.getNext() != null;

        if ("Object".equals(className) && !constructorHasArgs) {
          // "Object()" --> "{}"
          newLiteralNode = IR.objectlit();
        } else if ("Array".equals(className)) {
          // "Array(arg0, arg1, ...)" --> "[arg0, arg1, ...]"
          Node arg0 = constructorNameNode.getNext();
          FoldArrayAction action = isSafeToFoldArrayConstructor(arg0);

          if (action == FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS ||
              action == FoldArrayAction.SAFE_TO_FOLD_WITHOUT_ARGS) {
            newLiteralNode = IR.arraylit();
            n.removeChildren();
            if (action == FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS) {
              newLiteralNode.addChildrenToFront(arg0);
            }
          }
        }

        if (newLiteralNode != null) {
          n.getParent().replaceChild(n, newLiteralNode);
          reportCodeChange();
          return newLiteralNode;
        }
      }
    }
    return n;
  }

  private static enum FoldArrayAction {
    NOT_SAFE_TO_FOLD, SAFE_TO_FOLD_WITH_ARGS, SAFE_TO_FOLD_WITHOUT_ARGS}

  /**
   * Checks if it is safe to fold Array() constructor into []. It can be
   * obviously done, if the initial constructor has either no arguments or
   * at least two. The remaining case may be unsafe since Array(number)
   * actually reserves memory for an empty array which contains number elements.
   */
  private FoldArrayAction isSafeToFoldArrayConstructor(Node arg) {
    FoldArrayAction action = FoldArrayAction.NOT_SAFE_TO_FOLD;

    if (arg == null) {
      action = FoldArrayAction.SAFE_TO_FOLD_WITHOUT_ARGS;
    } else if (arg.getNext() != null) {
      action = FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS;
    } else {
      switch (arg.getType()) {
        case Token.STRING:
          // "Array('a')" --> "['a']"
          action = FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS;
          break;
        case Token.NUMBER:
          // "Array(0)" --> "[]"
          if (arg.getDouble() == 0) {
            action = FoldArrayAction.SAFE_TO_FOLD_WITHOUT_ARGS;
          }
          break;
        case Token.ARRAYLIT:
          // "Array([args])" --> "[[args]]"
          action = FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS;
          break;
        default:
      }
    }
    return action;
  }

  private Node tryFoldRegularExpressionConstructor(Node n) {
    Node parent = n.getParent();
    Node constructor = n.getFirstChild();
    Node pattern = constructor.getNext();  // e.g.  ^foobar$
    Node flags = null != pattern ? pattern.getNext() : null;  // e.g. gi

    if (null == pattern || (null != flags && null != flags.getNext())) {
      // too few or too many arguments
      return n;
    }

    if (// is pattern folded
        pattern.isString()
        // make sure empty pattern doesn't fold to //
        && !"".equals(pattern.getString())

        // NOTE(nicksantos): Make sure that the regexp isn't longer than
        // 100 chars, or it blows up the regexp parser in Opera 9.2.
        && pattern.getString().length() < 100

        && (null == flags || flags.isString())
        // don't escape patterns with Unicode escapes since Safari behaves badly
        // (read can't parse or crashes) on regex literals with Unicode escapes
        && (isEcmaScript5OrGreater()
            || !containsUnicodeEscape(pattern.getString()))) {

      // Make sure that / is escaped, so that it will fit safely in /brackets/
      // and make sure that no LineTerminatorCharacters appear literally inside
      // the pattern.
      // pattern is a string value with \\ and similar already escaped
      pattern = makeForwardSlashBracketSafe(pattern);

      Node regexLiteral;
      if (null == flags || "".equals(flags.getString())) {
        // fold to /foobar/
        regexLiteral = IR.regexp(pattern);
      } else {
        // fold to /foobar/gi
        if (!areValidRegexpFlags(flags.getString())) {
          report(INVALID_REGULAR_EXPRESSION_FLAGS, flags);
          return n;
        }
        if (!areSafeFlagsToFold(flags.getString())) {
          return n;
        }
        n.removeChild(flags);
        regexLiteral = IR.regexp(pattern, flags);
      }

      parent.replaceChild(n, regexLiteral);
      reportCodeChange();
      return regexLiteral;
    }

    return n;
  }

  private Node reduceTrueFalse(Node n) {
    if (late) {
      Node not = IR.not(IR.number(n.isTrue() ? 0 : 1));
      not.copyInformationFromForTree(n);
      n.getParent().replaceChild(n, not);
      reportCodeChange();
      return not;
    }
    return n;
  }

  private Node tryMinimizeArrayLiteral(Node n) {
    boolean allStrings = true;
    for (Node cur = n.getFirstChild(); cur != null; cur = cur.getNext()) {
      if (!cur.isString()) {
        allStrings = false;
      }
    }

    if (allStrings) {
      return tryMinimizeStringArrayLiteral(n);
    } else {
      return n;
    }
  }

  private Node tryMinimizeStringArrayLiteral(Node n) {
    if(!late) {
      return n;
    }

    int numElements = n.getChildCount();
    // We save two bytes per element.
    int saving = numElements * 2 - STRING_SPLIT_OVERHEAD;
    if (saving <= 0) {
      return n;
    }

    String[] strings = new String[n.getChildCount()];
    int idx = 0;
    for (Node cur = n.getFirstChild(); cur != null; cur = cur.getNext()) {
      strings[idx++] = cur.getString();
    }

    // These delimiters are chars that appears a lot in the program therefore
    // probably have a small Huffman encoding.
    String delimiter = pickDelimiter(strings);
    if (delimiter != null) {
      String template = Joiner.on(delimiter).join(strings);
      Node call = IR.call(
          IR.getprop(
              IR.string(template),
              IR.string("split")),
          IR.string("" + delimiter));
      call.copyInformationFromForTree(n);
      n.getParent().replaceChild(n, call);
      reportCodeChange();
      return call;
    }
    return n;
  }

  /**
   * Find a delimiter that does not occur in the given strings
   * @param strings The strings that must be separated.
   * @return a delimiter string or null
   */
  private String pickDelimiter(String[] strings) {
    boolean allLength1 = true;
    for (String s : strings) {
      if (s.length() != 1) {
        allLength1 = false;
        break;
      }
    }

    if (allLength1) {
      return "";
    }

    String[] delimiters = new String[]{" ", ";", ",", "{", "}", null};
    int i = 0;
    NEXT_DELIMITER: for (;delimiters[i] != null; i++) {
      for (String cur : strings) {
        if (cur.contains(delimiters[i])) {
          continue NEXT_DELIMITER;
        }
      }
      break;
    }
    return delimiters[i];
  }

  private static final Pattern REGEXP_FLAGS_RE = Pattern.compile("^[gmi]*$");

  /**
   * are the given flags valid regular expression flags?
   * JavaScript recognizes several suffix flags for regular expressions,
   * 'g' - global replace, 'i' - case insensitive, 'm' - multi-line.
   * They are case insensitive, and JavaScript does not recognize the extended
   * syntax mode, single-line mode, or expression replacement mode from Perl 5.
   */
  private static boolean areValidRegexpFlags(String flags) {
    return REGEXP_FLAGS_RE.matcher(flags).matches();
  }

  /**
   * are the given flags safe to fold?
   * We don't fold the regular expression if global ('g') flag is on,
   * because in this case it isn't really a constant: its 'lastIndex'
   * property contains the state of last execution, so replacing
   * 'new RegExp('foobar','g')' with '/foobar/g' may change the behavior of
   * the program if the RegExp is used inside a loop, for example.
   * <p>
   * ECMAScript 5 explicitly disallows pooling of regular expression literals so
   * in ECMAScript 5, {@code /foo/g} and {@code new RegExp('foo', 'g')} are
   * equivalent.
   * From section 7.8.5:
   * "Then each time the literal is evaluated, a new object is created as if by
   * the expression new RegExp(Pattern, Flags) where RegExp is the standard
   * built-in constructor with that name."
   */
  private boolean areSafeFlagsToFold(String flags) {
    return isEcmaScript5OrGreater() || flags.indexOf('g') < 0;
  }

  /**
   * returns a string node that can safely be rendered inside /brackets/.
   */
  private static Node makeForwardSlashBracketSafe(Node n) {
    String s = n.getString();
    // sb contains everything in s[0:pos]
    StringBuilder sb = null;
    int pos = 0;
    boolean isEscaped = false, inCharset = false;
    for (int i = 0; i < s.length(); ++i) {
      char ch = s.charAt(i);
      switch (ch) {
        case '\\':
          isEscaped = !isEscaped;
          continue;
        case '/':
          // Escape a literal forward slash if it is not already escaped and is
          // not inside a character set.
          //     new RegExp('/') -> /\//
          // but the following do not need extra escaping
          //     new RegExp('\\/') -> /\//
          //     new RegExp('[/]') -> /[/]/
          if (!isEscaped && !inCharset) {
            if (null == sb) { sb = new StringBuilder(s.length() + 16); }
            sb.append(s, pos, i).append('\\');
            pos = i;
          }
          break;
        case '[':
          if (!isEscaped) {
            inCharset = true;
          }
          break;
        case ']':
          if (!isEscaped) {
            inCharset = false;
          }
          break;
        case '\r': case '\n': case '\u2028': case '\u2029':
          // LineTerminators cannot appear raw inside a regular
          // expression literal.
          // They can't appear legally in a quoted string, but when
          // the quoted string from
          //     new RegExp('\n')
          // reaches here, the quoting has been removed.
          // Requote just these code-points.
          if (null == sb) { sb = new StringBuilder(s.length() + 16); }
          if (isEscaped) {
            sb.append(s, pos, i - 1);
          } else {
            sb.append(s, pos, i);
          }
          switch (ch) {
            case '\r': sb.append("\\r"); break;
            case '\n': sb.append("\\n"); break;
            case '\u2028': sb.append("\\u2028"); break;
            case '\u2029': sb.append("\\u2029"); break;
          }
          pos = i + 1;
          break;
      }
      isEscaped = false;
    }

    if (null == sb) { return n.cloneTree(); }

    sb.append(s, pos, s.length());
    return IR.string(sb.toString()).srcref(n);
  }

  /**
   * true if the JavaScript string would contain a Unicode escape when written
   * out as the body of a regular expression literal.
   */
  static boolean containsUnicodeEscape(String s) {
    String esc = REGEXP_ESCAPER.regexpEscape(s);
    for (int i = -1; (i = esc.indexOf("\\u", i + 1)) >= 0;) {
      int nSlashes = 0;
      while (i - nSlashes > 0 && '\\' == esc.charAt(i - nSlashes - 1)) {
        ++nSlashes;
      }
      // if there are an even number of slashes before the \ u then it is a
      // Unicode literal.
      if (0 == (nSlashes & 1)) { return true; }
    }
    return false;
  }
}
