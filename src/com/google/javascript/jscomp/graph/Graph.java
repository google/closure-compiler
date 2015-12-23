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

package com.google.javascript.jscomp.graph;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * The base generic class for graph-like data structure and algorithms in
 * the compiler.
 * <p>
 * Nodes and edges in the graph can store a piece of data that this graph is
 * used to represent. For example, a variable interference graph might store a
 * variable in the node. This piece of data can be accessed with
 * {@link GraphNode#getValue} and {@link GraphEdge#getValue}.
 * <p>
 * Algorithms and analysis can annotate information on the nodes and edges
 * using {@link GraphNode#getValue} and {@link GraphEdge#getValue}. For example,
 * a graph coloring algorithm can store the color as an annotation. If multiple
 * analyses are required, it is up to the user of the analysis to save the
 * annotated solution between passes.
 * <p>
 * We implemented our own graph data structure (as opposed to using
 * <code>com.google.common.graph</code>) for two reasons. First, aside from
 * the node's label value, we would like to annotate information on the nodes
 * and edges. Using a map to annotate would introduce too much overhead during
 * fix point analysis. Also, <code>com.google.common.graph</code> does not
 * support labeling of edges. Secondly, not using another external package would
 * limit our dependencies.
 * <p>
 * TODO(user): All functionality for removing nodes and edges.
 *
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 */
public abstract class Graph<N, E> implements AdjacencyGraph<N, E> {
  /**
   * Pseudo typedef for a pair of annotations. Record of an object's
   * annotation at some state.
   */
  private static final class AnnotationState {
    private final Annotatable first;
    private final Annotation second;

    public AnnotationState(Annotatable annotatable, Annotation annotation) {
      this.first = annotatable;
      this.second = annotation;
    }
  }

  /**
   * Pseudo typedef for {@code ArrayList<AnnotationState>}. Record of a collection of
   * objects' annotations at some state.
   */
  private static class GraphAnnotationState extends ArrayList<AnnotationState> {
    private static final long serialVersionUID = 1L;

    public GraphAnnotationState(int size) {
      super(size);
    }
  }

  /**
   * Used by {@link #pushNodeAnnotations()} and {@link #popNodeAnnotations()}.
   */
  private Deque<GraphAnnotationState> nodeAnnotationStack;

  /**
   * Used by {@link #pushEdgeAnnotations()} and {@link #popEdgeAnnotations()}.
   */
  private Deque<GraphAnnotationState> edgeAnnotationStack;

  /**
   * Connects two nodes in the graph with an edge.
   *
   * @param n1 First node.
   * @param edge The edge.
   * @param n2 Second node.
   */
  public abstract void connect(N n1, E edge, N n2);

  /**
   * Disconnects two nodes in the graph by removing all edges between them.
   *
   * @param n1 First node.
   * @param n2 Second node.
   */
  public abstract void disconnect(N n1, N n2);

  /**
   * Connects two nodes in the graph with an edge if such edge does not already
   * exists between the nodes.
   *
   * @param n1 First node.
   * @param edge The edge.
   * @param n2 Second node.
   */
  public final void connectIfNotFound(N n1, E edge, N n2) {
    if (!isConnected(n1, edge, n2)) {
      connect(n1, edge, n2);
    }
  }

  /**
   * Gets a node from the graph given a value. New nodes are created if that
   * value has not been assigned a graph node. Values equality are compared
   * using <code>Object.equals</code>.
   *
   * @param value The node's value.
   * @return The corresponding node in the graph.
   */
  public abstract GraphNode<N, E> createNode(N value);

  /** Gets an immutable list of all nodes. */
  @Override
  public abstract Collection<? extends GraphNode<N, E>> getNodes();

  /** Gets an immutable list of all edges. */
  public abstract List<? extends GraphEdge<N, E>> getEdges();

  /**
   * Gets the degree of a node.
   *
   * @param value The node's value.
   * @return The degree of the node.
   */
  public abstract int getNodeDegree(N value);

  @Override
  public int getWeight(N value) {
    return getNodeDegree(value);
  }

  /**
   * Gets the neighboring nodes.
   *
   * @param value The node's value.
   * @return A list of neighboring nodes.
   */
  public abstract List<GraphNode<N, E>> getNeighborNodes(N value);

  /**
   * Retrieves an edge from the graph.
   *
   * @param n1 Node one.
   * @param n2 Node two.
   * @return The list of edges between those two values in the graph.
   */
  public abstract List<? extends GraphEdge<N, E>> getEdges(N n1, N n2);

  /**
   * Retrieves any edge from the graph.
   *
   * @param n1 Node one.
   * @param n2 Node two.
   * @return The first edges between those two values in the graph. null if
   *    there are none.
   */
  public abstract GraphEdge<N, E> getFirstEdge(N n1, N n2);

  /**
   * Checks whether the node exists in the graph ({@link #createNode(Object)}
   * has been called with that value).
   *
   * @param n Node.
   * @return <code>true</code> if it exist.
   */
  public final boolean hasNode(N n) {
    return getNode(n) != null;
  }

  /**
   * Checks whether two nodes in the graph are connected.
   *
   * @param n1 Node 1.
   * @param n2 Node 2.
   * @return <code>true</code> if the two nodes are connected.
   */
  public abstract boolean isConnected(N n1, N n2);

  /**
   * Checks whether two nodes in the graph are connected by the given
   * edge type.
   *
   * @param n1 Node 1.
   * @param e The edge type.
   * @param n2 Node 2.
   */
  public abstract boolean isConnected(N n1, E e, N n2);

  /**
   * Gets the node of the specified type, or throws an
   * IllegalArgumentException.
   */
  @SuppressWarnings("unchecked")
  <T extends GraphNode<N, E>> T getNodeOrFail(N val) {
    T node = (T) getNode(val);
    if (node == null) {
      throw new IllegalArgumentException(val + " does not exist in graph");
    }
    return node;
  }

  @Override
  public final void clearNodeAnnotations() {
    for (GraphNode<N, E> n : getNodes()) {
      n.setAnnotation(null);
    }
  }

  /** Makes each edge's annotation null. */
  public final void clearEdgeAnnotations() {
    for (GraphEdge<N, E> e : getEdges()) {
      e.setAnnotation(null);
    }
  }

  /**
   * Pushes nodes' annotation values. Restored with
   * {@link #popNodeAnnotations()}. Nodes' annotation values are cleared.
   */
  public final void pushNodeAnnotations() {
    if (nodeAnnotationStack == null) {
      nodeAnnotationStack = new LinkedList<>();
    }
    pushAnnotations(nodeAnnotationStack, getNodes());
  }

  /**
   * Restores nodes' annotation values to state before last
   * {@link #pushNodeAnnotations()}.
   */
  public final void popNodeAnnotations() {
    Preconditions.checkNotNull(nodeAnnotationStack,
        "Popping node annotations without pushing.");
    popAnnotations(nodeAnnotationStack);
  }

  /**
   * Pushes edges' annotation values. Restored with
   * {@link #popEdgeAnnotations()}. Edges' annotation values are cleared.
   */
  public final void pushEdgeAnnotations() {
    if (edgeAnnotationStack == null) {
      edgeAnnotationStack = new LinkedList<>();
    }
    pushAnnotations(edgeAnnotationStack, getEdges());
  }

  /**
   * Restores edges' annotation values to state before last
   * {@link #pushEdgeAnnotations()}.
   */
  public final void popEdgeAnnotations() {
    Preconditions.checkNotNull(edgeAnnotationStack,
        "Popping edge annotations without pushing.");
    popAnnotations(edgeAnnotationStack);
  }

  /**
   * A generic edge.
   *
   * @param <N> Value type that the graph node stores.
   * @param <E> Value type that the graph edge stores.
   */
  public interface GraphEdge<N, E> extends Annotatable {
    /**
     * Retrieves the edge's value.
     *
     * @return The value.
     */
    E getValue();

    GraphNode<N, E> getNodeA();

    GraphNode<N, E> getNodeB();
  }

  /**
   * A simple implementation of SubGraph that calculates adjacency by iterating
   * over a node's neighbors.
   */
  class SimpleSubGraph<N, E> implements SubGraph<N, E> {
    private Graph<N, E> graph;
    private List<GraphNode<N, E>> nodes = new ArrayList<>();

    SimpleSubGraph(Graph<N, E> graph) {
      this.graph = graph;
    }

    @Override
    public boolean isIndependentOf(N value) {
      GraphNode<N, E> node = graph.getNode(value);
      for (GraphNode<N, E> n : nodes) {
        if (graph.getNeighborNodes(n.getValue()).contains(node)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public void addNode(N value) {
      nodes.add(graph.getNodeOrFail(value));
    }
  }

  /**
   * Pushes a new list on stack and stores nodes annotations in the new list.
   * Clears objects' annotations as well.
   */
  private static void pushAnnotations(
      Deque<GraphAnnotationState> stack,
      Collection<? extends Annotatable> haveAnnotations) {
    stack.push(new GraphAnnotationState(haveAnnotations.size()));
    for (Annotatable h : haveAnnotations) {
      stack.peek().add(new AnnotationState(h, h.getAnnotation()));
      h.setAnnotation(null);
    }
  }

  /**
   * Restores the node annotations on the top of stack and pops stack.
   */
  private static void popAnnotations(Deque<GraphAnnotationState> stack) {
    for (AnnotationState as : stack.pop()) {
      as.first.setAnnotation(as.second);
    }
  }
}
