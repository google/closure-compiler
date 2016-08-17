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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;

/**
 * Report an error if the user forgot to call super() in the constructor of an ES6 class.
 * We don't require that the super() call be the first statement in the constructor, because doing
 * so would break J2CL-compiled code.
 */
final class CheckMissingSuper extends AbstractPostOrderCallback implements HotSwapCompilerPass {
  static final DiagnosticType MISSING_CALL_TO_SUPER =
      DiagnosticType.error("JSC_MISSING_CALL_TO_SUPER", "constructor is missing a call to super()");

  private final AbstractCompiler compiler;

  public CheckMissingSuper(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    if (!compiler.getOptions().getLanguageIn().isEs6OrHigher()) {
      return;
    }
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    process(null, scriptRoot);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isClass()) {
      Node superclass = n.getSecondChild();
      if (superclass.isEmpty()) {
        return;
      }

      Node constructor =
          NodeUtil.getFirstPropMatchingKey(NodeUtil.getClassMembers(n), "constructor");
      if (constructor == null) {
        return;
      }

      FindSuper finder = new FindSuper();
      NodeTraversal.traverseEs6(compiler, NodeUtil.getFunctionBody(constructor), finder);

      if (!finder.found) {
        t.report(constructor, MISSING_CALL_TO_SUPER);
      }
    }
  }

  private static final class FindSuper implements Callback {
    boolean found = false;

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return !found;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isSuper() && parent.isCall()) {
        found = true;
        return;
      }
    }
  }
}
