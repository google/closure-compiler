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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Collapses anonymous function expressions into named function declarations,
 * i.e. the following:
 *
 * <pre>
 * var f = function()
 * <pre>
 *
 * becomes:
 *
 * <pre>function f()</pre>
 *
 * This reduces the generated code size but changes the semantics because f
 * will be defined before its definition is reached.
 *
 */
class CollapseAnonymousFunctions implements CompilerPass {
  private final AbstractCompiler compiler;

  public CollapseAnonymousFunctions(AbstractCompiler compiler) {
    Preconditions.checkArgument(compiler.getLifeCycleStage().isNormalized());
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, new Callback());
  }

  private class Callback extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isVar()) {
        return;
      }

      // It is only safe to collapse anonymous functions that appear
      // at top-level blocks.  In other cases the difference between
      // variable and function declarations can lead to problems or
      // expose subtle bugs in browser implementation as function
      // definitions are added to scopes before the start of execution.

      Node grandparent = parent.getParent();
      if (!(parent.isScript() ||
            grandparent != null &&
            grandparent.isFunction() &&
            parent.isBlock())) {
        return;
      }

      // Need to store the next name in case the current name is removed from
      // the linked list.
      Preconditions.checkState(n.hasOneChild(), n);
      Node name = n.getFirstChild();
      Node value = name.getFirstChild();
      if (value != null &&
          value.isFunction() &&
          !isRecursiveFunction(value)) {
        Node fnName = value.getFirstChild();
        fnName.setString(name.getString());
        NodeUtil.copyNameAnnotations(name, fnName);
        name.removeChild(value);
        parent.replaceChild(n, value);

        // Renormalize the code.
        if (!t.inGlobalScope() &&
            NodeUtil.isHoistedFunctionDeclaration(value)) {
          parent.addChildToFront(value.detachFromParent());
        }

        compiler.reportCodeChange();
      }
    }

    private boolean isRecursiveFunction(Node function) {
      Node name = function.getFirstChild();
      if (name.getString().isEmpty()) {
        return false;
      }
      Node args = name.getNext();
      Node body = args.getNext();
      return containsName(body, name.getString());
    }

    private boolean containsName(Node n, String name) {
      if (n.isName() && n.getString().equals(name)) {
        return true;
      }

      for (Node child : n.children()) {
        if (containsName(child, name)) {
          return true;
        }
      }
      return false;
    }
  }
}
