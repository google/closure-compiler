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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

/**
 * Peephole optimization to fold constants (e.g. x + 1 + 7 --> x + 8).
 *
 */
class PeepholeFoldConstants extends AbstractPeepholeOptimization {

  // TODO(johnlenz): optimizations should not be emiting errors. Move these to
  // a check pass.
  static final DiagnosticType INVALID_GETELEM_INDEX_ERROR =
      DiagnosticType.warning(
          "JSC_INVALID_GETELEM_INDEX_ERROR",
          "Array index not integer: {0}");

  static final DiagnosticType INDEX_OUT_OF_BOUNDS_ERROR =
      DiagnosticType.warning(
          "JSC_INDEX_OUT_OF_BOUNDS_ERROR",
          "Array index out of bounds: {0}");

  static final DiagnosticType NEGATING_A_NON_NUMBER_ERROR =
      DiagnosticType.warning(
          "JSC_NEGATING_A_NON_NUMBER_ERROR",
          "Can't negate non-numeric value: {0}");

  static final DiagnosticType BITWISE_OPERAND_OUT_OF_RANGE =
      DiagnosticType.warning(
          "JSC_BITWISE_OPERAND_OUT_OF_RANGE",
          "Operand out of range, bitwise operation will lose information: {0}");

  static final DiagnosticType SHIFT_AMOUNT_OUT_OF_BOUNDS =
      DiagnosticType.warning(
          "JSC_SHIFT_AMOUNT_OUT_OF_BOUNDS",
          "Shift amount out of bounds: {0}");

  static final DiagnosticType FRACTIONAL_BITWISE_OPERAND =
      DiagnosticType.warning(
          "JSC_FRACTIONAL_BITWISE_OPERAND",
          "Fractional bitwise operand: {0}");

  private static final double MAX_FOLD_NUMBER = Math.pow(2, 53);

  private final boolean late;

  /**
   * @param late When late is false, this mean we are currently running before
   * most of the other optimizations. In this case we would avoid optimizations
   * that would make the code harder to analyze. When this is true, we would
   * do anything to minimize for size.
   */
  PeepholeFoldConstants(boolean late) {
    this.late = late;
  }

  @Override
  Node optimizeSubtree(Node subtree) {
    switch(subtree.getType()) {
      case Token.NEW:
        return tryFoldCtorCall(subtree);

      case Token.TYPEOF:
        return tryFoldTypeof(subtree);

      case Token.NOT:
      case Token.POS:
      case Token.NEG:
      case Token.BITNOT:
        tryReduceOperandsForOp(subtree);
        return tryFoldUnaryOperator(subtree);

      case Token.VOID:
        return tryReduceVoid(subtree);

      default:
        tryReduceOperandsForOp(subtree);
        return tryFoldBinaryOperator(subtree);
    }
  }

  private Node tryFoldBinaryOperator(Node subtree) {
    Node left = subtree.getFirstChild();

    if (left == null) {
      return subtree;
    }

    Node right = left.getNext();

    if (right == null) {
      return subtree;
    }

    // If we've reached here, node is truly a binary operator.
    switch(subtree.getType()) {
      case Token.GETPROP:
        return tryFoldGetProp(subtree, left, right);

      case Token.GETELEM:
        return tryFoldGetElem(subtree, left, right);

      case Token.INSTANCEOF:
        return tryFoldInstanceof(subtree, left, right);

      case Token.AND:
      case Token.OR:
        return tryFoldAndOr(subtree, left, right);

      case Token.LSH:
      case Token.RSH:
      case Token.URSH:
        return tryFoldShift(subtree, left, right);

      case Token.ASSIGN:
        return tryFoldAssign(subtree, left, right);

      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
        return tryUnfoldAssignOp(subtree, left, right);

      case Token.ADD:
        return tryFoldAdd(subtree, left, right);

      case Token.SUB:
      case Token.DIV:
      case Token.MOD:
        return tryFoldArithmeticOp(subtree, left, right);

      case Token.MUL:
      case Token.BITAND:
      case Token.BITOR:
      case Token.BITXOR:
        Node result = tryFoldArithmeticOp(subtree, left, right);
        if (result != subtree) {
          return result;
        }
        return tryFoldLeftChildOp(subtree, left, right);

      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
        return tryFoldComparison(subtree, left, right);

      default:
        return subtree;
    }
  }

  private Node tryReduceVoid(Node n) {
    Node child = n.getFirstChild();
    if (!child.isNumber() || child.getDouble() != 0.0) {
      if (!mayHaveSideEffects(n)) {
        n.replaceChild(child, IR.number(0));
        reportCodeChange();
      }
    }
    return n;
  }

  private void tryReduceOperandsForOp(Node n) {
    switch (n.getType()) {
      case Token.ADD:
        Node left = n.getFirstChild();
        Node right = n.getLastChild();
        if (!NodeUtil.mayBeString(left) && !NodeUtil.mayBeString(right)) {
          tryConvertOperandsToNumber(n);
        }
        break;
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
        // TODO(johnlenz): convert these to integers.
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_MOD:
      case Token.ASSIGN_DIV:
        tryConvertToNumber(n.getLastChild());
        break;
      case Token.BITNOT:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.BITAND:
      case Token.LSH:
      case Token.RSH:
      case Token.URSH:
      case Token.SUB:
      case Token.MUL:
      case Token.MOD:
      case Token.DIV:
      case Token.POS:
      case Token.NEG:
        tryConvertOperandsToNumber(n);
        break;
    }
  }

  private void tryConvertOperandsToNumber(Node n) {
    Node next;
    for (Node c = n.getFirstChild(); c != null; c = next) {
      next = c.getNext();
      tryConvertToNumber(c);
    }
  }

  private void tryConvertToNumber(Node n) {
    switch (n.getType()) {
      case Token.NUMBER:
        // Nothing to do
        return;
      case Token.AND:
      case Token.OR:
      case Token.COMMA:
        tryConvertToNumber(n.getLastChild());
        return;
      case Token.HOOK:
        tryConvertToNumber(n.getChildAtIndex(1));
        tryConvertToNumber(n.getLastChild());
        return;
      case Token.NAME:
        if (!NodeUtil.isUndefined(n)) {
          return;
        }
        break;
    }

    Double result = NodeUtil.getNumberValue(n);
    if (result == null) {
      return;
    }

    double value = result;

    Node replacement = NodeUtil.numberNode(value, n);
    if (replacement.isEquivalentTo(n)) {
      return;
    }

    n.getParent().replaceChild(n, replacement);
    reportCodeChange();
  }

  /**
   * Folds 'typeof(foo)' if foo is a literal, e.g.
   * typeof("bar") --> "string"
   * typeof(6) --> "number"
   */
  private Node tryFoldTypeof(Node originalTypeofNode) {
    Preconditions.checkArgument(originalTypeofNode.isTypeOf());

    Node argumentNode = originalTypeofNode.getFirstChild();
    if (argumentNode == null || !NodeUtil.isLiteralValue(argumentNode, true)) {
      return originalTypeofNode;
    }

    String typeNameString = null;

    switch (argumentNode.getType()) {
      case Token.FUNCTION:
        typeNameString = "function";
        break;
      case Token.STRING:
        typeNameString = "string";
        break;
      case Token.NUMBER:
        typeNameString = "number";
        break;
      case Token.TRUE:
      case Token.FALSE:
        typeNameString = "boolean";
        break;
      case Token.NULL:
      case Token.OBJECTLIT:
      case Token.ARRAYLIT:
        typeNameString = "object";
        break;
      case Token.VOID:
        typeNameString = "undefined";
        break;
      case Token.NAME:
        // We assume here that programs don't change the value of the
        // keyword undefined to something other than the value undefined.
        if ("undefined".equals(argumentNode.getString())) {
          typeNameString = "undefined";
        }
        break;
    }

    if (typeNameString != null) {
      Node newNode = IR.string(typeNameString);
      originalTypeofNode.getParent().replaceChild(originalTypeofNode, newNode);
      reportCodeChange();

      return newNode;
    }

    return originalTypeofNode;
  }

  private Node tryFoldUnaryOperator(Node n) {
    Preconditions.checkState(n.hasOneChild());

    Node left = n.getFirstChild();
    Node parent = n.getParent();

    if (left == null) {
      return n;
    }

    TernaryValue leftVal = NodeUtil.getPureBooleanValue(left);
    if (leftVal == TernaryValue.UNKNOWN) {
      return n;
    }

    switch (n.getType()) {
      case Token.NOT:
        // Don't fold !0 and !1 back to false.
        if (late && left.isNumber()) {
          double numValue = left.getDouble();
          if (numValue == 0 || numValue == 1) {
            return n;
          }
        }
        Node replacementNode = NodeUtil.booleanNode(!leftVal.toBoolean(true));
        parent.replaceChild(n, replacementNode);
        reportCodeChange();
        return replacementNode;
      case Token.POS:
        if (NodeUtil.isNumericResult(left)) {
          // POS does nothing to numeric values.
          parent.replaceChild(n, left.detachFromParent());
          reportCodeChange();
          return left;
        }
        return n;
      case Token.NEG:
        if (left.isName()) {
          if (left.getString().equals("Infinity")) {
            // "-Infinity" is valid and a literal, don't modify it.
            return n;
          } else if (left.getString().equals("NaN")) {
            // "-NaN" is "NaN".
            n.removeChild(left);
            parent.replaceChild(n, left);
            reportCodeChange();
            return left;
          }
        }

        if (left.isNumber()) {
          double negNum = -left.getDouble();

          Node negNumNode = IR.number(negNum);
          parent.replaceChild(n, negNumNode);
          reportCodeChange();
          return negNumNode;
        } else {
          // left is not a number node, so do not replace, but warn the
          // user because they can't be doing anything good
          report(NEGATING_A_NON_NUMBER_ERROR, left);
          return n;
        }
      case Token.BITNOT:
        try {
          double val = left.getDouble();
          if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
            int intVal = (int) val;
            if (intVal == val) {
              Node notIntValNode = IR.number(~intVal);
              parent.replaceChild(n, notIntValNode);
              reportCodeChange();
              return notIntValNode;
            } else {
              report(FRACTIONAL_BITWISE_OPERAND, left);
              return n;
            }
          } else {
            report(BITWISE_OPERAND_OUT_OF_RANGE, left);
            return n;
          }
        } catch (UnsupportedOperationException ex) {
          // left is not a number node, so do not replace, but warn the
          // user because they can't be doing anything good
          report(NEGATING_A_NON_NUMBER_ERROR, left);
          return n;
        }
        default:
          return n;
    }
  }

  /**
   * Try to fold {@code left instanceof right} into {@code true}
   * or {@code false}.
   */
  private Node tryFoldInstanceof(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.isInstanceOf());

    // TODO(johnlenz) Use type information if available to fold
    // instanceof.
    if (NodeUtil.isLiteralValue(left, true)
        && !mayHaveSideEffects(right)) {

      Node replacementNode = null;

      if (NodeUtil.isImmutableValue(left)) {
        // Non-object types are never instances.
        replacementNode = IR.falseNode();
      } else if (right.isName()
          && "Object".equals(right.getString())) {
        replacementNode = IR.trueNode();
      }

      if (replacementNode != null) {
        n.getParent().replaceChild(n, replacementNode);
        reportCodeChange();
        return replacementNode;
      }
    }

    return n;
  }

  private Node tryFoldAssign(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.isAssign());

    if (!late) {
      return n;
    }

    // Tries to convert x = x + y -> x += y;
    if (!right.hasChildren() ||
        right.getFirstChild().getNext() != right.getLastChild()) {
      // RHS must have two children.
      return n;
    }

    if (mayHaveSideEffects(left)) {
      return n;
    }

    Node newRight;
    if (areNodesEqualForInlining(left, right.getFirstChild())) {
      newRight = right.getLastChild();
    } else if (NodeUtil.isCommutative(right.getType()) &&
          areNodesEqualForInlining(left, right.getLastChild())) {
      newRight = right.getFirstChild();
    } else {
      return n;
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
        return n;
    }

    Node newNode = new Node(newType,
        left.detachFromParent(), newRight.detachFromParent());
    n.getParent().replaceChild(n, newNode);

    reportCodeChange();

    return newNode;
  }

  private Node tryUnfoldAssignOp(Node n, Node left, Node right) {
    if (late) {
      return n;
    }

    if (!n.hasChildren() ||
        n.getFirstChild().getNext() != n.getLastChild()) {
      return n;
    }

    if (mayHaveSideEffects(left)) {
      return n;
    }

    // Tries to convert x += y -> x = x + y;
    int op = NodeUtil.getOpFromAssignmentOp(n);
    Node replacement = IR.assign(left.detachFromParent(),
        new Node(op, left.cloneTree(), right.detachFromParent())
            .srcref(n));
    n.getParent().replaceChild(n, replacement);
    reportCodeChange();

    return replacement;
  }

  /**
   * Try to fold a AND/OR node.
   */
  private Node tryFoldAndOr(Node n, Node left, Node right) {
    Node parent = n.getParent();

    Node result = null;

    int type = n.getType();

    TernaryValue leftVal = NodeUtil.getImpureBooleanValue(left);

    if (leftVal != TernaryValue.UNKNOWN) {
      boolean lval = leftVal.toBoolean(true);

      // (TRUE || x) => TRUE (also, (3 || x) => 3)
      // (FALSE && x) => FALSE
      if (lval && type == Token.OR ||
          !lval && type == Token.AND) {
        result = left;

      } else if (!mayHaveSideEffects(left)) {
        // (FALSE || x) => x
        // (TRUE && x) => x
        result = right;
      } else {
        // Left side may have side effects, but we know its boolean value.
        // e.g. true_with_sideeffects || foo() => true_with_sideeffects, foo()
        // or: false_with_sideeffects && foo() => false_with_sideeffects, foo()
        // This, combined with PeepholeRemoveDeadCode, helps reduce expressions
        // like "x() || false || z()".
        n.detachChildren();
        result = IR.comma(left, right);
      }
    }

    // Note: Right hand side folding is handled by
    // PeepholeMinimizeConditions#tryMinimizeCondition

    if (result != null) {
      // Fold it!
      n.detachChildren();
      parent.replaceChild(n, result);
      reportCodeChange();

      return result;
    } else {
      return n;
    }
  }

  /**
   * Expressions such as [foo() + 'a' + 'b'] generate parse trees
   * where no node has two const children ((foo() + 'a') + 'b'), so
   * tryFoldAdd() won't fold it -- tryFoldLeftChildAdd() will (for Strings).
   * Specifically, it folds Add expressions where:
   *  - The left child is also and add expression
   *  - The right child is a constant value
   *  - The left child's right child is a STRING constant.
   */
  private Node tryFoldChildAddString(Node n, Node left, Node right) {

    if (NodeUtil.isLiteralValue(right, false) &&
        left.isAdd()) {

      Node ll = left.getFirstChild();
      Node lr = ll.getNext();

      // Left's right child MUST be a string. We would not want to fold
      // foo() + 2 + 'a' because we don't know what foo() will return, and
      // therefore we don't know if left is a string concat, or a numeric add.
      if (lr.isString()) {
        String leftString = NodeUtil.getStringValue(lr);
        String rightString = NodeUtil.getStringValue(right);
        if (leftString != null && rightString != null) {
          left.removeChild(ll);
          String result = leftString + rightString;
          n.replaceChild(left, ll);
          n.replaceChild(right, IR.string(result));
          reportCodeChange();
          return n;
        }
      }
    }

    if (NodeUtil.isLiteralValue(left, false) &&
        right.isAdd()) {

      Node rl = right.getFirstChild();
      Node rr = right.getLastChild();

      // Left's right child MUST be a string. We would not want to fold
      // foo() + 2 + 'a' because we don't know what foo() will return, and
      // therefore we don't know if left is a string concat, or a numeric add.
      if (rl.isString()) {
        String leftString = NodeUtil.getStringValue(left);
        String rightString = NodeUtil.getStringValue(rl);
        if (leftString != null && rightString != null) {
          right.removeChild(rr);
          String result = leftString + rightString;
          n.replaceChild(right, rr);
          n.replaceChild(left, IR.string(result));
          reportCodeChange();
          return n;
        }
      }
    }

    return n;
  }

  /**
   * Try to fold an ADD node with constant operands
   */
  private Node tryFoldAddConstantString(Node n, Node left, Node right) {
    if (left.isString() ||
        right.isString()) {
      // Add strings.
      String leftString = NodeUtil.getStringValue(left);
      String rightString = NodeUtil.getStringValue(right);
      if (leftString != null && rightString != null) {
        Node newStringNode = IR.string(leftString + rightString);
        n.getParent().replaceChild(n, newStringNode);
        reportCodeChange();
        return newStringNode;
      }
    }



    return n;
  }

  /**
   * Try to fold arithmetic binary operators
   */
  private Node tryFoldArithmeticOp(Node n, Node left, Node right) {
    Node result = performArithmeticOp(n.getType(), left, right);
    if (result != null) {
      result.copyInformationFromForTree(n);
      n.getParent().replaceChild(n, result);
      reportCodeChange();
      return result;
    }
    return n;
  }

  /**
   * Try to fold arithmetic binary operators
   */
  private Node performArithmeticOp(int opType, Node left, Node right) {
    // Unlike other operations, ADD operands are not always converted
    // to Number.
    if (opType == Token.ADD
        && (NodeUtil.mayBeString(left)
            || NodeUtil.mayBeString(right))) {
      return null;
    }

    double result;

    // TODO(johnlenz): Handle NaN with unknown value. BIT ops convert NaN
    // to zero so this is a little awkward here.

    Double lValObj = NodeUtil.getNumberValue(left);
    if (lValObj == null) {
      return null;
    }
    Double rValObj = NodeUtil.getNumberValue(right);
    if (rValObj == null) {
      return null;
    }

    double lval = lValObj;
    double rval = rValObj;

    switch (opType) {
      case Token.BITAND:
        result = NodeUtil.toInt32(lval) & NodeUtil.toInt32(rval);
        break;
      case Token.BITOR:
        result = NodeUtil.toInt32(lval) | NodeUtil.toInt32(rval);
        break;
      case Token.BITXOR:
        result = NodeUtil.toInt32(lval) ^ NodeUtil.toInt32(rval);
        break;
      case Token.ADD:
        result = lval + rval;
        break;
      case Token.SUB:
        result = lval - rval;
        break;
      case Token.MUL:
        result = lval * rval;
        break;
      case Token.MOD:
        if (rval == 0) {
          return null;
        }
        result = lval % rval;
        break;
      case Token.DIV:
        if (rval == 0) {
          return null;
        }
        result = lval / rval;
        break;
      default:
        throw new Error("Unexpected arithmetic operator");
    }

    // TODO(johnlenz): consider removing the result length check.
    // length of the left and right value plus 1 byte for the operator.
    if ((String.valueOf(result).length() <=
        String.valueOf(lval).length() + String.valueOf(rval).length() + 1

        // Do not try to fold arithmetic for numbers > 2^53. After that
        // point, fixed-point math starts to break down and become inaccurate.
        && Math.abs(result) <= MAX_FOLD_NUMBER)
        || Double.isNaN(result)
        || result == Double.POSITIVE_INFINITY
        || result == Double.NEGATIVE_INFINITY) {
      return NodeUtil.numberNode(result, null);
    }
    return null;
  }

  /**
   * Expressions such as [foo() * 10 * 20] generate parse trees
   * where no node has two const children ((foo() * 10) * 20), so
   * performArithmeticOp() won't fold it -- tryFoldLeftChildOp() will.
   * Specifically, it folds associative expressions where:
   *  - The left child is also an associative expression of the same time.
   *  - The right child is a constant NUMBER constant.
   *  - The left child's right child is a NUMBER constant.
   */
  private Node tryFoldLeftChildOp(Node n, Node left, Node right) {
    int opType = n.getType();
    Preconditions.checkState(
        (NodeUtil.isAssociative(opType) && NodeUtil.isCommutative(opType))
        || n.isAdd());

    Preconditions.checkState(!n.isAdd() || !NodeUtil.mayBeString(n));

    // Use getNumberValue to handle constants like "NaN" and "Infinity"
    // other values are converted to numbers elsewhere.
    Double rightValObj = NodeUtil.getNumberValue(right);
    if (rightValObj != null && left.getType() == opType) {
      Preconditions.checkState(left.getChildCount() == 2);

      Node ll = left.getFirstChild();
      Node lr = ll.getNext();

      Node valueToCombine = ll;
      Node replacement = performArithmeticOp(opType, valueToCombine, right);
      if (replacement == null) {
        valueToCombine = lr;
        replacement = performArithmeticOp(opType, valueToCombine, right);
      }
      if (replacement != null) {
        // Remove the child that has been combined
        left.removeChild(valueToCombine);
        // Replace the left op with the remaining child.
        n.replaceChild(left, left.removeFirstChild());
        // New "-Infinity" node need location info explicitly
        // added.
        replacement.copyInformationFromForTree(right);
        n.replaceChild(right, replacement);
        reportCodeChange();
      }
    }

    return n;
  }

  private Node tryFoldAdd(Node node, Node left, Node right) {
    Preconditions.checkArgument(node.isAdd());

    if (NodeUtil.mayBeString(node)) {
      if (NodeUtil.isLiteralValue(left, false) &&
          NodeUtil.isLiteralValue(right, false)) {
        // '6' + 7
        return tryFoldAddConstantString(node, left, right);
      } else {
        // a + 7 or 6 + a
        return tryFoldChildAddString(node, left, right);
      }
    } else {
      // Try arithmetic add
      Node result = tryFoldArithmeticOp(node, left, right);
      if (result != node) {
        return result;
      }
      return tryFoldLeftChildOp(node, left, right);
    }
  }

  /**
   * Try to fold shift operations
   */
  private Node tryFoldShift(Node n, Node left, Node right) {
    if (left.isNumber() &&
        right.isNumber()) {

      double result;
      double lval = left.getDouble();
      double rval = right.getDouble();

      // check ranges.  We do not do anything that would clip the double to
      // a 32-bit range, since the user likely does not intend that.
      if (lval < Integer.MIN_VALUE) {
        report(BITWISE_OPERAND_OUT_OF_RANGE, left);
        return n;
      }
      // only the lower 5 bits are used when shifting, so don't do anything
      // if the shift amount is outside [0,32)
      if (!(rval >= 0 && rval < 32)) {
        report(SHIFT_AMOUNT_OUT_OF_BOUNDS, right);
        return n;
      }

      int rvalInt = (int) rval;
      if (rvalInt != rval) {
        report(FRACTIONAL_BITWISE_OPERAND, right);
        return n;
      }

      switch (n.getType()) {
        case Token.LSH:
        case Token.RSH:
          // Convert the numbers to ints
          if (lval > Integer.MAX_VALUE) {
            report(BITWISE_OPERAND_OUT_OF_RANGE, left);
            return n;
          }
          int lvalInt = (int) lval;
          if (lvalInt != lval) {
            report(FRACTIONAL_BITWISE_OPERAND, left);
            return n;
          }
          if (n.getType() == Token.LSH) {
            result = lvalInt << rvalInt;
          } else {
            result = lvalInt >> rvalInt;
          }
          break;
        case Token.URSH:
          // JavaScript handles zero shifts on signed numbers differently than
          // Java as an Java int can not represent the unsigned 32-bit number
          // where JavaScript can so use a long here.
          long maxUint32 = 0xffffffffL;
          if (lval > maxUint32) {
            report(BITWISE_OPERAND_OUT_OF_RANGE, left);
            return n;
          }
          long lvalLong = (long) lval;
          if (lvalLong != lval) {
            report(FRACTIONAL_BITWISE_OPERAND, left);
            return n;
          }
          result = (lvalLong & maxUint32) >>> rvalInt;
          break;
        default:
          throw new AssertionError("Unknown shift operator: " +
              Token.name(n.getType()));
      }

      Node newNumber = IR.number(result);
      n.getParent().replaceChild(n, newNumber);
      reportCodeChange();

      return newNumber;
    }

    return n;
  }

  /**
   * Try to fold comparison nodes, e.g ==
   */
  @SuppressWarnings("fallthrough")
  private Node tryFoldComparison(Node n, Node left, Node right) {
    TernaryValue result = evaluateComparison(n.getType(), left, right);
    if (result == TernaryValue.UNKNOWN) {
      return n;
    }

    Node newNode = NodeUtil.booleanNode(result.toBoolean(true));
    n.getParent().replaceChild(n, newNode);
    reportCodeChange();

    return newNode;
  }

  static TernaryValue evaluateComparison(int op, Node left, Node right) {
    boolean leftLiteral = NodeUtil.isLiteralValue(left, true);
    boolean rightLiteral = NodeUtil.isLiteralValue(right, true);

    if (!leftLiteral || !rightLiteral) {
      // We only handle literal operands for LT and GT.
      if (op != Token.GT && op != Token.LT) {
        return TernaryValue.UNKNOWN;
      }
    }

    boolean undefinedRight = NodeUtil.isUndefined(right) && rightLiteral;
    boolean nullRight = right.isNull();
    int lhType = getNormalizedNodeType(left);
    int rhType = getNormalizedNodeType(right);
    switch (lhType) {
      case Token.VOID:
        if (!leftLiteral) {
          return TernaryValue.UNKNOWN;
        } else if (!rightLiteral) {
          return TernaryValue.UNKNOWN;
        } else {
          return TernaryValue.forBoolean(compareToUndefined(right, op));
        }

      case Token.NULL:
        if (rightLiteral && isEqualityOp(op)) {
          return TernaryValue.forBoolean(compareToNull(right, op));
        }
        // fallthrough
      case Token.TRUE:
      case Token.FALSE:
        if (undefinedRight) {
          return TernaryValue.forBoolean(compareToUndefined(left, op));
        }
        if (rhType != Token.TRUE &&
            rhType != Token.FALSE &&
            rhType != Token.NULL) {
          return TernaryValue.UNKNOWN;
        }
        switch (op) {
          case Token.SHEQ:
          case Token.EQ:
            return TernaryValue.forBoolean(lhType == rhType);

          case Token.SHNE:
          case Token.NE:
            return TernaryValue.forBoolean(lhType != rhType);

          case Token.GE:
          case Token.LE:
          case Token.GT:
          case Token.LT:
            return compareAsNumbers(op, left, right);
        }
        return TernaryValue.UNKNOWN;

      case Token.THIS:
        if (!right.isThis()) {
          return TernaryValue.UNKNOWN;
        }
        switch (op) {
          case Token.SHEQ:
          case Token.EQ:
            return TernaryValue.TRUE;

          case Token.SHNE:
          case Token.NE:
            return TernaryValue.FALSE;
        }

        // We can only handle == and != here.
        // GT, LT, GE, LE depend on the type of "this" and how it will
        // be converted to number.  The results are different depending on
        // whether it is a string, NaN or other number value.
        return TernaryValue.UNKNOWN;

      case Token.STRING:
        if (undefinedRight) {
          return TernaryValue.forBoolean(compareToUndefined(left, op));
        }
        if (nullRight && isEqualityOp(op)) {
          return TernaryValue.forBoolean(compareToNull(left, op));
        }
        if (Token.STRING != right.getType()) {
          return TernaryValue.UNKNOWN;  // Only eval if they are the same type
        }

        switch (op) {
          case Token.SHEQ:
          case Token.EQ:
            return areStringsEqual(left.getString(), right.getString());

          case Token.SHNE:
          case Token.NE:
            return areStringsEqual(left.getString(), right.getString()).not();
        }

        return TernaryValue.UNKNOWN;

      case Token.NUMBER:
        if (undefinedRight) {
          return TernaryValue.forBoolean(compareToUndefined(left, op));
        }
        if (nullRight && isEqualityOp(op)) {
          return TernaryValue.forBoolean(compareToNull(left, op));
        }
        if (Token.NUMBER != right.getType()) {
          return TernaryValue.UNKNOWN;  // Only eval if they are the same type
        }
        return compareAsNumbers(op, left, right);

      case Token.NAME:
        if (leftLiteral && undefinedRight) {
          return TernaryValue.forBoolean(compareToUndefined(left, op));
        }

        if (rightLiteral) {
          boolean undefinedLeft = (left.getString().equals("undefined"));
          if (undefinedLeft) {
            return TernaryValue.forBoolean(compareToUndefined(right, op));
          }
          if (leftLiteral && nullRight && isEqualityOp(op)) {
            return TernaryValue.forBoolean(compareToNull(left, op));
          }
        }

        if (Token.NAME != right.getType()) {
          return TernaryValue.UNKNOWN;  // Only eval if they are the same type
        }
        String ln = left.getString();
        String rn = right.getString();
        if (!ln.equals(rn)) {
          return TernaryValue.UNKNOWN;  // Not the same value name.
        }

        switch (op) {
          // If we knew the named value wouldn't be NaN, it would be nice
          // to handle EQ,NE,LE,GE,SHEQ, and SHNE.
          case Token.LT:
          case Token.GT:
            return TernaryValue.FALSE;
        }

        return TernaryValue.UNKNOWN;  // don't handle that op

      case Token.NEG:
        if (leftLiteral) {
          if (undefinedRight) {
            return TernaryValue.forBoolean(compareToUndefined(left, op));
          }
          if (nullRight && isEqualityOp(op)) {
            return TernaryValue.forBoolean(compareToNull(left, op));
          }
        }
        // Nothing else for now.
        return TernaryValue.UNKNOWN;

      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
      case Token.REGEXP:
      case Token.FUNCTION:
        if (leftLiteral) {
          if (undefinedRight) {
            return TernaryValue.forBoolean(compareToUndefined(left, op));
          }
          if (nullRight && isEqualityOp(op)) {
            return TernaryValue.forBoolean(compareToNull(left, op));
          }
        }
        // ignore the rest for now.
        return TernaryValue.UNKNOWN;

      default:
        // assert, this should cover all consts
        return TernaryValue.UNKNOWN;
    }
  }

  /** Returns whether two JS strings are equal. */
  private static TernaryValue areStringsEqual(String a, String b) {
    // In JS, browsers parse \v differently. So do not consider strings
    // equal if one contains \v.
    if (a.indexOf('\u000B') != -1 ||
        b.indexOf('\u000B') != -1) {
      return TernaryValue.UNKNOWN;
    } else {
      return a.equals(b) ? TernaryValue.TRUE : TernaryValue.FALSE;
    }
  }

  /**
   * @return Translate NOT expressions into TRUE or FALSE when possible.
   */
  private static int getNormalizedNodeType(Node n) {
    int type = n.getType();
    if (type == Token.NOT) {
      TernaryValue value = NodeUtil.getPureBooleanValue(n);
      switch (value) {
        case TRUE:
          return Token.TRUE;
        case FALSE:
          return Token.FALSE;
        case UNKNOWN:
          return type;
      }
    }
    return type;
  }

  /**
   * The result of the comparison, or UNKNOWN if the
   * result could not be determined.
   */
  private static TernaryValue compareAsNumbers(int op, Node left, Node right) {
    Double leftValue = NodeUtil.getNumberValue(left);
    if (leftValue == null) {
      return TernaryValue.UNKNOWN;
    }
    Double rightValue = NodeUtil.getNumberValue(right);
    if (rightValue == null) {
      return TernaryValue.UNKNOWN;
    }

    double lv = leftValue;
    double rv = rightValue;

    switch (op) {
      case Token.SHEQ:
      case Token.EQ:
        Preconditions.checkState(
            left.isNumber() && right.isNumber());
        return TernaryValue.forBoolean(lv == rv);
      case Token.SHNE:
      case Token.NE:
        Preconditions.checkState(
            left.isNumber() && right.isNumber());
        return TernaryValue.forBoolean(lv != rv);
      case Token.LE:
        return TernaryValue.forBoolean(lv <= rv);
      case Token.LT:
        return TernaryValue.forBoolean(lv <  rv);
      case Token.GE:
        return TernaryValue.forBoolean(lv >= rv);
      case Token.GT:
        return TernaryValue.forBoolean(lv >  rv);
      default:
        return TernaryValue.UNKNOWN;  // don't handle that op
    }
  }

  /**
   * @param value The value to compare to "undefined"
   * @param op The boolean op to compare with
   * @return Whether the boolean op is true or false
   */
  private static boolean compareToUndefined(Node value, int op) {
    Preconditions.checkState(NodeUtil.isLiteralValue(value, true));
    boolean valueUndefined = NodeUtil.isUndefined(value);
    boolean valueNull = (Token.NULL == value.getType());
    boolean equivalent = valueUndefined || valueNull;
    switch (op) {
      case Token.EQ:
        // undefined is only equal to null or an undefined value
        return equivalent;
      case Token.NE:
        return !equivalent;
      case Token.SHEQ:
        return valueUndefined;
      case Token.SHNE:
        return !valueUndefined;
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
        return false;
      default:
        throw new IllegalStateException("unexpected.");
    }
  }

  private static boolean isEqualityOp(int op) {
    switch (op) {
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
        return true;
    }
    return false;
  }

  /**
   * @param value The value to compare to "null"
   * @param op The boolean op to compare with
   * @return Whether the boolean op is true or false
   */
  private static boolean compareToNull(Node value, int op) {
    boolean valueUndefined = NodeUtil.isUndefined(value);
    boolean valueNull = (Token.NULL == value.getType());
    boolean equivalent = valueUndefined || valueNull;
    switch (op) {
      case Token.EQ:
        // undefined is only equal to null or an undefined value
        return equivalent;
      case Token.NE:
        return !equivalent;
      case Token.SHEQ:
        return valueNull;
      case Token.SHNE:
        return !valueNull;
      default:
        throw new IllegalStateException("unexpected.");
    }
  }

  /**
   * Try to fold away unnecessary object instantiation.
   * e.g. this[new String('eval')] -> this.eval
   */
  private Node tryFoldCtorCall(Node n) {
    Preconditions.checkArgument(n.isNew());

    // we can remove this for GETELEM calls (anywhere else?)
    if (inForcedStringContext(n)) {
      return tryFoldInForcedStringContext(n);
    }
    return n;
  }

  /** Returns whether this node must be coerced to a string. */
  private static boolean inForcedStringContext(Node n) {
    if (n.getParent().isGetElem() &&
        n.getParent().getLastChild() == n) {
      return true;
    }

    // we can fold in the case "" + new String("")
    return n.getParent().isAdd();
  }

  private Node tryFoldInForcedStringContext(Node n) {
    // For now, we only know how to fold ctors.
    Preconditions.checkArgument(n.isNew());

    Node objectType = n.getFirstChild();
    if (!objectType.isName()) {
      return n;
    }

    if (objectType.getString().equals("String")) {
      Node value = objectType.getNext();
      String stringValue = null;
      if (value == null) {
        stringValue = "";
      } else {
        if (!NodeUtil.isImmutableValue(value)) {
          return n;
        }

        stringValue = NodeUtil.getStringValue(value);
      }

      if (stringValue == null) {
        return n;
      }

      Node parent = n.getParent();
      Node newString = IR.string(stringValue);

      parent.replaceChild(n, newString);
      newString.copyInformationFrom(parent);
      reportCodeChange();

      return newString;
    }
    return n;
  }

  /**
   * Try to fold array-element. e.g [1, 2, 3][10];
   */
  private Node tryFoldGetElem(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.isGetElem());

    if (left.isObjectLit()) {
      return tryFoldObjectPropAccess(n, left, right);
    }

    if (left.isArrayLit()) {
      return tryFoldArrayAccess(n, left, right);
    }
    return n;
  }

  /**
   * Try to fold array-length. e.g [1, 2, 3].length ==> 3, [x, y].length ==> 2
   */
  private Node tryFoldGetProp(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.isGetProp());

    if (left.isObjectLit()) {
      return tryFoldObjectPropAccess(n, left, right);
    }

    if (right.isString() &&
        right.getString().equals("length")) {
      int knownLength = -1;
      switch (left.getType()) {
        case Token.ARRAYLIT:
          if (mayHaveSideEffects(left)) {
            // Nope, can't fold this, without handling the side-effects.
            return n;
          }
          knownLength = left.getChildCount();
          break;
        case Token.STRING:
          knownLength = left.getString().length();
          break;
        default:
          // Not a foldable case, forget it.
          return n;
      }

      Preconditions.checkState(knownLength != -1);
      Node lengthNode = IR.number(knownLength);
      n.getParent().replaceChild(n, lengthNode);
      reportCodeChange();

      return lengthNode;
    }

    return n;
  }

  private Node tryFoldArrayAccess(Node n, Node left, Node right) {
    // If GETPROP/GETELEM is used as assignment target the array literal is
    // acting as a temporary we can't fold it here:
    //    "[][0] += 1"
    if (NodeUtil.isAssignmentTarget(n)) {
      return n;
    }

    if (!right.isNumber()) {
      // Sometimes people like to use complex expressions to index into
      // arrays, or strings to index into array methods.
      return n;
    }

    double index = right.getDouble();
    int intIndex = (int) index;
    if (intIndex != index) {
      report(INVALID_GETELEM_INDEX_ERROR, right);
      return n;
    }

    if (intIndex < 0) {
      report(INDEX_OUT_OF_BOUNDS_ERROR, right);
      return n;
    }

    Node current = left.getFirstChild();
    Node elem = null;
    for (int i = 0; current != null; i++) {
      if (i != intIndex) {
        if (mayHaveSideEffects(current)) {
          return n;
        }
      } else {
        elem = current;
      }

      current = current.getNext();
    }

    if (elem == null) {
      report(INDEX_OUT_OF_BOUNDS_ERROR, right);
      return n;
    }

    if (elem.isEmpty()) {
      elem = NodeUtil.newUndefinedNode(elem);
    } else {
      left.removeChild(elem);
    }

    // Replace the entire GETELEM with the value
    n.getParent().replaceChild(n, elem);
    reportCodeChange();
    return elem;
  }

  private Node tryFoldObjectPropAccess(Node n, Node left, Node right) {
    Preconditions.checkArgument(NodeUtil.isGet(n));

    if (!left.isObjectLit() || !right.isString()) {
      return n;
    }

    if (NodeUtil.isAssignmentTarget(n)) {
      // If GETPROP/GETELEM is used as assignment target the object literal is
      // acting as a temporary we can't fold it here:
      //    "{a:x}.a += 1" is not "x += 1"
      return n;
    }

    // find the last definition in the object literal
    Node key = null;
    Node value = null;
    for (Node c = left.getFirstChild(); c != null; c = c.getNext()) {
      if (c.getString().equals(right.getString())) {
        switch (c.getType()) {
          case Token.SETTER_DEF:
            continue;
          case Token.GETTER_DEF:
          case Token.STRING_KEY:
            if (value != null && mayHaveSideEffects(value)) {
              // The previously found value had side-effects
              return n;
            }
            key = c;
            value = key.getFirstChild();
            break;
          default:
            throw new IllegalStateException();
        }
      } else if (mayHaveSideEffects(c.getFirstChild())) {
        // We don't handle the side-effects here as they might need a temporary
        // or need to be reordered.
        return n;
      }
    }

    // Didn't find a definition of the name in the object literal, it might
    // be coming from the Object prototype
    if (value == null) {
      return n;
    }

    if (value.isFunction() && NodeUtil.referencesThis(value)) {
      // 'this' may refer to the object we are trying to remove
      return n;
    }

    Node replacement = value.detachFromParent();
    if (key.isGetterDef()){
      replacement = IR.call(replacement);
      replacement.putBooleanProp(Node.FREE_CALL, true);
    }

    n.getParent().replaceChild(n, replacement);
    reportCodeChange();
    return n;
  }
}
