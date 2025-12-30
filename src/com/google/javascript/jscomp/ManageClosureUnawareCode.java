/*
 * Copyright 2024 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.Node;
import org.jspecify.annotations.Nullable;

/**
 * This pass "unwraps" closure-unaware code into a regular AST.
 *
 * <p>ManageClosureUnawareCode.unwrap() should be run after all other passes have run, to unwrap the
 * code and re-expose it to the code-printing stage of the compiler.
 */
final class ManageClosureUnawareCode implements CompilerPass {

  private final AbstractCompiler compiler;

  private ManageClosureUnawareCode(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  static ManageClosureUnawareCode unwrap(AbstractCompiler compiler) {
    return new ManageClosureUnawareCode(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new UnwrapConcealedClosureUnawareCode());
  }

  // TODO jameswr: Given that the CodePrinter supports printing out the shadow instead of the shadow
  // host node, do we even need to revert the AST back to the original form at the end of
  // compilation?
  private static final class UnwrapConcealedClosureUnawareCode implements NodeTraversal.Callback {

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, @Nullable Node parent) {
      if (parent == null || !parent.isScript()) {
        return true; // keep going
      }

      if (parent.isScript() && !parent.isClosureUnawareCode()) {
        return false;
      }

      // Once inside a closureUnaware script, we want to traverse the entire thing to make sure we
      // find all the nodes marked as closure-unaware shadows.
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, @Nullable Node parent) {
      tryUnwrapClosureUnawareShadowedCode(t, n);
    }

    private void tryUnwrapClosureUnawareShadowedCode(NodeTraversal t, Node n) {
      Node shadowAstRoot = n.getClosureUnawareShadow();
      if (shadowAstRoot == null) {
        return;
      }

      // ROOT -> SCRIPT -> EXPR_RESULT -> CALL -> FUNCTION
      Node shadowScript = shadowAstRoot.getOnlyChild();
      checkState(shadowScript.isScript(), shadowScript);
      checkState(shadowScript.hasOneChild(), shadowScript);
      Node exprResult = shadowScript.getOnlyChild();
      checkState(exprResult.isExprResult(), exprResult);
      Node callNode = exprResult.getOnlyChild();
      checkState(callNode.isCall(), callNode);
      Node originalCodeFunction = callNode.getLastChild();
      checkState(originalCodeFunction.isFunction(), originalCodeFunction);
      originalCodeFunction.detach();
      n.replaceWith(originalCodeFunction);
    }
  }
}
