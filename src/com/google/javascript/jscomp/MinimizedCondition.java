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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collections;
import java.util.Comparator;

/**
 * A class that represents a minimized conditional expression.
 * This is a conditional expression that has been massaged according to
 * DeMorgan's laws in order to minimize the length of the source
 * representation.
 * <p>
 * Depending on the context, a leading NOT node in front of the conditional
 * may or may not be counted as a cost, so this class provides ways to
 * access minimized versions of both of those abstract syntax trees (ASTs).
 *
 * @author blickly@google.com (Ben Lickly)
 */
class MinimizedCondition {

  /** Definitions of the style of minimization preferred. */
  enum MinimizationStyle {
    /** Compute the length of the minimized condition as including
     *  any leading NOT node, if present. */
    PREFER_UNNEGATED,
    /** Compute the length of the minimized condition without penalizing
     *  a leading NOT node, if present. */
    ALLOW_LEADING_NOT
  }

  /** A representation equivalent to the original condition. */
  private final MeasuredNode positive;
  /** A representation equivalent to the negation of the original condition. */
  private final MeasuredNode negative;

  /** A placeholder at the same AST location as the original condition */
  private Node placeholder;

  private MinimizedCondition(MeasuredNode p, MeasuredNode n) {
    Preconditions.checkArgument(p.node.getParent() == null);
    Preconditions.checkArgument(n.node.getParent() == null);
    positive = p;
    negative = n.change();
  }

  Node getPlaceholder() {
    return placeholder;
  }

  MinimizedCondition setPlaceholder(Node placeholder) {
    this.placeholder = placeholder;
    return this;
  }

  /**
   * Remove the passed condition node from the AST, and then return a
   * MinimizedCondition that represents the condition node after
   * minimization.
   */
  static MinimizedCondition fromConditionNode(Node n) {
    switch (n.getType()) {
      case Token.NOT:
      case Token.AND:
      case Token.OR:
      case Token.HOOK:
      case Token.COMMA:
        Node placeholder = swapWithPlaceholderNode(n);
        return computeMinimizedCondition(n).setPlaceholder(placeholder);
      default:
        return unoptimized(n);
    }
  }

  /**
   * Return the shorter representation of the original condition node.
   * <p>
   * Depending on the context, this may require to either penalize or
   * not the existence of a leading NOT node.
   * <ul><li>When {@code style} is {@code PREFER_UNNEGATED}, simply try to
   * minimize the total length of the conditional.</li>
   * <li>When {@code style} is {@code ALLOW_LEADING_NOT}, prefer the right side
   * in cases such as:
   * <br><code>
   *    !x || !y || z  ==>  !(x && y && !z)
   * </code><br>
   * This is useful in contexts such as IFs or HOOKs where subsequent
   * optimizations can efficiently deal with leading NOTs.
   * </li></ul>
   *
   * @return the minimized condition MeasuredNode, with equivalent semantics
   *   to that passed to {@link #fromConditionNode}.
   */
  MeasuredNode getMinimized(MinimizationStyle style) {
    if (style == MinimizationStyle.PREFER_UNNEGATED
        || positive.node.isNot()
        || positive.length <= negative.length) {
      return positive;
    } else {
      return negative.addNot();
    }
  }

  /**
   * Return a MeasuredNode of the given condition node, without minimizing
   * the result.
   * <p>
   * Since a MinimizedCondition necessarily must contain two trees, this
   * method sets the negative side to a {@link Token#SCRIPT} node (never valid
   * inside an expression) with an unreasonably high length so that it will
   * never be chosen by {@link #getMinimized}.
   *
   * @param n the conditional expression tree to minimize.
   *  This must be connected to the AST, and will be swapped
   *  with a placeholder node during minimization.
   * @return a MinimizedCondition object representing that tree.
   */
  static MinimizedCondition unoptimized(Node n) {
    Preconditions.checkNotNull(n.getParent());
    Node placeholder = swapWithPlaceholderNode(n);
    MeasuredNode pos = new MeasuredNode(n, 0, false);
    MeasuredNode neg = new MeasuredNode(IR.script(), Integer.MAX_VALUE, true);
    return new MinimizedCondition(pos, neg).setPlaceholder(placeholder);
  }

  /**
   * Remove the given node from the AST, and replace it with a placeholder
   * SCRIPT node.
   * @return the new placeholder node.
   */
  private static Node swapWithPlaceholderNode(Node n) {
    Preconditions.checkNotNull(n.getParent());
    Node placeholder = IR.script();
    n.getParent().replaceChild(n, placeholder);
    return placeholder;
  }

  /**
   * Minimize the condition at the given node.
   *
   * @param n the conditional expression tree to minimize.
   *  This must be connected to the AST, and will be swapped
   *  with a placeholder node during minimization.
   * @return a MinimizedCondition object representing that tree.
   */
  private static MinimizedCondition computeMinimizedCondition(Node n) {
    Preconditions.checkArgument(n.getParent() == null);
    switch (n.getType()) {
      case Token.NOT: {
        MinimizedCondition subtree =
            computeMinimizedCondition(n.getFirstChild().detachFromParent());
        ImmutableList<MeasuredNode> positiveAsts = ImmutableList.of(
            subtree.positive.cloneTree().addNot(),
            subtree.negative.cloneTree());
        ImmutableList<MeasuredNode> negativeAsts = ImmutableList.of(
            subtree.negative.negate(),
            subtree.positive);
        return new MinimizedCondition(
            Collections.min(positiveAsts, AST_LENGTH_COMPARATOR),
            Collections.min(negativeAsts, AST_LENGTH_COMPARATOR));
      }
      case Token.AND:
      case Token.OR: {
        int opType = n.getType();
        int complementType = opType == Token.AND ? Token.OR : Token.AND;
        MinimizedCondition leftSubtree =
            computeMinimizedCondition(n.getFirstChild().detachFromParent());
        MinimizedCondition rightSubtree =
            computeMinimizedCondition(n.getLastChild().detachFromParent());
        ImmutableList<MeasuredNode> positiveAsts = ImmutableList.of(
            MeasuredNode.addNode(new Node(opType).srcref(n),
                leftSubtree.positive.cloneTree(),
                rightSubtree.positive.cloneTree()),
            MeasuredNode.addNode(new Node(complementType).srcref(n),
                leftSubtree.negative.cloneTree(),
                rightSubtree.negative.cloneTree()).negate());
        ImmutableList<MeasuredNode> negativeAsts = ImmutableList.of(
            MeasuredNode.addNode(new Node(opType).srcref(n),
                leftSubtree.positive,
                rightSubtree.positive).negate(),
            MeasuredNode.addNode(new Node(complementType).srcref(n),
                leftSubtree.negative,
                rightSubtree.negative));
        return new MinimizedCondition(
            Collections.min(positiveAsts, AST_LENGTH_COMPARATOR),
            Collections.min(negativeAsts, AST_LENGTH_COMPARATOR));
      }
      case Token.HOOK: {
        Node cond = n.getFirstChild();
        Node thenNode = cond.getNext();
        Node elseNode = thenNode.getNext();
        MinimizedCondition thenSubtree =
            computeMinimizedCondition(thenNode.detachFromParent());
        MinimizedCondition elseSubtree =
            computeMinimizedCondition(elseNode.detachFromParent());
        MeasuredNode posTree = MeasuredNode.addNode(
            new Node(Token.HOOK, cond.cloneTree()).srcref(n),
            thenSubtree.positive,
            elseSubtree.positive);
        MeasuredNode negTree = MeasuredNode.addNode(
            new Node(Token.HOOK, cond.cloneTree()).srcref(n),
            thenSubtree.negative,
            elseSubtree.negative);
        return new MinimizedCondition(posTree, negTree);
      }
      case Token.COMMA: {
        Node lhs = n.getFirstChild();
        MinimizedCondition rhsSubtree =
            computeMinimizedCondition(lhs.getNext().detachFromParent());
        MeasuredNode posTree = MeasuredNode.addNode(
            new Node(Token.COMMA, lhs.cloneTree()).srcref(n),
            rhsSubtree.positive);
        MeasuredNode negTree = MeasuredNode.addNode(
            new Node(Token.COMMA, lhs.cloneTree()).srcref(n),
            rhsSubtree.negative);
        return new MinimizedCondition(posTree, negTree);
      }
      default: {
        MeasuredNode pos = new MeasuredNode(n, 0, false);
        MeasuredNode neg = pos.cloneTree().negate();
        return new MinimizedCondition(pos, neg);
      }
    }
  }

  private static final Comparator<MeasuredNode> AST_LENGTH_COMPARATOR =
      new Comparator<MeasuredNode>() {
    @Override
    public int compare(MeasuredNode o1, MeasuredNode o2) {
      return o1.length - o2.length;
    }
  };

  /** An AST-node along with some additional metadata. */
  static class MeasuredNode {
    private Node node;
    private int length;
    private boolean changed;

    Node getNode() {
      return node;
    }

    boolean isChanged() {
      return changed;
    }

    MeasuredNode(Node n, int len, boolean ch) {
      node = n;
      length = len;
      changed = ch;
    }

    private MeasuredNode negate() {
      this.change();
      switch (node.getType()) {
        case Token.EQ:
          node.setType(Token.NE);
          return this;
        case Token.NE:
          node.setType(Token.EQ);
          return this;
        case Token.SHEQ:
          node.setType(Token.SHNE);
          return this;
        case Token.SHNE:
          node.setType(Token.SHEQ);
          return this;
        default:
          return this.addNot();
      }
    }

    private MeasuredNode change() {
      this.changed = true;
      return this;
    }

    private MeasuredNode addNot() {
      node = new Node(Token.NOT, node).srcref(node);
      length += estimateCostOneLevel(node);
      return this;
    }

    /**
     *  Estimate the number of characters in the textual representation of
     *  the given node and that will be devoted to negation or parentheses.
     *  Since these are the only characters that flipping a condition
     *  according to De Morgan's rule can affect, these are the only ones
     *  we count.
     *  Not nodes are counted by the NOT node itself, whereas
     *  parentheses around an expression are counted by the parent node.
     *  @param n the node to be checked.
     *  @return the number of negations and parentheses in the node.
     */
    private static int estimateCostOneLevel(Node n) {
      int cost = 0;
      if (n.isNot()) {
        cost++;  // A negation is needed.
      }
      int parentPrecedence = NodeUtil.precedence(n.getType());
      for (Node child = n.getFirstChild();
          child != null; child = child.getNext()) {
        if (PeepholeMinimizeConditions.isLowerPrecedence(child, parentPrecedence)) {
          cost += 2;  // A pair of parenthesis is needed.
        }
      }
      return cost;
    }

    private MeasuredNode cloneTree() {
      return new MeasuredNode(node.cloneTree(), length, changed);
    }

    private static MeasuredNode addNode(Node parent, MeasuredNode... children) {
      int cost = 0;
      boolean changed = false;
      for (MeasuredNode child : children) {
        parent.addChildrenToBack(child.node);
        cost += child.length;
        changed = changed || child.changed;
      }
      cost += estimateCostOneLevel(parent);
      return new MeasuredNode(parent, cost, changed);
    }

  }

}
