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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.ControlFlowGraph.AbstractCfgNodeTraversalCallback;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.DataFlowAnalysis.FlowState;
import com.google.javascript.jscomp.LiveVariablesAnalysis.LiveVariableLattice;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.GraphColoring;
import com.google.javascript.jscomp.graph.GraphColoring.GreedyGraphColoring;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.LinkedUndirectedGraph;
import com.google.javascript.jscomp.graph.UndiGraph;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reuse variable names if possible.
 *
 * <p>For example, from <code>var x = 1; print(x); var y = 2; print(y); </code>
 * to <code>var x = 1; print(x); x = 2; print(x)</code>. The benefits are
 * slightly shorter code because of the removed <code>var<code> declaration,
 * less unique variables in hope for better renaming, and finally better gzip
 * compression.
 *
 * <p>The pass operates similar to a typical register allocator found in an
 * optimizing compiler by first computing live ranges with
 * {@link LiveVariablesAnalysis} and a variable interference graph. Then it uses
 * graph coloring in {@link GraphColoring} to determine which two variables can
 * be merge together safely.
 *
 */
class CoalesceVariableNames extends AbstractPostOrderCallback implements
    CompilerPass, ScopedCallback {

  private final AbstractCompiler compiler;
  private final Deque<GraphColoring<Var, Void>> colorings;
  private final boolean usePseudoNames;

  private static final Comparator<Var> coloringTieBreaker =
      new Comparator<Var>() {
    @Override
    public int compare(Var v1, Var v2) {
      return v1.index - v2.index;
    }
  };

  /**
   * @param usePseudoNames For debug purposes, when merging variable foo and bar
   * to foo, rename both variable to foo_bar.
   */
  CoalesceVariableNames(AbstractCompiler compiler, boolean usePseudoNames) {
    Preconditions.checkState(!compiler.getLifeCycleStage().isNormalized());

    this.compiler = compiler;
    colorings = new LinkedList<>();
    this.usePseudoNames = usePseudoNames;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  private static boolean shouldOptimizeScope(Scope scope) {
    // TODO(user): We CAN do this in the global scope, just need to be
    // careful when something is exported. Liveness uses bit-vector for live
    // sets so I don't see compilation time will be a problem for running this
    // pass in the global scope.
    if (scope.isGlobal()) {
      return false;
    }

    return LiveVariablesAnalysis.MAX_VARIABLES_TO_ANALYZE >= scope.getVarCount();
  }

  @Override
  public void enterScope(NodeTraversal t) {
    Scope scope = t.getScope();
    if (!shouldOptimizeScope(scope)) {
      return;
    }

    Preconditions.checkState(scope.isFunctionScope(), scope);

    ControlFlowGraph<Node> cfg = t.getControlFlowGraph();
    LiveVariablesAnalysis liveness = new LiveVariablesAnalysis(cfg, scope, compiler);
    // If the function has exactly 2 params, mark them as escaped. This is
    // a work-around for an IE bug where it throws an exception if you
    // write to the parameters of the callback in a sort(). See:
    // http://blickly.github.io/closure-compiler-issues/#58
    if (NodeUtil.getFunctionParameters(scope.getRootNode()).getChildCount() == 2) {
      liveness.markAllParametersEscaped();
    }
    liveness.analyze();

    UndiGraph<Var, Void> interferenceGraph =
        computeVariableNamesInterferenceGraph(
            t, cfg, (Set<Var>) liveness.getEscapedLocals());

    GraphColoring<Var, Void> coloring =
        new GreedyGraphColoring<>(interferenceGraph, coloringTieBreaker);

    coloring.color();
    colorings.push(coloring);
  }

  @Override
  public void exitScope(NodeTraversal t) {
    if (!shouldOptimizeScope(t.getScope())) {
      return;
    }
    colorings.pop();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (colorings.isEmpty() || !n.isName() || parent.isFunction()) {
      // Don't rename named functions.
      return;
    }
    Var var = t.getScope().getVar(n.getString());
    GraphNode<Var, Void> vNode = colorings.peek().getGraph().getNode(var);
    if (vNode == null) {
      // This is not a local.
      return;
    }
    Var coalescedVar = colorings.peek().getPartitionSuperNode(var);

    if (!usePseudoNames) {
      if (vNode.getValue().equals(coalescedVar)) {
        // The coalesced name is itself, nothing to do.
        return;
      }

      // Rename.
      n.setString(coalescedVar.name);
      compiler.reportCodeChange();

      if (parent.isVar()) {
        removeVarDeclaration(n);
      }
    } else {
      // This code block is slow but since usePseudoName is for debugging,
      // we should not sacrifice performance for non-debugging compilation to
      // make this fast.
      String pseudoName = null;
      Set<String> allMergedNames = new TreeSet<>();
      for (Iterator<Var> i = t.getScope().getVars(); i.hasNext();) {
        Var iVar = i.next();

        // Look for all the variables that can be merged (in the graph by now)
        // and it is merged with the current coalescedVar.
        if (colorings.peek().getGraph().getNode(iVar) != null
            && coalescedVar.equals(colorings.peek().getPartitionSuperNode(iVar))) {
          allMergedNames.add(iVar.name);
        }
      }

      // Keep its original name.
      if (allMergedNames.size() == 1) {
        return;
      }

      pseudoName = Joiner.on("_").join(allMergedNames);

      while (t.getScope().isDeclared(pseudoName, true)) {
        pseudoName += "$";
      }

      n.setString(pseudoName);
      compiler.reportCodeChange();

      if (!vNode.getValue().equals(coalescedVar) && parent.isVar()) {
        removeVarDeclaration(n);
      }
    }
  }

  private UndiGraph<Var, Void> computeVariableNamesInterferenceGraph(
      NodeTraversal t, ControlFlowGraph<Node> cfg, Set<Var> escaped) {
    UndiGraph<Var, Void> interferenceGraph =
        LinkedUndirectedGraph.create();
    Scope scope = t.getScope();

    // First create a node for each non-escaped variable.
    for (Iterator<Var> i = scope.getVars(); i.hasNext();) {
      Var v = i.next();
      if (!escaped.contains(v)) {

        // TODO(user): In theory, we CAN coalesce function names just like
        // any variables. Our Liveness analysis captures this just like it as
        // described in the specification. However, we saw some zipped and
        // and unzipped size increase after this. We are not totally sure why
        // that is but, for now, we will respect the dead functions and not play
        // around with it.
        if (!v.getParentNode().isFunction()) {
          interferenceGraph.createNode(v);
        }
      }
    }

    // Go through each variable and try to connect them.
    for (Iterator<Var> i1 = scope.getVars(); i1.hasNext();) {
      Var v1 = i1.next();

      NEXT_VAR_PAIR:
      for (Iterator<Var> i2 = scope.getVars(); i2.hasNext();) {
        Var v2 = i2.next();

        // Skip duplicate pairs.
        if (v1.index >= v2.index) {
          continue;
        }

        if (!interferenceGraph.hasNode(v1) || !interferenceGraph.hasNode(v2)) {
          // Skip nodes that were not added. They are globals and escaped
          // locals. Also avoid merging a variable with itself.
          continue NEXT_VAR_PAIR;
        }

        if (v1.getParentNode().isParamList() && v2.getParentNode().isParamList()) {
          interferenceGraph.connectIfNotFound(v1, null, v2);
          continue NEXT_VAR_PAIR;
        }

        // Go through every CFG node in the program and look at
        // this variable pair. If they are both live at the same
        // time, add an edge between them and continue to the next pair.
        NEXT_CROSS_CFG_NODE:
        for (DiGraphNode<Node, Branch> cfgNode : cfg.getDirectedGraphNodes()) {
          if (cfg.isImplicitReturn(cfgNode)) {
            continue NEXT_CROSS_CFG_NODE;
          }

          FlowState<LiveVariableLattice> state = cfgNode.getAnnotation();
          // Check the live states and add edge when possible.
          if ((state.getIn().isLive(v1) && state.getIn().isLive(v2))
              || (state.getOut().isLive(v1) && state.getOut().isLive(v2))) {
            interferenceGraph.connectIfNotFound(v1, null, v2);
            continue NEXT_VAR_PAIR;
          }
        }

        // v1 and v2 might not have an edge between them! woohoo. there's
        // one last sanity check that we have to do: we have to check
        // if there's a collision *within* the cfg node.
        NEXT_INTRA_CFG_NODE:
        for (DiGraphNode<Node, Branch> cfgNode : cfg.getDirectedGraphNodes()) {
          if (cfg.isImplicitReturn(cfgNode)) {
            continue NEXT_INTRA_CFG_NODE;
          }

          FlowState<LiveVariableLattice> state = cfgNode.getAnnotation();
          boolean v1OutLive = state.getOut().isLive(v1);
          boolean v2OutLive = state.getOut().isLive(v2);
          CombinedLiveRangeChecker checker = new CombinedLiveRangeChecker(
              new LiveRangeChecker(v1, v2OutLive ? null : v2),
              new LiveRangeChecker(v2, v1OutLive ? null : v1));
          NodeTraversal.traverse(
              compiler,
              cfgNode.getValue(),
              checker);
          if (checker.connectIfCrossed(interferenceGraph)) {
            continue NEXT_VAR_PAIR;
          }
        }
      }
    }
    return interferenceGraph;
  }

  /**
   * A simple wrapper calls to call two AbstractCfgNodeTraversalCallback
   * callback during the same traversal.  Both traversals must have the same
   * "shouldTraverse" conditions.
   */
  private static class CombinedLiveRangeChecker
      extends AbstractCfgNodeTraversalCallback {

    private final LiveRangeChecker callback1;
    private final LiveRangeChecker callback2;

    CombinedLiveRangeChecker(
        LiveRangeChecker callback1,
        LiveRangeChecker callback2) {
      this.callback1 = callback1;
      this.callback2 = callback2;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (LiveRangeChecker.shouldVisit(n)) {
        callback1.visit(t, n, parent);
        callback2.visit(t, n, parent);
      }
    }

    boolean connectIfCrossed(UndiGraph<Var, Void> interferenceGraph) {
      if (callback1.crossed || callback2.crossed) {
        Var v1 = callback1.getDef();
        Var v2 = callback2.getDef();
        interferenceGraph.connectIfNotFound(v1, null, v2);
        return true;
      }
      return false;
    }
  }

  /**
   * Tries to remove variable declaration if the variable has been coalesced
   * with another variable that has already been declared.
   */
  private static void removeVarDeclaration(Node name) {
    Node var = name.getParent();
    Node parent = var.getParent();

    // Special case when we are in FOR-IN loop.
    if (NodeUtil.isForIn(parent)) {
      var.removeChild(name);
      parent.replaceChild(var, name);
    } else if (var.hasOneChild()) {
      // The removal is easy when there is only one variable in the VAR node.
      if (name.hasChildren()) {
        Node value = name.removeFirstChild();
        var.removeChild(name);
        Node assign = IR.assign(name, value).srcref(name);

        // We don't need to wrapped it with EXPR node if it is within a FOR.
        if (!parent.isFor()) {
          assign = NodeUtil.newExpr(assign);
        }
        parent.replaceChild(var, assign);

      } else {
        // In a FOR( ; ; ) node, we must replace it with an EMPTY or else it
        // becomes a FOR-IN node.
        NodeUtil.removeChild(parent, var);
      }
    } else {
      if (!name.hasChildren()) {
        var.removeChild(name);
      }
      // We are going to leave duplicated declaration otherwise.
    }
  }

  private static class LiveRangeChecker
      extends AbstractCfgNodeTraversalCallback {
    boolean defFound = false;
    boolean crossed = false;
    private final Var def;
    private final Var use;

    public LiveRangeChecker(Var def, Var use) {
      this.def = def;
      this.use = use;
    }

    Var getDef() {
      return def;
    }

    /**
     * @return Whether any LiveRangeChecker would be interested in the node.
     */
    public static boolean shouldVisit(Node n) {
      return (n.isName()
        || (n.hasChildren() && n.getFirstChild().isName()));
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!defFound && isAssignTo(def, n, parent)) {
        defFound = true;
      }

      if (defFound && (use == null || isReadFrom(use, n))) {
        crossed = true;
      }
    }

    private static boolean isAssignTo(Var var, Node n, Node parent) {
      if (n.isName() && var.getName().equals(n.getString()) && parent != null) {
        if (parent.isParamList()) {
          // In a function declaration, the formal parameters are assigned.
          return true;
        } else if (parent.isVar()) {
          // If this is a VAR declaration, if the name node has a child, we are
          // assigning to that name.
          return n.hasChildren();
        }
        return false; // Definitely a read.
      } else {
        // Lastly, any assignmentOP is also an assign.
        Node name = n.getFirstChild();
        return name != null && name.isName()
            && var.getName().equals(name.getString())
            && NodeUtil.isAssignmentOp(n);
      }
    }

    private static boolean isReadFrom(Var var, Node name) {
      return name != null
          && name.isName()
          && var.getName().equals(name.getString())
          && !NodeUtil.isVarOrSimpleAssignLhs(name, name.getParent());
    }
  }
}
