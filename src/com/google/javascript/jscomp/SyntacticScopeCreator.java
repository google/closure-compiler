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
import com.google.javascript.rhino.JSDocInfo;
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

  public static final DiagnosticType VAR_MULTIPLY_DECLARED_ERROR =
      DiagnosticType.error(
          "JSC_VAR_MULTIPLY_DECLARED_ERROR",
          "Variable {0} first declared in {1}");

  public static final DiagnosticType VAR_ARGUMENTS_SHADOWED_ERROR =
    DiagnosticType.error(
        "JSC_VAR_ARGUMENTS_SHADOWED_ERROR",
        "Shadowing \"arguments\" is not allowed");

  /**
   * Creates a ScopeCreator.
   */
  SyntacticScopeCreator(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.redeclarationHandler = new DefaultRedeclarationHandler();
  }

  SyntacticScopeCreator(
      AbstractCompiler compiler, RedeclarationHandler redeclarationHandler) {
    this.compiler = compiler;
    this.redeclarationHandler = redeclarationHandler;
  }

  @Override
  public Scope createScope(Node n, Scope parent) {
    inputId = null;
    if (parent == null) {
      scope = Scope.createGlobalScope(n);
    } else {
      scope = new Scope(parent, n);
    }

    scanRoot(n, parent);

    inputId = null;
    Scope returnedScope = scope;
    scope = null;
    return returnedScope;
  }

  private void scanRoot(Node n, Scope parent) {
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
      scanVars(body, n);
    } else {
      // It's the global block
      Preconditions.checkState(scope.getParent() == null);
      scanVars(n, null);
    }
  }

  /**
   * Scans and gather variables declarations under a Node
   */
  private void scanVars(Node n, Node parent) {
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
        Preconditions.checkState(n.getChildCount() == 2);
        Preconditions.checkState(n.getFirstChild().isName());
        // the first child is the catch var and the third child
        // is the code block

        final Node var = n.getFirstChild();
        final Node block = var.getNext();

        declareVar(var);
        scanVars(block, n);
        return;  // only one child to scan

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
        scanVars(child, n);
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
  private class DefaultRedeclarationHandler implements RedeclarationHandler {
    @Override
    public void onRedeclaration(
        Scope s, String name, Node n, CompilerInput input) {
      Node parent = n.getParent();

      // Don't allow multiple variables to be declared at the top-level scope
      if (scope.isGlobal()) {
        Scope.Var origVar = scope.getVar(name);
        Node origParent = origVar.getParentNode();
        if (origParent.isCatch() &&
            parent.isCatch()) {
          // Okay, both are 'catch(x)' variables.
          return;
        }

        boolean allowDupe = hasDuplicateDeclarationSuppression(n, origVar);

        if (!allowDupe) {
          compiler.report(
              JSError.make(NodeUtil.getSourceName(n), n,
                           VAR_MULTIPLY_DECLARED_ERROR,
                           name,
                           (origVar.input != null
                            ? origVar.input.getName()
                            : "??")));
        }
      } else if (name.equals(ARGUMENTS) && !NodeUtil.isVarDeclaration(n)) {
        // Disallow shadowing "arguments" as we can't handle with our current
        // scope modeling.
        compiler.report(
            JSError.make(NodeUtil.getSourceName(n), n,
                VAR_ARGUMENTS_SHADOWED_ERROR));
      }
    }
  }

  /**
   * Declares a variable.
   *
   * @param n The node corresponding to the variable name.
   */
  private void declareVar(Node n) {
    Preconditions.checkState(n.isName());

    CompilerInput input = compiler.getInput(inputId);
    String name = n.getString();
    if (scope.isDeclared(name, false)
        || (scope.isLocal() && name.equals(ARGUMENTS))) {
      redeclarationHandler.onRedeclaration(
          scope, name, n, input);
    } else {
      scope.declare(name, n, null, input);
    }
  }


  /**
   * @param n The name node to check.
   * @param origVar The associated Var.
   * @return Whether duplicated declarations warnings should be suppressed
   *     for the given node.
   */
  static boolean hasDuplicateDeclarationSuppression(Node n, Scope.Var origVar) {
    Preconditions.checkState(n.isName());
    Node parent = n.getParent();
    Node origParent = origVar.getParentNode();

    JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      info = parent.getJSDocInfo();
    }
    if (info != null && info.getSuppressions().contains("duplicate")) {
      return true;
    }

    info = origVar.nameNode.getJSDocInfo();
    if (info == null) {
      info = origParent.getJSDocInfo();
    }
    return (info != null && info.getSuppressions().contains("duplicate"));
  }

  /**
   * Generates an untyped global scope from the root of AST of compiler (which
   * includes externs).
   *
   * @param compiler The compiler for which the scope is generated.
   * @return The new untyped global scope generated as a result of this call.
   */
  static Scope generateUntypedTopScope(AbstractCompiler compiler) {
    return new SyntacticScopeCreator(compiler).createScope(compiler.getRoot(),
        null);
  }


}
