/*
 * Copyright 2014 The Closure Compiler Authors.
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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A compiler pass to normalize externs by declaring global names on
 * the "window" object.
 */
class DeclaredGlobalExternsOnWindow
    extends NodeTraversal.AbstractShallowStatementCallback
    implements CompilerPass {

  private final AbstractCompiler compiler;
  private final Set<String> names = new LinkedHashSet<>();

  public DeclaredGlobalExternsOnWindow(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, this);

    addWindowProperties();
  }

  private void addWindowProperties() {
    if (names.size() > 0) {
      Node declRoot = getSynthesizedExternsRoot();
      for (String prop : names) {
        addExtern(declRoot, prop);
      }
      compiler.reportCodeChange();
    }
  }

  private static void addExtern(Node declRoot, String export) {
    // TODO(johnlenz): add type declarations.
    Node propstmt = IR.exprResult(
        IR.getprop(IR.name("window"), IR.string(export)));
    declRoot.addChildToBack(propstmt);
  }

  /** Lazily create a "new" externs root for undeclared variables. */
  private Node getSynthesizedExternsRoot() {
    return  compiler.getSynthesizedExternsInput().getAstRoot(compiler);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isFunction()) {
      names.add(n.getFirstChild().getString());
    } else if (n.isVar()) {
      for (Node c : n.children()) {
        names.add(c.getString());
      }
    }
  }

}
