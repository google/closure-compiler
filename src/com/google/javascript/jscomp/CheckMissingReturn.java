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

import com.google.common.base.Predicate;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.TernaryValue;

/**
 * Checks functions for missing return statements. Return statements are only
 * expected for functions with return type information. Functions with empty
 * bodies are ignored.
 *
 */
class CheckMissingReturn implements ScopedCallback {

  static final DiagnosticType MISSING_RETURN_STATEMENT =
      DiagnosticType.warning(
          "JSC_MISSING_RETURN_STATEMENT",
          "Missing return statement. Function expected to return {0}.");

  private final AbstractCompiler compiler;
  private final CheckLevel level;

  private static final Predicate<Node> IS_RETURN = new Predicate<Node>() {
    @Override
    public boolean apply(Node input) {
      // Check for null because the control flow graph's implicit return node is
      // represented by null, so this value might be input.
      return input != null && input.isReturn();
    }
  };

  /* Skips all exception edges and impossible edges. */
  private static final Predicate<DiGraphEdge<Node, ControlFlowGraph.Branch>>
      GOES_THROUGH_TRUE_CONDITION_PREDICATE =
        new Predicate<DiGraphEdge<Node, ControlFlowGraph.Branch>>() {
    @Override
    public boolean apply(DiGraphEdge<Node, ControlFlowGraph.Branch> input) {
      // First skill all exceptions.
      Branch branch = input.getValue();
      if (branch == Branch.ON_EX) {
        return false;
      } else if (branch.isConditional()) {
        Node condition = NodeUtil.getConditionExpression(
            input.getSource().getValue());
        // TODO(user): We CAN make this bit smarter just looking at
        // constants. We DO have a full blown ReverseAbstractInterupter and
        // type system that can evaluate some impressions' boolean value but
        // for now we will keep this pass lightweight.
        if (condition != null) {
          TernaryValue val = NodeUtil.getImpureBooleanValue(condition);
          if (val != TernaryValue.UNKNOWN) {
            return val.toBoolean(true) == (Branch.ON_TRUE == branch);
          }
        }
      }
      return true;
    }
  };

  /**
   * @param level level of severity to report when a missing return statement
   *     is discovered
   */
  CheckMissingReturn(AbstractCompiler compiler, CheckLevel level) {
    this.compiler = compiler;
    this.level = level;
  }

  @Override
  public void enterScope(NodeTraversal t) {
    JSType returnType = explicitReturnExpected(t.getScopeRoot());
    if (returnType == null) {
      return;
    }

    if (fastAllPathsReturnCheck(t.getControlFlowGraph())) {
      return;
    }

    CheckPathsBetweenNodes<Node, ControlFlowGraph.Branch> test =
        new CheckPathsBetweenNodes<Node, ControlFlowGraph.Branch>(
            t.getControlFlowGraph(),
            t.getControlFlowGraph().getEntry(),
            t.getControlFlowGraph().getImplicitReturn(),
            IS_RETURN, GOES_THROUGH_TRUE_CONDITION_PREDICATE);

    if (!test.allPathsSatisfyPredicate()) {
      compiler.report(
          t.makeError(t.getScopeRoot(), level,
              MISSING_RETURN_STATEMENT, returnType.toString()));
    }
  }

  /**
   * Fast check to see if all execution paths contain a return statement.
   * May spuriously report that a return statement is missing.
   *
   * @return true if all paths return, converse not necessarily true
   */
  private static boolean fastAllPathsReturnCheck(ControlFlowGraph<Node> cfg) {
    for (DiGraphEdge<Node, Branch> s : cfg.getImplicitReturn().getInEdges()) {
      if (!s.getSource().getValue().isReturn()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void exitScope(NodeTraversal t) {
  }

  @Override
  public boolean shouldTraverse(
      NodeTraversal nodeTraversal, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
  }

  /**
   * Determines if the given scope should explicitly return. All functions
   * with non-void or non-unknown return types must have explicit returns.
   *
   * Exception: Constructors which specifically specify a return type are
   * used to allow invocation without requiring the "new" keyword. They
   * have an implicit return type. See unit tests.
   *
   * @return If a return type is expected, returns it. Otherwise, returns null.
   */
  private JSType explicitReturnExpected(Node scope) {
    FunctionType scopeType = JSType.toMaybeFunctionType(scope.getJSType());

    if (scopeType == null) {
      return null;
    }

    if (isEmptyFunction(scope)) {
      return null;
    }

    if (scopeType.isConstructor()) {
      return null;
    }

    JSType returnType = scopeType.getReturnType();

    if (returnType == null) {
      return null;
    }

    if (!isVoidOrUnknown(returnType)) {
      return returnType;
    }

    return null;
  }

  /**
   * @return {@code true} if function represents a JavaScript function
   *     with an empty body
   */
  private static boolean isEmptyFunction(Node function) {
    return function.getChildCount() == 3 &&
           !function.getFirstChild().getNext().getNext().hasChildren();
  }

  /**
   * @return {@code true} if returnType is void, unknown, or a union
   *     containing void or unknown
   */
  private boolean isVoidOrUnknown(JSType returnType) {
    final JSType voidType =
        compiler.getTypeRegistry().getNativeType(JSTypeNative.VOID_TYPE);
    return voidType.isSubtype(returnType);
  }
}
