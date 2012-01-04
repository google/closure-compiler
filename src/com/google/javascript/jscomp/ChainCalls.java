/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Chain calls to functions that return this.
 *
 */
class ChainCalls implements CompilerPass {
  private final AbstractCompiler compiler;
  private final Set<Node> badFunctionNodes = Sets.newHashSet();
  private final Set<Node> goodFunctionNodes = Sets.newHashSet();
  private final List<CallSite> callSites = Lists.newArrayList();
  private SimpleDefinitionFinder defFinder;
  private GatherFunctions gatherFunctions = new GatherFunctions();

  ChainCalls(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    defFinder = new SimpleDefinitionFinder(compiler);
    defFinder.process(externs, root);

    NodeTraversal.traverse(compiler, root, new GatherCallSites());

    for (CallSite callSite : callSites) {
      callSite.parent.removeChild(callSite.n);
      callSite.n.removeChild(callSite.callNode);
      callSite.nextGetPropNode.replaceChild(callSite.nextGetPropFirstChildNode,
                                            callSite.callNode);
      compiler.reportCodeChange();
    }
  }

  /**
   * Determines whether a function always returns this.
   */
  private class GatherFunctions implements ScopedCallback {
    @Override
    public void enterScope(NodeTraversal t) {
      ControlFlowGraph<Node> cfg = t.getControlFlowGraph();

      for (DiGraphEdge<Node, Branch> s : cfg.getImplicitReturn().getInEdges()) {
        Node exitNode = s.getSource().getValue();
        if (!exitNode.isReturn() ||
            exitNode.getFirstChild() == null ||
            !exitNode.getFirstChild().isThis()) {
          badFunctionNodes.add(t.getScopeRoot());
          return;
        }
      }

      goodFunctionNodes.add(t.getScopeRoot());
    }

    @Override
    public void exitScope(NodeTraversal t) {
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
                                  Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
    }
  }

  private class GatherCallSites extends AbstractPostOrderCallback {
    /**
     * If the function call returns this and the next statement has the same
     * target expression, record the call site.
     */
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isExprResult()) {
        return;
      }

      Node callNode = n.getFirstChild();
      if (!callNode.isCall()) {
        return;
      }

      Node getPropNode = callNode.getFirstChild();
      if (!getPropNode.isGetProp()) {
        return;
      }

      Node getPropFirstChildNode = getPropNode.getFirstChild();

      Collection<Definition> definitions =
          defFinder.getDefinitionsReferencedAt(getPropNode);
      if (definitions == null) {
        return;
      }
      for (Definition definition : definitions) {
        Node rValue = definition.getRValue();
        if (rValue == null) {
          return;
        }
        if (badFunctionNodes.contains(rValue)) {
          return;
        }
        if (!goodFunctionNodes.contains(rValue)) {
          NodeTraversal.traverse(compiler, rValue, gatherFunctions);
          if (badFunctionNodes.contains(rValue)) {
            return;
          }
        }
      }

      Node nextNode = n.getNext();
      if (nextNode == null || !nextNode.isExprResult()) {
        return;
      }

      Node nextCallNode = nextNode.getFirstChild();
      if (!nextCallNode.isCall()) {
        return;
      }

      Node nextGetPropNode = nextCallNode.getFirstChild();
      if (!nextGetPropNode.isGetProp()) {
        return;
      }

      Node nextGetPropFirstChildNode = nextGetPropNode.getFirstChild();
      if (!compiler.areNodesEqualForInlining(
              nextGetPropFirstChildNode, getPropFirstChildNode)) {
        return;
      }

      if (NodeUtil.mayEffectMutableState(getPropFirstChildNode)) {
        return;
      }

      // We can't chain immediately as it we wouldn't recognize further
      // opportunities to chain.
      callSites.add(new CallSite(parent, n, callNode, nextGetPropNode,
                                 nextGetPropFirstChildNode));
    }
  }

  /** Records a call site to chain. */
  private static class CallSite {
    final Node parent;
    final Node n;
    final Node callNode;
    final Node nextGetPropNode;
    final Node nextGetPropFirstChildNode;

    CallSite(Node parent, Node n, Node callNode, Node nextGetPropNode,
             Node nextGetPropFirstChildNode) {
      this.parent = parent;
      this.n = n;
      this.callNode = callNode;
      this.nextGetPropNode = nextGetPropNode;
      this.nextGetPropFirstChildNode = nextGetPropFirstChildNode;
    }
  }
}
