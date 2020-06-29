/*
 * Copyright 2020 The Closure Compiler Authors.
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
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

/**
 * Pass to remove from the AST types and type-based information from typechecking.
 *
 * <p>Eventually, we anticipate this pass to run at the beginning of optimizations, and leave a
 * representation of the types as needed for optimizations on the AST.
 */
final class RemoveTypes implements CompilerPass {
  private final AbstractCompiler compiler;

  RemoveTypes(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private static class RemoveTypesFromAst extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      n.setJSType(null);
      n.setJSTypeBeforeCast(null);
      n.setTypedefTypeProp(null);
      n.setDeclaredTypeExpression(null);
      JSDocInfo jsdoc = n.getJSDocInfo();
      if (jsdoc != null && jsdoc.getLicense() == null) {
        // Can license jsdocs also contain type declarations?
        // If so, they should be removed here.
        n.setJSDocInfo(null);
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    Node externsAndJsRoot = root.getParent();
    NodeTraversal.traverse(compiler, externsAndJsRoot, new RemoveTypesFromAst());
    compiler.clearJSTypeRegistry();
  }
}
