/*
 * Copyright 2008 Google Inc.
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

import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Check for invalid breaks and continues in the program.
 *
*
 */
class ControlStructureCheck implements CompilerPass {

  private AbstractCompiler compiler;

  private String sourceName = null;

  // List of labels for switch statements.
  private Deque<String> switchLabels = new ArrayDeque<String>();

  static final DiagnosticType INVALID_BREAK = DiagnosticType.error(
      "JSC_INVALID_BREAK",
      "unlabeled break must be inside loop or switch");

  static final DiagnosticType INVALID_CONTINUE = DiagnosticType.error(
      "JSC_INVALID_CONTINUE",
      "continue must be inside loop");

  static final DiagnosticType INVALID_LABEL_CONTINUE = DiagnosticType.error(
      "JSC_INVALID_LABEL_CONTINUE",
      "continue can only target labels of loop structures");

  static final DiagnosticType USE_OF_WITH = DiagnosticType.warning(
      "JSC_USE_OF_WITH",
      "The use of the 'with' structure should be avoided.");

  ControlStructureCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    check(root, false, false);
  }

  /**
   * Reports errors for any invalid breaks and continues in an AST. This method
   * uses recursion to perform a pre-order traversal. It keeps track the
   * iteration-statement nest depth and switch-statement nest depth. If there is
   * a break or continue in the AST but there is no corresponding nesting of
   * iteration-statement or switch-statement, the function will report an error.
   * Also, it keeps track of the labels for switch-statements. If a labeled
   * continue-statement points to a switch-statement, it will also report an
   * error.
   * <p>
   * There is no need to verify that a label has actually been defined because
   * the parser has already done so.
   * <p>
   * TODO(user): Use a light version of NodeTraversal for this and other
   * similar passes.
   *
   * @param node Current node to check.
   * @param inLoop Is there a loop above this node.
   * @param inSwitch Is there a switch above this node.
   */
  private void check(Node node, boolean inLoop, boolean inSwitch) {
    switch (node.getType()) {
      case Token.WITH:
        JSDocInfo info = node.getJSDocInfo();
        boolean allowWith =
            info != null && info.getSuppressions().contains("with");
        if (!allowWith) {
          report(node, USE_OF_WITH);
        }
        break;

      case Token.FUNCTION:
        // Save the old labels because we are in a new scope.
        Deque<String> oldSwitchLabels = switchLabels;
        switchLabels = new ArrayDeque<String>();

        // Reset to zero since the spec does not allow break/continue across
        // functions.
        Node body = node.getFirstChild().getNext().getNext();
        check(body, false, false);

        // Restore the old labels.
        switchLabels = oldSwitchLabels;
        break;

      case Token.FOR:
        Node child = node.getFirstChild();
        check(child, inLoop, inSwitch);
        child = child.getNext();
        check(child, inLoop, inSwitch);
        child = child.getNext();
        // We have a FOR-IN if we have 3 blocks only.
        if (child.getNext() == null) {
          // This is the case when we have FOR.
          check(child, true, inSwitch);
        } else {
          check(child, inLoop, inSwitch);
          check(child.getNext(), true, inSwitch);
        }
        break;

      case Token.WHILE:
        check(node.getFirstChild(), inLoop, inSwitch);
        check(node.getFirstChild().getNext(), true, inSwitch);
        break;

      case Token.DO:
        check(node.getFirstChild(), true, inSwitch);
        break;

      case Token.SWITCH:
        check(node.getFirstChild(), inLoop, inSwitch);
        for (Node cChild = node.getFirstChild().getNext(); cChild != null;) {
          Node next = cChild.getNext();
          check(cChild, inLoop, true);
          cChild = next;
        }
        break;

      case Token.SCRIPT:
        // Remember the source file name in case we need to report an error.
        sourceName = (String) node.getProp(Node.SOURCENAME_PROP);
        for (Node sChild = node.getFirstChild(); sChild != null;) {
          Node next = sChild.getNext();
          check(sChild, false, false);
          sChild = next;
        }
        break;

      case Token.LABEL:
        Node switchNode = node.getLastChild();

        // Record the switch label in the list.
        if (switchNode.getType() == Token.SWITCH) {
          String label = node.getFirstChild().getString();
          switchLabels.addFirst(label);
          check(node.getFirstChild().getNext(), inLoop, inSwitch);
          switchLabels.removeFirst();
        } else {
          check(node.getFirstChild().getNext(), inLoop, inSwitch);
        }
        break;

      case Token.BREAK:
        // Make sure we are in at least one loop nest or switch nest.
        if (!node.hasChildren() && !inLoop && !inSwitch) {
          report(node, INVALID_BREAK);
        }
        break;

      case Token.CONTINUE:
        // If there is no label, we just need to make sure we are in at least
        // one loop nest.
        if (!inLoop) {
          report(node, INVALID_CONTINUE);
        }
        if (node.hasChildren()) {
          // Now we have to verify that the label is not a label for "switch".
          Node label = node.getFirstChild();
          if (switchLabels.contains(label.getString())) {
            report(node, INVALID_LABEL_CONTINUE);
          }
        }
        break;

      default:
        for (Node bChild = node.getFirstChild(); bChild != null;) {
          Node next = bChild.getNext();
          check(bChild, inLoop, inSwitch);
          bChild = next;
        }
    }
  }

  private void report(Node n, DiagnosticType error) {
    compiler.report(JSError.make(sourceName, n, error));
  }
}
