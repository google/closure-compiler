/*
 * Copyright 2009 Google Inc.
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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiler pass that computes function purity.  A function is pure if
 * it has no outside visible side effects, and the result of the
 * computation does not depend on external factors that are beyond the
 * control of the application; repeated calls to the function should
 * return the same value as long as global state hasn't changed.
 *
 * Date.now is an example of a function that has no side effects but
 * is not pure.
 *
*
 *
 * We will prevail, in peace and freedom from fear, and in true
 * health, through the purity and essence of our natural... fluids.
 *                                    - General Turgidson
 */
class PureFunctionIdentifier implements CompilerPass {
  static final DiagnosticType INVALID_NO_SIDE_EFFECT_ANNOTATION =
      DiagnosticType.error(
          "JSC_INVALID_NO_SIDE_EFFECT_ANNOTATION",
          "@nosideeffects may only appear in externs files.");

  private final AbstractCompiler compiler;
  private final DefinitionProvider definitionProvider;

  // Function node -> function side effects map
  private final Map<Node, FunctionInformation> functionSideEffectMap;

  // List of all function call sites; used to iterate in markPureFunctionCalls.
  private final List<Node> allFunctionCalls;

  // Externs and ast tree root, for use in getDebugReport.  These two
  // fields are null until process is called.
  private Node externs;
  private Node root;

  public PureFunctionIdentifier(AbstractCompiler compiler,
                                DefinitionProvider definitionProvider) {
    this.compiler = compiler;
    this.definitionProvider = definitionProvider;
    this.functionSideEffectMap = Maps.newHashMap();
    this.allFunctionCalls = Lists.newArrayList();
    this.externs = null;
    this.root = null;
  }

  @Override
  public void process(Node externsAst, Node srcAst) {
    if (externs != null || root != null) {
      throw new IllegalStateException(
          "It is illegal to call PureFunctionIdentifier.process " +
          "twice the same instance.  Please use a new " +
          "PureFunctionIdentifier instance each time.");
    }

    externs = externsAst;
    root = srcAst;

    NodeTraversal.traverse(compiler, externs, new FunctionAnalyzer(true));
    NodeTraversal.traverse(compiler, root, new FunctionAnalyzer(false));

    propagateSideEffects();

    markPureFunctionCalls();
  }

  /**
   * Compute debug report that includes:
   *  - List of all pure functions.
   *  - Reasons we think the remaining functions have side effects.
   */
  String getDebugReport() {
    Preconditions.checkNotNull(externs);
    Preconditions.checkNotNull(root);

    StringBuilder sb = new StringBuilder();

    FunctionNames functionNames = new FunctionNames(compiler);
    functionNames.process(null, externs);
    functionNames.process(null, root);

    sb.append("Pure functions:\n");
    for (Map.Entry<Node, FunctionInformation> entry :
             functionSideEffectMap.entrySet()) {
      Node function = entry.getKey();
      FunctionInformation functionInfo = entry.getValue();

      boolean isPure =
          functionInfo.mayBePure() && !functionInfo.mayHaveSideEffects();
      if (isPure) {
        sb.append("  " + functionNames.getFunctionName(function) + "\n");
      }
    }
    sb.append("\n");

    for (Map.Entry<Node, FunctionInformation> entry :
             functionSideEffectMap.entrySet()) {
      Node function = entry.getKey();
      FunctionInformation functionInfo = entry.getValue();

      Set<String> depFunctionNames = Sets.newHashSet();
      for (Node callSite : functionInfo.getCallsInFunctionBody()) {
        Collection<Definition> defs =
            getCallableDefinitions(definitionProvider,
                                   callSite.getFirstChild());

        if (defs == null) {
          depFunctionNames.add("<null def list>");
          continue;
        }

        for (Definition def : defs) {
          depFunctionNames.add(
              functionNames.getFunctionName(def.getRValue()));
        }
      }

      sb.append(functionNames.getFunctionName(function) + " " +
                functionInfo.toString() +
                " Calls: " + depFunctionNames + "\n");
    }

    return sb.toString();
  }

  /**
   * Query the DefinitionProvider for the list of definitions that
   * correspond to a given qualified name subtree.  Return null if
   * DefinitionProvider does not contain an entry for a given name,
   * one or more of the values returned by getDeclarations is not
   * callable, or the "name" node is not a GETPROP or NAME.
   *
   * @param definitionProvider The name reference graph
   * @param name Query node
   * @return non-empty definition list or null
   */
  private static Collection<Definition> getCallableDefinitions(
      DefinitionProvider definitionProvider, Node name) {
    if (NodeUtil.isGetProp(name) || NodeUtil.isName(name)) {
      List<Definition> result = Lists.newArrayList();

      Collection<Definition> decls =
          definitionProvider.getDefinitionsReferencedAt(name);
      if (decls == null) {
        return null;
      }

      for (Definition current : decls) {
        Node rValue = current.getRValue();
        if ((rValue != null) && NodeUtil.isFunction(rValue)) {
          result.add(current);
        } else {
          return null;
        }
      }

      return result;
    } else if (name.getType() == Token.OR || name.getType() == Token.HOOK) {
      Node firstVal;
      if (name.getType() == Token.HOOK) {
        firstVal = name.getFirstChild().getNext();
      } else {
        firstVal = name.getFirstChild();
      }

      Collection<Definition> defs1 = getCallableDefinitions(definitionProvider,
                                                            firstVal);
      Collection<Definition> defs2 = getCallableDefinitions(definitionProvider,
                                                            firstVal.getNext());
      if (defs1 != null && defs2 != null) {
        defs1.addAll(defs2);
        return defs1;
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  /**
   * Propagate side effect information by building a graph based on
   * call site information stored in FunctionInformation and the
   * DefinitionProvider and then running GraphReachability to
   * determine the set of functions that have side effects.
   */
  private void propagateSideEffects() {
    // Nodes are function declarations; Edges are function call sites.
    DiGraph<FunctionInformation, Node> sideEffectGraph =
        new LinkedDirectedGraph<FunctionInformation, Node>();

    // create graph nodes
    for (FunctionInformation functionInfo : functionSideEffectMap.values()) {
      sideEffectGraph.createNode(functionInfo);
    }

    // add connections to called functions and side effect root.
    for (FunctionInformation functionInfo : functionSideEffectMap.values()) {
      if (!functionInfo.mayHaveSideEffects()) {
        continue;
      }

      for (Node callSite : functionInfo.getCallsInFunctionBody()) {
        Node callee = callSite.getFirstChild();
        Collection<Definition> defs =
            getCallableDefinitions(definitionProvider, callee);
        if (defs == null) {
          // Definition set is not complete or eligible.  Possible
          // causes include:
          //  * "callee" is not of type NAME or GETPROP.
          //  * One or more definitions are not functions.
          //  * One or more definitions are complex.
          //    (e.i. return value of a call that returns a function).
          functionInfo.setTaintsUnknown();
          break;
        }

        for (Definition def : defs) {
          Node defValue = def.getRValue();
          FunctionInformation dep = functionSideEffectMap.get(defValue);
          Preconditions.checkNotNull(dep);
          sideEffectGraph.connect(dep, callSite, functionInfo);
        }
      }
    }

    // Propagate side effect information to a fixed point.
    FixedPointGraphTraversal.newTraversal(new SideEffectPropagationCallback())
        .computeFixedPoint(sideEffectGraph);

    // Mark remaining functions "pure".
    for (FunctionInformation functionInfo : functionSideEffectMap.values()) {
      if (functionInfo.mayBePure()) {
        functionInfo.setIsPure();
      }
    }
  }

  /**
   * Set no side effect property at pure-function call sites.
   */
  private void markPureFunctionCalls() {
    for (Node callNode : allFunctionCalls) {
      Node name = callNode.getFirstChild();
      Collection<Definition> defs =
          getCallableDefinitions(definitionProvider, name);
      boolean hasSideEffects = true;
      if (defs != null) {
        hasSideEffects = false;
        for (Definition def : defs) {
          FunctionInformation functionInfo =
              functionSideEffectMap.get(def.getRValue());
          Preconditions.checkNotNull(functionInfo);

          if ((NodeUtil.isCall(callNode)
                  && functionInfo.mayHaveSideEffects())
               || (NodeUtil.isNew(callNode)
                      && (functionInfo.mutatesGlobalState()
                          || functionInfo.functionThrows()))) {
            hasSideEffects = true;
            break;
          }
        }
      }

      // Handle special cases (Math, RegEx)
      if (NodeUtil.isCall(callNode)) {
        Preconditions.checkState(compiler != null);
        if (!NodeUtil.functionCallHasSideEffects(callNode, compiler)) {
          hasSideEffects = false;
        }
      } else if (NodeUtil.isNew(callNode)) {
        // Handle known cases now (Object, Date, RegExp, etc)
        if (!NodeUtil.constructorCallHasSideEffects(callNode)) {
          hasSideEffects = false;
        }
      }

      if (!hasSideEffects) {
        callNode.setIsNoSideEffectsCall();
      }
    }
  }

  /**
   * Gather list of functions, functions with @nosideeffect
   * annotations, call sites, and functions that may mutate variables
   * not defined in the local scope.
   */
  private class FunctionAnalyzer implements Callback {
    private final boolean inExterns;

    FunctionAnalyzer(boolean inExterns) {
      this.inExterns = inExterns;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal traversal,
                                  Node node,
                                  Node parent) {

      // Functions need to be processed as part of pre-traversal so an
      // entry for the enclosing function exists in the
      // FunctionInformation map when processing assignments and calls
      // inside visit.
      if (NodeUtil.isFunction(node)) {
        Node gramp = parent.getParent();
        visitFunction(traversal, node, parent, gramp);
      }

      return true;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {

      if (inExterns) {
        return;
      }

      if (!NodeUtil.nodeTypeMayHaveSideEffects(node)) {
        return;
      }

      if (NodeUtil.isCall(node) || NodeUtil.isNew(node)) {
        allFunctionCalls.add(node);
      }

      Node enclosingFunction = traversal.getEnclosingFunction();
      if (enclosingFunction != null) {
        FunctionInformation sideEffectInfo =
            functionSideEffectMap.get(enclosingFunction);
        Preconditions.checkNotNull(sideEffectInfo);

        if (NodeUtil.isAssignmentOp(node)) {
          visitAssignmentOrUnaryOperatorLhs(
              sideEffectInfo, traversal.getScope(), node.getFirstChild());
        } else {
          switch(node.getType()) {
            case Token.CALL:
            case Token.NEW:
              visitCall(sideEffectInfo, node);
              break;
            case Token.DELPROP:
            case Token.DEC:
            case Token.INC:
              visitAssignmentOrUnaryOperatorLhs(
                  sideEffectInfo, traversal.getScope(), node.getFirstChild());
              break;
            case Token.NAME:

              // Variable definition are not side effects.
              // Just check that the name appears in the context of a
              // variable declaration.
              Preconditions.checkArgument(
                  NodeUtil.isVarDeclaration(node));
              break;
            case Token.THROW:
              visitThrow(sideEffectInfo);
              break;
            default:
              throw new IllegalArgumentException(
                  "Unhandled side effect node type " +
                  Token.name(node.getType()));
          }
        }
      }
    }

    /**
     * Record information about the side effects caused by an
     * assigment or mutating unary operator.
     *
     * If the operation modifies this or taints global state, mark the
     * enclosing function as having those side effects.
     */
    private void visitAssignmentOrUnaryOperatorLhs(
        FunctionInformation sideEffectInfo, Scope scope, Node lhs) {
      if (NodeUtil.isName(lhs)) {
        Var var = scope.getVar(lhs.getString());
        if (var == null || var.scope != scope) {
          sideEffectInfo.setTaintsGlobalState();
        }
      } else if (NodeUtil.isGetProp(lhs)) {
        if (NodeUtil.isThis(lhs.getFirstChild())) {
          sideEffectInfo.setTaintsThis();
        } else {
          sideEffectInfo.setTaintsUnknown();
        }
      } else {
        sideEffectInfo.setTaintsUnknown();
      }
    }

    /**
     * Record information about a call site.
     */
    private void visitCall(FunctionInformation sideEffectInfo, Node node) {
      // Handle special cases (Math, RegEx)
      if (NodeUtil.isCall(node)
          && !NodeUtil.functionCallHasSideEffects(node, compiler)) {
        return;
      }

      // Handle known cases now (Object, Date, RegExp, etc)
      if (NodeUtil.isNew(node)
          && !NodeUtil.constructorCallHasSideEffects(node)) {
        return;
      }

      sideEffectInfo.appendCall(node);
    }

    /**
     * Record function and check for @nosideeffects annotations.
     */
    private void visitFunction(NodeTraversal traversal,
                               Node node,
                               Node parent,
                               Node gramp) {
      Preconditions.checkArgument(!functionSideEffectMap.containsKey(node));

      FunctionInformation sideEffectInfo = new FunctionInformation(inExterns);
      functionSideEffectMap.put(node, sideEffectInfo);

      if (hasNoSideEffectsAnnotation(node, parent, gramp)) {
        if (inExterns) {
          sideEffectInfo.setIsPure();
        } else {
          traversal.report(node, INVALID_NO_SIDE_EFFECT_ANNOTATION);
        }
      } else if (inExterns) {
        sideEffectInfo.setTaintsGlobalState();
      }
    }

    /**
     * Record that the enclosing function throws.
     */
    private void visitThrow(FunctionInformation sideEffectInfo) {
      sideEffectInfo.setFunctionThrows();
    }

    /**
     * Get the value of the @nosideeffects annotation stored in the
     * doc info.
     */
    private boolean hasNoSideEffectsAnnotation(Node node,
                                               Node parent,
                                               Node gramp) {
      {
        JSDocInfo docInfo = node.getJSDocInfo();
        if (docInfo != null && docInfo.isNoSideEffects()) {
          return true;
        }
      }

      if (NodeUtil.isName(parent)) {
        JSDocInfo docInfo = gramp.getJSDocInfo();
        return gramp.hasOneChild() &&
            docInfo != null &&
            docInfo.isNoSideEffects();
      } else if (NodeUtil.isAssign(parent)) {
        JSDocInfo docInfo = parent.getJSDocInfo();
        return docInfo != null && docInfo.isNoSideEffects();
      } else {
        return false;
      }
    }
  }

  /**
   * Callback that propagates side effect information across call sites.
   */
  private static class SideEffectPropagationCallback
      implements EdgeCallback<FunctionInformation, Node> {
    public boolean traverseEdge(FunctionInformation callee,
                                Node callSite,
                                FunctionInformation caller) {
      Preconditions.checkArgument(callSite.getType() == Token.CALL ||
                                  callSite.getType() == Token.NEW);

      boolean changed = false;
      if (!caller.mutatesGlobalState() && callee.mutatesGlobalState()) {
        caller.setTaintsGlobalState();
        changed = true;
      }

      if (!caller.functionThrows() && callee.functionThrows()) {
        caller.setFunctionThrows();
        changed = true;
      }

      if (callee.mutatesThis()) {
        // Side effects only propagate via regular calls.
        // Calling a constructor that modifies "this" has no side effects.
        if (callSite.getType() != Token.NEW) {
          Node objectNode = getCallThisObject(callSite);
          if (objectNode != null && NodeUtil.isThis(objectNode)) {
            if (!caller.mutatesThis()) {
              caller.setTaintsThis();
              changed = true;
            }
          } else if (!caller.mutatesGlobalState()) {
            caller.setTaintsGlobalState();
            changed = true;
          }
        }
      }

      return changed;
    }
  }

  /**
   * Analyze a call site and extract the node that will be act as
   * "this" inside the call, which is either the object part of the
   * qualified function name, the first argument to the call in the
   * case of ".call" and ".apply" or null if object is not specified
   * in either of those ways.
   *
   * @return node that will act as "this" for the call.
   */
  private static Node getCallThisObject(Node callSite) {
    Node foo = callSite.getFirstChild();
    if (!NodeUtil.isGetProp(foo)) {

      // "this" is not specified explicitly; call modifies global "this".
      return null;
    }

    Node object = null;

    String propString = foo.getLastChild().getString();
    if (propString.equals("call") || propString.equals("apply")) {
      return foo.getNext();
    } else {
      return foo.getFirstChild();
    }
  }

  /**
   * Keeps track of a function's known side effects by type and the
   * list of calls that appear in a function's body.
   */
  private static class FunctionInformation {
    private final boolean extern;
    private final List<Node> callsInFunctionBody = Lists.newArrayList();
    private boolean pureFunction = false;
    private boolean functionThrows = false;
    private boolean taintsGlobalState = false;
    private boolean taintsThis = false;
    private boolean taintsUnknown = false;

    FunctionInformation(boolean extern) {
      this.extern = extern;
      checkInvariant();
    }

    /**
     * Function appeared in externs file.
     */
    boolean isExtern() {
      return extern;
    }

    /**
     * @returns false if function known to have side effects.
     */
    boolean mayBePure() {
      return !(functionThrows ||
               taintsGlobalState ||
               taintsThis ||
               taintsUnknown);
    }

    /**
     * @returns false if function known to be pure.
     */
    boolean mayHaveSideEffects() {
      return !pureFunction;
    }

    /**
     * Mark the function as being pure.
     */
    void setIsPure() {
      pureFunction = true;
      checkInvariant();
    }

    /**
     * Marks the function as having "modifies globals" side effects.
     */
    void setTaintsGlobalState() {
      taintsGlobalState = true;
      checkInvariant();
    }

    /**
     * Marks the function as having "modifies this" side effects.
     */
    void setTaintsThis() {
      taintsThis = true;
      checkInvariant();
    }

    /**
     * Marks the function as having "throw" side effects.
     */
    void setFunctionThrows() {
      functionThrows = true;
      checkInvariant();
    }

    /**
     * Marks the function as having "complex" side effects that are
     * not otherwise explicitly tracked.
     */
    void setTaintsUnknown() {
      taintsUnknown = true;
      checkInvariant();
    }

    /**
     * Returns true if function mutates global state.
     */
    boolean mutatesGlobalState() {
      return taintsGlobalState || taintsUnknown;
    }

    /**
     * Returns true if function mutates "this".
     */
    boolean mutatesThis() {
      return taintsThis;
    }

    /**
     * Returns true if function has an explicit "throw".
     */
    boolean functionThrows() {
      return functionThrows;
    }

    /**
     * Verify internal consistency.  Should be called at the end of
     * every method that mutates internal state.
     */
    private void checkInvariant() {
      boolean invariant = mayBePure() || mayHaveSideEffects();
      if (!invariant) {
        throw new IllegalStateException("Invariant failed.  " + toString());
      }
    }

    /**
     * Add a CALL or NEW node to the list of calls this function makes.
     */
    void appendCall(Node callNode) {
      callsInFunctionBody.add(callNode);
    }

    /**
     * Gets the list of CALL and NEW nodes.
     */
    List<Node> getCallsInFunctionBody() {
      return callsInFunctionBody;
    }

    @Override
    public String toString() {
      List<String> status = Lists.newArrayList();
      if (extern) {
        status.add("extern");
      }

      if (pureFunction) {
        status.add("pure");
      }

      if (taintsThis) {
        status.add("this");
      }

      if (taintsGlobalState) {
        status.add("global");
      }

      if (functionThrows) {
        status.add("throw");
      }

      if (taintsUnknown) {
        status.add("complex");
      }

      return "Side effects: " + status.toString();
    }
  }
}
