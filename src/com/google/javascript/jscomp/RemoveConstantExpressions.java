/*
 * Copyright 2009 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.ParallelCompilerPass.Result;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;

/**
 * Pass that simplifies expression statements by replacing the
 * expression with the portions that have side effects.
 *
 * <p>This pass will rewrite:
 * <pre>
 *   1 + foo() + bar()
 * </pre>
 * as:
 * <pre>
 *   foo();bar()
 * </pre>
 *
*
 */
final class RemoveConstantExpressions implements CompilerPass {
  private final AbstractCompiler compiler;

  RemoveConstantExpressions(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    RemoveConstantRValuesCallback cb = new RemoveConstantRValuesCallback();
    NodeTraversal.traverse(null, root, cb);
    cb.getResult().notifyCompiler(compiler);
  }

  /**
   * Used to simplify expressions by removing subexpressions that have
   * no side effects which are not inputs to expressions with side
   * effects.
   */
  static class RemoveConstantRValuesCallback extends AbstractPostOrderCallback {
    private final AstChangeProxy changeProxy;
    private final Result result = new Result();

    /**
     * Instantiates AstChangeProxy and registers ChangeListener to
     * report ast changes to the compiler.
     */
    RemoveConstantRValuesCallback() {
      this.changeProxy = new AstChangeProxy();
      this.changeProxy.registerListener(
          new ReportCodeHasChangedListener(result));
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      trySimplify(parent, node);
    }

    /**
     * Attempts to replace the input node with a simpler but functionally
     * equivalent set of nodes.
     */
    private void trySimplify(Node parent, Node node) {
      if (node.getType() != Token.EXPR_RESULT) {
        return;
      }

      Node exprBody = node.getFirstChild();
      if (!NodeUtil.nodeTypeMayHaveSideEffects(exprBody)
          || exprBody.getType() == Token.NEW
          || exprBody.getType() == Token.CALL) {
        changeProxy.replaceWith(parent, node, getSideEffectNodes(exprBody));
      }
    }

    /**
     * Extracts a list of replacement nodes to use.
     */
    private List<Node> getSideEffectNodes(Node node) {
      List<Node> subexpressions = Lists.newArrayList();
      NodeTraversal.traverse(
          null, node,
          new GatherSideEffectSubexpressionsCallback(
              null,
              new GatherSideEffectSubexpressionsCallback.
              CopySideEffectSubexpressions(null, subexpressions)));

      List<Node> replacements = Lists.newArrayList();
      for (Node subexpression : subexpressions) {
        replacements.add(new Node(Token.EXPR_RESULT, subexpression));
      }
      return replacements;
    }

    public Result getResult() {
      return result;
    }
  }

  /**
   * Used to reports code changes to the compiler as they happen.
   */
  private static class ReportCodeHasChangedListener
      implements AstChangeProxy.ChangeListener {
    private final Result result;

    private ReportCodeHasChangedListener(Result result) {
      this.result = result;
    }
    @Override
    public void nodeRemoved(Node node) {
      result.changed = true;
    }
  }
}
