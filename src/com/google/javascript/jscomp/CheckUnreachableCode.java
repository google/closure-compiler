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
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.GraphReachability;
import com.google.javascript.jscomp.graph.GraphReachability.EdgeTuple;
import com.google.javascript.rhino.Node;

/**
 * Use {@link ControlFlowGraph} and {@link GraphReachability} to inform user about unreachable code.
 */
class CheckUnreachableCode extends NodeTraversal.AbstractCfgCallback {

  static final DiagnosticType UNREACHABLE_CODE = DiagnosticType.warning(
      "JSC_UNREACHABLE_CODE", "unreachable code");

  private final AbstractCompiler compiler;

  CheckUnreachableCode(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void enterScopeWithCfg(NodeTraversal t) {
    if (NodeUtil.isValidCfgRoot(t.getScopeRoot())) {
      initScope(getControlFlowGraph(compiler));
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    ControlFlowGraph<Node> cfg = getControlFlowGraph(compiler);
    GraphNode<Node, Branch> gNode = cfg.getNode(n);
    if (gNode != null && gNode.getAnnotation() != GraphReachability.REACHABLE) {

      // Only report error when there are some line number informations.
      // There are synthetic nodes with no line number informations, nodes
      // introduce by other passes (although not likely since this pass should
      // be executed early) or some rhino bug.
      if (n.getLineno() != -1 &&
          // Allow spurious semi-colons and spurious breaks.
          !n.isEmpty() && !n.isBreak()) {
        compiler.report(JSError.make(n, UNREACHABLE_CODE));
        // From now on, we are going to assume the user fixed the error and not
        // give more warning related to code section reachable from this node.
        new GraphReachability<>(cfg).recompute(n);

        // Saves time by not traversing children.
        return false;
      }
    }
    return true;
  }

  private void initScope(ControlFlowGraph<Node> controlFlowGraph) {
    new GraphReachability<>(controlFlowGraph, REACHABLE)
        .compute(controlFlowGraph.getEntry().getValue());
  }

  private static final Predicate<EdgeTuple<Node, ControlFlowGraph.Branch>> REACHABLE =
      (EdgeTuple<Node, Branch> input) -> {
        Branch branch = input.edge;
        if (!branch.isConditional()) {
          return true;
        }
        Node predecessor = input.sourceNode;
        Node condition = NodeUtil.getConditionExpression(predecessor);

        // TODO(user): Handle more complicated expression like true == true,
        // etc....
        if (condition != null) {
          Tri val = NodeUtil.getBooleanValue(condition);
          if (val != Tri.UNKNOWN) {
            return val.toBoolean(true) == (branch == Branch.ON_TRUE);
          }
        }
        return true;
      };
}
