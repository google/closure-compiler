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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
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
  private static final Logger logger =
    Logger.getLogger(RemoveUnusedVars.class.getName());

  private final AbstractCompiler compiler;

  private final boolean removeGlobals;

  private boolean preserveFunctionExpressionNames;

  /**
   * Keep track of variables that we've referenced.
   */
  private final Set<Var> referenced = Sets.newHashSet();

  /**
   * Keep track of assigns to variables that we haven't referenced.
   */
  private final Multimap<Var, Assign> assigns = ArrayListMultimap.create();

  RemoveUnusedVars(
      AbstractCompiler compiler,
      boolean removeGlobals,
      boolean preserveFunctionExpressionNames) {
    this.compiler = compiler;
    this.removeGlobals = removeGlobals;
    this.preserveFunctionExpressionNames = preserveFunctionExpressionNames;
  }

  /**
   * Traverses the root, removing all unused variables. Multiple traversals
   * may occur to ensure all unused variables are removed.
   */
  public void process(Node externs, Node root) {
    traverseAndRemoveUnusedReferences(root);
  }

  /**
   * Traverses a node recursively. Call this once per pass.
   */
  private void traverseAndRemoveUnusedReferences(Node root) {
    Scope scope = new SyntacticScopeCreator(compiler).createScope(root, null);
    traverseNode(root, null, scope);

    if (removeGlobals) {
      interpretAssigns(scope);
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
        // If it's an exported function, or an function expression, assume
        // that it'll be called.
        if (traverseFunctionWhenFirstSeen(n, scope)) {
          traverseFunction(n, scope);
        }
        return;

      case Token.NAME:
        if (parent.getType() != Token.VAR) {
          // All name references that aren't declarations or assigns
          // are references to other vars. If that var hasn't already been
          // marked referenced, then start tracking it.
          Var var = scope.getVar(n.getString());
          if (var != null && !referenced.contains(var)) {
            Assign maybeAssign = Assign.maybeCreateAssign(n);
            if (maybeAssign == null) {
              markReferencedVar(var);
            } else {
              // Put this in the assign map. It might count as a reference,
              // but we won't know that until we have an index of all assigns.
              assigns.put(var, maybeAssign);
            }
          }
        }
        break;
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      traverseNode(c, n, scope);
    }
  }

  /**
   * @param n The function node.
   * @return Whether to traverse the function immediately.
   */
  private boolean traverseFunctionWhenFirstSeen(Node n, Scope scope) {
    return NodeUtil.isFunctionExpression(n) || isExportedFunction(n, scope);
  }

  /**
   * @param n The function node.
   * @return Whether the function is exported.
   */
  private boolean isExportedFunction(Node n, Scope scope) {
    Preconditions.checkState(NodeUtil.isFunctionDeclaration(n));
    // If we aren't removing global names, assume that all global functions
    // are exported.
    return (!removeGlobals && scope.isGlobal()) ||
        compiler.getCodingConvention().isExported(
           n.getFirstChild().getString());
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

    Scope fnScope = new SyntacticScopeCreator(compiler).createScope(n, scope);
    traverseNode(body, n, fnScope);

    interpretAssigns(fnScope);
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
        if (var == null) {
          throw new IllegalStateException(
              "Function parameter not declared in scope: "
              + lastArg.getString());
        }
        argList.removeChild(lastArg);
        fnScope.undeclare(var);
        finishRemove(var);
      } else {
        break;
      }
    }
  }

  /**
   * Look at all the property assigns to all variables in the given
   * scope. These may or may not count as references. For example,
   *
   * <code>
   * var x = {};
   * x.foo = 3; // not a reference.
   * var y = foo();
   * y.foo = 3; // is a reference.
   * </code>
   */
  private void interpretAssigns(Scope scope) {
    for (Iterator<Var> it = scope.getVars(); it.hasNext(); ) {
      Var var = it.next();
      if (!referenced.contains(var)) {
        boolean assignedToUnknownValue = false;
        boolean hasPropertyAssign = false;

        if (var.getParentNode().getType() == Token.VAR) {
          Node value = var.getInitialValue();
          assignedToUnknownValue = value != null &&
              !NodeUtil.isLiteralValue(value);
        } else {
          // This was initialized to a function arg or a catch param.
          assignedToUnknownValue = true;
        }

        for (Assign assign : assigns.get(var)) {
          if (assign.isPropertyAssign) {
            hasPropertyAssign = true;
          } else if (!NodeUtil.isLiteralValue(
              assign.assignNode.getLastChild())) {
            assignedToUnknownValue = true;
          }
        }

        if (assignedToUnknownValue && hasPropertyAssign) {
          markReferencedVar(var);
        }
      }
    }
  }


  /**
   * Finishes removal of a var by removing all assigns to it and reporting
   * a code change.
   */
  private void finishRemove(Var var) {
    for (Assign assign : assigns.get(var)) {
      assign.remove();
    }
    compiler.reportCodeChange();
  }

  /**
   * Marks a var as referenced, recursing into any functions.
   */
  private void markReferencedVar(Var var) {
    referenced.add(var);

    Node parent = var.getParentNode();
    if (parent.getType() == Token.FUNCTION) {
      // Now that the function has been referenced traverse it if it won't be
      // traversed otherwise.
      if (!traverseFunctionWhenFirstSeen(parent, var.getScope())) {
        traverseFunction(parent, var.scope);
      }
    }
  }

  /**
   * Removes any vars in the scope that were not referenced.
   */
  private void removeUnreferencedVars(Scope scope) {
    CodingConvention convention = compiler.getCodingConvention();

    for (Iterator<Var> it = scope.getVars(); it.hasNext(); ) {
      Var var = it.next();

      if (!referenced.contains(var) &&
          (var.isLocal() || !convention.isExported(var.name))) {

        compiler.addToDebugLog("Unreferenced var: " + var.name);
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
        } else if (NodeUtil.isFunctionExpression(toRemove)) {
          if (!preserveFunctionExpressionNames) {
            toRemove.getFirstChild().setString("");
            finishRemove(var);
          }
          // Don't remove bleeding functions.
        } else if (parent != null &&
            parent.getType() == Token.FOR &&
            parent.getChildCount() < 4) {
          // foreach iterations have 3 children. Leave them alone.
        } else if (toRemove.getType() == Token.VAR &&
                   nameNode.hasChildren() &&
                   NodeUtil.mayHaveSideEffects(nameNode.getFirstChild())) {
          // If this is a single var declaration, we can at least remove the
          // declaration itself and just leave the value, e.g.,
          // var a = foo(); => foo();
          if (toRemove.getChildCount() == 1) {
            parent.replaceChild(toRemove,
                new Node(Token.EXPR_RESULT, nameNode.removeFirstChild()));
            finishRemove(var);
          }
        } else if (toRemove.getType() == Token.VAR &&
                   toRemove.getChildCount() > 1) {
          // For var declarations with multiple names (i.e. var a, b, c),
          // only remove the unreferenced name
          toRemove.removeChild(nameNode);
          finishRemove(var);
        } else if (parent != null) {
          NodeUtil.removeChild(parent, toRemove);
          finishRemove(var);
        }
      }
    }
  }

  private static class Assign {

    final Node assignNode;

    // If false, then this is an assign to the normal variable. Otherwise,
    // this is an assign to a property of that variable.
    final boolean isPropertyAssign;

    Assign(Node assignNode, boolean isPropertyAssign) {
      Preconditions.checkState(NodeUtil.isAssignmentOp(assignNode));
      this.assignNode = assignNode;
      this.isPropertyAssign = isPropertyAssign;
    }

    /**
     * If this is an assign to the given name, return that name.
     * Otherwise, return null.
     */
    static Assign maybeCreateAssign(Node name) {
      Preconditions.checkState(name.getType() == Token.NAME);

      // Skip any GETPROPs or GETELEMs
      boolean isPropAssign = false;
      Node previous = name;
      Node current = name.getParent();
      while (previous == current.getFirstChild() &&
          NodeUtil.isGet(current)) {
        previous = current;
        current = current.getParent();
        isPropAssign = true;
      }

      if (previous == current.getFirstChild() &&
          NodeUtil.isAssignmentOp(current)) {
        return new Assign(current, isPropAssign);
      }
      return null;
    }

    /**
     * Replace the current assign with its right hand side.
     */
    void remove() {
      Node replacement = assignNode.getLastChild().detachFromParent();

      // Aggregate any expressions in GETELEMs.
      for (Node current = assignNode.getFirstChild();
           current.getType() != Token.NAME;
           current = current.getFirstChild()) {
        if (current.getType() == Token.GETELEM) {
          replacement = new Node(Token.COMMA,
              current.getLastChild().detachFromParent(), replacement);
          replacement.copyInformationFrom(current);
        }
      }

      assignNode.getParent().replaceChild(
          assignNode, replacement);
    }
  }
}
