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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;

/**
 * Instruments functions for when functions first get called and defined.
 *
 * This pass be used to instrument code to:
 *  1. Gather statistics from real users in the wild.
 *  2. Incorporate utilization statistics into Selenium tests
 *  3. Access utilization statistics from an app's debug UI
 *
 * By parametrizing the whole instrumentation process we expect to be
 * able to support a wide variety of use cases and minimize the cost
 * of developing new instrumentation schemes.
 *
 * TODO(user): This pass currently runs just before the variable and
 * property renaming near the end of the optimization pass.  I think
 * Mark put it there to minimize the difference between the code
 * generated with/without instrumentation; instrumentation makes
 * several optimization passes do less, for example inline functions.
 *
 * My opinion is that we want utilization/profiling information for
 * all function.  This pass should run before most passes that modify
 * the AST (exception being the localization pass, which makes
 * assumptions about the structure of the AST).  We should move the
 * pass up, list inlined functions or give clients the option to
 * instrument before or after optimization.
 *
 */
class InstrumentFunctions implements CompilerPass {

  private final AbstractCompiler compiler;
  private final FunctionNames functionNames;
  private final String appNameStr;
  private final String initCodeSource;
  private final String definedFunctionName;
  private final String reportFunctionName;
  private final String reportFunctionExitName;
  private final String appNameSetter;
  private final List<String> declarationsToRemove;

  /**
   * Creates an instrument functions compiler pass.
   *
   * @param compiler          The JSCompiler
   * @param functionNames     Assigned function identifiers.
   * @param template          Instrumentation template; for use during error reporting only.
   * @param appNameStr        String to pass to appNameSetter.
   */
  InstrumentFunctions(AbstractCompiler compiler,
                      FunctionNames functionNames,
                      Instrumentation template,
                      String appNameStr) {
    this.compiler = compiler;
    this.functionNames = functionNames;
    this.appNameStr = appNameStr;

    StringBuilder initCodeSourceBuilder = new StringBuilder();
    for (String line : template.getInitList()) {
      initCodeSourceBuilder.append(line).append("\n");
    }
    this.initCodeSource = initCodeSourceBuilder.toString();

    this.definedFunctionName = template.getReportDefined();
    this.reportFunctionName = template.getReportCall();
    this.reportFunctionExitName = template.getReportExit();
    this.appNameSetter = template.getAppNameSetter();

    this.declarationsToRemove = ImmutableList.copyOf(
        template.getDeclarationToRemoveList());
  }

  @Override
  public void process(Node externs, Node root) {
    Node initCode = null;
    if (!initCodeSource.isEmpty()) {
      Node initCodeRoot = compiler.parseSyntheticCode(
          "template:init", initCodeSource);
      if (initCodeRoot != null && initCodeRoot.getFirstChild() != null) {
        initCode = initCodeRoot.removeChildren();
      } else {
        return;  // parse failure
      }
    }

    NodeTraversal.traverseEs6(compiler, root,
                           new RemoveCallback(declarationsToRemove));
    NodeTraversal.traverseEs6(compiler, root, new InstrumentCallback());

    if (!appNameSetter.isEmpty()) {
      Node call = IR.call(
          IR.name(appNameSetter),
          IR.string(appNameStr));
      call.putBooleanProp(Node.FREE_CALL, true);
      Node expr = IR.exprResult(call);

      Node addingRoot = compiler.getNodeForCodeInsertion(null);
      addingRoot.addChildrenToFront(expr.useSourceInfoIfMissingFromForTree(addingRoot));
      compiler.reportCodeChange();
    }

    if (initCode != null) {
      Node addingRoot = compiler.getNodeForCodeInsertion(null);
      addingRoot.addChildrenToFront(initCode);
      compiler.reportCodeChange();
    }
  }

  /**
   * The application must refer to these variables to output them so the
   * application must also declare these variables for the first
   * {@link VarCheck} pass. These declarations must be removed before the
   * second {@link VarCheck} pass. Otherwise, the second pass would warn about
   * duplicate declarations.
   */
  private static class RemoveCallback extends AbstractPostOrderCallback {
    private final List<String> removable;
    RemoveCallback(List<String> removable) {
      this.removable = removable;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isVarDeclaration(n) && removable.contains(n.getString())) {
        parent.removeChild(n);
        if (!parent.hasChildren()) {
          parent.getParent().removeChild(parent);
        }
      }
    }
  }

  /**
   * Traverse a function's body by instrument return sites by
   * inserting calls to {@code reportFunctionExitName}.  If the
   * function is missing an explicit return statement in some control
   * path, this pass inserts a call to {@code reportFunctionExitName}
   * as the last statement in the function's body.
   *
   * Example:
   * Input:
   * function f() {
   *   if (pred) {
   *     return a;
   *   }
   * }
   *
   * Template:
   * reportFunctionExitName: "onExitFn"
   *
   * Output:
   * function f() {
   *   if (pred) {
   *     return onExitFn(0, a, "f");
   *   }
   *   onExitFn(0, undefined, "f");
   * }
   *
   **/
  private class InstrumentReturns implements NodeTraversal.Callback {
    private final int functionId;
    private final String functionName;

    /**
     * @param functionId Function identifier computed by FunctionNames;
     *     used as first argument to {@code reportFunctionExitName}
     *     {@code reportFunctionExitName} must be a 3 argument function that
     *     returns it's second argument.
     * @param functionName Function name.
     */
    InstrumentReturns(int functionId, String functionName) {
      this.functionId = functionId;
      this.functionName = functionName;
    }

    /**
     * @param function function with id == this.functionId
     */
    void process(Node function) {
      Node body = function.getLastChild();
      NodeTraversal.traverseEs6(compiler, body, this);

      if (!allPathsReturn(function)) {
        Node call = newReportFunctionExitNode(function, null);
        Node expr = IR.exprResult(call).useSourceInfoIfMissingFromForTree(function);
        body.addChildToBack(expr);
        compiler.reportCodeChange();
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isFunction();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isReturn()) {
        return;
      }

      Node returnRhs = n.removeFirstChild();
      Node call = newReportFunctionExitNode(n, returnRhs);
      n.addChildToFront(call);
      compiler.reportCodeChange();
    }

    private Node newReportFunctionExitNode(Node infoNode, Node returnRhs) {
      Node call = IR.call(
          IR.name(reportFunctionExitName),
          IR.number(functionId),
          (returnRhs != null) ? returnRhs : IR.name("undefined"),
          IR.string(functionName));
      call.putBooleanProp(Node.FREE_CALL, true);
      call.useSourceInfoFromForTree(infoNode);
      return call;
    }

    /**
     * @return true if all paths from block must exit with an explicit return.
     */
    private boolean allPathsReturn(Node function) {
      // Computes the control flow graph.
      ControlFlowAnalysis cfa = new ControlFlowAnalysis(
          compiler, false, false);
      cfa.process(null, function);
      ControlFlowGraph<Node> cfg = cfa.getCfg();

      Node returnPathsParent = cfg.getImplicitReturn().getValue();
      for (DiGraphNode<Node, Branch> pred :
        cfg.getDirectedPredNodes(returnPathsParent)) {
        Node n = pred.getValue();
        if (!n.isReturn()) {
          return false;
        }
      }
      return true;
    }
  }

  private class InstrumentCallback extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isFunction()) {
        return;
      }

      int id = functionNames.getFunctionId(n);
      if (id < 0) {
        // Function node was added during compilation; don't instrument.
        return;
      }

      String name = functionNames.getFunctionName(n);

      if (!reportFunctionName.isEmpty()) {
        Node body = n.getLastChild();
        Node call = IR.call(
            IR.name(reportFunctionName),
            IR.number(id),
            IR.string(name),
            IR.name("arguments"));
        call.putBooleanProp(Node.FREE_CALL, true);
        Node expr = IR.exprResult(call);
        expr.useSourceInfoFromForTree(n);
        body.addChildToFront(expr);
        compiler.reportCodeChange();
      }

      if (!reportFunctionExitName.isEmpty()) {
        (new InstrumentReturns(id, name)).process(n);
      }

      if (!definedFunctionName.isEmpty()) {
        Node call = IR.call(
            IR.name(definedFunctionName),
            IR.number(id),
            IR.string(name));
        call.putBooleanProp(Node.FREE_CALL, true);
        call.useSourceInfoFromForTree(n);
        Node expr = NodeUtil.newExpr(call);

        Node addingRoot = null;
        if (NodeUtil.isFunctionDeclaration(n)) {
          JSModule module = t.getModule();
          addingRoot = compiler.getNodeForCodeInsertion(module);
          addingRoot.addChildToFront(expr);
        } else {
          Node beforeChild = n;
          for (Node ancestor : n.getAncestors()) {
            int type = ancestor.getType();
            if (type == Token.BLOCK || type == Token.SCRIPT) {
              addingRoot = ancestor;
              break;
            }
            beforeChild = ancestor;
          }
          addingRoot.addChildBefore(expr, beforeChild);
        }
        compiler.reportCodeChange();
      }
    }
  }
}
