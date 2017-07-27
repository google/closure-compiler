/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Converts ES7 code to valid ES6 code.
 *
 * Currently this class converts ** and **= operators to calling Math.pow
 */
public final class Es7ToEs6Converter implements NodeTraversal.Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  public Es7ToEs6Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, this);
    TranspilationPasses.processTranspile(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case EXPONENT:
        visitExponentiationExpression(n, parent);
        break;
      case ASSIGN_EXPONENT:
        visitExponentiationAssignmentExpression(n, parent);
        break;
      default:
        break;
    }
  }

  private void visitExponentiationExpression(Node n, Node parent) {
    Node left = n.removeFirstChild();
    Node right = n.removeFirstChild();
    Node mathDotPowCall =
        IR.call(NodeUtil.newQName(compiler, "Math.pow"), left, right)
            .useSourceInfoIfMissingFromForTree(n);
    parent.replaceChild(n, mathDotPowCall);
    compiler.reportChangeToEnclosingScope(mathDotPowCall);
  }

  private void visitExponentiationAssignmentExpression(Node n, Node parent) {
    Node left = n.removeFirstChild();
    Node right = n.removeFirstChild();
    Node mathDotPowCall = IR.call(NodeUtil.newQName(compiler, "Math.pow"), left.cloneTree(), right);
    Node assign = IR.assign(left, mathDotPowCall).useSourceInfoIfMissingFromForTree(n);
    parent.replaceChild(n, assign);
    compiler.reportChangeToEnclosingScope(assign);
  }
}
