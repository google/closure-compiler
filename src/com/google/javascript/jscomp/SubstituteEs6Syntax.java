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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * An optimization that does peephole optimizations of ES6 code.
 *
 */
class SubstituteEs6Syntax extends AbstractPostOrderCallback implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;

  public SubstituteEs6Syntax(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    hotSwapScript(root, null);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch(n.getType()) {
      case Token.FUNCTION:
        if (n.isArrowFunction()) {
          maybeSimplifyArrowFunctionBody(n, n.getLastChild());
        }
        break;
    }
  }

  /**
   * If possible, replace functions of the form ()=>{ return x; } with ()=>x
   */
  private void maybeSimplifyArrowFunctionBody(Node arrowFunction, Node body) {
    Preconditions.checkArgument(arrowFunction.isArrowFunction());
    if (!body.isBlock() || body.getChildCount() != 1 || !body.getFirstChild().isReturn()) {
      return;
    }
    Node returnValue = body.getFirstChild().removeFirstChild();
    arrowFunction.replaceChild(body, returnValue != null ? returnValue : IR.name("undefined"));
    compiler.reportCodeChange();
  }
}
