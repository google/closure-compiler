/*
 * Copyright 2006 The Closure Compiler Authors.
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
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * <p>The syntactic scope creator scans the parse tree to create a Scope object
 * containing all the variable declarations in that scope.</p>
 *
 * <p>This implementation is not thread-safe.</p>
 *
 */
class SyntacticScopeCreator implements ScopeCreator {
  private final AbstractCompiler compiler;
  private Scope scope;
  private InputId inputId;
  private final RedeclarationHandler redeclarationHandler;

  // The arguments variable is special, in that it's declared in every local
  // scope, but not explicitly declared.
  private static final String ARGUMENTS = "arguments";

  private final boolean isTyped;

  private SyntacticScopeCreator(
      AbstractCompiler compiler, RedeclarationHandler redeclarationHandler) {
    this.compiler = compiler;
    this.isTyped = false;
    this.redeclarationHandler = redeclarationHandler;
  }

  private SyntacticScopeCreator(AbstractCompiler compiler, boolean isTyped) {
    this.compiler = compiler;
    this.isTyped = isTyped;
    this.redeclarationHandler = new DefaultRedeclarationHandler();
  }

  static SyntacticScopeCreator makeUntyped(AbstractCompiler compiler) {
    return new SyntacticScopeCreator(compiler, false);
  }

  static SyntacticScopeCreator makeTyped(AbstractCompiler compiler) {
    return new SyntacticScopeCreator(compiler, true);
  }

  static SyntacticScopeCreator makeUntypedWithRedeclHandler(
      AbstractCompiler compiler, RedeclarationHandler redeclarationHandler) {
    return new SyntacticScopeCreator(compiler, redeclarationHandler);
  }

  @Override
  @SuppressWarnings("unchecked")
  // The cast to T is OK because we cannot mix typed and untyped scopes in the same chain.
  public <T extends Scope> T createScope(Node n, T parent) {
    inputId = null;
    if (parent == null) {
      scope = isTyped ? TypedScope.createGlobalScope(n) : Scope.createGlobalScope(n);
    } else {
      scope = isTyped ? new TypedScope((TypedScope) parent, n) : new Scope(parent, n);
    }

    scanRoot(n);

    inputId = null;
    Scope returnedScope = scope;
    scope = null;
    return (T) returnedScope;
  }

  private void scanRoot(Node n) {
    if (n.isFunction()) {
      if (inputId == null) {
        inputId = NodeUtil.getInputId(n);
        // TODO(johnlenz): inputId maybe null if the FUNCTION node is detached
        // from the AST.
        // Is it meaningful to build a scope for detached FUNCTION node?
      }

      final Node fnNameNode = n.getFirstChild();
      final Node args = fnNameNode.getNext();
      final Node body = args.getNext();

      // Bleed the function name into the scope, if it hasn't
      // been declared in the outer scope.
      String fnName = fnNameNode.getString();
      if (!fnName.isEmpty() && NodeUtil.isFunctionExpression(n)) {
        declareVar(fnNameNode);
      }

      // Args: Declare function variables
      Preconditions.checkState(args.isParamList());
      for (Node a = args.getFirstChild(); a != null;
           a = a.getNext()) {
        Preconditions.checkState(a.isName());
        declareVar(a);
      }

      // Body
      scanVars(body);
    } else {
      // It's the global block
      Preconditions.checkState(scope.getParent() == null);
      scanVars(n);
    }
  }

  /**
   * Scans and gather variables declarations under a Node
   */
  private void scanVars(Node n) {
    switch (n.getType()) {
      case Token.VAR:
        // Declare all variables. e.g. var x = 1, y, z;
        for (Node child = n.getFirstChild();
             child != null;) {
          Node next = child.getNext();
          declareVar(child);
          child = next;
        }
        return;

      case Token.FUNCTION:
        if (NodeUtil.isFunctionExpression(n)) {
          return;
        }

        String fnName = n.getFirstChild().getString();
        if (fnName.isEmpty()) {
          // This is invalid, but allow it so the checks can catch it.
          return;
        }
        declareVar(n.getFirstChild());
        return;   // should not examine function's children

      case Token.CATCH:
        Preconditions.checkState(n.getChildCount() == 2, n);
        // The first child is the catch var and the second child
        // is the code block.

        final Node var = n.getFirstChild();
        Preconditions.checkState(var.isName(), var);

        final Node block = var.getNext();

        declareVar(var);
        scanVars(block);
        return; // only one child to scan

      case Token.SCRIPT:
        inputId = n.getInputId();
        Preconditions.checkNotNull(inputId);
        break;
    }

    // Variables can only occur in statement-level nodes, so
    // we only need to traverse children in a couple special cases.
    if (NodeUtil.isControlStructure(n) || NodeUtil.isStatementBlock(n)) {
      for (Node child = n.getFirstChild();
           child != null;) {
        Node next = child.getNext();
        scanVars(child);
        child = next;
      }
    }
  }

  /**
   * Interface for injectable duplicate handling.
   */
  interface RedeclarationHandler {
    void onRedeclaration(
        Scope s, String name, Node n, CompilerInput input);
  }

  /**
   * The default handler for duplicate declarations.
   */
  static class DefaultRedeclarationHandler implements RedeclarationHandler {
    @Override
    public void onRedeclaration(Scope s, String name, Node n, CompilerInput input) {}
  }

  /**
   * Declares a variable.
   *
   * @param n The node corresponding to the variable name.
   */
  private void declareVar(Node n) {
    Preconditions.checkState(n.isName(), n);

    CompilerInput input = compiler.getInput(inputId);
    String name = n.getString();
    if (scope.isDeclared(name, false)
        || (scope.isLocal() && name.equals(ARGUMENTS))) {
      redeclarationHandler.onRedeclaration(
          scope, name, n, input);
    } else {
      if (isTyped) {
        ((TypedScope) scope).declare(name, n, null, input);
      } else {
        scope.declare(name, n, input);
      }
    }
  }

  @Override
  public boolean hasBlockScope() {
    return false;
  }
}
