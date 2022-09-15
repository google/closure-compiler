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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.GraphvizGraph.GraphvizEdge;
import com.google.javascript.jscomp.graph.GraphvizGraph.GraphvizNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.nullness.Nullable;

/**
 * A directed graph using ArrayLists within nodes to store edge information.
 *
 * <p>This implementation favors directed graph operations inherited from <code>
 * DirectedGraph</code>. Operations from <code>Graph</code> would tend to be slower.
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 */
public class LinkedDirectedGraph<N, E> extends DiGraph<N, E> implements GraphvizGraph {
  protected final Map<N, LinkedDiGraphNode<N, E>> nodes = new LinkedHashMap<>();

  @Override
  public SubGraph<N, E> newSubGraph() {
    return new SimpleSubGraph<>(this);
  }

  public static <N, E> LinkedDirectedGraph<N, E> createWithoutAnnotations() {
    return new LinkedDirectedGraph<>(false, false);
  }

  public static <N, E> LinkedDirectedGraph<N, E> create() {
    return new LinkedDirectedGraph<>(true, true);
  }

  private final boolean useNodeAnnotations;
  private final boolean useEdgeAnnotations;

  protected LinkedDirectedGraph(
      boolean useNodeAnnotations, boolean useEdgeAnnotations) {
    this.useNodeAnnotations = useNodeAnnotations;
    this.useEdgeAnnotations = useEdgeAnnotations;
  }

  @Override
  public void connect(N srcValue, E edgeValue, N destValue) {
    LinkedDiGraphNode<N, E> src = getNodeOrFail(srcValue);
    LinkedDiGraphNode<N, E> dest = getNodeOrFail(destValue);
    LinkedDiGraphEdge<N, E> edge =
        useEdgeAnnotations
            ? new AnnotatedLinkedDiGraphEdge<>(src, edgeValue, dest)
            : new LinkedDiGraphEdge<>(src, edgeValue, dest);
    src.getOutEdges().add(edge);
    dest.getInEdges().add(edge);
  }

  // TODO(johnlenz): make this part of the general graph interface.
  /**
   * DiGraphNode look ups can be expensive for a large graph operation, prefer this method if you
   * have the DiGraphNode available.
   */
  public void connect(DiGraphNode<N, E> src, E edgeValue, DiGraphNode<N, E> dest) {
    LinkedDiGraphNode<N, E> lSrc = (LinkedDiGraphNode<N, E>) src;
    LinkedDiGraphNode<N, E> lDest = (LinkedDiGraphNode<N, E>) dest;

    LinkedDiGraphEdge<N, E> edge =
        useEdgeAnnotations
            ? new AnnotatedLinkedDiGraphEdge<>(lSrc, edgeValue, lDest)
            : new LinkedDiGraphEdge<>(lSrc, edgeValue, lDest);
    lSrc.getOutEdges().add(edge);
    lDest.getInEdges().add(edge);
  }

  // TODO(johnlenz): make this part of the general graph interface.
  /**
   * DiGraphNode look ups can be expensive for a large graph operation, prefer this
   * method if you have the DiGraphNode available.
   */
  public void connectIfNotConnectedInDirection(N srcValue, E edgeValue, N destValue) {
    LinkedDiGraphNode<N, E> src = createNode(srcValue);
    LinkedDiGraphNode<N, E> dest = createNode(destValue);
    if (!this.isConnectedInDirection(src, Predicates.equalTo(edgeValue), dest)) {
      this.connect(src, edgeValue, dest);
    }
  }

  @Override
  public void disconnect(N n1, N n2) {
    disconnectInDirection(n1, n2);
    disconnectInDirection(n2, n1);
  }

  @Override
  public void disconnectInDirection(N srcValue, N destValue) {
    LinkedDiGraphNode<N, E> src = getNodeOrFail(srcValue);
    LinkedDiGraphNode<N, E> dest = getNodeOrFail(destValue);
    for (DiGraphEdge<?, E> edge : getEdgesInDirection(srcValue, destValue)) {
      src.getOutEdges().remove(edge);
      dest.getInEdges().remove(edge);
    }
  }

  @Override
  public Collection<LinkedDiGraphNode<N, E>> getNodes() {
    return Collections.unmodifiableCollection(nodes.values());
  }

  @Override
  public LinkedDiGraphNode<N, E> getNode(N nodeValue) {
    return nodes.get(nodeValue);
  }

  @Override
  public List<LinkedDiGraphEdge<N, E>> getInEdges(N nodeValue) {
    LinkedDiGraphNode<N, E> node = getNodeOrFail(nodeValue);
    return Collections.unmodifiableList(node.getInEdges());
  }

  @Override
  public List<LinkedDiGraphEdge<N, E>> getOutEdges(N nodeValue) {
    LinkedDiGraphNode<N, E> node = getNodeOrFail(nodeValue);
    return Collections.unmodifiableList(node.getOutEdges());
  }

  @Override
  public LinkedDiGraphNode<N, E> createNode(N nodeValue) {
    return nodes.computeIfAbsent(
        nodeValue,
        (N k) ->
            useNodeAnnotations
                ? new AnnotatedLinkedDiGraphNode<N, E>(k)
                : new LinkedDiGraphNode<N, E>(k));
  }

  @Override
  public List<LinkedDiGraphEdge<N, E>> getEdges(N n1, N n2) {
    // Since this is a method from a generic graph, edges from both
    // directions must be added to the returning list.
    List<LinkedDiGraphEdge<N, E>> forwardEdges = getEdgesInDirection(n1, n2);
    List<LinkedDiGraphEdge<N, E>> backwardEdges = getEdgesInDirection(n2, n1);
    int totalSize = forwardEdges.size() + backwardEdges.size();
    List<LinkedDiGraphEdge<N, E>> edges = new ArrayList<>(totalSize);
    edges.addAll(forwardEdges);
    edges.addAll(backwardEdges);
    return edges;
  }

  @Override
  public List<LinkedDiGraphEdge<N, E>> getEdges() {
    List<LinkedDiGraphEdge<N, E>> result = new ArrayList<>();
    for (LinkedDiGraphNode<N, E> node : nodes.values()) {
      result.addAll(node.getOutEdges());
    }
    return Collections.unmodifiableList(result);
  }

  @Override
  public @Nullable GraphEdge<N, E> getFirstEdge(N n1, N n2) {
    LinkedDiGraphNode<N, E> dNode1 = getNodeOrFail(n1);
    LinkedDiGraphNode<N, E> dNode2 = getNodeOrFail(n2);
    for (DiGraphEdge<N, E> outEdge : dNode1.getOutEdges()) {
      if (outEdge.getDestination() == dNode2) {
        return outEdge;
      }
    }
    for (DiGraphEdge<N, E> outEdge : dNode2.getOutEdges()) {
      if (outEdge.getDestination() == dNode1) {
        return outEdge;
      }
    }
    return null;
  }

  @Override
  public List<LinkedDiGraphEdge<N, E>> getEdgesInDirection(N n1, N n2) {
    LinkedDiGraphNode<N, E> dNode1 = getNodeOrFail(n1);
    LinkedDiGraphNode<N, E> dNode2 = getNodeOrFail(n2);
    List<LinkedDiGraphEdge<N, E>> edges = new ArrayList<>();
    for (LinkedDiGraphEdge<N, E> outEdge : dNode1.getOutEdges()) {
      if (outEdge.getDestination() == dNode2) {
        edges.add(outEdge);
      }
    }
    return edges;
  }

  /**
   * DiGraphNode look ups can be expensive for a large graph operation, prefer the version below
   * that takes DiGraphNodes, if you have them available.
   *
   * @param source the source node from which we traverse outwards
   */
  @Override
  public boolean isConnectedInDirection(N source, N dest) {
    return isConnectedInDirection(source, Predicates.<E>alwaysTrue(), dest);
  }

  /**
   * DiGraphNode look ups can be expensive for a large graph operation, prefer the version below
   * that takes DiGraphNodes, if you have them available.
   *
   * @param source the source node from which we traverse outwards
   * @param edgeValue only edges equal to the given value will be traversed
   */
  @Override
  public boolean isConnectedInDirection(N source, E edgeValue, N dest) {
    return isConnectedInDirection(source, Predicates.equalTo(edgeValue), dest);
  }

  /**
   * DiGraphNode look ups can be expensive for a large graph operation, prefer this method if you
   * have the DiGraphNodes available.
   *
   * @param source the source node from which we traverse outwards
   * @param edgeFilter only edges matching this filter will be traversed
   * @param dest the destination node
   */
  public boolean isConnectedInDirection(
      LinkedDiGraphNode<N, E> source, Predicate<E> edgeFilter, LinkedDiGraphNode<N, E> dest) {
    List<LinkedDiGraphEdge<N, E>> outEdges = source.getOutEdges();
    int outEdgesLen = outEdges.size();
    List<LinkedDiGraphEdge<N, E>> inEdges = dest.getInEdges();
    int inEdgesLen = inEdges.size();
    // It is possible that there is a large asymmetry between the nodes, so pick the direction
    // to search based on the shorter list since the edge lists should be symmetric.
    if (outEdgesLen < inEdgesLen) {
      for (DiGraphEdge<N, E> outEdge : outEdges) {
        if (outEdge.getDestination() == dest && edgeFilter.apply(outEdge.getValue())) {
          return true;
        }
      }
    } else {
      for (DiGraphEdge<N, E> inEdge : inEdges) {
        if (inEdge.getSource() == source && edgeFilter.apply(inEdge.getValue())) {
          return true;
        }
      }
    }

    return false;
  }

  private boolean isConnectedInDirection(N n1, Predicate<E> edgeMatcher, N n2) {
    // Verify the nodes.
    LinkedDiGraphNode<N, E> dNode1 = getNodeOrFail(n1);
    LinkedDiGraphNode<N, E> dNode2 = getNodeOrFail(n2);
    return isConnectedInDirection(dNode1, edgeMatcher, dNode2);
  }

  @Override
  public List<LinkedDiGraphNode<N, E>> getDirectedPredNodes(N nodeValue) {
    return getDirectedPredNodes(nodes.get(nodeValue));
  }

  @Override
  public List<LinkedDiGraphNode<N, E>> getDirectedPredNodes(DiGraphNode<N, E> dNode) {
    checkNotNull(dNode);
    LinkedDiGraphNode<N, E> lNode = (LinkedDiGraphNode<N, E>) dNode;

    List<LinkedDiGraphNode<N, E>> nodeList = new ArrayList<>(lNode.getInEdges().size());
    for (LinkedDiGraphEdge<N, E> edge : lNode.getInEdges()) {
      nodeList.add(edge.getSource());
    }
    return nodeList;
  }

  @Override
  public List<LinkedDiGraphNode<N, E>> getDirectedSuccNodes(N nodeValue) {
    return getDirectedSuccNodes(nodes.get(nodeValue));
  }

  @Override
  public List<LinkedDiGraphNode<N, E>> getDirectedSuccNodes(DiGraphNode<N, E> dNode) {
    checkNotNull(dNode);
    LinkedDiGraphNode<N, E> lNode = (LinkedDiGraphNode<N, E>) dNode;

    List<LinkedDiGraphNode<N, E>> nodeList = new ArrayList<>(lNode.getOutEdges().size());
    for (LinkedDiGraphEdge<N, E> edge : lNode.getOutEdges()) {
      nodeList.add(edge.getDestination());
    }
    return nodeList;
  }

  @Override
  public List<GraphvizEdge> getGraphvizEdges() {
    List<GraphvizEdge> edgeList = new ArrayList<>();
    for (LinkedDiGraphNode<N, E> node : nodes.values()) {
      for (DiGraphEdge<N, E> edge : node.getOutEdges()) {
        edgeList.add((LinkedDiGraphEdge<N, E>) edge);
      }
    }
    return edgeList;
  }

  @Override
  public List<GraphvizNode> getGraphvizNodes() {
    List<GraphvizNode> nodeList = new ArrayList<>(nodes.size());
    for (LinkedDiGraphNode<N, E> node : nodes.values()) {
      nodeList.add(node);
    }
    return nodeList;
  }

  @Override
  public String getName() {
    return "LinkedGraph";
  }

  @Override
  public boolean isDirected() {
    return true;
  }

  @Override
  public int getNodeCount() {
    return nodes.size();
  }

  @Override
  public List<GraphNode<N, E>> getNeighborNodes(N value) {
    LinkedDiGraphNode<N, E> node = getNode(value);
    List<GraphNode<N, E>> result = new ArrayList<>(
        node.getInEdges().size() + node.getOutEdges().size());
    for (DiGraphEdge<N, E> inEdge : node.getInEdges()) {
      result.add(inEdge.getSource());
    }
    for (DiGraphEdge<N, E> outEdge : node.getOutEdges()) {
      result.add(outEdge.getDestination());
    }
    return result;
  }

  @Override
  public int getNodeDegree(N value) {
    LinkedDiGraphNode<N, E> node = getNodeOrFail(value);
    return node.getInEdges().size() + node.getOutEdges().size();
  }

  /**
   * A directed graph node that stores outgoing edges and incoming edges as an list within the node
   * itself.
   */
  public static class LinkedDiGraphNode<N, E> implements DiGraphNode<N, E>, GraphvizNode {

    // The overwhelming majority of nodes have in/out degree == 1. Initialize our lists to account
    // for that.
    private final List<LinkedDiGraphEdge<N, E>> inEdgeList =
        new ArrayList<>(/* initialCapacity= */ 1);

    private final List<LinkedDiGraphEdge<N, E>> outEdgeList =
        new ArrayList<>(/* initialCapacity= */ 1);

    protected final N value;

    private int priority = -1;

    /**
     * Constructor
     *
     * @param nodeValue Node's value.
     */
    private LinkedDiGraphNode(N nodeValue) {
      this.value = nodeValue;
    }

    @Override
    public N getValue() {
      return value;
    }

    @Override
    public <A extends Annotation> A getAnnotation() {
      throw new UnsupportedOperationException(
          "Graph initialized with node annotations turned off");
    }

    @Override
    public void setAnnotation(Annotation data) {
      throw new UnsupportedOperationException(
          "Graph initialized with node annotations turned off");
    }

    @Override
    public String getColor() {
      return "white";
    }

    @Override
    public String getId() {
      return "LDN" + hashCode();
    }

    @Override
    public String getLabel() {
      return this.toString();
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @Override
    public List<LinkedDiGraphEdge<N, E>> getInEdges() {
      return inEdgeList;
    }

    @Override
    public List<LinkedDiGraphEdge<N, E>> getOutEdges() {
      return outEdgeList;
    }

    @Override
    public boolean hasPriority() {
      return this.priority >= 0;
    }

    @Override
    public int getPriority() {
      checkState(this.priority >= 0, "priority not set");
      return this.priority;
    }

    @Override
    public void setPriority(int priority) {
      checkArgument(priority >= 0, "priorities must be non-negative");
      this.priority = priority;
    }
  }

  /** A directed graph node with annotations. */
  static final class AnnotatedLinkedDiGraphNode<N, E> extends LinkedDiGraphNode<N, E> {

    Annotation annotation;

    /** @param nodeValue Node's value. */
    private AnnotatedLinkedDiGraphNode(N nodeValue) {
      super(nodeValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends Annotation> A getAnnotation() {
      return (A) annotation;
    }

    @Override
    public void setAnnotation(Annotation data) {
      annotation = data;
    }

    @Override
    public String getLabel() {
      String result = this.toString();
      if (this.annotation != null) {
        result += "\n" + this.annotation;
      }
      return result;
    }
  }

  /** A directed graph edge that stores the source and destination nodes at each edge. */
  public static class LinkedDiGraphEdge<N, E> implements DiGraphEdge<N, E>, GraphvizEdge {

    private final LinkedDiGraphNode<N, E> sourceNode;

    private final LinkedDiGraphNode<N, E> destNode;

    protected final E value;

    /**
     * Constructor.
     *
     * @param edgeValue Edge Value.
     */
    private LinkedDiGraphEdge(
        LinkedDiGraphNode<N, E> sourceNode, E edgeValue, LinkedDiGraphNode<N, E> destNode) {
      this.value = edgeValue;
      this.sourceNode = sourceNode;
      this.destNode = destNode;
    }

    @Override
    public LinkedDiGraphNode<N, E> getSource() {
      return sourceNode;
    }

    @Override
    public LinkedDiGraphNode<N, E> getDestination() {
      return destNode;
    }

    @Override
    public E getValue() {
      return value;
    }

    @Override
    public <A extends Annotation> A getAnnotation() {
      throw new UnsupportedOperationException(
          "Graph initialized with edge annotations turned off");
    }

    @Override
    public void setAnnotation(Annotation data) {
      throw new UnsupportedOperationException(
          "Graph initialized with edge annotations turned off");
    }

    @Override
    public String getColor() {
      return "black";
    }

    @Override
    public String getLabel() {
      return String.valueOf(value);
    }

    @Override
    public String getNode1Id() {
      return sourceNode.getId();
    }

    @Override
    public String getNode2Id() {
      return destNode.getId();
    }

    @Override
    public String toString() {
      return sourceNode + " -> " + destNode;
    }

    @Override
    public GraphNode<N, E> getNodeA() {
      return sourceNode;
    }

    @Override
    public GraphNode<N, E> getNodeB() {
      return destNode;
    }
  }

  /** A directed graph edge that stores the source and destination nodes at each edge. */
  static final class AnnotatedLinkedDiGraphEdge<N, E> extends LinkedDiGraphEdge<N, E> {

    Annotation annotation;

    /**
     * Constructor.
     *
     * @param edgeValue Edge Value.
     */
    private AnnotatedLinkedDiGraphEdge(
        LinkedDiGraphNode<N, E> sourceNode, E edgeValue, LinkedDiGraphNode<N, E> destNode) {
      super(sourceNode, edgeValue, destNode);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends Annotation> A getAnnotation() {
      return (A) annotation;
    }

    @Override
    public void setAnnotation(Annotation data) {
      annotation = data;
    }
  }
}
