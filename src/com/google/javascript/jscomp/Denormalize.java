/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * The goal with this pass is to reverse the simplifications done in the
 * normalization pass that are not handled by other passes (such as
 * CollapseVariableDeclarations) to avoid making the resulting code larger.
 *
 * Currently this pass only does one thing pushing statements into for-loop
 * initializer. This:
 *   var a = 0; for(;a<0;a++) {}
 * becomes:
 *   for(var a = 0;a<0;a++) {}
 *
 * @author johnlenz@google.com (johnlenz)
 */
class Denormalize implements CompilerPass, Callback {

  private final AbstractCompiler compiler;

  Denormalize(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    maybeCollapseIntoForStatements(n, parent);
  }

  /**
   * Collapse VARs and EXPR_RESULT node into FOR loop initializers where
   * possible.
   */
  private void maybeCollapseIntoForStatements(Node n, Node parent) {
    // Only SCRIPT, BLOCK, and LABELs can have FORs that can be collapsed into.
    // LABELs are not supported here.
    if (parent == null || !NodeUtil.isStatementBlock(parent)) {
      return;
    }

    // Is the current node something that can be in a for loop initializer?
    if (!n.isExprResult() && !n.isVar()) {
      return;
    }

    // Is the next statement a valid FOR?
    Node nextSibling = n.getNext();
    if (nextSibling == null) {
      return;
    } else if (NodeUtil.isForIn(nextSibling)) {
      Node forNode = nextSibling;
      Node forVar = forNode.getFirstChild();
      if (forVar.isName()
          && n.isVar() && n.hasOneChild()) {
        Node name = n.getFirstChild();
        if (!name.hasChildren()
            && forVar.getString().equals(name.getString())) {
          // OK, the names match, and the var declaration does not have an
          // initializer. Move it into the loop.
          parent.removeChild(n);
          forNode.replaceChild(forVar, n);
          compiler.reportCodeChange();
        }
      }
    } else if (nextSibling.isFor()
        && nextSibling.getFirstChild().isEmpty()) {

      // Does the current node contain an in operator?  If so, embedding
      // the expression in a for loop can cause some JavaScript parsers (such
      // as the PlayStation 3's browser based on Access's NetFront
      // browser) to fail to parse the code.
      // See bug 1778863 for details.
      if (NodeUtil.containsType(n, Token.IN)) {
        return;
      }

      // Move the current node into the FOR loop initializer.
      Node forNode = nextSibling;
      Node oldInitializer = forNode.getFirstChild();
      parent.removeChild(n);

      Node newInitializer;
      if (n.isVar()) {
        newInitializer = n;
      } else {
        // Extract the expression from EXPR_RESULT node.
        Preconditions.checkState(n.hasOneChild());
        newInitializer = n.getFirstChild();
        n.removeChild(newInitializer);
      }

      forNode.replaceChild(oldInitializer, newInitializer);

      compiler.reportCodeChange();
    }
  }

  static class StripConstantAnnotations
      extends AbstractPostOrderCallback
      implements CompilerPass {
    private AbstractCompiler compiler;

    StripConstantAnnotations(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node js) {
      NodeTraversal.traverse(compiler, externs, this);
      NodeTraversal.traverse(compiler, js, this);
    }

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      if (node.isName() || node.isString() || node.isStringKey()) {
        node.removeProp(Node.IS_CONSTANT_NAME);
      }
    }
  }
}
