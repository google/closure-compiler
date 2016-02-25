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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Check for redundant casts to boolean via double negation or a Boolean call.
 * Eg:
 *   var foo = !!!bar;
 *   var foo = Boolean(!!bar);
 *   if (!!foo) {}
 *   if (Boolean(foo)) {}
 *
 * Inspired by ESLint
 *   (https://github.com/eslint/eslint/blob/master/lib/rules/no-extra-boolean-cast.js)
 */
public final class CheckRedundantBooleanCast extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  public static final DiagnosticType REDUNDANT_BOOLEAN_CAST =
      DiagnosticType.warning("JSC_REDUNDANT_BOOLEAN_CAST", "Redundant cast to boolean.");

  private final AbstractCompiler compiler;

  public CheckRedundantBooleanCast(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  private boolean isInBooleanContext(Node n, Node parent) {
    switch (parent.getType()) {
      case Token.IF:
      case Token.WHILE:
      case Token.HOOK:
        return parent.getFirstChild().equals(n);
      case Token.DO:
      case Token.FOR:
        return parent.getSecondChild().equals(n);
      case Token.NOT:
        return true;
    }
    return false;
  }

  private boolean isBooleanCall(Node n) {
    return n.getFirstChild().isName() && n.getFirstChild().getString().equals("Boolean");
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    boolean shouldWarn = false;
    if (n.isNot() && parent.isNot()) {
      Node grandParent = parent.getParent();
      if (isInBooleanContext(parent, grandParent)) {
        shouldWarn = true;
      } else if (grandParent.isCall() || grandParent.isNew()) {
        shouldWarn = isBooleanCall(grandParent);
      }
    } else if (n.isCall()) {
      shouldWarn = isBooleanCall(n) && isInBooleanContext(n, parent);
    }
    if (shouldWarn) {
      t.report(n, REDUNDANT_BOOLEAN_CAST);
    }
  }
}
