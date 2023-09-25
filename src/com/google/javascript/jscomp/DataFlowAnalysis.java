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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.base.format.SimpleFormat;
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.LatticeElement;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * A framework to help writing static program analysis.
 *
 * <p>A subclass of this framework should specify how a single node changes the state of a program.
 * This class finds a safe estimate (a fixed-point) for the whole program. The proven facts about
 * the program will be annotated with {@link
 * com.google.javascript.jscomp.graph.GraphNode#setAnnotation} to the given control flow graph's
 * nodes in form of {@link LatticeElement} after calling {@link #analyze()}.
 *
 * <p>As a guideline, the following is a list of behaviors that any analysis can take:
 *
 * <ol>
 *   <li>Flow Direction: Is the analysis a forward or backward analysis?
 *   <li>Lattice Elements: How does the analysis represent the state of the program at any given
 *       point?
 *   <li>Branching: Does the analysis propagate a different lattice element along each branch
 *       exiting a node?
 *   <li>JOIN Operation: Given two incoming paths and a lattice state value, what can the compiler
 *       conclude at the join point?
 *   <li>Flow Equations: How does an instruction modify the state of program in terms of lattice
 *       values?
 *   <li>Initial Entry Value: What can the compiler assume at the beginning of the program?
 *   <li>Initial Estimate: What can the compiler assume at each point of the program? (What is the
 *       BOTTOM value of the lattice) By definition this lattice JOIN {@code x} for any {@code x}
 *       must also be {@code x}.
 *   <li>(Optional) Branch Operation: How should the flow branch along edges?
 * </ol>
 *
 * To make these behaviors known to the framework, the following steps must be taken.
 *
 * <ol>
 *   <li>Flow Direction: Implement {@link #isForward()}.
 *   <li>Lattice Elements: Implement {@link LatticeElement}.
 *   <li>JOIN Operation: Implement {@link JoinOp#apply}.
 *   <li>Flow Equations: Implement {@link #flowThrough(Object, LatticeElement)}.
 *   <li>Initial Entry Value: Implement {@link #createEntryLattice()}.
 *   <li>Initial Estimate: Implement {@link #createInitialEstimateLattice()}.
 *   <li>(Optional) Branch Operation: Return true from {@link #isBranched()} and implement {@link
 *       #createFlowBrancher}.
 * </ol>
 *
 * <p>Upon execution of the {@link #analyze()} method, nodes of the input control flow graph will be
 * annotated with a {@link FlowState} object that represents maximum fixed point solution. Any
 * previous annotations at the nodes of the control flow graph will be lost.
 *
 * @param <N> The control flow graph's node value type.
 * @param <L> Lattice element type.
 */
abstract class DataFlowAnalysis<N, L extends LatticeElement> {

  private final ControlFlowGraph<N> cfg;
  private final UniqueQueue<DiGraphNode<N, Branch>> workQueue;

  /**
   * The maximum number of steps per individual CFG node before we assume the analysis is divergent.
   *
   * <p>TODO(b/196398705): This is way too high. Find traversal ordering heurisitc that reduces it.
   */
  public static final int MAX_STEPS_PER_NODE = 20000;

  /**
   * Constructs a data flow analysis.
   *
   * <p>Typical usage
   *
   * <pre>
   * DataFlowAnalysis dfa = ...
   * dfa.analyze();
   * </pre>
   *
   * {@link #analyze()} annotates the result to the control flow graph by means of {@link
   * DiGraphNode#setAnnotation} without any modification of the graph itself. Additional calls to
   * {@link #analyze()} recomputes the analysis which can be useful if the control flow graph has
   * been modified.
   *
   * @param cfg The control flow graph object that this object performs on. Modification of the
   *     graph requires a separate call to {@link #analyze()}.
   * @see #analyze()
   */
  DataFlowAnalysis(ControlFlowGraph<N> cfg) {
    this.cfg = cfg;
    this.workQueue = new UniqueQueue<>(cfg.getOptionalNodeComparator(isForward()));

    if (this.isBranched()) {
      checkState(this.isForward());
    }
  }

  /**
   * Returns the control flow graph that this analysis was performed on. Modifications can be done
   * on this graph, however, the only time that the annotations are correct is after {@link
   * #analyze()} is called and before the graph has been modified.
   */
  final ControlFlowGraph<N> getCfg() {
    return cfg;
  }

  /**
   * Checks whether the analysis is a forward flow analysis or backward flow analysis.
   *
   * @return {@code true} if it is a forward analysis.
   */
  abstract boolean isForward();

  /** Whether or not {@link #createFlowBrancher} should be used. */
  boolean isBranched() {
    return false;
  }

  final L join(L latticeA, L latticeB) {
    FlowJoiner<L> joiner = this.createFlowJoiner();
    joiner.joinFlow(latticeA);
    joiner.joinFlow(latticeB);
    return joiner.finish();
  }

  /**
   * Gets a new joiner for an analysis step.
   *
   * <p>The joiner is invoked once for each input edge and then the final joined result is
   * retrieved. No joiner will be created for a single input.
   */
  abstract FlowJoiner<L> createFlowJoiner();

  /** A reducer that joins flow states from distinct input states into a single input state. */
  interface FlowJoiner<L> {
    void joinFlow(L input);

    L finish();
  }

  /**
   * Gets a new brancher for an analysis step.
   *
   * <p>The brancher is invoked for each output edge. The returned states are annotated onto each
   * edge and compared against the previous state to determine whether a fixed-point has been
   * reached.
   *
   * @param node The node at which to branch.
   * @param output The output state of {@link #flowThrough} at node.
   */
  FlowBrancher<L> createFlowBrancher(N node, L output) {
    throw new UnsupportedOperationException();
  }

  /** A callback that branches a flow state into a distinct state for each output edge. */
  @FunctionalInterface
  interface FlowBrancher<L> {
    L branchFlow(Branch branch);
  }

  /**
   * Computes the output state for a given node given its input state.
   *
   * @param node The node.
   * @param input Input lattice that should be read-only.
   * @return Output lattice.
   */
  abstract L flowThrough(N node, L input);

  /**
   * Finds a fixed-point solution. The function has the side effect of replacing the existing node
   * annotations with the computed solutions using {@link
   * com.google.javascript.jscomp.graph.GraphNode#setAnnotation(Annotation)}.
   *
   * <p>Initially, each node's input and output flow state contains the value given by {@link
   * #createInitialEstimateLattice()} (with the exception of the entry node of the graph which takes
   * on the {@link #createEntryLattice()} value. Each node will use the output state of its
   * predecessor and compute an output state according to the instruction. At that time, any nodes
   * that depend on the node's newly modified output value will need to recompute their output state
   * again. Each step will perform a computation at one node until no extra computation will modify
   * any existing output state anymore.
   */
  final void analyze() {
    initialize();
    while (!this.workQueue.isEmpty()) {
      DiGraphNode<N, Branch> curNode = this.workQueue.removeFirst();
      LinearFlowState<L> curState = curNode.getAnnotation();
      if (curState.stepCount++ > MAX_STEPS_PER_NODE) {
        throw new IllegalStateException("Dataflow analysis appears to diverge around: " + curNode);
      }

      joinInputs(curNode);
      if (flow(curNode)) {
        // If there is a change in the current node, we want to grab the list
        // of nodes that this node affects.
        List<? extends DiGraphNode<N, Branch>> nextNodes =
            isForward() ? cfg.getDirectedSuccNodes(curNode) : cfg.getDirectedPredNodes(curNode);

        for (DiGraphNode<N, Branch> nextNode : nextNodes) {
          if (nextNode != cfg.getImplicitReturn()) {
            this.workQueue.add(nextNode);
          }
        }
      }
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

  /** Initializes the work list and the control flow graph. */
  private void initialize() {
    // TODO(user): Calling clear doesn't deallocate the memory in a
    // LinkedHashSet. Consider creating a new work set if we plan to repeatedly
    // call analyze.
    workQueue.clear();
    for (DiGraphNode<N, Branch> node : cfg.getNodes()) {
      node.setAnnotation(
          new LinearFlowState<>(createInitialEstimateLattice(), createInitialEstimateLattice()));
      if (node != cfg.getImplicitReturn()) {
        workQueue.add(node);
      }
    }
    if (this.isBranched()) {
      for (DiGraphEdge<N, Branch> edge : cfg.getEdges()) {
        edge.setAnnotation(this.createInitialEstimateLattice());
      }
    }
  }

  /**
   * Performs a single flow through a node.
   *
   * @return {@code true} if the flow state differs from the previous state.
   */
  private boolean flow(DiGraphNode<N, Branch> node) {
    LinearFlowState<L> state = node.getAnnotation();
    if (isForward()) {
      L outBefore = state.getOut();
      state.setOut(flowThrough(node.getValue(), state.getIn()));
      boolean changed = !outBefore.equals(state.getOut());

      if (this.isBranched()) {
        FlowBrancher<L> brancher = this.createFlowBrancher(node.getValue(), state.getOut());
        for (DiGraphEdge<N, Branch> outEdge : node.getOutEdges()) {
          L outBranchBefore = outEdge.getAnnotation();
          outEdge.setAnnotation(brancher.branchFlow(outEdge.getValue()));
          if (!changed) {
            changed = !outBranchBefore.equals(outEdge.getAnnotation());
          }
        }
      }

      return changed;
    } else {
      L inBefore = state.getIn();
      state.setIn(flowThrough(node.getValue(), state.getOut()));
      return !inBefore.equals(state.getIn());
    }
  }

  /**
   * Computes the new flow state at a given node's entry by merging the output (input) lattice of
   * the node's predecessor (successor).
   *
   * @param node Node to compute new join.
   */
  private void joinInputs(DiGraphNode<N, Branch> node) {
    LinearFlowState<L> state = node.getAnnotation();
    if (this.isForward() && cfg.getEntry() == node) {
      state.setIn(createEntryLattice());
      return;
    }

    List<? extends DiGraphEdge<N, Branch>> inEdges =
        this.isForward() ? node.getInEdges() : node.getOutEdges();

    final L result;
    switch (inEdges.size()) {
      case 0:
        return;

      case 1:
        result = this.getInputFromEdge(inEdges.get(0));
        break;

      default:
        {
          FlowJoiner<L> joiner = this.createFlowJoiner();
          for (DiGraphEdge<N, Branch> inEdge : inEdges) {
            joiner.joinFlow(this.getInputFromEdge(inEdge));
          }
          result = joiner.finish();
        }
    }

    if (this.isForward()) {
      state.setIn(result);
    } else {
      state.setOut(result);
    }
  }

  private L getInputFromEdge(DiGraphEdge<N, Branch> edge) {
    if (this.isBranched()) {
      return edge.getAnnotation();
    } else if (this.isForward()) {
      LinearFlowState<L> state = edge.getSource().getAnnotation();
      return state.getOut();
    } else {
      DiGraphNode<N, Branch> node = edge.getDestination();
      if (node == this.cfg.getImplicitReturn()) {
        return this.createEntryLattice();
      }
      LinearFlowState<L> state = node.getAnnotation();
      return state.getIn();
    }
  }

  /** The in and out states of a node. */
  static final class LinearFlowState<L> implements Annotation {
    private int stepCount = 0;
    private L in;
    private L out;

    private LinearFlowState(L in, L out) {
      checkNotNull(in);
      checkNotNull(out);
      this.in = in;
      this.out = out;
    }

    int getStepCount() {
      return this.stepCount;
    }

    final L getIn() {
      return in;
    }

    private final void setIn(L in) {
      checkNotNull(in);
      this.in = in;
    }

    final L getOut() {
      return out;
    }

    private final void setOut(L out) {
      checkNotNull(out);
      this.out = out;
    }

    @Override
    public final String toString() {
      return SimpleFormat.format("IN: %s OUT: %s", in, out);
    }
  }

  /**
   * Compute set of escaped variables. When a variable is escaped in a dataflow analysis, it can be
   * referenced outside of the code that we are analyzing. A variable is escaped if any of the
   * following is true:
   *
   * <p>1. Exported variables as they can be needed after the script terminates. 2. Names of named
   * functions because in JavaScript, function foo(){} does not kill foo in the dataflow.
   *
   * @param jsScope Must be a function scope
   */
  static void computeEscaped(
      final Scope jsScope,
      final Set<Var> escaped,
      AbstractCompiler compiler,
      ScopeCreator scopeCreator,
      Map<String, Var> allVarsInFn) {

    // Optimize for Class Static Blocks.
    checkArgument(jsScope.isFunctionScope());

    AbstractPostOrderCallback finder =
        new AbstractPostOrderCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {

            if (!n.isName() || parent.isFunction()) {
              return;
            }

            String name = n.getString();
            Var var = t.getScope().getVar(name);

            if (var == null) {
              return;
            }

            Scope variableCfgScope = var.getScope().getClosestCfgRootScope();

            // If the current examined scope and var decleration scope are different then var is
            // 'not escaped'.
            if (variableCfgScope != jsScope) {
              return;
            }

            Scope referenceCfgScope = t.getScope().getClosestCfgRootScope();

            // Variable is referenced outside of decleration scope it is 'escaped'.
            if (referenceCfgScope != variableCfgScope) {
              escaped.add(var);
            }
          }
        };

    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(finder)
        .setScopeCreator(scopeCreator)
        .traverseAtScope(jsScope);

    // TODO (simranarora) catch variables should not be considered escaped in ES6. Getting rid of
    // the catch check is causing breakages however
    for (Var var : allVarsInFn.values()) {
      if (var.getParentNode().isCatch()
          || compiler.getCodingConvention().isExported(var.getName())) {
        escaped.add(var);
      }
    }
  }

  private static final class UniqueQueue<T> {
    private final LinkedHashSet<T> seenSet = new LinkedHashSet<>();
    private final Queue<T> queue;

    UniqueQueue(@Nullable Comparator<T> priority) {
      this.queue = (priority == null) ? new ArrayDeque<>() : new PriorityQueue<>(priority);
    }

    boolean isEmpty() {
      return this.queue.isEmpty();
    }

    T removeFirst() {
      T t = this.queue.poll();
      this.seenSet.remove(t);
      return t;
    }

    void add(T t) {
      if (this.seenSet.add(t)) {
        this.queue.add(t);
      }
    }

    void clear() {
      this.seenSet.clear();
      this.queue.clear();
    }
  }
}
