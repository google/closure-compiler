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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.OptimizeCalls.ReferenceMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
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
      // An argument is unused if its position is greater than the number of declared parameters
      // or if it marked as unused.
      ImmutableListMultimap<Node, Node> fns = ReferenceMap.getFunctionNodes(refs);
      Preconditions.checkState(!fns.isEmpty());

      // Examine all function definitions that are ever assigned to the symbol to determine:
      // 1. Which formal parameter positions are used by at least one of the definitions?
      // 2. What is the largest number of formal parameters across all of the functions?
      // 3. The lowest formal parameter position that contains a rest parameter that is used.
      // e.g.
      // foo = function(used0, unused1, ...usedRest) {}
      // foo = function(unused0, ...unusedRest) {}
      // In this case maxFormalsCount = 3, lowestUsedRest = 2, and used = { 0, 2 }
      int maxFormalsCount = 0;
      int lowestUsedRest = Integer.MAX_VALUE;
      BitSet used = new BitSet();
      for (Node fn : fns.values()) {
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

        maxFormalsCount = Math.max(maxFormalsCount, index + 1);
      }

      // every argument slot after the earliest rest is used
      if (lowestUsedRest < maxFormalsCount) {
        used.set(lowestUsedRest, maxFormalsCount);
      }

      BitSet unused = ((BitSet) used.clone());
      unused.flip(0, maxFormalsCount);

      // If was a used "rest" declaration, there are no trailing parameters to remove, so
      // bail out now if there are no unused formals.
      if (lowestUsedRest < maxFormalsCount && unused.cardinality() == 0) {
        return;
      }

      // NOTE: RemoveUnusedCode removes any trailing unused formals, so we don't need to
      // do for that case.

      BitSet unremovable = ((BitSet) used.clone());

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
            if (paramIndex >= maxFormalsCount) {
              break;
            }

            if (param.isSpread()) {
              lowestSpread = Math.min(lowestSpread, paramIndex);
              break;
            }

            if (!unremovable.get(paramIndex) && NodeUtil.mayHaveSideEffects(param, compiler)) {
              unremovable.set(paramIndex);
            }

            param = param.getNext();
            paramIndex++;
          }
        }
      }

      // Although, a spread prevents the removal of it and all following used slots, it doesn't
      // prevent the replacement of unused values from other call sites
      if (lowestSpread < maxFormalsCount) {
        unremovable.set(lowestSpread, maxFormalsCount);
      }

      // Only remove trailing parameters if there isn't a rest arguments in any of the
      // definition sites.
      int removeAllAfterIndex = Integer.MAX_VALUE;
      if (lowestUsedRest == Integer.MAX_VALUE) {
        removeAllAfterIndex = maxFormalsCount - 1;
      }

      for (Node n : refs) {
        if (ReferenceMap.isCallOrNewTarget(n) && !alreadyRemoved(n)) {
          Node arg = ReferenceMap.getFirstArgumentForCallOrNewOrDotCall(n);
          recordRemovalCallArguments(
              lowestUsedRest, removeAllAfterIndex, unused, unremovable, arg, 0);
        }
      }

      for (Node fn : fns.values()) {
        Node paramList = NodeUtil.getFunctionParameters(fn);
        Node param = paramList.getFirstChild();
        removeUnusedFunctionParameters(unremovable, param, 0);
      }
    }

    // Either firstRestIndex will be MAX_VALUE or removeAllAfterIndex will be MAX_VALUE
    void recordRemovalCallArguments(
        int firstRestIndex, int removeAllAfterIndex,
        BitSet unused, BitSet unremovable,
        Node arg, int index) {
      if (arg == null) {
        return;
      }

      if (index > removeAllAfterIndex) {
        removeArgAndFollowing(arg);
        return;
      }

      if (arg.isSpread()) {
        // There is no meaningful "index" after a spread.
        return;
      }

      recordRemovalCallArguments(
          firstRestIndex, removeAllAfterIndex,
          unused, unremovable,
          arg.getNext(), index + 1);

      if (index < firstRestIndex && unused.get(index)) {
        if (!NodeUtil.mayHaveSideEffects(arg, compiler)) {
          if (unremovable.get(index)) {
            if (!arg.isNumber() || arg.getDouble() != 0) {
              toReplaceWithZero.add(arg);
            }
          } else {
            toRemove.add(arg);
          }
        }
      }
    }

    void removeArgAndFollowing(Node arg) {
      if (arg != null) {
        removeArgAndFollowing(arg.getNext());
        if (!NodeUtil.mayHaveSideEffects(arg, compiler)) {
          toRemove.add(arg);
        }
      }
    }

    void removeUnusedFunctionParameters(BitSet unremovable, Node param, int index) {
      if (param != null) {
        removeUnusedFunctionParameters(unremovable, param.getNext(), index + 1);
        if (!unremovable.get(index)) {
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
        Preconditions.checkState(!n.isNumber() || n.getDouble() != 0.0);
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
    while (parent != null) {
      n = parent;
      parent = n.getParent();
    }
    return !n.isRoot();
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
    } else if (isClassMemberDefinition(n)) {
      return true;
    }

    return false;
  }

  private boolean isClassMemberDefinition(Node n) {
    return n.isMemberFunctionDef() && n.getParent().isClassMembers();
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

    for (Node fn : ReferenceMap.getFunctionNodes(refs).values()) {
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
    List<Parameter> parameters = findFixedArguments(refs);
    if (parameters == null) {
      return;
    }

    ImmutableListMultimap<Node, Node> fns = ReferenceMap.getFunctionNodes(refs);
    if (fns.size() > 1) {
      // TODO(johnlenz): support moving simple constants.
      // This requires cloning the tree and avoiding adding additional calls/definitions that will
      // invalidate the reference map
      return;
    }

    // Only one definition is currently supported.
    Node fn = Iterables.getOnlyElement(fns.values());

    boolean continueLooking = adjustForConstraints(fn, parameters);
    if (!continueLooking) {
      return;
    }

    // Found something to do, move the values from the call sites to the function definitions.
    for (Node n : refs) {
      if (ReferenceMap.isCallOrNewTarget(n) && !alreadyRemoved(n)) {
        optimizeCallSite(parameters, n);
      }
    }

    optimizeFunctionDefinition(parameters, fn);
  }

  /**
   * @param refs A list of references to the symbol (name or property) as vetted by
   *     #isCandidate.
   * @return A list of Parameter objects, in the declaration order, which represent potentially
   *     movable values fixed values from all call sites or null if there are no candidate values.
   */
  private List<Parameter> findFixedArguments(ArrayList<Node> refs) {
    List<Parameter> parameters = new ArrayList<>();
    boolean firstCall = true;

    // Build a list of parameters to remove
    boolean continueLooking = false;
    for (Node n : refs) {
      if (ReferenceMap.isCallOrNewTarget(n)) {
        Node call = n.getParent();
        Node firstDotCallParam = call.getFirstChild();
        // Normally, we ignore the first parameter to a .call expression (the 'this' value)
        // but if it is a spread, we know nothing about any of the parameters, so bail out now.
        if (firstDotCallParam.isSpread()) {
          continueLooking = false;
          break;
        }
        Node cur = ReferenceMap.getFirstArgumentForCallOrNewOrDotCall(n);
        if (firstCall) {
          // Use the first call to construct a list of parameter values of the
          // function.
          continueLooking = buildInitialParameterList(parameters, cur);
          firstCall = false;
        } else {
          // All the rest must match
          continueLooking = findFixedParameters(parameters, cur);
        }
        if (!continueLooking) {
          break;
        }
      }
    }

    return (continueLooking) ? parameters : null;
  }

  /**
   * Adjust the provided Parameter objects value created by #findFixedArguments for "rest" value
   * and side-effects which might prevent the motion of the parameters from the call sites to the
   * function body.
   *
   * @param parameters A list of Parameter objects summarizing all the call-sites and
   *     whether any of the parameters are fixed.
   * @return Whether there are any movable parameters.
   */
  private static boolean adjustForConstraints(Node fn, List<Parameter> parameters) {
    JSDocInfo info = NodeUtil.getBestJSDocInfo(fn);
    if (info != null && info.isNoInline()) {
      return false;
    }

    Node paramList = NodeUtil.getFunctionParameters(fn);
    Node lastFormal = paramList.getLastChild();
    int restIndex = Integer.MAX_VALUE;
    int lastNonRestFormal = paramList.getChildCount() - 1;
    Node formal = lastFormal;
    if (lastFormal != null && lastFormal.isRest()) {
      restIndex = lastNonRestFormal;
      lastNonRestFormal--;
      formal = formal.getPrevious();
    }

    // A parameter with side-effects can move if there are no following parameters
    // that can be affected.

    // A parameter can be moved if it can't be side-effected (a literal),
    // or there are no following side-effects, that aren't moved.

    boolean anyMovable = false;
    boolean seenUnmovableSideEffects = false;
    boolean seenUnmoveableSideEffected = false;
    boolean allRestValueRemovable = true;
    for (int i = parameters.size() - 1; i >= 0; i--) {
      Parameter current = parameters.get(i);

      // back-off for default values whose default value maybe needed.
      // TODO(johnlenz): handle used default
      if (i <= lastNonRestFormal) {
        if (formal.isDefaultValue() && current.mayBeUndefined) {
          current.shouldRemove = false;
        }
        formal = formal.getPrevious();
      }

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

      // If any values that are part of the rest cannot be moved to the function body,
      // then all the rest values must remain at the callsite.
      if (i >= restIndex) {
        if (allRestValueRemovable) {
          if (!current.shouldRemove) {
            anyMovable = false;
            allRestValueRemovable = false;
            // revisit the trailing params and remark them now that we know they are unremovable.
            for (int j = i + 1; j < parameters.size(); j++) {
              Parameter p = parameters.get(0);
              p.shouldRemove = false;
              if (p.canBeSideEffected) {
                seenUnmoveableSideEffected = true;
              }
              if (p.hasSideEffects) {
                seenUnmovableSideEffects = true;
              }
            }
          }
        } else {
          current.shouldRemove = false;
        }
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
        setParameterSideEffectInfo(p, cur);
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
  private boolean buildInitialParameterList(List<Parameter> parameters, Node cur) {
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
    p.setHasSideEffects(NodeUtil.mayHaveSideEffects(value, compiler));
    p.setCanBeSideEffected(NodeUtil.canBeSideEffected(value));
    p.setMayBeUndefined(NodeUtil.mayBeUndefined(value));
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

  private void optimizeFunctionDefinition(List<Parameter> parameters, Node fn) {
    Node paramList = NodeUtil.getFunctionParameters(fn);
    Node maybeRest = paramList.getLastChild();
    int lastParameter = parameters.size() - 1;
    if (maybeRest != null && maybeRest.isRest()) {
      int restIndex = paramList.getChildCount() - 1;
      // If the rest parameter is removable they all are.
      if (parameters.size() < restIndex
          || (restIndex < parameters.size() && parameters.get(restIndex).shouldRemove())) {
        Node value = IR.arraylit().srcref(maybeRest);
        for (int i = restIndex; i < parameters.size(); i++) {
          Parameter parameter = parameters.get(i);

          checkState(parameter.shouldRemove());
          value.addChildToBack(parameters.get(i).getArg());
        }
        maybeRest.detach();
        Node lhs = maybeRest.removeFirstChild();
        addRestVariableToFunction(fn, lhs, value);
      }

      // process the rest.
      lastParameter = Math.min(parameters.size() - 1, restIndex - 1);
    }

    for (int i = lastParameter; i >= 0; i--) {
      Parameter parameter = parameters.get(i);
      if (parameter.shouldRemove()) {
        Node formalParam = NodeUtil.getArgumentForFunction(fn, i);
        if (formalParam != null) {
          formalParam.detach();
          if (formalParam.isDefaultValue()) {
            // Drop the default value as we should only get here if the default value isn't going
            // to be used.
            checkState(!parameter.mayBeUndefined);
            formalParam = formalParam.getFirstChild().detach();
          }
        }

        addVariableToFunction(fn, formalParam, parameters.get(i).getArg());
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
    private boolean mayBeUndefined;

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

    public void setMayBeUndefined(boolean mayBeUndefined) {
      this.mayBeUndefined = mayBeUndefined;
    }

    public boolean mayBeUndefined() {
      return mayBeUndefined;
    }
  }

  private void addRestVariableToFunction(Node function, Node lhs, Node value) {
    checkState(lhs.getParent() == null);
    addVariableToFunction(function, lhs, value);
  }

  /**
   * Adds a variable to the top of a function block.
   * @param function A function node.
   * @param lhs The lhs expression.
   * @param value The initial value of the variable.
   */
  private void addVariableToFunction(Node function, @Nullable Node lhs, Node value) {
    checkState(value.getParent() == null);
    checkState(lhs == null || lhs.getParent() == null);
    Node block = NodeUtil.getFunctionBody(function);
    Node stmt;
    if (lhs != null) {
      stmt = NodeUtil.newVarNode(lhs, value);
    } else {
      stmt = IR.exprResult(value).useSourceInfoFrom(value);
    }

    // Insert the statement at the beginning of the function body, but after any
    // existing function declarations so that the tree stays normalized.
    Node insertionPoint = block.getFirstChild();
    while (insertionPoint != null && insertionPoint.isFunction()) {
      insertionPoint = insertionPoint.getNext();
    }
    if (insertionPoint == null) {
      block.addChildToBack(stmt);
    } else {
      block.addChildBefore(stmt, insertionPoint);
    }
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

  private void eliminateParamsAfter(Node fnNode, Node formal) {
    if (formal != null) {
      // Keep the args in the same order, do the last first.
      eliminateParamsAfter(fnNode, formal.getNext());
      formal.detach();
      Node stmt;
      if (formal.isRest()) {
        checkState(formal.getNext() == null);
        stmt = NodeUtil.newVarNode(formal.getFirstChild().detach(), IR.arraylit().srcref(formal));
      } else {
        if (formal.isDefaultValue()) {
          Node lhs = formal.getFirstChild().detach();
          Node value = formal.getLastChild().detach();
          stmt = NodeUtil.newVarNode(lhs, value);
        } else {
          stmt = IR.var(formal).useSourceInfoIfMissingFrom(formal);
        }
      }
      fnNode.getLastChild().addChildToFront(stmt);
      compiler.reportChangeToEnclosingScope(stmt);
    }
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
