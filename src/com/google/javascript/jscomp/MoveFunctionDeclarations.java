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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;

import java.util.List;
import java.util.Map;
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
 */
class MoveFunctionDeclarations implements Callback, CompilerPass {
  private final AbstractCompiler compiler;
  private final Map<JSModule, List<Node>> functions;

  MoveFunctionDeclarations(AbstractCompiler compiler) {
    this.compiler = compiler;
    functions = Maps.newHashMap();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    for (Entry<JSModule, List<Node>> entry : functions.entrySet()) {
      JSModule module = entry.getKey();
      Node addingRoot = compiler.getNodeForCodeInsertion(module);
      for (Node n : Lists.reverse(entry.getValue())) {
        addingRoot.addChildToFront(n);
      }
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    Node gramps = n.getAncestor(2);
    return gramps == null || !gramps.isScript();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (parent == null || !parent.isScript()) {
      return;
    }

    if (NodeUtil.isFunctionDeclaration(n)) {
      parent.removeChild(n);
      compiler.reportCodeChange();

      JSModule module = t.getModule();
      List<Node> moduleFunctions = functions.get(module);
      if (moduleFunctions == null) {
        moduleFunctions = Lists.newArrayList();
        functions.put(module, moduleFunctions);
      }
      moduleFunctions.add(n);
    }
  }
}
