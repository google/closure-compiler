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
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.regex.Pattern;

/**
 * FoldConstants simplifies expressions which consist only of constants,
 * e.g (1 + 2).
 *
*
*
 */
class FoldConstants extends AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType DIVIDE_BY_0_ERROR = DiagnosticType.error(
      "JSC_DIVIDE_BY_0_ERROR",
      "Divide by 0");

  static final DiagnosticType INVALID_GETELEM_INDEX_ERROR =
      DiagnosticType.error(
          "JSC_INVALID_GETELEM_INDEX_ERROR",
          "Array index not integer: {0}");

  static final DiagnosticType INDEX_OUT_OF_BOUNDS_ERROR =
      DiagnosticType.error(
          "JSC_INDEX_OUT_OF_BOUNDS_ERROR",
          "Array index out of bounds: {0}");

  static final DiagnosticType NEGATING_A_NON_NUMBER_ERROR =
      DiagnosticType.error(
          "JSC_NEGATING_A_NON_NUMBER_ERROR",
          "Can't negate non-numeric value: {0}");

  static final DiagnosticType INVALID_REGULAR_EXPRESSION_FLAGS =
      DiagnosticType.error(
          "JSC_INVALID_REGULAR_EXPRESSION_FLAGS",
          "Invalid flags to RegExp constructor: {0}");

  static final DiagnosticType BITWISE_OPERAND_OUT_OF_RANGE =
      DiagnosticType.error(
          "JSC_BITWISE_OPERAND_OUT_OF_RANGE",
          "Operand out of range, bitwise operation will lose information: {0}");

  static final DiagnosticType SHIFT_AMOUNT_OUT_OF_BOUNDS = DiagnosticType.error(
      "JSC_SHIFT_AMOUNT_OUT_OF_BOUNDS",
      "Shift amount out of bounds: {0}");

  static final DiagnosticType FRACTIONAL_BITWISE_OPERAND = DiagnosticType.error(
      "JSC_FRACTIONAL_BITWISE_OPERAND",
      "Fractional bitwise operand: {0}");

  private static final int AND_PRECEDENCE = NodeUtil.precedence(Token.AND);
  private static final int OR_PRECEDENCE = NodeUtil.precedence(Token.OR);

  private final AbstractCompiler compiler;

  FoldConstants(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public void process(Node externs, Node jsRoot) {
    NodeTraversal.traverse(compiler, jsRoot, this);
  }

  public void visit(NodeTraversal t, Node n, Node parent) {
    int type = n.getType();

    if (type == Token.BLOCK) {
      tryFoldBlock(t, n, parent);
      return;
    }

    Node left = n.getFirstChild();
    if (left == null) {
      return;
    }

    if (type == Token.TYPEOF && NodeUtil.isLiteralValue(left)) {
      String newValue = null;

      switch (left.getType()) {
        case Token.STRING:
          newValue = "string";
          break;
        case Token.NUMBER:
          newValue = "number";
          break;
        case Token.TRUE:
        case Token.FALSE:
          newValue = "boolean";
          break;
        case Token.NULL:
        case Token.OBJECTLIT:
        case Token.ARRAYLIT:
          newValue = "object";
          break;
        case Token.NAME:
          // We assume here that programs don't change the value of the
          // keyword undefined to something other than the value undefined.
          if ("undefined".equals(left.getString())) {
            newValue = "undefined";
          }
          break;
      }

      if (newValue != null) {
        parent.replaceChild(n, Node.newString(newValue));
        t.getCompiler().reportCodeChange();
      }

      return;
    }

    if (type == Token.NOT ||
        type == Token.NEG ||
        type == Token.BITNOT) {
        Preconditions.checkState(n.hasOneChild());

        if (NodeUtil.isExpressionNode(parent)) {
          // If the value of the NOT isn't used, then just throw
          // away the operator
          parent.replaceChild(n, n.removeFirstChild());
          t.getCompiler().reportCodeChange();
          return;
        }

        // Try to mimize NOT nodes such as !(x==y) into x!=y.
        if (type == Token.NOT && tryMinimizeNot(t, n, parent)) {
          return;
        }

        if (!NodeUtil.isLiteralValue(left)) {
          return;
        }

        switch (type) {
          case Token.NOT:
            int result = NodeUtil.getBooleanValue(left) ? Token.FALSE :
                         Token.TRUE;
            parent.replaceChild(n, new Node(result));
            t.getCompiler().reportCodeChange();
            break;

          case Token.NEG:
            try {
              if (left.getType() == Token.NAME) {
                if (left.getString().equals("Infinity")) {
                  // "-Infinity" is valid and a literal, don't modify it.
                  return;
                } else if (left.getString().equals("NaN")) {
                  // "-NaN" is "NaN".
                  n.removeChild(left);
                  parent.replaceChild(n, left);
                  t.getCompiler().reportCodeChange();
                  return;
                }
              }

              double negNum = -left.getDouble();
              parent.replaceChild(n, Node.newNumber(negNum));
              t.getCompiler().reportCodeChange();
            } catch (UnsupportedOperationException ex) {
              // left is not a number node, so do not replace, but warn the
              // user because they can't be doing anything good
              error(t, NEGATING_A_NON_NUMBER_ERROR, left);
            }
            break;

          case Token.BITNOT:
            try {
              double val = left.getDouble();
              if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                int intVal = (int) val;
                if (intVal == val) {
                  parent.replaceChild(n, Node.newNumber(~intVal));
                  t.getCompiler().reportCodeChange();
                } else {
                  error(t, FRACTIONAL_BITWISE_OPERAND, left);
                }
              } else {
                error(t, BITWISE_OPERAND_OUT_OF_RANGE, left);
              }
            } catch (UnsupportedOperationException ex) {
              // left is not a number node, so do not replace, but warn the
              // user because they can't be doing anything good
              error(t, NEGATING_A_NON_NUMBER_ERROR, left);
            }
            break;
        }
        return;
    } else if (type == Token.NEW) {
      if (Token.NAME == left.getType()) {
        String className = left.getString();
        if ("RegExp".equals(className)) {
          tryFoldRegularExpressionConstructor(t, n, parent);
        } else if (left.getNext() == null) {
          if ("Array".equals(className)) {
            tryFoldLiteralConstructor(
                t, n, parent, className, Token.ARRAYLIT);
          } else if ("Object".equals(className)) {
            tryFoldLiteralConstructor(
                t, n, parent, className, Token.OBJECTLIT);
          }
        }
      }
    }

    if (type == Token.EXPR_RESULT) {
      tryMinimizeCondition(t, left, n);
      return;
    }

    if (type == Token.RETURN) {
      tryReduceReturn(t, n);
      return;
    }

    Node right = left.getNext();
    if (right == null) {
      return;
    }

    // TODO(johnlenz) Use type information if available to fold
    // instanceof.
    if (type == Token.INSTANCEOF
        && NodeUtil.isLiteralValue(left)
        && !NodeUtil.mayHaveSideEffects(right)) {
      if (NodeUtil.isImmutableValue(left)) {
        // Non-object types are never instances.
        parent.replaceChild(n, new Node(Token.FALSE));
        t.getCompiler().reportCodeChange();
        return;
      }

      if (right.getType() == Token.NAME
          && "Object".equals(right.getString())) {
        parent.replaceChild(n, new Node(Token.TRUE));
        t.getCompiler().reportCodeChange();
        return;
      }
    }

    if (type == Token.IF || type == Token.HOOK) {
      tryMinimizeCondition(t, n.getFirstChild(), n);
      boolean changes = tryFoldHookIf(t, n, parent);

      // bad cascades can occur if we run the second round
      // of IF optimizations immediately
      if (type == Token.IF && !changes) {
        tryMinimizeIf(t, n, parent);
      }
      return;
    }

    if (type == Token.DO) {
      tryMinimizeCondition(t, NodeUtil.getConditionExpression(n), n);
      tryFoldDo(t, n, parent);
      return;
    }

    if (type == Token.WHILE) {
      tryMinimizeCondition(t, NodeUtil.getConditionExpression(n), n);
      tryFoldWhile(t, n, parent);
      return;
    }

    if (type == Token.FOR) {
      Node condition = NodeUtil.getConditionExpression(n);
      if (condition != null) {
        tryMinimizeCondition(t, condition, n);
        // The root condition node might have changed, get it again.
        condition = NodeUtil.getConditionExpression(n);
        this.tryFoldForCondition(condition, n);
      }
        
      tryFoldFor(t, n, parent);
      return;
    }

    if (type == Token.AND ||
        type == Token.OR) {
      tryFoldAndOr(t, n, left, right, parent);
      return;
    }

    if (type == Token.BITOR ||
        type == Token.BITAND) {
      tryFoldBitAndOr(t, n, left, right, parent);
      return;
    }

    if (type == Token.LSH ||
        type == Token.RSH ||
        type == Token.URSH) {
      tryFoldShift(t, n, left, right, parent);
      return;
    }

    if (type == Token.GETPROP) {
      tryFoldGetProp(t, n, left, right, parent);
      return;
    }

    if (type == Token.CALL) {
      tryFoldStringJoin(t, n, left, right, parent);
      tryFoldStringIndexOf(t, n, left, right, parent);
      return;
    }

    if (type == Token.ASSIGN) {
      tryFoldAssign(t, n, left, right);
    }

    if (!NodeUtil.isLiteralValue(left) ||
        !NodeUtil.isLiteralValue(right)) {

      if (type == Token.ADD)
        tryFoldLeftChildAdd(t, n, left, right, parent);

      if (type == Token.LT ||
          type == Token.GT) {
        tryFoldComparison(t, n, left, right, parent);
      }

      return; // The subsequent ops only work if the LHS & RHS are consts
    }

    if (type == Token.ADD) {
      tryFoldAdd(t, n, left, right, parent);
      return;
    }
    if (type == Token.SUB ||
        type == Token.MUL ||
        type == Token.DIV) {
      tryFoldArithmetic(t, n, left, right, parent);
      return;
    }

    if (type == Token.LT ||
        type == Token.GT ||
        type == Token.LE ||
        type == Token.GE ||
        type == Token.EQ ||
        type == Token.NE ||
        type == Token.SHEQ ||
        type == Token.SHNE) {
      tryFoldComparison(t, n, left, right, parent);
      return;
    }

    if (type == Token.GETELEM) {
      tryFoldGetElem(t, n, left, right, parent);
      return;
    }

    // other types aren't handled
  }

  private void error(NodeTraversal t, DiagnosticType diagnostic, Node n) {
    t.getCompiler().report(JSError.make(t, n, diagnostic, n.toString()));
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
          if (n.getChildCount() < 3) return true;
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

  private void tryFoldAssign(NodeTraversal t, Node n, Node left, Node right) {
    Preconditions.checkArgument(n.getType() == Token.ASSIGN);

    // Tries to convert x = x + y -> x += y;
    if (!right.hasChildren() ||
        right.getFirstChild().getNext() != right.getLastChild()) {
      // RHS must have two children.
      return;
    }

    if (NodeUtil.mayHaveSideEffects(left)) {
      return;
    }

    Node leftChild = right.getFirstChild();
    if (!compiler.areNodesEqualForInlining(left, leftChild)) {
      return;
    }

    int newType = -1;
    switch (right.getType()) {
      case Token.ADD:
        newType = Token.ASSIGN_ADD;
        break;
      case Token.BITAND:
        newType = Token.ASSIGN_BITAND;
        break;
      case Token.BITOR:
        newType = Token.ASSIGN_BITOR;
        break;
      case Token.BITXOR:
        newType = Token.ASSIGN_BITXOR;
        break;
      case Token.DIV:
        newType = Token.ASSIGN_DIV;
        break;
      case Token.LSH:
        newType = Token.ASSIGN_LSH;
        break;
      case Token.MOD:
        newType = Token.ASSIGN_MOD;
        break;
      case Token.MUL:
        newType = Token.ASSIGN_MUL;
        break;
      case Token.RSH:
        newType = Token.ASSIGN_RSH;
        break;
      case Token.SUB:
        newType = Token.ASSIGN_SUB;
        break;
      case Token.URSH:
        newType = Token.ASSIGN_URSH;
        break;
      default:
        return;
    }

    n.getParent().replaceChild(n, new Node(newType,
        left.detachFromParent(), right.getLastChild().detachFromParent()));
    t.getCompiler().reportCodeChange();
  }

  /**
   * Try removing unneeded block nodes and their useless children
   */
  void tryFoldBlock(NodeTraversal t, Node n, Node parent) {
    // Remove any useless children
    for (Node c = n.getFirstChild(); c != null; ) {
      Node next = c.getNext();  // save c.next, since 'c' may be removed
      if (!NodeUtil.mayHaveSideEffects(c)) {
        n.removeChild(c);  // lazy kids
        t.getCompiler().reportCodeChange();
      }
      c = next;
    }

    if (n.isSyntheticBlock() || parent == null) {
      return;
    }

    // Try to remove the block.
    if (NodeUtil.tryMergeBlock(n)) {
      t.getCompiler().reportCodeChange();
    }
  }

  /**
   * Try folding :? (hook) and IF nodes by removing dead branches.
   * @return were any changes performed?
   */
  boolean tryFoldHookIf(NodeTraversal t, Node n, Node parent) {
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
        t.getCompiler().reportCodeChange();
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
        t.getCompiler().reportCodeChange();
        changes = true;
      }

      // if (x()) { }
      if (!NodeUtil.mayHaveSideEffects(thenBody) && elseBody == null) {
        if (NodeUtil.mayHaveSideEffects(cond)) {
          // x() has side effects, just leave the condition on its own.
          n.removeChild(cond);
          parent.replaceChild(n, NodeUtil.newExpr(cond));
        } else {
          // x() has no side effects, the whole tree is useless now.
          NodeUtil.removeChild(parent, n);
        }
        t.getCompiler().reportCodeChange();
        return true; // The if has been removed. There is nothing to do.
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
            Node not = new Node(Token.NOT);
            n.removeChild(cond);
            not.addChildToBack(cond);
            ifNode.addChildToBack(not);
          }

          n.removeChild(elseBody);
          ifNode.addChildToBack(
              new Node(Token.BLOCK, NodeUtil.newExpr(elseBody)));
          parent.getParent().replaceChild(parent, ifNode);
          t.getCompiler().reportCodeChange();
          return true;
        } else if (!NodeUtil.mayHaveSideEffects(elseBody)) {
          // x?y:void 0 --> if(x)y
          Node ifNode = new Node(Token.IF);
          n.removeChild(cond);
          ifNode.addChildToBack(cond);
          n.removeChild(thenBody);

          ifNode.addChildToBack(
              new Node(Token.BLOCK, NodeUtil.newExpr(thenBody)));
          parent.getParent().replaceChild(parent, ifNode);
          t.getCompiler().reportCodeChange();
          return true;
        }
      }
    }

    // Try transforms that apply to both IF and HOOK.
    if (!NodeUtil.isLiteralValue(cond)) {
      return changes;  // We can't remove branches otherwise!
    }

    boolean condTrue = NodeUtil.getBooleanValue(cond);

    if (n.getChildCount() == 2) {
      Preconditions.checkState(type == Token.IF);

      if (condTrue) {
        // Replace "if (true) { X }" with "X".
        Node thenStmt = n.getFirstChild().getNext();
        n.removeChild(thenStmt);
        parent.replaceChild(n, thenStmt);
        t.getCompiler().reportCodeChange();
      } else {
        // Replace "if (false) { X }" with empty node.
        NodeUtil.redeclareVarsInsideBranch(n);
        NodeUtil.removeChild(parent, n);
        t.getCompiler().reportCodeChange();
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
      t.getCompiler().reportCodeChange();
    }
    return true;
  }

  /**
   * Try to minimize NOT nodes such as !(x==y).
   */
  private boolean tryMinimizeNot(NodeTraversal t, Node n, Node parent) {
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
        return false;
    }
    Node newOperator = n.removeFirstChild();
    newOperator.setType(complementOperator);
    parent.replaceChild(n, newOperator);
    t.getCompiler().reportCodeChange();
    return true;
  }

  /**
   * Try turning IF nodes into smaller HOOKs
   */
  void tryMinimizeIf(NodeTraversal t, Node n, Node parent) {
    Node cond = n.getFirstChild();
    Node thenBranch = cond.getNext();
    Node elseBranch = thenBranch.getNext();

    if (elseBranch == null) {
      if (isExpressBlock(thenBranch)) {
        Node expr = getBlockExpression(thenBranch);
        if (isPropertyAssignmentInExpression(t, expr)) {
          // Keep opportunities for CollapseProperties such as
          // a.longIdentifier || a.longIdentifier = ... -> var a = ...;
          return;
        }

        if (cond.getType() == Token.NOT) {
          // if(!x)bar(); -> x||bar();
          if (isLowerPrecedenceInExpression(t, cond, OR_PRECEDENCE) &&
              isLowerPrecedenceInExpression(t, expr.getFirstChild(),
                  OR_PRECEDENCE)) {
            // It's not okay to add two sets of parentheses.
            return;
          }

          Node or = new Node(Token.OR, cond.removeFirstChild(),
              expr.removeFirstChild());
          Node newExpr = NodeUtil.newExpr(or);
          parent.replaceChild(n, newExpr);
          t.getCompiler().reportCodeChange();

          return;
        }

        // if(x)foo(); -> x&&foo();
        if (isLowerPrecedenceInExpression(t, cond, AND_PRECEDENCE) ||
            isLowerPrecedenceInExpression(t, expr.getFirstChild(),
                AND_PRECEDENCE)) {
          // One additional set of parentheses isn't worth it.
          return;
        }

        n.removeChild(cond);
        Node and = new Node(Token.AND, cond, expr.removeFirstChild());
        Node newExpr = NodeUtil.newExpr(and);
        parent.replaceChild(n, newExpr);
        t.getCompiler().reportCodeChange();
      }

      return;
    }

    tryRemoveRepeatedStatements(t, n);

    // if(!x)foo();else bar(); -> if(x)bar();else foo();
    // An additional set of curly braces isn't worth it.
    if (cond.getType() == Token.NOT && !consumesDanglingElse(elseBranch)) {
      n.replaceChild(cond, cond.removeFirstChild());
      n.removeChild(thenBranch);
      n.addChildToBack(thenBranch);
      t.getCompiler().reportCodeChange();
      return;
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
      Node hookNode = new Node(Token.HOOK, cond, thenExpr, elseExpr);
      Node returnNode = new Node(Token.RETURN, hookNode);
      parent.replaceChild(n, returnNode);
      t.getCompiler().reportCodeChange();
      return;
    }

    boolean thenBranchIsExpressionBlock = isExpressBlock(thenBranch);
    boolean elseBranchIsExpressionBlock = isExpressBlock(elseBranch);

    if (thenBranchIsExpressionBlock && elseBranchIsExpressionBlock) {
      Node thenOp = getBlockExpression(thenBranch).getFirstChild();
      Node elseOp = getBlockExpression(elseBranch).getFirstChild();
      if (thenOp.getType() == elseOp.getType()) {
        // if(x)a=1;else a=2; -> a=x?1:2;
        if (NodeUtil.isAssignmentOp(thenOp)) {
          Node lhs = thenOp.getFirstChild();
          if (compiler.areNodesEqualForInlining(lhs, elseOp.getFirstChild()) &&
              // if LHS has side effects, don't proceed [since the optimization
              // evaluates LHS before cond]
              // NOTE - there are some circumstances where we can
              // proceed even if there are side effects...
              !NodeUtil.mayEffectMutableState(lhs)) {

            n.removeChild(cond);
            Node assignName = thenOp.removeFirstChild();
            Node thenExpr = thenOp.removeFirstChild();
            Node elseExpr = elseOp.getLastChild();
            elseOp.removeChild(elseExpr);

            Node hookNode = new Node(Token.HOOK, cond, thenExpr,
                elseExpr);
            Node assign = new Node(thenOp.getType(), assignName,
                hookNode);
            Node expr = NodeUtil.newExpr(assign);
            parent.replaceChild(n, expr);
            t.getCompiler().reportCodeChange();
          }
        } else if (NodeUtil.isCall(thenOp)) {
          // if(x)foo();else bar(); -> x?foo():bar()
          n.removeChild(cond);
          thenOp.detachFromParent();
          elseOp.detachFromParent();
          Node hookNode = new Node(Token.HOOK, cond, thenOp, elseOp);
          Node expr = NodeUtil.newExpr(hookNode);
          parent.replaceChild(n, expr);
          t.getCompiler().reportCodeChange();
        }
      }
      return;
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
        Node hookNode = new Node(Token.HOOK, cond, thenExpr, elseExpr);
        var.detachFromParent();
        name1.addChildrenToBack(hookNode);
        parent.replaceChild(n, var);
        t.getCompiler().reportCodeChange();
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
        Node hookNode = new Node(Token.HOOK, cond, thenExpr, elseExpr);
        var.detachFromParent();
        name2.addChildrenToBack(hookNode);
        parent.replaceChild(n, var);
        t.getCompiler().reportCodeChange();
      }
    }
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
  private void tryRemoveRepeatedStatements(NodeTraversal t, Node n) {
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
          || !compiler.areNodesEqualForInlining(lastTrue, lastFalse)) {
        break;
      }
      lastTrue.detachFromParent();
      lastFalse.detachFromParent();
      parent.addChildAfter(lastTrue, n);
      t.getCompiler().reportCodeChange();
    }
  }

  /**
   * Reduce "return undefined" or "return void 0" to simply "return".
   */
  private void tryReduceReturn(NodeTraversal t, Node n) {
    Node result = n.getFirstChild();
    if (result != null) {
      switch (result.getType()) {
        case Token.VOID:
          Node operand = result.getFirstChild();
          if (!NodeUtil.mayHaveSideEffects(operand)) {
            n.removeFirstChild();
            t.getCompiler().reportCodeChange();
          }
          return;
        case Token.NAME:
          String name = result.getString();
          if (name.equals("undefined")) {
            n.removeFirstChild();
            t.getCompiler().reportCodeChange();
          }
          return;
      }
    }
  }

  /**
   * Does the expression contain a property assignment?
   */
  private boolean isPropertyAssignmentInExpression(NodeTraversal t, Node n) {
    final boolean[] found = { false };
    new NodeTraversal(t.getCompiler(), new AbstractShallowCallback() {
      public void visit(NodeTraversal t, Node n, Node parent) {
        found[0] |= (n.getType() == Token.GETPROP &&
                     parent.getType() == Token.ASSIGN);
      }
    }).traverse(n);
    return found[0];
  }

  /**
   * Does the expression contain an operator with lower precedence than
   * the argument?
   */
  private boolean isLowerPrecedenceInExpression(NodeTraversal t, Node n,
      final int precedence) {
    final boolean[] lower = { false };
    new NodeTraversal(t.getCompiler(), new AbstractShallowCallback() {
      public void visit(NodeTraversal t, Node n, Node parent) {
        lower[0] |= NodeUtil.precedence(n.getType()) < precedence;
      }
    }).traverse(n);
    return lower[0];
  }

  /**
   * Try to fold a AND/OR node.
   */
  void tryFoldAndOr(NodeTraversal t, Node n, Node left, Node right,
                    Node parent) {
    Node result = null;

    int type = n.getType();
    if (NodeUtil.isLiteralValue(left)) {
      boolean lval = NodeUtil.getBooleanValue(left);

      // (TRUE || x) => TRUE (also, (3 || x) => 3)
      // (FALSE && x) => FALSE
      if (lval && type == Token.OR ||
          !lval && type == Token.AND) {
        result = left;

      } else {
        // (FALSE || x) => x
        // (TRUE && x) => x
        result = right;
      }
    } else if (NodeUtil.isLiteralValue(right)) {
      // Note: We cannot always fold when the constant is on the
      // right, because the typed value of the expression will depend
      // on the type of the constant on the right, even if the boolean
      // equivalent of the value does not. Specifically, in "a = x ||
      // 0", a will be numeric 0 if x is undefined (and hence is
      // e.g. a valid array index). However, it is safe to fold
      // e.g. "if (x || true)" because 'if' doesn't care if the
      // expression is 'true' or '3'.
      int pt = parent.getType();
      if (pt == Token.IF || pt == Token.WHILE || pt == Token.DO ||
          (pt == Token.FOR && NodeUtil.getConditionExpression(parent) == n) ||
          (pt == Token.HOOK && parent.getFirstChild() == n)) {
        boolean rval = NodeUtil.getBooleanValue(right);

        // (x || FALSE) => x
        // (x && TRUE) => x
        if (type == Token.OR && !rval ||
            type == Token.AND && rval) {
          result = left;
        } else {
          // If x has no side-effects:
          //   (x || TRUE) => TRUE
          //   (x && FALSE) => FALSE
          if (!NodeUtil.mayHaveSideEffects(left)) {
            result = right;
          }
        }
      }
    }

    // Note: The parser parses 'x && FALSE && y' as 'x && (FALSE && y)', so
    // there is not much need to worry about const values on left's
    // right child.

    if (result != null) {
      // Fold it!
      n.removeChild(result);
      parent.replaceChild(n, result);
      t.getCompiler().reportCodeChange();
    }
  }

  /**
   * Expressions such as [foo() + 'a' + 'b'] generate parse trees
   * where no node has two const children ((foo() + 'a') + 'b'), so
   * tryFoldAdd() won't fold it -- tryFoldLeftChildAdd() will (for Strings).
   * Specifically it folds Add exprssions where:
   *  - The left child is also and add expression
   *  - The right child is a constant value
   *  - The left child's right child is a STRING constant.
   *
   * WARNING: If javascript ever adds operator overloading, this will
   * probably stop being correct.
   */
  void tryFoldLeftChildAdd(NodeTraversal t, Node n, Node left, Node right,
                           Node parent) {

    if (NodeUtil.isLiteralValue(right) &&
        left.getType() == Token.ADD &&
        left.getChildCount() == 2) {

      Node ll = left.getFirstChild();
      Node lr = ll.getNext();

      // Left's right child MUST be a string. We would not want to fold
      // foo() + 2 + 'a' because we don't know what foo() will return, and
      // therefore we don't know if left is a string concat, or a numeric add.
      if (lr.getType() != Token.STRING)
        return;

      String leftString = NodeUtil.getStringValue(lr);
      String rightString = NodeUtil.getStringValue(right);
      if (leftString != null && rightString != null) {
        left.removeChild(ll);
        String result = leftString + rightString;
        n.replaceChild(left, ll);
        n.replaceChild(right, Node.newString(result));
        t.getCompiler().reportCodeChange();
      }
    }
  }

  /**
   * Try to fold a ADD node
   */
  void tryFoldAdd(NodeTraversal t, Node n, Node left, Node right,
                  Node parent) {
    if (left.getType() == Token.STRING ||
        right.getType() == Token.STRING) {

      // Add strings.
      String leftString = NodeUtil.getStringValue(left);
      String rightString = NodeUtil.getStringValue(right);
      if (leftString != null && rightString != null) {
        parent.replaceChild(n, Node.newString(leftString + rightString));
        t.getCompiler().reportCodeChange();
      }
    } else {
      // Try arithmetic add
      tryFoldArithmetic(t, n, left, right, parent);
    }
  }

  /**
   * Try to fold arithmetic binary operators
   */
  void tryFoldArithmetic(NodeTraversal t, Node n, Node left, Node right,
                         Node parent) {

    if (left.getType() == Token.NUMBER &&
        right.getType() == Token.NUMBER) {
      double result;
      double lval = left.getDouble();
      double rval = right.getDouble();

      switch (n.getType()) {
        case Token.ADD:
          result = lval + rval;
          break;
        case Token.SUB:
          result = lval - rval;
          break;
        case Token.MUL:
          result = lval * rval;
          break;
        case Token.DIV:
          if (rval == 0) {
            error(t, DIVIDE_BY_0_ERROR, right);
            return;
          }
          result = lval / rval;
          break;
        default:
          throw new Error("Unknown arithmetic operator");
      }

      // length of the left and right value plus 1 byte for the operator.
      if (String.valueOf(result).length() <=
          String.valueOf(lval).length() + String.valueOf(rval).length() + 1) {
        parent.replaceChild(n, Node.newNumber(result));
        t.getCompiler().reportCodeChange();
      }
   }
  }

  /**
   * Try to fold arithmetic binary operators
   */
  void tryFoldBitAndOr(NodeTraversal t, Node n, Node left, Node right,
                       Node parent) {

    if (left.getType() == Token.NUMBER &&
        right.getType() == Token.NUMBER) {
      double result;
      double lval = left.getDouble();
      double rval = right.getDouble();

      // For now, we are being extra conservative, and only folding ints in
      // the range MIN_VALUE-MAX_VALUE
      if (lval < Integer.MIN_VALUE || lval > Integer.MAX_VALUE ||
          rval < Integer.MIN_VALUE || rval > Integer.MAX_VALUE) {

        // Fall back through and let the javascript use the larger values
        return;
      }

      // Convert the numbers to ints
      int lvalInt = (int) lval;
      if (lvalInt != lval) {
        return;
      }

      int rvalInt = (int) rval;
      if (rvalInt != rval) {
        return;
      }

      switch (n.getType()) {
        case Token.BITAND:
          result = lvalInt & rvalInt;
          break;
        case Token.BITOR:
          result = lvalInt | rvalInt;
          break;
        default:
          throw new Error("Unknown bitwise operator");
      }
      parent.replaceChild(n, Node.newNumber(result));
      t.getCompiler().reportCodeChange();
    }
  }

  /**
   * Try to fold shift operations
   */
  void tryFoldShift(NodeTraversal t, Node n, Node left, Node right,
                    Node parent) {

    if (left.getType() == Token.NUMBER &&
        right.getType() == Token.NUMBER) {

      double result;
      double lval = left.getDouble();
      double rval = right.getDouble();

      // check ranges.  We do not do anything that would clip the double to
      // a 32-bit range, since the user likely does not intend that.
      if (!(lval >= Integer.MIN_VALUE && lval <= Integer.MAX_VALUE)) {
        error(t, BITWISE_OPERAND_OUT_OF_RANGE, left);
        return;
      }

      // only the lower 5 bits are used when shifting, so don't do anything
      // if the shift amount is outside [0,32)
      if (!(rval >= 0 && rval < 32)) {
        error(t, SHIFT_AMOUNT_OUT_OF_BOUNDS, right);
        return;
      }

      // Convert the numbers to ints
      int lvalInt = (int) lval;
      if (lvalInt != lval) {
        error(t, FRACTIONAL_BITWISE_OPERAND, left);
        return;
      }

      int rvalInt = (int) rval;
      if (rvalInt != rval) {
        error(t, FRACTIONAL_BITWISE_OPERAND, right);
        return;
      }

      switch (n.getType()) {
        case Token.LSH:
          result = lvalInt << rvalInt;
          break;
        case Token.RSH:
          result = lvalInt >> rvalInt;
          break;
        case Token.URSH:
          result = lvalInt >>> rvalInt;
          break;
        default:
          throw new AssertionError("Unknown shift operator: " +
              Node.tokenToName(n.getType()));
      }
      parent.replaceChild(n, Node.newNumber(result));
      t.getCompiler().reportCodeChange();
    }
  }

  /**
   * Try to fold comparison nodes, e.g ==
   */
  @SuppressWarnings("fallthrough")
  void tryFoldComparison(NodeTraversal t, Node n, Node left, Node right,
                         Node parent) {

    int op = n.getType();
    boolean result;

    // TODO(johnlenz): Use the JSType to compare nodes of different types.

    boolean rightLiteral = NodeUtil.isLiteralValue(right);
    boolean undefinedRight = ((Token.NAME == right.getType()
          && right.getString().equals("undefined"))
          || (Token.VOID == right.getType()
              && NodeUtil.isLiteralValue(right.getFirstChild())));

    switch (left.getType()) {
      case Token.VOID:
        if (!NodeUtil.isLiteralValue(left.getFirstChild())) {
          return;
        } else if (!rightLiteral) {
          return;
        } else {
          boolean nullRight = (Token.NULL == right.getType());
          boolean equivalent = undefinedRight || nullRight;
          switch (op) {
            case Token.EQ:
              // undefined is only equal to
              result = equivalent;
              break;
            case Token.NE:
              result = !equivalent;
              break;
            case Token.SHEQ:
              result = undefinedRight;
              break;
            case Token.SHNE:
              result = !undefinedRight;
              break;
            case Token.LT:
            case Token.GT:
            case Token.LE:
            case Token.GE:
              result = false;
              break;
            default:
              return;
          }
        }
        break;

      case Token.NULL:
        if (undefinedRight) {
          result = (op == Token.EQ);
          break;
        }
        // fall through
      case Token.TRUE:
      case Token.FALSE:
        if (undefinedRight) {
          result = false;
          break;
        }
        // fall through
      case Token.THIS:
        int tt = right.getType();
        if (tt != Token.THIS &&
            tt != Token.TRUE &&
            tt != Token.FALSE &&
            tt != Token.NULL)
          return;
        switch (op) {
          case Token.EQ:
            result = left.getType() == right.getType();
            break;

          case Token.NE:
            result = left.getType() != right.getType();
            break;

          default:
            return;  // we only handle == and != here
        }
        break;

      case Token.STRING:
        if (undefinedRight) {
          result = false;
          break;
        }
        if (Token.STRING != right.getType()) {
          return;  // Only eval if they are the same type
        }
        switch (op) {
          case Token.EQ:
            result = left.getString().equals(right.getString());
            break;

          case Token.NE:
            result = !left.getString().equals(right.getString());
            break;

          default:
            return;  // we only handle == and != here
        }
        break;

      case Token.NUMBER:
        if (undefinedRight) {
          result = false;
          break;
        }
        if (Token.NUMBER != right.getType()) {
          return;  // Only eval if they are the same type
        }
        double lv = left.getDouble();
        double rv = right.getDouble();

        switch (op) {
          case Token.EQ: result = lv == rv; break;
          case Token.NE: result = lv != rv; break;
          case Token.LE: result = lv <= rv; break;
          case Token.LT: result = lv <  rv; break;
          case Token.GE: result = lv >= rv; break;
          case Token.GT: result = lv >  rv; break;
          default:
            return;  // don't handle that op
        }
        break;

      case Token.NAME:
        if (rightLiteral) {
          boolean undefinedLeft = (left.getString().equals("undefined"));
          if (undefinedLeft) {
            boolean nullRight = (Token.NULL == right.getType());
            boolean equivalent = undefinedRight || nullRight;
            switch (op) {
              case Token.EQ:
                // undefined is only equal to
                result = equivalent;
                break;
              case Token.NE:
                result = !equivalent;
                break;
              case Token.SHEQ:
                result = undefinedRight;
                break;
              case Token.SHNE:
                result = !undefinedRight;
                break;
              case Token.LT:
              case Token.GT:
              case Token.LE:
              case Token.GE:
                result = false;
                break;
              default:
                return;
            }
            break;
          }
        }

        if (Token.NAME != right.getType()) {
          return;  // Only eval if they are the same type
        }
        String ln = left.getString();
        String rn = right.getString();
        if (!ln.equals(rn)) {
          return;  // Not the same value name.
        }

        switch (op) {
          // If we knew the named value wouldn't be NaN, it would be nice
          // to handle EQ,NE,LE,GE,SHEQ, and SHNE.
          case Token.LT:
          case Token.GT:
            result = false;
            break;
          default:
            return;  // don't handle that op
        }
        break;

      default:
        // assert, this should cover all consts
        return;
    }

    parent.replaceChild(n, new Node(result ? Token.TRUE :
                                    Token.FALSE));
    t.getCompiler().reportCodeChange();
  }

  /**
   * Try to evaluate String.indexOf/lastIndexOf:
   *     "abcdef".indexOf("bc") -> 1
   *     "abcdefbc".indexOf("bc", 3) -> 6
   */
  void tryFoldStringIndexOf(NodeTraversal t, Node n, Node left, Node right,
                            Node parent) {
    if (!NodeUtil.isGetProp(left) || !NodeUtil.isImmutableValue(right)) {
      return;
    }

    Node lstringNode = left.getFirstChild();
    Node functionName = lstringNode.getNext();

    if ((lstringNode.getType() != Token.STRING) ||
        (!functionName.getString().equals("indexOf") &&
        !functionName.getString().equals("lastIndexOf"))) {
      return;
    }

    String lstring = NodeUtil.getStringValue(lstringNode);
    boolean isIndexOf = functionName.getString().equals("indexOf");
    Node firstArg = right;
    Node secondArg = right.getNext();
    String searchValue = NodeUtil.getStringValue(firstArg);
    // searchValue must be a valid string.
    if (searchValue == null) {
      return;
    }
    int fromIndex = isIndexOf ? 0 : lstring.length();
    if (secondArg != null) {
      // Third-argument and non-numeric second arg are problematic. Discard.
      if ((secondArg.getNext() != null) ||
          (secondArg.getType() != Token.NUMBER)) {
        return;
      } else {
        fromIndex = (int) secondArg.getDouble();
      }
    }
    int indexVal = isIndexOf ? lstring.indexOf(searchValue, fromIndex)
                             : lstring.lastIndexOf(searchValue, fromIndex);
    Node newNode = Node.newNumber(indexVal);
    parent.replaceChild(n, newNode);

    t.getCompiler().reportCodeChange();
  }


  /**
   * Try to fold an array join: ['a', 'b', 'c'].join('') -> 'abc';
   */
  void tryFoldStringJoin(NodeTraversal t, Node n, Node left, Node right,
                         Node parent) {
    if (!NodeUtil.isGetProp(left) || !NodeUtil.isImmutableValue(right)) {
      return;
    }

    Node arrayNode = left.getFirstChild();
    Node functionName = arrayNode.getNext();

    if ((arrayNode.getType() != Token.ARRAYLIT) ||
        !functionName.getString().equals("join")) {
      return;
    }

    String joinString = NodeUtil.getStringValue(right);
    List<Node> arrayFoldedChildren = Lists.newLinkedList();
    StringBuilder sb = null;
    int foldedSize = 0;
    Node elem = arrayNode.getFirstChild();
    // Merges adjacent String nodes.
    while (elem != null) {
      if (NodeUtil.isImmutableValue(elem)) {
        if (sb == null) {
          sb = new StringBuilder();
        } else {
          sb.append(joinString);
        }
        sb.append(NodeUtil.getStringValue(elem));
      } else {
        if (sb != null) {
          // + 2 for the quotes.
          foldedSize += sb.length() + 2;
          arrayFoldedChildren.add(Node.newString(sb.toString()));
          sb = null;
        }
        foldedSize += InlineCostEstimator.getCost(elem);
        arrayFoldedChildren.add(elem);
      }
      elem = elem.getNext();
    }

    if (sb != null) {
      // + 2 for the quotes.
      foldedSize += sb.length() + 2;
      arrayFoldedChildren.add(Node.newString(sb.toString()));
    }
    // one for each comma.
    foldedSize += arrayFoldedChildren.size() - 1;

    int originalSize = InlineCostEstimator.getCost(n);
    switch (arrayFoldedChildren.size()) {
      case 0:
        Node emptyStringNode = Node.newString("");
        parent.replaceChild(n, emptyStringNode);
        break;

      case 1:
        Node foldedStringNode = arrayFoldedChildren.remove(0);
        if (foldedSize > originalSize) {
          return;
        }
        arrayNode.detachChildren();
        if (foldedStringNode.getType() != Token.STRING) {
          // If the Node is not a string literal, ensure that
          // it is coerced to a string.
          Node replacement = new Node(Token.ADD,
              Node.newString(""), foldedStringNode);
          foldedStringNode = replacement;
        }
        parent.replaceChild(n, foldedStringNode);
        break;

      default:
        // No folding could actually be performed.
        if (arrayFoldedChildren.size() == arrayNode.getChildCount()) {
          return;
        }
        int kJoinOverhead = "[].join()".length();
        foldedSize += kJoinOverhead;
        foldedSize += InlineCostEstimator.getCost(right);
        if (foldedSize > originalSize) {
          return;
        }
        arrayNode.detachChildren();
        for (Node node : arrayFoldedChildren) {
          arrayNode.addChildToBack(node);
        }
        break;
    }
    t.getCompiler().reportCodeChange();
  }

  /**
   * Try to fold array-element. e.g [1, 2, 3][10];
   */
  void tryFoldGetElem(NodeTraversal t, Node n, Node left, Node right,
                      Node parent) {
    if (left.getType() == Token.ARRAYLIT) {

      if (right.getType() != Token.NUMBER) {
        // Sometimes people like to use complex expressions to index into
        // arrays, or strings to index into array methods.
        return;
      }

      double index = right.getDouble();
      int intIndex = (int) index;
      if (intIndex != index) {
        t.getCompiler().report(JSError.make(t, right,
            INVALID_GETELEM_INDEX_ERROR, String.valueOf(index)));
        return;
      }

      if (intIndex < 0) {
        t.getCompiler().report(JSError.make(t, n, INDEX_OUT_OF_BOUNDS_ERROR,
            String.valueOf(intIndex)));
        return;
      }

      Node elem = left.getFirstChild();
      for (int i = 0; elem != null && i < intIndex; i++) {
        elem = elem.getNext();
      }

      if (elem == null) {
        t.getCompiler().report(JSError.make(t, n, INDEX_OUT_OF_BOUNDS_ERROR,
            String.valueOf(intIndex)));
        return;
      }

      // Replace the entire GETELEM with the value
      left.removeChild(elem);
      parent.replaceChild(n, elem);
      t.getCompiler().reportCodeChange();
    }
  }

  /**
   * Try to fold array-length. e.g [1, 2, 3].length ==> 3, [x, y].length ==> 2
   */
  void tryFoldGetProp(NodeTraversal t, Node n, Node left, Node right,
                      Node parent) {
    if (right.getType() == Token.STRING &&
        right.getString().equals("length")) {
      int knownLength = -1;
      switch (left.getType()) {
        case Token.ARRAYLIT:
          if (NodeUtil.mayHaveSideEffects(left)) {
            // Nope, can't fold this, without handling the side-effects.
            return;
          }
          knownLength = left.getChildCount();
          break;
        case Token.STRING:
          knownLength = left.getString().length();
          break;
        default:
          // Not a foldable case, forget it.
          return;
      }

      Preconditions.checkState(knownLength != -1);
      Node lengthNode = Node.newNumber(knownLength);
      parent.replaceChild(n, lengthNode);
      t.getCompiler().reportCodeChange();
    }
  }

  /**
   * Try to fold a RegExp constructor to a regular expression literal.
   */
  void tryFoldRegularExpressionConstructor(
      NodeTraversal t, Node n, Node parent) {
    Node constructor = n.getFirstChild();
    Node pattern = constructor.getNext();  // e.g.  ^foobar$
    Node flags = null != pattern ? pattern.getNext() : null;  // e.g. gi

    if (null == pattern || (null != flags && null != flags.getNext())) {
      // too few or too many arguments
      return;
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
          error(t, INVALID_REGULAR_EXPRESSION_FLAGS, flags);
          return;
        }
        if (!areSafeFlagsToFold(flags.getString())) {
          return;
        }
        n.removeChild(flags);
        regexLiteral = new Node(Token.REGEXP, pattern, flags);
      }

      parent.replaceChild(n, regexLiteral);
      t.getCompiler().reportCodeChange();
    }
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
    return Node.newString(sb.toString());
  }

  /**
   * Replaces a new Array or Object node with an object literal, unless the
   * call to Array or Object is to a local function with the same name.
   *
   * @param t
   * @param n new call node (assumed to be of type TokenStream.NEW)
   * @param parent
   * @param type type of object literal to replace the new call node with
   */
  void tryFoldLiteralConstructor(
      NodeTraversal t, Node n, Node parent, String className, int type) {
    // Ignore calls to local functions with the same name.
    Scope.Var var = t.getScope().getVar(className);
    if (var != null && var.isLocal()) {
      return;
    }

    Node literalNode = new Node(type);
    parent.replaceChild(n, literalNode);
    t.getCompiler().reportCodeChange();
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

  /**
   * Removes WHILEs that always evaluate to false.
   */
  void tryFoldWhile(NodeTraversal t, Node n, Node parent) {
    Preconditions.checkArgument(n.getType() == Token.WHILE);
    Node cond = NodeUtil.getConditionExpression(n);
    if (!NodeUtil.isLiteralValue(cond) || NodeUtil.getBooleanValue(cond)) {
      return;
    }
    NodeUtil.redeclareVarsInsideBranch(n);
    NodeUtil.removeChild(parent, n);
    t.getCompiler().reportCodeChange();
  }

  /**
   * Removes FORs that always evaluate to false.
   */
  void tryFoldFor(NodeTraversal t, Node n, Node parent) {
    Preconditions.checkArgument(n.getType() == Token.FOR);
    // This is not a FOR-IN loop
    if (n.getChildCount() != 4) return;
    // There isn't an initializer
    if (n.getFirstChild().getType() != Token.EMPTY) return;

    Node cond = NodeUtil.getConditionExpression(n);
    if (!NodeUtil.isLiteralValue(cond) || NodeUtil.getBooleanValue(cond)) {
      return;
    }
    NodeUtil.redeclareVarsInsideBranch(n);
    NodeUtil.removeChild(parent, n);
    t.getCompiler().reportCodeChange();
  }

  /**
   * Removes DOs that always evaluate to false. This leaves the
   * statements that were in the loop in a BLOCK node.
   * The block will be removed in a later pass, if possible.
   */
  void tryFoldDo(NodeTraversal t, Node n, Node parent) {
    Preconditions.checkArgument(n.getType() == Token.DO);

    Node cond = NodeUtil.getConditionExpression(n);
    if (!NodeUtil.isLiteralValue(cond) || NodeUtil.getBooleanValue(cond)) {
      return;
    }

    // TODO(johnlenz): The do-while can be turned into a label with
    // named breaks and the label optimized away (maybe).
    if (hasBreakOrContinue(n)) {
      return;
    }

    Preconditions.checkState(
        NodeUtil.isControlStructureCodeBlock(n, n.getFirstChild()));
    Node block = n.removeFirstChild();

    parent.replaceChild(n, block);
    t.getCompiler().reportCodeChange();
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
        Predicates.<Node>not(new NodeUtil.MatchNodeType(Token.FUNCTION)));
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
   */
  void tryMinimizeCondition(NodeTraversal t, Node n, Node parent) {

    switch (n.getType()) {
      case Token.NOT:
        Node first = n.getFirstChild();
        switch (first.getType()) {
          case Token.NOT: {
              Node newRoot = first.removeFirstChild();
              parent.replaceChild(n, newRoot);
              n = newRoot; // continue from here.
              t.getCompiler().reportCodeChange();

              // The child has moved up, to minimize it recurse.
              tryMinimizeCondition(t, n, parent);
              return;
            }
          case Token.AND:
          case Token.OR: {
              Node leftParent = first.getFirstChild();
              Node rightParent = first.getLastChild();
              if (leftParent.getType() != Token.NOT
                  || rightParent.getType() != Token.NOT) {
                // No NOTs to elminate.
                break;
              }
              Node left = leftParent.removeFirstChild();
              Node right = rightParent.removeFirstChild();

              int newOp = (first.getType() == Token.AND) ? Token.OR : Token.AND;
              Node newRoot = new Node(newOp, left, right);
              parent.replaceChild(n, newRoot);
              n = newRoot; // continue from here.
              t.getCompiler().reportCodeChange();

              // Unlike the NOT case above, we know that AND and OR are
              // valid root to check minimize so just break out and check
              // the children.
            }
            break;
        }
        break;

      case Token.OR:
      case Token.AND:
        // check the children.
        break;

      default:
        // if(true) --> if(1)
        if (NodeUtil.isLiteralValue(n)) {
          boolean result = NodeUtil.getBooleanValue(n);
          int equivalentResult = result ? 1 : 0;
          maybeReplaceChildWithNumber(t, n, parent, equivalentResult);
        }
        // We can't do anything else currently.
        return;
    }

    for (Node c = n.getFirstChild(); c != null; ) {
      Node next = c.getNext();  // c may be removed.
      tryMinimizeCondition(t, c, n);
      c = next;
    }
  }

  /**
   * Remove always true loop conditions.
   */
  private void tryFoldForCondition(Node n, Node parent) {
    if (NodeUtil.isLiteralValue(n)) {
      boolean result = NodeUtil.getBooleanValue(n);
      if (result) {
        parent.replaceChild(n, new Node(Token.EMPTY));
        compiler.reportCodeChange();
      }
    }
  }
  
  /**
   * Replaces a node with a number node if the new number node is not equivalent
   * to the current node.
   */
  private void maybeReplaceChildWithNumber(NodeTraversal t, Node n, Node parent,
      int num) {
    Node newNode = Node.newNumber(num);
    if(!newNode.isEquivalentTo(n)) {
      parent.replaceChild(n, newNode);
      t.getCompiler().reportCodeChange();
    }
  }

  /**
   * @return Whether the node is a block with a single statement that is
   *     an expression.
   */
  private boolean isExpressBlock(Node n) {
    if (n.getType() == Token.BLOCK) {
      if (n.hasOneChild()) {
        return NodeUtil.isExpressionNode(n.getFirstChild());
      }
    }

    return false;
  }

  /**
   * @return The expression node.
   */
  private Node getBlockExpression(Node n) {
    Preconditions.checkState(isExpressBlock(n));
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
}
