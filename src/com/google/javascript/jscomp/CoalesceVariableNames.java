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
import static java.util.Comparator.comparingInt;

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.DataFlowAnalysis.LinearFlowState;
import com.google.javascript.jscomp.LiveVariablesAnalysis.LiveVariableLattice;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.NodeUtil.AllVarsDeclaredInFunction;
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
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
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
 */
class CoalesceVariableNames extends AbstractPostOrderCallback
    implements CompilerPass, ScopedCallback {

  private final AbstractCompiler compiler;
  private final MemoizedScopeCreator scopeCreator;
  private final Deque<GraphColoring<Var, Void>> colorings;
  private final Deque<LiveVariablesAnalysis> liveAnalyses;
  private final boolean usePseudoNames;
  private final AstFactory astFactory;
  private LiveVariablesAnalysis liveness;

  private final Comparator<Var> coloringTieBreaker =
      comparingInt((Var arg) -> liveness.getVarIndex(arg.getName()));

  /** A stack of shouldOptimizeScope results. */
  private final Deque<Boolean> shouldOptimizeScopeStack = new ArrayDeque<>();

  /**
   * @param usePseudoNames For debug purposes, when merging variable foo and bar to foo, rename both
   *     variable to foo_bar.
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
    this.astFactory = compiler.createAstFactory();
    this.scopeCreator = new MemoizedScopeCreator(new SyntacticScopeCreator(compiler));
  }

  @Override
  public void process(Node externs, Node root) {
    checkNotNull(externs);
    checkNotNull(root);
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(this)
        .setScopeCreator(this.scopeCreator)
        .traverse(root);
    compiler.setLifeCycleStage(LifeCycleStage.RAW);
  }

  /** Returns populated AllVarsDeclaredInFunction object iff shouldOptimizeScope is true. */
  private static AllVarsDeclaredInFunction shouldOptimizeScope(NodeTraversal t) {
    // TODO(user): We CAN do this in the global scope, just need to be
    // careful when something is exported. Liveness uses bit-vector for live
    // sets so I don't see compilation time will be a problem for running this
    // pass in the global scope.

    if (t.getScopeRoot().isFunction()) {
      AllVarsDeclaredInFunction allVarsDeclaredInFunction =
          NodeUtil.getAllVarsDeclaredInFunction(t.getCompiler(), t.getScopeCreator(), t.getScope());
      if (LiveVariablesAnalysis.MAX_VARIABLES_TO_ANALYZE
          > allVarsDeclaredInFunction.getAllVariablesInOrder().size()) {
        return allVarsDeclaredInFunction;
      }
    }

    return null;
  }

  @Override
  public void enterScope(NodeTraversal t) {
    AllVarsDeclaredInFunction allVarsDeclaredInFunction = shouldOptimizeScope(t);
    if (allVarsDeclaredInFunction == null) {
      shouldOptimizeScopeStack.push(false);
      return;
    }
    shouldOptimizeScopeStack.push(true);

    Scope scope = t.getScope();
    checkState(scope.isFunctionScope(), scope);

    // live variables analysis is based off of the control flow graph
    ControlFlowGraph<Node> cfg = t.getControlFlowGraph();

    liveness =
        new LiveVariablesAnalysis(
            cfg, scope, null, compiler, this.scopeCreator, allVarsDeclaredInFunction);

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
    if (!shouldOptimizeScopeStack.pop()) {
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
      n.setString(coalescedVar.getName());
      compiler.reportChangeToEnclosingScope(n);

      if (NodeUtil.isNameDeclaration(parent)
          || (NodeUtil.getEnclosingType(n, Token.DESTRUCTURING_LHS) != null
              && NodeUtil.isLhsByDestructuring(n))) {
        makeDeclarationVar(coalescedVar);
        removeVarDeclaration(n);
      }
    } else {
      // This code block is slow but since usePseudoName is for debugging,
      // we should not sacrifice performance for non-debugging compilation to
      // make this fast.
      Set<String> allMergedNames = new TreeSet<>();
      for (Var iVar : liveness.getAllVariablesInOrder()) {
        // Look for all the variables that can be merged (in the graph by now)
        // and it is merged with the current coalescedVar.
        if (colorings.peek().getGraph().getNode(iVar) != null
            && coalescedVar.equals(colorings.peek().getPartitionSuperNode(iVar))) {
          allMergedNames.add(iVar.getName());
        }
      }

      // Keep its original name.
      if (allMergedNames.size() == 1) {
        return;
      }

      String pseudoName = Joiner.on("_").join(allMergedNames);

      while (t.getScope().hasSlot(pseudoName)) {
        pseudoName += "$";
      }

      // Rename.
      n.setString(pseudoName);
      compiler.reportChangeToEnclosingScope(n);

      if (!vNode.getValue().equals(coalescedVar)
          && (NodeUtil.isNameDeclaration(parent)
              || (NodeUtil.getEnclosingType(n, Token.DESTRUCTURING_LHS) != null
                  && NodeUtil.isLhsByDestructuring(n)))) {
        makeDeclarationVar(coalescedVar);
        removeVarDeclaration(n);
      }
    }
  }

  /**
   * In order to determine when it is appropriate to coalesce two variables, we use a live variables
   * analysis to make sure they are not alive at the same time. We take every CFG node and determine
   * which pairs of variables are alive at the same time. These pairs are set to true in a bit map.
   * We take every pairing of variables and use the bit map to check if the two variables are alive
   * at the same time. If two variables are alive at the same time, we create an edge between them
   * in the interference graph. The interference graph is the input to a graph coloring algorithm
   * that ensures any interfering variables are marked in different color groups, while variables
   * that can safely be coalesced are assigned the same color group.
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
    Var[] orderedVariables = liveness.getAllVariablesInOrder().toArray(new Var[0]);

    // index i in interferenceGraphNodes is set to true when interferenceGraph
    // has node orderedVariables[i]
    BitSet interferenceGraphNodes = new BitSet();

    // interferenceBitSet[i] = indices of all variables that should have an edge with
    // orderedVariables[i]
    BitSet[] interferenceBitSet = new BitSet[orderedVariables.length];
    Arrays.setAll(interferenceBitSet, i -> new BitSet());

    int vIndex = -1;
    for (Var v : orderedVariables) {
      vIndex++;
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
      // Skipping vars technically isn't needed for correct semantics, but works around a Safari
      // bug for var redeclarations (https://github.com/google/closure-compiler/issues/3164)
      if (isInMultipleLvalueDecl(v)) {
        continue;
      }

      interferenceGraph.createNode(v);
      interferenceGraphNodes.set(vIndex);
    }

    // Go through every CFG node in the program and look at variables that are live.
    // Set the pair of live variables in interferenceBitSet so we can add an edge between them.
    for (DiGraphNode<Node, Branch> cfgNode : cfg.getNodes()) {
      if (cfg.isImplicitReturn(cfgNode)) {
        continue;
      }

      LinearFlowState<LiveVariableLattice> state = cfgNode.getAnnotation();

      // Check the live states and add edge when possible. An edge between two variables
      // means that they are alive at overlapping times, which means that their
      // variable names cannot be coalesced.
      LiveVariableLattice livein = state.getIn();
      for (int i = livein.nextSetBit(0); i >= 0; i = livein.nextSetBit(i + 1)) {
        for (int j = livein.nextSetBit(i); j >= 0; j = livein.nextSetBit(j + 1)) {
          interferenceBitSet[i].set(j);
        }
      }
      LiveVariableLattice liveout = state.getOut();
      for (int i = liveout.nextSetBit(0); i >= 0; i = liveout.nextSetBit(i + 1)) {
        for (int j = liveout.nextSetBit(i); j >= 0; j = liveout.nextSetBit(j + 1)) {
          interferenceBitSet[i].set(j);
        }
      }

      LiveRangeChecker liveRangeChecker =
          new LiveRangeChecker(cfgNode.getValue(), orderedVariables, state);
      liveRangeChecker.check(cfgNode.getValue());
      liveRangeChecker.setCrossingVariables(interferenceBitSet);
    }

    // Go through each variable and try to connect them.
    int v1Index = -1;
    for (Var v1 : orderedVariables) {
      v1Index++;

      int v2Index = -1;
      for (Var v2 : orderedVariables) {
        v2Index++;
        // Skip duplicate pairs. Also avoid merging a variable with itself.
        if (v1Index > v2Index) {
          continue;
        }

        if (!interferenceGraphNodes.get(v1Index) || !interferenceGraphNodes.get(v2Index)) {
          // Skip nodes that were not added. They are globals and escaped locals.
          continue;
        }

        if ((v1.isParam() && v2.isParam()) || interferenceBitSet[v1Index].get(v2Index)) {
          // Add an edge between variable pairs that are both parameters
          // because we don't want parameters to share a name.
          interferenceGraph.connectIfNotFound(v1, null, v2);
        }
      }
    }
    return interferenceGraph;
  }

  /**
   * Returns whether this variable's declaration also declares other names.
   *
   * <p>For example, this would return true for `x` in `let [x, y, z] = []`;
   */
  private boolean isInMultipleLvalueDecl(Var v) {
    Token declarationType = v.declarationType();
    switch (declarationType) {
      case LET:
      case CONST:
      case VAR:
        Node nameDecl = NodeUtil.getEnclosingNode(v.getNode(), NodeUtil::isNameDeclaration);
        return NodeUtil.findLhsNodesInNode(nameDecl).size() > 1;
      default:
        return false;
    }
  }

  /**
   * Remove variable declaration if the variable has been coalesced with another variable that has
   * already been declared.
   *
   * <p>A precondition is that if the variable has already been declared, it must be the only lvalue
   * in said declaration. For example, this method will not accept `var x = 1, y = 2`. In theory we
   * could leave in the `var` declaration, but var shadowing of params triggers a Safari bug:
   * https://bugs.webkit.org/show_bug.cgi?id=182414 Another
   *
   * @param name name node of the variable being coalesced
   */
  private static void removeVarDeclaration(Node name) {
    Node var = NodeUtil.getEnclosingNode(name, NodeUtil::isNameDeclaration);
    Node parent = var.getParent();

    if (var.getFirstChild().isDestructuringLhs()) {
      // convert `const [x] = arr` to `[x] = arr`
      // a precondition for this method is that `x` is the only lvalue in the destructuring pattern
      Node destructuringLhs = var.getFirstChild();
      Node pattern = destructuringLhs.removeFirstChild();
      if (NodeUtil.isEnhancedFor(parent)) {
        var.replaceWith(pattern);
      } else {
        Node rvalue = var.getFirstFirstChild().detach();
        var.replaceWith(NodeUtil.newExpr(IR.assign(pattern, rvalue).srcref(var)));
      }
    } else if (NodeUtil.isEnhancedFor(parent)) {
      // convert `for (let x of ...` to `for (x of ...`
      var.replaceWith(name.detach());
    } else {
      // either `var x = 0;` or `var x;`
      checkState(var.hasOneChild() && var.getFirstChild() == name, var);
      if (name.hasChildren()) {
        // convert `let x = 0;` to `x = 0;`
        Node value = name.removeFirstChild();
        name.detach();
        Node assign = IR.assign(name, value).srcref(name);

        // We don't need to wrapped it with EXPR node if it is within a FOR.
        if (!parent.isVanillaFor()) {
          assign = NodeUtil.newExpr(assign);
        }
        var.replaceWith(assign);

      } else {
        // convert `let x;` to ``
        // and `for (let x;;) {}` to `for (;;) {}`
        NodeUtil.removeChild(parent, var);
      }
    }
  }

  /**
   * Convert `const` or `let` declarations to `var` declarations.
   *
   * <p>This method should be called on the first declared variable of a group that are being
   * coalesced.
   *
   * <p>Because the code has already been normalized by the time this pass runs, we can safely
   * redeclare any let and const coalesced variables as vars
   */
  private void makeDeclarationVar(Var coalescedName) {
    if (coalescedName.isConst() || coalescedName.isLet()) {
      Node nameNode = checkNotNull(coalescedName.getNameNode(), coalescedName);
      if (isUninitializedLetNameInLoopBody(nameNode)) {
        // We need to make sure that within a loop:
        //
        // `let x;`
        // becomes
        // `var x = void 0;`
        //
        // If we don't we won't be correctly resetting the variable to undefined on each loop
        // iteration once we turn it into a var declaration.
        //
        // Note that all other cases will already have an initializer.
        // const x = 1; // constant requires an initializer
        // let {x, y} = obj; // destructuring requires an initializer
        // let [x, y] = iterable; // destructuring requires an initializer
        Node undefinedValue = astFactory.createUndefinedValue().srcrefTree(nameNode);
        nameNode.addChildToFront(undefinedValue);
      }
      // find the declaration node in a way that works normal and destructuring declarations.
      Node declNode = NodeUtil.getEnclosingNode(nameNode.getParent(), NodeUtil::isNameDeclaration);
      // normalization ensures that all variables in a function are uniquely named, so it's OK
      // to turn a `const` or `let` into a `var`.
      declNode.setToken(Token.VAR);
    }
  }

  private static boolean isUninitializedLetNameInLoopBody(Node nameNode) {
    checkState(nameNode.isName(), nameNode);
    Node letNode = nameNode.getParent();
    if (!letNode.isLet()) {
      // We're looking for `let name;`
      // Note that in the case of destructuring an initializer always exists.
      // `let {name} = initializerRequiredHere;
      return false;
    }
    if (nameNode.hasOneChild()) {
      // `let name = child;` has an initializer
      return false;
    }

    Node letParent = letNode.getParent();
    if (NodeUtil.isLoopStructure(letParent)) {
      // `for (let x; ...`
      // `for (let x in ...`
      // `for (let x of ...`
      // `for await (let x of ...`
      // In all these cases the variable gets initialized on each loop iteration
      return false;
    }
    // Inside a loop body, but not the loop control node itself
    return NodeUtil.isWithinLoop(letParent);
  }

  /**
   * Used to find written and read variables in the same CFG node so that the variable pairs can be
   * marked as interfering in an interference bit map. Indices of written and read variables are put
   * in a list. These two lists are used to mark each written variable as "crossing" all read
   * variables.
   */
  private static class LiveRangeChecker {

    private final Node root;
    private final LinearFlowState<LiveVariableLattice> state;
    private final Var[] orderedVariables;
    private final List<Integer> isAssignToList = new ArrayList<>(); // indices of written variables
    private final List<Integer> isReadFromList = new ArrayList<>(); // indices of read variables

    LiveRangeChecker(
        Node root, Var[] orderedVariables, LinearFlowState<LiveVariableLattice> state) {
      this.root = root;
      this.orderedVariables = orderedVariables;
      this.state = state;
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
        if (shouldVisit(n)) {
          visit(n, n.getParent());
        }
      }
    }

    void visit(Node n, Node parent) {
      for (int iVar = 0; iVar < orderedVariables.length; iVar++) {
        if (isAssignTo(orderedVariables[iVar], n, parent)) {
          isAssignToList.add(iVar);
        }
      }
      if (!isAssignToList.isEmpty()) {
        for (int iVar = 0; iVar < orderedVariables.length; iVar++) {
          boolean varOutLive = state.getOut().isLive(iVar);
          if (varOutLive || isReadFrom(orderedVariables[iVar], n)) {
            isReadFromList.add(iVar);
          }
        }
      }
    }

    void setCrossingVariables(BitSet[] interferenceBitSet) {
      for (Integer iWrittenVar : isAssignToList) {
        for (Integer iReadVar : isReadFromList) {
          interferenceBitSet[iWrittenVar].set(iReadVar);
          interferenceBitSet[iReadVar].set(iWrittenVar);
        }
      }
    }

    /** Returns whether any LiveRangeChecker would be interested in the node. */
    public static boolean shouldVisit(Node n) {
      return (n.isName() || (n.hasChildren() && n.getFirstChild().isName()));
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
