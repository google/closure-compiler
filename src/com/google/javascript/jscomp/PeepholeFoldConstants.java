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
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompDoubles.ecmascriptToInt32;
import static com.google.javascript.jscomp.base.JSCompDoubles.isAtLeastIntegerPrecision;
import static com.google.javascript.jscomp.base.JSCompDoubles.isExactInt32;
import static com.google.javascript.jscomp.base.JSCompDoubles.isExactInt64;
import static com.google.javascript.jscomp.base.JSCompDoubles.isMathematicalInteger;
import static com.google.javascript.jscomp.base.JSCompDoubles.isPositive;

import com.google.javascript.jscomp.NodeUtil.ValueType;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.math.BigInteger;
import org.jspecify.nullness.Nullable;

/**
 * Peephole optimization to fold constants (e.g. x + 1 + 7 --> x + 8).
 */
class PeepholeFoldConstants extends AbstractPeepholeOptimization {

  // TODO(johnlenz): optimizations should not be emiting errors. Move these to
  // a check pass.
  static final DiagnosticType INVALID_GETELEM_INDEX_ERROR =
      DiagnosticType.warning(
          "JSC_INVALID_GETELEM_INDEX_ERROR",
          "Array index not integer: {0}");

  static final DiagnosticType FRACTIONAL_BITWISE_OPERAND =
      DiagnosticType.warning(
          "JSC_FRACTIONAL_BITWISE_OPERAND",
          "Fractional bitwise operand: {0}");

  private static final double MAX_FOLD_NUMBER = Math.pow(2, 53);

  private final boolean late;

  private final boolean shouldUseTypes;

  /**
   * @param late When late is false, this mean we are currently running before
   * most of the other optimizations. In this case we would avoid optimizations
   * that would make the code harder to analyze. When this is true, we would
   * do anything to minimize for size.
   */
  PeepholeFoldConstants(boolean late, boolean shouldUseTypes) {
    this.late = late;
    this.shouldUseTypes = shouldUseTypes;
  }

  @Override
  Node optimizeSubtree(Node subtree) {
    switch (subtree.getToken()) {
      case OPTCHAIN_CALL:
      case CALL:
        return tryFoldCall(subtree);

      case NEW:
        return tryFoldCtorCall(subtree);

      case TYPEOF:
        return tryFoldTypeof(subtree);

      case ARRAYLIT:
      case OBJECTLIT:
        return tryFlattenArrayOrObjectLit(subtree);

      case NOT:
      case POS:
      case NEG:
      case BITNOT:
        tryReduceOperandsForOp(subtree);
        return tryFoldUnaryOperator(subtree);

      case VOID:
        return tryReduceVoid(subtree);

      case OPTCHAIN_GETPROP:
      case GETPROP:
        return tryFoldGetProp(subtree);

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
    switch (subtree.getToken()) {


      case GETELEM:
      case OPTCHAIN_GETELEM:
        return tryFoldGetElem(subtree, left, right);

      case INSTANCEOF:
        return tryFoldInstanceof(subtree, left, right);

      case AND:
      case OR:
        return tryFoldAndOr(subtree, left, right);

      case COALESCE:
        return tryFoldCoalesce(subtree, left, right);

      case LSH:
      case RSH:
      case URSH:
        return tryFoldShift(subtree, left, right);

      case ASSIGN:
        return tryFoldAssign(subtree, left, right);

      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_ADD:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
      case ASSIGN_EXPONENT:
        return tryUnfoldAssignOp(subtree, left, right);

      case ADD:
        return tryFoldAdd(subtree, left, right);

      case SUB:
      case DIV:
      case MOD:
      case EXPONENT:
        return tryFoldArithmeticOp(subtree, left, right);

      case MUL:
      case BITAND:
      case BITOR:
      case BITXOR:
        Node result = tryFoldArithmeticOp(subtree, left, right);
        if (result != subtree) {
          return result;
        }
        return tryFoldLeftChildOp(subtree, left, right);

      case LT:
      case GT:
      case LE:
      case GE:
      case EQ:
      case NE:
      case SHEQ:
      case SHNE:
        return tryFoldComparison(subtree, left, right);

      default:
        return subtree;
    }
  }

  private Node tryReduceVoid(Node n) {
    Node child = n.getFirstChild();
    if ((!child.isNumber() || child.getDouble() != 0.0) && !mayHaveSideEffects(n)) {
      child.replaceWith(IR.number(0));
      reportChangeToEnclosingScope(n);
    }
    return n;
  }

  private void tryReduceOperandsForOp(Node n) {
    switch (n.getToken()) {
      case ADD:
        Node left = n.getFirstChild();
        Node right = n.getLastChild();
        if (!NodeUtil.mayBeString(left, shouldUseTypes)
            && !NodeUtil.mayBeString(right, shouldUseTypes)) {
          tryConvertOperandsToNumber(n);
        }
        break;
      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
        // TODO(johnlenz): convert these to integers.
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_MOD:
      case ASSIGN_DIV:
        tryConvertToNumber(n.getLastChild());
        break;
      case BITNOT:
      case BITOR:
      case BITXOR:
      case BITAND:
      case LSH:
      case RSH:
      case URSH:
      case SUB:
      case MUL:
      case MOD:
      case DIV:
      case POS:
      case NEG:
      case EXPONENT:
        tryConvertOperandsToNumber(n);
        break;
      default:
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
    switch (n.getToken()) {
      case NUMBER:
        // Nothing to do
        return;
      case AND:
      case OR:
      case COMMA:
      case COALESCE:
        tryConvertToNumber(n.getLastChild());
        return;
      case HOOK:
        tryConvertToNumber(n.getSecondChild());
        tryConvertToNumber(n.getLastChild());
        return;
      case NAME:
        if (!NodeUtil.isUndefined(n)) {
          return;
        }
        break;
      default:
        break;
    }

    Double result = getSideEffectFreeNumberValue(n);
    if (result == null) {
      return;
    }

    double value = result;

    Node replacement = NodeUtil.numberNode(value, n);
    if (replacement.isEquivalentTo(n)) {
      return;
    }

    n.replaceWith(replacement);
    reportChangeToEnclosingScope(replacement);
  }

  /**
   * Folds 'typeof(foo)' if foo is a literal, e.g.
   * typeof("bar") --> "string"
   * typeof(6) --> "number"
   */
  private Node tryFoldTypeof(Node originalTypeofNode) {
    checkArgument(originalTypeofNode.isTypeOf());

    Node argumentNode = originalTypeofNode.getFirstChild();
    if (argumentNode == null || !NodeUtil.isLiteralValue(argumentNode, true)) {
      return originalTypeofNode;
    }

    String typeNameString = null;

    switch (argumentNode.getToken()) {
      case FUNCTION:
        typeNameString = "function";
        break;
      case STRINGLIT:
        typeNameString = "string";
        break;
      case NUMBER:
        typeNameString = "number";
        break;
      case TRUE:
      case FALSE:
        typeNameString = "boolean";
        break;
      case NULL:
      case OBJECTLIT:
      case ARRAYLIT:
        typeNameString = "object";
        break;
      case VOID:
        typeNameString = "undefined";
        break;
      case NAME:
        // We assume here that programs don't change the value of the
        // keyword undefined to something other than the value undefined.
        if ("undefined".equals(argumentNode.getString())) {
          typeNameString = "undefined";
        }
        break;
      default:
        break;
    }

    if (typeNameString != null) {
      Node newNode = IR.string(typeNameString);
      reportChangeToEnclosingScope(originalTypeofNode);
      originalTypeofNode.replaceWith(newNode);
      markFunctionsDeleted(originalTypeofNode);

      return newNode;
    }

    return originalTypeofNode;
  }

  private Node tryFoldUnaryOperator(Node n) {
    checkState(n.hasOneChild(), n);

    Node left = n.getFirstChild();
    Node parent = n.getParent();

    if (left == null) {
      return n;
    }

    Tri leftVal = getSideEffectFreeBooleanValue(left);
    if (leftVal == Tri.UNKNOWN) {
      return n;
    }

    switch (n.getToken()) {
      case NOT:
        // Don't fold !0 and !1 back to false.
        if (late && left.isNumber()) {
          double numValue = left.getDouble();
          if (numValue == 0 || numValue == 1) {
            return n;
          }
        }
        Node replacementNode = NodeUtil.booleanNode(!leftVal.toBoolean(true));
        n.replaceWith(replacementNode);
        reportChangeToEnclosingScope(parent);
        return replacementNode;

      case POS:
        if (NodeUtil.isNumericResult(left)) {
          // POS does nothing to numeric values.
          n.replaceWith(left.detach());
          reportChangeToEnclosingScope(parent);
          return left;
        }
        return n;

      case NEG:
        {
          Node result = null;
          if (left.isName() && left.getString().equals("NaN")) {
            result = left.detach(); // "-NaN" is "NaN".
          } else if (left.isNeg()) {
            Node leftLeft = left.getOnlyChild();
            if (leftLeft.isBigInt() || leftLeft.isNumber()) {
              result = leftLeft.detach(); // `-(-4)` is `4`
            }
          }

          if (result != null) {
            n.replaceWith(result);
            reportChangeToEnclosingScope(parent);
            return result;
          }
          return n;
        }

      case BITNOT:
        {
          Double doubleVal = this.getSideEffectFreeNumberValue(left);
          if (doubleVal != null) {
            if (isMathematicalInteger(doubleVal)) {
              int intVal = ecmascriptToInt32(doubleVal);
              Node notIntValNode = NodeUtil.numberNode(~intVal, left);
              n.replaceWith(notIntValNode);
              reportChangeToEnclosingScope(parent);
              return notIntValNode;
            } else {
              report(FRACTIONAL_BITWISE_OPERAND, left);
              return n;
            }
          }

          BigInteger bigintVal = this.getSideEffectFreeBigIntValue(n);
          if (bigintVal != null) {
            Node bigintNotNode = bigintNode(bigintVal, n);
            n.replaceWith(bigintNotNode);
            reportChangeToEnclosingScope(parent);
            return bigintNotNode;
          }

          return n;
        }

        default:
          return n;
    }
  }

  private boolean isReasonableDoubleValue(@Nullable Double x) {
    return x != null && !x.isInfinite() && !x.isNaN();
  }

  /**
   * Try to fold {@code left instanceof right} into {@code true}
   * or {@code false}.
   */
  private Node tryFoldInstanceof(Node n, Node left, Node right) {
    checkArgument(n.isInstanceOf());

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
        n.replaceWith(replacementNode);
        reportChangeToEnclosingScope(replacementNode);
        markFunctionsDeleted(n);
        return replacementNode;
      }
    }

    return n;
  }

  private Node tryFoldAssign(Node n, Node left, Node right) {
    checkArgument(n.isAssign());

    if (!late) {
      return n;
    }

    // Tries to convert x = x + y -> x += y;
    if (!right.hasChildren() || right.getSecondChild() != right.getLastChild()) {
      // RHS must have two children.
      return n;
    }

    if (mayHaveSideEffects(left)) {
      return n;
    }

    Node newRight;
    if (areNodesEqualForInlining(left, right.getFirstChild())) {
      newRight = right.getLastChild();
    } else if (NodeUtil.isCommutative(right.getToken())
        && areNodesEqualForInlining(left, right.getLastChild())) {
      newRight = right.getFirstChild();
    } else {
      return n;
    }

    Token newType = null;
    switch (right.getToken()) {
      case ADD:
        newType = Token.ASSIGN_ADD;
        break;
      case BITAND:
        newType = Token.ASSIGN_BITAND;
        break;
      case BITOR:
        newType = Token.ASSIGN_BITOR;
        break;
      case BITXOR:
        newType = Token.ASSIGN_BITXOR;
        break;
      case DIV:
        newType = Token.ASSIGN_DIV;
        break;
      case LSH:
        newType = Token.ASSIGN_LSH;
        break;
      case MOD:
        newType = Token.ASSIGN_MOD;
        break;
      case MUL:
        newType = Token.ASSIGN_MUL;
        break;
      case RSH:
        newType = Token.ASSIGN_RSH;
        break;
      case SUB:
        newType = Token.ASSIGN_SUB;
        break;
      case URSH:
        newType = Token.ASSIGN_URSH;
        break;
      case EXPONENT:
        newType = Token.ASSIGN_EXPONENT;
        break;
      default:
        return n;
    }

    Node newNode = new Node(newType,
        left.detach(), newRight.detach());
    n.replaceWith(newNode);

    reportChangeToEnclosingScope(newNode);

    return newNode;
  }

  private Node tryUnfoldAssignOp(Node n, Node left, Node right) {
    if (late) {
      return n;
    }

    if (!n.hasChildren() || n.getSecondChild() != n.getLastChild()) {
      return n;
    }

    if (mayHaveSideEffects(left)) {
      return n;
    }

    // Tries to convert x += y -> x = x + y;
    Token op = NodeUtil.getOpFromAssignmentOp(n);
    Node replacement = IR.assign(left.detach(),
        new Node(op, left.cloneTree(), right.detach())
            .srcref(n));
    n.replaceWith(replacement);
    reportChangeToEnclosingScope(replacement);
    return replacement;
  }

  /**
   * Try to fold a AND/OR node.
   */
  private Node tryFoldAndOr(Node n, Node left, Node right) {
    Node parent = n.getParent();

    Node result = null;
    Node dropped = null;

    Token type = n.getToken();

    Tri leftVal = NodeUtil.getBooleanValue(left);

    if (leftVal != Tri.UNKNOWN) {
      boolean lval = leftVal.toBoolean(true);

      // (TRUE || x) => TRUE (also, (3 || x) => 3)
      // (FALSE && x) => FALSE
      if (lval ? type == Token.OR : type == Token.AND) {
        result = left;
        dropped = right;
      } else if (!mayHaveSideEffects(left)) {
        // (FALSE || x) => x
        // (TRUE && x) => x
        result = right;
        dropped = left;
      } else {
        // Left side may have side effects, but we know its boolean value.
        // e.g. true_with_sideeffects || foo() => true_with_sideeffects, foo()
        // or: false_with_sideeffects && foo() => false_with_sideeffects, foo()
        // This, combined with PeepholeRemoveDeadCode, helps reduce expressions
        // like "x() || false || z()".
        n.detachChildren();
        result = IR.comma(left, right);
        dropped = null;
      }
    } else if (parent.getToken() == type && n == parent.getFirstChild()) {
      Tri rightValue = NodeUtil.getBooleanValue(right);
      if (!mayHaveSideEffects(right)) {
        if ((rightValue == Tri.FALSE && type == Token.OR)
            || (rightValue == Tri.TRUE && type == Token.AND)) {
          result = left;
          dropped = right;
        }
      }
    }

    // Note: Right hand side folding is handled by
    // PeepholeMinimizeConditions#tryMinimizeCondition

    if (result != null) {
      // Fold it!
      n.detachChildren();
      n.replaceWith(result);
      reportChangeToEnclosingScope(result);
      if (dropped != null) {
        markFunctionsDeleted(dropped);
      }
      return result;
    } else {
      return n;
    }
  }

  /** Try to fold a COALESCE node. */
  private Node tryFoldCoalesce(Node n, Node left, Node right) {

    Node result = null;

    ValueType leftVal = NodeUtil.getKnownValueType(left);

    switch (leftVal) {
      case NULL:
      case VOID:
        // nullish condition => this expression evaluates to the right side.
        if (!mayHaveSideEffects(left)) {
          result = right;
          markFunctionsDeleted(left);
        } else {
          // e.g. `(a(), null) ?? 1` => `(a(), null, 1)`
          n.detachChildren();
          result = IR.comma(left, right);
        }
        break;
      case NUMBER:
      case BIGINT:
      case STRING:
      case BOOLEAN:
      case OBJECT:
        // non-nullish condition => this expression evaluates to the left side.
        result = left;
        markFunctionsDeleted(right);
        break;
      case UNDETERMINED:
        break;
    }

    if (result != null) {
      // Fold!
      n.detachChildren();
      n.replaceWith(result);
      reportChangeToEnclosingScope(result);
      return result;
    } else {
      return n;
    }
  }

  /**
   * Takes a subtree representing an expression of chained addition between expressions and folds
   * adjacent string addition when possible and safe.
   *
   * @param n root node of ADD expression to be optimized
   * @param left left child of n being added
   * @param right right child of n being added
   * @return AST subtree starting from n that has been optimized to collapse the rightmost left
   *     child leaf node which must be a string literal and the leftmost right child leaf node if
   *     possible to do so in a type safe way.
   */
  private Node tryFoldAdjacentLiteralLeaves(Node n, Node left, Node right) {
    // Find left child's rightmost leaf
    Node leftParent = n;
    Node rightParent = n;
    while (left.isAdd()) {
      // This had better be in a chain of '+' operations
      leftParent = left;
      left = left.getSecondChild();
    }
    // Find right child's leftmost leaf
    while (right.isAdd()) {
      // This had better be in a chain of '+' operations
      rightParent = right;
      right = right.getFirstChild();
    }
    // Try to fold if of the form:
    // ... + <STRINGLITERAL> + <LITERAL>
    // ... + <STRINGLITERAL> + (<LITERAL> + ...)
    // aka. when the literal might be collapsible into the string
    if (leftParent.isAdd()
        && left.isStringLit()
        && rightParent.isAdd()
        && NodeUtil.isLiteralValue(right, /* includeFunctions= */ false)) {
      Node rightGrandparent = rightParent.getParent();
      // `rr` is righthand side term that `right` is being added to and is null if right is still
      // the second child of `n` being added to `left` and non-null if it is in a nested add expr
      Node rr = right.getNext();
      boolean foldIsTypeSafe =
          // If right.getNext() is already a string, folding won't disturb any typecasting
          (rr != null && NodeUtil.isStringResult(rr))
              || (rr != null
                  // If right.getNext() isn't a string result, right must be a string to be folded
                  && right.isStringLit()
                  // Access the right grandparent safely...
                  && rightGrandparent != null
                  && rightGrandparent.isAdd()
                  // The second child of `rightGrandparent` is what `right + rr` is being added to.
                  // If it exists, `right` can only be safely removed if `rr` is still treated as a
                  // string in the resulting addition.
                  && NodeUtil.isStringResult(rightGrandparent.getSecondChild()))
              // Dangling literal term has no typecasting side effects; fold it!
              || (rr == null);
      if (foldIsTypeSafe) {
        String result = left.getString() + NodeUtil.getStringValue(right);
        // If the right parent is the root, shift the left parent up so as not to overwrite the tree
        // Otherwise, shift the right parent up
        if (rightParent.getSecondChild().equals(right)) {
          left.replaceWith(IR.string(result));
          replace(rightParent, rightParent.getFirstChild().cloneTree(true));
        } else {
          left.replaceWith(IR.string(result));
          replace(rightParent, rightParent.getSecondChild().cloneTree(true));
        }
      }
    }
    return n;
  }

  /** Try to fold an ADD node with constant operands */
  private Node tryFoldAddConstantString(Node n, Node left, Node right) {
    if (left.isStringLit() || right.isStringLit() || left.isArrayLit() || right.isArrayLit()) {
      // Add strings.
      String leftString = getSideEffectFreeStringValue(left);
      String rightString = getSideEffectFreeStringValue(right);
      if (leftString != null && rightString != null) {
        Node newStringNode = IR.string(leftString + rightString);
        n.replaceWith(newStringNode);
        reportChangeToEnclosingScope(newStringNode);
        return newStringNode;
      }
    }

    return n;
  }

  /**
   * Try to fold arithmetic binary operators
   */
  private Node tryFoldArithmeticOp(Node n, Node left, Node right) {
    Node result = performArithmeticOp(n, left, right);
    if (result != null) {
      result.srcrefTreeIfMissing(n);
      reportChangeToEnclosingScope(n);
      n.replaceWith(result);
      return result;
    }
    return n;
  }

  /** Try to fold arithmetic binary operators */
  private @Nullable Node performArithmeticOp(Node n, Node left, Node right) {
    // Unlike other operations, ADD operands are not always converted
    // to Number.
    if (n.isAdd()
        && (NodeUtil.mayBeString(left, shouldUseTypes)
            || NodeUtil.mayBeString(right, shouldUseTypes))) {
      return null;
    }

    if (isBigInt(left) && isBigInt(right)) {
      return performBigIntArithmeticOp(n, left, right);
    }

    double result;

    // TODO(johnlenz): Handle NaN with unknown value. BIT ops convert NaN
    // to zero so this is a little awkward here.

    Double lValObj = getSideEffectFreeNumberValue(left);
    Double rValObj = getSideEffectFreeNumberValue(right);
    // at least one of the two operands must have a value and both must be numeric
    if ((lValObj == null && rValObj == null) || !isNumeric(left) || !isNumeric(right)) {
      return null;
    }
    // handle the operations that have algebraic identities, since we can simplify the tree without
    // actually knowing the value statically.
    switch (n.getToken()) {
      case ADD:
        if (lValObj != null && rValObj != null) {
          return maybeReplaceBinaryOpWithNumericResult(lValObj + rValObj, lValObj, rValObj);
        }
        if (lValObj != null && lValObj == 0) {
          return right.cloneTree(true);
        } else if (rValObj != null && rValObj == 0) {
          return left.cloneTree(true);
        }
        return null;
      case SUB:
        if (lValObj != null && rValObj != null) {
          return maybeReplaceBinaryOpWithNumericResult(lValObj - rValObj, lValObj, rValObj);
        }
        if (lValObj != null && lValObj == 0) {
          // 0 - x -> -x
          return IR.neg(right.cloneTree(true));
        } else if (rValObj != null && rValObj == 0) {
          // x - 0 -> x
          return left.cloneTree(true);
        }
        return null;
      case MUL:
        if (lValObj != null && rValObj != null) {
          return maybeReplaceBinaryOpWithNumericResult(lValObj * rValObj, lValObj, rValObj);
        }
        // NOTE: 0*x != 0 for all x, if x==0, then it is NaN.  So we can't take advantage of that
        // without some kind of non-NaN proof.  So the special cases here only deal with 1*x
        if (lValObj != null) {
          if (lValObj == 1) {
            return right.cloneTree(true);
          }
        } else {
          if (rValObj == 1) {
            return left.cloneTree(true);
          }
        }
        return null;
      case DIV:
        if (lValObj != null && rValObj != null) {
          if (rValObj == 0) {
            return null;
          }
          return maybeReplaceBinaryOpWithNumericResult(lValObj / rValObj, lValObj, rValObj);
        }
        // NOTE: 0/x != 0 for all x, if x==0, then it is NaN
        if (rValObj != null) {
          if (rValObj == 1) {
            // x/1->x
            return left.cloneTree(true);
          }
        }
        return null;
      case EXPONENT:
        if (lValObj != null && rValObj != null) {
          return maybeReplaceBinaryOpWithNumericResult(
              Math.pow(lValObj, rValObj), lValObj, rValObj);
        }
        return null;
      default:
        // fall-through
    }
    if (lValObj == null || rValObj == null) {
      return null;
    }
    double lval = lValObj;
    double rval = rValObj;
    switch (n.getToken()) {
      case BITAND:
        result = ecmascriptToInt32(lval) & ecmascriptToInt32(rval);
        break;
      case BITOR:
        result = ecmascriptToInt32(lval) | ecmascriptToInt32(rval);
        break;
      case BITXOR:
        result = ecmascriptToInt32(lval) ^ ecmascriptToInt32(rval);
        break;
      case MOD:
        if (rval == 0) {
          return null;
        }
        result = lval % rval;
        break;
      default:
        throw new IllegalStateException("Unexpected arithmetic operator: " + n.getToken());
    }
    return maybeReplaceBinaryOpWithNumericResult(result, lval, rval);
  }

  private @Nullable Node performBigIntArithmeticOp(Node n, Node left, Node right) {
    BigInteger lVal = getSideEffectFreeBigIntValue(left);
    BigInteger rVal = getSideEffectFreeBigIntValue(right);
    if (lVal != null && rVal != null) {
      switch (n.getToken()) {
        case ADD:
          return bigintNode(lVal.add(rVal), n);
        case SUB:
          return bigintNode(lVal.subtract(rVal), n);
        case MUL:
          return bigintNode(lVal.multiply(rVal), n);
        case DIV:
          if (!rVal.equals(BigInteger.ZERO)) {
            return bigintNode(lVal.divide(rVal), n);
          } else {
            return null;
          }
        case EXPONENT:
          try {
            return bigintNode(lVal.pow(rVal.intValueExact()), n);
          } catch (ArithmeticException exception) {
            return null;
          }
        case MOD:
          return bigintNode(lVal.mod(rVal), n);
        case BITAND:
          return bigintNode(lVal.and(rVal), n);
        case BITOR:
          return bigintNode(lVal.or(rVal), n);
        case BITXOR:
          return bigintNode(lVal.xor(rVal), n);
        default:
          return null;
      }
    }

    // handle the operations that have algebraic identities, since we can simplify the tree without
    // actually knowing the value statically.
    switch (n.getToken()) {
      case ADD:
        if (lVal != null && lVal.equals(BigInteger.ZERO)) {
          return right.cloneTree(/* cloneTypeExprs= */ true);
        } else if (rVal != null && rVal.equals(BigInteger.ZERO)) {
          return left.cloneTree(/* cloneTypeExprs= */ true);
        }
        return null;
      case SUB:
        if (lVal != null && lVal.equals(BigInteger.ZERO)) {
          // 0n - x -> -x
          return IR.neg(right.cloneTree(/* cloneTypeExprs= */ true));
        } else if (rVal != null && rVal.equals(BigInteger.ZERO)) {
          // x - 0n -> x
          return left.cloneTree(/* cloneTypeExprs= */ true);
        }
        return null;
      case MUL:
        if (lVal != null && lVal.equals(BigInteger.ONE)) {
          return right.cloneTree(/* cloneTypeExprs= */ true);
        } else if (rVal != null && rVal.equals(BigInteger.ONE)) {
          return left.cloneTree(/* cloneTypeExprs= */ true);
        }
        return null;
      case DIV:
        if (rVal != null && rVal.equals(BigInteger.ONE)) {
          return left.cloneTree(/* cloneTypeExprs= */ true);
        }
        return null;
      default:
        return null;
    }
  }

  private boolean isNumeric(Node n) {
    if (NodeUtil.isNumericResult(n)) {
      return true;
    }
    if (shouldUseTypes) {
      return n.getColor() != null && n.getColor().equals(StandardColors.NUMBER);
    }
    return false;
  }

  private boolean isBigInt(Node n) {
    if (NodeUtil.isBigIntResult(n)) {
      return true;
    }
    if (shouldUseTypes) {
      return n.getColor() != null && n.getColor().equals(StandardColors.BIGINT);
    }
    return false;
  }

  private @Nullable Node maybeReplaceBinaryOpWithNumericResult(
      double result, double lval, double rval) {
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
   * Expressions such as [foo() * 10 * 20] generate parse trees where no node has two const children
   * ((foo() * 10) * 20), so performArithmeticOp() won't fold it: tryFoldLeftChildOp() will.
   *
   * <p>Specifically, this folds associative expressions where:
   *
   * <ul>
   *   <li>The left child is also an associative expression of the same type.
   *   <li>The right child is a BIGINT or NUMBER constant.
   *   <li>The left child's right child is a BIGINT or NUMBER constant.
   */
  private Node tryFoldLeftChildOp(Node n, Node left, Node right) {
    Token opType = n.getToken();
    checkState((NodeUtil.isAssociative(opType) && NodeUtil.isCommutative(opType)) || n.isAdd());

    checkState(!n.isAdd() || !NodeUtil.mayBeString(n, shouldUseTypes));

    // Use getNumberValue to handle constants like "NaN" and "Infinity"
    // other values are converted to numbers elsewhere.
    Double rightValObj = getSideEffectFreeNumberValue(right);
    BigInteger rightBigInt = getSideEffectFreeBigIntValue(right);
    if ((rightValObj != null || rightBigInt != null) && left.getToken() == opType) {
      checkState(left.hasTwoChildren());

      Node ll = left.getFirstChild();
      Node lr = ll.getNext();

      Node valueToCombine = ll;
      Node replacement = performArithmeticOp(n, valueToCombine, right);
      if (replacement == null) {
        valueToCombine = lr;
        replacement = performArithmeticOp(n, valueToCombine, right);
      }
      if (replacement != null) {
        // Remove the child that has been combined
        valueToCombine.detach();
        // Replace the left op with the remaining child.
        left.replaceWith(left.removeFirstChild());
        // New "-Infinity" node need location info explicitly
        // added.
        replacement.srcrefTreeIfMissing(right);
        right.replaceWith(replacement);
        reportChangeToEnclosingScope(n);
      }
    }

    return n;
  }

  private Node tryFoldAdd(Node node, Node left, Node right) {
    checkArgument(node.isAdd());

    if (NodeUtil.mayBeString(node, shouldUseTypes)) {
      if (NodeUtil.isLiteralValue(left, false) && NodeUtil.isLiteralValue(right, false)) {
        // '6' + 7
        return tryFoldAddConstantString(node, left, right);
      } else {
        if (left.isStringLit() && left.getString().isEmpty() && isStringTyped(right)) {
          return replace(node, right.cloneTree(true));
        } else if (right.isStringLit() && right.getString().isEmpty() && isStringTyped(left)) {
          return replace(node, left.cloneTree(true));
        }
        // a + 7 or 6 + a
        return tryFoldAdjacentLiteralLeaves(node, left, right);
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

  private Node replace(Node oldNode, Node newNode) {
    oldNode.replaceWith(newNode);
    reportChangeToEnclosingScope(newNode);
    return newNode;
  }

  private boolean isStringTyped(Node n) {
    // We could also accept !String, but it is unlikely to be very common.
    if (NodeUtil.isStringResult(n)) {
      return true;
    }
    if (shouldUseTypes) {
      return n.getColor() != null && n.getColor().equals(StandardColors.STRING);
    }
    return false;
  }

  /**
   * Try to fold shift operations
   */
  private Node tryFoldShift(Node n, Node left, Node right) {
    Double leftVal = this.getSideEffectFreeNumberValue(left);
    Double rightVal = this.getSideEffectFreeNumberValue(right);

    if (!isReasonableDoubleValue(leftVal) || !isReasonableDoubleValue(rightVal)) {
      return n;
    }
    if (!isMathematicalInteger(leftVal)) {
      report(FRACTIONAL_BITWISE_OPERAND, left);
      return n;
    }
    if (!isMathematicalInteger(rightVal)) {
      report(FRACTIONAL_BITWISE_OPERAND, right);
      return n;
    }

    // only the lower 5 bits are used when shifting, so don't do anything
    // if the shift amount is outside [0,32)
    if (!(0 <= rightVal && rightVal < 32)) {
      return n;
    }

    int rvalInt = rightVal.intValue();
    int bits = ecmascriptToInt32(leftVal);

    double result;
    switch (n.getToken()) {
      case LSH:
        result = bits << rvalInt;
        break;
      case RSH:
        result = bits >> rvalInt;
        break;
      case URSH:
        // JavaScript always treats the result of >>> as unsigned.
        // We must force Java to do the same here.
        result = 0xffffffffL & (bits >>> rvalInt);
        break;
      default:
        throw new AssertionError("Unknown shift operator: " + n.getToken());
    }

    Node newNumber = NodeUtil.numberNode(result, n);
    reportChangeToEnclosingScope(n);
    n.replaceWith(newNumber);

    return newNumber;
  }

  /**
   * Try to fold comparison nodes, e.g ==
   */
  private Node tryFoldComparison(Node n, Node left, Node right) {
    Tri result = evaluateComparison(this, n.getToken(), left, right);
    if (result == Tri.UNKNOWN) {
      return n;
    }

    Node newNode = NodeUtil.booleanNode(result.toBoolean(true));
    reportChangeToEnclosingScope(n);
    n.replaceWith(newNode);
    markFunctionsDeleted(n);

    return newNode;
  }

  /** https://tc39.es/ecma262/#sec-abstract-relational-comparison */
  private static Tri tryAbstractRelationalComparison(
      AbstractPeepholeOptimization peepholeOptimization,
      Node left,
      Node right,
      boolean willNegate) {
    ValueType leftValueType = NodeUtil.getKnownValueType(left);
    ValueType rightValueType = NodeUtil.getKnownValueType(right);
    // First, check for a string comparison.
    if (leftValueType == ValueType.STRING && rightValueType == ValueType.STRING) {
      String lvStr = peepholeOptimization.getSideEffectFreeStringValue(left);
      String rvStr = peepholeOptimization.getSideEffectFreeStringValue(right);
      if (lvStr != null && rvStr != null) {
        // In JS, browsers parse \v differently. So do not compare strings if one contains \v.
        if (lvStr.indexOf('\u000B') != -1 || rvStr.indexOf('\u000B') != -1) {
          return Tri.UNKNOWN;
        } else {
          return Tri.forBoolean(lvStr.compareTo(rvStr) < 0);
        }
      } else if (left.isTypeOf()
          && right.isTypeOf()
          && left.getFirstChild().isName()
          && right.getFirstChild().isName()
          && left.getFirstChild().getString().equals(right.getFirstChild().getString())) {
        // Special case: `typeof a < typeof a` is always false.
        return Tri.FALSE;
      }
    }

    // Next, try to evaluate based on the value of the node. Try comparing as BigInts first.
    BigInteger lvBig = peepholeOptimization.getSideEffectFreeBigIntValue(left);
    BigInteger rvBig = peepholeOptimization.getSideEffectFreeBigIntValue(right);
    if (lvBig != null && rvBig != null) {
      return Tri.forBoolean(lvBig.compareTo(rvBig) < 0);
    }

    // Then, try comparing as Numbers.
    Double lvNum = peepholeOptimization.getSideEffectFreeNumberValue(left);
    Double rvNum = peepholeOptimization.getSideEffectFreeNumberValue(right);
    if (lvNum != null && rvNum != null) {
      if (Double.isNaN(lvNum) || Double.isNaN(rvNum)) {
        return Tri.forBoolean(willNegate);
      } else {
        return Tri.forBoolean(lvNum.doubleValue() < rvNum.doubleValue());
      }
    }

    // Finally, try comparisons between BigInt and Number.
    if (lvBig != null && rvNum != null) {
      return bigintLessThanDouble(lvBig, rvNum, Tri.FALSE, willNegate);
    }
    if (lvNum != null && rvBig != null) {
      return bigintLessThanDouble(rvBig, lvNum, Tri.TRUE, willNegate);
    }

    // Special case: `x < x` is always false.
    // TODO(moz): If we knew the named value wouldn't be NaN, it would be nice to handle
    // LE and GE. We should use type information if available here.
    if (!willNegate && left.isName() && right.isName()) {
      if (left.getString().equals(right.getString())) {
        return Tri.FALSE;
      }
    }

    return Tri.UNKNOWN;
  }

  private static Tri bigintLessThanDouble(
      BigInteger bigint, double number, Tri invert, boolean willNegate) {
    // if invert is false, then the number is on the right in tryAbstractRelationalComparison
    // if it's true, then the number is on the left
    if (Double.isNaN(number)) {
      return Tri.forBoolean(willNegate);
    } else if (number == Double.POSITIVE_INFINITY) {
      return Tri.TRUE.xor(invert);
    } else if (number == Double.NEGATIVE_INFINITY) {
      return Tri.FALSE.xor(invert);
    } else if (!isAtLeastIntegerPrecision(number)) {
      return Tri.UNKNOWN;
    }

    // long can hold all values within [-2^53, 2^53]
    BigInteger numberAsBigInt = BigInteger.valueOf((long) number);
    int negativeMeansBigintSmaller = bigint.compareTo(numberAsBigInt);
    if (negativeMeansBigintSmaller < 0) {
      return Tri.TRUE.xor(invert);
    } else if (negativeMeansBigintSmaller > 0) {
      return Tri.FALSE.xor(invert);
    } else if (isExactInt64(number)) {
      return Tri.FALSE; // This is the == case, don't invert.
    } else {
      return Tri.forBoolean(isPositive(number)).xor(invert);
    }
  }

  /** http://www.ecma-international.org/ecma-262/6.0/#sec-abstract-equality-comparison */
  private static Tri tryAbstractEqualityComparison(
      AbstractPeepholeOptimization peepholeOptimization, Node left, Node right) {
    // Evaluate based on the general type.
    ValueType leftValueType = NodeUtil.getKnownValueType(left);
    ValueType rightValueType = NodeUtil.getKnownValueType(right);
    if (leftValueType != ValueType.UNDETERMINED && rightValueType != ValueType.UNDETERMINED) {
      // Delegate to strict equality comparison for values of the same type.
      if (leftValueType == rightValueType) {
        return tryStrictEqualityComparison(peepholeOptimization, left, right);
      }

      if ((leftValueType == ValueType.NULL && rightValueType == ValueType.VOID)
          || (leftValueType == ValueType.VOID && rightValueType == ValueType.NULL)) {
        return Tri.TRUE;
      }

      if ((leftValueType == ValueType.NUMBER && rightValueType == ValueType.STRING)
          || rightValueType == ValueType.BOOLEAN) {
        Double rv = peepholeOptimization.getSideEffectFreeNumberValue(right);
        return rv == null
            ? Tri.UNKNOWN
            : tryAbstractEqualityComparison(
                peepholeOptimization, left, NodeUtil.numberNode(rv, right));
      }
      if ((leftValueType == ValueType.STRING && rightValueType == ValueType.NUMBER)
          || leftValueType == ValueType.BOOLEAN) {
        Double lv = peepholeOptimization.getSideEffectFreeNumberValue(left);
        return lv == null
            ? Tri.UNKNOWN
            : tryAbstractEqualityComparison(
                peepholeOptimization, NodeUtil.numberNode(lv, left), right);
      }

      if (leftValueType == ValueType.BIGINT || rightValueType == ValueType.BIGINT) {
        BigInteger lv = peepholeOptimization.getSideEffectFreeBigIntValue(left);
        BigInteger rv = peepholeOptimization.getSideEffectFreeBigIntValue(right);
        if (lv != null && rv != null) {
          return Tri.forBoolean(lv.equals(rv));
        }
      }

      if ((leftValueType == ValueType.STRING || leftValueType == ValueType.NUMBER)
          && rightValueType == ValueType.OBJECT) {
        return Tri.UNKNOWN;
      }
      if (leftValueType == ValueType.OBJECT
          && (rightValueType == ValueType.STRING || rightValueType == ValueType.NUMBER)) {
        return Tri.UNKNOWN;
      }

      return Tri.FALSE;
    }
    // In general, the rest of the cases cannot be folded.
    return Tri.UNKNOWN;
  }

  /** http://www.ecma-international.org/ecma-262/6.0/#sec-strict-equality-comparison */
  private static Tri tryStrictEqualityComparison(
      AbstractPeepholeOptimization peepholeOptimization, Node left, Node right) {
    // First, try to evaluate based on the general type.
    ValueType leftValueType = NodeUtil.getKnownValueType(left);
    ValueType rightValueType = NodeUtil.getKnownValueType(right);
    if (leftValueType != ValueType.UNDETERMINED && rightValueType != ValueType.UNDETERMINED) {
      // Strict equality can only be true for values of the same type.
      if (leftValueType != rightValueType) {
        return Tri.FALSE;
      }
      switch (leftValueType) {
        case VOID:
        case NULL:
          return Tri.TRUE;
        case NUMBER:
          {
            if (NodeUtil.isNaN(left)) {
              return Tri.FALSE;
            }
            if (NodeUtil.isNaN(right)) {
              return Tri.FALSE;
            }
            Double lv = peepholeOptimization.getSideEffectFreeNumberValue(left);
            Double rv = peepholeOptimization.getSideEffectFreeNumberValue(right);
            if (lv != null && rv != null) {
              return Tri.forBoolean(lv.doubleValue() == rv.doubleValue());
            }
            break;
          }
        case STRING:
          {
            String lv = peepholeOptimization.getSideEffectFreeStringValue(left);
            String rv = peepholeOptimization.getSideEffectFreeStringValue(right);
            if (lv != null && rv != null) {
              // In JS, browsers parse \v differently. So do not consider strings
              // equal if one contains \v.
              if (lv.indexOf('\u000B') != -1 || rv.indexOf('\u000B') != -1) {
                return Tri.UNKNOWN;
              } else {
                return lv.equals(rv) ? Tri.TRUE : Tri.FALSE;
              }
            } else if (left.isTypeOf()
                && right.isTypeOf()
                && left.getFirstChild().isName()
                && right.getFirstChild().isName()
                && left.getFirstChild().getString().equals(right.getFirstChild().getString())) {
              // Special case, typeof a == typeof a is always true.
              return Tri.TRUE;
            }
            break;
          }
        case BOOLEAN:
          {
            Tri lv = peepholeOptimization.getSideEffectFreeBooleanValue(left);
            Tri rv = peepholeOptimization.getSideEffectFreeBooleanValue(right);
            return lv.and(rv).or(lv.not().and(rv.not()));
          }
        case BIGINT:
          {
            BigInteger lv = peepholeOptimization.getSideEffectFreeBigIntValue(left);
            BigInteger rv = peepholeOptimization.getSideEffectFreeBigIntValue(right);
            return Tri.forBoolean(lv.equals(rv));
          }
        default: // Symbol and Object cannot be folded in the general case.
          return Tri.UNKNOWN;
      }
    }

    // Then, try to evaluate based on the value of the node. There's only one special case:
    // Any strict equality comparison against NaN returns false.
    if (NodeUtil.isNaN(left) || NodeUtil.isNaN(right)) {
      return Tri.FALSE;
    }
    return Tri.UNKNOWN;
  }

  static Tri evaluateComparison(
      AbstractPeepholeOptimization peepholeOptimization, Token op, Node left, Node right) {
    // Don't try to minimize side-effects here.
    if (peepholeOptimization.mayHaveSideEffects(left)
        || peepholeOptimization.mayHaveSideEffects(right)) {
      return Tri.UNKNOWN;
    }

    switch (op) {
      case EQ:
        return tryAbstractEqualityComparison(peepholeOptimization, left, right);
      case NE:
        return tryAbstractEqualityComparison(peepholeOptimization, left, right).not();
      case SHEQ:
        return tryStrictEqualityComparison(peepholeOptimization, left, right);
      case SHNE:
        return tryStrictEqualityComparison(peepholeOptimization, left, right).not();
      case LT:
        return tryAbstractRelationalComparison(peepholeOptimization, left, right, false);
      case GT:
        return tryAbstractRelationalComparison(peepholeOptimization, right, left, false);
      case LE:
        return tryAbstractRelationalComparison(peepholeOptimization, right, left, true).not();
      case GE:
        return tryAbstractRelationalComparison(peepholeOptimization, left, right, true).not();
      default:
        break;
    }
    throw new IllegalStateException("Unexpected operator for comparison");
  }

  /**
   * Try to fold away unnecessary object instantiation.
   * e.g. this[new String('eval')] -> this.eval
   */
  private Node tryFoldCtorCall(Node n) {
    checkArgument(n.isNew());

    // we can remove this for GETELEM calls (anywhere else?)
    if (inForcedStringContext(n)) {
      return tryFoldInForcedStringContext(n);
    }
    return n;
  }

  /**
   * Remove useless calls:
   *   Object.defineProperties(o, {})  ->  o
   */
  private Node tryFoldCall(Node n) {
    checkArgument(n.isCall() || n.isOptChainCall());

    if (NodeUtil.isObjectDefinePropertiesDefinition(n)) {
      Node srcObj = n.getLastChild();
      if (srcObj.isObjectLit() && !srcObj.hasChildren()) {
        Node parent = n.getParent();
        Node destObj = n.getSecondChild().detach();
        n.replaceWith(destObj);
        reportChangeToEnclosingScope(parent);
      }
    }
    return n;
  }

  /** Returns whether this node must be coerced to a string. */
  private static boolean inForcedStringContext(Node n) {
    if (n.getParent().isGetElem()
        && n.getParent().getLastChild() == n) {
      return true;
    }

    // we can fold in the case "" + new String("")
    return n.getParent().isAdd();
  }

  private Node tryFoldInForcedStringContext(Node n) {
    // For now, we only know how to fold ctors.
    checkArgument(n.isNew());

    Node objectType = n.getFirstChild();
    if (!objectType.isName()) {
      return n;
    }

    if (objectType.getString().equals("String")) {
      Node value = objectType.getNext();
      String stringValue;
      if (value == null) {
        stringValue = "";
      } else {
        stringValue = getSideEffectFreeStringValue(value);
      }

      if (stringValue == null) {
        return n;
      }

      Node parent = n.getParent();
      Node newString = IR.string(stringValue);

      n.replaceWith(newString);
      newString.srcrefIfMissing(parent);
      reportChangeToEnclosingScope(parent);

      return newString;
    }
    return n;
  }

  /**
   * For element access using GETLEM/OPTCHAIN_GETELEM on object literals, arrays or strings, tries
   * to fold the prop access. e.g. folds array-element [1, 2, 3][1];
   */
  private Node tryFoldGetElem(Node n, Node left, Node right) {
    checkArgument(n.isGetElem() || n.isOptChainGetElem());

    if (left.isObjectLit()) {
      if (right.isStringLit()) {
        return tryFoldObjectPropAccess(n, left, right.getString());
      }
    } else if (left.isArrayLit()) {
      return tryFoldArrayAccess(n, left, right);
    } else if (left.isStringLit()) {
      return tryFoldStringArrayAccess(n, left, right);
    }
    return n;
  }

  /**
   * For prop access using GETPROP/OPTCHAIN_GETPROP on object literals, tries to fold their property
   * access. For prop access on arrays, only tries to fold array-length. e.g [1, 2, 3].length ==> 3,
   * [x, y].length ==> 2
   */
  private Node tryFoldGetProp(Node n) {
    checkArgument(n.isGetProp() || n.isOptChainGetProp());
    Node left = n.getFirstChild();
    String name = n.getString();

    if (left.isObjectLit()) {
      return tryFoldObjectPropAccess(n, left, name);
    }

    if (name.equals("length")) {
      int knownLength = -1;
      switch (left.getToken()) {
        case ARRAYLIT:
          if (mayHaveSideEffects(left)) {
            // Nope, can't fold this, without handling the side-effects.
            return n;
          }
          knownLength = left.getChildCount();
          break;
        case STRINGLIT:
          knownLength = left.getString().length();
          break;
        default:
          // Not a foldable case, forget it.
          return n;
      }

      checkState(knownLength != -1);
      Node lengthNode = IR.number(knownLength);
      reportChangeToEnclosingScope(n);
      n.replaceWith(lengthNode);

      return lengthNode;
    }

    return n;
  }

  private Node tryFoldArrayAccess(Node n, Node left, Node right) {
    // If GETPROP/GETELEM is used as assignment target the array literal is
    // acting as a temporary we can't fold it here:
    //    "[][0] += 1"
    if (NodeUtil.isLValue(n)) {
      return n;
    }

    Double index = this.getSideEffectFreeNumberValue(right);
    if (!isReasonableDoubleValue(index)) {
      // Sometimes people like to use complex expressions to index into
      // arrays, or strings to index into array methods.
      return n;
    }

    if (!isExactInt32(index)) {
      // Ideally this should be caught in the check passes.
      report(INVALID_GETELEM_INDEX_ERROR, right);
      return n;
    }

    int intIndex = index.intValue();
    Node current = intIndex >= 0 ? left.getFirstChild() : null;
    Node elem = null;
    for (int i = 0; current != null; i++) {
      if (current.isSpread()) {
        // The only time we can fold getelems with spread is for spread arrays literals, and
        // `tryFlattenArray` already flattens those.
        return n;
      }
      if (i != intIndex) {
        if (mayHaveSideEffects(current)) {
          return n;
        }
      } else {
        elem = current;
      }

      current = current.getNext();
    }

    if (elem == null) { // If the index was out of bounds
      elem = NodeUtil.newUndefinedNode(left);
    } else if (elem.isEmpty()) {
      elem = NodeUtil.newUndefinedNode(elem);
    } else {
      elem.detach();
    }

    // Replace the entire GETELEM with the value
    n.replaceWith(elem);
    reportChangeToEnclosingScope(elem);
    return elem;
  }

  /**
   * Flattens array- or object-literals that contain spreads of other literals.
   *
   * <p>Does not recurse into nested spreads because this method is already called as part of a
   * postorder traversal and nested spreads will already have been flattened.
   *
   * <p>Example: `[0, ...[1, 2, 3], 4, ...[5]]` => `[0, 1, 2, 3, 4, 5]`
   */
  private Node tryFlattenArrayOrObjectLit(Node parentLit) {
    for (Node child = parentLit.getFirstChild(); child != null; ) {
      // We have to store the next element here because nodes may be inserted below.
      Node spread = child;
      child = child.getNext();

      if (!spread.isSpread()) {
        continue;
      }

      Node innerLit = spread.getOnlyChild();
      if (!parentLit.getToken().equals(innerLit.getToken())) {
        continue; // We only want to inline arrays into arrays and objects into objects.
      }

      parentLit.addChildrenAfter(innerLit.removeChildren(), spread);
      spread.detach();
      reportChangeToEnclosingScope(parentLit);
    }
    return parentLit;
  }

  private Node tryFoldStringArrayAccess(Node n, Node left, Node right) {
    // If GETPROP/GETELEM is used as assignment target the array literal is
    // acting as a temporary we can't fold it here:
    //    "[][0] += 1"
    if (NodeUtil.isLValue(n)) {
      return n;
    }

    Double index = this.getSideEffectFreeNumberValue(right);
    if (!isReasonableDoubleValue(index)) {
      // Sometimes people like to use complex expressions to index into
      // arrays, or strings to index into array methods.
      return n;
    }

    int intIndex = index.intValue();
    if (intIndex != index) {
      report(INVALID_GETELEM_INDEX_ERROR, right);
      return n;
    }

    checkState(left.isStringLit());
    String value = left.getString();
    if (intIndex < 0 || intIndex >= value.length()) {
      Node undefined = NodeUtil.newUndefinedNode(left);
      n.replaceWith(undefined);
      reportChangeToEnclosingScope(undefined);
      return undefined;
    }

    char c = 0;
    // Note: For now skip the strings with unicode
    // characters as I don't understand the differences
    // between Java and JavaScript.
    for (int i = 0; i <= intIndex; i++) {
      c = value.charAt(i);
      if (c < 32 || c > 127) {
        return n;
      }
    }
    Node elem = IR.string(Character.toString(c));

    // Replace the entire GETELEM with the value
    n.replaceWith(elem);
    reportChangeToEnclosingScope(elem);
    return elem;
  }

  /**
   * Tries to fold the node `n` that's accessing an object literal's property. Bails out (skips
   * folding and returns the same `n` node) with certainty when any of the following holds true:
   *
   * <ul>
   *   <li>the access `n` is L-value
   *   <li>the property references super
   *   <li>the object has side-effects other than those preserved by folding the property
   *   <li>property accessed does not exist on the object (might exist on prototype)
   * </ul>
   *
   * <ul>
   *   Examples of folding:
   *   <li>`({a() { return 1; }})?.a` ---> `(function() { return 1; })`
   *   <li>`({a() { return 1; }}).a()` ---> `(function() { return 1; }())`
   * </ul>
   */
  private Node tryFoldObjectPropAccess(Node n, Node left, String name) {
    checkArgument(NodeUtil.isNormalOrOptChainGet(n));

    if (!left.isObjectLit()) {
      return n;
    }

    if (NodeUtil.isLValue(n)) {
      // If GETPROP/GETELEM is used as assignment target the object literal is
      // acting as a temporary we can't fold it here:
      //    "{a:x}.a += 1" is not "x += 1"
      checkState(!NodeUtil.isOptChainNode(n)); // optional chains can not be targets of assign
      return n;
    }

    // find the last definition in the object literal
    Node key = null;
    Node value = null;
    for (Node c = left.getFirstChild(); c != null; c = c.getNext()) {
      switch (c.getToken()) {
        case SETTER_DEF:
          break;

        case OBJECT_SPREAD:
          // Reset the search because spread could overwrite any previous result.
          key = null;
          value = null;
          break;

        case COMPUTED_PROP:
          // don't handle computed properties unless the input is a simple string
          Node prop = c.getFirstChild();
          if (!prop.isStringLit()) {
            return n;
          }
          if (prop.getString().equals(name)) {
            key = c;
            value = c.getSecondChild();
          }
          break;

        case GETTER_DEF:
        case STRING_KEY:
        case MEMBER_FUNCTION_DEF:
          if (c.getString().equals(name)) {
            key = c;
            value = key.getFirstChild();
          }
          break;

        default:
          throw new IllegalStateException();
      }
    }

    // Didn't find a definition of the name in the object literal, it might
    // be coming from the Object prototype.
    if (value == null) {
      return n;
    }

    /** `super` captures a hidden reference to the declaring objectlit, so we can't fold it away. */
    if (NodeUtil.referencesSuper(value)) {
      return n;
    }

    /**
     * Check to see if there are any side-effects to this object-literal.
     *
     * <p>We remove the value we're going to use because its side-effects will be preserved.
     */
    Node tempValue = IR.nullNode();
    value.replaceWith(tempValue);
    boolean hasSideEffectBesidesValue = mayHaveSideEffects(left);
    tempValue.replaceWith(value);
    if (hasSideEffectBesidesValue) {
      return n;
    }

    boolean keyIsGetter = NodeUtil.isGetOrSetKey(key);
    boolean nIsInvoked = NodeUtil.isInvocationTarget(n);

    if (keyIsGetter || nIsInvoked) {
      // When the code looks like:
      //   {x: f}.x();
      //   {get x() {...}}.x;
      //   {get ['x']() {...}}.x;

      /**
       * It's not safe, in general, to convert that to just a function call, because the receiver
       * value will be wrong.
       *
       * <p>However, there are some cases where it's ok which we check here.
       */
      if (!value.isFunction() || NodeUtil.referencesOwnReceiver(value)) {
        return n;
      }
    }

    value.detach();

    Node parent = n.getParent();

    if (NodeUtil.isOptChainNode(parent)) {

      /**
       * If the chain continues after `n`, simply doing `n.replaceWith(value)` below would leave the
       * subsequent nodes in the current chain segment optional, with their start `n` replaced.
       *
       * <p>So, we must ensure that all nodes in the chain's current segment are made non-optional.
       *
       * <p>This can happen for e.g.
       *
       * <ul>
       *   <li>`({a() { return 1; }})?.a()` ---> `(function() { return 1; })()`. Here, parent
       *       OPTCHAIN_CALL node must be converted to CALL.
       *   <li>`({a() { return 1; }})?.a().b.c?.d` ---> `(function() { return 1; })().b.c?.d`. Here,
       *       all nodes upto `({a() { return 1; }})?.a().b.c` must become non-optional
       * </ul>
       */
      Node endOfCurrentChain = NodeUtil.getEndOfOptChainSegment(parent);
      NodeUtil.convertToNonOptionalChainSegment(endOfCurrentChain);
    }
    if (keyIsGetter) {
      value = IR.call(value);
      value.putBooleanProp(Node.FREE_CALL, true);
    } else if (nIsInvoked) {
      n.getParent().putBooleanProp(Node.FREE_CALL, true);
    }

    n.replaceWith(value);
    reportChangeToEnclosingScope(value);
    markFunctionsDeleted(n);
    return n;
  }

  private static Node bigintNode(BigInteger val, Node srcref) {
    Node tree = (val.signum() < 0) ? IR.neg(IR.bigint(val.negate())) : IR.bigint(val);
    return tree.srcrefTree(srcref);
  }
}
