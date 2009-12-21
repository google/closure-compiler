/*
 * Copyright 2009 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * Provides common data structures for program analysis.
 *
 * Clients of the symbol table are strictly responsible for keeping
 * the data structures up to date. The testing infrastructure is
 * responsible for verifying that they do this.
 *
 * If a pass does not want to keep its data structures up to date,
 * then it should not use the symbol table--it should directly invoke
 * the factory for the data structure it wants.
 *
*
 */
class SymbolTable implements ScopeCreator, CodeChangeHandler {
  static final DiagnosticType MISSING_VARIABLE =
      DiagnosticType.error(
          "JSC_MISSING_VARIABLE",
          "Missing variable name: {0}");

  static final DiagnosticType MOVED_VARIABLE =
      DiagnosticType.error(
          "JSC_MOVED_VARIABLE",
          "Moved variable name: {0}");

  static final DiagnosticType VARIABLE_COUNT_MISMATCH =
      DiagnosticType.error(
          "JSC_VARIABLE_COUNT_MISMATCH",
          "Variable count does not match." +
          "\nCached : {0}\nActual : {1}");

  static final DiagnosticType SCOPE_MISMATCH =
      DiagnosticType.error(
          "JSC_SCOPE_MISMATCH",
          "Scope roots used with the symbol table do not match." +
          "\nExpected : {0}\nActual : {1}");

  private final AbstractCompiler compiler;
  private final ScopeCreator scopeCreator;

  // Mutex so that the symbol table may only be acquired by one pass
  // at a time.
  private boolean locked = false;

  // Memoized data with the pass that has currently acquired the
  // symbol table.
  private MemoizedData cache = null;

  SymbolTable(AbstractCompiler compiler) {
    this.compiler = compiler;
    compiler.addChangeHandler(this);

    scopeCreator = new SyntacticScopeCreator(compiler);
  }

  synchronized void acquire() {
    Preconditions.checkState(!locked, "SymbolTable already acquired");
    locked = true;
  }

  synchronized void release() {
    Preconditions.checkState(locked, "SymbolTable already released");
    locked = false;
  }

  /**
   * Returns the scope at the given node.
   */
  @Override
  public Scope createScope(Node n, Scope parent) {
    // We may only ask for local blocks and the global (all scripts) block.
    Preconditions.checkArgument(
        (n.getType() == Token.BLOCK && n.getParent() == null) ||
        n.getType() == Token.FUNCTION,
        "May only create scopes for the global node and functions");
    ensureCacheInitialized();

    if (!cache.scopes.containsKey(n)) {
      cache.scopes.put(n, scopeCreator.createScope(n, parent));
    }

    return cache.scopes.get(n);
  }

  /**
   * Ensure that the memoization data structures have been initialized.
   */
  private void ensureCacheInitialized() {
    Preconditions.checkState(locked, "Unacquired symbol table");
    if (cache == null) {
      cache = new MemoizedData();
    }
  }

  /**
   * If the AST changes, and the symbol table has not been acquired, then
   * all of our memoized data structures become stale. So delete them.
   */
  @Override
  public void reportChange() {
    if (!locked) {
      cache = null;
    }
  }

  /**
   * All the data structures cached by this table.
   */
  private static class MemoizedData {
    private Map<Node, Scope> scopes = Maps.newHashMap();
  }

  //----------------------------------------------------------------------------
  // Verification of consistency. Only for tests.

  /**
   * Check that this symbol table has been kept up to date. Compiler warnings
   * will be emitted if anything is wrong.
   * @param expectedRoot The root of the expected AST.
   * @param actualRoot The root of the actual AST used with this symbol table.
   */
  void verify(Node expectedRoot, Node actualRoot) {
    VerifyingCallback callback = new VerifyingCallback(
        expectedRoot, actualRoot);
    callback.verify();
  }

  /**
   * A callback that traverses an AST root and builds all the
   * secondary data structures for it.
   */
  private class VerifyingCallback implements ScopedCallback {
    private final List<Scope> expectedScopes = Lists.newArrayList();
    private final List<Scope> actualScopes = Lists.newArrayList();
    private boolean collectingExpected = true;
    private final Node actualRoot;
    private final Node expectedRoot;

    private VerifyingCallback(Node expectedRoot, Node actualRoot) {
      this.actualRoot = actualRoot;
      this.expectedRoot = expectedRoot;
    }

    @Override
    public boolean shouldTraverse(
        NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {}

    @Override
    public void enterScope(NodeTraversal t) {}

    @Override
    public void exitScope(NodeTraversal t) {
      if (collectingExpected) {
        expectedScopes.add(t.getScope());
      } else {
        actualScopes.add(t.getScope());
      }
    }

    private void verify() {
      if (cache == null) {
        // The symbol table was never used, so no need to check anything.
        return;
      }

      if (!cache.scopes.isEmpty()) {
        verifyScopes();
      }
    }

    private void verifyScopes() {
      collectingExpected = true;
      NodeTraversal.traverse(compiler, expectedRoot, this);

      collectingExpected = false;
      (new NodeTraversal(compiler, this, SymbolTable.this))
          .traverse(actualRoot);

      // This must be true unless something went horribly, horribly wrong.
      Preconditions.checkState(expectedScopes.size() == actualScopes.size());

      for (int i = 0; i < expectedScopes.size(); i++) {
        Scope expectedScope = expectedScopes.get(i);
        Scope actualScope = actualScopes.get(i);

        if (!checkNodesMatch(expectedScope.getRootNode(),
                actualScope.getRootNode())) {
          compiler.report(
              JSError.make(
                  SCOPE_MISMATCH,
                  expectedScope.getRootNode().toStringTree(),
                  actualScope.getRootNode().toStringTree()));
          continue;
        }

        if (expectedScope.getVarCount() != actualScope.getVarCount()) {
          compiler.report(
              JSError.make(
                  VARIABLE_COUNT_MISMATCH,
                  Integer.toString(expectedScope.getVarCount()),
                  Integer.toString(actualScope.getVarCount())));
        } else {
          Iterator<Var> it = expectedScope.getVars();
          while (it.hasNext()) {
            Var var = it.next();
            Scope.Var actualVar = actualScope.getVar(var.getName());
            if (actualVar == null ||
                expectedScope.getVar(var.getName()) != var) {
              compiler.report(
                  JSError.make(MISSING_VARIABLE, var.getName()));
            } else if (
                !checkNodesMatch(
                    var.getNameNode(),
                    actualVar.getNameNode()) ||
                !isNodeAttached(actualVar.getNameNode())) {
              compiler.report(
                  JSError.make(MOVED_VARIABLE, var.getName()));
            }
          }
        }
      }
    }

    /**
     * Check that the two nodes have the same relative position in the tree.
     */
    private boolean checkNodesMatch(Node nodeA, Node nodeB) {
      Node currentA = nodeA;
      Node currentB = nodeB;
      while (currentA != null && currentB != null) {
        if (currentA.getType() != currentB.getType() ||
            !currentA.isEquivalentTo(currentB)) {
          return false;
        }

        currentA = currentA.getParent();
        currentB = currentB.getParent();
      }

      return currentA == null && currentB == null;
    }

    private boolean isNodeAttached(Node node) {
      // Make sure the cached var is still attached.
      for (Node current = node;
           current != null; current = current.getParent()) {
        if (current.getType() == Token.SCRIPT) {
          return true;
        }
      }
      return false;
    }
  }
}
