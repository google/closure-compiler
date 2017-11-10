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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Garbage collection for variable and function definitions. Basically performs
 * a mark-and-sweep type algorithm over the JavaScript parse tree.
 *
 * For each scope:
 * (1) Scan the variable/function declarations at that scope.
 * (2) Traverse the scope for references, marking all referenced variables.
 *     Unlike other compiler passes, this is a pre-order traversal, not a
 *     post-order traversal.
 * (3) If the traversal encounters an assign without other side-effects,
 *     create a continuation. Continue the continuation iff the assigned
 *     variable is referenced.
 * (4) When the traversal completes, remove all unreferenced variables.
 *
 * If it makes it easier, you can think of the continuations of the traversal
 * as a reference graph. Each continuation represents a set of edges, where the
 * source node is a known variable, and the destination nodes are lazily
 * evaluated when the continuation is executed.
 *
 * This algorithm is similar to the algorithm used by {@code SmartNameRemoval}.
 * {@code SmartNameRemoval} maintains an explicit graph of dependencies
 * between global symbols. However, {@code SmartNameRemoval} cannot handle
 * non-trivial edges in the reference graph ("A is referenced iff both B and C
 * are referenced"), or local variables. {@code SmartNameRemoval} is also
 * substantially more complicated because it tries to handle namespaces
 * (which is largely unnecessary in the presence of {@code CollapseProperties}.
 *
 * This pass also uses a more complex analysis of assignments, where
 * an assignment to a variable or a property of that variable does not
 * necessarily count as a reference to that variable, unless we can prove
 * that it modifies external state. This is similar to
 * {@code FlowSensitiveInlineVariables}, except that it works for variables
 * used across scopes.
 *
 * Multiple datastructures are used to accumulate nodes, some of which are
 * later removed. Since some nodes encompass a subtree of nodes, the removal
 * can sometimes pre-remove other nodes which are also referenced in these
 * datastructures for later removal. Attempting double-removal violates scope
 * change notification constraints so there is a desire to excise
 * already-removed subtree nodes from these datastructures. But not all of the
 * datastructures are conducive to flexible removal and the ones that are
 * conducive don't necessarily track all flavors of nodes. So instead of
 * updating datastructures on the fly a pre-check is performed to skip
 * already-removed nodes right before the moment an attempt to remove them
 * would otherwise be made.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class RemoveUnusedVars implements CompilerPass {

  private final AbstractCompiler compiler;

  private final CodingConvention codingConvention;

  private final boolean removeGlobals;

  private final boolean preserveFunctionExpressionNames;

  /**
   * Keep track of variables that we've referenced.
   */
  private final Set<Var> referenced = new HashSet<>();

  /**
   * Keep track of variables that might be unreferenced.
   */
  private List<Var> maybeUnreferenced = new ArrayList<>();

  /**
   * Keep track of scopes that we've traversed.
   */
  private final List<Scope> allFunctionParamScopes = new ArrayList<>();

  /**
   * Keep track of assigns to variables that we haven't referenced.
   */
  private final Multimap<Var, Removable> assignsByVar =
      ArrayListMultimap.create();

  /**
   * The assigns, indexed by the NAME node that they assign to.
   */
  private final Set<Node> assignsByNode = new HashSet<>();

  /**
   * Subclass name -> class-defining call EXPR node. (like inherits)
   */
  private final Multimap<Var, Node> classDefiningCalls =
      ArrayListMultimap.create();

  /**
   * Keep track of continuations that are finished iff the variable they're
   * indexed by is referenced.
   */
  private final Multimap<Var, Continuation> continuations =
      ArrayListMultimap.create();

  private final ScopeCreator scopeCreator;

  RemoveUnusedVars(
      AbstractCompiler compiler,
      boolean removeGlobals,
      boolean preserveFunctionExpressionNames) {
    this.compiler = compiler;
    this.codingConvention = compiler.getCodingConvention();
    this.removeGlobals = removeGlobals;
    this.preserveFunctionExpressionNames = preserveFunctionExpressionNames;
    this.scopeCreator = new Es6SyntacticScopeCreator(compiler);
  }

  /**
   * Traverses the root, removing all unused variables. Multiple traversals
   * may occur to ensure all unused variables are removed.
   */
  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    traverseAndRemoveUnusedReferences(root);
  }

  /**
   * Traverses a node recursively. Call this once per pass.
   */
  private void traverseAndRemoveUnusedReferences(Node root) {
    Scope scope = scopeCreator.createScope(root, null);
    traverseNode(root, null, scope);

    if (removeGlobals) {
      collectMaybeUnreferencedVars(scope);
    }

    interpretAssigns();
    removeUnreferencedVars();
    for (Scope fparamScope : allFunctionParamScopes) {
      removeUnreferencedFunctionArgs(fparamScope);
    }
  }

  /**
   * Traverses everything in the current scope and marks variables that
   * are referenced.
   *
   * During traversal, we identify subtrees that will only be
   * referenced if their enclosing variables are referenced. Instead of
   * traversing those subtrees, we create a continuation for them,
   * and traverse them lazily.
   */
  private void traverseNode(Node n, Node parent, Scope scope) {
    Token type = n.getToken();
    Var var = null;
    switch (type) {
      case FUNCTION:
        // If this function is a removable var, then create a continuation
        // for it instead of traversing immediately.
        if (NodeUtil.isFunctionDeclaration(n)) {
          var = scope.getVar(n.getFirstChild().getString());
        }

        if (var != null && isRemovableVar(var)) {
          continuations.put(var, new Continuation(n, scope));
        } else {
          traverseFunction(n, scope);
        }
        return;

      case ASSIGN:
        Assign maybeAssign = Assign.maybeCreateAssign(n);
        if (maybeAssign != null) {
          // Put this in the assign map. It might count as a reference,
          // but we won't know that until we have an index of all assigns.
          var = scope.getVar(maybeAssign.nameNode.getString());
          if (var != null) {
            assignsByVar.put(var, maybeAssign);
            assignsByNode.add(maybeAssign.nameNode);

            if (isRemovableVar(var)
                && !maybeAssign.mayHaveSecondarySideEffects) {
              // If the var is unreferenced and performing this assign has
              // no secondary side effects, then we can create a continuation
              // for it instead of traversing immediately.
              continuations.put(var, new Continuation(n, scope));
              return;
            }
          }
        }
        break;

      case CALL:
        Var modifiedVar = null;

        // Look for calls to inheritance-defining calls (such as goog.inherits).
        SubclassRelationship subclassRelationship =
            codingConvention.getClassesDefinedByCall(n);
        if (subclassRelationship != null) {
          modifiedVar = scope.getVar(subclassRelationship.subclassName);
        } else {
          // Look for calls to addSingletonGetter calls.
          String className = codingConvention.getSingletonGetterClassName(n);
          if (className != null) {
            modifiedVar = scope.getVar(className);
          }
        }

        // Don't try to track the inheritance calls for non-globals. It would
        // be more correct to only not track when the subclass does not
        // reference a constructor, but checking that it is a global is
        // easier and mostly the same.
        if (modifiedVar != null && modifiedVar.isGlobal()
            && !referenced.contains(modifiedVar)) {
          // Save a reference to the EXPR node.
          classDefiningCalls.put(modifiedVar, parent);
          continuations.put(modifiedVar, new Continuation(n, scope));
          return;
        }
        break;

      // This case if for if there are let and const variables in block scopes.
      // Otherwise other variables will be hoisted up into the global scope and already be handled.
      case BLOCK:
        // check if we are already traversing that block node
        if (NodeUtil.createsBlockScope(n)) {
          Scope blockScope = scopeCreator.createScope(n, scope);
          collectMaybeUnreferencedVars(blockScope);
          scope = blockScope;
        }
        break;

      case CLASS:
        // If this class is a removable var, then create a continuation
        if (NodeUtil.isClassDeclaration(n)) {
          var = scope.getVar(n.getFirstChild().getString());
        }

        if (var != null && isRemovableVar(var)) {
          continuations.put(var, new Continuation(n, scope));
        }
        return;

      case DEFAULT_VALUE: {
        Node target = n.getFirstChild();
        if (target.isName()) {
          Node value = n.getLastChild();
          var = scope.getVar(target.getString());
          if (!NodeUtil.mayHaveSideEffects(value)) {
            continuations.put(var, new Continuation(n, scope));
            assignsByVar.put(var, new DestructuringAssign(n, target));
            return;
          } else {
            // TODO(johnlenz): we don't really need to retain all uses of the variable, just enough
            // to host the default value assignment.
            markReferencedVar(var);
          }
          assignsByNode.add(target);
        }
      }
      break;

      case REST: {
        Node target = n.getFirstChild();
        if (target.isName()) {
          assignsByNode.add(target);
          var = scope.getVar(target.getString());
          assignsByVar.put(var, new DestructuringAssign(n, target));
        }
      }
      break;

      case ARRAY_PATTERN:
        // Iterate in reverse order so we remove the last first, if possible
        for (Node c = n.getLastChild(); c != null; c = c.getPrevious()) {
          if (c.isName()) {
            assignsByNode.add(c);
            var = scope.getVar(c.getString());
            assignsByVar.put(var, new DestructuringAssign(c, c));
          }
        }
        break;

      case COMPUTED_PROP:
        if (n.getParent().isObjectPattern()) {
          // In a destructuring assignment, the target and the value name
          // are backward from a normal assignment (the rhs is the receiver).
          Node target = n.getLastChild();
          // If the computed properties calculation has side-effects, we have to leave it
          Node value = n.getFirstChild();
          if (!NodeUtil.mayHaveSideEffects(value)) {
            if (target.isName()) {
              var = scope.getVar(target.getString());
              assignsByNode.add(target);
              assignsByVar.put(var, new DestructuringAssign(n, target));
              return;
            }
          } else if (target.isDefaultValue() && target.getFirstChild().isName()) {
            // TODO(johnlenz): this is awkward, consider refactoring this.
            Node defaultTarget = target.getFirstChild();
            var = scope.getVar(defaultTarget.getString());
            markReferencedVar(var);
          }
        }
        break;

      case STRING_KEY:
        if (n.getParent().isObjectPattern()) {
          Node target = n.getLastChild();
          if (target.isName()) {
            var = scope.getVar(target.getString());
            assignsByNode.add(target);
            assignsByVar.put(var, new DestructuringAssign(n, target));
          }
        }
        break;

      case NAME:
        // the parameter declaration is not a read of the name, but we need to traverse
        // to find default values and destructuring assignments
        if (parent.isParamList()) {
          break;
        }

        var = scope.getVar(n.getString());
        if (NodeUtil.isNameDeclaration(parent)) {
          Node value = n.getFirstChild();
          if (value != null && var != null && isRemovableVar(var)
              && !NodeUtil.mayHaveSideEffects(value, compiler)) {
            // If the var is unreferenced and creating its value has no side
            // effects, then we can create a continuation for it instead
            // of traversing immediately.
            continuations.put(var, new Continuation(n, scope));
            return;
          }
        } else {
          // If arguments is escaped, we just assume the worst and continue
          // on all the parameters. Ignored if we are in block scope
          if (var != null
              && "arguments".equals(n.getString())
              && var.equals(scope.getArgumentsVar())) {
            Scope fnScope = var.getScope();
            Node paramList = NodeUtil.getFunctionParameters(fnScope.getRootNode());
            for (Node p : NodeUtil.findLhsNodesInNode(paramList)) {
              Var paramVar = fnScope.getOwnSlot(p.getString());
              checkNotNull(paramVar);
              markReferencedVar(paramVar);
            }
          }

          // All name references that aren't declarations or assigns
          // are references to other vars.
          if (var != null) {
            // If that var hasn't already been marked referenced, then
            // start tracking it.  If this is an assign, do nothing
            // for now.
            if (isRemovableVar(var)) {
              if (!assignsByNode.contains(n)) {
                markReferencedVar(var);
              }
            } else {
              markReferencedVar(var);
            }
          }
        }
        break;

      default:
        break;
    }

    traverseChildren(n, scope);
  }

  private void traverseChildren(Node n, Scope scope) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      traverseNode(c, n, scope);
    }
  }

  private boolean isRemovableVar(Var var) {
    // If this is a functions "arguments" object, it isn't removable
    if (var.equals(var.getScope().getArgumentsVar())) {
      return false;
    }

    // Global variables are off-limits if the user might be using them.
    if (!removeGlobals && var.isGlobal()) {
      return false;
    }
    // Variables declared in for in and for of loops are off limits
    if (var.getParentNode() != null && NodeUtil.isEnhancedFor(var.getParentNode().getParent())) {
      return false;
    }
    // Referenced variables are off-limits.
    if (referenced.contains(var)) {
      return false;
    }

    // Exported variables are off-limits.
    return !codingConvention.isExported(var.getName());
  }

  /**
   * Traverses a function
   *
   * ES6 scopes of a function include the parameter scope and the body scope
   * of the function.
   *
   * Note that CATCH blocks also create a new scope, but only for the
   * catch variable. Declarations within the block actually belong to the
   * enclosing scope. Because we don't remove catch variables, there's
   * no need to treat CATCH blocks differently like we do functions.
   */
  private void traverseFunction(Node function, Scope parentScope) {
    checkState(function.getChildCount() == 3, function);
    checkState(function.isFunction(), function);

    final Node paramlist = NodeUtil.getFunctionParameters(function);
    final Node body = function.getLastChild();
    checkState(body.getNext() == null && body.isNormalBlock(), body);

    // Checking the parameters
    Scope fparamScope = scopeCreator.createScope(function, parentScope);
    collectMaybeUnreferencedVars(fparamScope);

    // Checking the function body
    Scope fbodyScope = scopeCreator.createScope(body, fparamScope);
    collectMaybeUnreferencedVars(fbodyScope);

    traverseChildren(paramlist, fparamScope);
    traverseChildren(body, fbodyScope);

    allFunctionParamScopes.add(fparamScope);
  }

  /**
   * For each variable in this scope that we haven't found a reference
   * for yet, add it to the list of variables to check later.
   */
  private void collectMaybeUnreferencedVars(Scope scope) {
    for (Var var : scope.getVarIterable()) {
      if (isRemovableVar(var)) {
        maybeUnreferenced.add(var);
      }
    }
  }

  private boolean canRemoveParameters(Node parameterList) {
    checkState(parameterList.isParamList());
    Node function = parameterList.getParent();
    return removeGlobals && !NodeUtil.isGetOrSetKey(function.getParent());
  }

  /**
   * Removes unreferenced arguments from a function declaration and when
   * possible the function's callSites.
   *
   * @param fparamScope The function parameter
   */
  private void removeUnreferencedFunctionArgs(Scope fparamScope) {
    // Notice that removing unreferenced function args breaks
    // Function.prototype.length. In advanced mode, we don't really care
    // about this: we consider "length" the equivalent of reflecting on
    // the function's lexical source.
    //
    // Rather than create a new option for this, we assume that if the user
    // is removing globals, then it's OK to remove unused function args.
    //
    // See http://blickly.github.io/closure-compiler-issues/#253
    if (!removeGlobals) {
      return;
    }

    Node function = fparamScope.getRootNode();
    checkState(function.isFunction());
    if (NodeUtil.isGetOrSetKey(function.getParent())) {
      // The parameters object literal setters can not be removed.
      return;
    }

    Node argList = NodeUtil.getFunctionParameters(function);
    // Strip as many unreferenced args off the end of the function declaration as possible.
    maybeRemoveUnusedTrailingParameters(argList, fparamScope);

    // Mark any remaining unused parameters are unused to OptimizeParameters can try to remove
    // them.
    markUnusedParameters(argList, fparamScope);
  }

  /**
   * Mark any remaining unused parameters as being unused so it can be used elsewhere.
   *
   * @param paramList list of function's parameters
   * @param fparamScope
   */
  private void markUnusedParameters(Node paramList, Scope fparamScope) {
    for (Node param = paramList.getFirstChild(); param != null; param = param.getNext()) {
      if (!param.isUnusedParameter()) {
        Node lValue = param;
        if (lValue.isDefaultValue()) {
          lValue = lValue.getFirstChild();
        }
        if (lValue.isRest()) {
          lValue = lValue.getFirstChild();
        }
        if (lValue.isDestructuringPattern()) {
          continue;
        }
        Var var = fparamScope.getVar(lValue.getString());
        if (!referenced.contains(var)) {
          param.setUnusedParameter(true);
          compiler.reportChangeToEnclosingScope(paramList);
        }
      }
    }
  }

  /**
   * Strip as many unreferenced args off the end of the function declaration as possible. We start
   * from the end of the function declaration because removing parameters from the middle of the
   * param list could mess up the interpretation of parameters being sent over by any function
   * calls.
   *
   * @param argList list of function's arguments
   * @param fparamScope
   */
  private void maybeRemoveUnusedTrailingParameters(Node argList, Scope fparamScope) {
    Node lastArg;
    while ((lastArg = argList.getLastChild()) != null) {
      Node lValue = lastArg;
      if (lastArg.isDefaultValue()) {
        lValue = lastArg.getFirstChild();
        if (NodeUtil.mayHaveSideEffects(lastArg.getLastChild())) {
          break;
        }
      }

      if (lValue.isRest()) {
        lValue = lValue.getFirstChild();
      }

      if (lValue.isDestructuringPattern()) {
        if (lValue.hasChildren()) {
          // TODO(johnlenz): handle the case where there are no assignments.
          break;
        } else {
          // Remove empty destructuring patterns and their associated object literal assignment
          // if it exists and if the right hand side does not have side effects. Note, a
          // destructuring pattern with a "leftover" property key as in {a:{}} is not considered
          // empty in this case!
          NodeUtil.deleteNode(lastArg, compiler);
          continue;
        }
      }

      Var var = fparamScope.getVar(lValue.getString());
      if (!referenced.contains(var)) {
        NodeUtil.deleteNode(lastArg, compiler);
      } else {
        break;
      }
    }
  }

  /**
   * Look at all the property assigns to all variables.
   * These may or may not count as references. For example,
   *
   * <code>
   * var x = {};
   * x.foo = 3; // not a reference.
   * var y = foo();
   * y.foo = 3; // is a reference.
   * </code>
   *
   * Interpreting assignments could mark a variable as referenced that
   * wasn't referenced before, in order to keep it alive. Because we find
   * references by lazily traversing subtrees, marking a variable as
   * referenced could trigger new traversals of new subtrees, which could
   * find new references.
   *
   * Therefore, this interpretation needs to be run to a fixed point.
   */
  private void interpretAssigns() {
    boolean changes = false;
    do {
      changes = false;

      // We can't use traditional iterators and iterables for this list,
      // because our lazily-evaluated continuations will modify it while
      // we traverse it.
      int removedCount = 0;
      for (int current = 0; current < maybeUnreferenced.size(); current++) {
        Var var = maybeUnreferenced.get(current);
        if (var == null) {
          continue;
        }
        if (referenced.contains(var)) {
          maybeUnreferenced.set(current, null);
          removedCount++;
        } else {
          boolean assignedToUnknownValue = false;

          if (NodeUtil.isNameDeclaration(var.getParentNode())
              && !var.getParentNode().getParent().isForIn()) {
            Node value = var.getInitialValue();
            assignedToUnknownValue = value != null
                && !NodeUtil.isLiteralValue(value, true);
          } else if (NodeUtil.isFunctionDeclaration(var.getParentNode())) {
            assignedToUnknownValue = false;
          } else {
            // This was initialized to a function arg or a catch param
            // or a for...in variable.
            assignedToUnknownValue = true;
          }

          boolean maybeEscaped = false;
          boolean hasPropertyAssign = false;
          for (Removable removable : assignsByVar.get(var)) {
            if (removable instanceof DestructuringAssign) {
              assignedToUnknownValue = true;
              continue;
            }
            Assign assign = (Assign) removable;
            if (assign.isPropertyAssign) {
              hasPropertyAssign = true;
            } else if (!NodeUtil.isLiteralValue(
                assign.assignNode.getLastChild(), true)) {
              assignedToUnknownValue = true;
            }
            if (assign.maybeAliased) {
              maybeEscaped = true;
            }
          }

          if ((assignedToUnknownValue || maybeEscaped) && hasPropertyAssign) {
            changes = markReferencedVar(var) || changes;
            maybeUnreferenced.set(current, null);
            removedCount++;
          }
        }
      }

      // Removing unused items from the middle of an array list is relatively expensive,
      // so we batch them up and remove them all at the end.
      if (removedCount > 0) {
        int size = maybeUnreferenced.size();
        ArrayList<Var> refreshed = new ArrayList<>(size - removedCount);
        for (int i = 0; i < size; i++) {
          Var var = maybeUnreferenced.get(i);
          if (var != null) {
            refreshed.add(var);
          }
        }
        maybeUnreferenced = refreshed;
      }
    } while (changes);
  }

  /**
   * Remove all assigns to a var.
   */
  private void removeAllAssigns(Var var) {
    for (Removable removable : assignsByVar.get(var)) {
      removable.remove(compiler);
    }
  }

  /**
   * Marks a var as referenced, recursing into any values of this var
   * that we skipped.
   * @return True if this variable had not been referenced before.
   */
  private boolean markReferencedVar(Var var) {
    if (referenced.add(var)) {
      for (Continuation c : continuations.get(var)) {
        c.apply();
      }
      return true;
    }
    return false;
  }

  /**
   * Removes any vars in the scope that were not referenced. Removes any
   * assignments to those variables as well.
   */
  private void removeUnreferencedVars() {
    for (Var var : maybeUnreferenced) {
      // Remove calls to inheritance-defining functions where the unreferenced
      // class is the subclass.
      for (Node exprCallNode : classDefiningCalls.get(var)) {
        compiler.reportChangeToEnclosingScope(exprCallNode);
        NodeUtil.removeChild(exprCallNode.getParent(), exprCallNode);
      }

      // Regardless of what happens to the original declaration,
      // we need to remove all assigns, because they may contain references
      // to other unreferenced variables.
      removeAllAssigns(var);

      compiler.addToDebugLog("Unreferenced var: ", var.name);
      Node nameNode = var.nameNode;
      Node toRemove = nameNode.getParent();
      if (toRemove == null) {
        // array pattern assignments may have already been removed
        continue;
      }

      Node parent = toRemove != null ? toRemove.getParent() : null;
      Node grandParent = parent != null ? parent.getParent() : null;

      if (toRemove.isDefaultValue() || toRemove.isRest()) {
        // Rest and default value declarations should already have been removed.
        checkState(parent == null || grandParent == null);
      } else if (toRemove.isStringKey() || toRemove.isComputedProp()) {
        checkState(parent == null, "unremoved destructuring ", toRemove);
      } else if (toRemove.isParamList()) {
        // Don't remove function arguments here. That's a special case
        // that's taken care of in removeUnreferencedFunctionArgs.
      } else if (toRemove.isComputedProp()) {
        // Don't remove a computed property
      } else if (NodeUtil.isFunctionExpression(toRemove)) {
        if (!preserveFunctionExpressionNames) {
          Node fnNameNode = toRemove.getFirstChild();
          compiler.reportChangeToEnclosingScope(fnNameNode);
          fnNameNode.setString("");
        }
        // Don't remove bleeding functions.
      } else if (toRemove.isArrayPattern() && grandParent.isParamList()) {
        compiler.reportChangeToEnclosingScope(toRemove);
        NodeUtil.removeChild(toRemove, nameNode);
      } else if (parent.isForIn()) {
        // foreach iterations have 3 children. Leave them alone.
      } else if (parent.isDestructuringPattern()) {
        compiler.reportChangeToEnclosingScope(toRemove);
        NodeUtil.removeChild(parent, toRemove);
      } else if (parent.isDestructuringLhs()) {
        compiler.reportChangeToEnclosingScope(nameNode);
        NodeUtil.removeChild(toRemove, nameNode);
      } else if (NodeUtil.isNameDeclaration(toRemove)
          && nameNode.hasChildren()
          && NodeUtil.mayHaveSideEffects(nameNode.getFirstChild(), compiler)) {
        // If this is a single var declaration, we can at least remove the
        // declaration itself and just leave the value, e.g.,
        // var a = foo(); => foo();
        if (toRemove.hasOneChild()) {
          compiler.reportChangeToEnclosingScope(toRemove);
          parent.replaceChild(toRemove,
              IR.exprResult(nameNode.removeFirstChild()));
        }
      } else if (NodeUtil.isNameDeclaration(toRemove) && toRemove.hasMoreThanOneChild()) {
        // For var declarations with multiple names (i.e. var a, b, c),
        // only remove the unreferenced name
        compiler.reportChangeToEnclosingScope(toRemove);
        toRemove.removeChild(nameNode);
      } else if (parent != null) {
        compiler.reportChangeToEnclosingScope(toRemove);
        NodeUtil.removeChild(parent, toRemove);
        NodeUtil.markFunctionsDeleted(toRemove, compiler);
      }
    }
  }

  /**
   * Our progress in a traversal can be expressed completely as the
   * current node and scope. The continuation lets us save that
   * information so that we can continue the traversal later.
   */
  private class Continuation {
    private final Node node;
    private final Scope scope;

    Continuation(Node node, Scope scope) {
      this.node = node;
      this.scope = scope;
    }

    void apply() {
      if (NodeUtil.isFunctionDeclaration(node)) {
        traverseFunction(node, scope);
      } else {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
          traverseNode(child, node, scope);
        }
      }
    }
  }

  private static interface Removable {
    public void remove(AbstractCompiler compiler);
  }

  private class DestructuringAssign implements Removable {
    final Node removableNode;
    final Node nameNode;

    DestructuringAssign(Node removableNode, Node nameNode) {
      checkState(nameNode.isName());
      this.removableNode = removableNode;
      this.nameNode = nameNode;

      Node parent = nameNode.getParent();
      if (parent.isDefaultValue()) {
        checkState(!NodeUtil.mayHaveSideEffects(parent.getLastChild()));
      }
    }

    @Override
    public void remove(AbstractCompiler compiler) {
      Node removableParent = removableNode.getParent();
      if (removableParent.isArrayPattern()) {
        // [a, removableName, b] = something;
        // [a, ...removableName] = something;
        // [a, removableName = removableValue, b] = something;
        // [a, ...removableName = removableValue] = something;
        compiler.reportChangeToEnclosingScope(removableParent);
        if (removableNode == removableParent.getLastChild()) {
          removableNode.detach();
        } else {
          removableNode.replaceWith(IR.empty().srcref(removableNode));
        }
        // We prefer `[a, b]` to `[a, b, , , , ]`
        // So remove any trailing empty nodes.
        for (Node maybeEmpty = removableParent.getLastChild();
            maybeEmpty != null && maybeEmpty.isEmpty();
            maybeEmpty = removableParent.getLastChild()) {
          maybeEmpty.detach();
        }
        NodeUtil.markFunctionsDeleted(removableNode, compiler);
      } else if (removableParent.isParamList() && removableNode.isDefaultValue()) {
        // function(removableName = removableValue)
        compiler.reportChangeToEnclosingScope(removableNode);
        // preserve the slot in the parameter list
        Node name = removableNode.getFirstChild();
        checkState(name.isName());
        if (removableNode == removableParent.getLastChild()
            && removeGlobals
            && canRemoveParameters(removableParent)) {
          // function(p1, removableName = removableDefault)
          // and we're allowed to remove the parameter entirely
          removableNode.detach();
        } else {
          // function(removableName = removableDefault, otherParam)
          // or removableName is at the end, but cannot be completely removed.
          removableNode.replaceWith(name.detach());
        }
        NodeUtil.markFunctionsDeleted(removableNode, compiler);
      } else if (removableNode.isDefaultValue()) {
        // { a: removableName = removableValue }
        // { [removableExpression]: removableName = removableValue }
        checkState(
            removableParent.isStringKey()
                || (removableParent.isComputedProp()
                    && !NodeUtil.mayHaveSideEffects(removableParent.getFirstChild())));
        // Remove the whole property, not just its default value part.
        NodeUtil.deleteNode(removableParent, compiler);
      } else {
        // { removableStringKey: removableName }
        // function(...removableName) {}
        // function(...removableName = default)
        checkState(
            removableParent.isObjectPattern()
                || (removableParent.isParamList() && removableNode.isRest()));
        NodeUtil.deleteNode(removableNode, compiler);
      }
    }
  }

  private static class Assign implements Removable {

    final Node assignNode;

    final Node nameNode;

    // If false, then this is an assign to the normal variable. Otherwise,
    // this is an assign to a property of that variable.
    final boolean isPropertyAssign;

    // Secondary side effects are any side effects in this assign statement
    // that aren't caused by the assignment operation itself. For example,
    // a().b = 3;
    // a = b();
    // var foo = (a = b);
    // In the first two cases, the sides of the assignment have side-effects.
    // In the last one, the result of the assignment is used, so we
    // are conservative and assume that it may be used in a side-effecting
    // way.
    final boolean mayHaveSecondarySideEffects;

    // If true, the value may have escaped and any modification is a use.
    final boolean maybeAliased;

    Assign(Node assignNode, Node nameNode, boolean isPropertyAssign) {
      checkState(NodeUtil.isAssignmentOp(assignNode));
      this.assignNode = assignNode;
      this.nameNode = nameNode;
      this.isPropertyAssign = isPropertyAssign;

      this.maybeAliased = NodeUtil.isExpressionResultUsed(assignNode);
      this.mayHaveSecondarySideEffects =
          maybeAliased
              || NodeUtil.mayHaveSideEffects(assignNode.getFirstChild())
              || NodeUtil.mayHaveSideEffects(assignNode.getLastChild());
    }

    /**
     * If this is an assign to a variable or its property, return it.
     * Otherwise, return null.
     */
    static Assign maybeCreateAssign(Node assignNode) {
      checkState(NodeUtil.isAssignmentOp(assignNode));

      // Skip one level of GETPROPs or GETELEMs.
      //
      // Don't skip more than one level, because then we get into
      // situations where assigns to properties of properties will always
      // trigger side-effects, and the variable they're on cannot be removed.
      boolean isPropAssign = false;
      Node current = assignNode.getFirstChild();
      if (NodeUtil.isGet(current)) {
        current = current.getFirstChild();
        isPropAssign = true;

        if (current.isGetProp()
            && current.getLastChild().getString().equals("prototype")) {
          // Prototype properties sets should be considered like normal
          // property sets.
          current = current.getFirstChild();
        }
      }

      if (current.isName()) {
        return new Assign(assignNode, current, isPropAssign);
      }
      return null;
    }

    /** Replace the current assign with its right hand side. */
    @Override
    public void remove(AbstractCompiler compiler) {
      compiler.reportChangeToEnclosingScope(assignNode);
      if (mayHaveSecondarySideEffects) {
        Node replacement = assignNode.getLastChild().detach();

        // Aggregate any expressions in GETELEMs.
        for (Node current = assignNode.getFirstChild();
            !current.isName();
            current = current.getFirstChild()) {
          if (current.isGetElem()) {
            replacement = IR.comma(
                current.getLastChild().detach(), replacement);
            replacement.useSourceInfoIfMissingFrom(current);
          }
        }

        assignNode.replaceWith(replacement);
      } else {
        Node parent = assignNode.getParent();
        if (parent.isExprResult()) {
          parent.detach();
          NodeUtil.markFunctionsDeleted(parent, compiler);
        } else {
          // mayHaveSecondarySideEffects is false, which means the value isn't needed,
          // but we need to keep the AST valid.
          assignNode.replaceWith(IR.number(0).srcref(assignNode));
        }
      }
    }
  }

  private static boolean alreadyRemoved(Node n) {
    Node parent = n.getParent();
    if (parent == null) {
      return true;
    }
    if (parent.isRoot()) {
      return false;
    }
    return alreadyRemoved(parent);
  }
}
