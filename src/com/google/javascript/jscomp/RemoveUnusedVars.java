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

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
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
import java.util.Iterator;
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
 * @author nicksantos@google.com (Nick Santos)
 */
class RemoveUnusedVars
    implements CompilerPass, OptimizeCalls.CallGraphCompilerPass {

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
  private final List<Scope> allFunctionScopes = new ArrayList<>();

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
  private boolean mustResetModifyCallSites;

  private CallSiteOptimizer callSiteOptimizer;

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
    this.mustResetModifyCallSites = false;
  }

  /**
   * Traverses the root, removing all unused variables. Multiple traversals
   * may occur to ensure all unused variables are removed.
   */
  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());
    SimpleDefinitionFinder defFinder = compiler.getSimpleDefinitionFinder();
    if (this.modifyCallSites) {
      // When RemoveUnusedVars is run after OptimizeCalls, this.modifyCallSites
      // is true. But if OptimizeCalls stops making changes, PhaseOptimizer
      // stops running it, so we come to RemoveUnusedVars and the defFinder is
      // null. In this case, we temporarily set this.modifyCallSites to false
      // for this run, and then reset it back to true at the end, for
      // subsequent runs.
      if (defFinder == null) {
        this.modifyCallSites = false;
        this.mustResetModifyCallSites = true;
      } else {
        defFinder.process(externs, root);
      }
    }
    process(externs, root, defFinder);
    // When doing OptimizeCalls, RemoveUnusedVars is the last pass in the
    // sequence, so the def finder must not be used by any subsequent passes.
    compiler.setSimpleDefinitionFinder(null);
    if (this.mustResetModifyCallSites) {
      this.modifyCallSites = true;
      this.mustResetModifyCallSites = false;
    }
  }

  @Override
  public void process(
      Node externs, Node root, SimpleDefinitionFinder defFinder) {
    if (modifyCallSites) {
      Preconditions.checkNotNull(defFinder);
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
    Scope scope = SyntacticScopeCreator.makeUntyped(compiler).createScope(root, null);
    traverseNode(root, null, scope);

    if (removeGlobals) {
      collectMaybeUnreferencedVars(scope);
    }

    interpretAssigns();
    removeUnreferencedVars();
    for (Scope fnScope : allFunctionScopes) {
      removeUnreferencedFunctionArgs(fnScope);
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
    int type = n.getType();
    Var var = null;
    switch (type) {
      case Token.FUNCTION:
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

      case Token.ASSIGN:
        Assign maybeAssign = Assign.maybeCreateAssign(n);
        if (maybeAssign != null) {
          // Put this in the assign map. It might count as a reference,
          // but we won't know that until we have an index of all assigns.
          var = scope.getVar(maybeAssign.nameNode.getString());
          if (var != null) {
            assignsByVar.put(var, maybeAssign);
            assignsByNode.put(maybeAssign.nameNode, maybeAssign);

            if (isRemovableVar(var) &&
                !maybeAssign.mayHaveSecondarySideEffects) {
              // If the var is unreferenced and performing this assign has
              // no secondary side effects, then we can create a continuation
              // for it instead of traversing immediately.
              continuations.put(var, new Continuation(n, scope));
              return;
            }
          }
        }
        break;

      case Token.CALL:
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

      case Token.NAME:
        var = scope.getVar(n.getString());
        if (parent.isVar()) {
          Node value = n.getFirstChild();
          if (value != null && var != null && isRemovableVar(var) &&
              !NodeUtil.mayHaveSideEffects(value, compiler)) {
            // If the var is unreferenced and creating its value has no side
            // effects, then we can create a continuation for it instead
            // of traversing immediately.
            continuations.put(var, new Continuation(n, scope));
            return;
          }
        } else {

          // If arguments is escaped, we just assume the worst and continue
          // on all the parameters.
          if ("arguments".equals(n.getString()) && scope.isLocal()) {
            Node lp = scope.getRootNode().getSecondChild();
            for (Node a = lp.getFirstChild(); a != null; a = a.getNext()) {
              markReferencedVar(scope.getVar(a.getString()));
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
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      traverseNode(c, n, scope);
    }
  }

  private boolean isRemovableVar(Var var) {
    // Global variables are off-limits if the user might be using them.
    if (!removeGlobals && var.isGlobal()) {
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
   * Traverses a function, which creates a new scope in JavaScript.
   *
   * Note that CATCH blocks also create a new scope, but only for the
   * catch variable. Declarations within the block actually belong to the
   * enclosing scope. Because we don't remove catch variables, there's
   * no need to treat CATCH blocks differently like we do functions.
   */
  private void traverseFunction(Node n, Scope parentScope) {
    Preconditions.checkState(n.getChildCount() == 3, n);
    Preconditions.checkState(n.isFunction(), n);

    final Node body = n.getLastChild();
    Preconditions.checkState(body.getNext() == null && body.isBlock(), body);

    Scope fnScope = SyntacticScopeCreator.makeUntyped(compiler).createScope(n, parentScope);
    traverseNode(body, n, fnScope);

    collectMaybeUnreferencedVars(fnScope);
    allFunctionScopes.add(fnScope);
  }

  /**
   * For each variable in this scope that we haven't found a reference
   * for yet, add it to the list of variables to check later.
   */
  private void collectMaybeUnreferencedVars(Scope scope) {
    for (Iterator<Var> it = scope.getVars(); it.hasNext(); ) {
      Var var = it.next();
      if (isRemovableVar(var)) {
        maybeUnreferenced.add(var);
      }
    }
  }

  /**
   * Removes unreferenced arguments from a function declaration and when
   * possible the function's callSites.
   *
   * @param fnScope The scope inside the function
   */
  private void removeUnreferencedFunctionArgs(Scope fnScope) {
    // Notice that removing unreferenced function args breaks
    // Function.prototype.length. In advanced mode, we don't really care
    // about this: we consider "length" the equivalent of reflecting on
    // the function's lexical source.
    //
    // Rather than create a new option for this, we assume that if the user
    // is removing globals, then it's OK to remove unused function args.
    //
    // See http://code.google.com/p/closure-compiler/issues/detail?id=253
    if (!removeGlobals) {
      return;
    }

    Node function = fnScope.getRootNode();

    Preconditions.checkState(function.isFunction());
    if (NodeUtil.isGetOrSetKey(function.getParent())) {
      // The parameters object literal setters can not be removed.
      return;
    }

    Node argList = getFunctionArgList(function);
    boolean modifyCallers = modifyCallSites
        && callSiteOptimizer.canModifyCallers(function);
    if (!modifyCallers) {
      // Strip unreferenced args off the end of the function declaration.
      Node lastArg;
      while ((lastArg = argList.getLastChild()) != null) {
        Var var = fnScope.getVar(lastArg.getString());
        if (!referenced.contains(var)) {
          compiler.reportChangeToEnclosingScope(lastArg);
          argList.removeChild(lastArg);
        } else {
          break;
        }
      }
    } else {
      callSiteOptimizer.optimize(fnScope, referenced);
    }
  }


  /**
   * @return the LP node containing the function parameters.
   */
  private static Node getFunctionArgList(Node function) {
    return function.getSecondChild();
  }

  private static class CallSiteOptimizer {
    private final AbstractCompiler compiler;
    private final SimpleDefinitionFinder defFinder;
    private final List<Node> toRemove = new ArrayList<>();
    private final List<Node> toReplaceWithZero = new ArrayList<>();

    CallSiteOptimizer(
        AbstractCompiler compiler,
        SimpleDefinitionFinder defFinder) {
      this.compiler = compiler;
      this.defFinder = defFinder;
    }

    public void optimize(Scope fnScope, Set<Var> referenced) {
      Node function = fnScope.getRootNode();
      Preconditions.checkState(function.isFunction());
      Node argList = getFunctionArgList(function);

      // In this path we try to modify all the call sites to remove unused
      // function parameters.
      boolean changeCallSignature = canChangeSignature(function);
      markUnreferencedFunctionArgs(
          fnScope, function, referenced,
          argList.getFirstChild(), 0, changeCallSignature);
    }

    /**
     * Applies optimizations to all previously marked nodes.
     */
    public void applyChanges() {
      for (Node n : toRemove) {
        compiler.reportChangeToEnclosingScope(n);
        n.getParent().removeChild(n);
      }
      for (Node n : toReplaceWithZero) {
        compiler.reportChangeToEnclosingScope(n);
        n.getParent().replaceChild(n, IR.number(0).srcref(n));
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
          Preconditions.checkNotNull(var);

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
                || (arg.getNext() == null
                    && !NodeUtil.mayHaveSideEffects(arg, compiler))) {
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
      if (!SimpleDefinitionFinder.isSimpleFunctionDeclaration(function)) {
        return false;
      }

      return defFinder.canModifyDefinition(definition);
    }

    /**
     * @param site The site to inspect
     * @return Whether the call site is suitable for modification
     */
    private static boolean isModifiableCallSite(UseSite site) {
      return SimpleDefinitionFinder.isCallOrNewSite(site)
          && !NodeUtil.isFunctionObjectApply(site.node.getParent());
    }

    /**
     * @return Whether the definitionSite represents a function whose call
     *      signature can be modified.
     */
    private boolean canChangeSignature(Node function) {
      Definition definition = getFunctionDefinition(function);
      CodingConvention convention = compiler.getCodingConvention();

      Preconditions.checkState(!definition.isExtern());

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
        if (parent.isCall() &&
            convention.getClassesDefinedByCall(parent) != null) {
          continue;
        }

        // Accessing the property directly prevents rewrite.
        if (!SimpleDefinitionFinder.isCallOrNewSite(site)) {
          if (!(parent.isGetProp() &&
              NodeUtil.isFunctionObjectCall(parent.getParent()))) {
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
        Preconditions.checkState(singleSiteDefinitions.size() == 1);
        Preconditions.checkState(singleSiteDefinitions.contains(definition));
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
      Preconditions.checkNotNull(definitionSite);
      Definition definition = definitionSite.definition;
      Preconditions.checkState(!definitionSite.inExterns);
      Preconditions.checkState(definition.getRValue() == function);
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
          boolean hasPropertyAssign = false;

          if (var.getParentNode().isVar() &&
              !NodeUtil.isForIn(var.getParentNode().getParent())) {
            Node value = var.getInitialValue();
            assignedToUnknownValue = value != null &&
                !NodeUtil.isLiteralValue(value, true);
          } else {
            // This was initialized to a function arg or a catch param
            // or a for...in variable.
            assignedToUnknownValue = true;
          }

          boolean maybeEscaped = false;
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
      assign.remove();
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

      compiler.addToDebugLog("Unreferenced var: " + var.name);
      Node nameNode = var.nameNode;
      Node toRemove = nameNode.getParent();
      Node parent = toRemove.getParent();

      Preconditions.checkState(
          toRemove.isVar() ||
          toRemove.isFunction() ||
          toRemove.isParamList() &&
          parent.isFunction(),
          "We should only declare vars and functions and function args");

      if (toRemove.isParamList() &&
          parent.isFunction()) {
        // Don't remove function arguments here. That's a special case
        // that's taken care of in removeUnreferencedFunctionArgs.
      } else if (NodeUtil.isFunctionExpression(toRemove)) {
        if (!preserveFunctionExpressionNames) {
          compiler.reportChangeToEnclosingScope(toRemove);
          toRemove.getFirstChild().setString("");
        }
        // Don't remove bleeding functions.
      } else if (parent != null &&
          parent.isFor() &&
          parent.getChildCount() < 4) {
        // foreach iterations have 3 children. Leave them alone.
      } else if (toRemove.isVar() &&
          nameNode.hasChildren() &&
          NodeUtil.mayHaveSideEffects(nameNode.getFirstChild(), compiler)) {
        // If this is a single var declaration, we can at least remove the
        // declaration itself and just leave the value, e.g.,
        // var a = foo(); => foo();
        if (toRemove.getChildCount() == 1) {
          compiler.reportChangeToEnclosingScope(toRemove);
          parent.replaceChild(toRemove,
              IR.exprResult(nameNode.removeFirstChild()));
        }
      } else if (toRemove.isVar() &&
          toRemove.getChildCount() > 1) {
        // For var declarations with multiple names (i.e. var a, b, c),
        // only remove the unreferenced name
        compiler.reportChangeToEnclosingScope(toRemove);
        toRemove.removeChild(nameNode);
      } else if (parent != null) {
        compiler.reportChangeToEnclosingScope(toRemove);
        NodeUtil.removeChild(parent, toRemove);
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
        for (Node child = node.getFirstChild();
             child != null; child = child.getNext()) {
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
      Preconditions.checkState(NodeUtil.isAssignmentOp(assignNode));
      this.assignNode = assignNode;
      this.nameNode = nameNode;
      this.isPropertyAssign = isPropertyAssign;

      this.maybeAliased = NodeUtil.isExpressionResultUsed(assignNode);
      this.mayHaveSecondarySideEffects =
          maybeAliased ||
          NodeUtil.mayHaveSideEffects(assignNode.getFirstChild()) ||
          NodeUtil.mayHaveSideEffects(assignNode.getLastChild());
    }

    /**
     * If this is an assign to a variable or its property, return it.
     * Otherwise, return null.
     */
    static Assign maybeCreateAssign(Node assignNode) {
      Preconditions.checkState(NodeUtil.isAssignmentOp(assignNode));

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

        if (current.isGetProp() &&
            current.getLastChild().getString().equals("prototype")) {
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

    /**
     * Replace the current assign with its right hand side.
     */
    void remove() {
      Node parent = assignNode.getParent();
      if (mayHaveSecondarySideEffects) {
        Node replacement = assignNode.getLastChild().detachFromParent();

        // Aggregate any expressions in GETELEMs.
        for (Node current = assignNode.getFirstChild();
             !current.isName();
             current = current.getFirstChild()) {
          if (current.isGetElem()) {
            replacement = IR.comma(
                current.getLastChild().detachFromParent(), replacement);
            replacement.useSourceInfoIfMissingFrom(current);
          }
        }

        parent.replaceChild(assignNode, replacement);
      } else {
        Node grandparent = parent.getParent();
        if (parent.isExprResult()) {
          grandparent.removeChild(parent);
        } else {
          parent.replaceChild(assignNode,
              assignNode.getLastChild().detachFromParent());
        }
      }
    }
  }
}
