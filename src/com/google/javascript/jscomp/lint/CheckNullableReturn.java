/*
 * Copyright 2014 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.ControlFlowGraph;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;

/**
 * Checks when a function is annotated as returning {SomeType} (nullable)
 * but actually always returns {!SomeType}, i.e. never returns null.
 *
 */
public class CheckNullableReturn implements HotSwapCompilerPass, NodeTraversal.Callback {
  final AbstractCompiler compiler;

  public static final DiagnosticType NULLABLE_RETURN =
      DiagnosticType.disabled(
          "JSC_NULLABLE_RETURN",
          "This function''s return type is nullable, but it always returns a "
          + "non-null value. Consider making the return type non-nullable.");

  public static final DiagnosticType NULLABLE_RETURN_WITH_NAME =
      DiagnosticType.disabled(
          "JSC_NULLABLE_RETURN_WITH_NAME",
          "The return type of the function \"{0}\" is nullable, but it always "
          + "returns a non-null value. Consider making the return type "
          + "non-nullable.");

  public CheckNullableReturn(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // Do the checks when 'n' is the block node and 'parent' is the function
    // node, so that getControlFlowGraph will return the graph inside
    // the function, rather than the graph of the enclosing scope.
    if (n.isBlock() && n.hasChildren() && isReturnTypeNullable(parent)
        && !canReturnNull(t.getControlFlowGraph())) {
      String fnName = NodeUtil.getNearestFunctionName(parent);
      if (fnName != null && !fnName.isEmpty()) {
        compiler.report(t.makeError(parent, NULLABLE_RETURN_WITH_NAME, fnName));
      } else {
        compiler.report(t.makeError(parent, NULLABLE_RETURN));
      }
    }
  }

  /**
   * @return True if n is a function node which is explicitly annotated
   * as returning a nullable type, other than {?}.
   */
  private static boolean isReturnTypeNullable(Node n) {
    if (n == null) {
      return false;
    }
    if (!n.isFunction()) {
      return false;
    }
    JSType returnType = n.getJSType().toMaybeFunctionType().getReturnType();
    if (returnType == null
        || returnType.isUnknownType() || !returnType.isNullable()) {
      return false;
    }
    JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
    return info != null && info.hasReturnType();
  }

  /**
   * @return True if the given ControlFlowGraph could return null.
   */
  private static boolean canReturnNull(ControlFlowGraph<Node> graph) {
    DiGraph.DiGraphNode<Node, ControlFlowGraph.Branch> ir = graph.getImplicitReturn();
    for (DiGraph.DiGraphEdge<Node, ControlFlowGraph.Branch> inEdge : ir.getInEdges()) {
      DiGraph.DiGraphNode<Node, ControlFlowGraph.Branch> graphNode = inEdge.getSource();
      Node possibleReturnNode = graphNode.getValue();
      if (possibleReturnNode.isReturn()) {
        Node returnValue = possibleReturnNode.getFirstChild();
        if (returnValue != null && isNullable(returnValue)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return True if the node represents a nullable value. Essentially, this
   *     is just n.getJSType().isNullable(), but for purposes of this pass,
   *     the expression {@code x || null} is considered nullable even if
   *     x is always truthy. This often happens with expressions like
   *     {@code arr[i] || null}: The compiler doesn't know that arr[i] can
   *     be undefined.
   */
  private static boolean isNullable(Node n) {
    return n.getJSType().isNullable()
        || (n.isOr() && n.getLastChild().isNull());
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    return true;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, originalRoot, this);
  }
}
