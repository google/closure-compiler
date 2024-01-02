/*
 * Copyright 2023 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;

/**
 * Replaces all calls to property-renaming functions like JSCompiler_renameProperty with the string
 * literal property name.
 *
 * <p>Intended for use when {@link RenameProperties} isn't running, since this functionality is also
 * baked into {@link RenameProperties}.
 */
final class RemovePropertyRenamingCalls implements CompilerPass {

  private final AbstractCompiler compiler;

  RemovePropertyRenamingCalls(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new RemoveCompilerPropertyRenamingCallback());
  }

  private final class RemoveCompilerPropertyRenamingCallback
      extends NodeTraversal.AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall()) {
        Node fn = n.getFirstChild();
        if (compiler.getCodingConvention().isPropertyRenameFunction(fn)) {
          replacePropertyRenamingCall(n);
        }
      }
    }

    /** Replaces JSCompiler_renameProperty("bar", obj) with "bar" */
    private void replacePropertyRenamingCall(Node callNode) {
      Node propName = NodeUtil.getArgumentForCallOrNew(callNode, 0);
      if (propName != null) {
        callNode.replaceWith(propName.detach());
        compiler.reportChangeToEnclosingScope(propName);
        return;
      }
    }
  }
}
