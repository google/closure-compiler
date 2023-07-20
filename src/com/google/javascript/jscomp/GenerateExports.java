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

import com.google.common.annotations.VisibleForTesting;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/** Generates goog.exportSymbol/goog.exportProperty for the @export annotation. */
public class GenerateExports implements CompilerPass {

  private static final String PROTOTYPE_PROPERTY = "prototype";

  private final AbstractCompiler compiler;

  private final @Nullable String exportSymbolFunction;

  private final @Nullable String exportPropertyFunction;

  private final boolean allowNonGlobalExports;

  private final Set<String> exportedVariables = new HashSet<>();

  static final DiagnosticType MISSING_EXPORT_CONVENTION =
      DiagnosticType.error(
          "JSC_MISSING_EXPORT_CONVENTION",
          "@export cannot be used without defining a exportProperty and exportSymbol function in"
              + " the coding convention");

  @VisibleForTesting
  public static final DiagnosticType MISSING_GOOG_FOR_EXPORT =
      DiagnosticType.error(
          "JSC_MISSING_EXPORT_SYMBOL_DEFINITION",
          "@export cannot be used without including closure/base.js or other definition of"
              + " {0} and {1}");

  /**
   * Creates a new generate exports compiler pass.
   *
   * <p>The {@code exportSymbolFunction} and {@code exportPropertyFunction} are optional, but the
   * compiler will report an error if they are not passed <i>and</i> this pass finds @export
   * annotations. They are allowed to be optional if no code uses the @export annotation.
   *
   * @param compiler JS compiler.
   * @param exportSymbolFunction function used for exporting symbols.
   * @param exportPropertyFunction function used for exporting property names.
   */
  GenerateExports(
      AbstractCompiler compiler,
      boolean allowNonGlobalExports,
      @Nullable String exportSymbolFunction,
      @Nullable String exportPropertyFunction) {
    checkNotNull(compiler);

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
    FindExportableNodes findExportableNodes =
        new FindExportableNodes(compiler, allowNonGlobalExports);
    NodeTraversal.traverse(compiler, root, findExportableNodes);
    Map<String, Node> exports = findExportableNodes.getExports();
    Map<Node, String> es6Exports = findExportableNodes.getEs6ClassExports();
    Set<String> localExports = findExportableNodes.getLocalExports();

    for (String export : localExports) {
      addExtern(export);
    }

    if (!exports.isEmpty() || !es6Exports.isEmpty()) {
      if (!validateExportMethodsIncluded(exports, es6Exports, root)) {
        return;
      }
    }

    for (Map.Entry<Node, String> entry : es6Exports.entrySet()) {
      addExportForEs6Method(entry.getKey(), entry.getValue());
    }

    for (Map.Entry<String, Node> entry : exports.entrySet()) {
      String export = entry.getKey();
      Node context = entry.getValue();
      addExportMethod(exports, export, context);
    }
  }

  /**
   * Validate that a) the user has configured methods to call to export symbols and b) those methods
   * are included in this binary
   *
   * @return whether to continue trying to export methods
   */
  private boolean validateExportMethodsIncluded(
      Map<String, Node> exports, Map<Node, String> es6Exports, Node root) {

    // Pick an arbitrary @export to report the warning on.
    final Node errorLocation;
    if (!exports.isEmpty()) {
      errorLocation = exports.values().stream().findFirst().get();
    } else {
      errorLocation = es6Exports.keySet().stream().findFirst().get();
    }

    if (this.exportSymbolFunction == null || this.exportPropertyFunction == null) {
      compiler.report(JSError.make(errorLocation, MISSING_EXPORT_CONVENTION));
      // don't try to rewrite @export since there's nothing we can rewrite them to.
      return false;
    }

    if (!includesExportMethods(root.getParent())) {
      compiler.report(
          JSError.make(
              errorLocation,
              MISSING_GOOG_FOR_EXPORT,
              this.exportSymbolFunction,
              this.exportPropertyFunction));
      declareExportMethodsInExterns(errorLocation);
      return true;
    }
    return true;
  }

  /**
   * In order to use @export, the user must have included the defintiion of the property and symbol
   * export methods in their binary.
   *
   * <p>In real code these methods are typically goog.exportProperty and goog.exportSymbol.
   */
  private boolean includesExportMethods(Node root) {
    Scope globalScope = new SyntacticScopeCreator(compiler).createScope(root, /* parent= */ null);
    String exportPropertyRoot = NodeUtil.getRootOfQualifiedName(exportPropertyFunction);
    String exportSymbolRoot = NodeUtil.getRootOfQualifiedName(exportSymbolFunction);
    return globalScope.hasSlot(exportPropertyRoot) && globalScope.hasSlot(exportSymbolRoot);
  }

  /** Add a synthesized externs declaration to prevent crashes later on in VarCheck. */
  private void declareExportMethodsInExterns(Node srcref) {

    String exportPropertyRoot = NodeUtil.getRootOfQualifiedName(exportPropertyFunction);
    String exportSymbolRoot = NodeUtil.getRootOfQualifiedName(exportSymbolFunction);

    if (exportPropertyRoot.equals(exportSymbolRoot)) {
      declareSyntheticExternsVar(exportPropertyRoot, srcref);
    } else {
      declareSyntheticExternsVar(exportPropertyRoot, srcref);
      declareSyntheticExternsVar(exportSymbolRoot, srcref);
    }
  }

  private void declareSyntheticExternsVar(String name, Node srcref) {
    CompilerInput syntheticInput = compiler.getSynthesizedExternsInput();
    Node syntheticVarRoot = syntheticInput.getAstRoot(compiler);

    Node varDeclaration = IR.var(IR.name(name)).srcrefTree(srcref);
    varDeclaration.setStaticSourceFile(compiler.getSynthesizedExternsInput().getSourceFile());
    syntheticVarRoot.addChildToBack(varDeclaration);
    compiler.reportChangeToEnclosingScope(varDeclaration);
  }

  private void addExtern(String export) {
    Node objectPrototype = NodeUtil.newQName(compiler, "Object.prototype");
    JSType objCtor = compiler.getTypeRegistry().getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE);
    objectPrototype.getFirstChild().setJSType(objCtor);
    Node propstmt = IR.exprResult(IR.getprop(objectPrototype, export));
    propstmt.srcrefTree(getSynthesizedExternsRoot());
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

    Node child = context.getFirstChild();
    if (child != null && child.isGetProp()) { // e.g. `/** @export */ a.prototype.b = obj;`
      Node node = context.getFirstChild(); // e.g. get `a.prototype.b`
      Node ownerNode = node.getFirstChild(); // e.g. get `a.prototype`
      methodOwnerName = ownerNode.getQualifiedName(); // e.g. get the string "a.prototype"
      if (ownerNode.isGetProp() && ownerNode.getString().equals(PROTOTYPE_PROPERTY)) {
        // e.g. true if ownerNode is `a.prototype`
        // false if this export were `/** @export */ a.b = obj;` instead
        isEs5StylePrototypeAssignment = true;
      }
      propertyName = node.getString();
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
    // JS output: exportProperty(object, publicName, symbol);
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

    Node expression = IR.exprResult(call).srcrefTreeIfMissing(context);

    addStatement(context, expression);
  }

  private void addExportSymbolCall(String export, Node context) {
    // JS output: exportSymbol(publicPath, object);
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

    Node expression = IR.exprResult(call).srcrefTreeIfMissing(context);

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

    stmt.insertAfter(exprRoot);
    compiler.reportChangeToEnclosingScope(stmt);
  }

  /** Lazily create a "new" externs root for undeclared variables. */
  private Node getSynthesizedExternsRoot() {
    return compiler.getSynthesizedExternsInput().getAstRoot(compiler);
  }
}
