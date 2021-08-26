/*
 * Copyright 2021 The Closure Compiler Authors.
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

/** Normalizes all CAST nodes out of the AST. */
public final class RemoveCastNodes implements CompilerPass {
  private final AbstractCompiler compiler;

  public RemoveCastNodes(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, new RemoveCastsCallback(), externs, root);
  }

  private final class RemoveCastsCallback extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCast()) {
        compiler.reportChangeToEnclosingScope(n);
        n.replaceWith(n.removeFirstChild());
      }
    }
  }
}
