/*
 * Copyright 2008 The Closure Compiler Authors.
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

/**
 * Check for invalid breaks and continues in the program.
 *
 */
class ControlStructureCheck implements CompilerPass {

  private AbstractCompiler compiler;

  private String sourceName = null;

  static final DiagnosticType USE_OF_WITH = DiagnosticType.warning(
      "JSC_USE_OF_WITH",
      "The use of the 'with' structure should be avoided.");

  ControlStructureCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    check(root);
  }

  /**
   * Reports errors for any invalid use of control structures.
   *
   * @param node Current node to check.
   */
  private void check(Node node) {
    switch (node.getType()) {
      case Token.WITH:
        JSDocInfo info = node.getJSDocInfo();
        boolean allowWith =
            info != null && info.getSuppressions().contains("with");
        if (!allowWith) {
          report(node, USE_OF_WITH);
        }
        break;

      case Token.SCRIPT:
        // Remember the source file name in case we need to report an error.
        sourceName = (String) node.getProp(Node.SOURCENAME_PROP);
        break;
    }

    for (Node bChild = node.getFirstChild(); bChild != null;) {
      Node next = bChild.getNext();
      check(bChild);
      bChild = next;
    }
  }

  private void report(Node n, DiagnosticType error) {
    compiler.report(JSError.make(sourceName, n, error));
  }
}
