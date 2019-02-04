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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Comparator.comparing;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * Creates an externs file containing all exported symbols and properties
 * for later consumption.
 *
 * @author dcc@google.com (Devin Coughlin)
 */
final class ExternExportsPass extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  private static final Joiner Q_NAME_JOINER = Joiner.on('.');
  private static final Splitter Q_NAME_SPLITTER = Splitter.on('.');

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
  private ImmutableSet<String> exportSymbolFunctionNames;

  /** A list of function names used to export properties. */
  private ImmutableSet<String> exportPropertyFunctionNames;

  private abstract class Export {
    protected final String symbolName;
    protected final Node value;

    Export(String symbolName, Node value) {
      this.symbolName = checkNotNull(symbolName);
      this.value = checkNotNull(value);
    }

    /**
     * Generates the externs representation of this export and appends
     * it to the externsRoot AST.
     */
    void generateExterns() {
      appendExtern(getExportedPath(), getValue());
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

        // The complete path (the last path prefix) must be emitted and
        // it gets initialized to the externed version of the value.
        boolean isCompletePathPrefix = (i == pathPrefixes.size() - 1);

        boolean skipPathPrefix =
            pathPrefix.endsWith(".prototype")
                || (alreadyExportedPaths.contains(pathPrefix) && !isCompletePathPrefix);
        if (skipPathPrefix) {
          continue;
        }

        boolean exportedValueDefinesNewType = false;

        if (valueToExport != null) {
          JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(valueToExport);
          if (jsdoc != null && jsdoc.containsTypeDefinition()) {
            exportedValueDefinesNewType = true;
          }
        }

        // Namespaces get initialized to {}, functions to externed versions of their value, and if
        // we can't figure out where the value came from we initialize it to {}.
        //
        // Since externs are always exported in sorted order, we know that if we export a.b =
        // function() {} and later a.b.c = function then a.b will always be in alreadyExportedPaths
        // when we emit a.b.c and thus we will never overwrite the function exported for a.b with a
        // namespace.
        final Node initializer;
        JSDocInfo jsdoc = null;
        if (isCompletePathPrefix && valueToExport != null) {
          if (valueToExport.isFunction()) {
            initializer = createExternFunction(valueToExport);
          } else if (valueToExport.isClass()) {
            initializer = createExternFunctionForEs6Class(valueToExport);
          } else {
            checkState(valueToExport.isObjectLit());
            initializer = createExternObjectLit(valueToExport);
          }
        } else if (!isCompletePathPrefix && exportedValueDefinesNewType) {
          jsdoc = buildNamespaceJSDoc();
          initializer = createExternObjectLit(IR.objectlit());
          // Don't add the empty jsdoc here
          initializer.setJSDocInfo(null);
        } else {
          initializer = IR.empty();
        }

        appendPathDefinition(pathPrefix, initializer, jsdoc);
      }
    }

    private void appendPathDefinition(
        String path, Node initializer, JSDocInfo jsdoc) {
      final Node pathDefinition;

      if (path.contains(".")) {
        Node qualifiedPath = NodeUtil.newQName(compiler, path);
        if (initializer.isEmpty()) {
          pathDefinition = NodeUtil.newExpr(qualifiedPath);
        } else {
          pathDefinition = NodeUtil.newExpr(IR.assign(qualifiedPath, initializer));
        }
      } else {
        if (initializer.isEmpty()) {
          pathDefinition = IR.var(IR.name(path));
        } else {
          pathDefinition = NodeUtil.newVarNode(path, initializer);
        }
      }

      if (jsdoc != null) {
        if (pathDefinition.isExprResult()) {
          pathDefinition.getFirstChild().setJSDocInfo(jsdoc);
        } else {
          checkState(pathDefinition.isVar());
          pathDefinition.setJSDocInfo(jsdoc);
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
      Node paramList = createExternsParamListFromOriginalFunction(exportedFunction);
      Node externFunction = IR.function(IR.name(""), paramList, IR.block());

      externFunction.setJSType(exportedFunction.getJSType());

      return externFunction;
    }

    /**
     * Creates a PARAM_LIST to store in the AST we'll use to generate externs for a function with
     * the given type.
     *
     * <p>If the NODE defining the original function is available, it would be better to use
     * createExternsParamListFromOriginalFunction(), because that one will keep the parameter names
     * the same instead of generating arbitrary parameter names.
     *
     * @param exportedFunction FUNCTION Node of the original function
     * @return
     */
    private Node createExternsParamListFromOriginalFunction(Node exportedFunction) {
      final Node originalParamList = NodeUtil.getFunctionParameters(exportedFunction);
      return createExternsParamListFromOriginalParamList(originalParamList);
    }

    /**
     * Creates a PARAM_LIST to store in the AST we'll use to generate externs for a function with
     * the given type.
     *
     * <p>If the NODE defining the original function is available, it would be better to use
     * createExternsParamListFromOriginalFunction(), because that one will keep the parameter names
     * the same instead of generating arbitrary parameter names.
     *
     * @param functionType JSType read from the FUNCTION (or possibly CLASS) node
     * @return
     */
    private Node createExternsParamListFromFunctionType(JSType functionType) {
      return createExternsParamListFromOriginalParamList(
          functionType.assertFunctionType().getParametersNode());
    }

    /**
     * Creates a PARAM_LIST to store in the AST we'll use to generate externs for a function.
     *
     * @param originalParamList Either the original PARAM_LIST from the function or the synthetic
     *     PARAM_LIST stored in the function's FunctionType
     */
    private Node createExternsParamListFromOriginalParamList(Node originalParamList) {
      // First get all of the original positional parameter list names we can.
      // Place empty stings in the positions where we'll need to generate names.
      List<String> originalParamNames = new ArrayList<>();
      for (Node originalParam = originalParamList.getFirstChild();
          originalParam != null;
          originalParam = originalParam.getNext()) {
        // We'll get an empty string for a destructuring pattern.
        // Also if originalParamList came from a FunctionType instead of an actual FUNCTION node,
        // then all of the NAME nodes in it will have empty strings, so we'll end up generating
        // names for all of them.
        originalParamNames.add(getOriginalNameForParam(originalParam));
      }

      final Node paramList = IR.paramList();
      NameGenerator nameGenerator =
          new DefaultNameGenerator(
              ImmutableSet.copyOf(originalParamNames), "", /* reservedCharacters= */ null);
      for (String originalParamName : originalParamNames) {
        String externParamName =
            originalParamName.isEmpty() ? nameGenerator.generateNextName() : originalParamName;
        paramList.addChildToBack(IR.name(externParamName));
      }
      return paramList;
    }

    /**
     * @param paramNode expected to be a node in a PARAM_LIST
     * @return original name of the parameter, if possible, otherwise an empty string.
     */
    private String getOriginalNameForParam(Node paramNode) {
      final Node nameOrPatternNode;
      if (paramNode.isRest()) {
        // get name or pattern from `...nameOrPattern`
        nameOrPatternNode = paramNode.getOnlyChild();
      } else if (paramNode.isDefaultValue()) {
        // get name or pattern from `nameOrPattern = defaultValue`
        nameOrPatternNode = paramNode.getFirstChild();
      } else {
        nameOrPatternNode = paramNode;
      }
      if (nameOrPatternNode.isName()) {
        String originalName = nameOrPatternNode.getOriginalName();
        return (originalName != null) ? originalName : nameOrPatternNode.getString();
      } else {
        checkState(nameOrPatternNode.isDestructuringPattern(), nameOrPatternNode);
        return "";
      }
    }

    /**
     * Given a class to export, create the empty function that will be put in the externs file.
     *
     * <p>This extern function should have the same type as the original function and the same
     * parameter name but no function body.
     *
     * <p>TODO(b/123352214): It would be nice if we could put ES6 classes in the generated externs,
     * but we'd have to fix some things first.
     */
    private Node createExternFunctionForEs6Class(Node exportedClass) {
      Node constructorMethodDefinition =
          NodeUtil.getEs6ClassConstructorMemberFunctionDef(exportedClass);
      if (constructorMethodDefinition == null) {
        // no constructor for the class, so just create an empty function with parameters
        // to match the parameters indicated in the JSType, which should have inherited parameters
        // from the superclass, if any.
        JSType classJSType = exportedClass.getJSType();
        Node paramList = createExternsParamListFromFunctionType(classJSType);
        Node externFunction = IR.function(IR.name(""), paramList, IR.block());
        externFunction.setJSType(classJSType);
        return externFunction;
      } else {
        // The JSType on the constructor function definition is the same as the JSType on the whole
        // class, so we can just pretend that the function is an ES5 constructor function.
        return createExternFunction(constructorMethodDefinition.getOnlyChild());
      }
    }

    private JSDocInfo buildEmptyJSDoc() {
      // TODO(johnlenz): share the JSDocInfo here rather than building
      // a new one each time.
      return new JSDocInfoBuilder(false).build(true);
    }

    private JSDocInfo buildNamespaceJSDoc() {
      JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
      builder.recordConstancy();
      builder.recordSuppressions(ImmutableSet.of("const", "duplicate"));
      return builder.build();
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
      lit.setJSDocInfo(buildEmptyJSDoc());

      int index = 1;
      for (Node child = exportedObjectLit.getFirstChild();
           child != null;
           child = child.getNext()) {
        // TODO(dimvar): handle getters or setters?
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
     * If the given value is a qualified name which refers
     * a function or object literal, the node is returned. Otherwise,
     * {@code null} is returned.
     */
    protected Node getValue() {
      String qualifiedName = value.getQualifiedName();

      if (qualifiedName == null) {
        // We expect to see
        // goog.exportSymbol('exportedName', some.path);
        // goog.exportProperty(some.path, 'exportedName', some.path.prop);
        //
        // In either case `value` will be the last argument, which we expect to be a qualified name
        // If it isn't we won't include any type information in the output externs.
        // It would be very strange to use a literal value as the final argument, since it wouldn't
        // then be accessible by any non-exported name.
        return null;
      }

      Node definition = definitionMap.get(qualifiedName);
      if (definition == null) {
        // Couldn't find any assignment to the qualified name
        return null;
      }

      if (definition.isFunction() || definition.isClass() || definition.isObjectLit()) {
        // We can generate good type information for all of these cases.
        return definition;
      }

      // value was something unusual, so we won't return any node from which to get type
      // information.
      return null;
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

      this.exportPath = checkNotNull(exportPath);
    }

    @Override
    String getExportedPath() {
      // Find the longest path that has been mapped (if any).
      for (String currentPath : Lists.reverse(computePathPrefixes(exportPath))) {
        checkState(currentPath.length() > 0);

        // If this path is mapped, return the mapped path plus any remaining pieces.
        @Nullable String mappedPath = mappedPaths.get(currentPath);
        if (mappedPath == null) {
          continue;
        }

        // Append the remaining path segments, including a leading separator.
        mappedPath += exportPath.substring(currentPath.length());
        return Q_NAME_JOINER.join(mappedPath, symbolName);
      }

      return Q_NAME_JOINER.join(exportPath, symbolName);
    }
  }

  /**
   * Computes a list of the path prefixes constructed from the components of the path.
   *
   * <pre>
   * E.g., if the path is:
   *      "a.b.c"
   * then then path prefixes will be
   *    ["a","a.b","a.b.c"]:
   * </pre>
   */
  private static ImmutableList<String> computePathPrefixes(String path) {
    List<String> pieces = Q_NAME_SPLITTER.splitToList(path);
    ImmutableList.Builder<String> pathPrefixes = ImmutableList.builder();

    String partial = pieces.get(0); // There will always be at least 1.
    pathPrefixes.add(partial);
    for (int i = 1; i < pieces.size(); i++) {
      partial = Q_NAME_JOINER.join(partial, pieces.get(i));
      pathPrefixes.add(partial);
    }

    return pathPrefixes.build();
  }

  /**
   * Creates an instance.
   */
  ExternExportsPass(AbstractCompiler compiler) {
    this.exports = new ArrayList<>();
    this.compiler = compiler;
    this.definitionMap = new HashMap<>();
    this.externsRoot = IR.script();
    this.alreadyExportedPaths = new HashSet<>();
    this.mappedPaths = new HashMap<>();

    initExportMethods();
  }

  private void initExportMethods() {
    CodingConvention convention = compiler.getCodingConvention();
    exportSymbolFunctionNames =
        ImmutableSet.of(
            convention.getExportSymbolFunction(), // goog.exportSymbol(name, value)
            "google_exportSymbol"); // used within Google

    exportPropertyFunctionNames =
        ImmutableSet.of(
            convention.getExportPropertyFunction(), // goog.exportProperty(owner, name, value)
            "google_exportProperty"); // used within Google
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);

    // Sort by path length to ensure that the longer
    // paths (which may depend on the shorter ones)
    // come later.
    Set<Export> sorted = new TreeSet<>(comparing(Export::getExportedPath));

    sorted.addAll(exports);

    for (Export export : sorted) {
      export.generateExterns();
    }

    setGeneratedExternsOnCompiler();
  }

  private void setGeneratedExternsOnCompiler() {
    CodePrinter.Builder builder = new CodePrinter.Builder(externsRoot)
      .setPrettyPrint(true)
      .setOutputTypes(true)
      .setTypeRegistry(compiler.getTypeRegistry());

    compiler.setExternExports(Joiner.on("\n").join(
        "/**",
        " * @fileoverview Generated externs.",
        " * @externs",
        " */",
        builder.build()));
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    lookForQnameDefinition(n);
    lookForAtExportOnThisDotProperty(t, n);
    lookForSymbolExportCall(n);
    lookForPropertyExportCall(n);
  }

  private void lookForQnameDefinition(Node n) {
    // TODO(b/123725559): There are lots of cases where this could fail to find the right
    //     definition or be fooled by there being multiple definitions.
    if (n.isClass()) {
      if (NodeUtil.isClassDeclaration(n)) {
        // class Foo {...}
        definitionMap.put(n.getFirstChild().getString(), n);
      }
    } else if (n.isFunction()) {
      if (NodeUtil.isFunctionDeclaration(n)) {
        // function foo() {...}
        definitionMap.put(n.getFirstChild().getString(), n);
      }
    } else if (n.isAssign()) {
      // TODO(b/123718645): Add support for destructuring assignments
      Node lhs = n.getFirstChild();
      if (lhs.isQualifiedName()) {
        // qualified.name = value;
        definitionMap.put(lhs.getQualifiedName(), n.getLastChild());
      }
    } else if (n.isName()) {
      // TODO(b/123718645): Add support for destructuring declarations
      Node parent = checkNotNull(n.getParent(), n);
      if (NodeUtil.isNameDeclaration(parent)) {
        Node value = n.getFirstChild();
        if (value != null) {
          // const foo = value;
          definitionMap.put(n.getString(), value);
        }
      }
    } else if (n.isMemberFunctionDef()) {
      // Try to find a fully qualified name for the method
      String lvalueName = NodeUtil.getBestLValueName(n);
      if (lvalueName != null) {
        // Store the function as the value
        definitionMap.put(lvalueName, n.getOnlyChild());
      }
    }
    // TODO(b/123725422): Getters and setters?
  }

  private void lookForSymbolExportCall(Node n) {
    if (!isCallToOneOf(n, exportSymbolFunctionNames)) {
      return; // not a call to goog.exportSymbol()
    }
    // TODO(b/123725716): We should report errors for malformed calls instead of just ignoring them.
    // Ensure that we only check valid calls with the 2 arguments
    // (plus the GETPROP node itself).
    if (!n.hasXChildren(3)) {
      return;
    }

    Node thisNode = n.getFirstChild();
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

  private void lookForPropertyExportCall(Node n) {
    if (!isCallToOneOf(n, this.exportPropertyFunctionNames)) {
      return; // not a call to goog.exportProperty()
    }
    // TODO(b/123725716): We should report errors for malformed calls instead of just ignoring them.
    // Ensure that we only check valid calls with the 3 arguments
    // (plus the GETPROP node itself).
    if (!n.hasXChildren(4)) {
      return;
    }

    Node thisNode = n.getFirstChild();
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

  private boolean isCallToOneOf(Node n, ImmutableSet<String> functionQnames) {
    if (!n.isCall()) {
      return false;
    } else {
      Node callee = n.getFirstChild();
      return callee.isQualifiedName() && functionQnames.contains(callee.getQualifiedName());
    }
  }

  private void lookForAtExportOnThisDotProperty(NodeTraversal t, Node thisDotPropName) {
    if (!thisDotPropName.isGetProp() || !thisDotPropName.getFirstChild().isThis()) {
      return; // not this.propName
    }
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(thisDotPropName);
    if (jsdoc == null || !jsdoc.isExport()) {
      return; // no @export on this.propName
    }

    Node constructorNode = t.getEnclosingFunction();
    if (!NodeUtil.isConstructor(constructorNode)) {
      return; // @export on this.propName only works within a constructor
    }

    Node classNode =
        NodeUtil.isEs6Constructor(constructorNode)
            ? NodeUtil.getEnclosingClass(constructorNode)
            : constructorNode;
    String className = NodeUtil.getName(classNode);
    String propertyName = thisDotPropName.getLastChild().getString();
    String prototypeName = className + ".prototype";
    Node propertyNameNode = NodeUtil.newQName(compiler, "this." + propertyName);

    // Add the export to the list.
    this.exports.add(new PropertyExport(prototypeName, propertyName, propertyNameNode));
  }

}
