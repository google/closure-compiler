/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NameReferenceGraph.Name;
import com.google.javascript.jscomp.NameReferenceGraph.Reference;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Generate a nice HTML file describing the name reference graph.
 * For each declaration, list the sites where the declaration's name
 * is referenced, and list all the names that the declaration references.
 * For each, name exactly where use occurs in the source code.
 *
 * <p>This report should be useful both for internal compiler
 * developers and for engineers trying to understand running behavior
 * of their code or who want to understand why the compiler won't
 * move their code into a new module.
 *
 * @author bowdidge@google.com (Robert Bowdidge)
 */

final class NameReferenceGraphReport {
  private NameReferenceGraph graph = null;

  /**
   * Create a NameReferenceGraphReport object.
   *
   * @param g  name reference graph to describe in report.
   */
  NameReferenceGraphReport(NameReferenceGraph g) {
    this.graph = g;
  }

  /**
   * Generate a nice HTML file describing the name reference graph.
   * For each declaration, list the sites where the declaration's name
   * is referenced, and list all the names that the declaration references.
   * For each, name exactly where use occurs in the source code.
   *
   * <p>This report should be useful both for internal compiler
   * developers and for engineers trying to understand running
   * behavior of their code or who want to understand why
   * AbstractCompiler won't move their code into a new module.
   *
   * @return String containing the entire HTML for the report.
   */
  public String getHtmlReport() {
    StringBuilder builder = new StringBuilder();
    List<DiGraphNode<Name, Reference>> nodes = Lists.newArrayList(
        graph.getDirectedGraphNodes());

    generateHtmlReportHeader(builder);

    builder.append("<h1>Name Reference Graph Dump</h1>\n");
    builder.append("OVERALL STATS\n");
    builder.append("<ul>\n");
    builder.append("<li>Total names: " + nodes.size());
    builder.append("</ul>\n");

    builder.append("ALL NAMES\n");
    builder.append("<UL>\n");

    // Sort declarations in alphabetical order.
    Collections.sort(nodes, new DiGraphNodeComparator());

    for (DiGraphNode<Name, Reference> n : nodes) {
      // Generate the HTML describing the declaration itself.
      generateDeclarationReport(builder, n);

      // Next, list the places where this name is used (REFERS TO), and the
      // names that this declaration refers to (REFERENCED BY).
      List<DiGraphEdge<Name, Reference>> outEdges =
          graph.getOutEdges(n.getValue());
      List<DiGraphEdge<Name, Reference>> inEdges =
          graph.getInEdges(n.getValue());

      // Don't bother to create the dotted list if we don't have anything to
      // put in it.
      if (!outEdges.isEmpty() || !inEdges.isEmpty()) {
        builder.append("<ul>");

        if (outEdges.size() > 0) {
          builder.append("<li>REFERS TO:<br>\n");
          builder.append("<ul>");
          for (DiGraphEdge<Name, Reference> edge : outEdges) {
            generateEdgeReport(builder, edge.getDestination().getValue(),
                edge);
          }
          builder.append("</ul>\n");
        }

        if (inEdges.size() > 0) {
          builder.append("<li>REFERENCED BY:<br>\n");
          builder.append("<ul>");
          for (DiGraphEdge<Name, Reference> edge : inEdges) {
            generateEdgeReport(builder, edge.getSource().getValue(), edge);
          }
          builder.append("</ul>");
        }
        builder.append("</ul>\n");
      }
    }
    builder.append("</ul>\n");
    generateHtmlReportFooter(builder);
    return builder.toString();
  }

  /**
   * Given a node, find the name of the containing source file.
   *
   * @param node Parse tree node whose filename is requested
   * @return String containing name of source file, or empty string if name
   *     cannot be identified.
   */
  private String getSourceFile(Node node) {
    String filename = (String) node.getProp(Node.SOURCENAME_PROP);
    if (filename == null) {
      return "";
    }
    return filename;
  }

  /**
   * Generate the HTML for describing a specific declaration.
   * @param builder  contents of report to be generated
   * @param declarationNode declaration to describe
   */
  private void generateDeclarationReport(StringBuilder builder,
      DiGraphNode<Name, Reference> declarationNode) {
    // Provide the name and location of declaration,
    // with an anchor to allow navigation to this declaration.
    String declName = declarationNode.getValue().getQualifiedName();
    JSType declType = declarationNode.getValue().getType();

    builder.append("<LI> ");
    builder.append("<A NAME=\"" + declName + "\">");
    builder.append(declName);
    builder.append("\n");

    // Provide the type of the declaration.
    // This is helpful for debugging.
    generateType(builder, declType);

    // List all the definitions of this name that were found in the code.
    // For each, list
    List<DefinitionsRemover.Definition> defs =
        declarationNode.getValue().getDeclarations();

    if (defs.size() == 0) {
       builder.append("<br>No definitions found<br>");
    } else {
      // Otherwise, provide a list of definitions in a dotted list.
      // For each definition, print the location where that definition is
      // found.
      builder.append("<ul>");
      for (DefinitionsRemover.Definition def : defs) {
        Node fnDef = def.getRValue();
        String sourceFileName = getSourceFile(fnDef);
        builder.append("<li> Defined: ");
        generateSourceReferenceLink(builder,
            sourceFileName, fnDef.getLineno(), fnDef.getCharno());
      }
      builder.append("</ul>");
    }
  }

  /**
   *  Generate the HTML header for the report style.
   * Borrowed straight from NameAnalyzer's report style.
   *
   * @param builder contents of the report to be generated
   */
  private void generateHtmlReportHeader(StringBuilder builder) {
    builder.append("<!DOCTYPE html>\n" +
        "<html>" +
        "<head>" +
        "<meta http-equiv=\"Content-Type\" " +
        "content=\"text/html;charset=utf-8\" >" +
        "<title>Name Reference Graph Dump</title>" +
        "<style type=\"text/css\">body, td, ");
    builder.append("p {font-family: Arial; font-size: 83%} ");
    builder.append("ul {margin-top:2px; margin-left:0px; padding-left:1em;}");
    builder.append("li {margin-top:3px; margin-left:24px;" +
        "padding-left:0px;padding-bottom: 4px}");
    builder.append("</style></head><body>\n");
  }

  /**
   * Generate the HTML footer for the report style.
   */
  private void generateHtmlReportFooter(StringBuilder builder) {
    builder.append("</body></html>");
  }

  /**
   * Generate a description of a specific edge between two nodes.
   * For each edge, name the element being linked, the location of the
   * reference in the source file, and the type of the reference.
   *
   * @param builder contents of the report to be generated
   * @param referencedDecl name of the declaration being referenced
   * @param edge the graph edge being described
   */
  private void generateEdgeReport(StringBuilder builder,
      Name referencedDecl, DiGraphEdge<Name, Reference> edge) {
    String srcDeclName = referencedDecl.getQualifiedName();
    builder.append("<li><A HREF=\"#" + srcDeclName + "\">");
    builder.append(srcDeclName);
    builder.append("</a> ");

    Node def = edge.getValue().getSite();
    int lineNumber = def.getLineno();
    int columnNumber = def.getCharno();
    String sourceFile = getSourceFile(def);

    generateSourceReferenceLink(builder, sourceFile, lineNumber, columnNumber);

    JSType defType = edge.getValue().getSite().getJSType();
    generateType(builder, defType);
  }

  private void generateSourceReferenceLink(StringBuilder builder,
    String sourceFile, int lineNumber, int columnNumber) {
    assert(sourceFile != null);

    builder.append("(");


    // Print out the text path so the user knows where things come from.
    builder.append(sourceFile + ":" +
        lineNumber + "," + columnNumber);


    builder.append(")");
  }

  /**
   * Dump a type in a nice, readable way.
   *
   * @param builder contents of the report to be generated.
   * @param defType type to describe
   */
  private void generateType(StringBuilder builder, JSType defType) {
    if (defType == null) {
      builder.append(" (type: null) ");
    } else if (defType.isUnknownType()) {
      builder.append(" (type: unknown) ");
    } else {
      builder.append(" (type: " +
          defType.toString() + ") ");
    }
  }

  /**
   * DiGraphNodeComparator gives us a way to generate sorted lists
   * of DiGraphNodes.  It provides a compare function used by the
   * String class's sort method.
   */
  class DiGraphNodeComparator implements
      Comparator<DiGraphNode<Name, Reference>> {
    public int compare(DiGraphNode<Name, Reference> node1,
        DiGraphNode<Name, Reference> node2) {
      Preconditions.checkNotNull(node1.getValue());
      Preconditions.checkNotNull(node2.getValue());

      if ((node1.getValue().getQualifiedName() == null) &&
          (node2.getValue().getQualifiedName() == null)) {
        return 0;
      }

      // Node 1, if null, comes before node 2.
      if (node1.getValue().getQualifiedName() == null) {
        return -1;
      }

      // Node 2, if null, comes before node 1.
      if (node2.getValue().getQualifiedName() == null) {
        return 1;
      }

      return node1.getValue().getQualifiedName().compareTo(
          node2.getValue().getQualifiedName());
    }
  }

}
