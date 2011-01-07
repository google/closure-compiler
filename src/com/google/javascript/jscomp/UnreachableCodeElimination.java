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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.GraphReachability;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Removes dead code from a parse tree. The kinds of dead code that this pass
 * removes are:
 *  - Any code following a return statement, such as the <code>alert</code>
 *    call in: <code>if (x) { return; alert('unreachable'); }</code>.
 *  - Statements that have no side effects, such as:
 *    <code>a.b.MyClass.prototype.propertyName;</code> or <code>true;</code>.
 *    That first kind of statement sometimes appears intentionally, so that
 *    prototype properties can be annotated using JSDoc without actually
 *    being initialized.
 *
 */
class UnreachableCodeElimination extends AbstractPostOrderCallback
    implements CompilerPass, ScopedCallback  {
  private static final Logger logger =
    Logger.getLogger(UnreachableCodeElimination.class.getName());

  private final AbstractCompiler compiler;
  private final boolean removeNoOpStatements;

  Deque<ControlFlowGraph<Node>> cfgStack =
      new LinkedList<ControlFlowGraph<Node>>();

  ControlFlowGraph<Node> curCfg = null;

  UnreachableCodeElimination(AbstractCompiler compiler,
      boolean removeNoOpStatements) {
    this.compiler = compiler;
    this.removeNoOpStatements = removeNoOpStatements;
  }

  @Override
  public void enterScope(NodeTraversal t) {
    Scope scope = t.getScope();

    // Computes the control flow graph.
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, false);
    cfa.process(null, scope.getRootNode());
    cfgStack.push(curCfg);
    curCfg = cfa.getCfg();

    new GraphReachability<Node, ControlFlowGraph.Branch>(curCfg)
        .compute(curCfg.getEntry().getValue());
  }

  @Override
  public void exitScope(NodeTraversal t) {
    curCfg = cfgStack.pop();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (parent == null) {
      return;
    }
    if (n.getType() == Token.FUNCTION || n.getType() == Token.SCRIPT) {
      return;
    }

    DiGraphNode<Node, Branch> gNode = curCfg.getDirectedGraphNode(n);
    if (gNode == null) { // Not in CFG.
      return;
    }
    if (gNode.getAnnotation() != GraphReachability.REACHABLE ||
        (removeNoOpStatements && !NodeUtil.mayHaveSideEffects(n))) {
      removeDeadExprStatementSafely(n);
      return;
    }

    tryRemoveUnconditionalBranching(n);
  }

  /**
   * Tries to remove n if an unconditional branch node (break, continue or
   * return) if the target of n is the same as the the follow of n. That is, if
   * we remove n, the control flow remains the same. Also if n targets to
   * another unconditional branch, this function will recursively try to remove
   * the target branch as well. The reason why we want to cascade this removal
   * is because we only run this pass once. If we have code such as
   *
   * break -> break -> break
   *
   * where all 3 break's are useless. The order of removal matters. When we
   * first look at the first break, we see that it branches to the 2nd break.
   * However, if we remove the last break, the 2nd break becomes useless and
   * finally the first break becomes useless as well.
   *
   * @return The target of this jump. If the target is also useless jump,
   *     the target of that useless jump recursively.
   */
  @SuppressWarnings("fallthrough")
  private Node tryRemoveUnconditionalBranching(Node n) {
    /*
     * For each of the unconditional branching control flow node, check to see
     * if the ControlFlowAnalysis.computeFollowNode of that node is same as
     * the branching target. If it is, the branch node is safe to be removed.
     *
     * This is not as clever as MinimizeExitPoints because it doesn't do any
     * if-else conversion but it handles more complicated switch statements
     * much nicer.
     */

    // If n is null the target is the end of the function, nothing to do.
    if (n == null) {
       return n;
    }

    DiGraphNode<Node, Branch> gNode = curCfg.getDirectedGraphNode(n);

    if (gNode == null) {
      return n;
    }

    switch (n.getType()) {
      case Token.RETURN:
        if (n.hasChildren()) {
          break;
        }
      case Token.BREAK:
      case Token.CONTINUE:

        // We are looking for a control flow changing statement that always
        // branches to the same node. If removing it the control flow still
        // branches to that same node. It is safe to remove it.
        List<DiGraphEdge<Node,Branch>> outEdges = gNode.getOutEdges();
        if (outEdges.size() == 1 &&
            // If there is a next node, there is no chance this jump is useless.
            (n.getNext() == null || n.getNext().getType() == Token.FUNCTION)) {

          Preconditions.checkState(outEdges.get(0).getValue() == Branch.UNCOND);
          Node fallThrough = computeFollowing(n);
          Node nextCfgNode = outEdges.get(0).getDestination().getValue();
          if (nextCfgNode == fallThrough) {
            removeDeadExprStatementSafely(n);
            return fallThrough;
          }
        }
    }
    return n;
  }

  private Node computeFollowing(Node n) {
    Node next = ControlFlowAnalysis.computeFollowNode(n);
    while (next != null && next.getType() == Token.BLOCK) {
      if (next.hasChildren()) {
        next = next.getFirstChild();
      } else {
        next = computeFollowing(next);
      }
    }
    return next;
  }

  private void removeDeadExprStatementSafely(Node n) {
    Node parent = n.getParent();
    if (n.getType() == Token.EMPTY ||
        (n.getType() == Token.BLOCK && !n.hasChildren())) {
      // Not always trivial to remove, let FoldContants work its magic later.
      return;
    }

    switch (n.getType()) {
      // Removing an unreachable DO node is messy because it means we still have
      // to execute one iteration. If the DO's body has breaks in the middle, it
      // can get even more trickier and code size might actually increase.
      case Token.DO:
        return;

      case Token.BLOCK:
        // BLOCKs are used in several ways including wrapping CATCH blocks in TRYs
        if (parent.getType() == Token.TRY) {
          if (NodeUtil.isTryCatchNodeContainer(n)) {
            return;
          }
        }
        break;

      case Token.CATCH:
        Node tryNode = parent.getParent();
        NodeUtil.maybeAddFinally(tryNode);
        break;
    }

    NodeUtil.redeclareVarsInsideBranch(n);
    compiler.reportCodeChange();
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Removing " + n.toString());
    }
    NodeUtil.removeChild(n.getParent(), n);
  }
}
