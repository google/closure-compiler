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
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

import java.util.List;

/**
 * Peephole optimization to fold constants (e.g. x + 1 + 7 --> x + 8).
 *
*
*
 */
public class PeepholeFoldConstants extends AbstractPeepholeOptimization {

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

  private static final double MAX_FOLD_NUMBER = Math.pow(2, 53);

  @Override
  Node optimizeSubtree(Node subtree) {
    switch(subtree.getType()) {
      case Token.CALL:
        return tryFoldKnownMethods(subtree);

      case Token.TYPEOF:
        return tryFoldTypeof(subtree);

      case Token.NOT:
      case Token.NEG:
      case Token.BITNOT:
        return tryFoldUnaryOperator(subtree);

      default:
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

      case Token.BITAND:
      case Token.BITOR:
        return tryFoldBitAndOr(subtree, left, right);

      case Token.LSH:
      case Token.RSH:
      case Token.URSH:
        return tryFoldShift(subtree, left, right);

      case Token.ASSIGN:
        return tryFoldAssign(subtree, left, right);

      case Token.ADD:
        return tryFoldAdd(subtree, left, right);

      case Token.SUB:
      case Token.MUL:
      case Token.DIV:
        return tryFoldArithmetic(subtree, left, right);

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

  /**
   * Folds 'typeof(foo)' if foo is a literal, e.g.
   * typeof("bar") --> "string"
   * typeof(6) --> "number"
   */
  private Node tryFoldTypeof(Node originalTypeofNode) {
    Preconditions.checkArgument(originalTypeofNode.getType() == Token.TYPEOF);

    Node argumentNode = originalTypeofNode.getFirstChild();
    if (argumentNode == null || !NodeUtil.isLiteralValue(argumentNode)) {
      return originalTypeofNode;
    }

    String typeNameString = null;

    switch (argumentNode.getType()) {
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
      case Token.NAME:
        // We assume here that programs don't change the value of the
        // keyword undefined to something other than the value undefined.
        if ("undefined".equals(argumentNode.getString())) {
          typeNameString = "undefined";
        }
        break;
    }

    if (typeNameString != null) {
      Node newNode = Node.newString(typeNameString);
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

    // TODO(dcc): Just dropping the unary op makes some tests
    // (e.g. PeepholeIntegration.testMinimizeExpr) very confusing because it
    // leads to transformations like "!!true" --> "!false" --> "false".
    // Do we really need to do this here?

    if (NodeUtil.isExpressionNode(parent)) {
      // If the value isn't used, then just throw
      // away the operator
      parent.replaceChild(n, n.removeFirstChild());
      reportCodeChange();
      return null;
    }

    TernaryValue leftVal = NodeUtil.getBooleanValue(left);
    if (leftVal == TernaryValue.UNKNOWN) {
      return n;
    }

    switch (n.getType()) {
      case Token.NOT:
        int result = leftVal.toBoolean(true) ? Token.FALSE : Token.TRUE;
        Node replacementNode = new Node(result);
        parent.replaceChild(n, replacementNode);
        reportCodeChange();
        return replacementNode;
      case Token.NEG:
        try {
          if (left.getType() == Token.NAME) {
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

          double negNum = -left.getDouble();

          Node negNumNode = Node.newNumber(negNum);
          parent.replaceChild(n, negNumNode);
          reportCodeChange();
          return negNumNode;
        } catch (UnsupportedOperationException ex) {
          // left is not a number node, so do not replace, but warn the
          // user because they can't be doing anything good
          error(NEGATING_A_NON_NUMBER_ERROR, left);
          return n;
        }
      case Token.BITNOT:
        try {
          double val = left.getDouble();
          if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
            int intVal = (int) val;
            if (intVal == val) {
              Node notIntValNode = Node.newNumber(~intVal);
              parent.replaceChild(n, notIntValNode);
              reportCodeChange();
              return notIntValNode;
            } else {
              error(FRACTIONAL_BITWISE_OPERAND, left);
              return n;
            }
          } else {
            error(BITWISE_OPERAND_OUT_OF_RANGE, left);
            return n;
          }
        } catch (UnsupportedOperationException ex) {
          // left is not a number node, so do not replace, but warn the
          // user because they can't be doing anything good
          error(NEGATING_A_NON_NUMBER_ERROR, left);
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
    Preconditions.checkArgument(n.getType() == Token.INSTANCEOF);

    // TODO(johnlenz) Use type information if available to fold
    // instanceof.
    if (NodeUtil.isLiteralValue(left)
        && !NodeUtil.mayHaveSideEffects(right)) {

      Node replacementNode = null;

      if (NodeUtil.isImmutableValue(left)) {
        // Non-object types are never instances.
        replacementNode = new Node(Token.FALSE);
      } else if (right.getType() == Token.NAME
          && "Object".equals(right.getString())) {
        replacementNode = new Node(Token.TRUE);
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
    Preconditions.checkArgument(n.getType() == Token.ASSIGN);

    // Tries to convert x = x + y -> x += y;
    if (!right.hasChildren() ||
        right.getFirstChild().getNext() != right.getLastChild()) {
      // RHS must have two children.
      return n;
    }

    if (NodeUtil.mayHaveSideEffects(left)) {
      return n;
    }

    Node leftChild = right.getFirstChild();
    if (!areNodesEqualForInlining(left, leftChild)) {
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
        left.detachFromParent(), right.getLastChild().detachFromParent());
    n.getParent().replaceChild(n, newNode);

    reportCodeChange();

    return newNode;
  }

  /**
   * Try to fold a AND/OR node.
   */
  private Node tryFoldAndOr(Node n, Node left, Node right) {
    Node parent = n.getParent();

    Node result = null;

    int type = n.getType();

    TernaryValue leftVal = NodeUtil.getBooleanValue(left);

    if (leftVal != TernaryValue.UNKNOWN) {
      boolean lval = leftVal.toBoolean(true);

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
    } else {
      TernaryValue rightVal = NodeUtil.getBooleanValue(right);
      if (rightVal != TernaryValue.UNKNOWN) {

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
        boolean rval = rightVal.toBoolean(true);

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
    }

    // Note: The parser parses 'x && FALSE && y' as 'x && (FALSE && y)', so
    // there is not much need to worry about const values on left's
    // right child.

    if (result != null) {
      // Fold it!
      n.removeChild(result);
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
   * Specifically it folds Add exprssions where:
   *  - The left child is also and add expression
   *  - The right child is a constant value
   *  - The left child's right child is a STRING constant.
   *
   * WARNING: If javascript ever adds operator overloading, this will
   * probably stop being correct.
   */
  private Node tryFoldLeftChildAdd(Node n, Node left, Node right) {

    if (NodeUtil.isLiteralValue(right) &&
        left.getType() == Token.ADD &&
        left.getChildCount() == 2) {

      Node ll = left.getFirstChild();
      Node lr = ll.getNext();

      // Left's right child MUST be a string. We would not want to fold
      // foo() + 2 + 'a' because we don't know what foo() will return, and
      // therefore we don't know if left is a string concat, or a numeric add.
      if (lr.getType() != Token.STRING) {
        return n;
      }

      String leftString = NodeUtil.getStringValue(lr);
      String rightString = NodeUtil.getStringValue(right);
      if (leftString != null && rightString != null) {
        left.removeChild(ll);
        String result = leftString + rightString;
        n.replaceChild(left, ll);
        n.replaceChild(right, Node.newString(result));
        reportCodeChange();
      }
    }

    return n;
  }

  /**
   * Try to fold an ADD node with constant operands
   */
  private Node tryFoldAddConstant(Node n, Node left, Node right) {
    if (left.getType() == Token.STRING ||
        right.getType() == Token.STRING) {

      // Add strings.
      String leftString = NodeUtil.getStringValue(left);
      String rightString = NodeUtil.getStringValue(right);
      if (leftString != null && rightString != null) {
        Node newStringNode = Node.newString(leftString + rightString);
        n.getParent().replaceChild(n, newStringNode);
        reportCodeChange();
        return newStringNode;
      }
    } else {
      // Try arithmetic add
      return tryFoldArithmetic(n, left, right);
    }

    return n;
  }

  /**
   * Try to fold arithmetic binary operators
   */
  private Node tryFoldArithmetic(Node n, Node left, Node right) {
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
            error(DIVIDE_BY_0_ERROR, right);
            return n;
          }
          result = lval / rval;
          break;
        default:
          throw new Error("Unknown arithmetic operator");
      }

      // length of the left and right value plus 1 byte for the operator.
      if (String.valueOf(result).length() <=
          String.valueOf(lval).length() + String.valueOf(rval).length() + 1 &&

          // Do not try to fold arithmetic for numbers > 2^53. After that
          // point, fixed-point math starts to break down and become inaccurate.
          Math.abs(result) <= MAX_FOLD_NUMBER) {
        Node newNumber = Node.newNumber(result);
        n.getParent().replaceChild(n, newNumber);
        reportCodeChange();
        return newNumber;
      }
   }
    return n;
  }

  private Node tryFoldAdd(Node node, Node left, Node right) {
    Preconditions.checkArgument(node.getType() == Token.ADD);

    if (NodeUtil.isLiteralValue(left) && NodeUtil.isLiteralValue(right)) {
      // 6 + 7
      return tryFoldAddConstant(node, left, right);
    } else {
      // a + 7 or 6 + a
      return tryFoldLeftChildAdd(node, left, right);
    }
  }

  /**
   * Try to fold arithmetic binary operators
   */
  private Node tryFoldBitAndOr(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.getType() == Token.BITAND
        || n.getType() == Token.BITOR);

    if (left.getType() == Token.NUMBER &&
        right.getType() == Token.NUMBER) {
      double resultDouble;
      double lval = left.getDouble();
      double rval = right.getDouble();

      // For now, we are being extra conservative, and only folding ints in
      // the range MIN_VALUE-MAX_VALUE
      if (lval < Integer.MIN_VALUE || lval > Integer.MAX_VALUE ||
          rval < Integer.MIN_VALUE || rval > Integer.MAX_VALUE) {

        // Fall back through and let the javascript use the larger values
        return n;
      }

      // Convert the numbers to ints
      int lvalInt = (int) lval;
      if (lvalInt != lval) {
        return n;
      }

      int rvalInt = (int) rval;
      if (rvalInt != rval) {
        return n;
      }

      switch (n.getType()) {
        case Token.BITAND:
          resultDouble = lvalInt & rvalInt;
          break;
        case Token.BITOR:
          resultDouble = lvalInt | rvalInt;
          break;
        default:
          throw new Error("Unknown bitwise operator");
      }

      Node newNumber = Node.newNumber(resultDouble);
      n.getParent().replaceChild(n, newNumber);
      reportCodeChange();
    }

    return n;
  }

  /**
   * Try to fold shift operations
   */
  private Node tryFoldShift(Node n, Node left, Node right) {
    if (left.getType() == Token.NUMBER &&
        right.getType() == Token.NUMBER) {

      double result;
      double lval = left.getDouble();
      double rval = right.getDouble();

      // check ranges.  We do not do anything that would clip the double to
      // a 32-bit range, since the user likely does not intend that.
      if (!(lval >= Integer.MIN_VALUE && lval <= Integer.MAX_VALUE)) {
        error(BITWISE_OPERAND_OUT_OF_RANGE, left);
        return n;
      }

      // only the lower 5 bits are used when shifting, so don't do anything
      // if the shift amount is outside [0,32)
      if (!(rval >= 0 && rval < 32)) {
        error(SHIFT_AMOUNT_OUT_OF_BOUNDS, right);
        return n;
      }

      // Convert the numbers to ints
      int lvalInt = (int) lval;
      if (lvalInt != lval) {
        error(FRACTIONAL_BITWISE_OPERAND, left);
        return n;
      }

      int rvalInt = (int) rval;
      if (rvalInt != rval) {
        error(FRACTIONAL_BITWISE_OPERAND, right);
        return n;
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

      Node newNumber = Node.newNumber(result);
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
    if (!NodeUtil.isLiteralValue(left) || !NodeUtil.isLiteralValue(right)) {
      // We only handle non-literal operands for LT and GT.
      if (n.getType() != Token.GT && n.getType() != Token.LT) {
        return n;
      }
    }

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
          return n;
        } else if (!rightLiteral) {
          return n;
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
              return n;
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
            tt != Token.NULL) {
          return n;
        }
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
            return n;  // we only handle == and != here
        }
        break;

      case Token.STRING:
        if (undefinedRight) {
          result = false;
          break;
        }
        if (Token.STRING != right.getType()) {
          return n;  // Only eval if they are the same type
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
            return n;  // we only handle == and != here
        }
        break;

      case Token.NUMBER:
        if (undefinedRight) {
          result = false;
          break;
        }
        if (Token.NUMBER != right.getType()) {
          return n;  // Only eval if they are the same type
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
            return n;  // don't handle that op
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
                return n;
            }
            break;
          }
        }

        if (Token.NAME != right.getType()) {
          return n;  // Only eval if they are the same type
        }
        String ln = left.getString();
        String rn = right.getString();
        if (!ln.equals(rn)) {
          return n;  // Not the same value name.
        }

        switch (op) {
          // If we knew the named value wouldn't be NaN, it would be nice
          // to handle EQ,NE,LE,GE,SHEQ, and SHNE.
          case Token.LT:
          case Token.GT:
            result = false;
            break;
          default:
            return n;  // don't handle that op
        }
        break;

      default:
        // assert, this should cover all consts
        return n;
    }

    Node newNode = new Node(result ? Token.TRUE : Token.FALSE);
    n.getParent().replaceChild(n, newNode);
    reportCodeChange();

    return newNode;
  }

  private Node tryFoldKnownMethods(Node subtree) {
    // For now we only support .join() and .indexOf()

    subtree = tryFoldStringJoin(subtree);

    if (subtree.getType() == Token.CALL) {
      subtree = tryFoldStringIndexOf(subtree);
    }

    return subtree;
  }

  /**
   * Try to evaluate String.indexOf/lastIndexOf:
   *     "abcdef".indexOf("bc") -> 1
   *     "abcdefbc".indexOf("bc", 3) -> 6
   */
  private Node tryFoldStringIndexOf(Node n) {
    Preconditions.checkArgument(n.getType() == Token.CALL);

    Node left = n.getFirstChild();

    if (left == null) {
      return n;
    }

    Node right = left.getNext();

    if (right == null) {
      return n;
    }

    if (!NodeUtil.isGetProp(left) || !NodeUtil.isImmutableValue(right)) {
      return n;
    }

    Node lstringNode = left.getFirstChild();
    Node functionName = lstringNode.getNext();

    if ((lstringNode.getType() != Token.STRING) ||
        (!functionName.getString().equals("indexOf") &&
        !functionName.getString().equals("lastIndexOf"))) {
      return n;
    }

    String lstring = NodeUtil.getStringValue(lstringNode);
    boolean isIndexOf = functionName.getString().equals("indexOf");
    Node firstArg = right;
    Node secondArg = right.getNext();
    String searchValue = NodeUtil.getStringValue(firstArg);
    // searchValue must be a valid string.
    if (searchValue == null) {
      return n;
    }
    int fromIndex = isIndexOf ? 0 : lstring.length();
    if (secondArg != null) {
      // Third-argument and non-numeric second arg are problematic. Discard.
      if ((secondArg.getNext() != null) ||
          (secondArg.getType() != Token.NUMBER)) {
        return n;
      } else {
        fromIndex = (int) secondArg.getDouble();
      }
    }
    int indexVal = isIndexOf ? lstring.indexOf(searchValue, fromIndex)
                             : lstring.lastIndexOf(searchValue, fromIndex);
    Node newNode = Node.newNumber(indexVal);
    n.getParent().replaceChild(n, newNode);

    reportCodeChange();

    return newNode;
  }

  /**
   * Try to fold an array join: ['a', 'b', 'c'].join('') -> 'abc';
   */
  private Node tryFoldStringJoin(Node n) {
    Node left = n.getFirstChild();

    if (left == null) {
      return n;
    }

    Node right = left.getNext();

    if (right == null) {
      return n;
    }

    if (!NodeUtil.isGetProp(left) || !NodeUtil.isImmutableValue(right)) {
      return n;
    }

    Node arrayNode = left.getFirstChild();
    Node functionName = arrayNode.getNext();

    if ((arrayNode.getType() != Token.ARRAYLIT) ||
        !functionName.getString().equals("join")) {
      return n;
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
        n.getParent().replaceChild(n, emptyStringNode);
        reportCodeChange();
        return emptyStringNode;
      case 1:
        Node foldedStringNode = arrayFoldedChildren.remove(0);
        if (foldedSize > originalSize) {
          return n;
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
        n.getParent().replaceChild(n, foldedStringNode);
        reportCodeChange();
        return foldedStringNode;
      default:
        // No folding could actually be performed.
        if (arrayFoldedChildren.size() == arrayNode.getChildCount()) {
          return n;
        }
        int kJoinOverhead = "[].join()".length();
        foldedSize += kJoinOverhead;
        foldedSize += InlineCostEstimator.getCost(right);
        if (foldedSize > originalSize) {
          return n;
        }
        arrayNode.detachChildren();
        for (Node node : arrayFoldedChildren) {
          arrayNode.addChildToBack(node);
        }
        reportCodeChange();
        break;
    }

    return n;
  }

  /**
   * Try to fold array-element. e.g [1, 2, 3][10];
   */
  private Node tryFoldGetElem(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.getType() == Token.GETELEM);

    if (left.getType() == Token.ARRAYLIT) {

      if (right.getType() != Token.NUMBER) {
        // Sometimes people like to use complex expressions to index into
        // arrays, or strings to index into array methods.
        return n;
      }

      double index = right.getDouble();
      int intIndex = (int) index;
      if (intIndex != index) {
        error(INVALID_GETELEM_INDEX_ERROR, right);
        return n;
      }

      if (intIndex < 0) {
        error(INDEX_OUT_OF_BOUNDS_ERROR, right);
        return n;
      }

      Node elem = left.getFirstChild();
      for (int i = 0; elem != null && i < intIndex; i++) {
        elem = elem.getNext();
      }

      if (elem == null) {
        error(INDEX_OUT_OF_BOUNDS_ERROR, right);
        return n;
      }

      // Replace the entire GETELEM with the value
      left.removeChild(elem);
      n.getParent().replaceChild(n, elem);
      reportCodeChange();
      return elem;
    }
    return n;
  }

  /**
   * Try to fold array-length. e.g [1, 2, 3].length ==> 3, [x, y].length ==> 2
   */
  private Node tryFoldGetProp(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.getType() == Token.GETPROP);

    if (right.getType() == Token.STRING &&
        right.getString().equals("length")) {
      int knownLength = -1;
      switch (left.getType()) {
        case Token.ARRAYLIT:
          if (NodeUtil.mayHaveSideEffects(left)) {
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
      Node lengthNode = Node.newNumber(knownLength);
      n.getParent().replaceChild(n, lengthNode);
      reportCodeChange();

      return lengthNode;
    }

    return n;
  }

}
