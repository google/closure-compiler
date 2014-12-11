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
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A directed graph using linked list within nodes to store edge information.
 * <p>
 * This implementation favors directed graph operations inherited from <code>
 * DirectedGraph</code>.
 * Operations from <code>Graph</code> would tends to be slower.
 *
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 */
public class LinkedDirectedGraph<N, E>
    extends DiGraph<N, E> implements GraphvizGraph {
  protected final Map<N, LinkedDirectedGraphNode<N, E>> nodes = new HashMap<>();

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
    LinkedDirectedGraphNode<N, E> src = getNodeOrFail(srcValue);
    LinkedDirectedGraphNode<N, E> dest = getNodeOrFail(destValue);
    LinkedDirectedGraphEdge<N, E> edge =
        useEdgeAnnotations
        ? new AnnotatedLinkedDirectedGraphEdge<>(src, edgeValue, dest)
        : new LinkedDirectedGraphEdge<>(src, edgeValue, dest);
    src.getOutEdges().add(edge);
    dest.getInEdges().add(edge);
  }

  // TODO(johnlenz): make this part of the general graph interface.
  /**
   * DiGraphNode look ups can be expensive for a large graph operation, prefer this
   * method if you have the DiGraphNode available.
   */
  public void connect(
      DiGraphNode<N, E> src,
      E edgeValue,
      DiGraphNode<N, E> dest) {
    LinkedDirectedGraphEdge<N, E> edge =
        useEdgeAnnotations
        ? new AnnotatedLinkedDirectedGraphEdge<>(src, edgeValue, dest)
        : new LinkedDirectedGraphEdge<>(src, edgeValue, dest);
    src.getOutEdges().add(edge);
    dest.getInEdges().add(edge);
  }

  // TODO(johnlenz): make this part of the general graph interface.
  /**
   * DiGraphNode look ups can be expensive for a large graph operation, prefer this
   * method if you have the DiGraphNode available.
   */
  public void connectIfNotConnectedInDirection(N srcValue, E edgeValue, N destValue) {
    LinkedDirectedGraphNode<N, E> src = createDirectedGraphNode(srcValue);
    LinkedDirectedGraphNode<N, E> dest = createDirectedGraphNode(destValue);
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
    LinkedDirectedGraphNode<N, E> src = getNodeOrFail(srcValue);
    LinkedDirectedGraphNode<N, E> dest = getNodeOrFail(destValue);
    for (DiGraphEdge<?, E> edge : getDirectedGraphEdges(srcValue, destValue)) {
      src.getOutEdges().remove(edge);
      dest.getInEdges().remove(edge);
    }
  }

  @Override
  public Iterable<DiGraphNode<N, E>> getDirectedGraphNodes() {
    return Collections.<DiGraphNode<N, E>>unmodifiableCollection(
        nodes.values());
  }

  @Override
  public DiGraphNode<N, E> getDirectedGraphNode(N nodeValue) {
    return nodes.get(nodeValue);
  }

  @Override
  public GraphNode<N, E> getNode(N nodeValue) {
    return getDirectedGraphNode(nodeValue);
  }

  @Override
  public List<DiGraphEdge<N, E>> getInEdges(N nodeValue) {
    LinkedDirectedGraphNode<N, E> node = getNodeOrFail(nodeValue);
    return Collections.unmodifiableList(node.getInEdges());
  }

  @Override
  public List<DiGraphEdge<N, E>> getOutEdges(N nodeValue) {
    LinkedDirectedGraphNode<N, E> node = getNodeOrFail(nodeValue);
    return Collections.unmodifiableList(node.getOutEdges());
  }

  @Override
  public LinkedDirectedGraphNode<N, E> createDirectedGraphNode(N nodeValue) {
    LinkedDirectedGraphNode<N, E> node = nodes.get(nodeValue);
    if (node == null) {
      node = useNodeAnnotations
          ? new AnnotatedLinkedDirectedGraphNode<N, E>(nodeValue)
          : new LinkedDirectedGraphNode<N, E>(nodeValue);
      nodes.put(nodeValue, node);
    }
    return node;
  }

  @Override
  public List<DiGraphEdge<N, E>> getEdges(N n1, N n2) {
    // Since this is a method from a generic graph, edges from both
    // directions must be added to the returning list.
    List<DiGraphEdge<N, E>> forwardEdges = getDirectedGraphEdges(n1, n2);
    List<DiGraphEdge<N, E>> backwardEdges = getDirectedGraphEdges(n2, n1);
    int totalSize = forwardEdges.size() + backwardEdges.size();
    List<DiGraphEdge<N, E>> edges = new ArrayList<>(totalSize);
    edges.addAll(forwardEdges);
    edges.addAll(backwardEdges);
    return edges;
  }

  @Override
  public GraphEdge<N, E> getFirstEdge(N n1, N n2) {
    DiGraphNode<N, E> dNode1 = getNodeOrFail(n1);
    DiGraphNode<N, E> dNode2 = getNodeOrFail(n2);
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
  public GraphNode<N, E> createNode(N value) {
    return createDirectedGraphNode(value);
  }

  @Override
  public List<DiGraphEdge<N, E>> getDirectedGraphEdges(N n1, N n2) {
    DiGraphNode<N, E> dNode1 = getNodeOrFail(n1);
    DiGraphNode<N, E> dNode2 = getNodeOrFail(n2);
    List<DiGraphEdge<N, E>> edges = new ArrayList<>();
    for (DiGraphEdge<N, E> outEdge : dNode1.getOutEdges()) {
      if (outEdge.getDestination() == dNode2) {
        edges.add(outEdge);
      }
    }
    return edges;
  }

  /**
   * DiGraphNode look ups can be expensive for a large graph operation, prefer the
   * version below that takes DiGraphNodes, if you have them available.
   */
  @Override
  public boolean isConnectedInDirection(N n1, N n2) {
    return isConnectedInDirection(n1, Predicates.<E>alwaysTrue(), n2);
  }

  /**
   * DiGraphNode look ups can be expensive for a large graph operation, prefer the
   * version below that takes DiGraphNodes, if you have them available.
   */
  @Override
  public boolean isConnectedInDirection(N n1, E edgeValue, N n2) {
    return isConnectedInDirection(n1, Predicates.equalTo(edgeValue), n2);
  }

  /**
   * DiGraphNode look ups can be expensive for a large graph operation, prefer this
   * method if you have the DiGraphNodes available.
   */
  public boolean isConnectedInDirection(
      DiGraphNode<N, E> dNode1,
      Predicate<E> edgeMatcher,
      DiGraphNode<N, E>  dNode2) {
    // Verify the nodes.
    List<DiGraphEdge<N, E>> outEdges = dNode1.getOutEdges();
    int len = outEdges.size();
    for (int i = 0; i < len; i++) {
      DiGraphEdge<N, E> outEdge = outEdges.get(i);
      if (outEdge.getDestination() == dNode2
          && edgeMatcher.apply(outEdge.getValue())) {
        return true;
      }
    }

    return false;
  }

  private boolean isConnectedInDirection(N n1, Predicate<E> edgeMatcher, N n2) {
    // Verify the nodes.
    DiGraphNode<N, E> dNode1 = getNodeOrFail(n1);
    DiGraphNode<N, E> dNode2 = getNodeOrFail(n2);
    for (DiGraphEdge<N, E> outEdge : dNode1.getOutEdges()) {
      if (outEdge.getDestination() == dNode2
          && edgeMatcher.apply(outEdge.getValue())) {
        return true;
      }
    }

    return false;
  }

  @Override
  public List<DiGraphNode<N, E>> getDirectedPredNodes(N nodeValue) {
    return getDirectedPredNodes(nodes.get(nodeValue));
  }

  @Override
  public List<DiGraphNode<N, E>> getDirectedSuccNodes(N nodeValue) {
    return getDirectedSuccNodes(nodes.get(nodeValue));
  }

  @Override
  public List<DiGraphNode<N, E>> getDirectedPredNodes(
      DiGraphNode<N, E> dNode) {
    Preconditions.checkNotNull(dNode);
    List<DiGraphNode<N, E>> nodeList =
        new ArrayList<>(dNode.getInEdges().size());
    for (DiGraphEdge<N, E> edge : dNode.getInEdges()) {
      nodeList.add(edge.getSource());
    }
    return nodeList;
  }

  @Override
  public List<DiGraphNode<N, E>> getDirectedSuccNodes(
      DiGraphNode<N, E> dNode) {
    Preconditions.checkNotNull(dNode);
    List<DiGraphNode<N, E>> nodeList =
        new ArrayList<>(dNode.getOutEdges().size());
    for (DiGraphEdge<N, E> edge : dNode.getOutEdges()) {
      nodeList.add(edge.getDestination());
    }
    return nodeList;
  }

  @Override
  public List<GraphvizEdge> getGraphvizEdges() {
    List<GraphvizEdge> edgeList = new ArrayList<>();
    for (LinkedDirectedGraphNode<N, E> node : nodes.values()) {
      for (DiGraphEdge<N, E> edge : node.getOutEdges()) {
        edgeList.add((LinkedDirectedGraphEdge<N, E>) edge);
      }
    }
    return edgeList;
  }

  @Override
  public List<GraphvizNode> getGraphvizNodes() {
    List<GraphvizNode> nodeList = new ArrayList<>(nodes.size());
    for (LinkedDirectedGraphNode<N, E> node : nodes.values()) {
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
  public Collection<DiGraphNode<N, E>> getNodes() {
    return Collections.<DiGraphNode<N, E>>unmodifiableCollection(nodes.values());
  }

  @Override
  public List<GraphNode<N, E>> getNeighborNodes(N value) {
    DiGraphNode<N, E> node = getDirectedGraphNode(value);
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
  public List<DiGraphEdge<N, E>> getEdges() {
    List<DiGraphEdge<N, E>> result = new ArrayList<>();
    for (DiGraphNode<N, E> node : nodes.values()) {
      result.addAll(node.getOutEdges());
    }
    return Collections.unmodifiableList(result);
  }

  @Override
  public int getNodeDegree(N value) {
    DiGraphNode<N, E> node = getNodeOrFail(value);
    return node.getInEdges().size() + node.getOutEdges().size();
  }

  /**
   * A directed graph node that stores outgoing edges and incoming edges as an
   * list within the node itself.
   */
  public static class LinkedDirectedGraphNode<N, E> implements DiGraphNode<N, E>,
      GraphvizNode {

    List<DiGraphEdge<N, E>> inEdgeList = new ArrayList<>();
    List<DiGraphEdge<N, E>> outEdgeList = new ArrayList<>();

    protected final N value;

    /**
     * Constructor
     *
     * @param nodeValue Node's value.
     */
    LinkedDirectedGraphNode(N nodeValue) {
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
      return String.valueOf(value);
    }

    @Override
    public String toString() {
      return getLabel();
    }

    @Override
    public List<DiGraphEdge<N, E>> getInEdges() {
      return inEdgeList;
    }

    @Override
    public List<DiGraphEdge<N, E>> getOutEdges() {
      return outEdgeList;
    }
  }

  /**
   * A directed graph node with annotations.
   */
  static class AnnotatedLinkedDirectedGraphNode<N, E>
      extends LinkedDirectedGraphNode<N, E> {

    protected Annotation annotation;

    /**
     * @param nodeValue Node's value.
     */
    AnnotatedLinkedDirectedGraphNode(N nodeValue) {
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
  }

  /**
   * A directed graph edge that stores the source and destination nodes at each
   * edge.
   */
  static class LinkedDirectedGraphEdge<N, E> implements DiGraphEdge<N, E>,
      GraphvizEdge {

    private DiGraphNode<N, E> sourceNode;

    private DiGraphNode<N, E> destNode;

    protected final E value;

    /**
     * Constructor.
     *
     * @param edgeValue Edge Value.
     */
    LinkedDirectedGraphEdge(DiGraphNode<N, E> sourceNode,
        E edgeValue, DiGraphNode<N, E> destNode) {
      this.value = edgeValue;
      this.sourceNode = sourceNode;
      this.destNode = destNode;
    }

    @Override
    public DiGraphNode<N, E> getSource() {
      return sourceNode;
    }

    @Override
    public DiGraphNode<N, E> getDestination() {
      return destNode;
    }

    @Override
    public void setDestination(DiGraphNode<N, E> node) {
      destNode = node;
    }

    @Override
    public void setSource(DiGraphNode<N, E> node) {
      sourceNode = node;
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
      return ((LinkedDirectedGraphNode<N, E>) sourceNode).getId();
    }

    @Override
    public String getNode2Id() {
      return ((LinkedDirectedGraphNode<N, E>) destNode).getId();
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

  /**
   * A directed graph edge that stores the source and destination nodes at each
   * edge.
   */
  static class AnnotatedLinkedDirectedGraphEdge<N, E>
      extends LinkedDirectedGraphEdge<N, E> {

    protected Annotation annotation;

    /**
     * Constructor.
     *
     * @param edgeValue Edge Value.
     */
    AnnotatedLinkedDirectedGraphEdge(DiGraphNode<N, E> sourceNode,
        E edgeValue, DiGraphNode<N, E> destNode) {
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
