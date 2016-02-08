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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.LatticeElement;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A framework to help writing static program analysis. A subclass of
 * this framework should specify how a single node changes the state
 * of a program. This class finds a safe estimate (a fixed-point) for
 * the whole program. The proven facts about the program will be
 * annotated with
 * {@link com.google.javascript.jscomp.graph.GraphNode#setAnnotation} to the
 * given control flow graph's nodes in form of {@link LatticeElement}
 * after calling {@link #analyze()}.
 *
 * <p>As a guideline, the following is a list of behaviors that any analysis
 * can take:
 * <ol>
 * <li>Flow Direction: Is the analysis a forward or backward analysis?
 * <li>Lattice Elements: How does the analysis represent the state of the
 * program at any given point?
 * <li>JOIN Operation: Given two incoming paths and a lattice state value, what
 * can the compiler conclude at the join point?
 * <li>Flow Equations: How does an instruction modify the state of program in
 * terms of lattice values?
 * <li>Initial Entry Value: What can the compiler assume at the beginning of the
 * program?
 * <li>Initial Estimate: What can the compiler assume at each point of the
 * program? (What is the BOTTOM value of the lattice) By definition this lattice
 * JOIN {@code x} for any {@code x} must also be {@code x}.
 * </ol>
 * To make these behaviors known to the framework, the following steps must be
 * taken.
 * <ol>
 * <li>Flow Direction: Implement {@link #isForward()}.
 * <li>Lattice Elements: Implement {@link LatticeElement}.
 * <li>JOIN Operation: Implement
 *    {@link JoinOp#apply}.
 * <li>Flow Equations: Implement
 * {@link #flowThrough(Object, LatticeElement)}.
 * <li>Initial Entry Value: Implement {@link #createEntryLattice()}.
 * <li>Initial Estimate: Implement {@link #createInitialEstimateLattice()}.
 * </ol>
 *
 * <p>Upon execution of the {@link #analyze()} method, nodes of the input
 * control flow graph will be annotated with a {@link FlowState} object that
 * represents maximum fixed point solution. Any previous annotations at the
 * nodes of the control flow graph will be lost.
 *
 *
 * @param <N> The control flow graph's node value type.
 * @param <L> Lattice element type.
 */
abstract class DataFlowAnalysis<N, L extends LatticeElement> {

  private final ControlFlowGraph<N> cfg;
  final JoinOp<L> joinOp;
  protected final Set<DiGraphNode<N, Branch>> orderedWorkSet;

  /*
   * Feel free to increase this to a reasonable number if you are finding that
   * more and more passes need more than 400000 steps before finding a
   * fixed-point. If you just have a special case, consider calling
   * {@link #analyse(int)} instead.
   */
  public static final int MAX_STEPS = 400000;

  /**
   * Constructs a data flow analysis.
   *
   * <p>Typical usage
   * <pre>
   * DataFlowAnalysis dfa = ...
   * dfa.analyze();
   * </pre>
   *
   * {@link #analyze()} annotates the result to the control flow graph by
   * means of {@link DiGraphNode#setAnnotation} without any
   * modification of the graph itself. Additional calls to {@link #analyze()}
   * recomputes the analysis which can be useful if the control flow graph
   * has been modified.
   *
   * @param targetCfg The control flow graph object that this object performs
   *     on. Modification of the graph requires a separate call to
   *     {@link #analyze()}.
   *
   * @see #analyze()
   */
  DataFlowAnalysis(ControlFlowGraph<N> targetCfg, JoinOp<L> joinOp) {
    this.cfg = targetCfg;
    this.joinOp = joinOp;
    Comparator<DiGraphNode<N, Branch>> nodeComparator =
      cfg.getOptionalNodeComparator(isForward());
    if (nodeComparator != null) {
      this.orderedWorkSet = new TreeSet<>(nodeComparator);
    } else {
      this.orderedWorkSet = new LinkedHashSet<>();
    }
  }

  /**
   * Returns the control flow graph that this analysis was performed on.
   * Modifications can be done on this graph, however, the only time that the
   * annotations are correct is after {@link #analyze()} is called and before
   * the graph has been modified.
   */
  final ControlFlowGraph<N> getCfg() {
    return cfg;
  }

  /**
   * Returns the lattice element at the exit point.
   */
  L getExitLatticeElement() {
    DiGraphNode<N, Branch> node = getCfg().getImplicitReturn();
    FlowState<L> state = node.getAnnotation();
    return state.getIn();
  }

  @SuppressWarnings("unchecked")
  protected L join(L latticeA, L latticeB) {
    return joinOp.apply(ImmutableList.of(latticeA, latticeB));
  }

  /**
   * Checks whether the analysis is a forward flow analysis or backward flow
   * analysis.
   *
   * @return {@code true} if it is a forward analysis.
   */
  abstract boolean isForward();

  /**
   * Computes the output state for a given node and input state.
   *
   * @param node The node.
   * @param input Input lattice that should be read-only.
   * @return Output lattice.
   */
  abstract L flowThrough(N node, L input);

  /**
   * Finds a fixed-point solution using at most {@link #MAX_STEPS}
   * iterations.
   *
   * @see #analyze(int)
   */
  final void analyze() {
    analyze(MAX_STEPS);
  }

  /**
   * Finds a fixed-point solution. The function has the side effect of replacing
   * the existing node annotations with the computed solutions using {@link
   * com.google.javascript.jscomp.graph.GraphNode#setAnnotation(Annotation)}.
   *
   * <p>Initially, each node's input and output flow state contains the value
   * given by {@link #createInitialEstimateLattice()} (with the exception of the
   * entry node of the graph which takes on the {@link #createEntryLattice()}
   * value. Each node will use the output state of its predecessor and compute a
   * output state according to the instruction. At that time, any nodes that
   * depends on the node's newly modified output value will need to recompute
   * their output state again. Each step will perform a computation at one node
   * until no extra computation will modify any existing output state anymore.
   *
   * @param maxSteps Max number of iterations before the method stops and throw
   *        a {@link MaxIterationsExceededException}. This will prevent the
   *        analysis from going into a infinite loop.
   */
  final void analyze(int maxSteps) {
    initialize();
    int step = 0;
    while (!orderedWorkSet.isEmpty()) {
      if (step > maxSteps) {
        throw new MaxIterationsExceededException(
          "Analysis did not terminate after " + maxSteps + " iterations");
      }
      DiGraphNode<N, Branch> curNode = orderedWorkSet.iterator().next();
      orderedWorkSet.remove(curNode);
      joinInputs(curNode);
      if (flow(curNode)) {
        // If there is a change in the current node, we want to grab the list
        // of nodes that this node affects.
        List<DiGraphNode<N, Branch>> nextNodes = isForward() ?
            cfg.getDirectedSuccNodes(curNode) :
            cfg.getDirectedPredNodes(curNode);
        for (DiGraphNode<N, Branch> nextNode : nextNodes) {
          if (nextNode != cfg.getImplicitReturn()) {
            orderedWorkSet.add(nextNode);
          }
        }
      }
      step++;
    }
    if (isForward()) {
      joinInputs(getCfg().getImplicitReturn());
    }
  }

  /**
   * Gets the state of the initial estimation at each node.
   *
   * @return Initial state.
   */
  abstract L createInitialEstimateLattice();

  /**
   * Gets the incoming state of the entry node.
   *
   * @return Entry state.
   */
  abstract L createEntryLattice();

  /**
   * Initializes the work list and the control flow graph.
   */
  protected void initialize() {
    // TODO(user): Calling clear doesn't deallocate the memory in a
    // LinkedHashSet. Consider creating a new work set if we plan to repeatedly
    // call analyze.
    orderedWorkSet.clear();
    for (DiGraphNode<N, Branch> node : cfg.getDirectedGraphNodes()) {
      node.setAnnotation(new FlowState<>(createInitialEstimateLattice(),
          createInitialEstimateLattice()));
      if (node != cfg.getImplicitReturn()) {
        orderedWorkSet.add(node);
      }
    }
  }

  /**
   * Performs a single flow through a node.
   *
   * @return {@code true} if the flow state differs from the previous state.
   */
  protected boolean flow(DiGraphNode<N, Branch> node) {
    FlowState<L> state = node.getAnnotation();
    if (isForward()) {
      L outBefore = state.out;
      state.out = flowThrough(node.getValue(), state.in);
      return !outBefore.equals(state.out);
    } else {
      L inBefore = state.in;
      state.in = flowThrough(node.getValue(), state.out);
      return !inBefore.equals(state.in);
    }
  }

  /**
   * Computes the new flow state at a given node's entry by merging the
   * output (input) lattice of the node's predecessor (successor).
   *
   * @param node Node to compute new join.
   */
  protected void joinInputs(DiGraphNode<N, Branch> node) {
    FlowState<L> state = node.getAnnotation();
    if (isForward()) {
      if (cfg.getEntry() == node) {
        state.setIn(createEntryLattice());
      } else {
        List<DiGraphNode<N, Branch>> inNodes = cfg.getDirectedPredNodes(node);
        if (inNodes.size() == 1) {
          FlowState<L> inNodeState = inNodes.get(0).getAnnotation();
          state.setIn(inNodeState.getOut());
        } else if (inNodes.size() > 1) {
          List<L> values = new ArrayList<>(inNodes.size());
          for (DiGraphNode<N, Branch> currentNode : inNodes) {
            FlowState<L> currentNodeState = currentNode.getAnnotation();
            values.add(currentNodeState.getOut());
          }
          state.setIn(joinOp.apply(values));
        }
      }
    } else {
      List<DiGraphNode<N, Branch>> inNodes = cfg.getDirectedSuccNodes(node);
      if (inNodes.size() == 1) {
        DiGraphNode<N, Branch> inNode = inNodes.get(0);
        if (inNode == cfg.getImplicitReturn()) {
          state.setOut(createEntryLattice());
        } else {
          FlowState<L> inNodeState = inNode.getAnnotation();
          state.setOut(inNodeState.getIn());
        }
      } else if (inNodes.size() > 1) {
        List<L> values = new ArrayList<>(inNodes.size());
        for (DiGraphNode<N, Branch> currentNode : inNodes) {
          FlowState<L> currentNodeState = currentNode.getAnnotation();
          values.add(currentNodeState.getIn());
        }
        state.setOut(joinOp.apply(values));
      }
    }
  }

  /**
   * The in and out states of a node.
   *
   * @param <L> Input and output lattice element type.
   */
  static class FlowState<L extends LatticeElement> implements Annotation {
    private L in;
    private L out;

    /**
     * Private constructor. No other classes should create new states.
     *
     * @param inState Input.
     * @param outState Output.
     */
    private FlowState(L inState, L outState) {
      Preconditions.checkNotNull(inState);
      Preconditions.checkNotNull(outState);
      this.in = inState;
      this.out = outState;
    }

    L getIn() {
      return in;
    }

    void setIn(L in) {
      Preconditions.checkNotNull(in);
      this.in = in;
    }

    L getOut() {
      return out;
    }

    void setOut(L out) {
      Preconditions.checkNotNull(out);
      this.out = out;
    }

    @Override
    public String toString() {
      return SimpleFormat.format("IN: %s OUT: %s", in, out);
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof FlowState) {
        FlowState<?> that = (FlowState<?>) o;
        return that.in.equals(this.in)
            && that.out.equals(this.out);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(in, out);
    }
  }

  /**
   * The exception to be thrown if the analysis has been running for a long
   * number of iterations. Chances are the analysis is not monotonic, a
   * fixed-point cannot be found and it is currently stuck in an infinite loop.
   */
  static class MaxIterationsExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    MaxIterationsExceededException(String msg) {
      super(msg);
    }
  }

  abstract static class BranchedForwardDataFlowAnalysis
      <N, L extends LatticeElement> extends DataFlowAnalysis<N, L> {

    @Override
    protected void initialize() {
      orderedWorkSet.clear();
      for (DiGraphNode<N, Branch> node : getCfg().getDirectedGraphNodes()) {
        int outEdgeCount = getCfg().getOutEdges(node.getValue()).size();
        List<L> outLattices = new ArrayList<>();
        for (int i = 0; i < outEdgeCount; i++) {
          outLattices.add(createInitialEstimateLattice());
        }
        node.setAnnotation(new BranchedFlowState<>(
            createInitialEstimateLattice(), outLattices));
        if (node != getCfg().getImplicitReturn()) {
          orderedWorkSet.add(node);
        }
      }
    }

    BranchedForwardDataFlowAnalysis(ControlFlowGraph<N> targetCfg,
                                    JoinOp<L> joinOp) {
      super(targetCfg, joinOp);
    }

    /**
     * Returns the lattice element at the exit point. Needs to be overridden
     * because we use a BranchedFlowState instead of a FlowState; ugh.
     */
    @Override
    L getExitLatticeElement() {
      DiGraphNode<N, Branch> node = getCfg().getImplicitReturn();
      BranchedFlowState<L> state = node.getAnnotation();
      return state.getIn();
    }

    @Override
    final boolean isForward() {
      return true;
    }

    /**
     * The branched flow function maps a single lattice to a list of output
     * lattices.
     *
     * <p>Each outgoing edge of a node will have a corresponding output lattice
     * in the ordered returned by
     * {@link com.google.javascript.jscomp.graph.DiGraph#getOutEdges(Object)}
     * in the returned list.
     *
     * @return A list of output values depending on the edge's branch type.
     */
    abstract List<L> branchedFlowThrough(N node, L input);

    @Override
    protected final boolean flow(DiGraphNode<N, Branch> node) {
      BranchedFlowState<L> state = node.getAnnotation();
      List<L> outBefore = state.out;
      state.out = branchedFlowThrough(node.getValue(), state.in);
      Preconditions.checkState(outBefore.size() == state.out.size());
      for (int i = 0; i < outBefore.size(); i++) {
        if (!outBefore.get(i).equals(state.out.get(i))) {
          return true;
        }
      }
      return false;
    }

    @Override
    protected void joinInputs(DiGraphNode<N, Branch> node) {
      BranchedFlowState<L> state = node.getAnnotation();
      List<DiGraphNode<N, Branch>> predNodes =
          getCfg().getDirectedPredNodes(node);
      List<L> values = new ArrayList<>(predNodes.size());

      for (DiGraphNode<N, Branch> predNode : predNodes) {
        BranchedFlowState<L> predNodeState = predNode.getAnnotation();

        L in = predNodeState.out.get(
            getCfg().getDirectedSuccNodes(predNode).indexOf(node));

        values.add(in);
      }
      if (getCfg().getEntry() == node) {
        state.setIn(createEntryLattice());
      } else if (!values.isEmpty()) {
        state.setIn(joinOp.apply(values));
      }
    }
  }

  /**
   * The in and out states of a node.
   *
   * @param <L> Input and output lattice element type.
   */
  static class BranchedFlowState<L extends LatticeElement>
      implements Annotation {
    private L in;
    private List<L> out;

    /**
     * Private constructor. No other classes should create new states.
     *
     * @param inState Input.
     * @param outState Output.
     */
    private BranchedFlowState(L inState, List<L> outState) {
      Preconditions.checkNotNull(inState);
      Preconditions.checkNotNull(outState);
      this.in = inState;
      this.out = outState;
    }

    L getIn() {
      return in;
    }

    void setIn(L in) {
      Preconditions.checkNotNull(in);
      this.in = in;
    }

    @Override
    public String toString() {
      return SimpleFormat.format("IN: %s OUT: %s", in, out);
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof BranchedFlowState) {
        BranchedFlowState<?> that = (BranchedFlowState<?>) o;
        return that.in.equals(this.in)
            && that.out.equals(this.out);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(in, out);
    }
  }

  /**
   * Compute set of escaped variables. When a variable is escaped in a
   * dataflow analysis, it can be reference outside of the code that we are
   * analyzing. A variable is escaped if any of the following is true:
   *
   * <p><ol>
   * <li>It is defined as the exception name in CATCH clause so it became a
   * variable local not to our definition of scope.</li>
   * <li>Exported variables as they can be needed after the script terminates.
   * </li>
   * <li>Names of named functions because in JavaScript, <i>function foo(){}</i>
   * does not kill <i>foo</i> in the dataflow.</li>
   */
  static void computeEscaped(final Scope jsScope, final Set<Var> escaped,
      AbstractCompiler compiler) {
    // TODO(user): Very good place to store this information somewhere.
    AbstractPostOrderCallback finder = new AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (jsScope == t.getScope() || !n.isName()
            || parent.isFunction()) {
          return;
        }
        String name = n.getString();
        Var var = t.getScope().getVar(name);
        if (var != null && var.scope == jsScope) {
          escaped.add(jsScope.getVar(name));
        }
      }
    };

    NodeTraversal t = new NodeTraversal(compiler, finder);
    t.traverseAtScope(jsScope);

    // 1: Remove the exception name in CATCH which technically isn't local to
    //    begin with.
    for (Iterator<Var> i = jsScope.getVars(); i.hasNext();) {
      Var var = i.next();
      if (var.getParentNode().isCatch() ||
          compiler.getCodingConvention().isExported(var.getName())) {
        escaped.add(var);
      }
    }
  }
}
