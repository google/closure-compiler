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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CheckPathsBetweenNodes;
import com.google.javascript.jscomp.ControlFlowGraph;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;

/**
 * Checks when a function is annotated as returning {SomeType} (nullable)
 * but actually always returns {!SomeType}, i.e. never returns null.
 *
 */
public final class CheckNullableReturn implements HotSwapCompilerPass, NodeTraversal.Callback {
  final AbstractCompiler compiler;

  public static final DiagnosticType NULLABLE_RETURN =
      DiagnosticType.warning(
          "JSC_NULLABLE_RETURN",
          "This function''s return type is nullable, but it always returns a "
          + "non-null value. Consider making the return type non-nullable.");

  public static final DiagnosticType NULLABLE_RETURN_WITH_NAME =
      DiagnosticType.warning(
          "JSC_NULLABLE_RETURN_WITH_NAME",
          "The return type of the function \"{0}\" is nullable, but it always "
          + "returns a non-null value. Consider making the return type "
          + "non-nullable.");

  private static final Predicate<Node> NULLABLE_RETURN_PREDICATE =
      new Predicate<Node>() {
    @Override
    public boolean apply(Node input) {
      // Check for null because the control flow graph's implicit return node is
      // represented by null, so this value might be input.
      if (input == null || !input.isReturn()) {
        return false;
      }
      Node returnValue = input.getFirstChild();
      return returnValue != null && isNullable(returnValue);
    }
  };

  public CheckNullableReturn(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public static boolean hasReturnDeclaredNullable(Node n) {
    return n.isBlock() && n.hasChildren() && isReturnTypeNullable(n.getParent())
        && !hasSingleThrow(n);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // Do the checks when 'n' is the block node and 'parent' is the function
    // node, so that getControlFlowGraph will return the graph inside
    // the function, rather than the graph of the enclosing scope.
    if (hasReturnDeclaredNullable(n)
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
   * @return whether the blockNode contains only a single "throw" child node.
   */
  private static boolean hasSingleThrow(Node blockNode) {
    if (blockNode.getChildCount() == 1
        && blockNode.getFirstChild().getType() == Token.THROW) {
      // Functions consisting of a single "throw FOO" can be actually abstract,
      // so do not check their return type nullability.
      return true;
    }

    return false;
  }

  /**
   * @return True if n is a function node which is explicitly annotated
   * as returning a nullable type, other than {?}.
   */
  private static boolean isReturnTypeNullable(Node n) {
    if (n == null || !n.isFunction()) {
      return false;
    }
    FunctionType functionType = n.getJSType().toMaybeFunctionType();
    if (functionType == null) {
      // If the JSDoc declares a non-function type on a function node, we still shouldn't crash.
      return false;
    }
    JSType returnType = functionType.getReturnType();
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
  public static boolean canReturnNull(ControlFlowGraph<Node> graph) {
    CheckPathsBetweenNodes<Node, ControlFlowGraph.Branch> test = new CheckPathsBetweenNodes<>(graph,
        graph.getEntry(), graph.getImplicitReturn(), NULLABLE_RETURN_PREDICATE,
        Predicates.<DiGraphEdge<Node, ControlFlowGraph.Branch>>alwaysTrue());

    return test.somePathsSatisfyPredicate();
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
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, originalRoot, this);
  }
}
