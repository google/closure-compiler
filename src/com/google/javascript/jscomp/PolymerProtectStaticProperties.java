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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Prevents renaming of the Polymer 2 static properties "is" and "config" by adding externs to the
 * Function.prototype.
 *
 * <p>This pass must run after type checking since otherwise all functions would have an "is" and
 * "config" property.
 */
class PolymerProtectStaticProperties implements CompilerPass {

  private final AbstractCompiler compiler;

  PolymerProtectStaticProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    FindPolymerExterns findPolymerExterns = new FindPolymerExterns();
    NodeTraversal.traverseEs6(compiler, externs, findPolymerExterns);

    if (findPolymerExterns.hasPolymerElementExterns()) {
      addExtern("is");
      addExtern("properties");
      addExtern("observers");
    }
  }

  private void addExtern(String export) {
    Node propstmt =
        IR.exprResult(
            IR.getprop(NodeUtil.newQName(compiler, "Function.prototype"), IR.string(export)));
    propstmt.useSourceInfoFromForTree(getSynthesizedExternsRoot());
    propstmt.setOriginalName(export);
    getSynthesizedExternsRoot().addChildToBack(propstmt);
    compiler.reportCodeChange();
  }

  /** Lazily create a "new" externs root for undeclared variables. */
  private Node getSynthesizedExternsRoot() {
    return compiler.getSynthesizedExternsInput().getAstRoot(compiler);
  }

  /** Traverse the externs to find references to Polymer.Element. */
  class FindPolymerExterns implements NodeTraversal.Callback {
    private boolean hasPolymerElementExterns_ = false;

    public boolean hasPolymerElementExterns() {
      return hasPolymerElementExterns_;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (t.inGlobalScope()) {
        if (n.isAssign()
            && n.getFirstChild().isGetProp()
            && n.getFirstChild().matchesQualifiedName("Polymer.Element")) {
          hasPolymerElementExterns_ = true;
        }
      }
    }
  }
}
