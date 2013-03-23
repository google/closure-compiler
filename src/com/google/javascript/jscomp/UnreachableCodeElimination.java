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
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.NodeTraversal.FunctionCallback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.GraphReachability;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

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

// TODO(user): Besides dead code after returns, this pass removes useless live
// code such as breaks/continues/returns and stms w/out side effects.
// These things don't require reachability info, consider making them their own
// pass or putting them in some other, more related pass.

class UnreachableCodeElimination implements CompilerPass {
  private static final Logger logger =
    Logger.getLogger(UnreachableCodeElimination.class.getName());
  private final AbstractCompiler compiler;
  private final boolean removeNoOpStatements;
  private boolean codeChanged;

  UnreachableCodeElimination(AbstractCompiler compiler,
      boolean removeNoOpStatements) {
    this.compiler = compiler;
    this.removeNoOpStatements = removeNoOpStatements;
  }

  @Override
  public void process(Node externs, Node toplevel) {
    NodeTraversal.traverseChangedFunctions(compiler, new FunctionCallback() {
        @Override
        public void visit(AbstractCompiler compiler, Node root) {
          // Computes the control flow graph.
          ControlFlowAnalysis cfa =
              new ControlFlowAnalysis(compiler, false, false);
          cfa.process(null, root);
          ControlFlowGraph<Node> cfg = cfa.getCfg();
          new GraphReachability<Node, ControlFlowGraph.Branch>(cfg)
              .compute(cfg.getEntry().getValue());
          if (root.isFunction()) {
            root = root.getLastChild();
          }
          do {
            codeChanged = false;
            NodeTraversal.traverse(compiler, root, new EliminationPass(cfg));
          } while (codeChanged);
        }
      });
  }

  private class EliminationPass extends AbstractShallowCallback {
    private final ControlFlowGraph<Node> cfg;
    private EliminationPass(ControlFlowGraph<Node> cfg) {
      this.cfg = cfg;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (parent == null || n.isFunction() || n.isScript()) {
        return;
      }
      DiGraphNode<Node, Branch> gNode = cfg.getDirectedGraphNode(n);
      if (gNode == null) { // Not in CFG.
        return;
      }
      if (gNode.getAnnotation() != GraphReachability.REACHABLE ||
          (removeNoOpStatements && !NodeUtil.mayHaveSideEffects(n, compiler))) {
        removeDeadExprStatementSafely(n);
        return;
      }
      tryRemoveUnconditionalBranching(n);
    }

    /**
     * Tries to remove n if it is an unconditional branch node (break, continue,
     * or return) and the target of n is the same as the the follow of n.
     * That is, if removing n preserves the control flow. Also if n targets
     * another unconditional branch, this function will recursively try to
     * remove the target branch as well. The reason why we want to cascade this
     * removal is because we only run this pass once. If we have code such as
     *
     * break -> break -> break
     *
     * where all 3 breaks are useless, then the order of removal matters. When
     * we first look at the first break, we see that it branches to the 2nd
     * break. However, if we remove the last break, the 2nd break becomes
     * useless and finally the first break becomes useless as well.
     *
     * @returns The target of this jump. If the target is also useless jump,
     *     the target of that useless jump recursively.
     */
    @SuppressWarnings("fallthrough")
    private void tryRemoveUnconditionalBranching(Node n) {
      /*
       * For each unconditional branching control flow node, check to see
       * if the ControlFlowAnalysis.computeFollowNode of that node is same as
       * the branching target. If it is, the branch node is safe to be removed.
       *
       * This is not as clever as MinimizeExitPoints because it doesn't do any
       * if-else conversion but it handles more complicated switch statements
       * much more nicely.
       */

      // If n is null the target is the end of the function, nothing to do.
      if (n == null) {
         return;
      }

      DiGraphNode<Node, Branch> gNode = cfg.getDirectedGraphNode(n);

      if (gNode == null) {
        return;
      }

      switch (n.getType()) {
        case Token.RETURN:
          if (n.hasChildren()) {
            break;
          }
        case Token.BREAK:
        case Token.CONTINUE:
          // We are looking for a control flow changing statement that always
          // branches to the same node. If after removing it control still
          // branches to the same node, it is safe to remove.
          List<DiGraphEdge<Node, Branch>> outEdges = gNode.getOutEdges();
          if (outEdges.size() == 1 &&
              // If there is a next node, this jump is not useless.
              (n.getNext() == null || n.getNext().isFunction())) {

            Preconditions.checkState(
                outEdges.get(0).getValue() == Branch.UNCOND);
            Node fallThrough = computeFollowing(n);
            Node nextCfgNode = outEdges.get(0).getDestination().getValue();
            if (nextCfgNode == fallThrough && !inFinally(n.getParent(), n)) {
              removeNode(n);
            }
          }
      }
    }

    private boolean inFinally(Node parent, Node child) {
      if (parent == null || parent.isFunction()) {
        return false;
      } else if (NodeUtil.isTryFinallyNode(parent, child)) {
        return true;
      } else {
        return inFinally(parent.getParent(), parent);
      }
    }

    private Node computeFollowing(Node n) {
      Node next = ControlFlowAnalysis.computeFollowNode(n);
      while (next != null && next.isBlock()) {
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
      if (n.isEmpty() || (n.isBlock() && !n.hasChildren())) {
        // Not always trivial to remove, let FoldConstants work its magic later.
        return;
      }

      // TODO(user): This is a problem with removeNoOpStatements.
      // Every expression in a FOR-IN header looks side effect free on its own.
      if (NodeUtil.isForIn(parent)) {
        return;
      }

      switch (n.getType()) {
        // Removing an unreachable DO node is messy b/c it means we still have
        // to execute one iteration. If the DO's body has breaks in the middle,
        // it can get even more tricky and code size might actually increase.
        case Token.DO:
          return;

        case Token.BLOCK:
          // BLOCKs are used in several ways including wrapping CATCH
          // blocks in TRYs
          if (parent.isTry() && NodeUtil.isTryCatchNodeContainer(n)) {
            return;
          }
          break;

        case Token.CATCH:
          Node tryNode = parent.getParent();
          NodeUtil.maybeAddFinally(tryNode);
          break;
      }

      if (n.isVar() && !n.getFirstChild().hasChildren()) {
        // Very unlikely case, Consider this:
        // File 1: {throw 1}
        // File 2: {var x}
        // The node var x is unreachable in the global scope.
        // Before we remove the node, redeclareVarsInsideBranch
        // would basically move var x to the beginning of File 2,
        // which resulted in zero changes to the AST but triggered
        // reportCodeChange().
        // Instead, we should just ignore dead variable declarations.
        return;
      }

      removeNode(n);
    }

    private void removeNode(Node n) {
      codeChanged = true;
      NodeUtil.redeclareVarsInsideBranch(n);
      compiler.reportCodeChange();
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("Removing " + n.toString());
      }
      NodeUtil.removeChild(n.getParent(), n);
    }
  }
}
