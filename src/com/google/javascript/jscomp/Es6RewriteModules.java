/*
 * Copyright 2014 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature.MODULES;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Rewrites a ES6 module into a form that can be safely concatenated. Note that we treat a file as
 * an ES6 module if it has at least one import or export statement.
 *
 * @author moz@google.com (Michael Zhou)
 */
public final class Es6RewriteModules extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  private static final String DEFAULT_EXPORT_NAME = "$jscompDefaultExport";

  static final DiagnosticType LHS_OF_GOOG_REQUIRE_MUST_BE_CONST =
      DiagnosticType.error(
          "JSC_LHS_OF_GOOG_REQUIRE_MUST_BE_CONST",
          "The left side of a goog.require() must use ''const'' (not ''let'' or ''var'')");

  static final DiagnosticType NAMESPACE_IMPORT_CANNOT_USE_STAR =
      DiagnosticType.error(
          "JSC_NAMESPACE_IMPORT_CANNOT_USE_STAR",
          "Namespace imports ('goog:some.Namespace') cannot use import * as. "
              + "Did you mean to import {0} from ''{1}'';?");

  static final DiagnosticType DUPLICATE_EXPORT =
      DiagnosticType.error("JSC_DUPLICATE_EXPORT", "Duplicate export ''{0}''.");

  private final AbstractCompiler compiler;

  @Nullable private final PreprocessorSymbolTable preprocessorSymbolTable;
  private int scriptNodeCount;

  /**
   * Maps local names to their exported names. Multimap since the same name can be exported multiple
   * times.
   */
  private Multimap<String, NameNodePair> exportsByLocalName;

  /**
   * Maps symbol names to a pair of (moduleName, originalName). The original
   * name is the name of the symbol exported by the module. This is required
   * because we want to be able to update the original property on the module
   * object. Eg: "import {foo as f} from 'm'" maps 'f' to the pair ('m', 'foo').
   * In the entry for "import * as ns", the originalName will be the empty string.
   */
  private Map<String, ModuleOriginalNamePair> importMap;

  private Set<String> classes;
  private Set<String> typedefs;

  /**
   * Creates a new Es6RewriteModules instance which can be used to rewrite ES6 modules to a
   * concatenable form.
   */
  public Es6RewriteModules(
      AbstractCompiler compiler, @Nullable PreprocessorSymbolTable preprocessorSymbolTable) {
    this.compiler = compiler;
    this.preprocessorSymbolTable = preprocessorSymbolTable;
  }

  /**
   * Return whether or not the given script node represents an ES6 module file.
   */
  public static boolean isEs6ModuleRoot(Node scriptNode) {
    checkArgument(scriptNode.isScript(), scriptNode);
    if (scriptNode.getBooleanProp(Node.GOOG_MODULE)) {
      return false;
    }
    return scriptNode.hasChildren() && scriptNode.getFirstChild().isModuleBody();
  }

  @Override
  public void process(Node externs, Node root) {
    checkArgument(externs.isRoot(), externs);
    checkArgument(root.isRoot(), root);
    for (Node file : Iterables.concat(externs.children(), root.children())) {
      checkState(file.isScript(), file);
      hotSwapScript(file, null);
    }
    compiler.setFeatureSet(compiler.getFeatureSet().without(MODULES));
  }

  @Override
  public void hotSwapScript(Node scriptNode, Node originalRoot) {
    if (isEs6ModuleRoot(scriptNode)) {
      processFile(scriptNode);
    }
  }

  /**
   * Rewrite a single ES6 module file to a global script version.
   */
  private void processFile(Node root) {
    checkArgument(isEs6ModuleRoot(root), root);
    clearState();
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  public void clearState() {
    this.scriptNodeCount = 0;
    this.exportsByLocalName = LinkedHashMultimap.create();
    this.importMap = new HashMap<>();
    this.classes = new HashSet<>();
    this.typedefs = new HashSet<>();
  }

  /**
   * Avoid processing if we find the appearance of goog.provide or goog.module.
   *
   * <p>TODO(moz): Let ES6, CommonJS and goog.provide live happily together.
   */
  static class FindGoogProvideOrGoogModule extends NodeTraversal.AbstractPreOrderCallback {

    private boolean found;

    boolean isFound() {
      return found;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      if (found) {
        return false;
      }
      // Shallow traversal, since we don't need to inspect within functions or expressions.
      if (parent == null
          || NodeUtil.isControlStructure(parent)
          || NodeUtil.isStatementBlock(parent)) {
        if (n.isExprResult()) {
          Node maybeGetProp = n.getFirstFirstChild();
          if (maybeGetProp != null
              && (maybeGetProp.matchesQualifiedName("goog.provide")
                  || maybeGetProp.matchesQualifiedName("goog.module"))) {
            found = true;
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isImport()) {
      maybeWarnExternModule(t, n, parent);
      visitImport(t, n, parent);
    } else if (n.isExport()) {
      maybeWarnExternModule(t, n, parent);
      visitExport(t, n, parent);
    } else if (n.isScript()) {
      scriptNodeCount++;
      visitScript(t, n);
    }
  }

  private void maybeWarnExternModule(NodeTraversal t, Node n, Node parent) {
    checkState(parent.isModuleBody());
    if (parent.isFromExterns() && !NodeUtil.isFromTypeSummary(parent.getParent())) {
      t.report(n, Es6ToEs3Util.CANNOT_CONVERT_YET, "ES6 modules in externs");
    }
  }

  private void visitImport(NodeTraversal t, Node importDecl, Node parent) {
    checkArgument(parent.isModuleBody(), parent);
    String moduleName;
    String importName = importDecl.getLastChild().getString();
    boolean isNamespaceImport = importName.startsWith("goog:");
    if (isNamespaceImport) {
      // Allow importing Closure namespace objects (e.g. from goog.provide or goog.module) as
      //   import ... from 'goog:my.ns.Object'.
      // These are rewritten to plain namespace object accesses.
      moduleName = importName.substring("goog:".length());
    } else {
      ModuleLoader.ModulePath modulePath =
          t.getInput()
              .getPath()
              .resolveJsModule(
                  importName,
                  importDecl.getSourceFileName(),
                  importDecl.getLineno(),
                  importDecl.getCharno());
      if (modulePath == null) {
        // The module loader issues an error
        // Fall back to assuming the module is a file path
        modulePath = t.getInput().getPath().resolveModuleAsPath(importName);
      }

      moduleName = modulePath.toModuleName();
      maybeAddImportedFileReferenceToSymbolTable(importDecl.getLastChild(), modulePath.toString());
    }

    for (Node child : importDecl.children()) {
      if (child.isEmpty() || child.isString()) {
        continue;
      } else if (child.isName()) { // import a from "mod"
        // Namespace imports' default export is the namespace itself.
        String name = isNamespaceImport ? "" : "default";
        importMap.put(child.getString(), new ModuleOriginalNamePair(moduleName, name));
      } else if (child.isImportSpecs()) {
        for (Node grandChild : child.children()) {
          String origName = grandChild.getFirstChild().getString();
          maybeAddAliasToSymbolTable(grandChild.getFirstChild(), t.getSourceName());
          checkState(grandChild.hasTwoChildren());
          importMap.put(
              grandChild.getLastChild().getString(),
              new ModuleOriginalNamePair(moduleName, origName));
        }
      } else {
        // import * as ns from "mod"
        Preconditions.checkState(
            child.isImportStar(), "Expected an IMPORT_STAR node, but was: %s", child);
        // Namespace imports cannot be imported "as *".
        if (isNamespaceImport) {
          compiler.report(
              t.makeError(
                  importDecl, NAMESPACE_IMPORT_CANNOT_USE_STAR, child.getString(), moduleName));
        }
        maybeAddAliasToSymbolTable(child, t.getSourceName());
        importMap.put(
            child.getString(),
            new ModuleOriginalNamePair(moduleName, ""));
      }
    }

    parent.removeChild(importDecl);
    t.reportCodeChange();
  }

  private void visitExport(NodeTraversal t, Node export, Node parent) {
    checkArgument(parent.isModuleBody(), parent);
    if (export.getBooleanProp(Node.EXPORT_DEFAULT)) {
      // export default

      // If the thing being exported is a class or function that has a name,
      // extract it from the export statement, so that it can be referenced
      // from within the module.
      //
      //   export default class X {} -> class X {}; ... moduleName.default = X;
      //   export default function X() {} -> function X() {}; ... moduleName.default = X;
      //
      // Otherwise, create a local variable for it and export that.
      //
      //   export default 'someExpression'
      //     ->
      //   var $jscompDefaultExport = 'someExpression';
      //   ...
      //   moduleName.default = $jscompDefaultExport;
      Node child = export.getFirstChild();
      String name = null;

      if (child.isFunction() || child.isClass()) {
        name = NodeUtil.getName(child);
      }

      if (name != null) {
        Node decl = child.detach();
        parent.replaceChild(export, decl);
        exportsByLocalName.put(name, new NameNodePair("default", child));
      } else {
        Node var = IR.var(IR.name(DEFAULT_EXPORT_NAME), export.removeFirstChild());
        var.setJSDocInfo(child.getJSDocInfo());
        child.setJSDocInfo(null);
        var.useSourceInfoIfMissingFromForTree(export);
        parent.replaceChild(export, var);
        exportsByLocalName.put(DEFAULT_EXPORT_NAME, new NameNodePair("default", child));
      }
    } else if (export.getBooleanProp(Node.EXPORT_ALL_FROM)) {
      //   export * from 'moduleIdentifier';
      compiler.report(JSError.make(export, Es6ToEs3Util.CANNOT_CONVERT_YET, "Wildcard export"));
    } else if (export.hasTwoChildren()) {
      //   export {x, y as z} from 'moduleIdentifier';
      Node moduleIdentifier = export.getLastChild();
      Node importNode = IR.importNode(IR.empty(), IR.empty(), moduleIdentifier.cloneNode());
      importNode.useSourceInfoFrom(export);
      parent.addChildBefore(importNode, export);
      visit(t, importNode, parent);

      ModuleLoader.ModulePath path =
          t.getInput()
              .getPath()
              .resolveJsModule(
                  moduleIdentifier.getString(),
                  export.getSourceFileName(),
                  export.getLineno(),
                  export.getCharno());
      if (path == null) {
        path = t.getInput().getPath().resolveModuleAsPath(moduleIdentifier.getString());
      }
      String moduleName = path.toModuleName();

      for (Node exportSpec : export.getFirstChild().children()) {
        String nameFromOtherModule = exportSpec.getFirstChild().getString();
        String exportedName = exportSpec.getLastChild().getString();
        NameNodePair pair = new NameNodePair(exportedName, exportSpec);
        // No way of knowing if the thing we're reexporting is mutated or not. So to be on the
        // safe side we need to assume it is mutated.
        // TODO(johnplaisted): Preparse ES6 modules to figure this out and support export *.
        pair.mutated = true;
        exportsByLocalName.put(moduleName + "." + nameFromOtherModule, pair);
      }
      parent.removeChild(export);
    } else {
      if (export.getFirstChild().getToken() == Token.EXPORT_SPECS) {
        //     export {Foo};
        for (Node exportSpec : export.getFirstChild().children()) {
          checkState(exportSpec.hasTwoChildren());
          exportsByLocalName.put(
              exportSpec.getFirstChild().getString(),
              new NameNodePair(exportSpec.getLastChild().getString(), exportSpec));
        }
        parent.removeChild(export);
      } else {
        visitExportDeclaration(t, export, parent);
      }
      t.reportCodeChange();
    }
  }

  private void visitExportNameDeclaration(Node declaration) {
    //    export var Foo;
    //    export let {a, b:[c,d]} = {};
    List<Node> lhsNodes = NodeUtil.findLhsNodesInNode(declaration);

    for (Node lhs : lhsNodes) {
      checkState(lhs.isName());
      String name = lhs.getString();
      exportsByLocalName.put(name, new NameNodePair(name, lhs));

      if (declaration.getJSDocInfo() != null && declaration.getJSDocInfo().hasTypedefType()) {
        typedefs.add(name);
      }
    }
  }

  private void visitExportDeclaration(NodeTraversal t, Node export, Node parent) {
    //    export var Foo;
    //    export function Foo() {}
    // etc.
    Node declaration = export.getFirstChild();

    if (NodeUtil.isNameDeclaration(declaration)) {
      visitExportNameDeclaration(declaration);
    } else {
      checkState(declaration.isFunction() || declaration.isClass());
      Node nameNode = declaration.getFirstChild();
      String name = nameNode.getString();
      exportsByLocalName.put(name, new NameNodePair(name, nameNode));

      // If the declaration declares a new type, create annotations for
      // the type checker.
      // TODO(moz): Currently we only record ES6 classes and typedefs,
      // need to handle other kinds of type declarations too.
      if (declaration.isClass()) {
        classes.add(name);
      }
    }

    parent.replaceChild(export, declaration.detach());
    t.reportCodeChange();
  }

  private void inlineModuleToGlobalScope(Node moduleNode) {
    checkState(moduleNode.isModuleBody());
    Node scriptNode = moduleNode.getParent();
    moduleNode.detach();
    scriptNode.addChildrenToFront(moduleNode.removeChildren());
  }

  private void visitScript(NodeTraversal t, Node script) {
    NodeTraversal.traverseEs6(compiler, script, new FindMutatedExports());

    inlineModuleToGlobalScope(script.getFirstChild());

    ClosureRewriteModule.checkAndSetStrictModeDirective(t, script);

    checkArgument(
        scriptNodeCount == 1,
        "Es6RewriteModules supports only one invocation per " + "CompilerInput / script node");

    // rewriteRequires is here (rather than being part of the main visit()
    // method, because we only want to rewrite the requires if this is an
    // ES6 module.
    rewriteRequires(script);

    String moduleName = t.getInput().getPath().toModuleName();

    Node moduleVar = createExportsObject(t, script);

    // Rename vars to not conflict in global scope.
    NodeTraversal.traverseEs6(compiler, script, new RenameGlobalVars(moduleName));

    // Rename the exports object to something we can reference later.
    moduleVar.getFirstChild().setString(moduleName);
    moduleVar.makeNonIndexableRecursive();

    t.reportCodeChange();
  }

  private Node createExportsObject(NodeTraversal t, Node script) {
    String moduleName = t.getInput().getPath().toModuleName();
    Set<String> exportedNames = new HashSet<>();

    Node objLit = IR.objectlit();
    // Going to get renamed by rename global vars, doesn't matter
    Node moduleVar = IR.var(IR.name("exports"), objLit);
    moduleVar.getFirstChild().putBooleanProp(Node.MODULE_EXPORT, true);
    JSDocInfoBuilder infoBuilder = new JSDocInfoBuilder(false);
    infoBuilder.recordConstancy();
    moduleVar.setJSDocInfo(infoBuilder.build());
    script.addChildToBack(moduleVar.useSourceInfoIfMissingFromForTree(script));

    for (Map.Entry<String, NameNodePair> entry : exportsByLocalName.entries()) {
      NameNodePair pair = entry.getValue();
      String exportedName = pair.exportedName;
      Node nodeForSourceInfo = pair.nodeForSourceInfo;

      if (!exportedNames.add(exportedName)) {
        t.report(nodeForSourceInfo, DUPLICATE_EXPORT, exportedName);
        continue;
      }

      String withSuffix = entry.getKey();
      boolean mutated = pair.mutated;
      Node getProp = IR.getprop(IR.name(moduleName), IR.string(exportedName));
      getProp.putBooleanProp(Node.MODULE_EXPORT, true);

      if (typedefs.contains(exportedName)) {
        // /** @typedef {foo} */
        // moduleName.foo;
        JSDocInfoBuilder builder = new JSDocInfoBuilder(true);
        JSTypeExpression typeExpr = new JSTypeExpression(
            IR.string(exportedName), script.getSourceFileName());
        builder.recordTypedef(typeExpr);
        JSDocInfo info = builder.build();
        getProp.setJSDocInfo(info);
        Node exprResult = IR.exprResult(getProp)
            .useSourceInfoIfMissingFromForTree(nodeForSourceInfo);
        script.addChildToBack(exprResult);
      } else if (mutated || importMap.containsKey(withSuffix)) {
        addGetterExport(script, nodeForSourceInfo, objLit, exportedName, withSuffix);
      } else {
        // This step is done before type checking and the type checker doesn't understand getters.
        // However it does understand aliases. So if an export isn't mutated use an alias to make it
        // actually type checkable.
        // exports.foo = foo;
        Node assign = IR.assign(getProp, NodeUtil.newQName(compiler, withSuffix));
        if (classes.contains(exportedName)) {
          JSDocInfoBuilder builder = new JSDocInfoBuilder(true);
          builder.recordConstancy();
          JSDocInfo info = builder.build();
          assign.setJSDocInfo(info);
        }
        script.addChildToBack(
            IR.exprResult(assign).useSourceInfoIfMissingFromForTree(nodeForSourceInfo));
      }
    }

    exportsByLocalName.clear();

    return moduleVar;
  }

  private void addGetterExport(
      Node script, Node forSourceInfo, Node objLit, String exportedName, String localName) {
    // Type checker doesn't infer getters so mark the return as unknown.
    // { /** @return {?} */ get foo() { return foo; } }
    Node getter = Node.newString(Token.GETTER_DEF, exportedName);
    getter.putBooleanProp(Node.MODULE_EXPORT, true);
    objLit.addChildToBack(getter);

    Node name = NodeUtil.newQName(compiler, localName);
    Node function = IR.function(IR.name(""), IR.paramList(), IR.block(IR.returnNode(name)));
    getter.addChildToFront(function);

    JSDocInfoBuilder builder = new JSDocInfoBuilder(true);
    builder.recordReturnType(
        new JSTypeExpression(new Node(Token.QMARK), script.getSourceFileName()));
    getter.setJSDocInfo(builder.build());

    getter.useSourceInfoIfMissingFromForTree(forSourceInfo);
    compiler.reportChangeToEnclosingScope(getter.getFirstChild().getLastChild());
    compiler.reportChangeToEnclosingScope(getter);
  }

  private void rewriteRequires(Node script) {
    NodeTraversal.traverseEs6(
        compiler,
        script,
        new NodeTraversal.AbstractShallowCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (n.isCall()
                && n.getFirstChild().matchesQualifiedName("goog.require")
                && NodeUtil.isNameDeclaration(parent.getParent())) {
              visitRequire(n, parent);
            }
          }

          /**
           * Rewrites
           *   const foo = goog.require('bar.foo');
           * to
           *   goog.require('bar.foo');
           *   const foo = bar.foo;
           */
          private void visitRequire(Node requireCall, Node parent) {
            String namespace = requireCall.getLastChild().getString();
            if (!parent.getParent().isConst()) {
              compiler.report(JSError.make(parent.getParent(), LHS_OF_GOOG_REQUIRE_MUST_BE_CONST));
            }

            Node replacement = NodeUtil.newQName(compiler, namespace).srcrefTree(requireCall);
            parent.replaceChild(requireCall, replacement);
            Node varNode = parent.getParent();
            varNode.getParent().addChildBefore(
                IR.exprResult(requireCall).srcrefTree(requireCall),
                varNode);
          }
        });
  }

  private class FindMutatedExports extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case NAME:
          Scope scope = t.getScope();
          if (NodeUtil.isLValue(n) && !scope.getClosestHoistScope().isModuleScope()) {
            Collection<NameNodePair> pairs = exportsByLocalName.get(n.getString());
            if (pairs != null) {
              Var var = scope.getVar(n.getString());
              // A var declared in the module scope with the same name as an export must be the
              // export. And we know we're setting it in a function scope, so this cannot be the
              // declaration itself. We must be mutating.
              if (var != null && var.getScope().isModuleScope()) {
                for (NameNodePair pair : pairs) {
                  pair.mutated = true;
                }
              }
            }
          }
          break;
        default:
          break;
      }
    }
  }

  /**
   * Traverses a node tree and
   *
   * <ol>
   *   <li>Appends a suffix to all global variable names defined in this module.
   *   <li>Changes references to imported values to be property accesses on the
   *    imported module object.
   * </ol>
   */
  private class RenameGlobalVars extends AbstractPostOrderCallback {
    private final String suffix;

    RenameGlobalVars(String suffix) {
      this.suffix = suffix;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        for (Node typeNode : info.getTypeNodes()) {
          fixTypeNode(t, typeNode);
        }
      }

      if (n.isName()) {
        String name = n.getString();
        if (suffix.equals(name)) {
          // TODO(moz): Investigate whether we need to return early in this unlikely situation.
          return;
        }

        Var var = t.getScope().getVar(name);
        if (var != null && var.isGlobal()) {
          // Avoid polluting the global namespace.
          String newName = name + "$$" + suffix;
          n.setString(newName);
          n.setOriginalName(name);
          t.reportCodeChange(n);
        } else if (var == null && importMap.containsKey(name)) {
          // Change to property access on the imported module object.
          if (parent.isCall() && parent.getFirstChild() == n) {
            parent.putBooleanProp(Node.FREE_CALL, false);
          }

          ModuleOriginalNamePair pair = importMap.get(name);
          boolean isImportStar = pair.originalName.isEmpty();
          Node moduleAccess = NodeUtil.newQName(compiler, pair.module);

          maybeAddAliasToSymbolTable(n, t.getSourceName());
          if (isImportStar) {
            n.replaceWith(moduleAccess.useSourceInfoIfMissingFromForTree(n));
          } else {
            n.replaceWith(
                IR.getprop(moduleAccess, IR.string(pair.originalName))
                    .useSourceInfoIfMissingFromForTree(n));
            t.reportCodeChange(moduleAccess);
          }
        }
      }
    }

    /**
     * Replace type name references. Change short names to fully qualified names
     * with namespace prefixes. Eg: {Foo} becomes {module$test.Foo}.
     */
    private void fixTypeNode(NodeTraversal t, Node typeNode) {
      if (typeNode.isString()) {
        String name = typeNode.getString();
        if (ModuleLoader.isPathIdentifier(name)) {
          int lastSlash = name.lastIndexOf('/');
          int endIndex = name.indexOf('.', lastSlash);
          String localTypeName = null;
          if (endIndex == -1) {
            endIndex = name.length();
          } else {
            localTypeName = name.substring(endIndex);
          }

          String moduleName = name.substring(0, endIndex);
          ModuleLoader.ModulePath path =
              t.getInput()
                  .getPath()
                  .resolveJsModule(
                      moduleName,
                      typeNode.getSourceFileName(),
                      typeNode.getLineno(),
                      typeNode.getCharno());

          if (path == null) {
            path = t.getInput().getPath().resolveModuleAsPath(moduleName);
          }
          String globalModuleName = path.toModuleName();

          maybeSetNewName(
              t,
              typeNode,
              name,
              localTypeName == null ? globalModuleName : globalModuleName + localTypeName);
        } else {
          List<String> splitted = Splitter.on('.').limit(2).splitToList(name);
          String baseName = splitted.get(0);
          String rest = "";
          if (splitted.size() == 2) {
            rest = "." + splitted.get(1);
          }
          Var var = t.getScope().getVar(baseName);
          if (var != null && var.isGlobal()) {
            maybeSetNewName(t, typeNode, name, baseName + "$$" + suffix + rest);
          } else if (var == null && importMap.containsKey(baseName)) {
            ModuleOriginalNamePair pair = importMap.get(baseName);
            maybeAddAliasToSymbolTable(typeNode, t.getSourceName());
            if (pair.originalName.isEmpty()) {
              maybeSetNewName(t, typeNode, name, pair.module + rest);
            } else {
              maybeSetNewName(t, typeNode, name, baseName + "$$" + pair.module + rest);
            }
          }
          typeNode.setOriginalName(name);
        }
      }

      for (Node child = typeNode.getFirstChild(); child != null; child = child.getNext()) {
        fixTypeNode(t, child);
      }
    }

    private void maybeSetNewName(NodeTraversal t, Node node, String name, String newName) {
      if (!name.equals(newName)) {
        node.setString(newName);
        node.setOriginalName(name);
        t.reportCodeChange();
      }
    }
  }

  /**
   * Add alias nodes to the symbol table as they going to be removed by rewriter. Example aliases:
   *
   * <pre>
   *   import * as foo from './foo';
   *   import {doBar} from './bar';
   *
   *   console.log(doBar);
   * </pre>
   *
   * @param n Alias node. In the example above alias nodes are foo, doBar and doBar.
   * @param module Name of the module currently being processed.
   */
  private void maybeAddAliasToSymbolTable(Node n, String module) {
    if (preprocessorSymbolTable == null) {
      return;
    }
    n.putBooleanProp(Node.MODULE_ALIAS, true);
    // Alias can be used in js types. Types have node type STRING and not NAME so we have to
    // use their name as string.
    String nodeName =
        n.isString() || n.isImportStar()
            ? n.getString()
            : preprocessorSymbolTable.getQualifiedName(n);
    // We need to include module as part of the name because aliases are local to current module.
    // Aliases with the same name from different module should be completely different entities.
    String name = "alias_" + module + "_" + nodeName;
    preprocessorSymbolTable.addReference(n, name);
  }

  /**
   * Add reference to a file that current module imports. Example:
   *
   * <pre>
   * import * from '../some/file.js';
   * </pre>
   *
   * @param importNode String node from the import statement that references imported file. In the
   *     example above it is the '../some/file.js' STRING node.
   * @param importedFilePath Absolute path to the imported file. In the example above it can be
   *     myproject/folder/some/file.js
   */
  private void maybeAddImportedFileReferenceToSymbolTable(
      Node importNode, String importedFilePath) {
    if (preprocessorSymbolTable == null) {
      return;
    }

    // If this if the first import that mentions importedFilePath then we need to create a SCRIPT
    // node for the imported file.
    if (preprocessorSymbolTable.getSlot(importedFilePath) == null) {
      Node scriptNode = compiler.getScriptNode(importedFilePath);
      if (scriptNode != null) {
        preprocessorSymbolTable.addReference(scriptNode, importedFilePath);
      }
    }

    preprocessorSymbolTable.addReference(importNode, importedFilePath);
  }

  private static class ModuleOriginalNamePair {
    private final String module;
    private final String originalName;

    private ModuleOriginalNamePair(String module, String originalName) {
      this.module = module;
      this.originalName = originalName;
    }

    @Override
    public String toString() {
      return "(" + module + ", " + originalName + ")";
    }
  }

  private static class NameNodePair {
    final String exportedName;
    final Node nodeForSourceInfo;
    boolean mutated;

    private NameNodePair(String exportedName, Node nodeForSourceInfo) {
      this.exportedName = exportedName;
      this.nodeForSourceInfo = nodeForSourceInfo;
      mutated = false;
    }

    @Override
    public String toString() {
      return "(" + exportedName + ", " + nodeForSourceInfo + ")";
    }
  }
}
