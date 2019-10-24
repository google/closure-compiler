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

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;

/**
 * A simple pass to enforce some invariants about source information.
 *
 * <ul>
 *   <li>All nodes in externs and code have a line number and non-zero length
 *   <li>All JSTypeExpressions have a source name
 *   <li>All nodes in a JSTYpeExpression have a source file attached (does not verify lineno or
 *       charno)
 * </ul>
 *
 * This pass does not check the content of the line numbers for things like "being monotonically
 * increasing" because we often insert generated code with out-of-order line numbers. (e.g. imagine
 * inlining a function from a.js into b.js, but having the source information point to a.js)
 */
class SourceInfoCheck implements Callback, CompilerPass {

  private static final DiagnosticType MISSING_LINE_INFO =
      DiagnosticType.error(
          "JSC_MISSING_LINE_INFO",
          "No source location information associated with {0}."
              + "\nMost likely a Node has been created without setting the source file"
              + " and line/column location.  Usually this is done using"
              + " Node.useSourceInfoIfMissingFrom and supplying a Node from the source AST.");

  private static final DiagnosticType MISSING_LENGTH =
      DiagnosticType.error(
          "JSC_MISSING_LENGTH",
          "Negative length associated with {0}.\n"
              + "Most likely a Node's source information was set incorrectly at parse time.");

  private static final DiagnosticType MISSING_SOURCE_NAME =
      DiagnosticType.error(
          "JSC_MISSING_SOURCE_NAME",
          "No source name associated with {0}.\n"
              + "Most likely a new type was created without setting the source name.");

  private final AbstractCompiler compiler;
  private boolean requiresLineNumbers = false;

  SourceInfoCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public void setCheckSubTree(Node root) {
    requiresLineNumbers = true;

    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void process(Node externs, Node root) {
    requiresLineNumbers = false;

    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal unused, Node n, Node parent) {
    // Each JavaScript file is rooted in a script node, so we'll only
    // have line number information inside the script node.
    if (n.isScript()) {
      requiresLineNumbers = true;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal unused, Node n, Node parent) {
    if (n.isScript()) {
      requiresLineNumbers = false;
    } else if (requiresLineNumbers) {
      if (n.getLineno() == -1) {
        // The tree version of the node is really the best diagnostic
        // info we have to offer here.
        compiler.report(JSError.make(n, MISSING_LINE_INFO, n.toStringTree()));
      } else if (n.getLength() < 0) {
        compiler.report(JSError.make(n, MISSING_LENGTH, n.toStringTree()));
      }
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        checkJSDoc(info);
      }
    }
  }

  /** Verifies that all type nodes have source files (if not an actual length) */
  private void checkJSDoc(JSDocInfo info) {
    for (JSTypeExpression expression : info.getTypeExpressions()) {
      if (expression.getSourceName() == null) {
        compiler.report(
            JSError.make(
                expression.getRoot(), MISSING_SOURCE_NAME, "JSTypeExpression " + expression));
      } else {
        NodeUtil.visitPreOrder(expression.getRoot(), this::checkSourceFile);
      }
    }
  }

  private void checkSourceFile(Node n) {
    StaticSourceFile sourceName = n.getStaticSourceFile();
    if (sourceName == null) {
      compiler.report(JSError.make(n, MISSING_SOURCE_NAME, "type node " + n.toStringTree()));
    }
  }
}
