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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Checks for common errors, such as misplaced semicolons:
 * <pre>
 * if (x); act_now();
 * </pre>
 *  or comparison against NaN:
 * <pre>
 * if (x === NaN) act();
 * </pre>
 * and generates warnings.
 *
 * @author johnlenz@google.com (John Lenz)
 */
final class CheckSuspiciousCode extends AbstractPostOrderCallback {

  static final DiagnosticType SUSPICIOUS_SEMICOLON = DiagnosticType.warning(
      "JSC_SUSPICIOUS_SEMICOLON",
      "If this if/for/while really shouldn''t have a body, use '{}'");

  static final DiagnosticType SUSPICIOUS_COMPARISON_WITH_NAN =
      DiagnosticType.warning(
          "JSC_SUSPICIOUS_NAN",
          "Comparison against NaN is always false. Did you mean isNaN()?");

  static final DiagnosticType SUSPICIOUS_IN_OPERATOR =
      DiagnosticType.warning(
          "JSC_SUSPICIOUS_IN",
          "Use of the \"in\" keyword on non-object types throws an exception.");

  static final DiagnosticType SUSPICIOUS_INSTANCEOF_LEFT_OPERAND =
      DiagnosticType.warning(
          "JSC_SUSPICIOUS_INSTANCEOF_LEFT",
          "\"instanceof\" with left non-object operand is always false.");

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
  }

  private void checkMissingSemicolon(NodeTraversal t, Node n) {
    switch (n.getType()) {
      case Token.IF:
        Node trueCase = n.getSecondChild();
        reportIfWasEmpty(t, trueCase);
        Node elseCase = trueCase.getNext();
        if (elseCase != null) {
          reportIfWasEmpty(t, elseCase);
        }
        break;

      case Token.WHILE:
      case Token.FOR:
      case Token.FOR_OF:
        reportIfWasEmpty(t, NodeUtil.getLoopCodeBlock(n));
        break;
    }
  }

  private static void reportIfWasEmpty(NodeTraversal t, Node block) {
    Preconditions.checkState(block.isBlock());

    // A semicolon is distinguished from a block without children by
    // annotating it with EMPTY_BLOCK.  Blocks without children are
    // usually intentional, especially with loops.
    if (!block.hasChildren() && block.isAddedBlock()) {
        t.getCompiler().report(
            t.makeError(block, SUSPICIOUS_SEMICOLON));
    }
  }

  private void checkNaN(NodeTraversal t, Node n) {
    switch (n.getType()) {
      case Token.EQ:
      case Token.GE:
      case Token.GT:
      case Token.LE:
      case Token.LT:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
        reportIfNaN(t, n.getFirstChild());
        reportIfNaN(t, n.getLastChild());
    }
  }

  private static void reportIfNaN(NodeTraversal t, Node n) {
    if (NodeUtil.isNaN(n)) {
      t.getCompiler().report(
          t.makeError(n.getParent(), SUSPICIOUS_COMPARISON_WITH_NAN));
    }
  }

  private void checkInvalidIn(NodeTraversal t, Node n) {
    if (n.getType() == Token.IN) {
      reportIfNonObject(t, n.getLastChild(), SUSPICIOUS_IN_OPERATOR);
    }
  }

  private void checkNonObjectInstanceOf(NodeTraversal t, Node n) {
    if (n.getType() == Token.INSTANCEOF) {
      reportIfNonObject(
          t, n.getFirstChild(), SUSPICIOUS_INSTANCEOF_LEFT_OPERAND);
    }
  }

  private static boolean reportIfNonObject(
      NodeTraversal t, Node n, DiagnosticType diagnosticType) {
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
}
