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
import com.google.javascript.rhino.Node;

/**
 * Rewrite .bind(this) calls on an anonymous functions to arrow functions
 * (which have implicit this binding).
 * Remove .bind(this) calls on an arrow function (the bind call here would
 * have been useless)
 *
 * Skips functions that reference the 'arguments' object.
 *
 */
class RewriteBindThis extends AbstractPostOrderCallback implements CompilerPass{

  AbstractCompiler compiler;

  RewriteBindThis(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    if (node.isFunction() && canRewriteBinding(node)) {
      rewriteBinding(node);
    }
  }

  private boolean hasBindThisCall(Node functionNode) {
    Node parentNode = functionNode.getParent();
    return parentNode.isGetProp()
        && functionNode.getNext().getString().equals("bind")
        && parentNode.getParent().isCall()
        && parentNode.getNext().isThis();
  }

  private boolean canRewriteBinding(Node functionNode) {
    return functionNode.getFirstChild().getString().isEmpty()
        && hasBindThisCall(functionNode)
        && !NodeUtil.isVarArgsFunction(functionNode);
  }

  private void rewriteBinding(Node functionNode) {
    Node parent = functionNode.getParent();
    Node grandparent = parent.getParent();

    parent.removeChild(functionNode);
    grandparent.getParent().replaceChild(grandparent, functionNode);
    functionNode.putBooleanProp(Node.ARROW_FN, true);

    compiler.reportCodeChange();
  }
}
