/*
 * Copyright 2006 The Closure Compiler Authors.
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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Set;

/**
 * Collapses multiple variable declarations into a single one. i.e the
 * following:
 *
 * <pre>
 * var a;
 * var b = 1;
 * var c = 2;
 * </pre>
 *
 * becomes:
 *
 * <pre>var a, b = 1, c = 2;</pre>
 *
 * This reduces the generated code size. More optimizations are possible:
 * <li>Group all variable declarations inside a function into one such variable.
 * declaration block.</li>
 * <li>Re-use variables instead of declaring a new one if they are used for
 * only part of a function.</li>
 *
 * Similarly, also collapses assigns like:
 *
 * <pre>
 * a = true;
 * b = true;
 * var c = true;
 * </pre>
 *
 * becomes:
 *
 * <pre>var c = b = a = true;</pre>
 *
 */
class CollapseVariableDeclarations implements CompilerPass {
  /** Reference to JS Compiler */
  private final AbstractCompiler compiler;

  /** Encapsulation of information about a variable declaration collapse */
  private static class Collapse {
    /**
     * Variable declaration that any following var nodes should be
     * collapsed into
     */
    final Node startNode;

    /**
     * Last node (non-inclusive) of the chain of nodes to collapse.
     */
    final Node endNode;

    /** Parent of the nodes to the collapse */
    final Node parent;

    Collapse(Node startNode, Node endNode, Node parent) {
      this.startNode = startNode;
      this.endNode = endNode;
      this.parent = parent;
    }
  }

  /**
   * Collapses to do in this pass.
   */
  private final List<Collapse> collapses = Lists.newArrayList();

  /**
   * Nodes we've already looked at for collapsing, so that we don't look at them
   * again (we look ahead when examining what nodes can be collapsed, and the
   * node traversal may give them to us again)
   */
  private final Set<Node> nodesToCollapse = Sets.newHashSet();

  CollapseVariableDeclarations(AbstractCompiler compiler) {
    Preconditions.checkState(!compiler.getLifeCycleStage().isNormalized());
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    collapses.clear();
    nodesToCollapse.clear();

    NodeTraversal.traverse(compiler, root, new GatherCollapses());

    if (!collapses.isEmpty()) {
      applyCollapses();
      compiler.reportCodeChange();
    }
  }

  /**
   * Gathers all of the variable declarations / assignments that should be
   * collapsed into one.
   *
   * We do not do the collapsing as we go since node traversal would be affected
   * by the changes we are making to the parse tree.
   */
  private class GatherCollapses extends AbstractPostOrderCallback {

    // If a VAR is declared like
    // var x;
    // then we should not create new VAR nodes for it later in the tree.
    // This is a workaround for a bug in Firefox.
    private final Set<Var> blacklistedVars = Sets.newHashSet();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isVar()) {
        blacklistStubVars(t, n);
      }

      // Only care about var nodes
      if (!n.isVar() && !canBeRedeclared(n, t.getScope())) {
        return;
      }

      // If we've already looked at this node, skip it
      if (nodesToCollapse.contains(n)) {
        return;
      }

      // Adjacent VAR children of an IF node are the if and else parts and can't
      // be collapsed
      if (parent.isIf()) {
        return;
      }

      Node varNode = n;

      boolean hasVar = n.isVar();

      // Find variable declarations that follow this one (if any)
      n = n.getNext();

      boolean hasNodesToCollapse = false;

      while (n != null &&
          (n.isVar() || canBeRedeclared(n, t.getScope()))) {

        if (n.isVar()) {
          blacklistStubVars(t, n);
          hasVar = true;
        }

        nodesToCollapse.add(n);
        hasNodesToCollapse = true;

        n = n.getNext();
      }

      if (hasNodesToCollapse && hasVar) {
        nodesToCollapse.add(varNode);
        collapses.add(new Collapse(varNode, n, parent));
      }
    }

    private void blacklistStubVars(NodeTraversal t, Node varNode) {
      for (Node child = varNode.getFirstChild();
           child != null; child = child.getNext()) {
        if (child.getFirstChild() == null) {
          blacklistedVars.add(t.getScope().getVar(child.getString()));
        }
      }
    }

    private boolean canBeRedeclared(Node n, Scope s) {
      if (!NodeUtil.isExprAssign(n)) {
        return false;
      }
      Node assign = n.getFirstChild();
      Node lhs = assign.getFirstChild();

      if (!lhs.isName()) {
        return false;
      }

      Var var = s.getVar(lhs.getString());
      return var != null
          && var.getScope() == s
          && !isNamedParameter(var)
          && !blacklistedVars.contains(var);
    }
  }

  private boolean isNamedParameter(Var v) {
    return v.getParentNode().isParamList();
  }

  private void applyCollapses() {
    for (Collapse collapse : collapses) {

      Node var = new Node(Token.VAR);
      var.copyInformationFrom(collapse.startNode);
      collapse.parent.addChildBefore(var, collapse.startNode);

      boolean redeclaration = false;
      for (Node n = collapse.startNode; n != collapse.endNode;) {
        Node next = n.getNext();

        Preconditions.checkState(var.getNext() == n);
        collapse.parent.removeChildAfter(var);

        if (n.isVar()) {
          while (n.hasChildren()) {
            var.addChildToBack(n.removeFirstChild());
          }
        } else {
          Node assign = n.getFirstChild();
          Node lhs = assign.getFirstChild();
          Preconditions.checkState(lhs.isName());
          Node rhs = assign.getLastChild();
          lhs.addChildToBack(rhs.detachFromParent());
          var.addChildToBack(lhs.detachFromParent());
          redeclaration = true;
        }
        n = next;
      }

      if (redeclaration) {
        JSDocInfo info = new JSDocInfo();
        info.addSuppression("duplicate");
        var.setJSDocInfo(info);
      }
    }
  }
}
