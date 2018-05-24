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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/** Rewrites a script which was imported as a module into an ES6 module. */
public final class Es6RewriteScriptsToModules extends AbstractPreOrderCallback
    implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  /**
   * Creates a new Es6RewriteModules instance which can be used to rewrite ES6 modules to a
   * concatenable form.
   */
  public Es6RewriteScriptsToModules(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  /**
   * Force rewriting of a script into an ES6 module, such as for imported files that contain no
   * "import" or "export" statements.
   */
  void forceToEs6Module(Node root) {
    if (Es6RewriteModules.isEs6ModuleRoot(root)) {
      return;
    }
    Node moduleNode = new Node(Token.MODULE_BODY).srcref(root);
    moduleNode.addChildrenToBack(root.removeChildren());
    root.addChildToBack(moduleNode);
  }

  @Override
  public void process(Node externs, Node root) {
    for (Node file = root.getFirstChild(); file != null; file = file.getNext()) {
      hotSwapScript(file, null);
    }
  }

  @Override
  public void hotSwapScript(Node scriptNode, Node originalRoot) {
    checkArgument(scriptNode.isScript());
    NodeTraversal.traverse(compiler, scriptNode, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isScript()) {
      CompilerInput.ModuleType moduleType = compiler.getInput(n.getInputId()).getJsModuleType();

      if (moduleType == CompilerInput.ModuleType.IMPORTED_SCRIPT) {
        forceToEs6Module(n);
        compiler.reportChangeToChangeScope(n);
      }
    }

    return false;
  }
}
