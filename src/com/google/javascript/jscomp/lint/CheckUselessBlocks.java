/*
 * Copyright 2016 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Check for useless blocks. A block is considered useful if it is part of a
 * control structure like if / else / while / switch / etc. or if it contains
 * any block-scoped variables (let, const, class, or function declarations).
 * Otherwise there is no reason to use it so it is likely a mistake. This would
 * catch the classic error:
 *
 * return
 *     {foo: 'bar'};
 *
 * or more contrived cases like:
 *
 * if (denied) {
 *   showAccessDenied();
 * } {
 *   grantAccess();
 * }
 *
 * Inspired by ESLint (https://github.com/eslint/eslint/blob/master/lib/rules/no-lone-blocks.js)
 */
public final class CheckUselessBlocks implements Callback, HotSwapCompilerPass {
  public static final DiagnosticType USELESS_BLOCK = DiagnosticType.disabled(
      "JSC_USELESS_BLOCK", "Useless block.");

  private final AbstractCompiler compiler;
  private Deque<Node> loneBlocks;

  public CheckUselessBlocks(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.loneBlocks = new ArrayDeque<>();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  /**
   * A lone block is a non-synthetic, not-added BLOCK that is a direct child of
   * another non-synthetic, not-added BLOCK or a SCRIPT node.
   */
  private boolean isLoneBlock(Node n) {
    Node parent = n.getParent();
    if (parent != null && (parent.isScript()
        || (parent.isBlock() && !parent.isSyntheticBlock() && !parent.isAddedBlock()))) {
      return n.isBlock() && !n.isSyntheticBlock() && !n.isAddedBlock();
    }
    return false;
  }

  /**
   * Remove the enclosing block of a block-scoped declaration from the loneBlocks stack.
  */
  private void allowLoneBlock(Node parent) {
    if (loneBlocks.isEmpty()) {
      return;
    }

    if (loneBlocks.peek() == parent) {
      loneBlocks.pop();
    }
  }

  @Override
  public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.BLOCK:
        if (isLoneBlock(n)) {
          loneBlocks.push(n);
        }
        break;
      case Token.LET:
      case Token.CONST:
        allowLoneBlock(parent);
        break;
      case Token.CLASS:
        if (NodeUtil.isClassDeclaration(n)) {
          allowLoneBlock(parent);
        }
        break;
      case Token.FUNCTION:
        if (NodeUtil.isFunctionDeclaration(n)) {
          allowLoneBlock(parent);
        }
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isBlock() && !loneBlocks.isEmpty() && loneBlocks.peek() == n) {
      loneBlocks.pop();
      t.report(n, USELESS_BLOCK);
    }
  }
}
