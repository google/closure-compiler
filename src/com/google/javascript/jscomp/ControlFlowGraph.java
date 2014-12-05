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

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Comparator;

/**
 * Control flow graph.
 *
 *
 * @param <N> The instruction type of the control flow graph.
 */
public class ControlFlowGraph<N> extends
    LinkedDirectedGraph<N, ControlFlowGraph.Branch> {

  /**
   * A special node marked by the node value key null to a singleton
   * "return" when control is transferred outside of the current control flow
   * graph.
   */
  private final DiGraphNode<N, ControlFlowGraph.Branch> implicitReturn;

  private final DiGraphNode<N, ControlFlowGraph.Branch> entry;

  /**
   * Constructor.
   */
  ControlFlowGraph(
      N entry, boolean nodeAnnotations, boolean edgeAnnotations) {
    super(nodeAnnotations, edgeAnnotations);
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
   * Gets a comparator for the nodes. The default implementation returns
   * {@code null}. See {@link ControlFlowGraph#getOptionalNodeComparator}.
   * @param isForward Whether the comparator sorts the nodes in the direction of
   *    the flow.
   * @return a comparator or null (in particular, if not overridden)
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
    /**
     * Exception-handling code paths.
     * Conflates two kind of control flow passing:
     * - An exception is thrown, and falls into a catch or finally block
     * - During exception handling, a finally block finishes and control
     *   passes to the next finally block.
     * In theory, we need 2 different edge types. In practice, we
     * can just treat them as "the edges we can't really optimize".
     */
    ON_EX,
    /** Possible folded-away template */
    SYN_BLOCK;

    public boolean isConditional() {
      return this == ON_TRUE || this == ON_FALSE;
    }
  }

  /**
   * Abstract callback to visit a control flow graph node without going into
   * subtrees of the node that are also represented by other
   * control flow graph nodes.
   *
   * <p>For example, traversing an IF node as root will visit the two subtrees
   * pointed by the {@link ControlFlowGraph.Branch#ON_TRUE} and
   * {@link ControlFlowGraph.Branch#ON_FALSE} edges.
   */
  public abstract static class AbstractCfgNodeTraversalCallback implements
      Callback {
    @Override
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      if (parent == null) {
        return true;
      }
      return !isEnteringNewCfgNode(n);
    }
  }

  /**
   * @return True if n should be represented by a new CFG node in the control
   * flow graph.
   */
  public static boolean isEnteringNewCfgNode(Node n) {
    Node parent = n.getParent();
    switch (parent.getType()) {
      case Token.BLOCK:
      case Token.SCRIPT:
      case Token.TRY:
        return true;
      case Token.FUNCTION:
        // A function node represents the start of a function where the name
        // bleeds into the local scope and parameters are assigned
        // to the formal argument names. The node includes the name of the
        // function and the LP list since we assume the whole set up process
        // is atomic without change in control flow. The next change of
        // control is going into the function's body, represented by the second
        // child.
        return n != parent.getFirstChild().getNext();
      case Token.WHILE:
      case Token.DO:
      case Token.IF:
        // These control structures are represented by a node that holds the
        // condition. Each of them is a branch node based on its condition.
        return NodeUtil.getConditionExpression(parent) != n;

      case Token.FOR:
        // The FOR(;;) node differs from other control structures in that
        // it has an initialization and an increment statement. Those
        // two statements have corresponding CFG nodes to represent them.
        // The FOR node only represents the condition check for each iteration.
        // That way the following:
        // for(var x = 0; x < 10; x++) { } has a graph that is isomorphic to
        // var x = 0; while(x<10) {  x++; }
        if (NodeUtil.isForIn(parent)) {
          // TODO(user): Investigate how we should handle the case where
          // we have a very complex expression inside the FOR-IN header.
          return n != parent.getFirstChild();
        } else {
          return NodeUtil.getConditionExpression(parent) != n;
        }
      case Token.SWITCH:
      case Token.CASE:
      case Token.CATCH:
      case Token.WITH:
        return n != parent.getFirstChild();
      default:
        return false;
    }
  }

  @Override
  public String toString() {
    String s = "CFG:\n";
    for (GraphvizEdge e : getGraphvizEdges()) {
      s += e.toString() + '\n';
    }
    return s;
  }
}
