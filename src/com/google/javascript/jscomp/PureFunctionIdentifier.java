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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.errorprone.annotations.DoNotCall;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.jscomp.CodingConvention.Cache;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Compiler pass that computes function purity and annotates invocation nodes with those purities.
 *
 * <p>A function is pure if it has no outside visible side effects, and the result of the
 * computation does not depend on external factors that are beyond the control of the application;
 * repeated calls to the function should return the same value as long as global state hasn't
 * changed.
 *
 * <p>`Date.now` is an example of a function that has no side effects but is not pure.
 *
 * <p>Functions are not tracked individually but rather in aggregate by their name. This is because
 * it's impossible to determine exactly which function named "foo" is being called at a particular
 * site. Therefore, if <em>any</em> function "foo" has a particular side-effect, <em>all</em>
 * invocations "foo" are assumed to trigger it.
 *
 * <p>This pass could be greatly improved by proper tracking of locals within function bodies. Every
 * instance of the call to {@link NodeUtil#evaluatesToLocalValue(Node)} and {@link
 * NodeUtil#allArgsUnescapedLocal(Node)} do not actually take into account local variables. They
 * only assume literals, primitives, and operations on primitives are local.
 *
 * @author johnlenz@google.com (John Lenz)
 * @author tdeegan@google.com (Thomas Deegan)
 */
class PureFunctionIdentifier implements CompilerPass {
  private final AbstractCompiler compiler;
  private final NameBasedDefinitionProvider definitionProvider;

  /**
   * Map of function names to the summary of the functions with that name.
   *
   * @see {@link AmbiguatedFunctionSummary}
   */
  private final Map<String, AmbiguatedFunctionSummary> summaryByName = new HashMap<>();

  /**
   * Mapping from function node to side effects for all names associated with that node.
   *
   * <p>This is a multimap because you can construct situations in which a function node has
   * multiple names, and therefore multiple associated side-effect infos. For example:
   *
   * <pre>
   *   // Not enough type information to collapse/disambiguate properties on "staticMethod".
   *   SomeClass.staticMethod = function anotherName() {};
   *   OtherClass.staticMethod = function() { global++; }
   * </pre>
   *
   * <p>In this situation we want to keep the side effects for "staticMethod" which are "global"
   * separate from "anotherName". Hence the function node should point to the {@link
   * AmbiguatedFunctionSummary} for both "staticMethod" and "anotherName".
   *
   * <p>We could instead store a map of FUNCTION nodes to names, and then join that with the name of
   * names to infos. However, since names are 1:1 with infos, it's more effecient to "pre-join" in
   * this way.
   */
  private final Multimap<Node, AmbiguatedFunctionSummary> summariesForAllNamesOfFunctionByNode;

  // List of all function call sites; used to iterate in markPureFunctionCalls.
  private final List<Node> allFunctionCalls;

  /**
   * A graph linking the summary of function callees to the summaries of their callers.
   *
   * <p>The edge values indicate the details of the invocation necessary to propagate function
   * purity from callee to caller.
   */
  private final LinkedDirectedGraph<AmbiguatedFunctionSummary, CallSitePropagationInfo>
      reverseCallGraph = LinkedDirectedGraph.createWithoutAnnotations();

  // Externs and ast tree root, for use in getDebugReport.  These two
  // fields are null until process is called.
  private Node externs;
  private Node root;

  public PureFunctionIdentifier(
      AbstractCompiler compiler, NameBasedDefinitionProvider definitionProvider) {
    this.compiler = checkNotNull(compiler);
    this.definitionProvider = definitionProvider;
    this.summariesForAllNamesOfFunctionByNode = ArrayListMultimap.create();
    this.allFunctionCalls = new ArrayList<>();
    this.externs = null;
    this.root = null;
  }

  @Override
  public void process(Node externsAst, Node srcAst) {
    checkState(
        externs == null && root == null,
        "PureFunctionIdentifier::process may only be called once per instance.");

    externs = externsAst;
    root = srcAst;

    buildReverseCallGraph();

    NodeTraversal.traverse(compiler, externs, new FunctionAnalyzer(true));
    NodeTraversal.traverse(compiler, root, new FunctionAnalyzer(false));

    propagateSideEffects();

    markPureFunctionCalls();
  }

  /**
   * Unwraps a complicated expression to reveal directly callable nodes that correspond to
   * definitions. For example: (a.c || b) or (x ? a.c : b) are turned into [a.c, b]. Since when you
   * call
   *
   * <pre>
   *   var result = (a.c || b)(some, parameters);
   * </pre>
   *
   * either a.c or b are called.
   *
   * @param exp A possibly complicated expression.
   * @return A list of GET_PROP NAME and function expression nodes (all of which can be called). Or
   *     null if any of the callable nodes are of an unsupported type. e.g. x['asdf'](param);
   */
  @Nullable
  private static Iterable<Node> unwrapCallableExpression(Node exp) {
    switch (exp.getToken()) {
      case GETPROP:
        String propName = exp.getLastChild().getString();
        if (propName.equals("apply") || propName.equals("call")) {
          return unwrapCallableExpression(exp.getFirstChild());
        }
        return ImmutableList.of(exp);
      case FUNCTION:
      case NAME:
        return ImmutableList.of(exp);
      case OR:
      case HOOK:
        Node firstVal;
        if (exp.isHook()) {
          firstVal = exp.getSecondChild();
        } else {
          firstVal = exp.getFirstChild();
        }
        Iterable<Node> firstCallable = unwrapCallableExpression(firstVal);
        Iterable<Node> secondCallable = unwrapCallableExpression(firstVal.getNext());

        if (firstCallable == null || secondCallable == null) {
          return null;
        }
        return Iterables.concat(firstCallable, secondCallable);
      default:
        return null; // Unsupported call type.
    }
  }

  private static boolean isSupportedFunctionDefinition(@Nullable Node definitionRValue) {
    if (definitionRValue == null) {
      return false;
    }
    switch (definitionRValue.getToken()) {
      case FUNCTION:
        return true;
      case HOOK:
        return isSupportedFunctionDefinition(definitionRValue.getSecondChild())
            && isSupportedFunctionDefinition(definitionRValue.getLastChild());
      default:
        return false;
    }
  }

  private Iterable<Node> getGoogCacheCallableExpression(Cache cacheCall) {
    checkNotNull(cacheCall);

    if (cacheCall.keyFn == null) {
      return unwrapCallableExpression(cacheCall.valueFn);
    }
    return Iterables.concat(
        unwrapCallableExpression(cacheCall.valueFn), unwrapCallableExpression(cacheCall.keyFn));
  }

  @Nullable
  private List<AmbiguatedFunctionSummary> getSummariesForCallee(Node call) {
    checkArgument(call.isCall() || call.isNew(), call);

    Iterable<Node> expanded;
    Cache cacheCall = compiler.getCodingConvention().describeCachingCall(call);
    if (cacheCall != null) {
      expanded = getGoogCacheCallableExpression(cacheCall);
    } else {
      expanded = unwrapCallableExpression(call.getFirstChild());
    }
    if (expanded == null) {
      return null;
    }
    List<AmbiguatedFunctionSummary> results = new ArrayList<>();
    for (Node expression : expanded) {
      if (NodeUtil.isFunctionExpression(expression)) {
        // isExtern is false in the call to the constructor for the
        // FunctionExpressionDefinition below because we know that
        // getFunctionDefinitions() will only be called on the first
        // child of a call and thus the function expression
        // definition will never be an extern.
        results.addAll(summariesForAllNamesOfFunctionByNode.get(expression));
        continue;
      }

      String name = DefinitionsRemover.Definition.getSimplifiedName(expression);
      AmbiguatedFunctionSummary info = null;
      if (name != null) {
        info = summaryByName.get(name);
      }

      if (info != null) {
        results.add(info);
      } else {
        return null;
      }
    }
    return results;
  }

  /**
   * Construct an "ambiguated" reverse call graph where all functions of the same name are unified
   * to a single node, and edges point from callee to caller.
   *
   * <p>When propagating side effects we construct a graph from every function definition A to every
   * function definition B that calls A(). Since the definition provider cannot always provide a
   * unique definition for a name, there may be many possible definitions for a given call site. In
   * the case where multiple defs share the same node in the graph.
   *
   * <p>We need to build the map {@link PureFunctionIdentifier#summaryByName} to get a reference to
   * the side effects for a call and we need the map {@link #summariesForAllNamesOfFunctionByNode}
   * to get a reference to the side effects for a given function node.
   */
  private void buildReverseCallGraph() {
    final AmbiguatedFunctionSummary unknownDefinitionFunction =
        AmbiguatedFunctionSummary.createInGraph(reverseCallGraph, "<unknown>");
    unknownDefinitionFunction.setMutatesGlobalState();
    unknownDefinitionFunction.setFunctionThrows();
    unknownDefinitionFunction.setEscapedReturn();

    for (DefinitionSite site : definitionProvider.getDefinitionSites()) {
      Definition definition = site.definition;
      if (definition.getLValue() != null) {
        Node getOrName = definition.getLValue();
        checkArgument(getOrName.isGetProp() || getOrName.isName(), getOrName);
        String name = DefinitionsRemover.Definition.getSimplifiedName(getOrName);
        checkNotNull(name);
        if (isSupportedFunctionDefinition(definition.getRValue())) {
          addSupportedDefinition(site, name);
        } else {
          // Unsupported function definition. Mark a global side effect here since we don't
          // actually know anything about what's being defined.
          AmbiguatedFunctionSummary info = summaryByName.get(name);
          if (info != null) {
            info.setMutatesGlobalState();
            info.setFunctionThrows();
            info.setEscapedReturn();
          } else {
            summaryByName.put(name, unknownDefinitionFunction);
          }
        }
      }
    }
  }

  /**
   * Add the definition to the {@link #reverseCallGraph} as a {@link AmbiguatedFunctionSummary} node
   * or link it to the existing {@link AmbiguatedFunctionSummary} node if there is already a
   * function with the same definition name.
   */
  private void addSupportedDefinition(DefinitionSite definitionSite, String name) {
    for (Node function : unwrapCallableExpression(definitionSite.definition.getRValue())) {
      // A function may have multiple definitions.
      // Link this function definition to the existing summary node.
      AmbiguatedFunctionSummary summary = summaryByName.get(name);
      if (summary == null) {
        // Need to create a function info node.
        summary = AmbiguatedFunctionSummary.createInGraph(reverseCallGraph, name);
        // Keep track of this so that later functions of the same name can point to the same
        // AmbiguatedFunctionSummary.
        summaryByName.put(name, summary);
      }
      summariesForAllNamesOfFunctionByNode.put(function, summary);
      if (definitionSite.inExterns) {
        // Externs have their side effects computed here, otherwise in FunctionAnalyzer.
        summary.updateSideEffectsFromExtern(function, compiler);
      }
    }
  }

  /**
   * Propagate side effect information in {@link #reverseCallGraph} from callees to callers.
   *
   * <p>This is an iterative process executed until a fixed point, where no caller summary would be
   * given new side-effects from from any callee summary, is reached.
   */
  private void propagateSideEffects() {
    FixedPointGraphTraversal.newTraversal(
            (AmbiguatedFunctionSummary source,
                CallSitePropagationInfo edge,
                AmbiguatedFunctionSummary destination) -> edge.propagate(source, destination))
        .computeFixedPoint(reverseCallGraph);
  }

  /** Set no side effect property at pure-function call sites. */
  private void markPureFunctionCalls() {
    for (Node callNode : allFunctionCalls) {
      List<AmbiguatedFunctionSummary> calleeSummaries = getSummariesForCallee(callNode);
      // Default to side effects, non-local results
      Node.SideEffectFlags flags = new Node.SideEffectFlags();
      if (calleeSummaries == null) {
        flags.setMutatesGlobalState();
        flags.setThrows();
        flags.setReturnsTainted();
      } else {
        flags.clearAllFlags();
        for (AmbiguatedFunctionSummary calleeSummary : calleeSummaries) {
          checkNotNull(calleeSummary);
          if (calleeSummary.mutatesGlobalState()) {
            flags.setMutatesGlobalState();
          }

          if (calleeSummary.mutatesArguments()) {
            flags.setMutatesArguments();
          }

          if (calleeSummary.functionThrows()) {
            flags.setThrows();
          }

          if (callNode.isCall()) {
            if (calleeSummary.mutatesThis()) {
              // A FunctionInfo for "f" maps to both "f()" and "f.call()" nodes.
              if (isCallOrApply(callNode)) {
                flags.setMutatesArguments();
              } else {
                flags.setMutatesThis();
              }
            }
          }

          if (calleeSummary.escapedReturn()) {
            flags.setReturnsTainted();
          }
        }
      }

      // Handle special cases (Math, RegExp)
      if (callNode.isCall()) {
        if (!NodeUtil.functionCallHasSideEffects(callNode, compiler)) {
          flags.clearSideEffectFlags();
        }
      } else if (callNode.isNew()) {
        // Handle known cases now (Object, Date, RegExp, etc)
        if (!NodeUtil.constructorCallHasSideEffects(callNode)) {
          flags.clearSideEffectFlags();
        }
      }

      int newSideEffectFlags = flags.valueOf();
      if (callNode.getSideEffectFlags() != newSideEffectFlags) {
        callNode.setSideEffectFlags(newSideEffectFlags);
        compiler.reportChangeToEnclosingScope(callNode);
      }
    }
  }

  private static final Predicate<Node> RHS_IS_ALWAYS_LOCAL = lhs -> true;
  private static final Predicate<Node> RHS_IS_NEVER_LOCAL = lhs -> false;
  private static final Predicate<Node> FIND_RHS_AND_CHECK_FOR_LOCAL_VALUE = lhs -> {
    Node rhs = NodeUtil.getRValueOfLValue(lhs);
    return rhs == null || NodeUtil.evaluatesToLocalValue(rhs);
  };


  /**
   * Gather list of functions, functions with @nosideeffects annotations, call sites, and functions
   * that may mutate variables not defined in the local scope.
   */
  private class FunctionAnalyzer implements ScopedCallback {
    private final SetMultimap<Node, Var> blacklistedVarsByFunction = HashMultimap.create();
    private final SetMultimap<Node, Var> taintedVarsByFunction = HashMultimap.create();

    private final boolean inExterns;

    FunctionAnalyzer(boolean inExterns) {
      this.inExterns = inExterns;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal traversal, Node node, Node parent) {
      // Functions need to be processed as part of pre-traversal so that an entry for the function
      // exists in the summariesForAllNamesOfFunctionByNode map when processing assignments and
      // calls within the body.
      if (node.isFunction() && !summariesForAllNamesOfFunctionByNode.containsKey(node)) {
        // This function was not part of a definition which is why it was not created by
        // {@link buildReverseCallGraph}. For example, an anonymous function.
        AmbiguatedFunctionSummary summary =
            AmbiguatedFunctionSummary.createInGraph(reverseCallGraph, "<anonymous>");
        summariesForAllNamesOfFunctionByNode.put(node, summary);
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      if (inExterns) {
        return;
      }

      if (!NodeUtil.nodeTypeMayHaveSideEffects(node, compiler) && !node.isReturn()) {
        return;
      }

      if (NodeUtil.isCallOrNew(node)) {
        allFunctionCalls.add(node);
      }

      Scope containerScope = traversal.getScope().getClosestContainerScope();
      if (!containerScope.isFunctionScope()) {
        // We only need to look at nodes in function scopes.
        return;
      }
      Node enclosingFunction = containerScope.getRootNode();

      for (AmbiguatedFunctionSummary encloserSummary :
          summariesForAllNamesOfFunctionByNode.get(enclosingFunction)) {
        checkNotNull(encloserSummary);
        updateSideEffectsForNode(encloserSummary, traversal, node, enclosingFunction);
      }
    }

    public void updateSideEffectsForNode(
        AmbiguatedFunctionSummary encloserSummary,
        NodeTraversal traversal,
        Node node,
        Node enclosingFunction) {
      switch (node.getToken()) {
        case ASSIGN:
          // e.g.
          // lhs = rhs;
          // ({x, y} = object);
          visitLhsNodes(
              encloserSummary,
              traversal.getScope(),
              enclosingFunction,
              NodeUtil.findLhsNodesInNode(node),
              FIND_RHS_AND_CHECK_FOR_LOCAL_VALUE);
          break;

        case INC: // e.g. x++;
        case DEC:
        case DELPROP:
          visitLhsNodes(
              encloserSummary,
              traversal.getScope(),
              enclosingFunction,
              ImmutableList.of(node.getOnlyChild()),
              // The value assigned by a unary op is always local.
              RHS_IS_ALWAYS_LOCAL);
          break;

        case FOR_OF:
          // e.g.
          // for (const {prop1, prop2} of iterable) {...}
          // for ({prop1: x.p1, prop2: x.p2} of iterable) {...}
          //
          // TODO(bradfordcsmith): Possibly we should try to determine whether the iteration itself
          //     could have side effects.
          visitLhsNodes(
              encloserSummary,
              traversal.getScope(),
              enclosingFunction,
              NodeUtil.findLhsNodesInNode(node),
              // The RHS of a for-of must always be an iterable, making it a container, so we can't
              // consider its contents to be local
              RHS_IS_NEVER_LOCAL);
          break;

        case FOR_IN:
          // e.g.
          // for (prop in obj) {...}
          // Also this, though not very useful or readable.
          // for ([char1, char2, ...x.rest] in obj) {...}
          visitLhsNodes(
              encloserSummary,
              traversal.getScope(),
              enclosingFunction,
              NodeUtil.findLhsNodesInNode(node),
              // A for-in always assigns a string, which is a local value by definition.
              RHS_IS_ALWAYS_LOCAL);
          break;

        case CALL:
        case NEW:
          visitCall(encloserSummary, node);
          break;

        case NAME:
          // Variable definition are not side effects. Check that the name appears in the context of
          // a variable declaration.
          checkArgument(NodeUtil.isNameDeclaration(node.getParent()), node.getParent());
          Node value = node.getFirstChild();
          // Assignment to local, if the value isn't a safe local value,
          // new object creation or literal or known primitive result
          // value, add it to the local blacklist.
          if (value != null && !NodeUtil.evaluatesToLocalValue(value)) {
            Scope scope = traversal.getScope();
            Var var = scope.getVar(node.getString());
            blacklistedVarsByFunction.put(enclosingFunction, var);
          }
          break;

        case THROW:
          encloserSummary.setFunctionThrows();
          break;

        case RETURN:
          if (node.hasChildren() && !NodeUtil.evaluatesToLocalValue(node.getFirstChild())) {
            encloserSummary.setEscapedReturn();
          }
          break;

        case YIELD: // 'yield' throws if the caller calls `.throw` on the generator object.
        case AWAIT: // 'await' throws if the promise it's waiting on is rejected.
          encloserSummary.setFunctionThrows();
          break;

        case FOR_AWAIT_OF:
        case REST:
        case SPREAD:
          break; // TODO(b/123649765): Actually check for related side-effects.

        default:
          if (NodeUtil.isCompoundAssignmentOp(node)) {
            // e.g.
            // x += 3;
            visitLhsNodes(
                encloserSummary,
                traversal.getScope(),
                enclosingFunction,
                ImmutableList.of(node.getFirstChild()),
                // The update assignments (e.g. `+=) always assign primitive, and therefore local,
                // values.
                RHS_IS_ALWAYS_LOCAL);
            break;
          }

          throw new IllegalArgumentException("Unhandled side effect node type " + node);
      }
    }

    @Override
    public void enterScope(NodeTraversal t) {
      // Nothing to do.
    }

    @Override
    public void exitScope(NodeTraversal t) {
      Scope closestContainerScope = t.getScope().getClosestContainerScope();
      if (!closestContainerScope.isFunctionScope()) {
        // Only functions and the scopes within them are of interest to us.
        return;
      }
      Node function = closestContainerScope.getRootNode();

      // Handle deferred local variable modifications:
      for (AmbiguatedFunctionSummary sideEffectInfo :
          summariesForAllNamesOfFunctionByNode.get(function)) {
        checkNotNull(sideEffectInfo, "%s has no side effect info.", function);

        if (sideEffectInfo.mutatesGlobalState()) {
          continue;
        }

        for (Var v : t.getScope().getVarIterable()) {
          if (v.isParam()
              && !blacklistedVarsByFunction.containsEntry(function, v)
              && taintedVarsByFunction.containsEntry(function, v)) {
            sideEffectInfo.setMutatesArguments();
            continue;
          }

          boolean localVar = false;
          // Parameters and catch values can come from other scopes.
          if (!v.isParam() && !v.isCatch()) {
            // TODO(johnlenz): create a useful parameter list
            // sideEffectInfo.addKnownLocal(v.getName());
            localVar = true;
          }

          // Take care of locals that might have been tainted.
          if (!localVar || blacklistedVarsByFunction.containsEntry(function, v)) {
            if (taintedVarsByFunction.containsEntry(function, v)) {
              // If the function has global side-effects
              // don't bother with the local side-effects.
              sideEffectInfo.setMutatesGlobalState();
              break;
            }
          }
        }
      }

      // Clean up memory after exiting out of the function scope where we will no longer need these.
      if (t.getScopeRoot().isFunction()) {
        blacklistedVarsByFunction.removeAll(function);
        taintedVarsByFunction.removeAll(function);
      }
    }

    private boolean isVarDeclaredInSameContainerScope(@Nullable Var v, Scope scope) {
      return v != null && v.scope.hasSameContainerScope(scope);
    }

    /**
     * Record information about the side effects caused by assigning a value to a given LHS.
     *
     * <p>If the operation modifies this or taints global state, mark the enclosing function as
     * having those side effects.
     *
     * @param sideEffectInfo Function side effect record to be updated
     * @param scope variable scope in which the variable assignment occurs
     * @param enclosingFunction FUNCTION node for the enclosing function
     * @param lhsNodes LHS nodes that are all assigned values by a given parent node
     * @param hasLocalRhs Predicate indicating whether a given LHS is being assigned a local value
     */
    private void visitLhsNodes(
        AmbiguatedFunctionSummary sideEffectInfo,
        Scope scope,
        Node enclosingFunction,
        Iterable<Node> lhsNodes,
        Predicate<Node> hasLocalRhs) {
      for (Node lhs : lhsNodes) {
        if (NodeUtil.isGet(lhs)) {
          if (lhs.getFirstChild().isThis()) {
            sideEffectInfo.setMutatesThis();
          } else {
            Node objectNode = lhs.getFirstChild();
            if (objectNode.isName()) {
              Var var = scope.getVar(objectNode.getString());
              if (isVarDeclaredInSameContainerScope(var, scope)) {
                // Maybe a local object modification.  We won't know for sure until
                // we exit the scope and can validate the value of the local.
                taintedVarsByFunction.put(enclosingFunction, var);
              } else {
                sideEffectInfo.setMutatesGlobalState();
              }
            } else {
              // Don't track multi level locals: local.prop.prop2++;
              sideEffectInfo.setMutatesGlobalState();
            }
          }
        } else {
          checkState(lhs.isName(), lhs);
          Var var = scope.getVar(lhs.getString());
          if (isVarDeclaredInSameContainerScope(var, scope)) {
            if (!hasLocalRhs.test(lhs)) {
              // Assigned value is not guaranteed to be a local value,
              // so if we see any property assignments on this variable,
              // they could be tainting a non-local value.
              blacklistedVarsByFunction.put(enclosingFunction, var);
            }
          } else {
            sideEffectInfo.setMutatesGlobalState();
          }
        }
      }
    }

    /** Record information about a call site. */
    private void visitCall(AmbiguatedFunctionSummary callerInfo, Node invocation) {
      // Handle special cases (Math, RegExp)
      // TODO: This logic can probably be replaced with @nosideeffects annotations in externs.
      if (invocation.isCall() && !NodeUtil.functionCallHasSideEffects(invocation, compiler)) {
        return;
      }

      // Handle known cases now (Object, Date, RegExp, etc)
      if (invocation.isNew() && !NodeUtil.constructorCallHasSideEffects(invocation)) {
        return;
      }

      List<AmbiguatedFunctionSummary> calleeSummaries = getSummariesForCallee(invocation);
      if (calleeSummaries == null) {
        callerInfo.setMutatesGlobalState();
        callerInfo.setFunctionThrows();
        return;
      }

      for (AmbiguatedFunctionSummary calleeInfo : calleeSummaries) {
        CallSitePropagationInfo edge = CallSitePropagationInfo.computePropagationType(invocation);
        reverseCallGraph.connect(calleeInfo.graphNode, edge, callerInfo.graphNode);
      }
    }
  }

  private static boolean isCallOrApply(Node callSite) {
    return NodeUtil.isFunctionObjectCall(callSite) || NodeUtil.isFunctionObjectApply(callSite);
  }

  /**
   * This class stores all the information about a call site needed to propagate side effects from
   * one instance of {@link AmbiguatedFunctionSummary} to another.
   */
  @Immutable
  private static class CallSitePropagationInfo {

    private CallSitePropagationInfo(
        boolean allArgsUnescapedLocal, boolean calleeThisEqualsCallerThis, Token callType) {
      checkArgument(callType == Token.CALL || callType == Token.NEW);
      this.allArgsUnescapedLocal = allArgsUnescapedLocal;
      this.calleeThisEqualsCallerThis = calleeThisEqualsCallerThis;
      this.callType = callType;
    }

    // If all the arguments values are local to the scope in which the call site occurs.
    private final boolean allArgsUnescapedLocal;
    /**
     * If you call a function with apply or call, one of the arguments at the call site will be used
     * as 'this' inside the implementation. If this is pass into apply like so: function.apply(this,
     * ...) then 'this' in the caller is tainted.
     */
    private final boolean calleeThisEqualsCallerThis;
    // Whether this represents CALL (not a NEW node).
    private final Token callType;

    /**
     * Propagate the side effects from the callee to the caller.
     *
     * @param callee propagate from
     * @param caller propagate to
     * @return Returns true if the propagation changed the side effects on the caller.
     */
    boolean propagate(AmbiguatedFunctionSummary callee, AmbiguatedFunctionSummary caller) {
      CallSitePropagationInfo propagationType = this;
      boolean changed = false;
      // If the callee modifies global state then so does that caller.
      if (callee.mutatesGlobalState() && !caller.mutatesGlobalState()) {
        caller.setMutatesGlobalState();
        changed = true;
      }
      // If the callee throws an exception then so does the caller.
      if (callee.functionThrows() && !caller.functionThrows()) {
        caller.setFunctionThrows();
        changed = true;
      }
      // If the callee mutates its input arguments and the arguments escape the caller then it has
      // unbounded side effects.
      if (callee.mutatesArguments()
          && !propagationType.allArgsUnescapedLocal
          && !caller.mutatesGlobalState()) {
        caller.setMutatesGlobalState();
        changed = true;
      }
      if (callee.mutatesThis() && propagationType.calleeThisEqualsCallerThis) {
        if (!caller.mutatesThis()) {
          caller.setMutatesThis();
          changed = true;
        }
      } else if (callee.mutatesThis() && propagationType.callType != Token.NEW) {
        // NEW invocations of a constructor that modifies "this" don't cause side effects.
        if (!caller.mutatesGlobalState()) {
          caller.setMutatesGlobalState();
          changed = true;
        }
      }
      return changed;
    }

    static CallSitePropagationInfo computePropagationType(Node callSite) {
      checkArgument(callSite.isCall() || callSite.isNew());

      boolean thisIsOuterThis = false;
      if (callSite.isCall()) {
        // Side effects only propagate via regular calls.
        // Calling a constructor that modifies "this" has no side effects.
        // Notice that we're using "mutatesThis" from the callee
        // summary. If the call site is actually a .call or .apply, then
        // the "this" is going to be one of its arguments.
        boolean isCallOrApply = isCallOrApply(callSite);
        Node objectNode = isCallOrApply ? callSite.getSecondChild() : callSite.getFirstFirstChild();
        if (objectNode != null && objectNode.isName() && !isCallOrApply) {
          // Exclude ".call" and ".apply" as the value may still be
          // null or undefined. We don't need to worry about this with a
          // direct method call because null and undefined don't have any
          // properties.

          // TODO(nicksantos): Turn this back on when locals-tracking
          // is fixed. See testLocalizedSideEffects11.
          //if (!caller.knownLocals.contains(name)) {
          //}
        } else if (objectNode != null && objectNode.isThis()) {
          thisIsOuterThis = true;
        }
      }

      boolean argsUnescapedLocal = NodeUtil.allArgsUnescapedLocal(callSite);
      return new CallSitePropagationInfo(argsUnescapedLocal, thisIsOuterThis, callSite.getToken());
    }
  }

  /**
   * A summary for the set of functions that share a particular name.
   *
   * <p>Side-effects of the functions are the most significant aspect of this summary. Because the
   * functions are "ambiguated", the recorded side-effects are the union of all side effects
   * detected in any member of the set.
   *
   * <p>Name in this context refers to a short name, not a qualified name; only the last segment of
   * a qualified name is used.
   */
  private static final class AmbiguatedFunctionSummary {

    // Side effect types:
    private static final int THROWS = 1 << 1;
    private static final int MUTATES_GLOBAL_STATE = 1 << 2;
    private static final int MUTATES_THIS = 1 << 3;
    private static final int MUTATES_ARGUMENTS = 1 << 4;
    // Function metatdata
    private static final int ESCAPED_RETURN = 1 << 5;

    // The name shared by the set of functions that defined this summary.
    private final String name;
    // The node holding this summary in the reverse call graph.
    private final DiGraphNode<AmbiguatedFunctionSummary, CallSitePropagationInfo> graphNode;
    // The side effect flags for this set of functions.
    // TODO(nickreid): Replace this with a `Node.SideEffectFlags`.
    private int bitmask = 0;

    /** Adds a new summary node to {@code graph}, storing the node and returning the summary. */
    static AmbiguatedFunctionSummary createInGraph(
        DiGraph<AmbiguatedFunctionSummary, CallSitePropagationInfo> graph, String name) {
      return new AmbiguatedFunctionSummary(graph, name);
    }

    private AmbiguatedFunctionSummary(
        DiGraph<AmbiguatedFunctionSummary, CallSitePropagationInfo> graph, String name) {
      this.name = checkNotNull(name);
      this.graphNode = graph.createDirectedGraphNode(this);
    }

    private void setMask(int mask) {
      bitmask |= mask;
    }

    private boolean getMask(int mask) {
      return (bitmask & mask) != 0;
    }

    boolean mutatesThis() {
      return getMask(MUTATES_THIS);
    }

    /** Marks the function as having "modifies this" side effects. */
    void setMutatesThis() {
      setMask(MUTATES_THIS);
    }

    /**
     * Returns whether the function returns something that is not affected by global state.
     *
     * <p>In the current implementation, this is only true if the return value is a literal or
     * primitive since locals are not tracked correctly.
     */
    boolean escapedReturn() {
      return getMask(ESCAPED_RETURN);
    }

    /** Marks the function as having non-local return result. */
    void setEscapedReturn() {
      setMask(ESCAPED_RETURN);
    }

    /** Returns true if function has an explicit "throw". */
    boolean functionThrows() {
      return getMask(THROWS);
    }

    /** Marks the function as having "throw" side effects. */
    void setFunctionThrows() {
      setMask(THROWS);
    }

    /** Returns true if function mutates global state. */
    boolean mutatesGlobalState() {
      return getMask(MUTATES_GLOBAL_STATE);
    }

    /** Marks the function as having "modifies globals" side effects. */
    void setMutatesGlobalState() {
      setMask(MUTATES_GLOBAL_STATE);
    }

    /** Returns true if function mutates its arguments. */
    boolean mutatesArguments() {
      return getMask(MUTATES_GLOBAL_STATE | MUTATES_ARGUMENTS);
    }

    /** Marks the function as having "modifies arguments" side effects. */
    void setMutatesArguments() {
      setMask(MUTATES_ARGUMENTS);
    }

    @Override
    @DoNotCall // For debugging only.
    public String toString() {
      return MoreObjects.toStringHelper(getClass())
          .add("name", name)
          .add("graphNode", graphNode)
          .add("sideEffects", sideEffectsToString())
          .toString();
    }

    private String sideEffectsToString() {
      List<String> status = new ArrayList<>();
      if (mutatesThis()) {
        status.add("this");
      }

      if (mutatesGlobalState()) {
        status.add("global");
      }

      if (mutatesArguments()) {
        status.add("args");
      }

      if (escapedReturn()) {
        status.add("return");
      }

      if (functionThrows()) {
        status.add("throw");
      }

      return status.toString();
    }

    /** Update function for @nosideeffects annotations. */
    private void updateSideEffectsFromExtern(Node externFunction, AbstractCompiler compiler) {
      checkArgument(externFunction.isFunction());
      checkArgument(externFunction.isFromExterns());

      JSDocInfo info = NodeUtil.getBestJSDocInfo(externFunction);
      // Handle externs.
      JSType typei = externFunction.getJSType();
      FunctionType functionType = typei == null ? null : typei.toMaybeFunctionType();
      if (functionType == null) {
        // Assume extern functions return tainted values when we have no type info to say otherwise.
        setEscapedReturn();
      } else {
        JSType retType = functionType.getReturnType();
        if (!PureFunctionIdentifier.isLocalValueType(retType, compiler)) {
          setEscapedReturn();
        }
      }

      if (info == null) {
        // We don't know anything about this function so we assume it has side effects.
        setMutatesGlobalState();
        setFunctionThrows();
      } else {
        if (info.modifiesThis()) {
          setMutatesThis();
        } else if (info.hasSideEffectsArgumentsAnnotation()) {
          setMutatesArguments();
        } else if (!info.getThrownTypes().isEmpty()) {
          setFunctionThrows();
        } else if (info.isNoSideEffects()) {
          // Do nothing.
        } else {
          setMutatesGlobalState();
        }
      }
    }
  }

  /**
   * TODO: This could be greatly improved.
   *
   * @return Whether the jstype is something known to be a local value.
   */
  private static boolean isLocalValueType(JSType typei, AbstractCompiler compiler) {
    checkNotNull(typei);
    JSType nativeObj = compiler.getTypeRegistry().getNativeType(JSTypeNative.OBJECT_TYPE);
    JSType subtype = typei.meetWith(nativeObj);
    // If the type includes anything related to a object type, don't assume
    // anything about the locality of the value.
    return subtype.isEmptyType();
  }

  /**
   * A compiler pass that constructs a reference graph and drives the PureFunctionIdentifier across
   * it.
   */
  static class Driver implements CompilerPass {
    private final AbstractCompiler compiler;

    Driver(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      NameBasedDefinitionProvider defFinder = new NameBasedDefinitionProvider(compiler, true);
      defFinder.process(externs, root);

      PureFunctionIdentifier pureFunctionIdentifier =
          new PureFunctionIdentifier(compiler, defFinder);
      pureFunctionIdentifier.process(externs, root);
    }
  }
}
