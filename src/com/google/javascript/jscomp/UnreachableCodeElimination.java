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

import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.GraphReachability;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Deque;
import java.util.LinkedList;
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
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false);
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
    // Removes TRYs that had its CATCH removed and/or empty FINALLY.
    // TODO(dcc): Move the parts of this that don't require a control flow
    // graph to PeepholeRemoveDeadCode
    if (n.getType() == Token.TRY) {
      Node body = n.getFirstChild();
      Node catchOrFinallyBlock = body.getNext();
      Node finallyBlock = catchOrFinallyBlock.getNext();

      if (!catchOrFinallyBlock.hasChildren() &&
          (finallyBlock == null || !finallyBlock.hasChildren())) {
        n.removeChild(body);
        parent.replaceChild(n, body);
        compiler.reportCodeChange();
        n = body;
      }
    }
    GraphNode<Node, Branch> gNode = curCfg.getNode(n);
    if (gNode == null) { // Not in CFG.
      return;
    }
    if (gNode.getAnnotation() != GraphReachability.REACHABLE ||
        (removeNoOpStatements && !NodeUtil.mayHaveSideEffects(n))) {
      removeDeadExprStatementSafely(n, parent);
    }
  }

  private void removeDeadExprStatementSafely(Node n, Node parent) {
    if (n.getType() == Token.EMPTY ||
        (n.getType() == Token.BLOCK && !n.hasChildren())) {
      // Not always trivial to remove, let FoldContants work its magic later.
      return;
    }
    // Removing an unreachable DO node is messy because it means we still have
    // to execute one iteration. If the DO's body has breaks in the middle, it
    // can get even more trickier and code size might actually increase.
    switch (n.getType()) {
      case Token.DO:
      case Token.TRY:
      case Token.CATCH:
      case Token.FINALLY:
        return;
    }

    NodeUtil.redeclareVarsInsideBranch(n);
    compiler.reportCodeChange();
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Removing " + n.toString());
    }
    NodeUtil.removeChild(parent, n);
  }
}
