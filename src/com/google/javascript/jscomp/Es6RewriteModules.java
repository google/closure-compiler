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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_SCOPE_ERROR;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_DESTRUCTURING_FORWARD_DECLARE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_FORWARD_DECLARE_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_CALL_SCOPE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_REQUIRE_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_REQUIRE_TYPE_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MODULE_USES_GOOG_MODULE_GET;
import static com.google.javascript.jscomp.ClosureRewriteModule.ILLEGAL_MODULE_RENAMING_CONFLICT;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature.MODULES;

import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.javascript.jscomp.ModuleRenaming.GlobalizedModuleName;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.modules.Binding;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
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
 * <p>Also rewrites any goog.{require,requireType,forwardDeclare,goog.module.get} calls that are
 * either in an ES module or of an ES module using goog.declareModuleId.
 */
public final class Es6RewriteModules implements CompilerPass, NodeTraversal.Callback {
  static final DiagnosticType LHS_OF_GOOG_REQUIRE_MUST_BE_CONST =
      DiagnosticType.error(
          "JSC_LHS_OF_GOOG_REQUIRE_MUST_BE_CONST",
          "The left side of a goog.require() or goog.requireType() "
              + "must use ''const'' (not ''let'' or ''var'')");

  static final DiagnosticType REQUIRE_TYPE_FOR_ES6_SHOULD_BE_CONST =
      DiagnosticType.error(
          "JSC_REQUIRE_TYPE_FOR_ES6_SHOULD_BE_CONST",
          "goog.requireType alias for ES6 module should be const.");

  static final DiagnosticType FORWARD_DECLARE_FOR_ES6_SHOULD_BE_CONST =
      DiagnosticType.error(
          "JSC_FORWARD_DECLARE_FOR_ES6_SHOULD_BE_CONST",
          "goog.forwardDeclare alias for ES6 module should be const.");

  static final DiagnosticType SHOULD_IMPORT_ES6_MODULE =
      DiagnosticType.warning(
          "JSC_SHOULD_IMPORT_ES6_MODULE",
          "ES6 modules should import other ES6 modules rather than goog.require them.");

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final JSType unknownType;
  private static final Splitter DOT_SPLITTER = Splitter.on(".");

  @Nullable private final PreprocessorSymbolTable preprocessorSymbolTable;

  /**
   * Local variable names that were goog.require'd to qualified name we need to line.
   *
   * <p>We need to inline all required names since there are certain well-known Closure symbols
   * (like goog.asserts) that later stages of the compiler check for and cannot handle aliases.
   *
   * <p>We use this to rewrite something like:
   *
   * <pre>
   *   import {x} from '';
   *   const {assert} = goog.require('goog.asserts');
   *   assert(x);
   * </pre>
   *
   * To:
   *
   * <pre>
   *   import {x} from '';
   *   goog.asserts.assert(x);
   * </pre>
   *
   * Because if we used an alias like below the assertion would not be recognized:
   *
   * <pre>
   *   import {x} from '';
   *   const {assert} = goog.asserts;
   *   assert(x);
   * </pre>
   */
  // TODO(johnplaisted): This is actually incorrect if the require'd thing is mutated. But we need
  // it so that things like goog.asserts work. Mutated closure symbols are a lot rarer than needing
  // to use asserts and the like. Until there's a better solution to finding aliases of well known
  // symbols we have to inline anything that is require'd.
  private Map<String, GlobalizedModuleName> namesToInlineByAlias;

  private Set<String> typedefs;

  private final ModuleMetadataMap moduleMetadataMap;
  private final ModuleMap moduleMap;
  private final TypedScope globalTypedScope;

  /**
   * Creates a new Es6RewriteModules instance which can be used to rewrite ES6 modules to a
   * concatenable form.
   */
  Es6RewriteModules(
      AbstractCompiler compiler,
      ModuleMetadataMap moduleMetadataMap,
      ModuleMap moduleMap,
      @Nullable PreprocessorSymbolTable preprocessorSymbolTable,
      @Nullable TypedScope globalTypedScope) {
    checkNotNull(moduleMetadataMap);
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.moduleMetadataMap = moduleMetadataMap;
    this.moduleMap = moduleMap;
    this.preprocessorSymbolTable = preprocessorSymbolTable;
    this.globalTypedScope = globalTypedScope;
    this.unknownType = compiler.getTypeRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE);
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
    NodeTraversal.traverseRoots(compiler, this, externs, root);
    compiler.setFeatureSet(compiler.getFeatureSet().without(MODULES));
    // This pass may add getters properties on module objects.
    GatherGetterAndSetterProperties.update(compiler, externs, root);
  }

  private void clearPerFileState() {
    this.typedefs = new HashSet<>();
    this.namesToInlineByAlias = new HashMap<>();
  }

  /**
   * Checks for goog.require, goog.requireType, goog.module.get and goog.forwardDeclare calls that
   * are meant to import ES6 modules and rewrites them.
   */
  private class RewriteRequiresForEs6Modules extends AbstractPostOrderCallback {
    private boolean transpiled = false;
    // An (s, old, new) entry indicates that occurrences of `old` in scope `s` should be rewritten
    // as `new`. This is used to rewrite namespaces that appear in calls to goog.requireType and
    // goog.forwardDeclare.
    private Table<Node, String, String> renameTable;

    void rewrite(Node scriptNode) {
      transpiled = false;
      renameTable = HashBasedTable.create();
      NodeTraversal.traverse(compiler, scriptNode, this);

      if (transpiled) {
        scriptNode.putBooleanProp(Node.TRANSPILED, true);
      }

      if (!renameTable.isEmpty()) {
        NodeTraversal.traverse(
            compiler, scriptNode, new Es6RenameReferences(renameTable, /* typesOnly= */ true));
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isCall()) {
        return;
      }

      boolean isRequire = n.getFirstChild().matchesQualifiedName("goog.require");
      boolean isRequireType = n.getFirstChild().matchesQualifiedName("goog.requireType");
      boolean isGet = n.getFirstChild().matchesQualifiedName("goog.module.get");
      boolean isForwardDeclare = n.getFirstChild().matchesQualifiedName("goog.forwardDeclare");

      if (!isRequire && !isRequireType && !isGet && !isForwardDeclare) {
        return;
      }

      if (!n.hasTwoChildren() || !n.getLastChild().isStringLit()) {
        if (isRequire) {
          t.report(n, INVALID_REQUIRE_NAMESPACE);
        } else if (isRequireType) {
          t.report(n, INVALID_REQUIRE_TYPE_NAMESPACE);
        } else if (isGet) {
          t.report(n, INVALID_GET_NAMESPACE);
        } else {
          t.report(n, INVALID_FORWARD_DECLARE_NAMESPACE);
        }
        return;
      }

      String name = n.getLastChild().getString();
      ModuleMetadata moduleMetadata = moduleMetadataMap.getModulesByGoogNamespace().get(name);

      if (moduleMetadata == null || !moduleMetadata.isEs6Module()) {
        return;
      }

      // TODO(johnplaisted): Once we have an alternative to forwardDeclare / requireType that
      // doesn't require Closure Library warn about those too.
      // TODO(johnplaisted): Once we have import() support warn about goog.module.get.
      if (isRequire) {
        ModuleMetadata currentModuleMetadata =
            moduleMetadataMap.getModulesByPath().get(t.getInput().getPath().toString());
        if (currentModuleMetadata != null && currentModuleMetadata.isEs6Module()) {
          t.report(n, SHOULD_IMPORT_ES6_MODULE);
        }
      }

      if (isGet && t.inGlobalHoistScope()) {
        t.report(n, INVALID_GET_CALL_SCOPE);
        return;
      }

      Node statementNode = NodeUtil.getEnclosingStatement(n);
      boolean importHasAlias = NodeUtil.isNameDeclaration(statementNode);
      if (importHasAlias) {
        if (statementNode.getFirstChild().isDestructuringLhs()) {
          if (isForwardDeclare) {
            // const {a, c:b} = goog.forwardDeclare('an.es6.namespace');
            t.report(n, INVALID_DESTRUCTURING_FORWARD_DECLARE);
            return;
          }
          if (isRequireType) {
            if (!statementNode.isConst()) {
              t.report(statementNode, REQUIRE_TYPE_FOR_ES6_SHOULD_BE_CONST);
              return;
            }
            // const {a, c:b} = goog.requireType('an.es6.namespace');
            for (Node child = statementNode.getFirstFirstChild().getFirstChild();
                child != null;
                child = child.getNext()) {
              checkState(child.isStringKey());
              checkState(child.getFirstChild().isName());
              renameTable.put(
                  t.getScopeRoot(),
                  child.getFirstChild().getString(),
                  ModuleRenaming.getGlobalName(moduleMetadata, name)
                      .getprop(child.getString())
                      .join());
            }
          } else {
            // Work around a bug in the type checker where destructuring can create
            // too many layers of aliases and confuse the type checker. b/112061124.

            // const {a, c:b} = goog.require('an.es6.namespace');
            // const a = module$es6.a;
            // const b = module$es6.c;
            for (Node child = statementNode.getFirstFirstChild().getFirstChild();
                child != null;
                child = child.getNext()) {
              checkState(child.isStringKey());
              checkState(child.getFirstChild().isName());
              GlobalizedModuleName globalName =
                  getGlobalNameAndType(
                          moduleMetadata, name, /* isFromMissingModuleOrProvide= */ false)
                      .getprop(child.getString());
              Node constNode =
                  astFactory.createSingleConstNameDeclaration(
                      child.getFirstChild().getString(), globalName.toQname(astFactory));
              constNode.srcrefTree(child);
              constNode.insertBefore(statementNode);
            }
          }
          statementNode.detach();
          t.reportCodeChange();
        } else {
          if (isForwardDeclare || isRequireType) {
            if (!statementNode.isConst()) {
              DiagnosticType diagnostic =
                  isForwardDeclare
                      ? FORWARD_DECLARE_FOR_ES6_SHOULD_BE_CONST
                      : REQUIRE_TYPE_FOR_ES6_SHOULD_BE_CONST;
              t.report(statementNode, diagnostic);
              return;
            }
            // const namespace = goog.forwardDeclare('an.es6.namespace');
            // const namespace = goog.requireType('an.es6.namespace');
            renameTable.put(
                t.getScopeRoot(),
                statementNode.getFirstChild().getString(),
                ModuleRenaming.getGlobalName(moduleMetadata, name).join());
            statementNode.detach();
            t.reportCodeChange();
          } else {
            // const module = goog.require('an.es6.namespace');
            // const module = module$es6;
            n.replaceWith(
                astFactory
                    .createName(
                        ModuleRenaming.getGlobalName(moduleMetadata, name).getRoot(), type(n))
                    .srcrefTree(n));
            t.reportCodeChange();
          }
        }
      } else {
        if (isForwardDeclare || isRequireType) {
          // goog.forwardDeclare('an.es6.namespace')
          // goog.requireType('an.es6.namespace')
          renameTable.put(
              t.getScopeRoot(), name, ModuleRenaming.getGlobalName(moduleMetadata, name).join());
          statementNode.detach();
        } else {
          // goog.require('an.es6.namespace')
          if (statementNode.isExprResult() && statementNode.getFirstChild() == n) {
            statementNode.detach();
          } else {
            n.replaceWith(
                astFactory
                    .createName(
                        ModuleRenaming.getGlobalName(moduleMetadata, name).getRoot(), type(n))
                    .srcrefTree(n));
          }
        }
        t.reportCodeChange();
      }

      transpiled = true;
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isScript()) {
      // Trigger creation of the global scope before inserting any synthetic code.
      nodeTraversal.getScope();
      new RewriteRequiresForEs6Modules().rewrite(n);
      if (isEs6ModuleRoot(n)) {
        clearPerFileState();
        n.putBooleanProp(Node.TRANSPILED, true);
      } else {
        return false;
      }
    }
    return true;
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
      visitScript(t, n);
    } else if (n.isCall()) {
      // TODO(johnplaisted): Consolidate on declareModuleId.
      if (n.getFirstChild().matchesQualifiedName("goog.declareModuleId")) {
        n.getParent().detach();
      }
    } else if (n.isImportMeta()) {
      // We're choosing to not "support" import.meta because currently all the outputs from the
      // compiler are scripts and support for import.meta (only works in modules) would be
      // meaningless
      t.report(n, Es6ToEs3Util.CANNOT_CONVERT, "import.meta");
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
    String importName = importDecl.getLastChild().getString();
    boolean isNamespaceImport = importName.startsWith("goog:");
    if (isNamespaceImport) {
      // Allow importing Closure namespace objects (e.g. from goog.provide or goog.module) as
      //   import ... from 'goog:my.ns.Object'.
      String namespace = importName.substring("goog:".length());
      ModuleMetadata m = moduleMetadataMap.getModulesByGoogNamespace().get(namespace);

      if (m == null) {
        t.report(importDecl, MISSING_MODULE_OR_PROVIDE, namespace);
      } else {
        checkState(m.isEs6Module() || m.isGoogModule() || m.isGoogProvide());
      }
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

      maybeAddImportedFileReferenceToSymbolTable(importDecl.getLastChild(), modulePath.toString());
      // TODO(johnplaisted): Use ModuleMetadata to ensure the path required is CommonJs or ES6 and
      // if not give a better error.
    }

    for (Node child = importDecl.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isImportSpecs()) {
        for (Node grandChild = child.getFirstChild();
            grandChild != null;
            grandChild = grandChild.getNext()) {
          maybeAddAliasToSymbolTable(grandChild.getFirstChild(), t.getSourceName());
          checkState(grandChild.hasTwoChildren());
        }
      } else if (child.isImportStar()) {
        // import * as ns from "mod"
        maybeAddAliasToSymbolTable(child, t.getSourceName());
      }
    }

    importDecl.detach();
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
        export.replaceWith(decl);
      } else {
        Node var =
            astFactory.createSingleVarNameDeclaration(
                ModuleRenaming.DEFAULT_EXPORT_VAR_PREFIX, export.removeFirstChild());
        var.setJSDocInfo(child.getJSDocInfo());
        child.setJSDocInfo(null);
        var.srcrefTreeIfMissing(export);
        export.replaceWith(var);
      }
      t.reportCodeChange();
    } else if (export.getBooleanProp(Node.EXPORT_ALL_FROM)
        || export.hasTwoChildren()
        || export.getFirstChild().getToken() == Token.EXPORT_SPECS) {
      //   export * from 'moduleIdentifier';
      //   export {x, y as z} from 'moduleIdentifier';
      //   export {Foo};
      export.detach();
      t.reportCodeChange();
    } else {
      visitExportDeclaration(t, export);
    }
  }

  private void visitExportNameDeclaration(Node declaration) {
    //    export var Foo;
    //    export let {a, b:[c,d]} = {};
    List<Node> lhsNodes = NodeUtil.findLhsNodesInNode(declaration);

    for (Node lhs : lhsNodes) {
      checkState(lhs.isName());
      String name = lhs.getString();

      if (declaration.getJSDocInfo() != null && declaration.getJSDocInfo().hasTypedefType()) {
        typedefs.add(name);
      }
    }
  }

  private void visitExportDeclaration(NodeTraversal t, Node export) {
    //    export var Foo;
    //    export function Foo() {}
    // etc.
    Node declaration = export.getFirstChild();

    if (NodeUtil.isNameDeclaration(declaration)) {
      visitExportNameDeclaration(declaration);
    }

    export.replaceWith(declaration.detach());
    t.reportCodeChange();
  }

  private void inlineModuleToGlobalScope(Node moduleNode) {
    checkState(moduleNode.isModuleBody());
    Node scriptNode = moduleNode.getParent();
    moduleNode.detach();
    scriptNode.addChildrenToFront(moduleNode.removeChildren());
  }

  private void visitScript(NodeTraversal t, Node script) {
    final Node moduleBody = script.getFirstChild();
    // TypedScopeCreator sets the module object type on the MODULE_BODY during type checking.
    final AstFactory.Type moduleObjectType = type(moduleBody);
    inlineModuleToGlobalScope(moduleBody);

    ClosureRewriteModule.checkAndSetStrictModeDirective(t, script);

    Module thisModule = moduleMap.getModule(t.getInput().getPath());
    QualifiedName qualifiedName =
        ModuleRenaming.getGlobalName(thisModule.metadata(), /* googNamespace= */ null);
    checkState(qualifiedName.isSimple(), "Unexpected qualified name %s", qualifiedName);
    String moduleName = qualifiedName.getRoot();

    Node moduleVar = createExportsObject(moduleName, t, script, moduleObjectType);

    // Rename vars to not conflict in global scope.
    NodeTraversal.traverse(compiler, script, new RenameGlobalVars(thisModule));

    // Rename the exports object to something we can reference later.
    moduleVar.getFirstChild().setString(moduleName);
    moduleVar.makeNonIndexableRecursive();
    declareGlobalVariable(moduleVar.getFirstChild(), t);

    // rewriteRequires is here (rather than being part of the main visit() method, because we only
    // want to rewrite the requires if this is an ES6 module. Note that we also want to do this
    // AFTER renaming all module scoped vars in the event that something that is goog.require'd is
    // a global, unqualified name (e.g. if "goog.provide('foo')" exists, we don't want to rewrite
    // "const foo = goog.require('foo')" to "const foo = foo". If we rewrite our module scoped names
    // first then we'll rewrite to "const foo$module$fudge = goog.require('foo')", then to
    // "const foo$module$fudge = foo".
    rewriteRequires(script);

    t.reportCodeChange();
  }

  private Node createExportsObject(
      String moduleName, NodeTraversal t, Node script, AstFactory.Type moduleObjectType) {
    Node moduleObject = astFactory.createObjectLit(moduleObjectType);
    // Going to get renamed by RenameGlobalVars, so the name we choose here doesn't matter as long
    // as it doesn't collide with an existing variable. (We can't use `moduleName` since then
    // RenameGlobalVars will rename all references to `moduleName` incorrectly). We'll fix the name
    // in visitScript after the global renaming to ensure it has a name that is deterministic from
    // the path.
    //
    // So after this method we'll have:
    // var $jscomp$tmp$exports$module$name = {};
    // module$name.exportName = localName;
    //
    // After RenameGlobalVars:
    // var $jscomp$tmp$exports$module$nameglobalized = {};
    // module$name.exportName = localName$globalized;
    //
    // After visitScript:
    // var module$name = {};
    // module$name.exportName = localName$globalized;
    Node moduleVar =
        astFactory.createSingleVarNameDeclaration("$jscomp$tmp$exports$module$name", moduleObject);
    moduleVar.getFirstChild().putBooleanProp(Node.MODULE_EXPORT, true);
    // TODO(b/144593112): Stop adding JSDoc when this pass moves to always be after typechecking.
    JSDocInfo.Builder infoBuilder = JSDocInfo.builder();
    infoBuilder.recordConstancy();
    moduleVar.setJSDocInfo(infoBuilder.build());
    moduleVar.getFirstChild().setDeclaredConstantVar(true);
    script.addChildToBack(moduleVar.srcrefTreeIfMissing(script));

    Module thisModule = moduleMap.getModule(t.getInput().getPath());

    for (Map.Entry<String, Binding> entry : thisModule.namespace().entrySet()) {
      String exportedName = entry.getKey();
      Binding binding = entry.getValue();
      Node nodeForSourceInfo = binding.sourceNode();
      boolean mutated = binding.isMutated();
      QualifiedName boundVariableQualifiedName = ModuleRenaming.getGlobalName(binding);
      checkState(
          boundVariableQualifiedName.isSimple(),
          "unexpected qualified name: %s",
          boundVariableQualifiedName);
      String boundVariableName = boundVariableQualifiedName.getRoot();

      Node getProp =
          astFactory.createGetPropWithoutColor(
              astFactory.createName(moduleName, moduleObjectType), exportedName);
      getProp.putBooleanProp(Node.MODULE_EXPORT, true);

      if (typedefs.contains(exportedName)) {
        // /** @typedef {foo} */
        // moduleName.foo;
        JSDocInfo.Builder builder = JSDocInfo.builder().parseDocumentation();
        JSTypeExpression typeExpr =
            new JSTypeExpression(
                astFactory.createString(exportedName).srcref(nodeForSourceInfo),
                script.getSourceFileName());
        builder.recordTypedef(typeExpr);
        JSDocInfo info = builder.build();
        getProp.setJSDocInfo(info);
        Node exprResult = astFactory.exprResult(getProp).srcrefTreeIfMissing(nodeForSourceInfo);
        script.addChildToBack(exprResult);
      } else if (mutated) {
        final Node globalExportName = astFactory.createName(boundVariableName, type(getProp));
        addGetterExport(script, nodeForSourceInfo, moduleObject, exportedName, globalExportName);
        NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.GETTER, compiler);
      } else {
        // Avoid the extra complexity of using getters when the property isn't mutated.
        // exports.foo = foo;
        Node assign =
            astFactory.createAssign(
                getProp, astFactory.createName(boundVariableName, type(getProp)));
        // TODO(b/144593112): Stop adding JSDoc when this pass moves to always be after typechecking
        JSDocInfo.Builder builder = JSDocInfo.builder().parseDocumentation();
        builder.recordConstancy();
        JSDocInfo info = builder.build();
        assign.setJSDocInfo(info);
        script.addChildToBack(astFactory.exprResult(assign).srcrefTreeIfMissing(nodeForSourceInfo));
      }
    }

    return moduleVar;
  }

  private void addGetterExport(
      Node script, Node forSourceInfo, Node objLit, String exportedName, Node value) {
    Node getter = astFactory.createGetterDef(exportedName, value);
    getter.putBooleanProp(Node.MODULE_EXPORT, true);
    objLit.addChildToBack(getter);

    if (!astFactory.isAddingTypes()) {
      // TODO(b/143904518): Remove this code when this pass is permanently moved after type checking
      // Type checker doesn't infer getters so mark the return as unknown.
      // { /** @return {?} */ get foo() { return foo; } }
      JSDocInfo.Builder builder = JSDocInfo.builder().parseDocumentation();
      builder.recordReturnType(
          new JSTypeExpression(
              new Node(Token.QMARK).srcref(forSourceInfo), script.getSourceFileName()));
      getter.setJSDocInfo(builder.build());
    } else {
      // For a property typed as number, synthesize a type `function(): number`.
      getter.setJSType(compiler.getTypeRegistry().createFunctionType(value.getJSType()));
    }

    getter.srcrefTreeIfMissing(forSourceInfo);
    compiler.reportChangeToEnclosingScope(getter.getFirstChild().getLastChild());
    compiler.reportChangeToEnclosingScope(getter);
  }

  private void rewriteRequires(Node script) {
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(
            (NodeTraversal t, Node n, Node parent) -> {
              if (n.isCall()) {
                Node fn = n.getFirstChild();
                if (fn.matchesQualifiedName("goog.require")
                    || fn.matchesQualifiedName("goog.requireType")) {
                  // TODO(tjgq): This will rewrite both type references and code references. For
                  // goog.requireType, the latter are potentially broken because the symbols aren't
                  // guaranteed to be available at run time. A separate pass needs to be added to
                  // detect these incorrect uses of goog.requireType.
                  visitRequireOrGet(t, n, parent, /* isRequire= */ true);
                } else if (fn.matchesQualifiedName("goog.module.get")) {
                  visitGoogModuleGet(t, n, parent);
                }
              }
            })
        .traverse(script);
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(
            (NodeTraversal t, Node n, Node parent) -> {
              JSDocInfo info = n.getJSDocInfo();
              if (info != null) {
                for (Node typeNode : info.getTypeNodes()) {
                  inlineAliasedTypes(t, typeNode);
                }
              }

              if (n.isName() && namesToInlineByAlias.containsKey(n.getString())) {
                Var v = t.getScope().getVar(n.getString());
                if (v == null || v.getNameNode() != n) {
                  GlobalizedModuleName replacementName = namesToInlineByAlias.get(n.getString());
                  Node replacement = replacementName.toQname(astFactory).srcrefTree(n);
                  n.replaceWith(replacement);
                }
              }
            })
        .traverse(script);
  }

  private void inlineAliasedTypes(NodeTraversal t, Node typeNode) {
    if (typeNode.isStringLit()) {
      String name = typeNode.getString();
      List<String> split = DOT_SPLITTER.limit(2).splitToList(name);

      // We've already removed the alias.
      if (t.getScope().getVar(split.get(0)) == null) {
        GlobalizedModuleName replacement = namesToInlineByAlias.get(split.get(0));
        if (replacement != null) {
          String rest = "";
          if (split.size() == 2) {
            rest = "." + split.get(1);
          }
          typeNode.setOriginalName(name);
          typeNode.setString(replacement.aliasName().join() + rest);
          t.reportCodeChange();
        }
      }
    }
    for (Node child = typeNode.getFirstChild(); child != null; child = child.getNext()) {
      inlineAliasedTypes(t, child);
    }
  }

  private void visitGoogModuleGet(NodeTraversal t, Node getCall, Node parent) {
    if (!getCall.hasTwoChildren() || !getCall.getLastChild().isStringLit()) {
      t.report(getCall, INVALID_GET_NAMESPACE);
      return;
    }

    // Module has already been turned into a script at this point.
    if (t.inGlobalHoistScope()) {
      t.report(getCall, MODULE_USES_GOOG_MODULE_GET);
      return;
    }

    visitRequireOrGet(t, getCall, parent, /* isRequire= */ false);
  }

  /**
   * Gets some made-up metadata for the given Closure namespace.
   *
   * <p>This is used when the namespace is not part of the input so that this pass can be fault
   * tolerant and still rewrite to something. Some tools don't care about rewriting correctly and
   * just want the type information of this module (e.g. clutz).
   */
  private ModuleMetadata getFallbackMetadataForNamespace(String namespace) {
    // Assume a provide'd file to be consistent with goog.module rewriting.
    ModuleMetadata.Builder builder =
        ModuleMetadata.builder()
            .moduleType(ModuleMetadataMap.ModuleType.GOOG_PROVIDE)
            .usesClosure(true)
            .isTestOnly(false);
    builder.googNamespacesBuilder().add(namespace);
    return builder.build();
  }

  private void visitRequireOrGet(
      NodeTraversal t, Node requireCall, Node parent, boolean isRequire) {
    if (!requireCall.hasTwoChildren() || !requireCall.getLastChild().isStringLit()) {
      t.report(requireCall, INVALID_REQUIRE_NAMESPACE);
      return;
    }

    // Module has already been turned into a script at this point.
    if (isRequire && !t.getScope().isGlobal()) {
      t.report(requireCall, INVALID_CLOSURE_CALL_SCOPE_ERROR);
      return;
    }

    String namespace = requireCall.getLastChild().getString();

    boolean isStoredInDeclaration = NodeUtil.isDeclaration(parent.getParent());

    if (isStoredInDeclaration && !parent.getParent().isConst()) {
      compiler.report(JSError.make(parent.getParent(), LHS_OF_GOOG_REQUIRE_MUST_BE_CONST));
    }

    ModuleMetadata m = moduleMetadataMap.getModulesByGoogNamespace().get(namespace);

    boolean isFromFallbackMetadata = m == null;
    if (isFromFallbackMetadata) {
      t.report(requireCall, MISSING_MODULE_OR_PROVIDE, namespace);
      m = getFallbackMetadataForNamespace(namespace);
    }

    if (isStoredInDeclaration) {
      if (isRequire) {
        Node toDetach;

        if (parent.isDestructuringLhs()) {
          checkState(parent.getFirstChild().isObjectPattern());
          toDetach = parent.getParent();
          for (Node child = parent.getFirstFirstChild(); child != null; child = child.getNext()) {
            checkState(child.isStringKey() && child.getFirstChild().isName(), child);
            GlobalizedModuleName rep =
                getGlobalNameAndType(m, namespace, isFromFallbackMetadata)
                    .getprop(child.getString());
            namesToInlineByAlias.put(child.getFirstChild().getString(), rep);
          }
        } else if (parent.isName()) {
          GlobalizedModuleName alias = getGlobalNameAndType(m, namespace, isFromFallbackMetadata);
          namesToInlineByAlias.put(parent.getString(), alias);

          toDetach = parent.getParent();
        } else {
          checkState(parent.isExprResult());
          toDetach = parent;
        }
        toDetach.detach();
      } else {
        GlobalizedModuleName name = getGlobalNameAndType(m, namespace, isFromFallbackMetadata);
        Node replacement = name.toQname(astFactory).srcrefTree(requireCall);
        requireCall.replaceWith(replacement);
      }
    } else {
      checkState(requireCall.getParent().isExprResult());
      requireCall.getParent().detach();
    }
  }

  /**
   * Looks up information about the globalized name and type of a given module
   *
   * @param metadata Required. The metadata for the module or provide being imported.
   * @param googNamespace Optional.
   * @param isFromMissingModuleOrProvide Whether the metadata is synthesized fallback metadata
   */
  private GlobalizedModuleName getGlobalNameAndType(
      ModuleMetadata metadata,
      @Nullable String googNamespace,
      boolean isFromMissingModuleOrProvide) {
    if (isFromMissingModuleOrProvide) {
      // The missing namespace presumably does not have a corresponding type defined in the scope.
      // Use the unknownType instead of asking ModuleRenaming to look up the type.
      QualifiedName globalName = ModuleRenaming.getGlobalName(metadata, googNamespace);
      return GlobalizedModuleName.create(globalName, unknownType);
    }
    return GlobalizedModuleName.create(metadata, googNamespace, globalTypedScope);
  }

  /**
   * Traverses a node tree and
   *
   * <ol>
   *   <li>Appends a suffix to all global variable names defined in this module.
   *   <li>Changes references to imported values to access the exported variable.
   * </ol>
   */
  private class RenameGlobalVars extends AbstractPostOrderCallback {
    private final Module thisModule;

    RenameGlobalVars(Module thisModule) {
      this.thisModule = thisModule;
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

        Var var = t.getScope().getVar(name);
        if (var != null && var.isGlobal()) {
          // Avoid polluting the global namespace.
          String newName =
              ModuleRenaming.getGlobalNameOfEsModuleLocalVariable(thisModule.metadata(), name)
                  .join();
          n.setString(newName);
          n.setOriginalName(name);
          t.reportCodeChange(n);
          if (NodeUtil.isDeclarationLValue(n)) {
            declareGlobalVariable(n, t);
          }
        } else if (var == null && thisModule.boundNames().containsKey(name)) {
          // Imports have been detached, so they won't show up in scope. Thus if we have a variable
          // not in scope that shares the name of an import it is the import.
          maybeAddAliasToSymbolTable(n, t.getSourceName());
          Binding binding = thisModule.boundNames().get(name);

          Node replacement = replace(n, binding);

          // `n.x()` may become `foo()`
          if (replacement.isName()
              && parent.isCall()
              && parent.getFirstChild() == n
              && parent.getBooleanProp(Node.FREE_CALL)) {
            parent.putBooleanProp(Node.FREE_CALL, true);
          }

          if (NodeUtil.isDeclarationLValue(n)) {
            declareGlobalVariable(n, t);
          }
          t.reportCodeChange();
        }
      }
    }

    /**
     * Replaces the reference to a given binding. See {@link
     * ModuleRenaming#getGlobalNameForJsDoc(ModuleMap, Binding, List)} for a JS Doc version.
     *
     * <p>For example:
     *
     * <pre>
     *   // bar
     *   export let baz = {qux: 0};
     * </pre>
     *
     * <pre>
     *   // foo
     *   import * as bar from 'bar';
     *   export {bar};
     * </pre>
     *
     * <pre>
     *   import * as foo from 'foo';
     *   use(foo.bar.baz.qux);
     * </pre>
     *
     * <p>Should call this method with the binding and node for {@code foo}. In this example any of
     * these properties could also be modules. This method will replace as much as the GETPROP as it
     * can with module exported variables. Meaning in the above example this would return something
     * like "baz$$module$bar.qux", whereas if this method were called for just "foo.bar" it would
     * return "module$bar", as it refers to a module object itself.
     *
     * @param n the node to replace
     * @param binding the binding nameNode is a reference to
     */
    private Node replace(Node n, Binding binding) {
      checkState(n.isName());

      while (binding.isModuleNamespace()
          && binding.metadata().isEs6Module()
          && n.getParent().isGetProp()) {
        String propertyName = n.getParent().getString();
        Module m = moduleMap.getModule(binding.metadata().path());
        if (m.namespace().containsKey(propertyName)) {
          binding = m.namespace().get(propertyName);
          n = n.getParent();
        } else {
          // This means someone referenced an invalid export on a module object. This should be an
          // error, so just rewrite and let the type checker complain later. It isn't a super clear
          // error, but we're working on type checking modules soon.
          break;
        }
      }

      QualifiedName globalName = ModuleRenaming.getGlobalName(binding);
      final Node newNode;
      if (!globalName.isSimple()) {
        String root = globalName.getRoot();
        newNode =
            // we might encounter a name not in the global scope when requiring a missing symbol.
            globalTypedScope != null && globalTypedScope.hasSlot(root)
                ? astFactory.createQName(globalTypedScope, globalName.join())
                : astFactory.createQNameWithUnknownType(globalName.join());
      } else {
        // Because this pass does not update the global scope with injected names, t.getScope()
        // will not contain a declaration for this global name. Fortunately, we already have the
        // JSType on the existing node to pass to AstFactory.
        newNode = astFactory.createName(globalName.getRoot(), type(n));
      }

      // For kythe: the new node only represents the last name it replaced, not all the names.
      // e.g. if we rewrite `a.b.c.d.e` to `x.d.e`, then `x` should map to `c`, not `a.b.c`.
      n.replaceWith(newNode);
      newNode.srcrefTree(n);
      newNode.setOriginalName(n.getString());
      return newNode;
    }

    /**
     * Replace type name references. Change short names to fully qualified names with namespace
     * prefixes. Eg: {Foo} becomes {module$test.Foo}.
     */
    private void fixTypeNode(NodeTraversal t, Node typeNode) {
      if (typeNode.isStringLit()) {
        Module thisModule = moduleMap.getModule(t.getInput().getPath());
        String name = typeNode.getString();
        List<String> splitted = DOT_SPLITTER.splitToList(name);
        String baseName = splitted.get(0);
        String rest = "";
        if (splitted.size() > 1) {
          rest = name.substring(baseName.length());
        }
        Var var = t.getScope().getVar(baseName);
        if (var != null && var.isGlobal()) {
          maybeSetNewName(
              t,
              typeNode,
              name,
              ModuleRenaming.getGlobalNameOfEsModuleLocalVariable(thisModule.metadata(), baseName)
                      .join()
                  + rest);
        } else if (var == null && thisModule.boundNames().containsKey(baseName)) {
          // Imports have been detached, so they won't show up in scope. Thus if we have a variable
          // not in scope that shares the name of an import it is the import.
          Binding binding = thisModule.boundNames().get(baseName);
          String globalName =
              ModuleRenaming.getGlobalNameForJsDoc(
                  moduleMap, binding, splitted.subList(1, splitted.size()));
          maybeSetNewName(t, typeNode, name, globalName);

          if (preprocessorSymbolTable != null) {
            // Jsdoc type node is a single STRING node that spans the whole type. For example
            // STRING node "bar.Foo". ES6 import rewrite replaces only "module"
            // part of the type: "bar.Foo" => "module$full$path$bar$Foo". We have to record
            // "bar" as alias.
            Node onlyBaseName = Node.newString(baseName).srcref(typeNode);
            onlyBaseName.setLength(baseName.length());
            maybeAddAliasToSymbolTable(onlyBaseName, t.getSourceName());
          }
        }

        typeNode.setOriginalName(name);
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
   * @param n Alias node. In the example above alias nodes are foo, doBar, and doBar.
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
        n.isStringLit() || n.isImportStar()
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
   * import * as qux from '../some/file.js';
   * </pre>
   *
   * <p>Will add a reference to file.js on the string node `'../some/file.js'`.
   *
   * @param importStringNode String node from the import statement that references imported file. In
   *     the example above it is the '../some/file.js' STRING node.
   * @param importedFilePath Absolute path to the imported file. In the example above it can be
   *     myproject/folder/some/file.js
   */
  private void maybeAddImportedFileReferenceToSymbolTable(
      Node importStringNode, String importedFilePath) {
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

    preprocessorSymbolTable.addReference(importStringNode, importedFilePath);
  }

  private void declareGlobalVariable(Node n, NodeTraversal t) {
    checkState(n.isName());
    if (!astFactory.isAddingTypes()) {
      return;
    }
    checkNotNull(this.globalTypedScope);

    String name = n.getString();
    if (this.globalTypedScope.hasOwnSlot(name)) {
      t.report(t.getCurrentScript(), ILLEGAL_MODULE_RENAMING_CONFLICT, name);
    } else {
      JSType type = checkNotNull(n.getJSType());
      this.globalTypedScope.declare(name, n, type, t.getInput(), false);
    }
  }
}
