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

import com.google.common.base.Predicate;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.GraphReachability;
import com.google.javascript.jscomp.graph.GraphReachability.EdgeTuple;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.TernaryValue;

/**
 * Use {@link ControlFlowGraph} and {@link GraphReachability} to inform user
 * about unreachable code.
 *
 */
class CheckUnreachableCode implements ScopedCallback {

  static final DiagnosticType UNREACHABLE_CODE = DiagnosticType.error(
      "JSC_UNREACHABLE_CODE", "unreachable code");

  private final AbstractCompiler compiler;
  private final CheckLevel level;

  CheckUnreachableCode(AbstractCompiler compiler, CheckLevel level) {
    this.compiler = compiler;
    this.level = level;
  }

  @Override
  public void enterScope(NodeTraversal t) {
    initScope(t.getControlFlowGraph());
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    GraphNode<Node, Branch> gNode = t.getControlFlowGraph().getNode(n);
    if (gNode != null && gNode.getAnnotation() != GraphReachability.REACHABLE) {

      // Only report error when there are some line number informations.
      // There are synthetic nodes with no line number informations, nodes
      // introduce by other passes (although not likely since this pass should
      // be executed early) or some rhino bug.
      if (n.getLineno() != -1 &&
          // Allow spurious semi-colons and spurious breaks.
          !n.isEmpty() && !n.isBreak()) {
        compiler.report(t.makeError(n, level, UNREACHABLE_CODE));
        // From now on, we are going to assume the user fixed the error and not
        // give more warning related to code section reachable from this node.
        new GraphReachability<Node, ControlFlowGraph.Branch>(
            t.getControlFlowGraph()).recompute(n);

        // Saves time by not traversing children.
        return false;
      }
    }
    return true;
  }

  private void initScope(ControlFlowGraph<Node> controlFlowGraph) {
    new GraphReachability<Node, ControlFlowGraph.Branch>(
        controlFlowGraph, new ReachablePredicate()).compute(
            controlFlowGraph.getEntry().getValue());
  }

  @Override
  public void exitScope(NodeTraversal t) {
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
  }

  private final class ReachablePredicate implements
      Predicate<EdgeTuple<Node, ControlFlowGraph.Branch>> {

    @Override
    public boolean apply(EdgeTuple<Node, Branch> input) {
      Branch branch = input.edge;
      if (!branch.isConditional()) {
        return true;
      }
      Node predecessor = input.sourceNode;
      Node condition = NodeUtil.getConditionExpression(predecessor);

      // TODO(user): Handle more complicated expression like true == true,
      // etc....
      if (condition != null) {
        TernaryValue val = NodeUtil.getImpureBooleanValue(condition);
        if (val != TernaryValue.UNKNOWN) {
          return val.toBoolean(true) == (branch == Branch.ON_TRUE);
        }
      }
      return true;
    }
  }
}
