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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
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
   *
   * @param compiler JS compiler.
   * @param exportSymbolFunction function used for exporting symbols.
   * @param exportPropertyFunction function used for exporting property names.
   */
  GenerateExports(
      AbstractCompiler compiler,
      boolean allowNonGlobalExports,
      String exportSymbolFunction,
      String exportPropertyFunction) {
    checkNotNull(compiler);
    checkNotNull(exportSymbolFunction);
    checkNotNull(exportPropertyFunction);

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
    NodeTraversal.traverse(compiler, root, findExportableNodes);
    Map<String, Node> exports = findExportableNodes.getExports();
    Map<Node, String> es6Exports = findExportableNodes.getEs6ClassExports();
    Set<String> localExports = findExportableNodes.getLocalExports();

    for (Map.Entry<Node, String> entry : es6Exports.entrySet()) {
      addExportForEs6Method(entry.getKey(), entry.getValue());
    }

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
    Node objectPrototype = NodeUtil.newQName(compiler, "Object.prototype");
    JSType objCtor = compiler.getTypeRegistry().getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE);
    objectPrototype.getFirstChild().setJSType(objCtor);
    Node propstmt = IR.exprResult(IR.getprop(objectPrototype, IR.string(export)));
    propstmt.useSourceInfoFromForTree(getSynthesizedExternsRoot());
    propstmt.setOriginalName(export);
    getSynthesizedExternsRoot().addChildToBack(propstmt);
    compiler.reportChangeToEnclosingScope(propstmt);
  }

  private void recordExportSymbol(String qname) {
    int dot = qname.indexOf('.');
    if (dot == -1) {
      exportedVariables.add(qname);
    } else {
      exportedVariables.add(qname.substring(0, dot));
    }
  }

  private void addExportForEs6Method(Node memberFunction, String ownerName) {
    // We always export ES6 member methods as properties.
    checkArgument(memberFunction.isMemberFunctionDef(), memberFunction);
    checkArgument(!ownerName.isEmpty(), ownerName);
    String fullExport = ownerName + "." + memberFunction.getString();
    addExportPropertyCall(ownerName, memberFunction, fullExport, memberFunction.getString());
  }

  /**
   * Emits a call to either goog.exportProperty or goog.exportSymbol.
   *
   * <p>Attempts to optimize by creating a property export instead of a symbol export, because
   * property exports are significantly simpler/faster.
   *
   * @param export The fully qualified name of the object we want to export
   * @param context The node on which the @export annotation was found
   */
  private void addExportMethod(Map<String, Node> exports, String export, Node context) {
    // We can export as a property if any of the following conditions holds:
    // a) ES6 class members, which the above `addExportForEs6Method` handles
    // b) this is a property on a name which is also being exported
    // c) this is a prototype property
    String methodOwnerName = null; // the object this method is on, null for exported names.
    boolean isEs5StylePrototypeAssignment = false; // If this is a prototype property
    String propertyName = null;

    if (context.getFirstChild().isGetProp()) { // e.g. `/** @export */ a.prototype.b = obj;`
      Node node = context.getFirstChild(); // e.g. get `a.prototype.b`
      Node ownerNode = node.getFirstChild(); // e.g. get `a.prototype`
      methodOwnerName = ownerNode.getQualifiedName(); // e.g. get the string "a.prototype"
      if (ownerNode.isGetProp()
          && ownerNode.getLastChild().getString().equals(PROTOTYPE_PROPERTY)) {
        // e.g. true if ownerNode is `a.prototype`
        // false if this export were `/** @export */ a.b = obj;` instead
        isEs5StylePrototypeAssignment = true;
      }
      propertyName = node.getSecondChild().getString();
    }

    boolean useExportSymbol = true;
    if (isEs5StylePrototypeAssignment) {
      useExportSymbol = false;
    } else if (methodOwnerName != null && exports.containsKey(methodOwnerName)) {
      useExportSymbol = false;
    }

    if (useExportSymbol) {
      addExportSymbolCall(export, context);
    } else {
      addExportPropertyCall(methodOwnerName, context, export, propertyName);
    }
  }

  private void addExportPropertyCall(
      String methodOwnerName, Node context, String export, String propertyName) {
    // exportProperty(object, publicName, symbol);
    checkNotNull(methodOwnerName);
    Node call =
        IR.call(
            NodeUtil.newQName(
                compiler, exportPropertyFunction,
                context, exportPropertyFunction),
            NodeUtil.newQName(
                compiler, methodOwnerName,
                context, exportPropertyFunction),
            IR.string(propertyName),
            NodeUtil.newQName(
                compiler, export,
                context, exportPropertyFunction));

    Node expression = IR.exprResult(call).useSourceInfoIfMissingFromForTree(context);
    annotate(expression);

    addStatement(context, expression);
  }

  private void addExportSymbolCall(String export, Node context) {
    // exportSymbol(publicPath, object);
    recordExportSymbol(export);

    Node call =
        IR.call(
            NodeUtil.newQName(
                compiler, exportSymbolFunction,
                context, export),
            IR.string(export),
            NodeUtil.newQName(
                compiler, export,
                context, export));

    Node expression = IR.exprResult(call).useSourceInfoIfMissingFromForTree(context);
    annotate(expression);

    addStatement(context, expression);
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
    compiler.reportChangeToEnclosingScope(stmt);
  }

  private void annotate(Node node) {
    NodeTraversal.traverse(
        compiler, node, new PrepareAst.PrepareAnnotations());
  }

  /** Lazily create a "new" externs root for undeclared variables. */
  private Node getSynthesizedExternsRoot() {
    return  compiler.getSynthesizedExternsInput().getAstRoot(compiler);
  }
}
