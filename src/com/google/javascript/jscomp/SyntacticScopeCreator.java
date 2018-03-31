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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

/**
 * <p>The syntactic scope creator scans the parse tree to create a Scope object
 * containing all the variable declarations in that scope.</p>
 *
 * <p>This implementation is not thread-safe.</p>
 *
 */
public class SyntacticScopeCreator implements ScopeCreator {
  private final AbstractCompiler compiler;
  private AbstractScope<?, ?> scope;
  private InputId inputId;

  private final boolean isTyped;

  private SyntacticScopeCreator(AbstractCompiler compiler, boolean isTyped) {
    this.compiler = compiler;
    this.isTyped = isTyped;
  }

  /**
   * @deprecated Use Es6SyntacticScopeCreator instead.
   */
  @Deprecated
  public static SyntacticScopeCreator makeUntyped(AbstractCompiler compiler) {
    return new SyntacticScopeCreator(compiler, false);
  }

  static SyntacticScopeCreator makeTyped(AbstractCompiler compiler) {
    return new SyntacticScopeCreator(compiler, true);
  }

  @Override
  @SuppressWarnings("unchecked")
  // The cast to T is OK because we cannot mix typed and untyped scopes in the same chain.
  public AbstractScope<?, ?> createScope(Node n, AbstractScope<?, ?> parent) {
    inputId = null;
    if (parent == null) {
      scope = isTyped ? TypedScope.createGlobalScope(n) : Scope.createGlobalScope(n);
    } else {
      scope =
          isTyped
              ? new TypedScope((TypedScope) parent, n)
              : Scope.createChildScope((Scope) parent, n);
    }

    scanRoot(n);

    inputId = null;
    AbstractScope<?, ?> returnedScope = scope;
    scope = null;
    return returnedScope;
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
      checkState(args.isParamList());
      for (Node a = args.getFirstChild(); a != null;
           a = a.getNext()) {
        checkState(a.isName());
        declareVar(a);
      }

      // Body
      scanVars(body);
    } else {
      // It's either a module or the global block
      Preconditions.checkState(n.isModuleBody() || scope.getParent() == null,
          "Expected %s to be a module body, or %s to be the global scope.", n, scope);
      scanVars(n);
    }
  }

  /**
   * Scans and gather variables declarations under a Node
   */
  private void scanVars(Node n) {
    switch (n.getToken()) {
      case VAR:
        // Declare all variables. e.g. var x = 1, y, z;
        for (Node child = n.getFirstChild();
             child != null;) {
          Node next = child.getNext();
          declareVar(child);
          child = next;
        }
        return;

      case FUNCTION:
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

      case CATCH:
        Preconditions.checkState(n.hasTwoChildren(), n);
        // The first child is the catch var and the second child
        // is the code block.

        final Node var = n.getFirstChild();
        Preconditions.checkState(var.isName(), var);

        final Node block = var.getNext();

        declareVar(var);
        scanVars(block);
        return; // only one child to scan

      case SCRIPT:
        inputId = n.getInputId();
        checkNotNull(inputId);
        break;
      default:
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
   * Declares a variable.
   *
   * @param n The node corresponding to the variable name.
   */
  private void declareVar(Node n) {
    checkState(n.isName(), n);

    CompilerInput input = compiler.getInput(inputId);
    String name = n.getString();
    if (!scope.hasOwnSlot(name) && (!scope.isLocal() || !name.equals(Var.ARGUMENTS))) {
      if (isTyped) {
        ((TypedScope) scope).declare(name, n, null, input);
      } else {
        ((Scope) scope).declare(name, n, input);
      }
    }
  }

  @Override
  public boolean hasBlockScope() {
    return false;
  }
}
