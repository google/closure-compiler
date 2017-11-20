/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.OptimizeCalls.ReferenceMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/**
 * Optimize function calls and function signatures.
 *
 * <ul>
 * <li>Removes optional parameters if no caller specifies it as argument.</li>
 * <li>Removes arguments at call site to function that ignores the parameter.</li>
 * <li>Inline a parameter if the function is always called with that constant.</li>
 * </ul>
 *
 * @author johnlenz@google.com (John Lenz)
 */
class OptimizeParameters implements CompilerPass, OptimizeCalls.CallGraphCompilerPass {
  private final AbstractCompiler compiler;
  private Scope globalScope;

  OptimizeParameters(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  @VisibleForTesting
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage() == LifeCycleStage.NORMALIZED);
    ReferenceMap refMap = OptimizeCalls.buildPropAndGlobalNameReferenceMap(
        compiler, externs, root);
    process(externs, root, refMap);
  }

  @Override
  public void process(Node externs, Node root, ReferenceMap refMap) {
    this.globalScope = refMap.getGlobalScope();

    // Find all function nodes whose callers ignore the return values.
    List<ArrayList<Node>> toOptimize = new ArrayList<>();

    for (Entry<String, ArrayList<Node>> entry : refMap.getNameReferences()) {
      String key = entry.getKey();
      ArrayList<Node> refs = entry.getValue();
      if (isCandidate(key, refs)) {
        toOptimize.add(refs);
      }
    }

    for (Entry<String, ArrayList<Node>> entry : refMap.getPropReferences()) {
      String key = entry.getKey();
      ArrayList<Node> refs = entry.getValue();
      if (isCandidate(key, refs)) {
        toOptimize.add(refs);
      }
    }

    // NOTE: The optimization that are perform must be careful to keep the
    // ReferenceMap in a consistent state. They should be careful to not
    // remove or add global references without update the reference map.
    // While adding references to the map is O(1), removing references
    // O(n) where (n) is the number of references.
    //
    // So the most transformative pass should be last. So:
    //
    // - Removing parameters not provided by any call-site only
    // moves the name and default values from the parameter list to the
    // functions' bodies, so no updates are needed if the same node
    // are reused.
    //
    // - Moving parameters that are provided the same value by every
    // call-site to the function bodies, is currently limited to cases
    // where there are only one function definition, so no updates
    // are needed if the same nodes are reused.
    //
    // - Removing parameters that are unreferenced from call-sites, may
    // remove references, as it is run last.

    for (ArrayList<Node> refs : toOptimize) {
      tryEliminateOptionalArgs(refs);
    }

    for (ArrayList<Node> refs : toOptimize) {
      tryEliminateConstantArgs(refs);
    }

    // tryEliminateUnusedArgs may mutate
    UnusedParameterOptimizer optimizer = new UnusedParameterOptimizer();
    for (ArrayList<Node> refs : toOptimize) {
      optimizer.tryEliminateUnusedArgs(refs);
    }
    optimizer.applyChanges();
  }

  class UnusedParameterOptimizer {
    final List<Node> toRemove = new ArrayList<>();
    final List<Node> toReplaceWithZero = new ArrayList<>();

    /**
     * Attempt to eliminate unused parameters by removing them from both the call sites
     * and the function definitions.
     *
     * An unused first parameter:
     *   function foo(a, b) {use(b);}
     *   foo(1,2);
     *   foo(1,3)
     * becomes
     *   function foo(b) {use(b);}
     *   foo(2);
     *   foo(3);
     *
     * @param refs A list of references to the symbol (name or property) as vetted by
     *     #isCandidate.
     */
    void tryEliminateUnusedArgs(ArrayList<Node> refs) {
      // An argument is unused if it is than the number of declare parameters
      // or if it marked as unused.
      List<Node> fns = ReferenceMap.getFunctionNodes(refs);
      Preconditions.checkState(!fns.isEmpty());

      int maxFormals = 0;
      int lowestUsedRest = Integer.MAX_VALUE;
      BitSet used = new BitSet();
      for (Node fn : fns) {
        Node paramList = NodeUtil.getFunctionParameters(fn);
        int index = -1;
        for (Node c = paramList.getFirstChild(); c != null; c = c.getNext()) {
          index++;
          if (!c.isUnusedParameter()) {
            used.set(index);
            if (c.isRest()) {
              lowestUsedRest = Math.min(lowestUsedRest, index);
              if (lowestUsedRest == 0) {
                // don't bother doing anything more, all the parameters are used.
                return;
              }
            }
          }
        }

        maxFormals = Math.max(maxFormals, index + 1);
      }

      BitSet unused = new BitSet();
      if (maxFormals > 0) {
        unused = ((BitSet) used.clone());
        unused.flip(0, maxFormals);
        // There was a use "rest" declaration
        if (lowestUsedRest < maxFormals) {
          // everything above lowestUsedRest is used
          unused.clear(lowestUsedRest, maxFormals - 1);
          if (unused.cardinality() == 0) {
            // Nothing can possibly be removed
            return;
          }
        }
      }

      int lowestUnused = Integer.MAX_VALUE;
      for (int i = 0; i < maxFormals; i++) {
        if (unused.get(i)) {
          lowestUnused = i;
          break;
        }
      }

      // NOTE: RemoveUnusedVars removes any trailing unused formals, so we don't need to
      // do for that case.

      BitSet unremovableAtAnyCall = ((BitSet) used.clone());

      // A parameter is removable from a call-site if the parameter value
      // has no side-effects. If all call-sites can be updated, the
      // parameter can be removed from the function definitions.
      // Regardless, the side-effect free values can still be replaced
      // with a simpler expression.

      // 3 values determine the range of removable parameters:
      // the number of formal parameters, the unused parameters bitset,
      // and the position of a used rest parameter (if any).

      // - If the parameter index is higher >= the "rest", it
      // is not a candidate, otherwise if it is in bitset or
      // the greater than the declared formals.

      // To be removed, the candidate must be side-effect free.

      // If not all candidates can be removed, the removable
      // candidates are still removed if there are no following parameters.
      // If there are following parameters the removable candidates are
      // replaced with a literal zero instead.

      // Build a list of parameters to remove
      int lowestSpread = Integer.MAX_VALUE;
      for (Node n : refs) {
        if (ReferenceMap.isCallOrNewTarget(n)) {
          Node param = ReferenceMap.getFirstArgumentForCallOrNewOrDotCall(n);
          int paramIndex = 0;
          while (param != null) {
            if (param.isSpread()) {
              lowestSpread = Math.min(lowestSpread, paramIndex);
              unremovableAtAnyCall.set(paramIndex);
              if (lowestSpread < lowestUnused) {
                break;
              }
            }
            if (unused.get(paramIndex) && NodeUtil.mayHaveSideEffects(param, compiler)) {
              unremovableAtAnyCall.set(paramIndex);
            }

            param = param.getNext();
            paramIndex++;
          }
        }
      }

      // TODO: if spread < maxformal, set unremovable from spread...maxformal

      for (Node n : refs) {
        if (ReferenceMap.isCallOrNewTarget(n) && !alreadyRemoved(n)) {
          Node arg = ReferenceMap.getFirstArgumentForCallOrNewOrDotCall(n);
          recordRemovalCallArguments(
              lowestUsedRest, maxFormals, unused, unremovableAtAnyCall, arg, 0);
        }
      }

      for (Node fn : fns) {
        Node paramList = NodeUtil.getFunctionParameters(fn);
        Node param = paramList.getFirstChild();
        removeUnusedFunctionPameters(lowestUsedRest, unused, unremovableAtAnyCall, param, 0);
      }
    }

    // max (rest is used) is or removeAllAfter (last formal) is but not both.
    void recordRemovalCallArguments(
        int max, int removeAllAfter, BitSet unused, BitSet unremovable, Node arg, int index) {
      if (arg != null && index < max) {
        if (arg.isSpread() && index < removeAllAfter) {
          // Unless we can remove everything, we can't remove anything.
          return;
        }
        recordRemovalCallArguments(
            max, removeAllAfter, unused, unremovable, arg.getNext(), index + 1);
        if (index >= removeAllAfter || unused.get(index)) {
          if (!NodeUtil.mayHaveSideEffects(arg, compiler)) {
            if (unremovable.get(index)) {
              toReplaceWithZero.add(arg);
            } else {
              toRemove.add(arg);
            }
          }
        }
      }
    }

    void removeUnusedFunctionPameters(
        int max, BitSet unused, BitSet unremovable, Node param, int index) {
      if (param != null && index < max) {
        removeUnusedFunctionPameters(max, unused, unremovable, param.getNext(), index + 1);
        if (unused.get(index) && !unremovable.get(index)) {
          checkState(param.isName());  // update for ES6
          // params are not otherwise referenceable.
          compiler.reportChangeToEnclosingScope(param);
          param.detach();
        }
      }
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

  /**
   * This reference set is a candidate for parameter-moving if:
   *  - if all call sites are known (no aliasing)
   *  - if all definition sites are known (the possible values are known functions)
   *  - there is at least one definition
   */
  private boolean isCandidate(String name, ArrayList<Node> refs) {
    if (!OptimizeCalls.mayBeOptimizableName(compiler, name)) {
      return false;
    }

    boolean seenCandidateDefiniton = false;
    boolean seenCandidateUse = false;
    for (Node n : refs) {
      // TODO(johnlenz): Determine what to do about ".constructor" references.
      // Currently classes that are super classes or have superclasses aren't optimized
      //
      // if (parent.isCall() && n != parent.getFirstChild() && isClassDefiningCall(parent)) {
      //   continue;
      // } else

      if (ReferenceMap.isCallOrNewTarget(n)) {
        // TODO(johnlenz): filter .apply when we support it
        seenCandidateUse = true;
      } else if (isCandidateDefinition(n)) {
        seenCandidateDefiniton = true;
      } else {
        // If this isn't an non-aliasing reference (typeof, instanceof, etc)
        // then there is nothing that can be done.
        if (!OptimizeCalls.isAllowedReference(n)) {
          // TODO(johnlenz): allow extends clauses.
          return false;
        }
      }
    }

    return seenCandidateDefiniton && seenCandidateUse;
  }

  /**
   * Determines if a call defines a class inheritance or mixing
   * relation, according to the current coding convention.
   */
  private boolean isClassDefiningCall(Node callNode) {
    SubclassRelationship classes =
        compiler.getCodingConvention().getClassesDefinedByCall(callNode);
    return classes != null;
  }

  private boolean isCandidateDefinition(Node n) {
    Node parent = n.getParent();
    if (parent.isFunction() && NodeUtil.isFunctionDeclaration(parent)) {
      return allDefinitionsAreCandidateFunctions(parent);
    } else if (ReferenceMap.isSimpleAssignmentTarget(n)) {
      if (allDefinitionsAreCandidateFunctions(parent.getLastChild())) {
        return true;
      }
    } else if (n.isName() && n.hasChildren()) {
      if (allDefinitionsAreCandidateFunctions(n.getFirstChild())) {
        return true;
      }
    }

    return false;
  }

  private static boolean allDefinitionsAreCandidateFunctions(Node n) {
    switch (n.getToken()) {
      case FUNCTION:
        // Named function expression can refer to themselves,
        // "arguments" can refer to all parameters or their count,
        // so they are not candidates.
        return !NodeUtil.isNamedFunctionExpression(n)
            && !NodeUtil.doesFunctionReferenceOwnArgumentsObject(n);
      case CAST:
      case COMMA:
        return allDefinitionsAreCandidateFunctions(n.getLastChild());
      case HOOK:
        return allDefinitionsAreCandidateFunctions(n.getSecondChild())
            && allDefinitionsAreCandidateFunctions(n.getLastChild());
      case OR:
      case AND:
        return allDefinitionsAreCandidateFunctions(n.getFirstChild())
            && allDefinitionsAreCandidateFunctions(n.getLastChild());
      default:
        return false;
    }
  }

  /**
   * Removes any optional parameters if no callers specifies it as an argument.
   */
  private void tryEliminateOptionalArgs(ArrayList<Node> refs) {
    // Count the maximum number of arguments passed into this function all
    // all points of the program.
    int maxArgs = -1;

    for (Node n : refs) {
      if (ReferenceMap.isCallOrNewTarget(n)) {
        int numArgs = 0;
        Node firstArg = ReferenceMap.getFirstArgumentForCallOrNewOrDotCall(n);
        for (Node c = firstArg; c != null; c = c.getNext()) {
          numArgs++;
          if (c.isSpread()) {
            // Bail: with spread we must assume all parameters are used, don't waste
            // any more time.
            return;
          }
        }

        if (numArgs > maxArgs) {
          maxArgs = numArgs;
        }
      }
    }

    for (Node fn : ReferenceMap.getFunctionNodes(refs)) {
      eliminateParamsAfter(fn, maxArgs);
    }
  }

  /**
   * Eliminate parameters if they are always constant.
   *
   * function foo(a, b) {...}
   * foo(1,2);
   * foo(1,3)
   * becomes
   * function foo(b) { var a = 1 ... }
   * foo(2);
   * foo(3);
   *
   * @param refs A list of references to the symbol (name or property) as vetted by
   *     #isCandidate.
   */
  private void tryEliminateConstantArgs(ArrayList<Node> refs) {
    List<Parameter> parameters = new ArrayList<>();
    boolean firstCall = true;

    // Build a list of parameters to remove
    boolean continueLooking = false;
    for (Node n : refs) {
      if (ReferenceMap.isCallOrNewTarget(n)) {
        Node call = n.getParent();
        Node firstParam = call.getFirstChild();
        // Look at the first param in the case of a ".call", if it is a spread, the order
        // of the parameters is unknown.
        if (firstParam.isSpread()) {
          return;  // stop looking
        }
        Node cur = ReferenceMap.getFirstArgumentForCallOrNewOrDotCall(n);
        if (firstCall) {
          // Use the first call to construct a list of parameter values of the
          // function.
          continueLooking = buildParameterList(parameters, cur);
          firstCall = false;
        } else {
          // All the rest must match
          continueLooking = findFixedParameters(parameters, cur);
        }
        if (!continueLooking) {
          return;
        }
      }
    }

    continueLooking = adjustForSideEffects(parameters);
    if (!continueLooking) {
      return;
    }

    List<Node> fns = ReferenceMap.getFunctionNodes(refs);
    if (fns.size() > 1) {
      // TODO(johnlenz): support moving simple constants.
      // This requires cloning the tree and avoiding adding additional calls/definitions that will
      // invalidate the reference map
      return;
    }

    // Found something to do, move the values from the call sites to the function definitions.
    for (Node n : refs) {
      if (ReferenceMap.isCallOrNewTarget(n) && !alreadyRemoved(n)) {
        optimizeCallSite(parameters, n);
      }
    }

    for (Node fn : fns) {
      optimizeFunctionDefinition(parameters, fn);
    }
  }

  /**
   * Adjust the parameters to move based on the side-effects seen.
   * @return Whether there are any movable parameters.
   */
  private static boolean adjustForSideEffects(List<Parameter> parameters) {
    // A parameter with side-effects can move if there are no following parameters
    // that can be effected.

    // A parameter can be moved if it can't be side-effected (a literal),
    // or there are no following side-effects, that aren't moved.

    boolean anyMovable = false;
    boolean seenUnmovableSideEffects = false;
    boolean seenUnmoveableSideEffected = false;
    for (int i = parameters.size() - 1; i >= 0; i--) {
      Parameter current = parameters.get(i);

      // Preserve side-effect ordering, don't move this parameter if:
      // * the current parameter has side-effects and a following
      // parameters that will not be move can be effected.
      // * the current parameter can be effected and a following
      // parameter that will not be moved has side-effects

      if (current.shouldRemove
          && ((seenUnmovableSideEffects && current.canBeSideEffected())
          || (seenUnmoveableSideEffected && current.hasSideEffects()))) {
        current.shouldRemove = false;
      }

      if (current.shouldRemove) {
        anyMovable = true;
      } else {
        if (current.canBeSideEffected) {
          seenUnmoveableSideEffected = true;
        }

        if (current.hasSideEffects) {
          seenUnmovableSideEffects = true;
        }
      }
    }
    return anyMovable;
  }

  /**
   * Determine which parameters use the same expression.
   * @return Whether any parameter was found that can be updated.
   */
  private boolean findFixedParameters(List<Parameter> parameters, Node cur) {
    boolean anyMovable = false;
    int index = 0;
    while (cur != null) {
      Parameter p;
      if (index >= parameters.size()) {
        p = new Parameter(cur, false);
        parameters.add(p);
      } else {
        p = parameters.get(index);
        if (p.shouldRemove()) {
          Node value = p.getArg();
          if (!cur.isEquivalentTo(value)) {
            p.setShouldRemove(false);
          } else {
            anyMovable = true;
          }
        }
      }

      setParameterSideEffectInfo(p, cur);
      cur = cur.getNext();
      index++;
    }

    for (; index < parameters.size(); index++) {
      parameters.get(index).setShouldRemove(false);
    }

    return anyMovable;
  }

  /**
   * @return Whether any parameter was movable.
   */
  private boolean buildParameterList(List<Parameter> parameters, Node cur) {
    boolean anyMovable = false;
    while (cur != null) {
      if (cur.isSpread()) {
        break;
      }
      boolean movable = isMovableValue(cur, globalScope);
      Parameter p = new Parameter(cur, movable);
      setParameterSideEffectInfo(p, cur);
      parameters.add(p);
      if (movable) {
        anyMovable = true;
      }
      cur = cur.getNext();
    }
    return anyMovable;
  }

  private void setParameterSideEffectInfo(Parameter p, Node value) {
    if (!p.hasSideEffects()) {
      p.setHasSideEffects(NodeUtil.mayHaveSideEffects(value, compiler));
    }

    if (!p.canBeSideEffected()) {
      p.setCanBeSideEffected(NodeUtil.canBeSideEffected(value));
    }
  }


  /**
   * @return Whether the expression can be safely moved to another function
   *   in another scope.
   */
  private static boolean isMovableValue(Node n, Scope globalScope) {
    // Things that can change value or are inaccessible can't be moved, these
    // are "this", "arguments", local names, and functions that capture local
    // values.
    switch (n.getToken()) {
      case THIS:
        return false;
      case FUNCTION:
        // Don't move function closures.
        // TODO(johnlenz): Closure that only contain global reference can be
        // moved.
        return false;
      case NAME:
        if (n.getString().equals("arguments")) {
          return false;
        } else {
          Var v = globalScope.getVar(n.getString());
          if (v == null) {
            return false;
          }
        }
        break;
      default:
        break;
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (!isMovableValue(c, globalScope)) {
        return false;
      }
    }
    return true;
  }

  private void optimizeFunctionDefinition(List<Parameter> parameters, Node function) {
    for (int index = parameters.size() - 1; index >= 0; index--) {
      if (parameters.get(index).shouldRemove()) {
        Node paramName = eliminateFunctionParamAt(function, index);
        addVariableToFunction(function, paramName, parameters.get(index).getArg());
      }
    }
  }

  private void optimizeCallSite(List<Parameter> parameters, Node target) {
    Node call = ReferenceMap.getCallOrNewNodeForTarget(target);
    boolean mayMutateArgs = call.mayMutateArguments();
    boolean mayMutateGlobalsOrThrow = call.mayMutateGlobalStateOrThrow();
    for (int index = parameters.size() - 1; index >= 0; index--) {
      Parameter p = parameters.get(index);
      if (p.shouldRemove()) {
        eliminateCallTargetArgAt(target, index);

        if (mayMutateArgs && !mayMutateGlobalsOrThrow
            // We want to cover both global-state arguments, and
            // expressions that might throw exceptions.
            // We're deliberately conservative here b/c it's
            // difficult to test all the edge cases.
            && !NodeUtil.isImmutableValue(p.getArg())) {
          mayMutateGlobalsOrThrow = true;
          call.setSideEffectFlags(
              new Node.SideEffectFlags(call.getSideEffectFlags()).setMutatesGlobalState());
        }
      }
    }
  }

  /**
   * Simple container class that keeps tracks of a parameter and whether it
   * should be removed.
   */
  private static class Parameter {
    private final Node arg;
    private boolean shouldRemove;
    private boolean hasSideEffects;
    private boolean canBeSideEffected;

    public Parameter(Node arg, boolean shouldRemove) {
      this.shouldRemove = shouldRemove;
      this.arg = arg;
    }

    public Node getArg() {
      return arg;
    }

    public boolean shouldRemove() {
      return shouldRemove;
    }

    public void setShouldRemove(boolean value) {
      shouldRemove = value;
    }

    public void setHasSideEffects(boolean hasSideEffects) {
      this.hasSideEffects = hasSideEffects;
    }

    public boolean hasSideEffects() {
      return hasSideEffects;
    }

    public void setCanBeSideEffected(boolean canBeSideEffected) {
      this.canBeSideEffected = canBeSideEffected;
    }

    public boolean canBeSideEffected() {
      return canBeSideEffected;
    }
  }

  /**
   * Adds a variable to the top of a function block.
   * @param function A function node.
   * @param varName The name of the variable.
   * @param value The initial value of the variable.
   */
  private void addVariableToFunction(Node function, @Nullable Node varName, Node value) {
    Preconditions.checkArgument(function.isFunction(), "Expected function, got: %s", function);

    Node block = NodeUtil.getFunctionBody(function);
    checkState(value.getParent() == null);
    Node stmt;
    if (varName != null) {
      stmt = NodeUtil.newVarNode(varName.getString(), value);
    } else {
      stmt = IR.exprResult(value).useSourceInfoFrom(value);
    }
    block.addChildToFront(stmt);
    compiler.reportChangeToEnclosingScope(stmt);
  }

  /**
   * Removes all formal parameters starting at argIndex.
   */
  private void eliminateParamsAfter(Node fnNode, int argIndex) {
    Node formalArgPtr = NodeUtil.getFunctionParameters(fnNode).getFirstChild();
    while (argIndex != 0 && formalArgPtr != null) {
      formalArgPtr = formalArgPtr.getNext();
      argIndex--;
    }
    eliminateParamsAfter(fnNode, formalArgPtr);
  }

  private void eliminateParamsAfter(Node fnNode, Node argNode) {
    if (argNode != null) {
      // Keep the args in the same order, do the last first.
      eliminateParamsAfter(fnNode, argNode.getNext());

      argNode.detach();
      Node var = IR.var(argNode).useSourceInfoIfMissingFrom(argNode);
      fnNode.getLastChild().addChildToFront(var);
      compiler.reportChangeToEnclosingScope(var);
    }
  }

  /**
   * Eliminates the parameter from a function definition.
   *
   * @param function The function node
   * @param argIndex The index of the argument to remove.
   * @param definitionFinder The definition and use sites index.
   * @return The Node of the argument removed.
   */
  @Nullable
  private Node eliminateFunctionParamAt(Node function, int argIndex) {
    checkArgument(function.isFunction(), "Node must be a function.");
    Node formalParamNode = NodeUtil.getArgumentForFunction(function, argIndex);
    if (formalParamNode != null) {
      NodeUtil.deleteNode(formalParamNode, compiler);
    }
    return formalParamNode;
  }

  /**
   * Eliminates the parameter from a function call.
   * @param definitionFinder The definition and use sites index.
   * @param p
   * @param call The function call node
   * @param argIndex The index of the argument to remove.
   */
  private void eliminateCallTargetArgAt(Node ref, int argIndex) {
    Node callArgNode = ReferenceMap.getArgumentForCallOrNewOrDotCall(ref, argIndex);
    if (callArgNode != null) {
      NodeUtil.deleteNode(callArgNode, compiler);
    }
  }
}
