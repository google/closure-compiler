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
import com.google.javascript.jscomp.FunctionSideEffectData.CallValueEntry;
import com.google.javascript.jscomp.FunctionSideEffectData.KeywordValueEntry;
import com.google.javascript.jscomp.FunctionSideEffectData.ValueEntry;
import com.google.javascript.jscomp.FunctionSideEffectData.NameValueEntry;
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
  private final Map<Node, FunctionSideEffectData> functionSideEffectMap;

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
    for (Map.Entry<Node, FunctionSideEffectData> entry :
             functionSideEffectMap.entrySet()) {
      Node function = entry.getKey();
      FunctionSideEffectData functionInfo = entry.getValue();

      boolean isPure =
          functionInfo.mayBePure() && !functionInfo.mayHaveSideEffects();
      if (isPure) {
        sb.append("  " + functionNames.getFunctionName(function) + "\n");
      }
    }
    sb.append("\n");

    for (Map.Entry<Node, FunctionSideEffectData> entry :
             functionSideEffectMap.entrySet()) {
      Node function = entry.getKey();
      FunctionSideEffectData functionInfo = entry.getValue();

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
    DiGraph<FunctionSideEffectData, Node> sideEffectGraph =
        new LinkedDirectedGraph<FunctionSideEffectData, Node>();

    // create graph nodes
    for (FunctionSideEffectData functionInfo : functionSideEffectMap.values()) {
      sideEffectGraph.createNode(functionInfo);
    }

    // add connections to called functions and side effect root.
    for (FunctionSideEffectData functionInfo : functionSideEffectMap.values()) {
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
          FunctionSideEffectData dep = functionSideEffectMap.get(defValue);
          Preconditions.checkNotNull(dep);
          sideEffectGraph.connect(dep, callSite, functionInfo);
        }
      }
    }

    // Propagate side effect information to a fixed point.
    FixedPointGraphTraversal.newTraversal(new SideEffectPropagationCallback())
        .computeFixedPoint(sideEffectGraph);

    // Mark remaining functions "pure".
    for (FunctionSideEffectData functionInfo : functionSideEffectMap.values()) {
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
          FunctionSideEffectData functionInfo =
              functionSideEffectMap.get(def.getRValue());
          Preconditions.checkNotNull(functionInfo);
          // TODO(johnlenz): set the arguments separately from the
          // global state flag.
          if (functionInfo.mutatesGlobalState()) {
            flags.setMutatesGlobalState();
          }

          if (functionInfo.functionThrows()) {
            flags.setThrows();
          }

          if (!NodeUtil.isNew(callNode)) {
            if (functionInfo.mutatesThis()) {
              flags.setMutatesThis();
            }
          }

          if (functionInfo.hasNonLocalReturnValue()) {
            flags.setReturnsTainted();
          }

          if (flags.areAllFlagsSet()) {
            break;
          }
        }
      }

      // Handle special cases (Math, RegEx)
      if (NodeUtil.isCall(callNode)) {
        Preconditions.checkState(compiler != null);
        if (!NodeUtil.functionCallHasSideEffects(callNode, compiler)) {
          flags.clearSideEffectFlags();
        }
      } else if (NodeUtil.isNew(callNode)) {
        // Handle known cases now (Object, Date, RegExp, etc)
        if (!NodeUtil.constructorCallHasSideEffects(callNode)) {
          flags.clearSideEffectFlags();
        }
      }

      callNode.setSideEffectFlags(flags.valueOf());
    }
  }

  /**
   * Gather list of functions, functions with @nosideeffect
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



      return true;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {

      if (inExterns) {
        return;
      }

      if (!NodeUtil.nodeTypeMayHaveSideEffects(node)
          && node.getType() != Token.RETURN) {
        return;
      }

      if (NodeUtil.isCall(node) || NodeUtil.isNew(node)) {
        allFunctionCalls.add(node);
      }

      Node enclosingFunction = traversal.getEnclosingFunction();
      if (enclosingFunction != null) {
        FunctionSideEffectData sideEffectInfo =
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
              if (value != null && !analyzeSet(
                      new NameValueEntry(node), value, sideEffectInfo)) {
                Scope scope = traversal.getScope();
                Var var = scope.getVar(node.getString());
                sideEffectInfo.addNonLocalValue(var);
              }
              break;
            case Token.THROW:
              visitThrow(sideEffectInfo);
              break;
            case Token.RETURN:
              if (node.hasChildren() && !analyzeSet(
                      KeywordValueEntry.RETURN, node.getFirstChild(),
                      sideEffectInfo)) {
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
    public void enterScope(NodeTraversal traversal) {
      Node enclosingFunction = traversal.getEnclosingFunction();
      if (enclosingFunction != null) {
        // Functions need to be processed as part of pre-traversal so an
        // entry for the enclosing function exists in the
        // FunctionInformation map when processing assignments and calls
        // inside visit.
        visitFunction(traversal, enclosingFunction);
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
      if (t.inGlobalScope() || inExterns) {
        return;
      }

      FunctionSideEffectData sideEffectInfo =
          functionSideEffectMap.get(t.getScopeRoot());

      sideEffectInfo.normalizeValueMaps();
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
        FunctionSideEffectData sideEffectInfo,
        Scope scope, Node op, Node lhs, Node rhs) {
      if (NodeUtil.isName(lhs)) {
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
              || isIncDec(op) || op.getType() == Token.DELPROP);
          if (rhs != null
              && NodeUtil.isAssign(op)
              && !analyzeSet(
                new NameValueEntry(lhs), rhs, sideEffectInfo)) {
            sideEffectInfo.addNonLocalValue(var);
          }
        }
      } else if (NodeUtil.isGet(lhs)) {
        if (NodeUtil.isThis(lhs.getFirstChild())) {
          sideEffectInfo.setTaintsThis();
        } else {
          Var var = null;
          Node objectNode = lhs.getFirstChild();
          if (NodeUtil.isName(objectNode)) {
            var = scope.getVar(objectNode.getString());
          }
          if (var == null || var.scope != scope) {
            sideEffectInfo.setTaintsUnknown();
          } else {
            // Maybe a local object modification.  We won't know for sure until
            // we exit the scope and can validate the value of the local.
            //
            sideEffectInfo.addModified(var);
          }
        }
      } else {
        // TODO(johnlenz): track down what is inserting NULL on the lhs
        // of an assign.

        // The only valid lhs expressions are NAME, GETELEM, or GETPROP.
        // throw new IllegalStateException(
        //     "Unexpected lhs expression:" + lhs.toStringTree()
        //    + ", parent: " + op.toStringTree() );
        sideEffectInfo.setTaintsUnknown();
      }
    }

    /**
     * Record information about a call site.
     */
    private void visitCall(FunctionSideEffectData sideEffectInfo, Node node) {
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
    private void visitFunction(NodeTraversal traversal, Node node) {
      Preconditions.checkArgument(!functionSideEffectMap.containsKey(node));

      FunctionSideEffectData sideEffectInfo = new FunctionSideEffectData(
          inExterns, traversal.getScope());
      functionSideEffectMap.put(node, sideEffectInfo);

      if (!inExterns) {
        Scope localScope = traversal.getScope();

        // Only vars and function declarations maybe known to be local.
        // Parameters and catch exception definitions are are not tracked
        // so mark them non-local immediately.
        Iterator<Var> i = localScope.getVars();
        while (i.hasNext()) {
          Var v = i.next();
          Node parent = v.getParentNode();
          if (parent.getType() == Token.LP
              || parent.getType() == Token.CATCH) {
            // TODO(johnlenz): Allow function parameters
            sideEffectInfo.addNonLocalValue(v);
          }
        }
      }

      processFunctionAnnotations(sideEffectInfo, traversal, node);
    }

    /**
     * Update the function side effect info for any user provided annotations.
     * Reports discovered invalid usages of these annotations.
     */
    private void processFunctionAnnotations(
        FunctionSideEffectData sideEffectInfo,
        NodeTraversal traversal, Node function) {
      // Infer the locality of the return type
      if (inExterns) {
        JSType jstype = function.getJSType();
        boolean knownLocalResult = false;
        if (jstype != null && jstype.isFunctionType()) {
          FunctionType functionType = (FunctionType) jstype;
          JSType jstypeReturn = functionType.getReturnType();
          if (isLocalValueType(jstypeReturn, true)) {
            knownLocalResult = true;
          }
        }
        if (!knownLocalResult) {
          sideEffectInfo.setTaintsReturn();
        }
      }

      JSDocInfo info = getJSDocInfoForFunction(function);
      if (info != null) {
        boolean hasSpecificSideEffects = false;
        if (hasSideEffectsThisAnnotation(info)) {
          if (inExterns) {
            hasSpecificSideEffects = true;
            sideEffectInfo.setTaintsThis();
          } else {
            traversal.report(function, INVALID_MODIFIES_ANNOTATION);
          }
        }

        if (hasSideEffectsArgumentsAnnotation(info)) {
          if (inExterns) {
            hasSpecificSideEffects = true;
            sideEffectInfo.setTaintsArguments();
          } else {
            traversal.report(function, INVALID_MODIFIES_ANNOTATION);
          }
        }

        if (!hasSpecificSideEffects) {
          if (hasNoSideEffectsAnnotation(info)) {
            if (inExterns) {
              sideEffectInfo.setIsPure();
            } else {
              traversal.report(function, INVALID_NO_SIDE_EFFECT_ANNOTATION);
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
    private boolean isLocalValueType(JSType jstype, boolean recurse) {
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
    private void visitThrow(FunctionSideEffectData sideEffectInfo) {
      sideEffectInfo.setFunctionThrows();
    }

    /**
     * Get the doc info associated with the function.
     */
    private JSDocInfo getJSDocInfoForFunction(Node node) {
      Node parent = node.getParent();
      JSDocInfo info = node.getJSDocInfo();
      if (info != null) {
        return info;
      } else if (NodeUtil.isName(parent)) {
        Node gramp = parent.getParent();
        return gramp.hasOneChild() ? gramp.getJSDocInfo() : null;
      } else if (NodeUtil.isAssign(parent)) {
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
   * Check local values and updates the value influence map.
   * @param lValue A var name or token value used in the value influence map.
   *
   * @return Whether the node is maybe to be a value that has not escaped
   *     the local scope.
   */
  private static boolean analyzeSet(
      final ValueEntry lValue,
      Node rValue,
      final FunctionSideEffectData info) {

    final boolean isReturnResult = lValue.equals(KeywordValueEntry.RETURN);

    // Create a predicate for NodeUtil#evaluatesToLocalValue.
    final Predicate<Node> taintingPredicate = new Predicate<Node>() {
      @Override
      public boolean apply(Node value) {
        switch (value.getType()) {
          case Token.ASSIGN:
            // Check the LHS of the assignment, the RHS will be evaluated
            // separately.  If the LHS is a local NAME add that dependency on
            // that value as it is a alias of the RHS value.

            // Aliases of immutable RHS values don't matter, but should already
            // be filtered out.
            Preconditions.checkState(
                !NodeUtil.isImmutableValue(value.getLastChild()));
            Node lhs = value.getFirstChild();
            if (NodeUtil.isName(lhs)
                && info.getScope().isDeclared(lhs.getString(), false)) {
              addValue(lhs);
              return true;
            }
            // Don't attempt to track an aliasing property assignment.
            return false;
          case Token.THIS:
            // changes to "this" now change "key", changes to "key" will
            // also infer a change to "this".
            addValue(KeywordValueEntry.THIS);
            return true;
          case Token.NAME:
            // add to local tainting list, if the NAME
            // is known to be a local.
            if (info.getScope().isDeclared(value.getString(), false)) {
              addValue(value);
              return true;
            }
            return false;
          case Token.CALL:
            // For calls the taint is the actual call node.
            addCall(value);
            return true;
          case Token.GETELEM:
          case Token.GETPROP:
            // There is no information about the locality of object properties.
            return false;
          default:
            throw new IllegalStateException("unexpected");
        }
      }

      private void addCall(Node callNode) {
        Preconditions.checkState(callNode.getType() == Token.CALL);
        info.addInfluence(new CallValueEntry(callNode), lValue);
      }

      private void addValue(Node name) {
        ValueEntry value = new NameValueEntry(name);
        addValue(value);
      }

      private void addValue(ValueEntry value) {
        // The value and keys maybe aliases
        info.addInfluence(value, lValue);
        if (!isReturnResult) {
          info.addInfluence(lValue, value);
        }
      }
    };

    // Walk the expression with the provided predicate.
    return NodeUtil.evaluatesToLocalValue(rValue, taintingPredicate);
  }

  private static Predicate<Node> getLocalPredicate(
      final FunctionSideEffectData caller) {
    return new Predicate<Node>() {
      @Override
      public boolean apply(Node value) {
        return caller.isLocalValue(value);
      }
    };
  }

  /**
   * Callback that propagates side effect information across call sites.
   */
  private static class SideEffectPropagationCallback
      implements EdgeCallback<FunctionSideEffectData, Node> {
    public boolean traverseEdge(final FunctionSideEffectData callee,
                                final Node callSite,
                                final FunctionSideEffectData caller) {
      Preconditions.checkArgument(callSite.getType() == Token.CALL ||
                                  callSite.getType() == Token.NEW);

      boolean changed = false;
      if (caller.isInformationStable()) {
        // There is no more useful information to be gathered.
        return false;
      }

      if (!caller.mutatesGlobalState() && callee.mutatesGlobalState()) {
        caller.setTaintsGlobalState();
        changed = true;
      }

      if (!caller.functionThrows() && callee.functionThrows()) {
        caller.setFunctionThrows();
        changed = true;
      }

      Predicate<Node> locals = getLocalPredicate(caller);
      if (callee.hasNonLocalReturnValue()) {
        if (caller.maybePropagateNonLocal(new CallValueEntry(callSite))) {
          changed = true;
        }
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
          } else if (objectNode != null && !isCallOrApply(callSite)) {
            // Exclude ".call" and ".apply" as the value may still be may be
            // null or undefined. We don't need to worry about this with a
            // direct method call because null and undefined don't have any
            // properties.
            // TODO(johnlenz): We can improve this by including literal values
            // that we know for sure are not null.

            // Modifying 'this' on a known local object doesn't change any
            // significant state.
            if (!NodeUtil.evaluatesToLocalValue(objectNode, locals)) {
              if (!caller.mutatesGlobalState()) {
                caller.setTaintsGlobalState();
                changed = true;
              }
            }
          } else if (!caller.mutatesGlobalState()) {
            caller.setTaintsGlobalState();
            changed = true;
          }
        }
      }

      if (caller.isInformationStable()) {
        // Once the caller has reach a stable state, clear the local state
        // to avoid memory being a memory hog.
        caller.clearLocalityState();
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
    Node callTarget = callSite.getFirstChild();
    if (!NodeUtil.isGet(callTarget)) {

      // "this" is not specified explicitly; call modifies global "this".
      return null;
    }

    String propString = callTarget.getLastChild().getString();
    if (propString.equals("call") || propString.equals("apply")) {
      return callTarget.getNext();
    } else {
      return callTarget.getFirstChild();
    }
  }

  private static boolean isCallOrApply(Node callSite) {
    Node callTarget = callSite.getFirstChild();
    if (NodeUtil.isGet(callTarget)) {
      String propString = callTarget.getLastChild().getString();
      if (propString.equals("call") || propString.equals("apply")) {
        return true;
      }
    }
    return false;
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
