/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Rewrites ES6 import paths to be absolute according to the compiler's module resolver.
 *
 * <p>Useful for servers that wish to preserve ES6 modules, meaning their paths need to be valid
 * in the browser.</p>
 */
public class Es6RewriteImportPaths implements CompilerPass {

  private final AbstractCompiler compiler;

  public Es6RewriteImportPaths(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    for (Node script : root.children()) {
      if (Es6RewriteModules.isEs6ModuleRoot(script)) {
        NodeTraversal.traverse(compiler, script, new Rewriter());
        script.putBooleanProp(Node.TRANSPILED, true);
      }
    }
  }

  private static class Rewriter extends AbstractPreOrderCallback {
    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      switch (n.getToken()) {
        case ROOT:
        case MODULE_BODY:
        case SCRIPT:
          return true;
        case IMPORT:
          visitImport(nodeTraversal, n);
          return false;
        case EXPORT:
          visitExport(nodeTraversal, n);
          return false;
        default:
          return false;
      }
    }

    private void visitImport(NodeTraversal t, Node importDecl) {
      Node specifierNode = importDecl.getLastChild();
      String specifier = specifierNode.getString();
      String newSpecifier = t.getInput().getPath().resolveModuleAsPath(specifier).toString();
      if (!newSpecifier.equals(specifier)) {
        specifierNode.setString(newSpecifier);
        t.reportCodeChange(specifierNode);
      }
    }

    private void visitExport(NodeTraversal t, Node export) {
      if (export.hasTwoChildren()) {
        // export from
        visitImport(t, export);
      }
    }
  }
}
