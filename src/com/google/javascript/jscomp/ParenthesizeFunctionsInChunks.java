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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.nullness.Nullable;

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
  private final AbstractCompiler compiler;
  private final Set<String> parenthesizeFunctionsInChunks;

  /**
   * @param compiler An abstract compiler.
   * @param parenthesizeFunctionsInChunks The set of chunk names in which to parenthesize top level
   *     functions.
   */
  public ParenthesizeFunctionsInChunks(
      AbstractCompiler compiler, Set<String> parenthesizeFunctionsInChunks) {
    this.compiler = compiler;
    this.parenthesizeFunctionsInChunks = parenthesizeFunctionsInChunks;
  }

  @Override
  public void process(Node externs, Node root) {
    Traversal traversal = new Traversal(parenthesizeFunctionsInChunks);
    NodeTraversal.traverse(compiler, root, traversal);

    Map<String, Long> chunkToEagerCompileFnCounts = traversal.getChunkToEagerCompileFnCounts();

    try (LogFile log = compiler.createOrReopenLog(getClass(), "eager_compile_chunks.log")) {
      for (Map.Entry<String, Long> entry : chunkToEagerCompileFnCounts.entrySet()) {
        log.log("%s: %d fn's marked for eager compile", entry.getKey(), entry.getValue());
      }
    }
  }

  private static class Traversal implements NodeTraversal.Callback {
    private final Set<String> parenthesizeFunctionsInChunks;

    // The stack of nested block scopes for the node we're currently visiting.
    private final Deque<Node> nestedBlockScopes = new ArrayDeque<>();

    // A multimap relating a scope node to any children nodes which should be hosted into its scope.
    private final ListMultimap<Node, Node> hoistNodesToScope = ArrayListMultimap.create();

    // Map for recording diagnostic information about what was marked for eager compilation.
    private final Map<String, Long> chunkToEagerCompileFnCounts = new LinkedHashMap<>();

    public Traversal(Set<String> parenthesizeFunctionsInChunks) {
      this.parenthesizeFunctionsInChunks = parenthesizeFunctionsInChunks;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (!shouldParenthesizeTree(t, n)) {
        return false;
      }

      if (parent != null && parent.isFunction()) {
        return false; // Don't visit the contents of any functions.
      }

      if (NodeUtil.isStatementBlock(n)) {
        nestedBlockScopes.push(n); // Enter block scope.
      }

      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (isInnerMostBlockScope(n)) {
        hoistChildrenToTopOfScope(n);
        nestedBlockScopes.pop(); // Exit block scope.
      } else if (NodeUtil.isFunctionExpression(n)) {
        n.setMarkForParenthesize(true);
        incrementEagerlyCompiledFunctionCount(t);
      } else if (NodeUtil.isFunctionDeclaration(n)) {
        n.setMarkForParenthesize(true);
        addChildForHoistToScope(functionDeclarationToFunctionExpression(t, n));
        incrementEagerlyCompiledFunctionCount(t);
      } else {
        // Ex: Method function definitions.
        // Do nothing.
      }
    }

    public Map<String, Long> getChunkToEagerCompileFnCounts() {
      checkState(
          nestedBlockScopes.isEmpty(), "Expected empty scope stack. Got: %s", nestedBlockScopes);
      checkState(
          hoistNodesToScope.isEmpty(), "Expected empty hoist map. Got: %s", hoistNodesToScope);
      return chunkToEagerCompileFnCounts;
    }

    /**
     * Whether we should parenthesize functions in the given tree of the traversal. If there is no
     * chunk, then we are compiling a single-chunk output, so we will just parenthesize all
     * top-level functions.
     */
    private boolean shouldParenthesizeTree(NodeTraversal t, Node n) {
      if (!n.isScript()) {
        return true;
      }

      String chunkName = getChunkName(t);
      return chunkName == null || parenthesizeFunctionsInChunks.contains(chunkName);
    }

    private void incrementEagerlyCompiledFunctionCount(NodeTraversal t) {
      chunkToEagerCompileFnCounts.merge(getChunkName(t), 1L, (oldValue, value) -> oldValue + 1);
    }

    /** Gets the chunk name of the current traveral or null if it doesn't belong to a chunk. */
    private @Nullable String getChunkName(NodeTraversal t) {
      JSChunk chunk = t.getChunk();
      return (chunk != null ? chunk.getName() : null);
    }

    /** Whether this node is the inner-most block scope. */
    private boolean isInnerMostBlockScope(Node n) {
      return !nestedBlockScopes.isEmpty() && nestedBlockScopes.peek() == n;
    }

    /**
     * Converts the given function declaration into a function expression suitable for wrapping in
     * parenthesis. The function expression is assigned to a `var` declaration so that it is
     * semantically hoisted to the inner-most function scope like with function declarations.
     */
    private Node functionDeclarationToFunctionExpression(NodeTraversal t, Node n) {
      AbstractCompiler compiler = t.getCompiler();
      Node nameNode = n.getFirstChild();
      Node name = IR.name(nameNode.getString()).srcref(nameNode);
      Node var = IR.var(name).srcref(n);
      // read the function, but remove the function name, to guard against
      // functions that re-assign to themselves, which will end up causing a
      // recursive loop.
      nameNode.setString("");
      compiler.reportChangeToEnclosingScope(n.getLastChild());
      // Add the VAR, remove the FUNCTION
      n.replaceWith(var);
      compiler.reportChangeToEnclosingScope(var);
      name.addChildToFront(n);
      return var;
    }

    /**
     * Marks a node to be moved to the top of its parent *block* scope. This is useful for hoisting
     * function expression assignments to emulate how function declarations are assigned.
     *
     * <pre>
     * Function declarations have the following semantics:
     * 1. Hoists the function variable declaration to the top of the inner-most *function* scope.
     * 2. Hoists the function assignment to the top of the inner-most *block* scope.
     * </pre>
     */
    private void addChildForHoistToScope(Node node) {
      if (nestedBlockScopes.isEmpty()) { // This node is already at the root scope.
        return;
      }

      Node innerMostBlockScope = nestedBlockScopes.peek();
      hoistNodesToScope.put(innerMostBlockScope, node.detach());
    }

    /** Hoists any marked nodes to the beginning of this scope. */
    private void hoistChildrenToTopOfScope(Node scope) {
      List<Node> nodes = new ArrayList<>(hoistNodesToScope.removeAll(scope));
      Collections.reverse(nodes); // Maintain node order when pushing as first sibling.

      for (Node node : nodes) {
        scope.addChildToFront(node);
      }
    }
  }
}
