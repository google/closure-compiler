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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.jspecify.nullness.Nullable;

/**
 * A class that represents a minimized conditional expression.
 * This is a conditional expression that has been massaged according to
 * DeMorgan's laws in order to minimize the length of the source
 * representation.
 * <p>
 * Depending on the context, a leading NOT node in front of the conditional
 * may or may not be counted as a cost, so this class provides ways to
 * access minimized versions of both of those abstract syntax trees (ASTs).
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

  private MinimizedCondition(MeasuredNode p, MeasuredNode n) {
    positive = p;
    negative = n.change();
  }

  /**
   * Returns a MinimizedCondition that represents the condition node after
   * minimization.
   */
  static MinimizedCondition fromConditionNode(Node n) {
    checkState(n.hasParent());
    switch (n.getToken()) {
      case NOT:
      case AND:
      case OR:
      case HOOK:
      case COMMA:
        return computeMinimizedCondition(n);
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
   * Since a MinimizedCondition necessarily must contain two trees,
   * this method sets the negative side to a invalid node
   * with an unreasonably high length so that it will
   * never be chosen by {@link #getMinimized}.
   *
   * @param n the conditional expression tree
   * @return a MinimizedCondition object representing that tree.
   */
  static MinimizedCondition unoptimized(Node n) {
    checkNotNull(n.getParent());
    MeasuredNode pos = new MeasuredNode(n, null, 0, false);
    MeasuredNode neg = new MeasuredNode(null, null, Integer.MAX_VALUE, true);
    return new MinimizedCondition(pos, neg);
  }

  /** return the best, prefer unchanged */
  static MeasuredNode pickBest(MeasuredNode a, MeasuredNode b) {
    if (a.length == b.length) {
      return (b.isChanged()) ? a : b;
    }

    return (a.length < b.length) ? a : b;
  }

  /**
   * Minimize the condition at the given node.
   *
   * @param n the conditional expression tree to minimize.
   * @return a MinimizedCondition object representing that tree.
   */
  private static MinimizedCondition computeMinimizedCondition(Node n) {
    switch (n.getToken()) {
      case NOT: {
        MinimizedCondition subtree = computeMinimizedCondition(n.getFirstChild());
        MeasuredNode positive = pickBest(
            MeasuredNode.addNode(n, subtree.positive),
            subtree.negative);
          MeasuredNode negative =
              pickBest(
                  subtree.negative
                      .negate(), // since parent node `n` is a NOT, we need to negate the subtree's
                  // computed `negative` to obtain the parent `n`'s real negative.
                  subtree.positive);
        return new MinimizedCondition(positive, negative);
      }
      case AND:
      case OR: {
          Node complementNode = new Node(n.isAnd() ? Token.OR : Token.AND).srcref(n);
        MinimizedCondition leftSubtree = computeMinimizedCondition(n.getFirstChild());
        MinimizedCondition rightSubtree = computeMinimizedCondition(n.getLastChild());
        MeasuredNode positive = pickBest(
            MeasuredNode.addNode(n,
                leftSubtree.positive,
                rightSubtree.positive),
            MeasuredNode.addNode(complementNode,
                leftSubtree.negative,
                rightSubtree.negative).negate());
        MeasuredNode negative = pickBest(
            MeasuredNode.addNode(n,
                leftSubtree.positive,
                rightSubtree.positive).negate(),
            MeasuredNode.addNode(complementNode,
                leftSubtree.negative,
                rightSubtree.negative).change());
        return new MinimizedCondition(positive, negative);
      }
      case HOOK: {
        Node cond = n.getFirstChild();
        Node thenNode = cond.getNext();
        Node elseNode = thenNode.getNext();
        MinimizedCondition thenSubtree = computeMinimizedCondition(thenNode);
        MinimizedCondition elseSubtree = computeMinimizedCondition(elseNode);
        MeasuredNode positive = MeasuredNode.addNode(
            n,
            MeasuredNode.forNode(cond),
            thenSubtree.positive,
            elseSubtree.positive);
        MeasuredNode negative = MeasuredNode.addNode(
            n,
            MeasuredNode.forNode(cond),
            thenSubtree.negative,
            elseSubtree.negative);
        return new MinimizedCondition(positive, negative);
      }
      case COMMA: {
        Node lhs = n.getFirstChild();
        MinimizedCondition rhsSubtree = computeMinimizedCondition(lhs.getNext());
        MeasuredNode positive = MeasuredNode.addNode(
            n,
            MeasuredNode.forNode(lhs),
            rhsSubtree.positive);
        MeasuredNode negative = MeasuredNode.addNode(
            n,
            MeasuredNode.forNode(lhs),
            rhsSubtree.negative);
        return new MinimizedCondition(positive, negative);
      }
      default: {
        MeasuredNode pos = MeasuredNode.forNode(n);
        MeasuredNode neg = pos.negate();
        return new MinimizedCondition(pos, neg);
      }
    }
  }

  /** An AST-node along with some additional metadata. */
  static class MeasuredNode {
    private final Node node;
    private final int length;
    private final boolean changed;
    private final MeasuredNode[] children;

    MeasuredNode(@Nullable Node n, MeasuredNode @Nullable [] children, int len, boolean ch) {
      node = n;
      this.children = children;
      length = len;
      changed = ch;
    }

    boolean isChanged() {
      return changed;
    }

    boolean isNot() {
      return node.isNot();
    }

    MeasuredNode withoutNot() {
      checkState(isNot());
      return (normalizeChildren(node, children)[0]).change();
    }

    private MeasuredNode negate() {
      switch (node.getToken()) {
        case EQ:
          return updateToken(Token.NE);
        case NE:
          return updateToken(Token.EQ);
        case SHEQ:
          return updateToken(Token.SHNE);
        case SHNE:
          return updateToken(Token.SHEQ);
        case NOT:
          return withoutNot();
        default:
          return addNot();
      }
    }

    static MeasuredNode[] normalizeChildren(Node node, MeasuredNode[] children) {
      if (children != null || !node.hasChildren()) {
        return children;
      } else {
        MeasuredNode[] measuredChildren = new MeasuredNode[node.getChildCount()];
        int child = 0;
        for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
          measuredChildren[child++] = forNode(c);
        }
        return measuredChildren;
      }
    }

    private MeasuredNode updateToken(Token token) {
      return new MeasuredNode(
          new Node(token).srcref(node), normalizeChildren(node, children), length, true);
    }

    private MeasuredNode addNot() {
      return addNode(
          new Node(Token.NOT).srcref(node), this).change();
    }

    private MeasuredNode change() {
      return (isChanged()) ? this : new MeasuredNode(node, children, length, true);
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
    private static int estimateCostOneLevel(Node n, MeasuredNode ...children) {
      int cost = 0;
      if (n.isNot()) {
        cost++;  // A negation is needed.
      }
      int parentPrecedence = NodeUtil.precedence(n.getToken());
      for (MeasuredNode child : children) {
        if (child.isLowerPrecedenceThan(parentPrecedence)) {
          cost += 2;  // A pair of parenthesis is needed.
        }
      }
      return cost;
    }

    /**
     * Whether the node type has lower precedence than "precedence"
     */
    boolean isLowerPrecedenceThan(int precedence) {
      return NodeUtil.precedence(node.getToken()) < precedence;
    }

    /**
     * The returned MeasuredNode is only marked as changed if the children
     * are marked as changed.
     */
    private static MeasuredNode addNode(Node parent, MeasuredNode ...children) {
      int cost = 0;
      boolean changed = false;
      for (MeasuredNode child : children) {
        cost += child.length;
        changed = changed || child.changed;
      }
      cost += estimateCostOneLevel(parent, children);
      return new MeasuredNode(parent, children, cost, changed);
    }

    /**
     * Return a MeasuredNode for a non-participating AST Node. This is used for leaf expression
     * nodes.
     */
    private static MeasuredNode forNode(Node n) {
      return new MeasuredNode(n, null, 0, false);
    }

    /**
     * Whether the MeasuredNode is a change from the original.
     * This can either be a change within the original AST tree or a
     * replacement of the original node.
     */
    public boolean willChange(Node original) {
      checkNotNull(original);
      return original != node || isChanged();
    }

    /**
     * Update the AST for the result of this MeasuredNode.
     * This can either be a change within the original AST tree or a
     * replacement of the original node.
     */
    public Node applyTo(Node original) {
      checkNotNull(original);
      checkState(willChange(original));
      Node replacement = buildReplacement();
      if (original != replacement) {
        safeDetach(replacement);
        original.replaceWith(replacement);
      }
      return replacement;
    }

    /** Detach a node only IIF it is in the tree */
    private Node safeDetach(Node n) {
      return n.hasParent() ? n.detach() : n;
    }

    /**
     * Build the final AST structure, detaching component Nodes as necessary 
     * from the original AST. The root Node, if currently attached is left attached
     * to avoid the need to keep track of its position.
     */
    @VisibleForTesting
    Node buildReplacement() {
      if (children != null) {
        node.detachChildren();
        for (MeasuredNode child : children) {
          Node replacementChild = safeDetach(child.buildReplacement());
          node.addChildToBack(replacementChild);
        }
      }
      return node;
    }

  }
}
