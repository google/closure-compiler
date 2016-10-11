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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;
import com.google.javascript.jscomp.CodingConvention.Cache;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiler pass that computes function purity. A function is pure if it has no outside visible side
 * effects, and the result of the computation does not depend on external factors that are beyond
 * the control of the application; repeated calls to the function should return the same value as
 * long as global state hasn't changed.
 *
 * <p>Date.now is an example of a function that has no side effects but is not pure.
 *
 * <p>TODO: This pass could be greatly improved by proper tracking of locals within function bodies.
 * Every instance of the call to {@link NodeUtil#evaluatesToLocalValue(Node)} and {@link
 * NodeUtil#allArgsUnescapedLocal(Node)} do not actually take into account local variables. They
 * only assume literals, primatives, and operations on primatives are local.
 *
 * @author johnlenz@google.com (John Lenz)
 * @author tdeegan@google.com (Thomas Deegan)
 *     <p>We will prevail, in peace and freedom from fear, and in true health, through the purity
 *     and essence of our natural... fluids. - General Turgidson
 */
class PureFunctionIdentifier implements CompilerPass {
  private final AbstractCompiler compiler;
  private final DefinitionProvider definitionProvider;

  // Function node -> function side effects map
  private final Multimap<Node, FunctionInformation> functionSideEffectMap;

  /** Map of function names to side effect gathering representative nodes */
  private final Map<String, FunctionInformation> functionInfoByName = new HashMap<>();

  // List of all function call sites; used to iterate in markPureFunctionCalls.
  private final List<Node> allFunctionCalls;

  private final LinkedDirectedGraph<FunctionInformation, CallSitePropagationInfo> sideEffectGraph =
      LinkedDirectedGraph.createWithoutAnnotations();

  // Externs and ast tree root, for use in getDebugReport.  These two
  // fields are null until process is called.
  private Node externs;
  private Node root;

  public PureFunctionIdentifier(AbstractCompiler compiler, DefinitionProvider definitionProvider) {
    this.compiler = Preconditions.checkNotNull(compiler);
    this.definitionProvider = definitionProvider;
    this.functionSideEffectMap = ArrayListMultimap.create();
    this.allFunctionCalls = new ArrayList<>();
    this.externs = null;
    this.root = null;
  }

  @Override
  public void process(Node externsAst, Node srcAst) {
    Preconditions.checkState(
        externs == null && root == null,
        "It is illegal to call PureFunctionIdentifier.process  twice the same instance.  Please "
            + " use a new PureFunctionIdentifier instance each time.");

    externs = externsAst;
    root = srcAst;

    buildGraph();

    NodeTraversal.traverseEs6(compiler, externs, new FunctionAnalyzer(true));
    NodeTraversal.traverseEs6(compiler, root, new FunctionAnalyzer(false));

    propagateSideEffects();

    markPureFunctionCalls();
  }

  /**
   * Compute debug report that includes:
   *  - List of all pure functions.
   *  - Reasons we think the remaining functions have side effects.
   */
  @VisibleForTesting
  String getDebugReport() {
    Preconditions.checkNotNull(externs);
    Preconditions.checkNotNull(root);

    StringBuilder sb = new StringBuilder();

    FunctionNames functionNames = new FunctionNames(compiler);
    functionNames.process(null, externs);
    functionNames.process(null, root);

    for (Node call : allFunctionCalls) {
      sb.append("  ");
      Iterable<Node> expanded = unwrapCallableExpression(call.getFirstChild());
      if (expanded != null) {
        for (Node comp : expanded) {
          String name = NameBasedDefinitionProvider.getSimplifiedName(comp);
          sb.append(name).append("|");
        }
      } else {
        sb.append("<cant expand>");
      }

      sb.append(" ")
          .append(new Node.SideEffectFlags(call.getSideEffectFlags()))
          .append(" from: ")
          .append(call.getSourceFileName())
          .append("\n");
    }
    return sb.toString();
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

  private static boolean isSupportedFunctionDefinition(Node definitionRValue) {
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
    Preconditions.checkNotNull(cacheCall);

    if (cacheCall.keyFn == null) {
      return unwrapCallableExpression(cacheCall.valueFn);
    }
    return Iterables.concat(
        unwrapCallableExpression(cacheCall.valueFn), unwrapCallableExpression(cacheCall.keyFn));
  }

  private List<FunctionInformation> getSideEffectsForCall(Node call) {
    Preconditions.checkArgument(call.isCall() || call.isNew());

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
    List<FunctionInformation> results = new ArrayList<>();
    for (Node expression : expanded) {
      if (NodeUtil.isFunctionExpression(expression)) {
        // isExtern is false in the call to the constructor for the
        // FunctionExpressionDefinition below because we know that
        // getFunctionDefinitions() will only be called on the first
        // child of a call and thus the function expression
        // definition will never be an extern.
        results.addAll(Preconditions.checkNotNull(functionSideEffectMap.get(expression)));
        continue;
      }

      String name = NameBasedDefinitionProvider.getSimplifiedName(expression);
      if (name != null && functionInfoByName.containsKey(name)) {
        results.add(functionInfoByName.get(name));
      } else {
        return null;
      }
    }
    return results;
  }

  /**
   * When propagating side effects we construct a graph from every function definition A to every
   * function definition B that calls A(). Since the definition provider cannot always provide a
   * unique defintion for a name, there may be many possible definitions for a given call site. In
   * the case where multiple defs share the same node in the graph.
   *
   * <p>We need to build the map {@link PureFunctionIdentifier#functionInfoByName} to get a
   * reference to the side effects for a call and we need the map {@link
   * PureFunctionIdentifier#functionSideEffectMap} to get a reference to the side effects for a
   * given function node.
   */
  private void buildGraph() {
    final FunctionInformation unknownDefinitionFunction = new FunctionInformation();
    unknownDefinitionFunction.setTaintsGlobalState();
    unknownDefinitionFunction.setFunctionThrows();
    unknownDefinitionFunction.setTaintsReturn();
    unknownDefinitionFunction.graphNode = sideEffectGraph.createNode(unknownDefinitionFunction);
    for (DefinitionSite site : definitionProvider.getDefinitionSites()) {
      Definition definition = site.definition;
      if (definition.getLValue() != null) {
        Node getOrName = definition.getLValue();
        Preconditions.checkArgument(getOrName.isGetProp() || getOrName.isName(), getOrName);
        String name = NameBasedDefinitionProvider.getSimplifiedName(getOrName);
        Preconditions.checkNotNull(name);
        if (isSupportedFunctionDefinition(definition.getRValue())) {
          addSupportedDefinition(site, name);
        } else {
          // Unsupported function definition. Mark a global side effect here since we don't
          // actually know anything about what's being defined.
          if (functionInfoByName.containsKey(name)) {
            functionInfoByName.get(name).setTaintsGlobalState();
            functionInfoByName.get(name).setFunctionThrows();
            functionInfoByName.get(name).setTaintsReturn();
          } else {
            functionInfoByName.put(name, unknownDefinitionFunction);
          }
        }
      }
    }
  }

  /**
   * Add the definition to the {@link PureFunctionIdentifier#sideEffectGraph} as a
   * FunctionInformation node or link it to the existing functionInformation node if there is
   * already a function with the same definition name.
   */
  private void addSupportedDefinition(DefinitionSite definitionSite, String name) {
    for (Node function : unwrapCallableExpression(definitionSite.definition.getRValue())) {
      FunctionInformation functionInfo;
      if (functionInfoByName.containsKey(name)) {
        // This is a function name with multiple definitions!
        // Here we link this function definition to the existing FunctionInfo node.
        functionInfo = functionInfoByName.get(name);
      } else {
        // Need to create a function info node.
        functionInfo = new FunctionInformation();
        functionInfo.graphNode = sideEffectGraph.createNode(functionInfo);
        // Keep track of this so that later functions of the same name can point to the same
        // FunctionInformation.
        functionInfoByName.put(name, functionInfo);
      }
      functionSideEffectMap.put(function, functionInfo);
      if (definitionSite.inExterns) {
        // Externs have their side effects computed here, otherwise in FunctionAnalyzer.
        functionInfo.updateSideEffectsFromExtern(function, compiler);
      }
    }
  }

  /**
   * Propagate side effect information by building a graph based on call site information stored in
   * FunctionInformation and the DefinitionProvider and then running GraphReachability to determine
   * the set of functions that have side effects.
   */
  private void propagateSideEffects() {
    // Propagate side effect information to a fixed point.
    FixedPointGraphTraversal.newTraversal(
            new EdgeCallback<FunctionInformation, CallSitePropagationInfo>() {
              @Override
              public boolean traverseEdge(
                  FunctionInformation source,
                  CallSitePropagationInfo edge,
                  FunctionInformation destination) {
                return edge.propagate(source, destination);
              }
            })
        .computeFixedPoint(sideEffectGraph);
  }

  /** Set no side effect property at pure-function call sites. */
  private void markPureFunctionCalls() {
    for (Node callNode : allFunctionCalls) {
      List<FunctionInformation> possibleSideEffects = getSideEffectsForCall(callNode);
      // Default to side effects, non-local results
      Node.SideEffectFlags flags = new Node.SideEffectFlags();
      if (possibleSideEffects == null) {
        flags.setMutatesGlobalState();
        flags.setThrows();
        flags.setReturnsTainted();
      } else {
        flags.clearAllFlags();
        for (FunctionInformation functionInfo : possibleSideEffects) {
          Preconditions.checkNotNull(functionInfo);
          if (functionInfo.mutatesGlobalState()) {
            flags.setMutatesGlobalState();
          }

          if (functionInfo.mutatesArguments()) {
            flags.setMutatesArguments();
          }

          if (functionInfo.functionThrows()) {
            flags.setThrows();
          }

          if (callNode.isCall()) {
            if (functionInfo.taintsThis()) {
              // A FunctionInfo for "f" maps to both "f()" and "f.call()" nodes.
              if (isCallOrApply(callNode)) {
                flags.setMutatesArguments();
              } else {
                flags.setMutatesThis();
              }
            }
          }

          if (functionInfo.taintsReturn()) {
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

      callNode.setSideEffectFlags(flags.valueOf());
    }
  }

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

    private void resetFunctionVars(Node function) {
      blacklistedVarsByFunction.replaceValues(function, Collections.<Var>emptySet());
      taintedVarsByFunction.replaceValues(function, Collections.<Var>emptySet());
    }

    @Override
    public boolean shouldTraverse(NodeTraversal traversal, Node node, Node parent) {
      // Functions need to be processed as part of pre-traversal so that an entry for the function
      // exists in the functionSideEffectMap map when processing assignments and calls within the
      // body.
      if (node.isFunction()) {
        if (!functionSideEffectMap.containsKey(node)) {
          // This function was not part of a definition which is why it was not created by
          // {@link buildGraph}. For example an anonymous function.
          FunctionInformation functionInfo = new FunctionInformation();
          functionSideEffectMap.put(node, functionInfo);
          functionInfo.graphNode = sideEffectGraph.createNode(functionInfo);
        }
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

      // TODO: This may be more expensive than necessary.
      Node enclosingFunction = traversal.getEnclosingFunction();
      if (enclosingFunction == null) {
        return;
      }

      for (FunctionInformation sideEffectInfo : functionSideEffectMap.get(enclosingFunction)) {
        Preconditions.checkNotNull(sideEffectInfo);
        updateSideEffectsForNode(sideEffectInfo, traversal, node, enclosingFunction);
      }
    }

    public void updateSideEffectsForNode(
        FunctionInformation sideEffectInfo,
        NodeTraversal traversal,
        Node node,
        Node enclosingFunction) {
      if (NodeUtil.isAssignmentOp(node) || node.isInc() || node.isDelProp() || node.isDec()) {
        visitAssignmentOrUnaryOperator(
            sideEffectInfo, traversal.getScope(), node, enclosingFunction);
      } else if (NodeUtil.isCallOrNew(node)) {
        visitCall(sideEffectInfo, node);
      } else if (node.isName()) {
        // Variable definition are not side effects. Check that the name appears in the context of a
        // variable declaration.
        Preconditions.checkArgument(NodeUtil.isNameDeclaration(node.getParent()));
        Node value = node.getFirstChild();
        // Assignment to local, if the value isn't a safe local value,
        // new object creation or literal or known primitive result
        // value, add it to the local blacklist.
        if (value != null && !NodeUtil.evaluatesToLocalValue(value)) {
          Scope scope = traversal.getScope();
          Var var = scope.getVar(node.getString());
          blacklistedVarsByFunction.put(enclosingFunction, var);
        }
      } else if (node.isThrow()) {
        sideEffectInfo.setFunctionThrows();
      } else if (node.isReturn()) {
        if (node.hasChildren() && !NodeUtil.evaluatesToLocalValue(node.getFirstChild())) {
          sideEffectInfo.setTaintsReturn();
        }
      } else {
        throw new IllegalArgumentException("Unhandled side effect node type " + node.getToken());
      }
    }

    @Override
    public void enterScope(NodeTraversal t) {
      // Nothing to do.
    }

    @Override
    public void exitScope(NodeTraversal t) {
      if (!t.getScope().isFunctionBlockScope() && !t.getScope().isFunctionScope()) {
        return;
      }

      Node function = NodeUtil.getEnclosingFunction(t.getScopeRoot());
      if (function == null) {
        return;
      }

      // Handle deferred local variable modifications:
      for (FunctionInformation sideEffectInfo : functionSideEffectMap.get(function)) {
        Preconditions.checkNotNull(sideEffectInfo, "%s has no side effect info.", function);

        if (sideEffectInfo.mutatesGlobalState()) {
          resetFunctionVars(function);
          return;
        }

        for (Var v : t.getScope().getVarIterable()) {
          boolean param = v.getParentNode().isParamList();
          if (param
              && !blacklistedVarsByFunction.containsEntry(function, v)
              && taintedVarsByFunction.containsEntry(function, v)) {
            sideEffectInfo.setTaintsArguments();
            continue;
          }

          boolean localVar = false;
          // Parameters and catch values come can from other scopes.
          if (v.getParentNode().isVar()) {
            // TODO(johnlenz): create a useful parameter list
            // sideEffectInfo.addKnownLocal(v.getName());
            localVar = true;
          }

          // Take care of locals that might have been tainted.
          if (!localVar || blacklistedVarsByFunction.containsEntry(function, v)) {
            if (taintedVarsByFunction.containsEntry(function, v)) {
              // If the function has global side-effects
              // don't bother with the local side-effects.
              sideEffectInfo.setTaintsGlobalState();
              resetFunctionVars(function);
              break;
            }
          }
        }
      }

      if (t.getScopeRoot().isFunction()) {
        resetFunctionVars(function);
      }
    }

    private boolean varDeclaredInDifferentFunction(Var v, Scope scope) {
      if (v == null) {
        return true;
      } else if (v.scope != scope) {
        Node declarationRoot = NodeUtil.getEnclosingFunction(v.scope.rootNode);
        Node scopeRoot = NodeUtil.getEnclosingFunction(scope.rootNode);
        return declarationRoot != scopeRoot;
      } else {
        return false;
      }
    }

    /**
     * Record information about the side effects caused by an assignment or mutating unary operator.
     *
     * <p>If the operation modifies this or taints global state, mark the enclosing function as
     * having those side effects.
     *
     * @param op operation being performed.
     */
    private void visitAssignmentOrUnaryOperator(
        FunctionInformation sideEffectInfo, Scope scope, Node op, Node enclosingFunction) {
      Node lhs = op.getFirstChild();
      if (lhs.isName()) {
        Var var = scope.getVar(lhs.getString());
        if (varDeclaredInDifferentFunction(var, scope)) {
          sideEffectInfo.setTaintsGlobalState();
        } else {
          // Assignment to local, if the value isn't a safe local value,
          // a literal or new object creation, add it to the local blacklist.
          // parameter values depend on the caller.

          // Note: other ops result in the name or prop being assigned a local
          // value (x++ results in a number, for instance)
          Preconditions.checkState(NodeUtil.isAssignmentOp(op) || isIncDec(op) || op.isDelProp());
          Node rhs = op.getLastChild();
          if (rhs != null && op.isAssign() && !NodeUtil.evaluatesToLocalValue(rhs)) {
            blacklistedVarsByFunction.put(enclosingFunction, var);
          }
        }
      } else if (NodeUtil.isGet(lhs)) {
        if (lhs.getFirstChild().isThis()) {
          sideEffectInfo.setTaintsThis();
        } else {
          Var var = null;
          Node objectNode = lhs.getFirstChild();
          if (objectNode.isName()) {
            var = scope.getVar(objectNode.getString());
          }
          if (varDeclaredInDifferentFunction(var, scope)) {
            sideEffectInfo.setTaintsGlobalState();
          } else {
            // Maybe a local object modification.  We won't know for sure until
            // we exit the scope and can validate the value of the local.
            //
            taintedVarsByFunction.put(enclosingFunction, var);
          }
        }
      } else {
        // TODO(johnlenz): track down what is inserting NULL on the LHS
        // of an assign.

        // The only valid LHS expressions are NAME, GETELEM, or GETPROP.
        // throw new IllegalStateException(
        //     "Unexpected LHS expression:" + lhs.toStringTree()
        //    + ", parent: " + op.toStringTree() );
        sideEffectInfo.setTaintsGlobalState();
      }
    }

    /** Record information about a call site. */
    private void visitCall(FunctionInformation sideEffectInfo, Node node) {
      // Handle special cases (Math, RegExp)
      // TODO: This logic can probably be replaced with @nosideeffects annotations in externs.
      if (node.isCall() && !NodeUtil.functionCallHasSideEffects(node, compiler)) {
        return;
      }

      // Handle known cases now (Object, Date, RegExp, etc)
      if (node.isNew() && !NodeUtil.constructorCallHasSideEffects(node)) {
        return;
      }

      List<FunctionInformation> possibleSideEffects = getSideEffectsForCall(node);
      if (possibleSideEffects == null) {
        sideEffectInfo.setTaintsGlobalState();
        sideEffectInfo.setFunctionThrows();
        return;
      }

      for (FunctionInformation sideEffectNode : possibleSideEffects) {
        CallSitePropagationInfo edge = CallSitePropagationInfo.computePropagationType(node);
        sideEffectGraph.connect(sideEffectNode.graphNode, edge, sideEffectInfo.graphNode);
      }
    }
  }

  private static boolean isIncDec(Node n) {
    Token type = n.getToken();
    return (type == Token.INC || type == Token.DEC);
  }

  private static boolean isCallOrApply(Node callSite) {
    return NodeUtil.isFunctionObjectCall(callSite) || NodeUtil.isFunctionObjectApply(callSite);
  }

  /**
   * This class stores all the information about a call site needed to propagate side effects from
   * one instance of {@link FunctionInformation} to another.
   */
  private static class CallSitePropagationInfo {

    private CallSitePropagationInfo(
        boolean allArgsUnescapedLocal, boolean calleeThisEqualsCallerThis, Token callType) {
      Preconditions.checkArgument(callType == Token.CALL || callType == Token.NEW);
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
    boolean propagate(FunctionInformation callee, FunctionInformation caller) {
      CallSitePropagationInfo propagationType = this;
      boolean changed = false;
      // If the callee modifies global state then so does that caller.
      if (callee.mutatesGlobalState() && !caller.mutatesGlobalState()) {
        caller.setTaintsGlobalState();
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
        caller.setTaintsGlobalState();
        changed = true;
      }
      if (callee.mutatesThis() && propagationType.calleeThisEqualsCallerThis) {
        if (!caller.mutatesThis()) {
          caller.setTaintsThis();
          changed = true;
        }
      } else if (callee.mutatesThis() && propagationType.callType != Token.NEW) {
        // NEW invocations of a constructor that modifies "this" don't cause side effects.
        if (!caller.mutatesGlobalState()) {
          caller.setTaintsGlobalState();
          changed = true;
        }
      }
      return changed;
    }

    static CallSitePropagationInfo computePropagationType(Node callSite) {
      Preconditions.checkArgument(callSite.isCall() || callSite.isNew());

      boolean thisIsOuterThis = false;
      if (callSite.isCall()) {
        // Side effects only propagate via regular calls.
        // Calling a constructor that modifies "this" has no side effects.
        // Notice that we're using "mutatesThis" from the callee
        // FunctionInfo. If the call site is actually a .call or .apply, then
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
   * Keeps track of a function's known side effects by type and the list of calls that appear in a
   * function's body.
   */
  private static class FunctionInformation {
    DiGraphNode<FunctionInformation, CallSitePropagationInfo> graphNode;
    private int bitmask = 0;

    // Side effect types:
    private static final int FUNCTION_THROWS_MASK = 1 << 1;
    private static final int TAINTS_GLOBAL_STATE_MASK = 1 << 2;
    private static final int TAINTS_THIS_MASK = 1 << 3;
    private static final int TAINTS_ARGUMENTS_MASK = 1 << 4;

    // Function metatdata
    private static final int TAINTS_RETURN_MASK = 1 << 5;

    void setMask(int mask) {
      bitmask |= mask;
    }

    boolean getMask(int mask) {
      return (bitmask & mask) != 0;
    }

    boolean taintsGlobalState() {
      return getMask(TAINTS_GLOBAL_STATE_MASK);
    }

    boolean taintsThis() {
      return getMask(TAINTS_THIS_MASK);
    }

    /**
     * @return Whether the function returns something that is not affected by global state. In this
     *     case, only true if return value is a literal or primative since locals are not tracked
     *     correctly.
     */
    boolean taintsReturn() {
      return getMask(TAINTS_RETURN_MASK);
    }

    /** Returns true if function has an explicit "throw". */
    boolean functionThrows() {
      return getMask(FUNCTION_THROWS_MASK);
    }

    /** @return false if function known to have side effects. */
    boolean isPure() {
      return !getMask(
          FUNCTION_THROWS_MASK
              | TAINTS_GLOBAL_STATE_MASK
              | TAINTS_THIS_MASK
              | TAINTS_ARGUMENTS_MASK);
    }

    /** Marks the function as having "modifies globals" side effects. */
    void setTaintsGlobalState() {
      setMask(TAINTS_GLOBAL_STATE_MASK);
    }

    /** Marks the function as having "modifies this" side effects. */
    void setTaintsThis() {
      setMask(TAINTS_THIS_MASK);
    }

    /** Marks the function as having "modifies arguments" side effects. */
    void setTaintsArguments() {
      setMask(TAINTS_ARGUMENTS_MASK);
    }

    /** Marks the function as having "throw" side effects. */
    void setFunctionThrows() {
      setMask(FUNCTION_THROWS_MASK);
    }

    /** Marks the function as having non-local return result. */
    void setTaintsReturn() {
      setMask(TAINTS_RETURN_MASK);
    }

    /** Returns true if function mutates global state. */
    boolean mutatesGlobalState() {
      return getMask(TAINTS_GLOBAL_STATE_MASK);
    }

    /** Returns true if function mutates its arguments. */
    boolean mutatesArguments() {
      return getMask(TAINTS_GLOBAL_STATE_MASK | TAINTS_ARGUMENTS_MASK);
    }

    /** Returns true if function mutates "this". */
    boolean mutatesThis() {
      return taintsThis();
    }

    @Override
    public String toString() {
      List<String> status = new ArrayList<>();
      if (taintsThis()) {
        status.add("this");
      }

      if (taintsGlobalState()) {
        status.add("global");
      }

      if (mutatesArguments()) {
        status.add("args");
      }

      if (functionThrows()) {
        status.add("throw");
      }

      return "Side effects: " + status;
    }

    /** Update function for @nosideeffects annotations. */
    private void updateSideEffectsFromExtern(Node externFunction, AbstractCompiler compiler) {
      Preconditions.checkArgument(externFunction.isFunction());
      Preconditions.checkArgument(externFunction.isFromExterns());

      JSDocInfo info = NodeUtil.getBestJSDocInfo(externFunction);
      // Handle externs.
      JSType jstype = externFunction.getJSType();
      FunctionType functionType = JSType.toMaybeFunctionType(jstype);
      if (functionType != null) {
        JSType jstypeReturn = functionType.getReturnType();
        if (!PureFunctionIdentifier.isLocalValueType(jstypeReturn, compiler)) {
          setTaintsReturn();
        }
      }

      if (info == null) {
        // We don't know anything about this function so we assume it has side effects.
        setTaintsGlobalState();
        setFunctionThrows();
      } else {
        if (info.modifiesThis()) {
          setTaintsThis();
        } else if (info.hasSideEffectsArgumentsAnnotation()) {
          setTaintsArguments();
        } else if (!info.getThrownTypes().isEmpty()) {
          setFunctionThrows();
        } else if (info.isNoSideEffects()) {
          // Do nothing.
        } else {
          setTaintsGlobalState();
        }
      }
    }
  }

  /**
   * TODO: This could be greatly improved.
   *
   * @return Whether the jstype is something known to be a local value.
   */
  private static boolean isLocalValueType(JSType jstype, AbstractCompiler compiler) {
    Preconditions.checkNotNull(jstype);
    JSType subtype =
        jstype.getGreatestSubtype(
            (JSType) compiler.getTypeIRegistry().getNativeType(JSTypeNative.OBJECT_TYPE));
    // If the type includes anything related to a object type, don't assume
    // anything about the locality of the value.
    return subtype.isNoType();
  }

  /**
   * A compiler pass that constructs a reference graph and drives the PureFunctionIdentifier across
   * it.
   */
  static class Driver implements CompilerPass {
    private final AbstractCompiler compiler;
    private final String reportPath;

    Driver(AbstractCompiler compiler, String reportPath) {
      this.compiler = compiler;
      this.reportPath = reportPath;
    }

    @Override
    public void process(Node externs, Node root) {
      NameBasedDefinitionProvider defFinder = new NameBasedDefinitionProvider(compiler, true);
      defFinder.process(externs, root);

      PureFunctionIdentifier pureFunctionIdentifier =
          new PureFunctionIdentifier(compiler, defFinder);
      pureFunctionIdentifier.process(externs, root);

      if (reportPath != null) {
        try {
          Files.write(pureFunctionIdentifier.getDebugReport(), new File(reportPath), UTF_8);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
