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
import com.google.javascript.jscomp.FindExportableNodes.GenerateNodeContext;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.Map;

/**
 * Generates goog.exportSymbol/goog.exportProperty for the @export annotation.
 *
 */
class GenerateExports implements CompilerPass {

  private static final String PROTOTYPE_PROPERTY = "prototype";

  private final AbstractCompiler compiler;

  private final String exportSymbolFunction;

  private final String exportPropertyFunction;

  /**
   * Creates a new generate exports compiler pass.
   * @param compiler JS compiler.
   * @param exportSymbolFunction function used for exporting symbols.
   * @param exportPropertyFunction function used for exporting property names.
   */
  GenerateExports(AbstractCompiler compiler, String exportSymbolFunction,
      String exportPropertyFunction) {
    Preconditions.checkNotNull(compiler);
    Preconditions.checkNotNull(exportSymbolFunction);
    Preconditions.checkNotNull(exportPropertyFunction);

    this.compiler = compiler;
    this.exportSymbolFunction = exportSymbolFunction;
    this.exportPropertyFunction = exportPropertyFunction;
  }

  @Override
  public void process(Node externs, Node root) {
    FindExportableNodes findExportableNodes = new FindExportableNodes(compiler);
    NodeTraversal.traverse(compiler, root, findExportableNodes);
    Map<String, GenerateNodeContext> exports = findExportableNodes
        .getExports();

    CodingConvention convention = compiler.getCodingConvention();
    for (Map.Entry<String, GenerateNodeContext> entry : exports.entrySet()) {
      String export = entry.getKey();
      GenerateNodeContext context = entry.getValue();

      // Emit the proper CALL expression.
      // This is an optimization to avoid exporting everything as a symbol
      // because exporting a property is significantly simpler/faster.
      // Only export the property if the parent is being exported or
      // if the parent is "prototype" and the grandparent is being exported.
      String parent = null;
      String grandparent = null;

      Node node = context.getNode().getFirstChild();
      if (node.isGetProp()) {
        parent = node.getFirstChild().getQualifiedName();
        if (node.getFirstChild().isGetProp() &&
            getPropertyName(node.getFirstChild()).equals(PROTOTYPE_PROPERTY)) {
          grandparent = node.getFirstChild().getFirstChild().getQualifiedName();
        }
      }

      boolean useExportSymbol = true;
      if (grandparent != null && exports.containsKey(grandparent)) {
        useExportSymbol = false;
      } else if (parent != null && exports.containsKey(parent)) {
        useExportSymbol = false;
      }

      Node call;
      if (useExportSymbol) {
        // exportSymbol(publicPath, object);
        call = IR.call(
            NodeUtil.newQualifiedNameNode(
                convention, exportSymbolFunction,
                context.getNode(), export),
            IR.string(export),
            NodeUtil.newQualifiedNameNode(
                convention, export,
                context.getNode(), export));
      } else {
        // exportProperty(object, publicName, symbol);
        String property = getPropertyName(node);
        call = IR.call(
            NodeUtil.newQualifiedNameNode(
                convention, exportPropertyFunction,
                context.getNode(), exportPropertyFunction),
            NodeUtil.newQualifiedNameNode(
                convention, parent,
                context.getNode(), exportPropertyFunction),
            IR.string(property),
            NodeUtil.newQualifiedNameNode(
                convention, export,
                context.getNode(), exportPropertyFunction));
      }

      Node expression = IR.exprResult(call);
      annotate(expression);

      // It's important that any class-building calls (goog.inherits)
      // come right after the class definition, so move the export after that.
      Node insertionPoint = context.getContextNode().getNext();
      while (insertionPoint != null &&
          NodeUtil.isExprCall(insertionPoint) &&
          convention.getClassesDefinedByCall(
              insertionPoint.getFirstChild()) != null) {
        insertionPoint = insertionPoint.getNext();
      }

      if (insertionPoint == null) {
        context.getScriptNode().addChildToBack(expression);
      } else {
        context.getScriptNode().addChildBefore(expression, insertionPoint);
      }
      compiler.reportCodeChange();
    }
  }

  private void annotate(Node node) {
    NodeTraversal.traverse(
        compiler, node, new PrepareAst.PrepareAnnotations());
  }

  /**
   * Assumes the node type is correct and returns the property name
   * (not fully qualified).
   * @param node node
   * @return property name.
   */
  private String getPropertyName(Node node) {
    Preconditions.checkArgument(node.isGetProp());
    return node.getLastChild().getString();
  }
}
