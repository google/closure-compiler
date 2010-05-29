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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;

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
    } 

    if (type == Token.NEW) {
      tryFoldStandardConstructors(t, n);

      // The type might have changed from NEW to CALL, update it
      // and continue.
      type = n.getType();
    }

    if (type == Token.NEW || type == Token.CALL) {
      if (Token.NAME == left.getType() && left.getNext() == null) {
        String className = left.getString();
        if ("Array".equals(className)) {
          if (tryFoldLiteralConstructor(
              t, n, parent, className, Token.ARRAYLIT)) {
            return;
          }
        } else if ("Object".equals(className)) {
          if (tryFoldLiteralConstructor(
              t, n, parent, className, Token.OBJECTLIT)) {
            return;
          }
        }
      }
    }

    if (type == Token.EXPR_RESULT) {
        return;
    }
  
    if (type == Token.RETURN) {
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
      tryFoldHookIf(t, n, parent);
      return;
    }

    if (type == Token.DO) {
      tryFoldDo(t, n, parent);
      return;
    }

    if (type == Token.WHILE) {
      tryFoldWhile(t, n, parent);
      return;
    }

    if (type == Token.FOR) {
      Node condition = NodeUtil.getConditionExpression(n);
      if (condition != null) {
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

    if (type == Token.COMMA) {
      tryFoldComma(t, n, left, right, parent);
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
  private void tryFoldStandardConstructors(NodeTraversal t, Node n) {
    Preconditions.checkState(n.getType() == Token.NEW);
    
    if (n.getFirstChild().getType() == Token.NAME) {
      String className = n.getFirstChild().getString();
      if (STANDARD_OBJECT_CONSTRUCTORS.contains(className)) {
        // The var check isn't really needed here due to name normalizations
        // but we do it here to simplify unit testing.
        Scope.Var var = t.getScope().getVar(className);
        if (var == null || var.isGlobal()) {
          n.setType(Token.CALL);
          compiler.reportCodeChange();
        }    
      }
    }
  }

  private void tryFoldComma(
      NodeTraversal t, Node n, Node left, Node right, Node parent) {
    // If the left side does nothing replace the comma with the result.
    if (!NodeUtil.mayHaveSideEffects(left)) {
      // Fold it!
      n.removeChild(right);
      parent.replaceChild(n, right);
      t.getCompiler().reportCodeChange();
    } else {
      if (parent.getType() == Token.EXPR_RESULT) {
        // split comma
        n.detachChildren();
        // Replace the original expression with the left operand.
        parent.replaceChild(n, left);
        // Add the right expression afterward.
        Node newStatement = new Node(Token.EXPR_RESULT, right);
        newStatement.copyInformationFrom(n);
        parent.getParent().addChildAfter(newStatement, parent);
        t.getCompiler().reportCodeChange();
      }
    }
  }

  private void error(NodeTraversal t, DiagnosticType diagnostic, Node n) {
    t.getCompiler().report(t.makeError(n, diagnostic, n.toString()));
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
            Node not = new Node(Token.NOT).copyInformationFrom(cond);
            n.removeChild(cond);
            not.addChildToBack(cond);
            ifNode.addChildToBack(not);
          }

          n.removeChild(elseBody);
          ifNode.addChildToBack(
              new Node(Token.BLOCK, NodeUtil.newExpr(elseBody))
                  .copyInformationFrom(elseBody));
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
              new Node(Token.BLOCK, NodeUtil.newExpr(thenBody))
                  .copyInformationFrom(thenBody));
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
          case Token.SHEQ:
          case Token.EQ:
            result = left.getType() == right.getType();
            break;

          case Token.SHNE:
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
          case Token.SHEQ:
          case Token.EQ:
            result = left.getString().equals(right.getString());
            break;

          case Token.SHNE:
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
          case Token.SHEQ:
          case Token.EQ: result = lv == rv; break;
          case Token.SHNE:
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
    Node prev = null;
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
          Preconditions.checkNotNull(prev);
          // + 2 for the quotes.
          foldedSize += sb.length() + 2;
          arrayFoldedChildren.add(
              Node.newString(sb.toString()).copyInformationFrom(prev));
          sb = null;
        }
        foldedSize += InlineCostEstimator.getCost(elem);
        arrayFoldedChildren.add(elem);
      }
      prev = elem;
      elem = elem.getNext();
    }

    if (sb != null) {
      Preconditions.checkNotNull(prev);
      // + 2 for the quotes.
      foldedSize += sb.length() + 2;
      arrayFoldedChildren.add(
          Node.newString(sb.toString()).copyInformationFrom(prev));
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
              Node.newString("").copyInformationFrom(right), 
              foldedStringNode);
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
        t.getCompiler().report(t.makeError(right,
            INVALID_GETELEM_INDEX_ERROR, String.valueOf(index)));
        return;
      }

      if (intIndex < 0) {
        t.getCompiler().report(t.makeError(n, INDEX_OUT_OF_BOUNDS_ERROR,
            String.valueOf(intIndex)));
        return;
      }

      Node elem = left.getFirstChild();
      for (int i = 0; elem != null && i < intIndex; i++) {
        elem = elem.getNext();
      }

      if (elem == null) {
        t.getCompiler().report(t.makeError(n, INDEX_OUT_OF_BOUNDS_ERROR,
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
   * Replaces a new Array or Object node with an object literal, unless the
   * call to Array or Object is to a local function with the same name.
   *
   * @param t
   * @param n new call node (assumed to be of type TokenStream.NEW)
   * @param parent
   * @param type type of object literal to replace the new call node with
   */
  boolean tryFoldLiteralConstructor(
      NodeTraversal t, Node n, Node parent, String className, int type) {
    // Ignore calls to local functions with the same name.
    Scope.Var var = t.getScope().getVar(className);
    if (var != null && var.isLocal()) {
      // no change.
      return false;
    }

    Node literalNode = new Node(type);
    parent.replaceChild(n, literalNode);
    t.getCompiler().reportCodeChange();
    return true;
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
        new NodeUtil.MatchNotFunction());
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
}
