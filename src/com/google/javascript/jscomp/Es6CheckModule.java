/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * Checks that ES6 Modules are used correctly, and do not reference undefined keywords or features.
 */
public final class Es6CheckModule extends AbstractPostOrderCallback implements HotSwapCompilerPass {
  static final DiagnosticType ES6_MODULE_REFERENCES_THIS =
      DiagnosticType.warning(
          "ES6_MODULE_REFERENCES_THIS", "The body of an ES6 module cannot reference 'this'.");

  private final AbstractCompiler compiler;

  public Es6CheckModule(AbstractCompiler compiler) {
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
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case THIS:
        if (t.inModuleHoistScope()) {
          t.report(n, ES6_MODULE_REFERENCES_THIS);
        }
        break;
      default:
        break;
    }
  }
}
