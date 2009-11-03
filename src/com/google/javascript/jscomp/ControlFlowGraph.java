/*
 * Copyright 2008 Google Inc.
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

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Comparator;

/**
 * Control flow graph.
 *
*
 *
 * @param <N> The instruction type of the control flow graph.
 */
class ControlFlowGraph<N> extends
    LinkedDirectedGraph<N, ControlFlowGraph.Branch> {

  /**
   * A special node marked by the node value key null to a singleton
   * "return" when control is transfered outside of the current control flow
   * graph.
   */
  private final DiGraphNode<N, ControlFlowGraph.Branch> implicitReturn;

  private final DiGraphNode<N, ControlFlowGraph.Branch> entry;

  /**
   * Constructor.
   */
  public ControlFlowGraph(N entry) {
    implicitReturn = createDirectedGraphNode(null);
    this.entry = createDirectedGraphNode(entry);
  }

  /**
   * Gets the implicit return node.
   *
   * @return Return node.
   */
  public DiGraphNode<N, ControlFlowGraph.Branch> getImplicitReturn() {
    return implicitReturn;
  }

  /**
   * Gets the entry point of the control flow graph. In general, this should be
   * the beginning of the global script or beginning of a function.
   *
   * @return The entry point.
   */
  public DiGraphNode<N, ControlFlowGraph.Branch> getEntry() {
    return entry;
  }

  /**
   * Checks whether node is the implicit return.
   *
   * @param node Node.
   * @return True if the node is the implicit return.
   */
  public boolean isImplicitReturn(
      DiGraphNode<N, ControlFlowGraph.Branch> node) {
    return node == implicitReturn;
  }

  /**
   * Connects the node to the explicit return.
   *
   * @param srcValue Node.
   * @param edgeValue Edge.
   */
  public void connectToImplicitReturn(N srcValue, Branch edgeValue) {
    super.connect(srcValue, edgeValue, null);
  }

  /**
   * Gets a comparator for the nodes. The default implementation returns
   * {@code null}. See {@link ControlFlowGraph#getOptionalNodeComparator}.
   * @param isForward Whether the comparator sorts the nodes in the direction of
   *    the flow.
   * @return a comparator or null (in particular, if not overriden)
   */
  public Comparator<DiGraphNode<N, Branch>> getOptionalNodeComparator(
      boolean isForward) {
    return null;
  }

  /**
   * The edge object for the control flow graph.
   */
  public static enum Branch {
    /** Edge is taken if the condition is true. */
    ON_TRUE,
    /** Edge is taken if the condition is false. */
    ON_FALSE,
    /** Unconditional branch. */
    UNCOND,
    /** Exception related. */
    ON_EX,
    /** Possible folded-away template */
    SYN_BLOCK;

    public boolean isConditional() {
      return this == ON_TRUE || this == ON_FALSE;
    }
  }

  /**
   * Abstract callback to visit a control flow graph node without going into
   * subtrees of the node that is also represented by another control flow graph
   * node.
   *
   * <p>For example, traversing an IF node as root will visit the two subtree
   * pointed by the {@link ControlFlowGraph.Branch#ON_TRUE} and
   * {@link ControlFlowGraph.Branch#ON_FALSE} edge.
   */
  public abstract static class AbstractCfgNodeTraversalCallback implements
      Callback {
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      if (parent == null) {
        return true;
      }
      switch (parent.getType()) {
        case Token.BLOCK:
        case Token.SCRIPT:
        case Token.TRY:
        case Token.FINALLY:
          return false;
        case Token.FUNCTION:
          return n == parent.getFirstChild().getNext();
        case Token.WHILE:
        case Token.DO:
        case Token.IF:
          return NodeUtil.getConditionExpression(parent) == n;
        case Token.FOR:
          if (parent.getChildCount() == 4) {
            return NodeUtil.getConditionExpression(parent) == n;
          } else {
            return n != parent.getLastChild();
          }
        case Token.SWITCH:
        case Token.CASE:
        case Token.CATCH:
        case Token.WITH:
          return n == parent.getFirstChild();

        default:
          return true;
      }
    }
  }
}
