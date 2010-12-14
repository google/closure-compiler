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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
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
public class PeepholeSubstituteAlternateSyntax
  extends AbstractPeepholeOptimization {

  private static final int AND_PRECEDENCE = NodeUtil.precedence(Token.AND);
  private static final int OR_PRECEDENCE = NodeUtil.precedence(Token.OR);

  static final DiagnosticType INVALID_REGULAR_EXPRESSION_FLAGS =
    DiagnosticType.error(
        "JSC_INVALID_REGULAR_EXPRESSION_FLAGS",
        "Invalid flags to RegExp constructor: {0}");

  static final Predicate<Node> DONT_TRAVERSE_FUNCTIONS_PREDICATE
      = new Predicate<Node>() {
    @Override
    public boolean apply(Node input) {
      return input.getType() != Token.FUNCTION;
    }
  };

  /**
   * Tries apply our various peephole minimizations on the passed in node.
   */
  @Override
  @SuppressWarnings("fallthrough")
  public Node optimizeSubtree(Node node) {
    switch(node.getType()) {
      case Token.RETURN:
        return tryReduceReturn(node);

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
          tryMinimizeCondition(NodeUtil.getConditionExpression(node));
        }
        return node;

      case Token.NEW:
        node = tryFoldStandardConstructors(node);
        if (node.getType() != Token.CALL) {
          return node;
        }
        // Fall through on purpose because tryFoldStandardConstructors() may
        // convert a NEW node into a CALL node
      case Token.CALL:
        return tryFoldLiteralConstructor(node);

      default:
        return node; //Nothing changed
    }
  }

  /**
   * Reduce "return undefined" or "return void 0" to simply "return".
   *
   * Returns the replacement for n, or the original if no change was made.
   */
  private Node tryReduceReturn(Node n) {
    Node result = n.getFirstChild();

    boolean possibleException = result != null &&
        ControlFlowAnalysis.mayThrowException(result);

    // Try to use a substitute that with a break because it is shorter.

    // First lets pretend it is a break with no labels.
    Node breakTarget = n;
    boolean safe = true;

    for (;!ControlFlowAnalysis.isBreakTarget(breakTarget, null /* no label */);
        breakTarget = breakTarget.getParent()) {
      if (NodeUtil.isFunction(breakTarget) ||
          breakTarget.getType() == Token.SCRIPT) {

          // We can switch the return to a break if the return value has
          // side effect and it must encounter a finally.

          // example: return alert('a') -> finally { alert('b') } ->
          //          return alert('a')
          // prints a then b. If the first return is a break,
          // it prints b then a.
        safe = false;
        break;
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
    while (follow != null &&
        NodeUtil.isTryFinallyNode(follow.getParent(), follow)) {
      if (result != null &&
          // TODO(user): Use the new side effects API for more accuracy.
          (NodeUtil.canBeSideEffected(result) ||
           NodeUtil.mayHaveSideEffects(result))) {
        safe = false;
        break;
      }
      follow = ControlFlowAnalysis.computeFollowNode(follow);
    }

    if (safe) {
      if (follow == null) {
        // When follow is null, this mean the follow of a break target is the
        // end of a function. This means a break is same as return.
        if (result == null) {
          n.setType(Token.BREAK);
          reportCodeChange();
          return n;
        }

      } else if (follow.getType() == Token.RETURN &&
          (result == follow.getFirstChild() ||
           (result != null && follow.hasChildren() &&
            result.checkTreeEqualsSilent(follow.getFirstChild())) &&
            ControlFlowAnalysis.getExceptionHandler(n) ==
            ControlFlowAnalysis.getExceptionHandler(follow)
           )) {
        // When the follow is a return, if both doesn't return anything
        // or both returns the same thing. This mean we can replace it with a
        // break.
        n.removeChildren();
        n.setType(Token.BREAK);
        reportCodeChange();
        return n;
      }
      // If any of the above is executed, we must return because n is no longer
      // a "return" node.
    }

    // TODO(user): consider cases such as if (x) { return 1} return 1;

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
        default:
          //Do nothing
            break;
      }
    }

    return n;
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
        if (isPropertyAssignmentInExpression(expr)) {
          // Keep opportunities for CollapseProperties such as
          // a.longIdentifier || a.longIdentifier = ... -> var a = ...;
          return n;
        }

        if (cond.getType() == Token.NOT) {
          // if(!x)bar(); -> x||bar();
          if (isLowerPrecedenceInExpression(cond, OR_PRECEDENCE) &&
              isLowerPrecedenceInExpression(expr.getFirstChild(),
                  OR_PRECEDENCE)) {
            // It's not okay to add two sets of parentheses.
            return n;
          }

          Node or = new Node(Token.OR, cond.removeFirstChild(),
          expr.removeFirstChild()).copyInformationFrom(n);
          Node newExpr = NodeUtil.newExpr(or);
          parent.replaceChild(n, newExpr);
          reportCodeChange();

          return newExpr;
        }

        // if(x)foo(); -> x&&foo();
        if (isLowerPrecedenceInExpression(cond, AND_PRECEDENCE) ||
            isLowerPrecedenceInExpression(expr.getFirstChild(),
                AND_PRECEDENCE)) {
          // One additional set of parentheses isn't worth it.
          return n;
        }

        n.removeChild(cond);
        Node and = new Node(Token.AND, cond, expr.removeFirstChild())
                       .copyInformationFrom(n);
        Node newExpr = NodeUtil.newExpr(and);
        parent.replaceChild(n, newExpr);
        reportCodeChange();

        return newExpr;
      }

      return n;
    }

    /* TODO(dcc) This modifies the siblings of n, which is undesirable for a
     * peephole optimization. This should probably get moved to another pass.
     */
    tryRemoveRepeatedStatements(n);

    // if(!x)foo();else bar(); -> if(x)bar();else foo();
    // An additional set of curly braces isn't worth it.
    if (cond.getType() == Token.NOT && !consumesDanglingElse(elseBranch)) {
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
      Node hookNode = new Node(Token.HOOK, cond, thenExpr, elseExpr)
                          .copyInformationFrom(n);
      Node returnNode = new Node(Token.RETURN, hookNode);
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

            Node hookNode = new Node(Token.HOOK, cond, thenExpr, elseExpr)
                                .copyInformationFrom(n);
            Node assign = new Node(thenOp.getType(), assignName, hookNode)
                              .copyInformationFrom(thenOp);
            Node expr = NodeUtil.newExpr(assign);
            parent.replaceChild(n, expr);
            reportCodeChange();

            return expr;
          }
        } else if (NodeUtil.isCall(thenOp)) {
          // if(x)foo();else bar(); -> x?foo():bar()
          n.removeChild(cond);
          thenOp.detachFromParent();
          elseOp.detachFromParent();
          Node hookNode = new Node(Token.HOOK, cond, thenOp, elseOp)
                              .copyInformationFrom(n);
          Node expr = NodeUtil.newExpr(hookNode);
          parent.replaceChild(n, expr);
          reportCodeChange();

          return expr;
        }
      }
      return n;
    }

    boolean thenBranchIsVar = isVarBlock(thenBranch);
    boolean elseBranchIsVar = isVarBlock(elseBranch);

    // if(x)var y=1;else y=2  ->  var y=x?1:2
    if (thenBranchIsVar && elseBranchIsExpressionBlock &&
        NodeUtil.isAssign(getBlockExpression(elseBranch).getFirstChild())) {

      Node var = getBlockVar(thenBranch);
      Node elseAssign = getBlockExpression(elseBranch).getFirstChild();

      Node name1 = var.getFirstChild();
      Node maybeName2 = elseAssign.getFirstChild();

      if (name1.hasChildren()
          && maybeName2.getType() == Token.NAME
          && name1.getString().equals(maybeName2.getString())) {
        Node thenExpr = name1.removeChildren();
        Node elseExpr = elseAssign.getLastChild().detachFromParent();
        cond.detachFromParent();
        Node hookNode = new Node(Token.HOOK, cond, thenExpr, elseExpr)
                            .copyInformationFrom(n);
        var.detachFromParent();
        name1.addChildrenToBack(hookNode);
        parent.replaceChild(n, var);
        reportCodeChange();
        return var;
      }

    // if(x)y=1;else var y=2  ->  var y=x?1:2
    } else if (elseBranchIsVar && thenBranchIsExpressionBlock &&
        NodeUtil.isAssign(getBlockExpression(thenBranch).getFirstChild())) {

      Node var = getBlockVar(elseBranch);
      Node thenAssign = getBlockExpression(thenBranch).getFirstChild();

      Node maybeName1 = thenAssign.getFirstChild();
      Node name2 = var.getFirstChild();

      if (name2.hasChildren()
          && maybeName1.getType() == Token.NAME
          && maybeName1.getString().equals(name2.getString())) {
        Node thenExpr = thenAssign.getLastChild().detachFromParent();
        Node elseExpr = name2.removeChildren();
        cond.detachFromParent();
        Node hookNode = new Node(Token.HOOK, cond, thenExpr, elseExpr)
                            .copyInformationFrom(n);
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
    Preconditions.checkState(n.getType() == Token.IF);

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
    if (n.getType() == Token.BLOCK) {
      if (n.hasOneChild()) {
        Node maybeExpr = n.getFirstChild();
        if (maybeExpr.getType() == Token.EXPR_RESULT) {
          // IE has a bug where event handlers behave differently when
          // their return value is used vs. when their return value is in
          // an EXPR_RESULT. It's pretty freaking weird. See:
          // http://code.google.com/p/closure-compiler/issues/detail?id=291
          // We try to detect this case, and not fold EXPR_RESULTs
          // into other expressions.
          if (maybeExpr.getFirstChild().getType() == Token.CALL) {
            Node calledFn = maybeExpr.getFirstChild().getFirstChild();

            // We only have to worry about methods with an implicit 'this'
            // param, or this doesn't happen.
            if (calledFn.getType() == Token.GETELEM) {
              return false;
            } else if (calledFn.getType() == Token.GETPROP &&
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
   *     an return.
   */
  private boolean isReturnExpressBlock(Node n) {
    if (n.getType() == Token.BLOCK) {
      if (n.hasOneChild()) {
        Node first = n.getFirstChild();
        if (first.getType() == Token.RETURN) {
          return first.hasOneChild();
        }
      }
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
    if (n.getType() == Token.BLOCK) {
      if (n.hasOneChild()) {
        Node first = n.getFirstChild();
        if (first.getType() == Token.VAR) {
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
   * Does the expression contain a property assignment?
   */
  private boolean isPropertyAssignmentInExpression(Node n) {
    Predicate<Node> isPropertyAssignmentInExpressionPredicate =
        new Predicate<Node>() {
      @Override
      public boolean apply(Node input) {
        return (input.getType() == Token.GETPROP &&
            input.getParent().getType() == Token.ASSIGN);
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
              Node leftParent = first.getFirstChild();
              Node rightParent = first.getLastChild();
              if (leftParent.getType() == Token.NOT
                  && rightParent.getType() == Token.NOT) {
                Node left = leftParent.removeFirstChild();
                Node right = rightParent.removeFirstChild();

                int newOp = (first.getType() == Token.AND) ? Token.OR : Token.AND;
                Node newRoot = new Node(newOp, left, right);
                parent.replaceChild(n, newRoot);
                reportCodeChange();
                // No need to traverse, tryMinimizeCondition is called on the
                // AND and OR children below.
                return newRoot;
              }
            }
            break;
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
        TernaryValue rightVal = NodeUtil.getBooleanValue(right);
        if (NodeUtil.getBooleanValue(right) != TernaryValue.UNKNOWN) {
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
        if (NodeUtil.getBooleanValue(trueNode) == TernaryValue.TRUE
            && NodeUtil.getBooleanValue(falseNode) == TernaryValue.FALSE) {
          // Remove useless conditionals, keep the condition
          condition.detachFromParent();
          replacement = condition;
        } else if (NodeUtil.getBooleanValue(trueNode) == TernaryValue.FALSE
            && NodeUtil.getBooleanValue(falseNode) == TernaryValue.TRUE) {
          // Remove useless conditionals, keep the condition
          condition.detachFromParent();
          replacement = new Node(Token.NOT, condition);
        } else if (NodeUtil.getBooleanValue(trueNode) == TernaryValue.TRUE) {
          // Remove useless true case.
          n.detachChildren();
          replacement = new Node(Token.OR, condition, falseNode);
        } else if (NodeUtil.getBooleanValue(falseNode) == TernaryValue.FALSE) {
          // Remove useless false case
          n.detachChildren();
          replacement = new Node(Token.AND, condition, trueNode);
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
        TernaryValue nVal = NodeUtil.getBooleanValue(n);
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
    Node newNode = Node.newNumber(num);
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
    Preconditions.checkState(n.getType() == Token.NEW);

    // If name normalization has been run then we know that
    // new Object() does in fact refer to what we think it is
    // and not some custom-defined Object().
    if (isASTNormalized()) {
      if (n.getFirstChild().getType() == Token.NAME) {
        String className = n.getFirstChild().getString();
        if (STANDARD_OBJECT_CONSTRUCTORS.contains(className)) {
            n.setType(Token.CALL);
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
    Preconditions.checkArgument(n.getType() == Token.CALL
        || n.getType() == Token.NEW);

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
          newLiteralNode = new Node(Token.OBJECTLIT);
        } else if ("Array".equals(className)) {
          // "Array(arg0, arg1, ...)" --> "[arg0, arg1, ...]"
          Node arg0 = constructorNameNode.getNext();
          FoldArrayAction action = isSafeToFoldArrayConstructor(arg0);

          if (action == FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS ||
              action == FoldArrayAction.SAFE_TO_FOLD_WITHOUT_ARGS) {
            newLiteralNode = new Node(Token.ARRAYLIT);
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
        case (Token.STRING):
          // "Array('a')" --> "['a']"
          action = FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS;
          break;
        case (Token.NUMBER):
          // "Array(0)" --> "[]"
          if (arg.getDouble() == 0) {
            action = FoldArrayAction.SAFE_TO_FOLD_WITHOUT_ARGS;
          }
          break;
        case (Token.ARRAYLIT):
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

    // Only run on normalized AST to make sure RegExp() is actually
    // the RegExp we expect (if the AST has been normalized then
    // other RegExp's will have been renamed to something like RegExp$1)
    if (!isASTNormalized()) {
      return n;
    }

    if (null == pattern || (null != flags && null != flags.getNext())) {
      // too few or too many arguments
      return n;
    }

    if (// is pattern folded
        pattern.getType() == Token.STRING
        // make sure empty pattern doesn't fold to //
        && !"".equals(pattern.getString())

        // NOTE(nicksantos): Make sure that the regexp isn't longer than
        // 100 chars, or it blows up the regexp parser in Opera 9.2.
        && pattern.getString().length() < 100

        && (null == flags || flags.getType() == Token.STRING)
        // don't escape patterns with unicode escapes since Safari behaves badly
        // (read can't parse or crashes) on regex literals with unicode escapes
        && !containsUnicodeEscape(pattern.getString())) {

      // Make sure that / is escaped, so that it will fit safely in /brackets/.
      // pattern is a string value with \\ and similar already escaped
      pattern = makeForwardSlashBracketSafe(pattern);

      Node regexLiteral;
      if (null == flags || "".equals(flags.getString())) {
        // fold to /foobar/
        regexLiteral = new Node(Token.REGEXP, pattern);
      } else {
        // fold to /foobar/gi
        if (!areValidRegexpFlags(flags.getString())) {
          error(INVALID_REGULAR_EXPRESSION_FLAGS, flags);
          return n;
        }
        if (!areSafeFlagsToFold(flags.getString())) {
          return n;
        }
        n.removeChild(flags);
        regexLiteral = new Node(Token.REGEXP, pattern, flags);
      }

      parent.replaceChild(n, regexLiteral);
      reportCodeChange();
      return regexLiteral;
    }

    return n;
  }

  private static final Pattern REGEXP_FLAGS_RE = Pattern.compile("^[gmi]*$");

  /**
   * are the given flags valid regular expression flags?
   * Javascript recognizes several suffix flags for regular expressions,
   * 'g' - global replace, 'i' - case insensitive, 'm' - multi-line.
   * They are case insensitive, and javascript does not recognize the extended
   * syntax mode, single-line mode, or expression replacement mode from perl5.
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
   */
  private static boolean areSafeFlagsToFold(String flags) {
    return flags.indexOf('g') < 0;
  }

  /**
   * returns a string node that can safely be rendered inside /brackets/.
   */
  private static Node makeForwardSlashBracketSafe(Node n) {
    String s = n.getString();
    // sb contains everything in s[0:pos]
    StringBuilder sb = null;
    int pos = 0;
    for (int i = 0; i < s.length(); ++i) {
      switch (s.charAt(i)) {
        case '\\':  // skip over the next char after a '\\'.
          ++i;
          break;
        case '/':  // escape it
          if (null == sb) { sb = new StringBuilder(s.length() + 16); }
          sb.append(s, pos, i).append('\\');
          pos = i;
          break;
      }
    }

    // don't discard useful line-number info if there were no changes
    if (null == sb) { return n.cloneTree(); }

    sb.append(s, pos, s.length());
    return Node.newString(sb.toString()).copyInformationFrom(n);
  }

  /**
   * true if the javascript string would contain a unicode escape when written
   * out as the body of a regular expression literal.
   */
  static boolean containsUnicodeEscape(String s) {
    String esc = CodeGenerator.regexpEscape(s);
    for (int i = -1; (i = esc.indexOf("\\u", i + 1)) >= 0;) {
      int nSlashes = 0;
      while (i - nSlashes > 0 && '\\' == esc.charAt(i - nSlashes - 1)) {
        ++nSlashes;
      }
      // if there are an even number of slashes before the \ u then it is a
      // unicode literal.
      if (0 == (nSlashes & 1)) { return true; }
    }
    return false;
  }
}
