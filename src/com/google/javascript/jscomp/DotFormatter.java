/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.GraphvizGraph;
import com.google.javascript.jscomp.graph.GraphvizGraph.GraphvizEdge;
import com.google.javascript.jscomp.graph.GraphvizGraph.GraphvizNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * <p>DotFormatter prints out a dot file of the Abstract Syntax Tree.
 * For a detailed description of the dot format and visualization tool refer
 * to <a href="http://www.graphviz.org">Graphviz</a>.</p>
 * <p>Typical usage of this class</p>
 * <code>System.out.println(new DotFormatter().toDot(<i>node</i>));</code>
 * <p>This class is <b>not</b> thread safe and should not be used without proper
 * external synchronization.</p>
 *
 */
public class DotFormatter {
  private static final String INDENT = "  ";
  private static final String ARROW = " -> ";
  private static final String LINE = " -- ";

  // stores the current assignment of node to keys
  private final HashMap<Node, Integer> assignments = new HashMap<>();

  // key count in order to assign a unique key to each node
  private int keyCount = 0;

  // the builder used to generate the dot diagram
  private final Appendable builder;

  private final ControlFlowGraph<Node> cfg;

  private final boolean printAnnotations;

  /** For Testing Only */
  private DotFormatter() {
    this.builder = new StringBuilder();
    this.cfg = null;
    this.printAnnotations = false;
  }

  private DotFormatter(Node n, ControlFlowGraph<Node> cfg,
      Appendable builder, boolean printAnnotations) throws IOException {
    this.cfg = cfg;
    this.builder = builder;
    this.printAnnotations = printAnnotations;

    formatPreamble();
    traverseNodes(n);
    formatConclusion();
  }

  /**
   * Converts an AST to dot representation.
   * @param n the root of the AST described in the dot formatted string
   * @return the dot representation of the AST
   */
  public static String toDot(Node n) throws IOException  {
    return toDot(n, null);
  }

  /**
   * Converts an AST to dot representation.
   * @param n the root of the AST described in the dot formatted string
   * @param inCFG Control Flow Graph.
   * @return the dot representation of the AST
   */
  static String toDot(Node n, ControlFlowGraph<Node> inCFG)
      throws IOException  {
    StringBuilder builder = new StringBuilder();
    new DotFormatter(n, inCFG, builder, false);
    return builder.toString();
  }

  /**
   * Converts an AST to dot representation and appends it to the given buffer.
   * @param n the root of the AST described in the dot formatted string
   * @param inCFG Control Flow Graph.
   * @param builder A place to dump the graph.
   */
  static void appendDot(Node n, ControlFlowGraph<Node> inCFG,
      Appendable builder) throws IOException {
    new DotFormatter(n, inCFG, builder, false);
  }

  /**
   * Creates a DotFormatter purely for testing DotFormatter's internal methods.
   */
  static DotFormatter newInstanceForTesting() {
    return new DotFormatter();
  }

  private void traverseNodes(Node parent) throws IOException {
    // key
    int keyParent = key(parent);

    // edges
    for (Node child = parent.getFirstChild(); child != null;
        child = child.getNext()) {
      int keyChild = key(child);
      builder.append(INDENT);
      builder.append(formatNodeName(keyParent));
      builder.append(ARROW);
      builder.append(formatNodeName(keyChild));
      builder.append(" [weight=1];\n");

      traverseNodes(child);
    }

    // Flow Edges
    if (cfg != null && cfg.hasNode(parent)) {
      List<DiGraphEdge<Node, Branch>> outEdges =
        cfg.getOutEdges(parent);
      String[] edgeList = new String[outEdges.size()];
      for (int i = 0; i < edgeList.length; i++) {
        DiGraphEdge<Node, ControlFlowGraph.Branch> edge = outEdges.get(i);
        DiGraphNode<Node, Branch> succ = edge.getDestination();

        String toNode = null;
        if (succ == cfg.getImplicitReturn()) {
          toNode = "RETURN";
        } else {
          int keySucc = key(succ.getValue());
          toNode = formatNodeName(keySucc);
        }

        edgeList[i] =
            formatNodeName(keyParent) + ARROW + toNode + " [label=\"" + edge.getValue() + "\", "
            + "fontcolor=\"red\", "
            + "weight=0.01, color=\"red\"];\n";
      }

      Arrays.sort(edgeList);

      for (int i = 0; i < edgeList.length; i++) {
          builder.append(INDENT);
          builder.append(edgeList[i]);
      }
    }
  }

  int key(Node n) throws IOException {
    Integer key = assignments.get(n);
    if (key == null) {
      key = keyCount++;
      assignments.put(n, key);
      builder.append(INDENT);
      builder.append(formatNodeName(key));
      builder.append(" [label=\"");
      builder.append(name(n));
      JSType type = n.getJSType();
      if (type != null) {
        builder.append(" : ");
        builder.append(type.toString());
      }
      if (printAnnotations && cfg != null && cfg.hasNode(n)) {
        Object annotation = cfg.getNode(n).getAnnotation();
        if (annotation != null) {
          builder.append("\\n");
          builder.append(annotation.toString());
        }
      }
      builder.append("\"");
      if (n.getJSDocInfo() != null) {
        builder.append(" color=\"green\"");
      }
      builder.append("];\n");
    }
    return key;
  }

  private static String name(Node n) {
    int type = n.getType();
    switch (type) {
      case Token.VOID:
        return "VOID";

      default:
        return Token.name(type);
    }
  }

  private static String formatNodeName(Integer key) {
    return "node" + key;
  }

  private void formatPreamble() throws IOException {
    builder.append("digraph AST {\n");
    builder.append(INDENT);
    builder.append("node [color=lightblue2, style=filled];\n");
  }

  private void formatConclusion() throws IOException {
    builder.append("}\n");
  }

  /**
   * Outputs a string in DOT format that presents the graph.
   *
   * @param graph Input graph.
   * @return A string in Dot format that presents the graph.
   */
  public static String toDot(GraphvizGraph graph) {
    StringBuilder builder = new StringBuilder ();
    builder.append(graph.isDirected() ? "digraph" : "graph");
    builder.append(INDENT);
    builder.append(graph.getName());
    builder.append(" {\n");
    builder.append(INDENT);
    builder.append("node [color=lightblue2, style=filled];\n");

    final String edgeSymbol = graph.isDirected() ? ARROW : LINE;

    List<GraphvizNode> nodes = graph.getGraphvizNodes();

    String[] nodeNames = new String[nodes.size()];

    for (int i = 0; i < nodeNames.length; i++) {
      GraphvizNode gNode = nodes.get(i);
      nodeNames[i] = gNode.getId() + " [label=\"" + gNode.getLabel() +
          "\" color=\"" + gNode.getColor() + "\"]";
    }

    // We sort the nodes so we get a deterministic output every time regardless
    // of the implementation of the graph data structure.
    Arrays.sort(nodeNames);

    for (String nodeName : nodeNames) {
      builder.append(INDENT);
      builder.append(nodeName);
      builder.append(";\n");
    }

    List<GraphvizEdge> edges = graph.getGraphvizEdges();

    String[] edgeNames = new String[edges.size()];

    for (int i = 0; i < edgeNames.length; i++) {
      GraphvizEdge edge = edges.get(i);
      edgeNames[i] = edge.getNode1Id() + edgeSymbol + edge.getNode2Id();
    }

    // Again, we sort the edges as well.
    Arrays.sort(edgeNames);

    for (String edgeName : edgeNames) {
      builder.append(INDENT);
      builder.append(edgeName);
      builder.append(";\n");
    }

    builder.append("}\n");
    return builder.toString();
  }
}
