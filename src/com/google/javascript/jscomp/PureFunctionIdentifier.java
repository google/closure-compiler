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

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.DiGraph;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
 * We will prevail, in peace and freedom from fear, and in true
 * health, through the purity and essence of our natural... fluids.
 *                                    - General Turgidson
 */
class PureFunctionIdentifier implements CompilerPass {
  static final DiagnosticType INVALID_NO_SIDE_EFFECT_ANNOTATION =
      DiagnosticType.error(
          "JSC_INVALID_NO_SIDE_EFFECT_ANNOTATION",
          "@nosideeffects may only appear in externs files.");

  static final DiagnosticType INVALID_MODIFIES_ANNOTATION =
    DiagnosticType.error(
        "JSC_INVALID_MODIFIES_ANNOTATION",
        "@modifies may only appear in externs files.");

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
    if (name.isGetProp() || name.isName()) {
      List<Definition> result = Lists.newArrayList();

      Collection<Definition> decls =
          definitionProvider.getDefinitionsReferencedAt(name);
      if (decls == null) {
        return null;
      }

      for (Definition current : decls) {
        Node rValue = current.getRValue();
        if ((rValue != null) && rValue.isFunction()) {
          result.add(current);
        } else {
          return null;
        }
      }

      return result;
    } else if (name.isOr() || name.isHook()) {
      Node firstVal;
      if (name.isHook()) {
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
    } else if (NodeUtil.isFunctionExpression(name)) {
      // The anonymous function reference is also the definition.
      // TODO(user) Change SimpleDefinitionFinder so it is possible to query for
      // function expressions by function node.

      // isExtern is false in the call to the constructor for the
      // FunctionExpressionDefinition below because we know that
      // getCallableDefinitions() will only be called on the first
      // child of a call and thus the function expression
      // definition will never be an extern.
      return Lists.newArrayList(
          (Definition)
              new DefinitionsRemover.FunctionExpressionDefinition(name, false));
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
        LinkedDirectedGraph.createWithoutAnnotations();

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
      // Default to side effects, non-local results
      Node.SideEffectFlags flags = new Node.SideEffectFlags();
      if (defs == null) {
        flags.setMutatesGlobalState();
        flags.setThrows();
        flags.setReturnsTainted();
      } else {
        flags.clearAllFlags();
        for (Definition def : defs) {
          FunctionInformation functionInfo =
              functionSideEffectMap.get(def.getRValue());
          Preconditions.checkNotNull(functionInfo);
          if (functionInfo.mutatesGlobalState()) {
            flags.setMutatesGlobalState();
          }

          if (functionInfo.mutatesArguments()) {
            flags.setMutatesArguments();
          }

          if (functionInfo.functionThrows) {
            flags.setThrows();
          }

          if (!callNode.isNew()) {
            if (functionInfo.taintsThis) {
              // A FunctionInfo for "f" maps to both "f()" and "f.call()" nodes.
              if (isCallOrApply(callNode)) {
                flags.setMutatesArguments();
              } else {
                flags.setMutatesThis();
              }
            }
          }

          if (functionInfo.taintsReturn) {
            flags.setReturnsTainted();
          }

          if (flags.areAllFlagsSet()) {
            break;
          }
        }
      }

      // Handle special cases (Math, RegExp)
      if (callNode.isCall()) {
        Preconditions.checkState(compiler != null);
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
   * Gather list of functions, functions with @nosideeffects
   * annotations, call sites, and functions that may mutate variables
   * not defined in the local scope.
   */
  private class FunctionAnalyzer implements ScopedCallback {
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
      if (node.isFunction()) {
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

      if (!NodeUtil.nodeTypeMayHaveSideEffects(node)
          && !node.isReturn()) {
        return;
      }

      if (node.isCall() || node.isNew()) {
        allFunctionCalls.add(node);
      }

      Node enclosingFunction = traversal.getEnclosingFunction();
      if (enclosingFunction != null) {
        FunctionInformation sideEffectInfo =
            functionSideEffectMap.get(enclosingFunction);
        Preconditions.checkNotNull(sideEffectInfo);

        if (NodeUtil.isAssignmentOp(node)) {
          visitAssignmentOrUnaryOperator(
              sideEffectInfo, traversal.getScope(),
              node, node.getFirstChild(), node.getLastChild());
        } else {
          switch(node.getType()) {
            case Token.CALL:
            case Token.NEW:
              visitCall(sideEffectInfo, node);
              break;
            case Token.DELPROP:
            case Token.DEC:
            case Token.INC:
              visitAssignmentOrUnaryOperator(
                  sideEffectInfo, traversal.getScope(),
                  node, node.getFirstChild(), null);
              break;
            case Token.NAME:
              // Variable definition are not side effects.
              // Just check that the name appears in the context of a
              // variable declaration.
              Preconditions.checkArgument(
                  NodeUtil.isVarDeclaration(node));
              Node value = node.getFirstChild();
              // Assignment to local, if the value isn't a safe local value,
              // new object creation or literal or known primitive result
              // value, add it to the local blacklist.
              if (value != null && !NodeUtil.evaluatesToLocalValue(value)) {
                Scope scope = traversal.getScope();
                Var var = scope.getVar(node.getString());
                sideEffectInfo.blacklistLocal(var);
              }
              break;
            case Token.THROW:
              visitThrow(sideEffectInfo);
              break;
            case Token.RETURN:
              if (node.hasChildren()
                  && !NodeUtil.evaluatesToLocalValue(node.getFirstChild())) {
                sideEffectInfo.setTaintsReturn();
              }
              break;
            default:
              throw new IllegalArgumentException(
                  "Unhandled side effect node type " +
                  Token.name(node.getType()));
          }
        }
      }
    }

    @Override
    public void enterScope(NodeTraversal t) {
      // Nothing to do.
    }

    @Override
    public void exitScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        return;
      }

      // Handle deferred local variable modifications:
      //
      FunctionInformation sideEffectInfo =
        functionSideEffectMap.get(t.getScopeRoot());
      if (sideEffectInfo.mutatesGlobalState()){
        sideEffectInfo.resetLocalVars();
        return;
      }

      for (Iterator<Var> i = t.getScope().getVars(); i.hasNext();) {
        Var v = i.next();

        boolean param = v.getParentNode().isParamList();
        if (param &&
            !sideEffectInfo.blacklisted.contains(v) &&
            sideEffectInfo.taintedLocals.contains(v)) {
          sideEffectInfo.setTaintsArguments();
          continue;
        }

        boolean localVar = false;
        // Parameters and catch values come can from other scopes.
        if (v.getParentNode().isVar()) {
          // TODO(johnlenz): create a useful parameter list
          sideEffectInfo.knownLocals.add(v.getName());
          localVar = true;
        }

        // Take care of locals that might have been tainted.
        if (!localVar || sideEffectInfo.blacklisted.contains(v)) {
          if (sideEffectInfo.taintedLocals.contains(v)) {
            // If the function has global side-effects
            // don't bother with the local side-effects.
            sideEffectInfo.setTaintsUnknown();
            sideEffectInfo.resetLocalVars();
            break;
          }
        }
      }

      sideEffectInfo.taintedLocals = null;
      sideEffectInfo.blacklisted = null;
    }


    /**
     * Record information about the side effects caused by an
     * assignment or mutating unary operator.
     *
     * If the operation modifies this or taints global state, mark the
     * enclosing function as having those side effects.
     * @param op operation being performed.
     * @param lhs The store location (name or get) being operated on.
     * @param rhs The right have value, if any.
     */
    private void visitAssignmentOrUnaryOperator(
        FunctionInformation sideEffectInfo,
        Scope scope, Node op, Node lhs, Node rhs) {
      if (lhs.isName()) {
        Var var = scope.getVar(lhs.getString());
        if (var == null || var.scope != scope) {
          sideEffectInfo.setTaintsGlobalState();
        } else {
          // Assignment to local, if the value isn't a safe local value,
          // a literal or new object creation, add it to the local blacklist.
          // parameter values depend on the caller.

          // Note: other ops result in the name or prop being assigned a local
          // value (x++ results in a number, for instance)
          Preconditions.checkState(
              NodeUtil.isAssignmentOp(op)
              || isIncDec(op) || op.isDelProp());
          if (rhs != null
              && op.isAssign()
              && !NodeUtil.evaluatesToLocalValue(rhs)) {
            sideEffectInfo.blacklistLocal(var);
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
          if (var == null || var.scope != scope) {
            sideEffectInfo.setTaintsUnknown();
          } else {
            // Maybe a local object modification.  We won't know for sure until
            // we exit the scope and can validate the value of the local.
            //
            sideEffectInfo.addTaintedLocalObject(var);
          }
        }
      } else {
        // TODO(johnlenz): track down what is inserting NULL on the LHS
        // of an assign.

        // The only valid LHS expressions are NAME, GETELEM, or GETPROP.
        // throw new IllegalStateException(
        //     "Unexpected LHS expression:" + lhs.toStringTree()
        //    + ", parent: " + op.toStringTree() );
        sideEffectInfo.setTaintsUnknown();
      }
    }

    /**
     * Record information about a call site.
     */
    private void visitCall(FunctionInformation sideEffectInfo, Node node) {
      // Handle special cases (Math, RegExp)
      if (node.isCall()
          && !NodeUtil.functionCallHasSideEffects(node, compiler)) {
        return;
      }

      // Handle known cases now (Object, Date, RegExp, etc)
      if (node.isNew()
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

      if (inExterns) {
        JSType jstype = node.getJSType();
        boolean knownLocalResult = false;
        FunctionType functionType = JSType.toMaybeFunctionType(jstype);
        if (functionType != null) {
          JSType jstypeReturn = functionType.getReturnType();
          if (isLocalValueType(jstypeReturn)) {
            knownLocalResult = true;
          }
        }
        if (!knownLocalResult) {
          sideEffectInfo.setTaintsReturn();
        }
      }

      JSDocInfo info = getJSDocInfoForFunction(node, parent, gramp);
      if (info != null) {
        boolean hasSpecificSideEffects = false;
        if (hasSideEffectsThisAnnotation(info)) {
          if (inExterns) {
            hasSpecificSideEffects = true;
            sideEffectInfo.setTaintsThis();
          } else {
            traversal.report(node, INVALID_MODIFIES_ANNOTATION);
          }
        }

        if (hasSideEffectsArgumentsAnnotation(info)) {
          if (inExterns) {
            hasSpecificSideEffects = true;
            sideEffectInfo.setTaintsArguments();
          } else {
            traversal.report(node, INVALID_MODIFIES_ANNOTATION);
          }
        }

        if (inExterns && !info.getThrownTypes().isEmpty()) {
          hasSpecificSideEffects = true;
          sideEffectInfo.setFunctionThrows();
        }

        if (!hasSpecificSideEffects) {
          if (hasNoSideEffectsAnnotation(info)) {
            if (inExterns) {
              sideEffectInfo.setIsPure();
            } else {
              traversal.report(node, INVALID_NO_SIDE_EFFECT_ANNOTATION);
            }
          } else if (inExterns) {
            sideEffectInfo.setTaintsGlobalState();
          }
        }
      } else {
        if (inExterns) {
          sideEffectInfo.setTaintsGlobalState();
        }
      }
    }

    /**
     * @return Whether the jstype is something known to be a local value.
     */
    private boolean isLocalValueType(JSType jstype) {
      Preconditions.checkNotNull(jstype);
      JSType subtype =  jstype.getGreatestSubtype(
          compiler.getTypeRegistry().getNativeType(JSTypeNative.OBJECT_TYPE));
      // If the type includes anything related to a object type, don't assume
      // anything about the locality of the value.
      return subtype.isNoType();
    }

    /**
     * Record that the enclosing function throws.
     */
    private void visitThrow(FunctionInformation sideEffectInfo) {
      sideEffectInfo.setFunctionThrows();
    }

    /**
     * Get the doc info associated with the function.
     */
    private JSDocInfo getJSDocInfoForFunction(
        Node node, Node parent, Node gramp) {
      JSDocInfo info = node.getJSDocInfo();
      if (info != null) {
        return info;
      } else if (parent.isName()) {
        return gramp.hasOneChild() ? gramp.getJSDocInfo() : null;
      } else if (parent.isAssign()) {
        return parent.getJSDocInfo();
      } else {
        return null;
      }
    }

    /**
     * Get the value of the @nosideeffects annotation stored in the
     * doc info.
     */
    private boolean hasNoSideEffectsAnnotation(JSDocInfo docInfo) {
      Preconditions.checkNotNull(docInfo);
      return docInfo.isNoSideEffects();
    }

    /**
     * Get the value of the @modifies{this} annotation stored in the
     * doc info.
     */
    private boolean hasSideEffectsThisAnnotation(JSDocInfo docInfo) {
      Preconditions.checkNotNull(docInfo);
      return (docInfo.getModifies().contains("this"));
    }

    /**
     * @returns Whether the @modifies annotation includes "arguments"
     * or any named parameters.
     */
    private boolean hasSideEffectsArgumentsAnnotation(JSDocInfo docInfo) {
      Preconditions.checkNotNull(docInfo);
      Set<String> modifies = docInfo.getModifies();
      // TODO(johnlenz): if we start tracking parameters individually
      // this should simply be a check for "arguments".
      return (modifies.size() > 1
          || (modifies.size() == 1 && !modifies.contains("this")));
    }
  }

  private static boolean isIncDec(Node n) {
    int type = n.getType();
    return (type == Token.INC || type == Token.DEC);
  }

  /**
   * @return Whether the node is known to be a value that is not a reference
   *     outside the local scope.
   */
  @SuppressWarnings("unused")
  private static boolean isKnownLocalValue(final Node value) {
    Predicate<Node> taintingPredicate = new Predicate<Node>() {
      @Override
      public boolean apply(Node value) {
        switch (value.getType()) {
          case Token.ASSIGN:
            // The assignment might cause an alias, look at the LHS.
            return false;
          case Token.THIS:
            // TODO(johnlenz): maybe redirect this to be a tainting list for 'this'.
            return false;
          case Token.NAME:
            // TODO(johnlenz): add to local tainting list, if the NAME
            // is known to be a local.
            return false;
          case Token.GETELEM:
          case Token.GETPROP:
            // There is no information about the locality of object properties.
            return false;
          case Token.CALL:
            // TODO(johnlenz): add to local tainting list, if the call result
            // is not known to be a local result.
            return false;
        }
        return false;
      }
    };

    return NodeUtil.evaluatesToLocalValue(value, taintingPredicate);
  }

  /**
   * Callback that propagates side effect information across call sites.
   */
  private static class SideEffectPropagationCallback
      implements EdgeCallback<FunctionInformation, Node> {
    @Override
    public boolean traverseEdge(FunctionInformation callee,
                                Node callSite,
                                FunctionInformation caller) {
      Preconditions.checkArgument(callSite.isCall() ||
                                  callSite.isNew());

      boolean changed = false;
      if (!caller.mutatesGlobalState() && callee.mutatesGlobalState()) {
        caller.setTaintsGlobalState();
        changed = true;
      }

      if (!caller.functionThrows() && callee.functionThrows()) {
        caller.setFunctionThrows();
        changed = true;
      }

      if (!caller.mutatesGlobalState() && callee.mutatesArguments() &&
          !NodeUtil.allArgsUnescapedLocal(callSite)) {
        // TODO(nicksantos): We should track locals in the caller
        // and using that to be more precise. See testMutatesArguments3.
        caller.setTaintsGlobalState();
        changed = true;
      }

      if (callee.mutatesThis()) {
        // Side effects only propagate via regular calls.
        // Calling a constructor that modifies "this" has no side effects.
        if (!callSite.isNew()) {
          // Notice that we're using "mutatesThis" from the callee
          // FunctionInfo. If the call site is actually a .call or .apply, then
          // the "this" is going to be one of its arguments.
          boolean isCallOrApply = isCallOrApply(callSite);
          Node objectNode = isCallOrApply ?
              callSite.getFirstChild().getNext() :
              callSite.getFirstChild().getFirstChild();
          if (objectNode != null && objectNode.isName()
              && !isCallOrApply) {
            // Exclude ".call" and ".apply" as the value may still be
            // null or undefined. We don't need to worry about this with a
            // direct method call because null and undefined don't have any
            // properties.

            // TODO(nicksantos): Turn this back on when locals-tracking
            // is fixed. See testLocalizedSideEffects11.
            //if (!caller.knownLocals.contains(name)) {
              if (!caller.mutatesGlobalState()) {
                caller.setTaintsGlobalState();
                changed = true;
              }
            //}
          } else if (objectNode != null && objectNode.isThis()) {
            if (!caller.mutatesThis()) {
              caller.setTaintsThis();
              changed = true;
            }
          } else if (objectNode != null
              && NodeUtil.evaluatesToLocalValue(objectNode)
              && !isCallOrApply) {
            // Modifying 'this' on a known local object doesn't change any
            // significant state.
            // TODO(johnlenz): We can improve this by including literal values
            // that we know for sure are not null.
          } else if (!caller.mutatesGlobalState()) {
            caller.setTaintsGlobalState();
            changed = true;
          }
        }
      }

      return changed;
    }
  }

  private static boolean isCallOrApply(Node callSite) {
    return NodeUtil.isFunctionObjectCall(callSite) ||
      NodeUtil.isFunctionObjectApply(callSite);
  }

  /**
   * Keeps track of a function's known side effects by type and the
   * list of calls that appear in a function's body.
   */
  private static class FunctionInformation {
    private final boolean extern;
    private final List<Node> callsInFunctionBody = Lists.newArrayList();
    private Set<Var> blacklisted = Sets.newHashSet();
    private Set<Var> taintedLocals = Sets.newHashSet();
    private Set<String> knownLocals = Sets.newHashSet();
    private boolean pureFunction = false;
    private boolean functionThrows = false;
    private boolean taintsGlobalState = false;
    private boolean taintsThis = false;
    private boolean taintsArguments = false;
    private boolean taintsUnknown = false;
    private boolean taintsReturn = false;

    FunctionInformation(boolean extern) {
      this.extern = extern;
      checkInvariant();
    }

    /**
     * @param var
     */
    void addTaintedLocalObject(Var var) {
      taintedLocals.add(var);
    }

    void resetLocalVars() {
      blacklisted = null;
      taintedLocals = null;
      knownLocals = Collections.emptySet();
    }

    /**
     * @param var
     */
    public void blacklistLocal(Var var) {
      blacklisted.add(var);
    }

    /**
     * @returns false if function known to have side effects.
     */
    boolean mayBePure() {
      return !(functionThrows ||
               taintsGlobalState ||
               taintsThis ||
               taintsArguments ||
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
     * Marks the function as having "modifies arguments" side effects.
     */
    void setTaintsArguments() {
      taintsArguments = true;
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
     * Marks the function as having non-local return result.
     */
    void setTaintsReturn() {
      taintsReturn = true;
      checkInvariant();
    }


    /**
     * Returns true if function mutates global state.
     */
    boolean mutatesGlobalState() {
      return taintsGlobalState || taintsUnknown;
    }


    /**
     * Returns true if function mutates its arguments.
     */
    boolean mutatesArguments() {
      return taintsGlobalState || taintsArguments || taintsUnknown;
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

  /**
   * A compiler pass that constructs a reference graph and drives
   * the PureFunctionIdentifier across it.
   */
  static class Driver implements CompilerPass {
    private final AbstractCompiler compiler;
    private final String reportPath;
    private final boolean useNameReferenceGraph;

    Driver(AbstractCompiler compiler, String reportPath,
        boolean useNameReferenceGraph) {
      this.compiler = compiler;
      this.reportPath = reportPath;
      this.useNameReferenceGraph = useNameReferenceGraph;
    }

    @Override
    public void process(Node externs, Node root) {
      DefinitionProvider definitionProvider = null;
      if (useNameReferenceGraph) {
        NameReferenceGraphConstruction graphBuilder =
            new NameReferenceGraphConstruction(compiler);
        graphBuilder.process(externs, root);
        definitionProvider = graphBuilder.getNameReferenceGraph();
      } else {
        SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
        defFinder.process(externs, root);
        definitionProvider = defFinder;
      }

      PureFunctionIdentifier pureFunctionIdentifier =
          new PureFunctionIdentifier(compiler, definitionProvider);
      pureFunctionIdentifier.process(externs, root);

      if (reportPath != null) {
        try {
          Files.write(pureFunctionIdentifier.getDebugReport(),
              new File(reportPath),
              Charsets.UTF_8);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
