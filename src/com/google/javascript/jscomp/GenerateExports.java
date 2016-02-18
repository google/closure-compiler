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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generates goog.exportSymbol/goog.exportProperty for the @export annotation.
 *
 */
class GenerateExports implements CompilerPass {

  private static final String PROTOTYPE_PROPERTY = "prototype";

  private final AbstractCompiler compiler;

  private final String exportSymbolFunction;

  private final String exportPropertyFunction;

  private final boolean allowNonGlobalExports;

  private final Set<String> exportedVariables = new HashSet<>();

  /**
   * Creates a new generate exports compiler pass.
   * @param compiler JS compiler.
   * @param exportSymbolFunction function used for exporting symbols.
   * @param exportPropertyFunction function used for exporting property names.
   */
  GenerateExports(
      AbstractCompiler compiler,
      boolean allowNonGlobalExports,
      String exportSymbolFunction,
      String exportPropertyFunction) {
    Preconditions.checkNotNull(compiler);
    Preconditions.checkNotNull(exportSymbolFunction);
    Preconditions.checkNotNull(exportPropertyFunction);

    this.compiler = compiler;
    this.allowNonGlobalExports = allowNonGlobalExports;
    this.exportSymbolFunction = exportSymbolFunction;
    this.exportPropertyFunction = exportPropertyFunction;
  }

  Set<String> getExportedVariableNames() {
    return exportedVariables;
  }

  @Override
  public void process(Node externs, Node root) {
    FindExportableNodes findExportableNodes = new FindExportableNodes(
        compiler, allowNonGlobalExports);
    NodeTraversal.traverseEs6(compiler, root, findExportableNodes);
    Map<String, Node> exports = findExportableNodes.getExports();
    Set<String> localExports = findExportableNodes.getLocalExports();

    for (Map.Entry<String, Node> entry : exports.entrySet()) {
      String export = entry.getKey();
      Node context = entry.getValue();
      addExportMethod(exports, export, context);
    }

    for (String export : localExports) {
      addExtern(export);
    }
  }

  private void addExtern(String export) {
    Node propstmt = IR.exprResult(
        IR.getprop(NodeUtil.newQName(compiler, "Object.prototype"), IR.string(export)));
    NodeUtil.setDebugInformation(propstmt, getSynthesizedExternsRoot(), export);
    getSynthesizedExternsRoot().addChildToBack(propstmt);
    compiler.reportCodeChange();
  }

  private void recordExportSymbol(String qname) {
    int dot = qname.indexOf('.');
    if (dot == -1) {
      exportedVariables.add(qname);
    } else {
      exportedVariables.add(qname.substring(0, dot));
    }
  }

  private void addExportMethod(Map<String, Node> exports, String export, Node context) {
    // Emit the proper CALL expression.
    // This is an optimization to avoid exporting everything as a symbol
    // because exporting a property is significantly simpler/faster.
    // Only export the property if the parent is being exported or
    // if the parent is "prototype" and the grandparent is being exported.
    String parent = null;
    String grandparent = null;

    Node node = context.getFirstChild();
    if (node.isGetProp()) {
      Node parentNode = node.getFirstChild();
      parent = parentNode.getQualifiedName();
      if (parentNode.isGetProp()
          && parentNode.getLastChild().getString().equals(PROTOTYPE_PROPERTY)) {
        grandparent = parentNode.getFirstChild().getQualifiedName();
      }
    } else if (node.getParent().isMemberFunctionDef()) {
      Node classNode = node.getGrandparent().getParent();
      parent = NodeUtil.getName(classNode);
      parent += node.getParent().isStaticMember() ? "" : ".prototype";
      export = parent + "." + export;
    }

    boolean useExportSymbol = true;
    if (grandparent != null) {
      // grandparent is only set for properties exported off a prototype obj.
      useExportSymbol = false;
    } else if (parent != null && exports.containsKey(parent)) {
      useExportSymbol = false;
    } else if (node.getParent().isMemberFunctionDef()) {
      useExportSymbol = false;
    }

    Node call;
    if (useExportSymbol) {
      recordExportSymbol(export);

      // exportSymbol(publicPath, object);
      call = IR.call(
          NodeUtil.newQName(
              compiler, exportSymbolFunction,
              context, export),
          IR.string(export),
          NodeUtil.newQName(
              compiler, export,
              context, export));
    } else {
      // exportProperty(object, publicName, symbol);
      String property = getPropertyName(node);
      call = IR.call(
          NodeUtil.newQName(
              compiler, exportPropertyFunction,
              context, exportPropertyFunction),
          NodeUtil.newQName(
              compiler, parent,
              context, exportPropertyFunction),
          IR.string(property),
          NodeUtil.newQName(
              compiler, export,
              context, exportPropertyFunction));
    }

    Node expression = IR.exprResult(call).useSourceInfoIfMissingFromForTree(node);
    annotate(expression);

    addStatement(context, expression);

    compiler.reportCodeChange();
  }

  private void addStatement(Node context, Node stmt) {
    CodingConvention convention = compiler.getCodingConvention();

    Node n = context;
    Node exprRoot = n;
    while (!NodeUtil.isStatementBlock(exprRoot.getParent())) {
      exprRoot = exprRoot.getParent();
    }

    // It's important that any class-building calls (goog.inherits)
    // come right after the class definition, so move the export after that.
    while (true) {
      Node next = exprRoot.getNext();
      if (next != null
          && NodeUtil.isExprCall(next)
          && convention.getClassesDefinedByCall(next.getFirstChild()) != null) {
        exprRoot = next;
      } else {
        break;
      }
    }

    Node block = exprRoot.getParent();
    block.addChildAfter(stmt, exprRoot);
  }

  private void annotate(Node node) {
    NodeTraversal.traverseEs6(
        compiler, node, new PrepareAst.PrepareAnnotations());
  }

  /**
   * Assumes the node type is correct and returns the property name
   * (not fully qualified).
   * @param node node
   * @return property name.
   */
  private static String getPropertyName(Node node) {
    Preconditions.checkArgument(node.isGetProp() || node.getParent().isMemberFunctionDef());
    if (node.isGetProp()) {
      return node.getLastChild().getString();
    } else {
      return node.getParent().getString();
    }
  }

  /** Lazily create a "new" externs root for undeclared variables. */
  private Node getSynthesizedExternsRoot() {
    return  compiler.getSynthesizedExternsInput().getAstRoot(compiler);
  }
}
