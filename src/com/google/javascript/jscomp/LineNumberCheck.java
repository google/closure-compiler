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
import com.google.javascript.rhino.Node;

/**
 * A simple pass to ensure that all AST nodes have line numbers,
 * an that the line numbers are monotonically increasing.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class LineNumberCheck implements Callback, CompilerPass {

  static final DiagnosticType MISSING_LINE_INFO = DiagnosticType.error(
      "JSC_MISSING_LINE_INFO",
      "No source location information associated with {0}.\n" +
      "Most likely a Node has been created with settings the source file " +
      "and line/column location.  Usually this is done using " +
      "Node.copyInformationFrom and supplying a Node from the source AST.");

  private final AbstractCompiler compiler;
  private boolean requiresLineNumbers = false;

  LineNumberCheck(AbstractCompiler compiler) {
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
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    // Each JavaScript file is rooted in a script node, so we'll only
    // have line number information inside the script node.
    if (n.isScript()) {
      requiresLineNumbers = true;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      requiresLineNumbers = false;
    } else if (requiresLineNumbers) {
      if (n.getLineno() == -1) {
        // The tree version of the node is really the best diagnostic
        // info we have to offer here.
        compiler.report(
            t.makeError(n, MISSING_LINE_INFO,
                n.toStringTree()));
      }
    }
  }
}
