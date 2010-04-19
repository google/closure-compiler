/*
 * Copyright 2009 Google Inc.
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

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;


import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Creates an externs file containing all exported symbols and properties
 * for later consumption.
 *
*
 */
class ExternExportsPass extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  /** The exports found. */
  private final List<Export> exports;

  /** A map of all assigns to their parent nodes. */
  private final Map<String, Node> definitionMap;

  /** The parent compiler. */
  private final AbstractCompiler compiler;

  /** The builder which holds the externs generated. */
  private final StringBuilder sb;

  /** A mapping of internal paths to exported paths. */
  private final Map<String, String> mappedPaths;

  /** A list of exported paths. */
  private final List<String> paths;

  private abstract class Export {

    /**
     * Generates the externs representation of this export and appends
     * it to the parent pass's builder.
     */
    abstract void generateExterns();

    /**
     * Returns the path exported by this export.
     */
    abstract String getExportedPath();

    /**
     * Appends all paths necessary for the path to be declared.
     * For example, for "a.b.c", the paths "a", "a.b" and "a.b.c"
     * will be appended (if they have not already).
     */
    protected void appendInferredPaths(String path) {
      // We wrap in a list here so we have an Iterable for the
      // Iterables lib.
      List<String> pieces = Lists.newArrayList(path.split("\\."));

      // For each path prefix, append.
      for (int i = 0; i < pieces.size(); ++i) {

        String cPath = Joiner.on(".").join(Iterables.limit(pieces, i + 1));

        // Actually append the path if it is needed (i.e. it is not
        // already present OR it is the path given to the method).
        if (i == pieces.size() - 1 || !paths.contains(cPath)) {
          if (i == 0) {
            sb.append("var ");
          }

          sb.append(cPath);

          if (i < pieces.size() - 1) {
            sb.append(";\n");
          }

          paths.add(cPath);
        }
      }
    }


    /**
     * If the given value is a qualified name which refers
     * a function, the function's node is returned. Otherwise,
     * {@code null} is returned.
     */
    protected Node getFunctionValue(Node value) {
      String qualifiedName = value.getQualifiedName();

      if (qualifiedName == null) {
        return null;
      }

      if (!definitionMap.containsKey(qualifiedName)) {
        return null;
      }

      Node definitionParent = definitionMap.get(qualifiedName);
      Node definition = definitionParent.getLastChild();

      if (definition.getType() != Token.FUNCTION) {
        return null;
      }

      return definition;
    }


    /**
     * Appends the given function definition to the builder.
     */
    protected void appendFunctionValue(Node definition) {
      sb.append(" = ");
      sb.append("function(");

      // Add the parameters.
      Node parameters = definition.getFirstChild().getNext();

      int i = 0;
      for (Node current = parameters.getFirstChild();
           current != null;
           current = current.getNext()) {

        if (i > 0) {
          sb.append(", ");
        }

        sb.append(current.getString());

        ++i;
      }

      sb.append(") {}");
    }
  }

  /**
   * A symbol export.
   */
  private class SymbolExport extends Export {
    private final String symbolName;
    private final Node value;

    public SymbolExport(String symbolName, Node value) {
      this.symbolName = symbolName;
      this.value = value;

      String qualifiedName = value.getQualifiedName();

      if (qualifiedName != null) {
        mappedPaths.put(qualifiedName, symbolName);
      }
    }

    @Override
    String getExportedPath() {
      return symbolName;
    }

    @Override
    void generateExterns() {
      appendInferredPaths(symbolName);

      Node functionValue = getFunctionValue(value);

      if (functionValue != null) {
        appendFunctionValue(functionValue);
      }

      sb.append(";\n");
    }
  }

  /**
   * A property export.
   */
  private class PropertyExport extends Export {
    private final String exportPath;
    private final String symbolName;
    private final Node value;

    public PropertyExport(String exportPath, String symbolName, Node value) {
      this.exportPath = exportPath;
      this.symbolName = symbolName;
      this.value = value;
    }

    @Override
    String getExportedPath() {

      // Find the longest path that has been mapped (if any).
      List<String> pieces = Lists.newArrayList(exportPath.split("\\."));

      for (int i = pieces.size(); i > 0; i--) {
        // Find the path of the current length.
        String cPath = Joiner.on(".").join(Iterables.limit(pieces, i));

        // If this path is mapped, return the mapped path plus any remaining
        // pieces.
        if (mappedPaths.containsKey(cPath)) {
          String newPath = mappedPaths.get(cPath);

          if (i < pieces.size()) {
            newPath += "." + Joiner.on(".").join(Iterables.skip(pieces, i));
          }

          return newPath + "." + symbolName;
        }
      }

      return exportPath + "." + symbolName;
    }

    @Override
    void generateExterns() {
      String exportedPath = getExportedPath();

      appendInferredPaths(exportedPath);

      Node functionValue = getFunctionValue(value);

      if (functionValue != null) {
        appendFunctionValue(functionValue);
      }

      sb.append(";\n");
    }
  }

  /**
   * Creates an instance.
   */
  ExternExportsPass(AbstractCompiler compiler) {
    this.exports = Lists.newArrayList();
    this.compiler = compiler;
    this.definitionMap = Maps.newHashMap();
    this.sb = new StringBuilder();
    this.paths = Lists.newArrayList();
    this.mappedPaths = Maps.newHashMap();
  }

  @Override
  public void process(Node externs, Node root) {
    new NodeTraversal(compiler, this).traverse(root);

    // Sort by path length to ensure that the longer
    // paths (which may depend on the shorter ones)
    // come later.
    Set<Export> sorted =
        new TreeSet<Export>(new Comparator<Export>() {
          @Override
          public int compare(Export e1, Export e2) {
            return e1.getExportedPath().compareTo(e2.getExportedPath());
          }
        });

    sorted.addAll(exports);

    for (Export export : sorted) {
      export.generateExterns();
    }
  }

  /**
   * Returns the generated externs.
   */
  public String getGeneratedExterns() {
    return sb.toString();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {

      case Token.NAME:
      case Token.GETPROP:
        if (parent.getType() == Token.ASSIGN) {
          definitionMap.put(n.getQualifiedName(), parent);
        }

        // Only handle function calls. This avoids assignments
        // that do not export items directly.
        if (parent.getType() != Token.CALL) {
          return;
        }

        List<String> exportSymbolNames = Lists.newArrayList();
        List<String> exportPropertyNames = Lists.newArrayList();

        // From Closure:
        // goog.exportSymbol = function(publicName, symbol)
        // goog.exportProperty = function(object, publicName, symbol)
        exportSymbolNames.add("goog.exportSymbol");
        exportPropertyNames.add("goog.exportProperty");

        // Another common one used inside google:
        exportSymbolNames.add("google_exportSymbol");
        exportPropertyNames.add("google_exportProperty");

        if (exportPropertyNames.contains(n.getQualifiedName())) {
          handlePropertyExport(parent);
        }

        if (exportSymbolNames.contains(n.getQualifiedName())) {
          handleSymbolExport(parent);
        }
    }
  }

  private void handleSymbolExport(Node parent) {
    // Ensure that we only check valid calls with the 2 arguments
    // (plus the GETPROP node itself).
    if (parent.getChildCount() != 3) {
      return;
    }

    Node thisNode = parent.getFirstChild();
    Node nameArg = thisNode.getNext();
    Node valueArg = nameArg.getNext();

    // Confirm the arguments are the expected types. If they are not,
    // then we have an export that we cannot statically identify.
    if (nameArg.getType() != Token.STRING) {
      return;
    }

    // Add the export to the list.
    this.exports.add(new SymbolExport(nameArg.getString(), valueArg));
  }

  private void handlePropertyExport(Node parent) {
    // Ensure that we only check valid calls with the 3 arguments
    // (plus the GETPROP node itself).
    if (parent.getChildCount() != 4) {
      return;
    }

    Node thisNode = parent.getFirstChild();
    Node objectArg = thisNode.getNext();
    Node nameArg = objectArg.getNext();
    Node valueArg = nameArg.getNext();

    // Confirm the arguments are the expected types. If they are not,
    // then we have an export that we cannot statically identify.
    if (objectArg.getQualifiedName() == null) {
      return;
    }

    if (nameArg.getType() != Token.STRING) {
      return;
    }

    // Add the export to the list.
    this.exports.add(
        new PropertyExport(objectArg.getQualifiedName(),
                           nameArg.getString(),
                           valueArg));
  }
}
