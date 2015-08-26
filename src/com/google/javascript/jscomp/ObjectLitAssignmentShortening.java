/*
 * Copyright 2015 The Closure Compiler Authors.
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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Shorten a object literal by using following features in ES6 upward:
 *   1. Shorthand assignment ({@code {a: a}} can be written as {@code {a}})
 *   2. Shorthand method ({@code {method: function(){}}} can be written
 *      as {@code {method(){}}})
 *
 */
class ObjectLitAssignmentShortening implements CompilerPass {

  private final AbstractCompiler compiler;

  ObjectLitAssignmentShortening(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, new ObjectLitShorteningCallback(compiler));
  }

  private class ObjectLitShorteningCallback extends AbstractPostOrderCallback {

    AbstractCompiler compiler;

    ObjectLitShorteningCallback(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (parent != null && parent.isObjectLit() && n.isStringKey()) {
        // Already object literal shorthand assignment
        if (!n.hasChildren()) {
          return;
        }

        Node valueNode = n.getFirstChild();
        if (valueNode.isName() && n.getString().equals(valueNode.getString())) {
          n.removeChild(valueNode);
          compiler.reportCodeChange();
        } else if (valueNode.isFunction() && !valueNode.isArrowFunction()) {
          // It is not worth rewriting the arrow function since even a return
          // statement is longer than the arrow function
          functionAssignmentShortening(n, valueNode, parent);
        }
      }
    }

    private void functionAssignmentShortening(
        Node n, Node functionNode, Node parent) {
      n.removeChild(functionNode);
      Node memDefNode = IR.memberFunctionDef(n.getString(), functionNode);
      parent.replaceChild(n, memDefNode);
      compiler.reportCodeChange();
    }
  }
}
