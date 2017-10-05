/*
 * Copyright 2017 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents the workset used by the flow-sensitive analysis in NTI.
 * We compute the workset iteratively, otherwise large programs can cause stack overflow.
 */
public class NTIWorkset {
  // What this class computes. Represents the workset used by the flow-sensitive analysis in NTI.
  private List<DiGraphNode<Node, ControlFlowGraph.Branch>> ntiWorkset;

  static NTIWorkset create(ControlFlowGraph<Node> cfg) {
    NTIWorkset result = new NTIWorkset();
    result.ntiWorkset = Collections.unmodifiableList((new WorksetBuilder(cfg)).build());
    return result;
  }

  Iterable<DiGraphNode<Node, ControlFlowGraph.Branch>> forward() {
    Preconditions.checkState(!ntiWorkset.isEmpty());
    return ntiWorkset;
  }

  /** The backwards analysis in NTI traverses the workset in the reverse direction. */
  private class BackwardIterator implements Iterator<DiGraphNode<Node, ControlFlowGraph.Branch>> {
    int i = ntiWorkset.size() - 1;

    @Override
    public boolean hasNext() {
      return i >= 0;
    }

    @Override
    public DiGraphNode<Node, Branch> next() {
      return ntiWorkset.get(i--);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  Iterable<DiGraphNode<Node, ControlFlowGraph.Branch>> backward() {
    Preconditions.checkState(!ntiWorkset.isEmpty());
    return new Iterable<DiGraphNode<Node, ControlFlowGraph.Branch>>() {
      @Override
      public Iterator<DiGraphNode<Node, Branch>> iterator() {
        return new BackwardIterator();
      }
    };
  }

  private static class WorksetBuilder {
    private final ControlFlowGraph<Node> cfg;
    private List<DiGraphNode<Node, ControlFlowGraph.Branch>> ntiWorkset;
    // The algorithm that computes the NTI workset itself uses a workset.
    private Deque<DiGraphNode<Node, ControlFlowGraph.Branch>> workset;
    // If a node is in this set, don't revisit it.
    private Set<DiGraphNode<Node, ControlFlowGraph.Branch>> seen;

    WorksetBuilder(ControlFlowGraph<Node> cfg) {
      this.cfg = cfg;
    }

    List<DiGraphNode<Node, ControlFlowGraph.Branch>> build() {
      ntiWorkset = new ArrayList<>();
      workset = new ArrayDeque<>();
      seen = new LinkedHashSet<>();
      workset.push(cfg.getEntry());
      while (!workset.isEmpty()) {
        processGraphNode();
      }
      return ntiWorkset;
    }

    private void processGraphNode() {
      DiGraphNode<Node, ControlFlowGraph.Branch> dn = workset.pop();
      if (seen.contains(dn) || dn == cfg.getImplicitReturn()) {
        return;
      }
      switch (dn.getValue().getToken()) {
        case DO:
        case WHILE:
        case FOR:
        case FOR_IN:
        case FOR_OF: {
          List<DiGraphEdge<Node, ControlFlowGraph.Branch>> outEdges = dn.getOutEdges();
          // The workset is a stack. If we want to analyze nodeA after nodeB, we need to push nodeA
          // before nodeB. For this reason, we push the code after a loop before the loop body.
          for (DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge : outEdges) {
            if (outEdge.getValue() == ControlFlowGraph.Branch.ON_FALSE) {
              workset.push(outEdge.getDestination());
            }
          }
          for (DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge : outEdges) {
            if (outEdge.getValue() == ControlFlowGraph.Branch.ON_TRUE) {
              workset.push(outEdge.getDestination());
            }
          }
          // The loop condition must be analyzed first, so it's pushed last.
          seen.add(dn);
          ntiWorkset.add(dn);
          return;
        }
        default: {
          for (DiGraphEdge<Node, ControlFlowGraph.Branch> inEdge : dn.getInEdges()) {
            DiGraphNode<Node, ControlFlowGraph.Branch> source = inEdge.getSource();
            Node sourceNode = source.getValue();
            // Wait for all other incoming edges at join nodes.
            if (!seen.contains(inEdge.getSource()) && !sourceNode.isDo()) {
              return;
            }
            // The loop header has already been added, and will be analyzed before the loop body.
            // Here, we want to add it again, so that we analyze the header after the loop body,
            // and before the code following the loop.
            if (NodeUtil.isLoopStructure(sourceNode) && !sourceNode.isDo()
                && inEdge.getValue() == ControlFlowGraph.Branch.ON_FALSE) {
              ntiWorkset.add(source);
            }
          }
          seen.add(dn);
          if (cfg.getEntry() != dn) {
            ntiWorkset.add(dn);
          }
          Node n = dn.getValue();
          List<DiGraphNode<Node, ControlFlowGraph.Branch>> succs = cfg.getDirectedSuccNodes(dn);
          // Currently, the ELSE branch of an IF is analyzed before the THEN branch.
          // To do it the other way around, the ELSE branch has to be pushed to the workset
          // *before* the THEN branch, so we need to reverse succs. But the order doesn't impact
          // correctness, so we don't do the reversal.
          for (DiGraphNode<Node, ControlFlowGraph.Branch> succ : succs) {
            workset.push(succ);
            if (succ == cfg.getImplicitReturn()) {
              if (n.getNext() != null) {
                processDeadNode(n.getNext());
              }
            }
          }
          if (n.isTry()) {
            processDeadNode(n.getSecondChild());
          } else if (n.isBreak() || n.isContinue() || n.isThrow()) {
            processDeadNode(n.getNext());
          }
        }
      }
    }

    /**
     * Analyze dead code, such as a catch that is never executed or a statement following a
     * return/break/continue. This code can be a predecessor of live code in the cfg. We wait
     * on incoming edges before adding nodes to the workset, and don't want dead code to block
     * live code from being analyzed.
     */
    private void processDeadNode(Node maybeDeadNode) {
      if (maybeDeadNode == null) {
        return;
      }
      DiGraphNode<Node, ControlFlowGraph.Branch> cfgNode = cfg.getDirectedGraphNode(maybeDeadNode);
      if (cfgNode == null) {
        return;
      }
      if (cfg.getDirectedPredNodes(cfgNode).isEmpty()) {
        workset.push(cfgNode);
      }
    }
  }
}
