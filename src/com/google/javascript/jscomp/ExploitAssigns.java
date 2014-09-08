/*
 * Copyright 2006 The Closure Compiler Authors.
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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Tries to chain assignments together.
 *
 * @author nicksantos@google.com (Nick Santos)
 *
 */
class ExploitAssigns extends AbstractPeepholeOptimization {

  @Override
  Node optimizeSubtree(Node subtree) {
    for (Node child = subtree.getFirstChild(); child != null;) {
      Node next = child.getNext();
      if (NodeUtil.isExprAssign(child)) {
        collapseAssign(child.getFirstChild(), child, subtree);
      }
      child = next;
    }
    return subtree;
  }

  /**
   * Try to collapse the given assign into subsequent expressions.
   */
  private void collapseAssign(Node assign, Node expr,
      Node exprParent) {
    Node leftValue = assign.getFirstChild();
    Node rightValue = leftValue.getNext();
    if (isCollapsibleValue(leftValue, true) &&
        collapseAssignEqualTo(expr, exprParent, leftValue)) {
      reportCodeChange();
    } else if (isCollapsibleValue(rightValue, false) &&
        collapseAssignEqualTo(expr, exprParent, rightValue)) {
      reportCodeChange();
    } else if (rightValue.isAssign()) {
      // Recursively deal with nested assigns.
      collapseAssign(rightValue, expr, exprParent);
    }
  }

  /**
   * Determines whether we know enough about the given value to be able
   * to collapse it into subsequent expressions.
   *
   * For example, we can collapse booleans and variable names:
   * <code>
   * x = 3; y = x; // y = x = 3;
   * a = true; b = true; // b = a = true;
   * <code>
   * But we won't try to collapse complex expressions.
   *
   * @param value The value node.
   * @param isLValue Whether it's on the left-hand side of an expr.
   */
  private static boolean isCollapsibleValue(Node value, boolean isLValue) {
    switch (value.getType()) {
      case Token.GETPROP:
        // Do not collapse GETPROPs on arbitrary objects, because
        // they may be implemented setter functions, and oftentimes
        // setter functions fail on native objects. This is OK for "THIS"
        // objects, because we assume that they are non-native.
        return !isLValue || value.getFirstChild().isThis();

      case Token.NAME:
        return true;

      default:
        return NodeUtil.isImmutableValue(value);
    }
  }

  /**
   * Collapse the given assign expression into the expression directly
   * following it, if possible.
   *
   * @param expr The expression that may be moved.
   * @param exprParent The parent of {@code expr}.
   * @param value The value of this expression, expressed as a node. Each
   *     expression may have multiple values, so this function may be called
   *     multiple times for the same expression. For example,
   *     <code>
   *     a = true;
   *     </code>
   *     is equal to the name "a" and the boolean "true".
   * @return Whether the expression was collapsed successfully.
   */
  private boolean collapseAssignEqualTo(Node expr, Node exprParent,
      Node value) {
    Node assign = expr.getFirstChild();
    Node parent = exprParent;
    Node next = expr.getNext();
    while (next != null) {
      switch (next.getType()) {
        case Token.AND:
        case Token.OR:
        case Token.HOOK:
        case Token.IF:
        case Token.RETURN:
        case Token.EXPR_RESULT:
          // Dive down the left side
          parent = next;
          next = next.getFirstChild();
          break;

        case Token.VAR:
          if (next.getFirstChild().hasChildren()) {
            parent = next.getFirstChild();
            next = parent.getFirstChild();
            break;
          }
          return false;

        case Token.GETPROP:
        case Token.NAME:
          if (next.isQualifiedName()) {
            if (value.isQualifiedName() &&
                next.matchesQualifiedName(value)) {
              // If the previous expression evaluates to value of a
              // qualified name, and that qualified name is used again
              // shortly, then we can exploit the assign here.

              // Verify the assignment doesn't change its own value.
              if (!isSafeReplacement(next, assign)) {
                return false;
              }

              exprParent.removeChild(expr);
              expr.removeChild(assign);
              parent.replaceChild(next, assign);
              return true;
            }
          }
          return false;

        case Token.ASSIGN:
          // Assigns are really tricky. In lots of cases, we want to inline
          // into the right side of the assign. But the left side of the
          // assign is evaluated first, and it may have convoluted logic:
          //   a = null;
          //   (a = b).c = null;
          // We don't want to exploit the first assign. Similarly:
          //   a.b = null;
          //   a.b.c = null;
          // We don't want to exploit the first assign either.
          //
          // To protect against this, we simply only inline when the left side
          // is guaranteed to evaluate to the same L-value no matter what.
          Node leftSide = next.getFirstChild();
          if (leftSide.isName() ||
              leftSide.isGetProp() &&
              leftSide.getFirstChild().isThis()) {
            // Dive down the right side of the assign.
            parent = next;
            next = leftSide.getNext();
            break;
          } else {
            return false;
          }

        default:
          if (NodeUtil.isImmutableValue(next)
              && next.isEquivalentTo(value)) {
            // If the r-value of the expr assign is an immutable value,
            // and the value is used again shortly, then we can exploit
            // the assign here.
            exprParent.removeChild(expr);
            expr.removeChild(assign);
            parent.replaceChild(next, assign);
            return true;
          }
          // Return without inlining a thing
          return false;
      }
    }

    return false;
  }

  /**
   * Checks name referenced in node to determine if it might have
   * changed.
   * @return Whether the replacement can be made.
   */
  private boolean isSafeReplacement(Node node, Node replacement) {
    // No checks are needed for simple names.
    if (node.isName()) {
      return true;
    }
    Preconditions.checkArgument(node.isGetProp());

    while (node.isGetProp()) {
      node = node.getFirstChild();
    }
    return !(node.isName() && isNameAssignedTo(node.getString(), replacement));

  }

  /**
   * @return Whether name is assigned in the expression rooted at node.
   */

  private static boolean isNameAssignedTo(String name, Node node) {
    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      if (isNameAssignedTo(name, c)) {
        return true;
      }
    }

    if (node.isName()) {
      Node parent = node.getParent();
      if (parent.isAssign() && parent.getFirstChild() == node) {
        if (name.equals(node.getString())) {
          return true;
        }
      }
    }

    return false;
  }
}
