/*
 * Copyright 2013 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Enforces that invocations of the method {@code goog.string.Const.from} are
 * done with an argument which is a string literal.
 *
 * <p>This function parameter checker enforces that for all invocations of
 * method {@code goog.string.Const.from} the actual argument satisfies one of
 * the following conditions:
 * <ol>
 * <li>The argument is an expression that is a string literal or concatenation
 * thereof, or
 * <li>The argument is a constant variable previously assigned from a string
 * literal or concatenation thereof.
 * </ol>
 */
class ConstParamCheck extends AbstractPostOrderCallback
    implements CompilerPass {

  private static final String CONST_FUNCTION_NAME = "goog.string.Const.from";

  @VisibleForTesting
  static final DiagnosticType CONST_NOT_STRING_LITERAL_ERROR =
      DiagnosticType.error("JSC_CONSTANT_NOT_STRING_LITERAL_ERROR",
          "Function argument is not a string literal or a constant assigned "
          + "from a string literal");

  @VisibleForTesting
  static final DiagnosticType CONST_NOT_ASSIGNED_STRING_LITERAL_ERROR =
      DiagnosticType.error("JSC_CONSTANT_NOT_ASSIGNED_STRING_LITERAL_ERROR",
          "Function argument is a variable {0} which is not a constant "
          + "assigned from a string literal");

  private final AbstractCompiler compiler;

  public ConstParamCheck(AbstractCompiler compiler) {
    this.compiler = Preconditions.checkNotNull(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());
    NodeTraversal.traverse(compiler, root, this);
  }

  /**
   * Callback to visit a node and check method call arguments of
   * {@code goog.string.Const.from}.
   *
   * @param traversal The node traversal object that supplies context, such as
   *        the scope chain to use in name lookups as well as error reporting.
   * @param node The node being visited.
   * @param parent The parent of the node.
   */
  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    if (node.getType() == Token.CALL) {
      Node name = node.getFirstChild();
      Node argument = name.getNext();
      if (argument == null) {
        return;
      }

      if (name.isName()) {
        Scope scope = traversal.getScope();
        Var var = scope.getVar(name.getString());
        if (var == null) {
          return;
        }
        name = var.getInitialValue();
        if (name == null) {
          return;
        }
      }

      // goog.string.Const.from('constant')
      if (name.matchesQualifiedName(CONST_FUNCTION_NAME)) {
        checkArgumentConstant(traversal, argument);
      }
    }
  }

  /**
   * Check the method call argument to be constant string literal.
   *
   * <p>This function argument checker will yield an error if:
   * <ol>
   * <li>The argument is not a constant variable, or
   * <li>The constant variable is not assigned from string literal or
   * concatenation thereof, or
   * <li>The argument is not an expression that is a string literal or
   * concatenation thereof.
   * </ol>
   *
   * @param traversal The node traversal object that supplies context, such as
   *        the scope chain to use in name lookups as well as error reporting.
   * @param argument The node of function argument to check.
   */
  private void checkArgumentConstant(NodeTraversal traversal, Node argument) {
    if (argument.isName()) {
      String name = argument.getString();
      Scope scope = traversal.getScope();
      Var var = scope.getVar(name);
      if (var == null || !var.isInferredConst()) {
        compiler.report(traversal.makeError(
            argument, CONST_NOT_STRING_LITERAL_ERROR, name));
        return;
      }

      Node valueNode = var.getInitialValue();
      if (!isStringLiteralValue(valueNode)) {
        compiler.report(traversal.makeError(
            argument, CONST_NOT_ASSIGNED_STRING_LITERAL_ERROR, name));
      }
    } else {
      if (!isStringLiteralValue(argument)) {
        compiler.report(
            traversal.makeError(argument, CONST_NOT_STRING_LITERAL_ERROR));
      }
    }
  }

  /**
   * Returns true iff the value associated with the node is a JS string literal,
   * or a concatenation thereof.
   */
  private static boolean isStringLiteralValue(Node node) {
    if (node.getType() == Token.STRING) {
      return true;
    } else if (node.getType() == Token.ADD) {
      Preconditions.checkState(node.getChildCount() == 2);
      Node left = node.getFirstChild();
      Node right = node.getLastChild();
      return isStringLiteralValue(left) && isStringLiteralValue(right);
    }
    return false;
  }
}
