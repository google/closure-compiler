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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Check for unused labels blocks. This can help catching errors like:
 *   () => {a: 2}  // Returns undefined, not an Object
 *
 * Inspired by ESLint (https://github.com/eslint/eslint/blob/master/lib/rules/no-unused-labels.js)
 */
public final class CheckUnusedLabels implements Callback, HotSwapCompilerPass {
  public static final DiagnosticType UNUSED_LABEL = DiagnosticType.disabled(
      "JSC_UNUSED_LABEL", "Unused label {0}.");

  private class LabelContext {
    private final String name;
    private final LabelContext parent;
    private boolean used;

    private LabelContext(String name, LabelContext parent) {
      this.name = name;
      this.parent = parent;
    }
  }

  private final AbstractCompiler compiler;
  private LabelContext currentContext;

  public CheckUnusedLabels(AbstractCompiler compiler) {
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

  @Override
  public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.BREAK:
      case Token.CONTINUE:
        if (n.hasChildren()) {
          LabelContext temp = currentContext;
          while (temp != null) {
            if (temp.name.equals(n.getFirstChild().getString())) {
              temp.used = true;
              break;
            }
            temp = temp.parent;
          }
        }
        return false;
      case Token.LABEL:
        currentContext = new LabelContext(n.getFirstChild().getString(), currentContext);
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isLabel() && currentContext != null) {
      if (!currentContext.used) {
        t.report(n, UNUSED_LABEL, n.getFirstChild().getString());
      }
      currentContext = currentContext.parent;
    }
  }
}
