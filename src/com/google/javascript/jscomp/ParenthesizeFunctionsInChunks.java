/*
 * Copyright 2022 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Marks all functions of specified chunks for eager parsing by adding the node property
 * Node.MARK_FOR_PARENTHESIZE, which will wrap the fn in ().
 *
 * <p>For non function expressions, we re-write the expression as follows:
 *
 * <ul>
 *   <li>Before: function foo() { ... }
 *   <li>After: var foo = (function() { ... })
 * </ul>
 *
 * <p>A log file is created 'eager_compile_chunks.log' with output on how many functions were marked
 * for eager compile for each specified chunk.
 */
public final class ParenthesizeFunctionsInChunks implements CompilerPass {
  final AbstractCompiler compiler;
  final Set<String> parenthesizeFunctionsInChunks;

  // Map for recording diagnostic information about what was marked for eager compilation.
  final Map<String, Long> chunkToEagerCompileFnCount = new HashMap<>();

  public ParenthesizeFunctionsInChunks(
      AbstractCompiler compiler, Set<String> parenthesizeFunctionsInChunks) {
    this.compiler = compiler;
    this.parenthesizeFunctionsInChunks = parenthesizeFunctionsInChunks;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new Traversal());
    for (String key : chunkToEagerCompileFnCount.keySet()) {
      try (LogFile log = compiler.createOrReopenLog(getClass(), "eager_compile_chunks.log")) {
        log.log("%s: %d fn's marked for eager compile", key, chunkToEagerCompileFnCount.get(key));
      }
    }
  }

  private class Traversal implements NodeTraversal.Callback {

    Traversal() {}

    private void rewriteAsFunctionExpression(Node n) {
      Node nameNode = n.getFirstChild();
      Node name = IR.name(nameNode.getString()).srcref(nameNode);
      Node var = IR.var(name).srcref(n);
      // Add the VAR, remove the FUNCTION
      n.replaceWith(var);
      // readd the function, but remove the function name, to guard against
      // functions that re-assign to themselves, which will end up causing a
      // recursive loop.
      n.getFirstChild().setString("");
      name.addChildToFront(n);
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isScript() || n.isRoot() || n.isModuleBody()) {
        return true;
      }
      if (n.isFunction()) {
        String chunkName = t.getChunk().getName();
        chunkToEagerCompileFnCount.putIfAbsent(chunkName, 0L);
        if (parenthesizeFunctionsInChunks.contains(chunkName)) {
          n.putBooleanProp(Node.MARK_FOR_PARENTHESIZE, true);
          if (!NodeUtil.isFunctionExpression(n)) {
            rewriteAsFunctionExpression(n);
          }
          chunkToEagerCompileFnCount.put(chunkName, chunkToEagerCompileFnCount.get(chunkName) + 1);
        }
        return false;
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {}
  }
}
