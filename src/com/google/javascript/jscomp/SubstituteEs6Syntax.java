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
 * An optimization that minimizes code by simplifying expressions that
 * can be represented more succinctly with ES6 syntax, like arrow functions,
 * classes, etc.
 *
 */
class SubstituteEs6Syntax extends AbstractPostOrderCallback implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;

  public SubstituteEs6Syntax(AbstractCompiler compiler) {
    Preconditions.checkState(compiler.getOptions().getLanguageOut().isEs6OrHigher());
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    hotSwapScript(root, null);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
    compiler.setLanguageMode(compiler.getOptions().getLanguageOut());
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch(n.getType()) {
      case Token.FUNCTION:
        if (n.getFirstChild().getString().isEmpty() && !n.isArrowFunction()) {
          if (parent.isStringKey()) {
            parent.setType(Token.MEMBER_FUNCTION_DEF);
            compiler.reportCodeChange();
          } else if (!NodeUtil.isVarArgsFunction(n) // i.e. doesn't reference arguments
              && !NodeUtil.referencesThis(n.getLastChild())) {
            // When possible, change regular function to arrow functions
            n.setIsArrowFunction(true);
            compiler.reportCodeChange();
          }
        }
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
