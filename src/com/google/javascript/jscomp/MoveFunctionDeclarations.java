/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.List;
import java.util.Map.Entry;

/**
 * Moves top-level function declarations to the top.
 *
 * Enable this pass if a try catch block wraps the output after compilation,
 * and the output runs on Firefox because function declarations are only
 * defined when reached inside a try catch block on Firefox.
 *
 * On Firefox, this code works:
 *
 * var g = f;
 * function f() {}
 *
 * but this code does not work:
 *
 * try {
 *   var g = f;
 *   function f() {}
 * } catch(e) {}
 *
 * NOTE(dimvar):
 * This pass is safe to turn on by default and delete the associated compiler
 * option. However, we don't do that because the pass is only useful for code
 * wrapped in a try/catch, and otherwise it makes debugging harder because it
 * moves code around.
 *
 */
class MoveFunctionDeclarations implements Callback, CompilerPass {
  private final AbstractCompiler compiler;
  private final ListMultimap<JSModule, Node> functions;

  MoveFunctionDeclarations(AbstractCompiler compiler) {
    this.compiler = compiler;
    functions = ArrayListMultimap.create();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
    for (Entry<JSModule, List<Node>> entry : Multimaps.asMap(functions).entrySet()) {
      Node addingRoot = compiler.getNodeForCodeInsertion(entry.getKey());
      for (Node n : Lists.reverse(entry.getValue())) {
        Node nameNode = n.getFirstChild();
        String name = nameNode.getString();
        nameNode.setString("");
        addingRoot.addChildToFront(
            IR.var(IR.name(name), n).useSourceInfoIfMissingFromForTree(n));
      }
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    Node grandparent = n.getAncestor(2);
    return grandparent == null || !grandparent.isScript();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (parent == null || !parent.isScript()) {
      return;
    }

    if (NodeUtil.isFunctionDeclaration(n)) {
      parent.removeChild(n);
      compiler.reportCodeChange();

      functions.put(t.getModule(), n);
    }
  }
}
