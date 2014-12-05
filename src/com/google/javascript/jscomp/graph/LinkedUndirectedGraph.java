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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An undirected graph using linked list within nodes to store edge
 * information.
 *
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 */
public class LinkedUndirectedGraph<N, E>
    extends UndiGraph<N, E> implements GraphvizGraph {
  protected final Map<N, LinkedUndirectedGraphNode<N, E>> nodes =
      Maps.newHashMap();

  @Override
  public SubGraph<N, E> newSubGraph() {
    return new SimpleSubGraph<>(this);
  }

  public static <N, E> LinkedUndirectedGraph<N, E> createWithoutAnnotations() {
    return new LinkedUndirectedGraph<>(false, false);
  }

  public static <N, E> LinkedUndirectedGraph<N, E> create() {
    return new LinkedUndirectedGraph<>(true, true);
  }

  private final boolean useNodeAnnotations;
  private final boolean useEdgeAnnotations;

  protected LinkedUndirectedGraph(
      boolean useNodeAnnotations, boolean useEdgeAnnotations) {
    this.useNodeAnnotations = useNodeAnnotations;
    this.useEdgeAnnotations = useEdgeAnnotations;
  }

  @Override
  public void connect(N srcValue, E edgeValue, N destValue) {
    LinkedUndirectedGraphNode<N, E> src = getNodeOrFail(srcValue);
    LinkedUndirectedGraphNode<N, E> dest = getNodeOrFail(destValue);
    LinkedUndirectedGraphEdge<N, E> edge =
        useEdgeAnnotations ?
        new AnnotatedLinkedUndirectedGraphEdge<>(src, edgeValue, dest) :
        new LinkedUndirectedGraphEdge<>(src, edgeValue, dest);
    src.getNeighborEdges().add(edge);
    dest.getNeighborEdges().add(edge);
  }

  @Override
  public void disconnect(N srcValue, N destValue) {
    LinkedUndirectedGraphNode<N, E> src = getNodeOrFail(srcValue);
    LinkedUndirectedGraphNode<N, E> dest = getNodeOrFail(destValue);
    for (UndiGraphEdge<N, E> edge :
      getUndirectedGraphEdges(srcValue, destValue)) {
      src.getNeighborEdges().remove(edge);
      dest.getNeighborEdges().remove(edge);
    }
  }

  @Override
  public UndiGraphNode<N, E> createUndirectedGraphNode(
      N nodeValue) {
    LinkedUndirectedGraphNode<N, E> node = nodes.get(nodeValue);
    if (node == null) {
      node = useNodeAnnotations ?
          new AnnotatedLinkedUndirectedGraphNode<N, E>(nodeValue) :
          new LinkedUndirectedGraphNode<N, E>(nodeValue);
      nodes.put(nodeValue, node);
    }
    return node;
  }

  @Override
  public List<GraphNode<N, E>> getNeighborNodes(N value) {
    UndiGraphNode<N, E> uNode = getUndirectedGraphNode(value);
    return ((LinkedUndirectedGraphNode<N, E>) uNode).neighborList();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<UndiGraphEdge<N, E>> getUndirectedGraphEdges(N n1, N n2) {
    UndiGraphNode<N, E> dNode1 = nodes.get(n1);
    if (dNode1 == null) {
      return null;
    }
    UndiGraphNode<N, E> dNode2 = nodes.get(n2);
    if (dNode2 == null) {
      return null;
    }
    List<UndiGraphEdge<N, E>> edges = new ArrayList<>();
    for (UndiGraphEdge<N, E> outEdge : dNode1.getNeighborEdges()) {
      if (outEdge.getNodeA() == dNode2 || outEdge.getNodeB() == dNode2) {
        edges.add(outEdge);
      }
    }
    return edges;
  }

  @Override
  public UndiGraphNode<N, E> getUndirectedGraphNode(N nodeValue) {
    return nodes.get(nodeValue);
  }

  @Override
  public Collection<UndiGraphNode<N, E>> getUndirectedGraphNodes() {
    return Collections.<UndiGraphNode<N, E>>unmodifiableCollection(
        nodes.values());
  }

  @Override
  public GraphNode<N, E> createNode(N value) {
    return createUndirectedGraphNode(value);
  }

  @Override
  public List<GraphEdge<N, E>> getEdges(N n1, N n2) {
    return Collections.<GraphEdge<N, E>>unmodifiableList(
        getUndirectedGraphEdges(n1, n2));
  }

  @Override
  public GraphEdge<N, E> getFirstEdge(N n1, N n2) {
    UndiGraphNode<N, E> dNode1 = getNodeOrFail(n1);
    UndiGraphNode<N, E> dNode2 = getNodeOrFail(n2);
    for (UndiGraphEdge<N, E> outEdge : dNode1.getNeighborEdges()) {
      if (outEdge.getNodeA() == dNode2 || outEdge.getNodeB() == dNode2) {
        return outEdge;
      }
    }
    return null;
  }

  @Override
  public GraphNode<N, E> getNode(N value) {
    return getUndirectedGraphNode(value);
  }

  @Override
  public boolean isConnected(N n1, N n2) {
    return isConnected(n1, Predicates.<E>alwaysTrue(), n2);
  }

  @Override
  public boolean isConnected(N n1, E e, N n2) {
    return isConnected(n1, Predicates.equalTo(e), n2);
  }

  private boolean isConnected(N n1, Predicate<E> edgePredicate, N n2) {
    UndiGraphNode<N, E> dNode1 = nodes.get(n1);
    if (dNode1 == null) {
      return false;
    }
    UndiGraphNode<N, E> dNode2 = nodes.get(n2);
    if (dNode2 == null) {
      return false;
    }
    for (UndiGraphEdge<N, E> outEdge : dNode1.getNeighborEdges()) {
      if ((outEdge.getNodeA() == dNode1 && outEdge.getNodeB() == dNode2) ||
          (outEdge.getNodeA() == dNode2 && outEdge.getNodeB() == dNode1)) {
        if (edgePredicate.apply(outEdge.getValue())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public List<GraphvizEdge> getGraphvizEdges() {
    List<GraphvizEdge> edgeList = new ArrayList<>();
    for (LinkedUndirectedGraphNode<N, E> node : nodes.values()) {
      for (UndiGraphEdge<N, E> edge : node.getNeighborEdges()) {
        if (edge.getNodeA() == node) {
          edgeList.add((GraphvizEdge) edge);
        }
      }
    }
    return edgeList;
  }

  @Override
  public String getName() {
    return "LinkedUndirectedGraph";
  }

  @Override
  public List<GraphvizNode> getGraphvizNodes() {
    List<GraphvizNode> nodeList = new ArrayList<>(nodes.size());
    for (LinkedUndirectedGraphNode<N, E> node : nodes.values()) {
      nodeList.add(node);
    }
    return nodeList;
  }

  @Override
  public boolean isDirected() {
    return false;
  }

  @Override
  public Collection<GraphNode<N, E>> getNodes() {
    return Collections.<GraphNode<N, E>> unmodifiableCollection(nodes.values());
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<GraphEdge<N, E>> getEdges() {
    List<GraphEdge<N, E>> result = new ArrayList<>();
    for (LinkedUndirectedGraphNode<N, E> node : nodes.values()) {
      for (UndiGraphEdge<N, E> edge : node.getNeighborEdges()) {
        if (edge.getNodeA() == node) {
          result.add(edge);
        }
      }
    }
    return result;
  }

  @Override
  public int getNodeDegree(N value) {
    UndiGraphNode<N, E> uNode = getUndirectedGraphNode(value);
    if (uNode == null) {
      throw new IllegalArgumentException(value + " not found in graph");
    }
    return uNode.getNeighborEdges().size();
  }

  /**
   * An undirected graph node that stores outgoing edges and incoming edges as
   * an list within the node itself.
   */
  static class LinkedUndirectedGraphNode<N, E> implements UndiGraphNode<N, E>,
      GraphvizNode {

    private List<UndiGraphEdge<N, E>> neighborEdges = new ArrayList<>();
    private final N value;

    LinkedUndirectedGraphNode(N nodeValue) {
      this.value = nodeValue;
    }

    @Override
    public List<UndiGraphEdge<N, E>> getNeighborEdges() {
      return neighborEdges;
    }

    @Override
    public Iterator<UndiGraphEdge<N, E>> getNeighborEdgesIterator() {
      return neighborEdges.iterator();
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
    public N getValue() {
      return value;
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

    private List<GraphNode<N, E>> neighborList() {
      List<GraphNode<N, E>> result = new ArrayList<>(neighborEdges.size());
      for (UndiGraphEdge<N, E> edge : neighborEdges) {
        if (edge.getNodeA() == this) {
          result.add(edge.getNodeB());
        } else {
          result.add(edge.getNodeA());
        }
      }
      return result;
    }
  }

  /**
   * An undirected graph node with annotations.
   */
  static class AnnotatedLinkedUndirectedGraphNode<N, E>
      extends LinkedUndirectedGraphNode<N, E> {

    protected Annotation annotation;

    AnnotatedLinkedUndirectedGraphNode(N nodeValue) {
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
   * An undirected graph edge that stores two nodes at each edge.
   */
  static class LinkedUndirectedGraphEdge<N, E> implements UndiGraphEdge<N, E>,
      GraphvizEdge {

    private UndiGraphNode<N, E> nodeA;
    private UndiGraphNode<N, E> nodeB;
    protected final E value;

    LinkedUndirectedGraphEdge(UndiGraphNode<N, E> nodeA, E edgeValue,
        UndiGraphNode<N, E> nodeB) {
      this.value = edgeValue;
      this.nodeA = nodeA;
      this.nodeB = nodeB;
    }

    @Override
    public E getValue() {
      return value;
    }

    @Override
    public GraphNode<N, E> getNodeA() {
      return nodeA;
    }

    @Override
    public GraphNode<N, E> getNodeB() {
      return nodeB;
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

    @SuppressWarnings("unchecked")
    @Override
    public String getNode1Id() {
      return ((LinkedUndirectedGraphNode<N, E>) nodeA).getId();
    }

    @SuppressWarnings("unchecked")
    @Override
    public String getNode2Id() {
      return ((LinkedUndirectedGraphNode<N, E>) nodeB).getId();
    }

    @Override
    public String toString() {
      return nodeA + " -- " + nodeB;
    }
  }

  /**
   * An annotated undirected graph edge..
   */
  static class AnnotatedLinkedUndirectedGraphEdge<N, E>
      extends LinkedUndirectedGraphEdge<N, E> {

    protected Annotation annotation;

    AnnotatedLinkedUndirectedGraphEdge(
        UndiGraphNode<N, E> nodeA, E edgeValue,
        UndiGraphNode<N, E> nodeB) {
      super(nodeA, edgeValue, nodeB);
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
