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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.DataFlowAnalysis.FlowState;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.LiveVariablesAnalysis.LiveVariableLattice;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.logging.Logger;

/**
 * Removes local variable assignments that are useless based on information from
 * {@link LiveVariablesAnalysis}. If there is an assignment to variable
 * {@code x} and {@code x} is dead after this assignment, we know that the
 * current content of {@code x} will not be read and this assignment is useless.
 *
*
 */
class DeadAssignmentsElimination extends AbstractPostOrderCallback implements
    CompilerPass, ScopedCallback {

  private final AbstractCompiler compiler;
  private LiveVariablesAnalysis liveness;
  private static final Logger logger =
    Logger.getLogger(DeadAssignmentsElimination.class.getName());

  public DeadAssignmentsElimination(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkNotNull(externs);
    Preconditions.checkNotNull(root);
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void enterScope(NodeTraversal t) {
    Scope scope = t.getScope();
    // Global scope _SHOULD_ work, however, liveness won't finish without
    // -Xmx1024 in closure. We might have to look at coding conventions for
    // exported variables as well.
    if (scope.isGlobal()) {
      return;
    }
    
    // We are not going to do any dead assignment elimination in when there is
    // at least one inner function because in most browsers, when there is a
    // closure, ALL the variables are saved (escaped).
    if (!NodeUtil.containsFunctionDeclaration(
        t.getScopeRoot().getLastChild())) {
      // Computes liveness information first.
      ControlFlowGraph<Node> cfg = t.getControlFlowGraph();
      liveness = new LiveVariablesAnalysis(cfg, scope, compiler);
      liveness.analyze();
      tryRemoveDeadAssignments(t, cfg);
    }
  }

  @Override
  public void exitScope(NodeTraversal t) {
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
  }

  /**
   * Try to remove useless assignments from a control flow graph that has been
   * annotated with liveness information.
   *
   * @param t The node traversal.
   * @param cfg The control flow graph of the program annotated with liveness
   *        information.
   */
  private void tryRemoveDeadAssignments(NodeTraversal t,
      ControlFlowGraph<Node> cfg) {
    List<DiGraphNode<Node, Branch>> nodes = cfg.getDirectedGraphNodes();

    for (DiGraphNode<Node, Branch> cfgNode : nodes) {
      FlowState<LiveVariableLattice> state =
          cfgNode.getAnnotation();
      Node n = cfgNode.getValue();
      if (n == null) {
        continue;
      }
      switch (n.getType()) {
        case Token.IF:
        case Token.WHILE:
        case Token.DO:
          tryRemoveAssignment(t, NodeUtil.getConditionExpression(n), n, state);
          continue;
        case Token.FOR:
          if (n.getChildCount() == 4) {
            tryRemoveAssignment(
                t, NodeUtil.getConditionExpression(n), n, state);
            tryRemoveAssignment(
                t, n.getFirstChild().getNext().getNext(), n, state);
          }
          continue;
        case Token.SWITCH:
        case Token.CASE:
        case Token.RETURN:
          if (n.hasChildren()) {
            tryRemoveAssignment(t, n.getFirstChild(), n, state);
          }
          continue;
        // TODO(user): case Token.VAR: Remove var a=1;a=2;.....
      }
      
      if (NodeUtil.isExpressionNode(n)) {
        tryRemoveAssignment(t, n.getFirstChild(), n, state);
      }
    }
  }

  /**
   * Determines if any local variables are dead after the instruction {@code n}
   * and are assigned within the subtree of {@code n}. Removes those assignments
   * if there are any.
   *
   * @param n Target instruction.
   * @param parent Parent of {@code n}.
   * @param state The liveness information at {@code n}.
   */
  private void tryRemoveAssignment(NodeTraversal t, Node n, Node parent,
      FlowState<LiveVariableLattice> state) {
    if (NodeUtil.isAssign(n)) {
      Node lhs = n.getFirstChild();
      Scope scope = t.getScope();
      if (!NodeUtil.isName(lhs)) {
        return; // Not a local variable assignment.
      }
      String name = lhs.getString();
      if (!scope.isDeclared(name, false)) {
        return;
      }
      Var var = scope.getVar(name);
      if (liveness.getEscapedLocals().contains(var)) {
        return; // Local variable that might be escaped due to closures.
      }
      if (state.getOut().isLive(var)) {
        return; // Variable not dead.
      }
      if (state.getIn().isLive(var)) {
        // Oddly, the variable is killed here but it is also live before it.
        // This is possible if we have say:
        //    if (X = a && a = C) {..} ; .......; a = S;
        // In this case we are safe to remove "a = C" because it is dead.
        // However if we have:
        //    if (a = C && X = a) {..} ; .......; a = S;
        // removing "a = C" is NOT correct, although the live set at the node
        // is exactly the same.
        // TODO(user): We need more fine grain CFA or we need to keep track
        // of GEN sets when we recurse here.
        return;
      }
      Node rhs = n.getLastChild();
      // Now we are at a dead local variable assignment.
      logger.info("Removing dead assignemnt to " + name + " in "
          + t.getSourceName() + " line " + n.getLineno());
      n.removeChild(rhs);
      parent.replaceChild(n, rhs);
      compiler.reportCodeChange();
      return;

    } else {
      for (Node c = n.getFirstChild(); c != null;) {
        Node next = c.getNext();
        tryRemoveAssignment(t, c, n, state);
        c = next;
      }
      return;
    }
  }
}
