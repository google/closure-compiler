/*
 * Copyright 2016 The Closure Compiler Authors.
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
import com.google.common.base.Strings;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * An optimziation pass for J2CL-generated code to help prune class initializers that would be seen
 * as having side-effects.
 */
public class J2clEmptyClinitPrunerPass implements CompilerPass {

  private final AbstractCompiler compiler;

  J2clEmptyClinitPrunerPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, new EmptyClinitPruner());
  }

  /**
   * A traversal callback that sets equivalently empty clinits to the empty function.
   *
   * <p>A clinit is considered empty if the following criteria is met:
   *
   * <ol>
   * <li>The first statement must set itself to be the empty function
   * <li>All other statements in the body of the function must be recursive calls to itself.
   * </ol>
   */
  private static final class EmptyClinitPruner extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      if (!isClinitMethod(node)) {
        return;
      }

      trySubstituteEmptyFunction(node, t.getCompiler());
    }

    private static boolean isClinitMethod(Node fnNode) {
      if (!fnNode.isFunction()) {
        return false;
      }

      String fnName = NodeUtil.getName(fnNode);

      return fnName != null && (fnName.endsWith("$$0clinit") || fnName.endsWith(".$clinit"));
    }

    /**
     * Clears the body of any functions are are equivalent to empty functions.
     */
    private Node trySubstituteEmptyFunction(Node fnNode, AbstractCompiler compiler) {
      Preconditions.checkArgument(fnNode.isFunction());

      String fnQualifiedName = NodeUtil.getName(fnNode);

      // Ignore anonymous/constructor functions.
      if (Strings.isNullOrEmpty(fnQualifiedName)) {
        return fnNode;
      }

      Node body = fnNode.getLastChild();
      if (!body.hasChildren()) {
        return fnNode;
      }

      // Ensure that the first expression in the body is setting itself to the empty function and
      // that all other statements are only calls to itself.
      Node firstExpr = body.getFirstChild();
      if (firstExpr.isExprResult()
          && isAssignToEmptyFn(firstExpr.getFirstChild(), fnQualifiedName)
          && siblingsOnlyCallTarget(firstExpr.getNext(), fnQualifiedName)) {
        body.removeChildren();
        compiler.reportCodeChange();
      }

      return fnNode;
    }

    private static boolean isAssignToEmptyFn(Node assignNode, String enclosingFnName) {
      if (!assignNode.isAssign()) {
        return false;
      }

      Node lhs = assignNode.getFirstChild();
      Node rhs = assignNode.getLastChild();

      // If the RHS is not an empty function then this isn't of interest.
      if (!NodeUtil.isEmptyFunctionExpression(rhs)) {
        return false;
      }

      // Ensure that we are actually mutating the given function.
      return lhs.getQualifiedName() != null && lhs.matchesQualifiedName(enclosingFnName);
    }

    private static boolean siblingsOnlyCallTarget(Node expr, String targetName) {
      while (expr != null) {
        if (!expr.isExprResult()
            || !expr.getFirstChild().isCall()
            || !isCallToNode(expr.getFirstChild(), targetName)) {
          return false;
        }
        expr = expr.getNext();
      }
      return true;
    }

    private static boolean isCallToNode(Node callNode, String targetName) {
      Preconditions.checkArgument(callNode.isCall());

      return callNode.getFirstChild() != null
          && callNode.getFirstChild().matchesQualifiedName(targetName);
    }
  }
}
