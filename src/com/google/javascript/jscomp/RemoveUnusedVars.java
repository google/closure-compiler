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
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
class RemoveUnusedVars implements CompilerPass, OptimizeCalls.CallGraphCompilerPass {

  private final AbstractCompiler compiler;

  private final CodingConvention codingConvention;

  private final boolean removeGlobals;

  private boolean preserveFunctionExpressionNames;

  /**
   * Keep track of variables that we've referenced.
   */
  private final Set<Var> referenced = new HashSet<>();

  /**
   * Keep track of variables that might be unreferenced.
   */
  private final List<Var> maybeUnreferenced = new ArrayList<>();

  /**
   * Keep track of scopes that we've traversed.
   */
  private final List<Scope> allFunctionParamScopes = new ArrayList<>();

  /**
   * Keep track of assigns to variables that we haven't referenced.
   */
  private final Multimap<Var, Assign> assignsByVar =
      ArrayListMultimap.create();

  /**
   * The assigns, indexed by the NAME node that they assign to.
   */
  private final Map<Node, Assign> assignsByNode = new HashMap<>();

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

  private boolean modifyCallSites;

  private CallSiteOptimizer callSiteOptimizer;

  private final ScopeCreator scopeCreator;

  RemoveUnusedVars(
      AbstractCompiler compiler,
      boolean removeGlobals,
      boolean preserveFunctionExpressionNames,
      boolean modifyCallSites) {
    this.compiler = compiler;
    this.codingConvention = compiler.getCodingConvention();
    this.removeGlobals = removeGlobals;
    this.preserveFunctionExpressionNames = preserveFunctionExpressionNames;
    this.modifyCallSites = modifyCallSites;
    this.scopeCreator = new Es6SyntacticScopeCreator(compiler);
  }

  /**
   * Traverses the root, removing all unused variables. Multiple traversals
   * may occur to ensure all unused variables are removed.
   */
  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    boolean shouldResetModifyCallSites = false;
    if (this.modifyCallSites) {
      // When RemoveUnusedVars is run after OptimizeCalls, this.modifyCallSites
      // is true. But if OptimizeCalls stops making changes, PhaseOptimizer
      // stops running it, so we come to RemoveUnusedVars and the defFinder is
      // null. In this case, we temporarily set this.modifyCallSites to false
      // for this run, and then reset it back to true at the end, for
      // subsequent runs.
      if (compiler.getDefinitionFinder() == null) {
        this.modifyCallSites = false;
        shouldResetModifyCallSites = true;
      }
    }
    process(externs, root, compiler.getDefinitionFinder());
    // When doing OptimizeCalls, RemoveUnusedVars is the last pass in the
    // sequence, so the def finder must not be used by any subsequent passes.
    compiler.setDefinitionFinder(null);
    if (shouldResetModifyCallSites) {
      this.modifyCallSites = true;
    }
  }

  @Override
  public void process(
      Node externs, Node root, DefinitionUseSiteFinder defFinder) {
    if (modifyCallSites) {
      checkNotNull(defFinder);
      callSiteOptimizer = new CallSiteOptimizer(compiler, defFinder);
    }
    traverseAndRemoveUnusedReferences(root);
    if (callSiteOptimizer != null) {
      callSiteOptimizer.applyChanges();
    }
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
            assignsByNode.put(maybeAssign.nameNode, maybeAssign);

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

      case ARRAY_PATTERN:
        // VAR or LET or CONST
        //  DESTRUCTURING_LHS
        //    ARRAY_PATTERN
        //      NAME

        // back off if there are nested array patterns
        if (n.getParent().isDestructuringLhs()) {
          if (NodeUtil.isNestedArrayPattern(n)) {
            break;
          } else {
            return;
          }
        }
        break;

      case OBJECT_PATTERN:
        // VAR or LET or CONST
        //  DESTRUCTURING_LHS
        //    OBJECT_PATTERN
        //      STRING
        //        NAME

        // back off if there are nested object patterns
        if (n.getParent().isDestructuringLhs()) {
          if (NodeUtil.isNestedObjectPattern(n)) {
            break;
          } else {
            return;
          }
        }
        break;

      case NAME:
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
            for (Node p : NodeUtil.getLhsNodesOfDeclaration(paramList)) {
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
              if (!assignsByNode.containsKey(n)) {
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

    final Node body = function.getLastChild();
    checkState(body.getNext() == null && body.isNormalBlock(), body);

    // Checking the parameters
    Scope fparamScope = scopeCreator.createScope(function, parentScope);

    // Checking the function body
    Scope fbodyScope = scopeCreator.createScope(body, fparamScope);
    traverseChildren(body, fbodyScope);

    collectMaybeUnreferencedVars(fparamScope);
    collectMaybeUnreferencedVars(fbodyScope);
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
    boolean modifyCallers = modifyCallSites
        && callSiteOptimizer.canModifyCallers(function);
    if (!modifyCallers) {

      // Remove any unused names from destructuring patterns as long as there are no side effects
      removeUnusedDestructuringNames(argList, fparamScope);

      // Strip as many unreferenced args off the end of the function declaration as possible.
      maybeRemoveUnusedTrailingParameters(argList, fparamScope);
    } else {
      callSiteOptimizer.optimize(fparamScope, referenced);
    }
  }

  /**
   * Iterate through the parameters of the function and if they are destructuring parameters, remove
   * any unreferenced variables from inside the destructuring pattern.
   */
  private void removeUnusedDestructuringNames(Node argList, Scope fparamScope) {
    List<Node> destructuringDeclarations = NodeUtil.getLhsNodesOfDeclaration(argList);
    for (Node patternElt : Lists.reverse(destructuringDeclarations)) {
      Node toRemove = patternElt;
      if (patternElt.getParent().isDefaultValue()) {
        Node defaultValueRhs = patternElt.getNext();
        if (NodeUtil.mayHaveSideEffects(defaultValueRhs)) {
          // Protects in the case where function f({a:b = alert('bar')} = alert('foo')){};
          continue;
        }
        toRemove = patternElt.getParent();
      }

      if (toRemove.getParent().isParamList()) {
        continue;
      }

      // Go through all elements of the object pattern and determine whether they should be
      // removed
      Var var = fparamScope.getVar(patternElt.getString());
      if (!referenced.contains(var)) {
        if (toRemove.getParent().isStringKey()) {
          toRemove = toRemove.getParent();
        }
        NodeUtil.markFunctionsDeleted(toRemove, compiler);
        compiler.reportChangeToEnclosingScope(toRemove.getParent());
        NodeUtil.removeChild(toRemove.getParent(), toRemove);
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
        Node defaultValueSecondChild = lValue.getNext();
        if (NodeUtil.mayHaveSideEffects(defaultValueSecondChild)) {
          break;
        }
      }

      if (lValue.isRest()) {
          lValue = lValue.getFirstChild();
      }

      if (lValue.isDestructuringPattern()) {
        if (lValue.hasChildren()) {
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

  private static class CallSiteOptimizer {
    private final AbstractCompiler compiler;
    private final DefinitionUseSiteFinder defFinder;
    private final List<Node> toRemove = new ArrayList<>();
    private final List<Node> toReplaceWithZero = new ArrayList<>();

    CallSiteOptimizer(
        AbstractCompiler compiler,
        DefinitionUseSiteFinder defFinder) {
      this.compiler = compiler;
      this.defFinder = defFinder;
    }

    public void optimize(Scope fparamScope, Set<Var> referenced) {
      Node function = fparamScope.getRootNode();
      checkState(function.isFunction());
      Node argList = NodeUtil.getFunctionParameters(function);

      // In this path we try to modify all the call sites to remove unused
      // function parameters.
      boolean changeCallSignature = canChangeSignature(function);
      markUnreferencedFunctionArgs(
          fparamScope, function, referenced,
          argList.getFirstChild(), 0, changeCallSignature);
    }

    /**
     * Applies optimizations to all previously marked nodes.
     */
    public void applyChanges() {
      for (Node n : toRemove) {
        // Don't remove any nodes twice since doing so would violate change reporting constraints.
        if (alreadyRemoved(n)) {
          continue;
        }

        compiler.reportChangeToEnclosingScope(n);
        n.detach();
        NodeUtil.markFunctionsDeleted(n, compiler);
      }
      for (Node n : toReplaceWithZero) {
        // Don't remove any nodes twice since doing so would violate change reporting constraints.
        if (alreadyRemoved(n)) {
          continue;
        }

        compiler.reportChangeToEnclosingScope(n);
        n.replaceWith(IR.number(0).srcref(n));
        NodeUtil.markFunctionsDeleted(n, compiler);
      }
    }

    /**
     * For each unused function parameter, determine if it can be removed
     * from all the call sites, if so, remove it from the function signature
     * and the call sites otherwise replace the unused value where possible
     * with a constant (0).
     *
     * @param scope The function scope
     * @param function The function
     * @param param The current parameter node in the parameter list.
     * @param paramIndex The index of the current parameter
     * @param canChangeSignature Whether function signature can be change.
     * @return Whether there is a following function parameter.
     */
    private boolean markUnreferencedFunctionArgs(
        Scope scope, Node function, Set<Var> referenced,
        Node param, int paramIndex,
        boolean canChangeSignature) {
      if (param != null) {
        // Take care of the following siblings first.
        boolean hasFollowing = markUnreferencedFunctionArgs(
            scope, function, referenced, param.getNext(), paramIndex + 1,
            canChangeSignature);

        Var var = scope.getVar(param.getString());
        if (!referenced.contains(var)) {
          checkNotNull(var);

          // Remove call parameter if we can generally change the signature
          // or if it is the last parameter in the parameter list.
          boolean modifyAllCallSites = canChangeSignature || !hasFollowing;
          if (modifyAllCallSites) {
            modifyAllCallSites = canRemoveArgFromCallSites(
                function, paramIndex);
          }

          tryRemoveArgFromCallSites(function, paramIndex, modifyAllCallSites);

          // Remove an unused function parameter if all the call sites can
          // be modified to remove it, or if it is the last parameter.
          if (modifyAllCallSites || !hasFollowing) {
            toRemove.add(param);
            return hasFollowing;
          }
        }
        return true;
      } else {
        // Anything past the last formal parameter can be removed from the call
        // sites.
        tryRemoveAllFollowingArgs(function, paramIndex - 1);
        return false;
      }
    }

    /**
     * Remove all references to a parameter, otherwise simplify the known
     * references.
     * @return Whether all the references were removed.
     */
    private boolean canRemoveArgFromCallSites(Node function, int argIndex) {
      Definition definition = getFunctionDefinition(function);

      // Check all the call sites.
      for (UseSite site : defFinder.getUseSites(definition)) {
        if (isModifiableCallSite(site)) {
          Node arg = getArgumentForCallOrNewOrDotCall(site, argIndex);
          // TODO(johnlenz): try to remove parameters with side-effects by
          // decomposing the call expression.
          if (arg != null && NodeUtil.mayHaveSideEffects(arg, compiler)) {
            return false;
          }
        } else {
          return false;
        }
      }

      return true;
    }

    /**
     * Remove all references to a parameter if possible otherwise simplify the
     * side-effect free parameters.
     */
    private void tryRemoveArgFromCallSites(
        Node function, int argIndex, boolean canModifyAllSites) {
      Definition definition = getFunctionDefinition(function);

      for (UseSite site : defFinder.getUseSites(definition)) {
        if (isModifiableCallSite(site)) {
          Node arg = getArgumentForCallOrNewOrDotCall(site, argIndex);
          if (arg != null) {
            // Even if we can't change the signature in general we can always
            // remove an unused value off the end of the parameter list.
            if (canModifyAllSites
                || (arg.getNext() == null && !NodeUtil.mayHaveSideEffects(arg, compiler))) {
              toRemove.add(arg);
            } else {
              // replace the node in the arg with 0
              if (!NodeUtil.mayHaveSideEffects(arg, compiler)
                  && (!arg.isNumber() || arg.getDouble() != 0)) {
                toReplaceWithZero.add(arg);
              }
            }
          }
        }
      }
    }

    /**
     * Remove all the following parameters without side-effects
     */
    private void tryRemoveAllFollowingArgs(Node function, final int argIndex) {
      Definition definition = getFunctionDefinition(function);
      for (UseSite site : defFinder.getUseSites(definition)) {
        if (!isModifiableCallSite(site)) {
          continue;
        }
        Node arg = getArgumentForCallOrNewOrDotCall(site, argIndex + 1);
        while (arg != null) {
          if (!NodeUtil.mayHaveSideEffects(arg)) {
            toRemove.add(arg);
          }
          arg = arg.getNext();
        }
      }
    }

    /**
     * Returns the nth argument node given a usage site for a direct function
     * call or for a func.call() node.
     */
    private static Node getArgumentForCallOrNewOrDotCall(UseSite site,
        final int argIndex) {
      int adjustedArgIndex = argIndex;
      Node parent = site.node.getParent();
      if (NodeUtil.isFunctionObjectCall(parent)) {
        adjustedArgIndex++;
      }
      return NodeUtil.getArgumentForCallOrNew(parent, adjustedArgIndex);
    }

    /**
     * @param function
     * @return Whether the callers to this function can be modified in any way.
     */
    boolean canModifyCallers(Node function) {
      if (NodeUtil.isVarArgsFunction(function)) {
        return false;
      }

      DefinitionSite defSite = defFinder.getDefinitionForFunction(function);
      if (defSite == null) {
        return false;
      }

      Definition definition = defSite.definition;

      // Be conservative, don't try to optimize any declaration that isn't as
      // simple function declaration or assignment.
      if (!NodeUtil.isSimpleFunctionDeclaration(function)) {
        return false;
      }

      return defFinder.canModifyDefinition(definition);
    }

    /**
     * @param site The site to inspect
     * @return Whether the call site is suitable for modification
     */
    private static boolean isModifiableCallSite(UseSite site) {
      return DefinitionUseSiteFinder.isCallOrNewSite(site)
          && !NodeUtil.isFunctionObjectApply(site.node.getParent());
    }

    /**
     * @return Whether the definitionSite represents a function whose call
     *      signature can be modified.
     */
    private boolean canChangeSignature(Node function) {
      Definition definition = getFunctionDefinition(function);
      CodingConvention convention = compiler.getCodingConvention();

      checkState(!definition.isExtern());

      Collection<UseSite> useSites = defFinder.getUseSites(definition);
      for (UseSite site : useSites) {
        Node parent = site.node.getParent();

        // This was a use site removed by something else before we run.
        // 1. By another pass before us which means the definition graph is
        //    no updated properly.
        // 2. By the continuations algorithm above.
        if (parent == null) {
          continue; // Ignore it.
        }

        // Ignore references within goog.inherits calls.
        if (parent.isCall()
            && convention.getClassesDefinedByCall(parent) != null) {
          continue;
        }

        // Accessing the property directly prevents rewrite.
        if (!DefinitionUseSiteFinder.isCallOrNewSite(site)) {
          if (!(parent.isGetProp()
              && NodeUtil.isFunctionObjectCall(parent.getParent()))) {
            return false;
          }
        }

        if (NodeUtil.isFunctionObjectApply(parent)) {
          return false;
        }

        // TODO(johnlenz): support specialization

        // Multiple definitions prevent rewrite.
        // Attempt to validate the state of the simple definition finder.
        Node nameNode = site.node;
        Collection<Definition> singleSiteDefinitions =
            defFinder.getDefinitionsReferencedAt(nameNode);
        checkState(singleSiteDefinitions.size() == 1);
        checkState(singleSiteDefinitions.contains(definition));
      }

      return true;
    }

    /**
     * @param function
     * @return the Definition object for the function.
     */
    private Definition getFunctionDefinition(Node function) {
      DefinitionSite definitionSite = defFinder.getDefinitionForFunction(
          function);
      checkNotNull(definitionSite);
      Definition definition = definitionSite.definition;
      checkState(!definitionSite.inExterns);
      checkState(definition.getRValue() == function);
      return definition;
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
      for (int current = 0; current < maybeUnreferenced.size(); current++) {
        Var var = maybeUnreferenced.get(current);
        if (referenced.contains(var)) {
          maybeUnreferenced.remove(current);
          current--;
        } else {
          boolean assignedToUnknownValue = false;

          if (NodeUtil.isNameDeclaration(var.getParentNode())
              && !var.getParentNode().getParent().isForIn()) {
            Node value = var.getInitialValue();
            assignedToUnknownValue = value != null
                && !NodeUtil.isLiteralValue(value, true);
          } else {
            // This was initialized to a function arg or a catch param
            // or a for...in variable.
            assignedToUnknownValue = true;
          }

          boolean maybeEscaped = false;
          boolean hasPropertyAssign = false;
          for (Assign assign : assignsByVar.get(var)) {
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
            maybeUnreferenced.remove(current);
            current--;
          }
        }
      }
    } while (changes);
  }

  /**
   * Remove all assigns to a var.
   */
  private void removeAllAssigns(Var var) {
    for (Assign assign : assignsByVar.get(var)) {
      compiler.reportChangeToEnclosingScope(assign.assignNode);
      assign.remove(compiler);
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
      Node parent = toRemove.getParent();
      Node grandParent = toRemove.getGrandparent();
      checkState(
          NodeUtil.isNameDeclaration(toRemove)
              || toRemove.isFunction()
              || (toRemove.isParamList() && parent.isFunction())
              || NodeUtil.isDestructuringDeclaration(grandParent)
              || toRemove.isArrayPattern() // Array Pattern
              || parent.isObjectPattern() // Object Pattern
              || toRemove.isClass()
              || (toRemove.isDefaultValue()
                      && NodeUtil.getEnclosingScopeRoot(toRemove).isFunction())
              || (toRemove.isRest() && NodeUtil.getEnclosingScopeRoot(toRemove).isFunction()),
          "We should only declare Vars and functions and function args and classes");

      if ((toRemove.isParamList() && parent.isFunction())
          || (toRemove.isDefaultValue() && NodeUtil.getEnclosingScopeRoot(toRemove).isFunction())
          || (toRemove.isRest() && NodeUtil.getEnclosingScopeRoot(toRemove).isFunction())) {
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

  private static class Assign {

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
    void remove(AbstractCompiler compiler) {
      Node parent = assignNode.getParent();
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

        parent.replaceChild(assignNode, replacement);
      } else {
        Node grandparent = parent.getParent();
        if (parent.isExprResult()) {
          grandparent.removeChild(parent);
          NodeUtil.markFunctionsDeleted(parent, compiler);
        } else {
          // mayHaveSecondarySideEffects is false, which means the value isn't needed,
          // but we need to keep the AST valid.
          parent.replaceChild(assignNode, IR.number(0).srcref(assignNode));
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
