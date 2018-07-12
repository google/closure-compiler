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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
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
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

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
  private final Deque<LiveVariablesAnalysis> liveAnalyses;
  private final boolean usePseudoNames;
  private LiveVariablesAnalysis liveness;

  private final Comparator<Var> coloringTieBreaker =
      new Comparator<Var>() {
        @Override
        public int compare(Var v1, Var v2) {
          return liveness.getVarIndex(v1.getName()) - liveness.getVarIndex(v2.getName());
        }
      };

  /**
   * @param usePseudoNames For debug purposes, when merging variable foo and bar
   * to foo, rename both variable to foo_bar.
   */
  CoalesceVariableNames(AbstractCompiler compiler, boolean usePseudoNames) {
    // The code is normalized at this point in the compilation process. This allows us to use the
    // fact that all variables have been given unique names. We can hoist coalesced variables to
    // VARS because we know that shadowing can't occur.
    checkState(compiler.getLifeCycleStage().isNormalized());

    this.compiler = compiler;
    colorings = new ArrayDeque<>();
    liveAnalyses = new ArrayDeque<>();
    this.usePseudoNames = usePseudoNames;
  }

  @Override
  public void process(Node externs, Node root) {
    checkNotNull(externs);
    checkNotNull(root);
    NodeTraversal.traverse(compiler, root, this);
    compiler.setLifeCycleStage(LifeCycleStage.RAW);
  }

  private static boolean shouldOptimizeScope(NodeTraversal t) {
    // TODO(user): We CAN do this in the global scope, just need to be
    // careful when something is exported. Liveness uses bit-vector for live
    // sets so I don't see compilation time will be a problem for running this
    // pass in the global scope.

    if (!t.getScopeRoot().isFunction()) {
      return false;
    }

    Map<String, Var> allVarsInFn = new HashMap<>();
    List<Var> orderedVars = new ArrayList<>();
    NodeUtil.getAllVarsDeclaredInFunction(
        allVarsInFn, orderedVars, t.getCompiler(), t.getScopeCreator(), t.getScope());

    return LiveVariablesAnalysis.MAX_VARIABLES_TO_ANALYZE > orderedVars.size();
  }

  @Override
  public void enterScope(NodeTraversal t) {
    Scope scope = t.getScope();
    if (!shouldOptimizeScope(t)) {
      return;
    }

    checkState(scope.isFunctionScope(), scope);

    // live variables analysis is based off of the control flow graph
    ControlFlowGraph<Node> cfg = t.getControlFlowGraph();

    liveness =
        new LiveVariablesAnalysis(
            cfg, scope, null, compiler, new Es6SyntacticScopeCreator(compiler));

    if (FeatureSet.ES3.contains(compiler.getOptions().getOutputFeatureSet())) {
      // If the function has exactly 2 params, mark them as escaped. This is a work-around for a
      // bug in IE 8 and below, where it throws an exception if you write to the parameters of the
      // callback in a sort(). See http://blickly.github.io/closure-compiler-issues/#58 and
      // https://www.zachleat.com/web/array-sort/
      Node enclosingFunction = scope.getRootNode();
      if (NodeUtil.getFunctionParameters(enclosingFunction).hasTwoChildren()) {
        liveness.markAllParametersEscaped();
      }
    }

    liveness.analyze();
    liveAnalyses.push(liveness);

    // The interference graph has the function's variables as its nodes and any interference
    // between the variables as the edges. Interference between two variables means that they are
    // alive at overlapping times, which means that their variable names cannot be coalesced.
    UndiGraph<Var, Void> interferenceGraph =
        computeVariableNamesInterferenceGraph(cfg, liveness.getEscapedLocals());

    // Color any interfering variables with different colors and any variables that can be safely
    // coalesced wih the same color.
    GraphColoring<Var, Void> coloring =
        new GreedyGraphColoring<>(interferenceGraph, coloringTieBreaker);
    coloring.color();
    colorings.push(coloring);
  }

  @Override
  public void exitScope(NodeTraversal t) {
    if (!shouldOptimizeScope(t)) {
      return;
    }
    colorings.pop();
    liveAnalyses.pop();
    liveness = liveAnalyses.peek();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (colorings.isEmpty() || !n.isName() || parent.isFunction()) {
      // Don't rename named functions.
      return;
    }

    Var var = liveness.getAllVariables().get(n.getString());
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
      compiler.reportChangeToEnclosingScope(n);

      if (NodeUtil.isNameDeclaration(parent)
          || NodeUtil.getEnclosingType(n, Token.DESTRUCTURING_LHS) != null) {
        makeDeclarationVar(coalescedVar);
        removeVarDeclaration(n);
      }
    } else {
      // This code block is slow but since usePseudoName is for debugging,
      // we should not sacrifice performance for non-debugging compilation to
      // make this fast.
      String pseudoName = null;
      Set<String> allMergedNames = new TreeSet<>();
      for (Var iVar : liveness.getAllVariablesInOrder()) {
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

      while (t.getScope().hasSlot(pseudoName)) {
        pseudoName += "$";
      }

      // Rename.
      n.setString(pseudoName);
      compiler.reportChangeToEnclosingScope(n);

      if (!vNode.getValue().equals(coalescedVar)
          && (NodeUtil.isNameDeclaration(parent)
              || NodeUtil.getEnclosingType(n, Token.DESTRUCTURING_LHS) != null)) {
        makeDeclarationVar(coalescedVar);
        removeVarDeclaration(n);
      }
    }
  }

  /**
   * In order to determine when it is appropriate to coalesce two variables, we use a live variables
   * analysis to make sure they are not alive at the same time. We take every pairing of variables
   * and for every CFG node, determine whether the two variables are alive at the same time. If two
   * variables are alive at the same time, we create an edge between them in the interference graph.
   * The interference graph is the input to a graph coloring algorithm that ensures any interfering
   * variables are marked in different color groups, while variables that can safely be coalesced
   * are assigned the same color group.
   *
   * @param cfg
   * @param escaped we don't want to coalesce any escaped variables
   * @return graph with variable nodes and edges representing variable interference
   */
  private UndiGraph<Var, Void> computeVariableNamesInterferenceGraph(
      ControlFlowGraph<Node> cfg, Set<? extends Var> escaped) {
    UndiGraph<Var, Void> interferenceGraph = LinkedUndirectedGraph.create();

    // First create a node for each non-escaped variable. We add these nodes in the order in which
    // they appear in the code because we want the names that appear earlier in the code to be used
    // when coalescing to variables that appear later in the code.
    List<Var> orderedVariables = liveness.getAllVariablesInOrder();

    for (Var v : orderedVariables) {
      if (escaped.contains(v)) {
        continue;
      }

      // NOTE(user): In theory, we CAN coalesce function names just like any variables. Our
      // Liveness analysis captures this just like it as described in the specification. However, we
      // saw some zipped and unzipped size increase after this. We are not totally sure why
      // that is but, for now, we will respect the dead functions and not play around with it
      if (v.getParentNode().isFunction()) {
        continue;
      }

      // NOTE: we skip class declarations for a combination of two reasons:
      // 1. they are block-scoped, so we would need to rewrite them as class expressions
      //      e.g. `class C {}` -> `var C = class {}` to avoid incorrect semantics
      //      (see testDontCoalesceClassDeclarationsWithDestructuringDeclaration).
      //    This is possible but increases pre-gzip code size and complexity.
      // 2. since function declaration coalescing seems to cause a size regression (as discussed
      //    above) we assume that coalescing class names may cause a similar size regression.
      if (v.getParentNode().isClass()) {
        continue;
      }

      // Skip lets and consts that have multiple variables declared in them, otherwise this produces
      // incorrect semantics. See test case "testCapture".
      if (v.isLet() || v.isConst()) {
        Node nameDecl = NodeUtil.getEnclosingNode(v.getNode(), NodeUtil::isNameDeclaration);
        if (NodeUtil.findLhsNodesInNode(nameDecl).size() > 1) {
          continue;
        }
      }

      interferenceGraph.createNode(v);
    }

    // Go through each variable and try to connect them.
    int v1Index = -1;
    for (Var v1 : orderedVariables) {
      v1Index++;

      int v2Index = -1;
      NEXT_VAR_PAIR:
      for (Var v2 : orderedVariables) {
        v2Index++;
        // Skip duplicate pairs.
        if (v1Index > v2Index) {
          continue;
        }

        if (!interferenceGraph.hasNode(v1) || !interferenceGraph.hasNode(v2)) {
          // Skip nodes that were not added. They are globals and escaped
          // locals. Also avoid merging a variable with itself.
          continue NEXT_VAR_PAIR;
        }

        if (v1.isParam() && v2.isParam()) {
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

          if ((state.getIn().isLive(v1Index) && state.getIn().isLive(v2Index))
              || (state.getOut().isLive(v1Index) && state.getOut().isLive(v2Index))) {
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
          boolean v1OutLive = state.getOut().isLive(v1Index);
          boolean v2OutLive = state.getOut().isLive(v2Index);
          CombinedLiveRangeChecker checker = new CombinedLiveRangeChecker(
              cfgNode.getValue(),
              new LiveRangeChecker(v1, v2OutLive ? null : v2),
              new LiveRangeChecker(v2, v1OutLive ? null : v1));
          checker.check(cfgNode.getValue());
          if (checker.connectIfCrossed(interferenceGraph)) {
            continue NEXT_VAR_PAIR;
          }
        }
      }
    }
    return interferenceGraph;
  }

  /**
   * A simple wrapper to call two LiveRangeChecker callbacks during the same traversal.
   */
  private static class CombinedLiveRangeChecker {

    private final Node root;
    private final LiveRangeChecker callback1;
    private final LiveRangeChecker callback2;

    CombinedLiveRangeChecker(
        Node root,
        LiveRangeChecker callback1,
        LiveRangeChecker callback2) {
      this.root = root;
      this.callback1 = callback1;
      this.callback2 = callback2;
    }

    void check(Node n) {
      // For most AST nodes, traverse the subtree in postorder because that's how the expressions
      // are evaluated.
      if (n == root || !ControlFlowGraph.isEnteringNewCfgNode(n)) {
        if ((n.isDestructuringLhs() && n.hasTwoChildren())
            || (n.isAssign() && n.getFirstChild().isDestructuringPattern())
            || n.isDefaultValue()) {
          // Evaluate the rhs of a destructuring assignment/declaration before the lhs.
          check(n.getSecondChild());
          check(n.getFirstChild());
        } else {
          for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
            check(c);
          }
        }
        visit(n, n.getParent());
      }
    }

    void visit(Node n, Node parent) {
      if (LiveRangeChecker.shouldVisit(n)) {
        callback1.visit(n, parent);
        callback2.visit(n, parent);
      }
    }

    boolean connectIfCrossed(UndiGraph<Var, Void> interferenceGraph) {
      if (callback1.crossed || callback2.crossed) {
        Var v1 = callback1.def;
        Var v2 = callback2.def;
        interferenceGraph.connectIfNotFound(v1, null, v2);
        return true;
      }
      return false;
    }
  }

  /**
   * Tries to remove variable declaration if the variable has been coalesced with another variable
   * that has already been declared. Any lets or consts are redeclared as vars because at this point
   * in the compilation, the code is normalized, so we can safely hoist variables without worrying
   * about shadowing.
   *
   * @param name name node of the variable being coalesced
   */
  private static void removeVarDeclaration(Node name) {
    Node var = NodeUtil.getEnclosingNode(name, NodeUtil::isNameDeclaration);
    Node parent = var.getParent();

    if (!var.isVar()) {
      var.setToken(Token.VAR);
    }
    checkState(var.isVar(), var);

    // Special case for enhanced for-loops
    if (NodeUtil.isEnhancedFor(parent)) {
      var.removeChild(name);
      parent.replaceChild(var, name);
    } else if (var.hasOneChild() && var.getFirstChild() == name) {
      // The removal is easy when there is only one variable in the VAR node.
      if (name.hasChildren()) {
        Node value = name.removeFirstChild();
        var.removeChild(name);
        Node assign = IR.assign(name, value).srcref(name);

        // We don't need to wrapped it with EXPR node if it is within a FOR.
        if (!parent.isVanillaFor()) {
          assign = NodeUtil.newExpr(assign);
        }
        parent.replaceChild(var, assign);

      } else {
        // In a FOR( ; ; ) node, we must replace it with an EMPTY or else it
        // becomes a FOR-IN node.
        NodeUtil.removeChild(parent, var);
      }
    } else {
      if (var.getFirstChild() == name && !name.hasChildren()) {
        name.detach();
      }
      // We are going to leave duplicated declaration otherwise.
    }
  }

  /**
   * Because the code has already been normalized by the time this pass runs, we can safely
   * redeclare any let and const coalesced variables as vars
   */
  private static void makeDeclarationVar(Var coalescedName) {
    if (coalescedName.isLet() || coalescedName.isConst()) {
      Node declNode =
          NodeUtil.getEnclosingNode(coalescedName.getParentNode(), NodeUtil::isNameDeclaration);
      declNode.setToken(Token.VAR);
    }
  }

  private static class LiveRangeChecker {
    boolean defFound = false;
    boolean crossed = false;

    private final Var def;

    @Nullable
    private final Var use;

    public LiveRangeChecker(Var def, Var use) {
      this.def = checkNotNull(def);
      this.use = use;
    }

    /**
     * @return Whether any LiveRangeChecker would be interested in the node.
     */
    public static boolean shouldVisit(Node n) {
      return (n.isName() || (n.hasChildren() && n.getFirstChild().isName()));
    }

    void visit(Node n, Node parent) {
      if (!defFound && isAssignTo(def, n, parent)) {
        defFound = true;
      }

      if (defFound && (use == null || isReadFrom(use, n))) {
        crossed = true;
      }
    }

    static boolean isAssignTo(Var var, Node n, Node parent) {
      if (n.isName()) {
        if (parent.isParamList()) {
          // In a function declaration, the formal parameters are assigned.
          return var.getName().equals(n.getString());
        } else if (NodeUtil.isNameDeclaration(parent) && n.hasChildren()) {
          // If this is a VAR declaration, if the name node has a child, we are
          // assigning to that name.
          return var.getName().equals(n.getString());
        } else if (NodeUtil.isLhsByDestructuring(n)) {
          return var.getName().equals(n.getString());
        }
      } else if (NodeUtil.isAssignmentOp(n)) {
        // Lastly, any assignmentOP is also an assign.
        Node name = n.getFirstChild();
        return name.isName() && var.getName().equals(name.getString());
      }
      return false; // Definitely a read.
    }

    static boolean isReadFrom(Var var, Node name) {
      return name.isName()
          && var.getName().equals(name.getString())
          && !NodeUtil.isNameDeclOrSimpleAssignLhs(name, name.getParent());
    }
  }
}
