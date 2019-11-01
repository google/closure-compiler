/*
 * Copyright 2012 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.TernaryValue;

/**
 * Checks for common errors, such as misplaced semicolons:
 *
 * <pre>
 * if (x); act_now();
 * </pre>
 *
 * or comparison against NaN:
 *
 * <pre>
 * if (x === NaN) act();
 * </pre>
 *
 * and generates warnings.
 */
final class CheckSuspiciousCode extends AbstractPostOrderCallback {

  static final DiagnosticType SUSPICIOUS_SEMICOLON =
      DiagnosticType.warning(
          "JSC_SUSPICIOUS_SEMICOLON",
          "If this if/for/while really shouldn''t have a body, use '{}'");

  static final DiagnosticType SUSPICIOUS_COMPARISON_WITH_NAN =
      DiagnosticType.warning(
          "JSC_SUSPICIOUS_NAN", "Comparison against NaN is always false. Did you mean isNaN()?");

  static final DiagnosticType SUSPICIOUS_IN_OPERATOR =
      DiagnosticType.warning(
          "JSC_SUSPICIOUS_IN",
          "Use of the \"in\" keyword on non-object types throws an exception.");

  static final DiagnosticType SUSPICIOUS_INSTANCEOF_LEFT_OPERAND =
      DiagnosticType.warning(
          "JSC_SUSPICIOUS_INSTANCEOF_LEFT",
          "\"instanceof\" with left non-object operand is always false.");

  static final DiagnosticType SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR =
      DiagnosticType.warning(
          "JSC_SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR",
          "Left operand of {0} operator is always {1}.");

  static final DiagnosticType SUSPICIOUS_NEGATED_LEFT_OPERAND_OF_IN_OPERATOR =
      DiagnosticType.warning(
          "JSC_SUSPICIOUS_NEGATED_LEFT_OPERAND_OF_IN_OPERATOR",
          "Suspicious negated left operand of 'in' operator.");

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    checkMissingSemicolon(t, n);
    checkNaN(t, n);
    checkInvalidIn(t, n);
    checkNonObjectInstanceOf(t, n);
    checkNegatedLeftOperandOfInOperator(t, n);
    checkLeftOperandOfLogicalOperator(t, n);
  }

  private void checkMissingSemicolon(NodeTraversal t, Node n) {
    switch (n.getToken()) {
      case IF:
        Node trueCase = n.getSecondChild();
        reportIfWasEmpty(t, trueCase);
        Node elseCase = trueCase.getNext();
        if (elseCase != null) {
          reportIfWasEmpty(t, elseCase);
        }
        break;

      case WHILE:
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
        reportIfWasEmpty(t, NodeUtil.getLoopCodeBlock(n));
        break;
      default:
        break;
    }
  }

  private static void reportIfWasEmpty(NodeTraversal t, Node block) {
    checkState(block.isBlock());

    // A semicolon is distinguished from a block without children by
    // annotating it with EMPTY_BLOCK.  Blocks without children are
    // usually intentional, especially with loops.
    if (!block.hasChildren() && block.isAddedBlock()) {
      t.getCompiler().report(JSError.make(block, SUSPICIOUS_SEMICOLON));
    }
  }

  private void checkNaN(NodeTraversal t, Node n) {
    switch (n.getToken()) {
      case EQ:
      case GE:
      case GT:
      case LE:
      case LT:
      case NE:
      case SHEQ:
      case SHNE:
        reportIfNaN(t, n.getFirstChild());
        reportIfNaN(t, n.getLastChild());
        break;
      default:
        break;
    }
  }

  private static void reportIfNaN(NodeTraversal t, Node n) {
    if (NodeUtil.isNaN(n)) {
      t.getCompiler().report(JSError.make(n.getParent(), SUSPICIOUS_COMPARISON_WITH_NAN));
    }
  }

  private void checkInvalidIn(NodeTraversal t, Node n) {
    if (n.isIn()) {
      reportIfNonObject(t, n.getLastChild(), SUSPICIOUS_IN_OPERATOR);
    }
  }

  private void checkNonObjectInstanceOf(NodeTraversal t, Node n) {
    if (n.isInstanceOf()) {
      reportIfNonObject(t, n.getFirstChild(), SUSPICIOUS_INSTANCEOF_LEFT_OPERAND);
    }
  }

  private static boolean reportIfNonObject(NodeTraversal t, Node n, DiagnosticType diagnosticType) {
    if (n.isAdd() || !NodeUtil.mayBeObject(n)) {
      t.report(n.getParent(), diagnosticType);
      return true;
    }
    return false;
  }

  private void checkNegatedLeftOperandOfInOperator(NodeTraversal t, Node n) {
    if (n.isIn() && n.getFirstChild().isNot()) {
      t.report(n.getFirstChild(), SUSPICIOUS_NEGATED_LEFT_OPERAND_OF_IN_OPERATOR);
    }
  }

  /**
   * Check for the LHS of a logical operator (&amp;&amp; and ||) being deterministically truthy or
   * falsy, using both syntactic and type information. This is always suspicious (though for
   * different reasons: "truthy and" means the LHS is always ignored and should be removed, "falsy
   * and" means the RHS is dead code, and vice versa for "or". However, there are a number of
   * legitimate use cases where we need to back off from using type information (see {@link
   * #getBooleanValueWithTypes} for more details on these back offs).
   */
  private void checkLeftOperandOfLogicalOperator(NodeTraversal t, Node n) {
    if (n.isOr() || n.isAnd()) {
      String operator = n.isOr() ? "||" : "&&";
      TernaryValue v = getBooleanValueWithTypes(n.getFirstChild());
      if (v != TernaryValue.UNKNOWN) {
        String result = v == TernaryValue.TRUE ? "truthy" : "falsy";
        t.report(n, SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR, operator, result);
      }
    }
  }

  /**
   * Returns the possible boolean values of a node. This is a combination of {@link
   * NodeUtil#getBooleanValue} and {@link JSType#getPossibleToBooleanOutcomes}, with some additional
   * backoff for situations that are known to be less accurate (to wit, qualified names and truthy
   * return types, which return UNKNOWN even if the type information seems conclusive). Another
   * difference from the NodeUtil and JSTyoe methods is that we include some specific optimization
   * to avoid quadratic behavior of large nested logical operations.
   *
   * <p>The specifics of when this method returns UNKNOWN instead of TRUE or FALSE is as follows:
   *
   * <ul>
   *   <li>We should not determine that a qualified name (expanded slightly to include computed
   *       properties) is always truthy or always falsy. There are many cases where an extern
   *       defines a name to be truthy, but it still makes sense to feature-test in the browser.
   *   <li>We should not determine that a getelem or a function call is always truthy (though we
   *       expand it to simply never complain about always-truthy based only on types) since the
   *       standard externs lie about the return type of {@code Map.prototype.get} and array
   *       accesses, which can both return undefined despite what the externs say.
   * </ul>
   *
   * We do not back off from boolean literals (e.g. "{@code true &&}"), though they appear to be
   * common in generated code. Instead, such code should suppress "suspiciousCode". We also do not
   * back off from always-falsy function call results, since it provides a valuable check and lies
   * in this direction are much less common.
   */
  private TernaryValue getBooleanValueWithTypes(Node n) {
    switch (n.getToken()) {
      case ASSIGN:
      case COMMA:
        return getBooleanValueWithTypes(n.getLastChild());
      case NOT:
        return getBooleanValueWithTypes(n.getLastChild()).not();
      case AND:
        // Assume the left-hand side is unknown. If it's not then we'll report it elsewhere. This
        // prevents revisiting deeper nodes repeatedly, which would result in O(n^2) performance.
        return TernaryValue.UNKNOWN.and(getBooleanValueWithTypes(n.getLastChild()));
      case OR:
        // Assume the left-hand side is unknown. If it's not then we'll report it elsewhere. This
        // prevents revisiting deeper nodes repeatedly, which would result in O(n^2) performance.
        return TernaryValue.UNKNOWN.or(getBooleanValueWithTypes(n.getLastChild()));
      case HOOK:
        {
          TernaryValue trueValue = getBooleanValueWithTypes(n.getSecondChild());
          TernaryValue falseValue = getBooleanValueWithTypes(n.getLastChild());
          return trueValue.equals(falseValue) ? trueValue : TernaryValue.UNKNOWN;
        }
      case FUNCTION:
      case CLASS:
      case NEW:
      case ARRAYLIT:
      case OBJECTLIT:
        return TernaryValue.TRUE;
      case VOID:
        return TernaryValue.FALSE;
      case GETPROP:
      case GETELEM:
        // Assume that type information on getprops and getelems are likely to be wrong.  This
        // prevents spurious warnings from not including undefined in getelem's return value,
        // from existence checks of symbols the externs define as certainly true, or from default
        // initialization of globals ({@code x.y = x.y || {}}).
        return TernaryValue.UNKNOWN;
      default:
    }
    // If we reach this point then all the composite structures that we can decompose have
    // already been handled, leaving only qualified names and type-aware checks to handle below.
    // Note that much of the switch above in fact duplicates the logic in getImpureBooleanValue,
    // though with some subtle differences.  Important differences include (1) avoiding recursion
    // into the left-hand-side of nested logical operators, instead treating them as unknown since
    // they would have already been reported elsewhere in the traversal had they been otherwise
    // (this guarantees we visit each node once, rather than quadratically repeating work);
    // (2) it propagates our unique amalgam of syntax-based and type-based checks to work when more
    // deeply nested (i.e. recursively).  These differences rely on assumptions that are very
    // specific to this use case, so it does not make sense to upstream them.
    TernaryValue literalValue = NodeUtil.getBooleanValue(n);
    if (literalValue != TernaryValue.UNKNOWN || n.isName()) {
      // If the truthiness is determinstic from the syntax then return that immediately.
      // Alternatively, NAME nodes also get a pass since we don't trust the type information.
      return literalValue;
    }
    JSType type = n.getJSType();
    if (type != null) {
      // Distrust types we think are always truthy, since sometimes the types lie, even for results
      // of function calls (e.g. Map.prototype.get), so it's still important to check.  But
      // always-falsy values are a little more obviously wrong and there should be no reason for
      // those type annotations to be lies.  ANDing with UNKNOWN ensures we never return TRUE.
      return TernaryValue.UNKNOWN.and(type.getPossibleToBooleanOutcomes().toTernaryValue());
    }
    return TernaryValue.UNKNOWN;
  }
}
