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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashSet;
import java.util.Set;

/**
 * Removes try catch finally blocks from a parse tree for easier debugging
 * (these statements impact both debugging in IE and sometimes even in FF).
 *
 */
class RemoveTryCatch implements CompilerPass {
  private final AbstractCompiler compiler;
  private final Set<Node> tryNodesContainingReturnStatements;

  RemoveTryCatch(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.tryNodesContainingReturnStatements = new HashSet<Node>();
  }

  /**
   * Do all processing on the root node.
   */
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new RemoveTryCatchCode());
  }

  private class RemoveTryCatchCode extends AbstractPostOrderCallback {
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.TRY:
          // Ignore the try statement if it has the @preserveTry annotation
          // (for expected exceptions).
          JSDocInfo info = n.getJSDocInfo();
          if (info != null && info.shouldPreserveTry()) {
            return;
          }

          Node tryBlock = n.getFirstChild();
          Node catchBlock = tryBlock.getNext();  // may be null or empty
          Node finallyBlock = catchBlock != null ? catchBlock.getNext() : null;

          // Ignore the try statement if it has a finally part and the try
          // block contains an early return.
          if (finallyBlock != null &&
              tryNodesContainingReturnStatements.contains(n)) {
            return;
          }

          // Redeclare vars declared in the catch node to be removed.
          if (catchBlock.hasOneChild()) {
            NodeUtil.redeclareVarsInsideBranch(catchBlock);
          }

          // Disconnect the try/catch/finally nodes from the parent
          // and each other.
          n.detachChildren();

          // try node
          Node block;
          if (!NodeUtil.isStatementBlock(parent)) {
            block = new Node(Token.BLOCK);
            parent.replaceChild(n, block);
            block.addChildToFront(tryBlock);
          } else {
            parent.replaceChild(n, tryBlock);
            block = parent;
          }

          // finally node
          if (finallyBlock != null) {
            block.addChildAfter(finallyBlock, tryBlock);
          }
          compiler.reportCodeChange();
          break;

        case Token.RETURN:
          boolean isInTryBlock = false;
          for (Node anc = parent;
               anc != null && anc.getType() != Token.FUNCTION;
               anc = anc.getParent()) {
            if (anc.getType() == Token.TRY) {
              tryNodesContainingReturnStatements.add(anc);
              break;
            }
          }
          break;
      }
    }
  }
}
