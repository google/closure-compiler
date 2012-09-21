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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Creates an externs file containing all exported symbols and properties
 * for later consumption.
 *
 */
final class ExternExportsPass extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType EXPORTED_FUNCTION_UNKNOWN_PARAMETER_TYPE =
    DiagnosticType.warning(
        "JSC_EXPORTED_FUNCTION_UNKNOWN_PARAMETER_TYPE",
        "Unable to determine type of parameter {0} for exported function {1}");

  static final DiagnosticType EXPORTED_FUNCTION_UNKNOWN_RETURN_TYPE =
    DiagnosticType.warning(
        "JSC_EXPORTED_FUNCTION_UNKNOWN_RETURN_TYPE",
        "Unable to determine return type for exported function {0}");

  /** The exports found. */
  private final List<Export> exports;

  /** A map of all assigns to their parent nodes. */
  private final Map<String, Node> definitionMap;

  /** The parent compiler. */
  private final AbstractCompiler compiler;

  /** The AST root which holds the externs generated. */
  private final Node externsRoot;

  /** A mapping of internal paths to exported paths. */
  private final Map<String, String> mappedPaths;

  /** A list of exported paths. */
  private final Set<String> alreadyExportedPaths;

  /** A list of function names used to export symbols. */
  private List<String> exportSymbolFunctionNames;

  /** A list of function names used to export properties. */
  private List<String> exportPropertyFunctionNames;

  private abstract class Export {
    protected final String symbolName;
    protected final Node value;

    Export(String symbolName, Node value) {
      this.symbolName = symbolName;
      this.value = value;
    }

    /**
     * Generates the externs representation of this export and appends
     * it to the externsRoot AST.
     */
    void generateExterns() {
      appendExtern(getExportedPath(), getValue(value));
    }

    /**
     * Returns the path exported by this export.
     */
    abstract String getExportedPath();

    /**
     * Appends the exported function and all paths necessary for the path to be
     * declared. For example, for a property "a.b.c", the initializers for
     * paths "a", "a.b" will be appended (if they have not already) and a.b.c
     * will be initialized with the exported version of the function:
     * <pre>
     * var a = {};
     * a.b = {};
     * a.b.c = function(x,y) { }
     * </pre>
     */
    void appendExtern(String path, Node valueToExport) {
      List<String> pathPrefixes = computePathPrefixes(path);

      for (int i = 0; i < pathPrefixes.size(); ++i) {
        String pathPrefix = pathPrefixes.get(i);

        /* The complete path (the last path prefix) must be emitted and
         * it gets initialized to the externed version of the value.
         */
        boolean isCompletePathPrefix = (i == pathPrefixes.size() - 1);

        boolean skipPathPrefix = pathPrefix.endsWith(".prototype")
            || (alreadyExportedPaths.contains(pathPrefix)
                && !isCompletePathPrefix);

        if (!skipPathPrefix) {
           Node initializer;

          /* Namespaces get initialized to {}, functions to
           * externed versions of their value, and if we can't
           * figure out where the value came from we initialize
           * it to {}.
           *
           * Since externs are always exported in sorted order,
           * we know that if we export a.b = function() {} and later
           * a.b.c = function then a.b will always be in alreadyExportedPaths
           * when we emit a.b.c and thus we will never overwrite the function
           * exported for a.b with a namespace.
           */

          if (isCompletePathPrefix && valueToExport != null) {
            if (valueToExport.isFunction()) {
              initializer = createExternFunction(valueToExport);
            } else {
              Preconditions.checkState(valueToExport.isObjectLit());
              initializer = createExternObjectLit(valueToExport);
            }
          } else {
            initializer = IR.empty();
          }

          appendPathDefinition(pathPrefix, initializer);
        }
      }
    }

    /**
     * Computes a list of the path prefixes constructed from the components
     * of the path.
     * <pre>
     * E.g., if the path is:
     *      "a.b.c"
     * then then path prefixes will be
     *    ["a","a.b","a.b.c"]:
     * </pre>
     */
    private List<String> computePathPrefixes(String path) {
      List<String> pieces = Lists.newArrayList(path.split("\\."));

      List<String> pathPrefixes = Lists.newArrayList();

      for (int i = 0; i < pieces.size(); i++) {
        pathPrefixes.add(Joiner.on(".").join(Iterables.limit(pieces, i + 1)));
      }

      return pathPrefixes;
    }

    private void appendPathDefinition(String path, Node initializer) {
      Node pathDefinition;

      if (!path.contains(".")) {
        if (initializer.isEmpty()) {
          pathDefinition = IR.var(IR.name(path));
        } else {
          pathDefinition = NodeUtil.newVarNode(path, initializer);
        }
      } else {
        Node qualifiedPath = NodeUtil.newQualifiedNameNode(
            compiler.getCodingConvention(), path);
        if (initializer.isEmpty()) {
          pathDefinition = NodeUtil.newExpr(qualifiedPath);
        } else {
          pathDefinition = NodeUtil.newExpr(
              IR.assign(qualifiedPath, initializer));
        }
      }

      externsRoot.addChildToBack(pathDefinition);

      alreadyExportedPaths.add(path);
    }

    /**
     * Given a function to export, create the empty function that
     * will be put in the externs file. This extern function should have
     * the same type as the original function and the same parameter
     * name but no function body.
     *
     * We create a warning here if the the function to export is missing
     * parameter or return types.
     */
    private Node createExternFunction(Node exportedFunction) {
      Node paramList = NodeUtil.getFunctionParameters(exportedFunction)
          .cloneTree();
      Node externFunction = IR.function(IR.name(""), paramList, IR.block());

      checkForFunctionsWithUnknownTypes(exportedFunction);
      externFunction.setJSType(exportedFunction.getJSType());

      return externFunction;
    }

    /**
     * Given an object literal to export, create an object lit with all its
     * string properties. We don't care what the values of those properties
     * are because they are not checked.
     */
    private Node createExternObjectLit(Node exportedObjectLit) {
      Node lit = IR.objectlit();
      lit.setJSType(exportedObjectLit.getJSType());

      // This is an indirect way of telling the typed code generator
      // "print the type of this"
      lit.setJSDocInfo(new JSDocInfo());

      int index = 1;
      for (Node child = exportedObjectLit.getFirstChild();
           child != null;
           child = child.getNext()) {
        // TODO: handle getters or setters?
        if (child.isStringKey()) {
          lit.addChildToBack(
              IR.propdef(
                  IR.stringKey(child.getString()),
                  IR.number(index++)));
        }
      }
      return lit;
    }

    /**
     * Warn the user if there is an exported function for which a parameter
     * or return type is unknown.
     */
    private void checkForFunctionsWithUnknownTypes(Node function) {
      Preconditions.checkArgument(function.isFunction());

      FunctionType functionType =
          JSType.toMaybeFunctionType(function.getJSType());

      if (functionType == null) {
        // No type information is available (CheckTypes was probably not run)
        // so just bail.
        return;
      }

      /* We must get the JSDocInfo from the function's type since the function
       * itself does not have an associated JSDocInfo node.
       */
      JSDocInfo functionJSDocInfo = functionType.getJSDocInfo();

      JSType returnType = functionType.getReturnType();

      /* It is OK if a constructor doesn't have a return type */
      if (!functionType.isConstructor() &&
          (returnType == null || returnType.isUnknownType())) {
        reportUnknownReturnType(function);
      }

      /* We can't just use the function's type's getParameters() to get the
       * parameter nodes because the nodes returned from that method
       * do not have names or locations. Similarly, the function's AST parameter
       * nodes do not have JSTypes(). So we walk both lists of parameter nodes
       * in lock step getting parameter names from the first and types from the
       * second.
       */
      Node astParameterIterator = NodeUtil.getFunctionParameters(function)
        .getFirstChild();

      Node typeParameterIterator = functionType.getParametersNode()
        .getFirstChild();

      while (astParameterIterator != null) {
        JSType parameterType = typeParameterIterator.getJSType();

        if (parameterType == null || parameterType.isUnknownType()) {
          reportUnknownParameterType(function, astParameterIterator);
        }

        astParameterIterator = astParameterIterator.getNext();
        typeParameterIterator = typeParameterIterator.getNext();
      }
    }

    private void reportUnknownParameterType(Node function, Node parameter) {
      compiler.report(JSError.make(NodeUtil.getSourceName(function),
          parameter, CheckLevel.WARNING,
          EXPORTED_FUNCTION_UNKNOWN_PARAMETER_TYPE,
          NodeUtil.getFunctionName(function), parameter.getString()));
    }

    private void reportUnknownReturnType(Node function) {
      compiler.report(JSError.make(NodeUtil.getSourceName(function),
          function, CheckLevel.WARNING, EXPORTED_FUNCTION_UNKNOWN_RETURN_TYPE,
          NodeUtil.getFunctionName(function)));
    }

    /**
     * If the given value is a qualified name which refers
     * a function or object literal, the node is returned. Otherwise,
     * {@code null} is returned.
     */
    protected Node getValue(Node qualifiedNameNode) {
      String qualifiedName = value.getQualifiedName();

      if (qualifiedName == null) {
        return null;
      }

      Node definitionParent = definitionMap.get(qualifiedName);
      if (definitionParent == null) {
        return null;
      }

      Node definition;

      switch (definitionParent.getType()) {
        case Token.ASSIGN:
          definition = definitionParent.getLastChild();
          break;
        case Token.VAR:
          definition = definitionParent.getLastChild().getLastChild();
          break;
        default:
            return null;
      }

      if (!definition.isFunction() && !definition.isObjectLit()) {
        return null;
      }

      return definition;
    }
  }

  /**
   * A symbol export.
   */
  private class SymbolExport extends Export {

    public SymbolExport(String symbolName, Node value) {
      super(symbolName, value);

      String qualifiedName = value.getQualifiedName();

      if (qualifiedName != null) {
        mappedPaths.put(qualifiedName, symbolName);
      }
    }

    @Override
    String getExportedPath() {
      return symbolName;
    }
  }

  /**
   * A property export.
   */
  private class PropertyExport extends Export {
    private final String exportPath;

    public PropertyExport(String exportPath, String symbolName, Node value) {
      super(symbolName, value);

      this.exportPath = exportPath;
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
  }

  /**
   * Creates an instance.
   */
  ExternExportsPass(AbstractCompiler compiler) {
    this.exports = Lists.newArrayList();
    this.compiler = compiler;
    this.definitionMap = Maps.newHashMap();
    this.externsRoot = IR.block();
    this.externsRoot.setIsSyntheticBlock(true);
    this.alreadyExportedPaths = Sets.newHashSet();
    this.mappedPaths = Maps.newHashMap();

    initExportMethods();
  }

  private void initExportMethods() {
    exportSymbolFunctionNames = Lists.newArrayList();
    exportPropertyFunctionNames = Lists.newArrayList();

    // From Closure:
    // goog.exportSymbol = function(publicName, symbol)
    // goog.exportProperty = function(object, publicName, symbol)
    CodingConvention convention = compiler.getCodingConvention();
    exportSymbolFunctionNames.add(convention.getExportSymbolFunction());
    exportPropertyFunctionNames.add(convention.getExportPropertyFunction());

    // Another common one used inside google:
    exportSymbolFunctionNames.add("google_exportSymbol");
    exportPropertyFunctionNames.add("google_exportProperty");
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
    CodePrinter.Builder builder = new CodePrinter.Builder(externsRoot)
      .setPrettyPrint(true).setOutputTypes(true);

    return builder.build();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {

      case Token.NAME:
      case Token.GETPROP:
        String name = n.getQualifiedName();
        if (name == null) {
          return;
        }

        if (parent.isAssign() || parent.isVar()) {
          definitionMap.put(name, parent);
        }

        // Only handle function calls. This avoids assignments
        // that do not export items directly.
        if (!parent.isCall()) {
          return;
        }

        if (exportPropertyFunctionNames.contains(name)) {
          handlePropertyExport(parent);
        }

        if (exportSymbolFunctionNames.contains(name)) {
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
    if (!nameArg.isString()) {
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
    if (!objectArg.isQualifiedName()) {
      return;
    }

    if (!nameArg.isString()) {
      return;
    }

    // Add the export to the list.
    this.exports.add(
        new PropertyExport(objectArg.getQualifiedName(),
                           nameArg.getString(),
                           valueArg));
  }
}
