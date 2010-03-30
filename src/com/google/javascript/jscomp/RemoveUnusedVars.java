/*
 * Copyright 2008 Google Inc.
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
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.*;
import java.util.logging.Logger;


/**
 * Garbage collection for variable and function definitions. Basically performs
 * a mark-and-sweep type algorithm over the javascript parse tree.
 *
 * For each scope:
 * (1) Scan the variable/function declarations at that scope, but do not
 *     recurse into the function definitions.
 * (2) Mark all referenced variables and functions calls. Recurse into the
 *     scopes of those function calls.
 * (3) When leaving the scope, remove any variables in that scope that were
 *     never referenced.
 *
 * Multiple passes are performed until no variables are removed, indicating a
 * convergence.
 *
*
 */
class RemoveUnusedVars implements CompilerPass {
  private static final Logger logger_ =
    Logger.getLogger(RemoveUnusedVars.class.getName());

  private final AbstractCompiler compiler_;

  /** Keeps track of the number of variables removed per instance. */
  private int numRemoved_ = 0;

  private final boolean removeGlobals;

  private boolean preserveAnonymousFunctionNames;

  /**
   * Keeps track of what variables we've warned about, so that we don't do it
   * on subsequent traversals.
   */
  private final Set<Var> warnedVars_ = Sets.newHashSet();

  /**
   * Keep track of variables that we've referenced.
   */
  private final Set<Var> referenced = Sets.newHashSet();

  RemoveUnusedVars(
      AbstractCompiler compiler,
      boolean removeGlobals,
      boolean preserveAnonymousFunctionNames) {
    compiler_ = compiler;
    this.removeGlobals = removeGlobals;
    this.preserveAnonymousFunctionNames = preserveAnonymousFunctionNames;
  }

  /**
   * Traverses the root, removing all unused variables. Multiple traversals
   * may occur to ensure all unused variables are removed.
   */
  public void process(Node externs, Node root) {
    warnedVars_.clear();
    numRemoved_ = 0;
    referenced.clear();

    traverseAndRemoveUnusedReferences(root);

    if (numRemoved_ > 0) {
      compiler_.reportCodeChange();
    }
  }

  /**
   * Traverses a node recursively. Call this once per pass.
   */
  private void traverseAndRemoveUnusedReferences(Node root) {
    Scope scope = new SyntacticScopeCreator(compiler_).createScope(root, null);
    traverseNode(root, null, scope);

    if (removeGlobals) {
      removeUnreferencedVars(scope);
    }
  }

  /**
   * Traverses everything in the current scope and marks variables that
   * are referenced. Functions create their own scope, so we don't
   * recurse into them unless they are called.
   */
  private void traverseNode(Node n, Node parent, Scope scope) {
    int type = n.getType();
    switch (type) {
      // We traverse a function only if the function definition is
      // immediately being referenced, e.g.
      // - return function() { ... };
      // - foo( function() { ... } )
      // - array[ function() { ... } ];
      //
      // Otherwise we traverse into the function only when we encounter
      // a reference to it (see markReferencedVar())
      case Token.FUNCTION:
        // If it's an exported function, or an anonymous function, assume
        // that it'll be called.
        if (NodeUtil.isFunctionAnonymous(n) ||
            compiler_.getCodingConvention().isExported(
                n.getFirstChild().getString())) {
          traverseFunction(n, scope);
        }
        return;

      case Token.NAME:
        if (parent.getType() != Token.VAR) {
          // All non-var declarations are references to other vars
          Var var = scope.getVar(n.getString());
          if (var != null) {
            markReferencedVar(var);
          }
        }
        break;
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      traverseNode(c, n, scope);
    }
  }

  /**
   * Traverses a function, which creates a new scope in javascript.
   *
   * Note that CATCH blocks also create a new scope, but only for the
   * catch variable. Declarations within the block actually belong to the
   * enclosing scope. Because we don't remove catch variables, there's
   * no need to treat CATCH blocks differently like we do functions.
   */
  private void traverseFunction(Node n, Scope scope) {
    Preconditions.checkState(n.getChildCount() == 3);
    Preconditions.checkState(n.getType() == Token.FUNCTION);

    final Node body = n.getLastChild();
    Preconditions.checkState(body.getNext() == null &&
            body.getType() == Token.BLOCK);

    Scope fnScope = new SyntacticScopeCreator(compiler_).createScope(n, scope);
    traverseNode(body, n, fnScope);

    removeUnreferencedFunctionArgs(n, fnScope);
    removeUnreferencedVars(fnScope);
  }

  /**
   * Removes unreferenced arguments from a function declaration.
   *
   * @param function The FUNCTION node
   * @param fnScope The scope inside the function
   */
  private void removeUnreferencedFunctionArgs(Node function, Scope fnScope) {
    // Strip unreferenced args off the end of the function declaration.
    Node argList = function.getFirstChild().getNext();
    Node lastArg;
    while ((lastArg = argList.getLastChild()) != null) {
      Var var = fnScope.getVar(lastArg.getString());
      if (!referenced.contains(var)) {
        argList.removeChild(lastArg);
        fnScope.undeclare(var);
        numRemoved_++;
      } else {
        break;
      }
    }
  }

  /**
   * Marks a var as referenced, recursing into any functions.
   */
  private void markReferencedVar(Var var) {
    if (referenced.contains(var)) {
      // Already marked
      return;
    }
    referenced.add(var);

    Node parent = var.getParentNode();
    if (parent.getType() == Token.FUNCTION &&
        var.getInitialValue() != var.scope.getRootNode()) {
      // Now that the function has been referenced, traverse it.
      // Unless it's a bleeding function, in which case we're already
      // traversing it.

      traverseFunction(parent, var.scope);
    }
  }

  /**
   * Removes any vars in the scope that were not referenced.
   */
  private void removeUnreferencedVars(Scope scope) {
    CodingConvention convention = compiler_.getCodingConvention();

    for (Iterator<Var> it = scope.getVars(); it.hasNext(); ) {
      Var var = it.next();

      if (!referenced.contains(var) &&
          (var.isLocal() || !convention.isExported(var.name))) {

        compiler_.addToDebugLog("Unreferenced var: " + var.name);
        Node nameNode = var.nameNode;
        Node toRemove = nameNode.getParent();
        Node parent = toRemove.getParent();

        Preconditions.checkState(
            toRemove.getType() == Token.VAR ||
            toRemove.getType() == Token.FUNCTION ||
            toRemove.getType() == Token.LP &&
            parent.getType() == Token.FUNCTION,
            "We should only declare vars and functions and function args");

        if (toRemove.getType() == Token.LP &&
            parent.getType() == Token.FUNCTION) {
          // Don't remove function arguments here. That's a special case
          // that's taken care of in removeUnreferencedFunctionArgs.
        } else if (toRemove.getType() == Token.FUNCTION &&
            NodeUtil.isFunctionAnonymous(toRemove)) {
          if (!preserveAnonymousFunctionNames) {
            toRemove.getFirstChild().setString("");
            compiler_.reportCodeChange();
          }
          // Don't remove bleeding functions.
        } else if (parent != null &&
            parent.getType() == Token.FOR &&
            parent.getChildCount() < 4) {
          // foreach iterations have 3 children. Leave them alone.
        } else if (toRemove.getType() == Token.VAR &&
                   nameNode.hasChildren() &&
                   NodeUtil.mayHaveSideEffects(nameNode.getFirstChild())) {
          if (!warnedVars_.contains(var)) {
            warnedVars_.add(var);
            String inputName = var.input != null
                               ? var.input.getName()
                               : "<unknown>";
            logger_.info("Unused var " + var.name +
                         " declared in " + inputName +
                         " at line " + toRemove.getLineno() +
                         " may have side effects and can't be removed");
          }

          // If this is a single var declaration, we can at least remove the
          // declaration itself and just leave the value, e.g.,
          // var a = foo(); => foo();
          if (toRemove.getChildCount() == 1) {
            parent.replaceChild(toRemove,
                new Node(Token.EXPR_RESULT, nameNode.removeFirstChild()));
            numRemoved_++;
          }
        } else if (toRemove.getType() == Token.VAR &&
                   toRemove.getChildCount() > 1) {
          // For var declarations with multiple names (i.e. var a, b, c),
          // only remove the unreferenced name
          toRemove.removeChild(nameNode);
          numRemoved_++;
        } else if (parent != null) {
          NodeUtil.removeChild(parent, toRemove);
          numRemoved_++;
        }
      }
    }
  }
}
