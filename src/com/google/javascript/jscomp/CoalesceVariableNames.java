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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.ControlFlowGraph.AbstractCfgNodeTraversalCallback;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.DataFlowAnalysis.FlowState;
import com.google.javascript.jscomp.LiveVariablesAnalysis.LiveVariableLattice;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.GraphColoring;
import com.google.javascript.jscomp.graph.LinkedUndirectedGraph;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.GraphColoring.GreedyGraphColoring;
import com.google.javascript.jscomp.graph.UndiGraph;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;

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
*
 */
class CoalesceVariableNames extends AbstractPostOrderCallback implements
    CompilerPass, ScopedCallback {

  private final AbstractCompiler compiler;
  private final Deque<GraphColoring<Var, ?>> colorings;

  /** Logs all name assignments */
  private StringBuilder coalescedLog;

  private static final Comparator<Var> coloringTieBreaker =
      new Comparator<Var>() {
    public int compare(Var v1, Var v2) {
      return v1.index - v2.index;
    }
  };

  CoalesceVariableNames(AbstractCompiler compiler) {
    this.compiler = compiler;
    colorings = Lists.newLinkedList();
    coalescedLog = new StringBuilder();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);

    // Lastly, write the report to the debug log.
    compiler.addToDebugLog("JS vars coalesced:\n" + coalescedLog.toString());
    coalescedLog = new StringBuilder();
  }

  @Override
  public void enterScope(NodeTraversal t) {
    // TODO(user): We CAN do this in the global scope, just need to be
    // careful when something is exported. Liveness uses bit-vector for live
    // sets so I don't see compilation time will be a problem for running this
    // pass in the global scope.
    Scope scope = t.getScope();
    if (scope.isGlobal()) {
      return;
    }
    ControlFlowGraph<Node> cfg = t.getControlFlowGraph();

    LiveVariablesAnalysis liveness =
        new LiveVariablesAnalysis(cfg, scope, compiler);
    liveness.analyze();

    UndiGraph<Var, Void> interferenceGraph =
        computeVariableNamesInterferenceGraph(
            t, cfg, liveness.getEscapedLocals());

    GraphColoring<Var, ?> coloring =
        new GreedyGraphColoring<Var, Void>(interferenceGraph,
            coloringTieBreaker);

    coloring.color();
    colorings.push(coloring);
  }

  @Override
  public void exitScope(NodeTraversal t) {
    if (t.inGlobalScope()) {
      return;
    }
    colorings.pop();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (colorings.isEmpty() || !NodeUtil.isName(n) ||
        NodeUtil.isFunction(parent)) {
      // Don't rename named functions.
      return;
    }
    Var var = t.getScope().getVar(n.getString());
    GraphNode<Var, ?> vNode = colorings.peek().getGraph().getNode(var);
    if (vNode == null) {
      // This is not a local.
      return;
    }
    Var coalescedVar = colorings.peek().getPartitionSuperNode(var);
    if (vNode.getValue().equals(coalescedVar)) {
      // The coalesced name is itself, nothing to do.
      return;
    }

    if (var.getNameNode() == n) {
      coalescedLog.append(n.getString()).append(" => ")
          .append(coalescedVar.name).append(" in ")
          .append(t.getSourceName()).append(':')
          .append(n.getLineno()).append('\n');
    }

    // Rename.
    n.setString(coalescedVar.name);
    compiler.reportCodeChange();

    if (NodeUtil.isVar(parent)) {
      removeVarDeclaration(n);
    }
  }

  private UndiGraph<Var, Void> computeVariableNamesInterferenceGraph(
      NodeTraversal t, ControlFlowGraph<Node> cfg, Set<Var> escaped) {
    UndiGraph<Var, Void> interferenceGraph =
        new LinkedUndirectedGraph<Var, Void>();
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
        if (!NodeUtil.isFunction(v.getParentNode())) {
          interferenceGraph.createNode(v);
        }
      }
    }

    // Go through every single point of the program and look at each variable
    // pairs. If they are both live at the same time, at an edge between them.
    for (DiGraphNode<Node, Branch> cfgNode : cfg.getDirectedGraphNodes()) {
      FlowState<LiveVariableLattice> state = cfgNode.getAnnotation();
      if (cfg.isImplicitReturn(cfgNode)) {
        continue;
      }

      int varsInScope = scope.getVarCount();
      ArrayList<CombinedLiveRangeChecker> rangesToCheck =
          new ArrayList<CombinedLiveRangeChecker>(
              varsInScope * varsInScope);

      for (Iterator<Var> i1 = scope.getVars(); i1.hasNext();) {
        Var v1 = i1.next();
        for (Iterator<Var> i2 = scope.getVars(); i2.hasNext();) {
          Var v2 = i2.next();

          if (v1 == v2 || !interferenceGraph.hasNode(v1) ||
              !interferenceGraph.hasNode(v2)) {
            // Skip nodes that were not added. They are globals and escaped
            // locals. Also avoid merging a variable with itself.
            continue;
          }

          boolean v1OutLive = state.getOut().isLive(v1);
          boolean v2OutLive = state.getOut().isLive(v2);
          // Finally, check the live states and add edge when possible.
          if (v1.getParentNode().getType() == Token.LP &&
              v2.getParentNode().getType() == Token.LP) {
            interferenceGraph.connectIfNotFound(v1, null, v2);
          } else if ((state.getIn().isLive(v1) && state.getIn().isLive(v2)) ||
              (v1OutLive && v2OutLive)) {
            interferenceGraph.connectIfNotFound(v1, null, v2);
          } else {
            LiveRangeChecker checker1 =
                new LiveRangeChecker(v1, v2OutLive ? null : v2);
            LiveRangeChecker checker2 =
                new LiveRangeChecker(v2, v1OutLive ? null : v1);
            rangesToCheck.add(new CombinedLiveRangeChecker(checker1, checker2));
          }
        }
      }

      // Do the collected live range checks.
      checkRanges(rangesToCheck, cfgNode.getValue());
      for (CombinedLiveRangeChecker range : rangesToCheck) {
        range.connectIfCrossed(interferenceGraph);
      }
    }
    return interferenceGraph;
  }

  /**
   * Check if the live ranges of the given pairs of variables overlap within a
   * node represented by a CFG node. This only occurs when a variable {@code
   * def} is assigned within a node and a second variable {@code use} is live
   * after it. This function will traversing the subtree in a
   * left-to-right-post-order fashion in correspondent to how expressions are
   * evaluated.
   *
   * @param root The current subtree represent by a control flow graph node.
   */
  private void checkRanges(
      ArrayList<CombinedLiveRangeChecker> rangesToCheck, Node root) {
    CombinedCfgNodeLiveRangeChecker callbacks =
      new CombinedCfgNodeLiveRangeChecker(rangesToCheck);
    NodeTraversal.traverse(compiler, root, callbacks);
  }

  /**
   * A simple wrapper calls to call two AbstractCfgNodeTraversalCallback
   * callback during the same traversal.  All traversals callbacks have the same
   * "shouldTraverse" conditions.
   */
  private static class CombinedCfgNodeLiveRangeChecker
      extends AbstractCfgNodeTraversalCallback {

    private final ArrayList<CombinedLiveRangeChecker> callbacks;

    CombinedCfgNodeLiveRangeChecker(
        ArrayList<CombinedLiveRangeChecker> callbacks) {
      this.callbacks = callbacks;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (CombinedLiveRangeChecker.shouldVisit(n)) {
        for (CombinedLiveRangeChecker callback : callbacks) {
          callback.visit(t, n, parent);
        }
      }
    }
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

    /**
     * @return Whether any CombinedLiveRangeChecker would be interested in the
     * node.
     */
    public static boolean shouldVisit(Node n) {
      return LiveRangeChecker.shouldVisit(n);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      callback1.visit(t, n, parent);
      callback2.visit(t, n, parent);
    }

    void connectIfCrossed(UndiGraph<Var, Void> interferenceGraph) {
      if (callback1.crossed || callback2.crossed) {
        Var v1 = callback1.getDef();
        Var v2 = callback2.getDef();
        interferenceGraph.connectIfNotFound(v1, null, v2);
      }
    }
  }

  /**
   * Tries to remove variable declaration if the variable has been coalesced
   * with another variable that has already been declared.
   */
  private void removeVarDeclaration(Node name) {
    Node var = name.getParent();
    Node parent = var.getParent();

    // Special case when we are in FOR-IN loop.
    if (NodeUtil.isForIn(parent)) {
      var.removeChild(name);
      parent.replaceChild(var, name);
    } else if (var.getChildCount() == 1) {
      // The removal is easy when there is only one variable in the VAR node.
      if (name.hasChildren()) {
        Node value = name.removeFirstChild();
        var.removeChild(name);
        Node assign = new Node(Token.ASSIGN, name, value);

        // We don't need to wrapped it with EXPR node if it is within a FOR.
        if (parent.getType() != Token.FOR) {
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
      return (NodeUtil.isName(n)
        || (n.hasChildren() && NodeUtil.isName(n.getFirstChild())));
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
      if (NodeUtil.isName(n) && var.getName().equals(n.getString()) &&
          parent != null) {
        if (parent.getType() == Token.LP) {
          // In a function declaration, the formal parameters are assigned.
          return true;
        } else if (NodeUtil.isVar(parent)) {
          // If this is a VAR declaration, if the name node has a child, we are
          // assigning to that name.
          return n.hasChildren();
        }
        return false; // Definitely a read.
      } else {
        // Lastly, any assignmentOP is also an assign.
        Node name = n.getFirstChild();
        return name != null && NodeUtil.isName(name) &&
          var.getName().equals(name.getString()) &&
          NodeUtil.isAssignmentOp(n);
      }
    }

    private static boolean isReadFrom(Var var, Node name) {
      return name != null && NodeUtil.isName(name) &&
          var.getName().equals(name.getString()) &&
          !NodeUtil.isLhs(name, name.getParent());
    }
  }
}
