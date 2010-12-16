/*
 * Copyright 2010 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Transforms compound assignments into simple ones.
 * <p>
 * {@code x++} and {@code x--} are unfolded to {@code x = +x + 1} and {@code x =
 * x - 1}, and assignments such as {@code x += y} are expanded to {@code x = x +
 * y}.
 * <p>
 * Notice the prefix '+' when unfolding ++. This is needed because the operand
 * is implicitly converted to a number.
 * <p>
 * These transformations can only be performed if the left-hand side of the
 * assignment has no side-effects.
 *
 * @author elnatan@google.com (Elnatan Reisner)
 */
class UnfoldCompoundAssignments implements Callback, CompilerPass {
  private final AbstractCompiler compiler;

  public UnfoldCompoundAssignments(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  /* (non-Javadoc)
   * @see Callback#shouldTraverse(NodeTraversal, Node, Node)
   */
  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
      Node parent) {
    return true;
  }

  /* (non-Javadoc)
   * @see Callback#visit(NodeTraversal, Node, Node)
   */
  @Override
  public void visit(NodeTraversal t, Node node, Node parent) {
    switch (node.getType()) {
      case Token.INC:
      case Token.DEC:
        unfoldIncrDecr(node, node.getType() == Token.INC);
        break;
      case Token.ASSIGN_BITOR:  // |=
      case Token.ASSIGN_BITXOR: // ^=
      case Token.ASSIGN_BITAND: // &=
      case Token.ASSIGN_LSH:    // <<=
      case Token.ASSIGN_RSH:    // >>=
      case Token.ASSIGN_URSH:   // >>>=
      case Token.ASSIGN_ADD:    // +=
      case Token.ASSIGN_SUB:    // -=
      case Token.ASSIGN_MUL:    // *=
      case Token.ASSIGN_DIV:    // /=
      case Token.ASSIGN_MOD:    // %=
        unfoldCompoundAssignment(node);
        break;
    }
  }

  /**
   * Unfolds ++ and -- operators into {@code x = +x + 1} and {@code x = x - 1}.
   * <p>
   * The operand gets a prefix {@code +} when unfolding an increment because
   * {@code ++} converts its operand to a number but binary {@code +} does not.
   * ({@code -} <em>does</em> convert its operands to numbers, so we don't need
   * to add a prefix {@code +} when unfolding {@code --}.)
   *
   * @param node an increment or decrement node
   * @param isIncrement true if the operator is ++; false if it is --
   */
  private void unfoldIncrDecr(Node node, boolean isIncrement) {
    Preconditions.checkArgument(
        isPrefix(node) || valueIsDiscarded(node),
        "Unfolding postfix ++/-- requires that the result be ignored.");
    Node lhs = node.getFirstChild().cloneTree();
    Preconditions.checkArgument(!NodeUtil.mayHaveSideEffects(lhs),
        "Cannot unfold compound assignment if LHS can have side effects");
    // TODO(elnatan): We might want to use type information to only add this '+'
    // when lhs isn't already a number.
    if (isIncrement) {
      lhs = new Node(Token.POS, lhs);
    }
    node.setType(Token.ASSIGN);
    Node rhs = new Node(isIncrement ? Token.ADD : Token.SUB,
        lhs, Node.newNumber(1));
    rhs.copyInformationFromForTree(node);
    node.addChildToBack(rhs);
    compiler.reportCodeChange();
  }

  /**
   * Returns true if the node's value is discarded.
   * <p>
   * The value is discarded if node is
   * <ul>
   * <li>the child of an EXPR_RESULT,
   * <li>the first child of a COMMA, or
   * <li>the increment of a FOR loop.
   * </ul>
   */
  private boolean valueIsDiscarded(Node node) {
    Node parent = node.getParent();
    switch (parent.getType()) {
      case Token.EXPR_RESULT:
        return true;
      case Token.COMMA:
        return parent.getFirstChild() == node;
      case Token.FOR:
        Preconditions.checkArgument(!NodeUtil.isForIn(parent),
            "Error: the child of a FOR-IN cannot be an INC or DEC");
        return parent.getChildAtIndex(2) == node;
    }
    return false;
  }

  /**
   * @param node an INC or DEC node
   * @return true if the increment/decrement is prefix; false if postfix
   */
  private boolean isPrefix(Node node) {
    Preconditions.checkArgument(
        node.getType() == Token.INC || node.getType() == Token.DEC,
        "isPrefix can only be called on INC and DEC nodes");
    // According to CodeGenerator:
    // A non-zero post-prop value indicates a post inc/dec, default of zero is a
    // pre-inc/dec.
    return node.getIntProp(Node.INCRDECR_PROP) == 0;
  }

  /**
   * Unfolds a compound assignment node {@code lhs op= rhs} to {@code lhs = lhs
   * op rhs}.
   *
   * @param node a compound assignment node
   */
  private void unfoldCompoundAssignment(Node node) {
    Node lhs = node.getFirstChild();
    Preconditions.checkArgument(!NodeUtil.mayHaveSideEffects(lhs),
        "Cannot unfold compound assignment if LHS can have side effects");
    Node newRhs = node.cloneTree();
    newRhs.setType(NodeUtil.getOpFromAssignmentOp(node));
    node.replaceChildAfter(lhs, newRhs);
    node.setType(Token.ASSIGN);
    compiler.reportCodeChange();
  }

  /* (non-Javadoc)
   * @see CompilerPass#process(Node, Node)
   */
  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized(),
        "UnfoldCompoundAssignments requires a normalized AST");
    NodeTraversal.traverse(compiler, root, this);
  }
}
