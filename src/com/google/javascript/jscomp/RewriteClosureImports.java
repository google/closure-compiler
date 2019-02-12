/*
 * Copyright 2018 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_FORWARD_DECLARE;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.google.javascript.jscomp.NodeTraversal.AbstractModuleCallback;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleType;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;

/**
 * Rewrites all goog.require, goog.module.get, goog.forwardDeclare, and goog.requireType calls for
 * goog.provide, goog.module, and ES6 modules (that call goog.declareModuleId).
 */
final class RewriteClosureImports implements HotSwapCompilerPass {

  private static final Node GOOG_REQUIRE = IR.getprop(IR.name("goog"), IR.string("require"));
  private static final Node GOOG_MODULE_GET =
      IR.getprop(IR.getprop(IR.name("goog"), IR.string("module")), IR.string("get"));
  private static final Node GOOG_FORWARD_DECLARE =
      IR.getprop(IR.name("goog"), IR.string("forwardDeclare"));
  private static final Node GOOG_REQUIRE_TYPE =
      IR.getprop(IR.name("goog"), IR.string("requireType"));

  private final AbstractCompiler compiler;
  @Nullable private final PreprocessorSymbolTable preprocessorSymbolTable;
  private final ImmutableSet<ModuleType> typesToRewriteIn;
  private final Rewriter rewriter;

  /**
   * @param typesToRewriteIn A temporary set of module types to rewrite in. As this pass replaces
   *     the logic inside ES6RewriteModules, ClosureRewriteModules, and ProcessClosurePrimitives,
   *     this set should be expanded. Once it covers all module types it should be removed. This is
   *     how we will slowly and safely consolidate all logic to this pass. e.g. if the set is empty
   *     then this pass does nothing.
   */
  RewriteClosureImports(
      AbstractCompiler compiler,
      ModuleMetadataMap moduleMetadataMap,
      @Nullable PreprocessorSymbolTable preprocessorSymbolTable,
      ImmutableSet<ModuleType> typesToRewriteIn) {
    this.compiler = compiler;
    this.preprocessorSymbolTable = preprocessorSymbolTable;
    this.typesToRewriteIn = typesToRewriteIn;
    this.rewriter = new Rewriter(compiler, moduleMetadataMap);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    if (typesToRewriteIn.isEmpty()) {
      return;
    }

    NodeTraversal.traverse(compiler, scriptRoot, rewriter);
  }

  @Override
  public void process(Node externs, Node root) {
    if (typesToRewriteIn.isEmpty()) {
      return;
    }

    NodeTraversal.traverse(compiler, externs, rewriter);
    NodeTraversal.traverse(compiler, root, rewriter);
  }

  private class Rewriter extends AbstractModuleCallback {
    // Requires for Closure files need to be inlined if aliased. This behavior isn't really correct,
    // but is required as some Closure symbols need to be recognized by the compiler, like
    // goog.asserts. e.g.:
    //
    // const {assert} = goog.require('goog.asserts');
    // assert(false);
    //
    // Needs to be written to:
    //
    // goog.asserts.assert(false);
    //
    // As other stages of the compiler look for "goog.asserts.assert".
    // However we don't do this for ES modules due to mutable exports.
    // Note that doing this for types also allows type references to non-imported non-legacy
    // goog.module files - which we really shouldn't have allowed, but people are now relying on it.
    //
    // These tables are [old name, alias node, new name].
    private final Table<String, Node, String> variablesToInline = HashBasedTable.create();
    private final Table<String, Node, String> typesToInline = HashBasedTable.create();

    // Map from Closure ID to global name for imports that have no alias.
    private final Map<String, String> nonAliasedNamespaces = new HashMap<>();

    private final ModuleMetadataMap moduleMetadataMap;

    Rewriter(AbstractCompiler compiler, ModuleMetadataMap moduleMetadataMap) {
      super(compiler, moduleMetadataMap);
      this.moduleMetadataMap = moduleMetadataMap;
    }

    @Override
    protected void exitModule(ModuleMetadata oldModule, Node moduleScopeRoot) {
      variablesToInline.clear();
      typesToInline.clear();
      nonAliasedNamespaces.clear();
    }

    @Override
    public void visit(
        NodeTraversal t,
        Node n,
        @Nullable ModuleMetadata currentModule,
        @Nullable Node moduleScopeRoot) {
      // currentModule is null on ROOT nodes.
      if (currentModule == null || !typesToRewriteIn.contains(currentModule.moduleType())) {
        return;
      }

      Node parent = n.getParent();

      switch (n.getToken()) {
        case CALL:
          if (n.getFirstChild().matchesQualifiedName(GOOG_REQUIRE)
              || n.getFirstChild().matchesQualifiedName(GOOG_REQUIRE_TYPE)) {
            visitRequire(t, n, parent, currentModule);
          } else if (n.getFirstChild().matchesQualifiedName(GOOG_FORWARD_DECLARE)) {
            visitForwardDeclare(t, n, parent, currentModule);
          } else if (n.getFirstChild().matchesQualifiedName(GOOG_MODULE_GET)) {
            visitGoogModuleGet(t, n);
          }
          break;
        case NAME:
          maybeInlineName(t, n);
          break;
        default:
          break;
      }

      if (n.getJSDocInfo() != null) {
        rewriteJsdoc(t, n.getJSDocInfo(), currentModule);
      }
    }

    /**
     * Gets some made-up metadata for the given Closure namespace.
     *
     * <p>This is used when the namespace is not part of the input so that this pass can be fault
     * tolerant and still rewrite to something. Some tools don't care about rewriting correctly and
     * just want the type information of this modules (e.g. clutz).
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

    private void rewriteImport(
        NodeTraversal t,
        ModuleMetadata currentModule,
        @Nullable ModuleMetadata requiredModule,
        String requiredNamespace,
        Node callNode,
        Node parentNode) {
      Node callee = callNode.getFirstChild();
      maybeAddToSymbolTable(callee);

      Node statementNode = NodeUtil.getEnclosingStatement(callNode);
      Node changeScope = NodeUtil.getEnclosingChangeScopeRoot(statementNode);
      String globalName = ModuleRenaming.getGlobalName(requiredModule, requiredNamespace);

      if (parentNode.isExprResult()) {
        // goog.require('something');
        if (requiredModule.isNonLegacyGoogModule() || requiredModule.isEs6Module()) {
          // Fully qualified module namespaces can be referenced in type annotations.
          // Legacy modules and provides don't need rewriting since their references will be the
          // same after rewriting.
          nonAliasedNamespaces.put(requiredNamespace, globalName);
        }

        statementNode.detach();
        maybeAddToSymbolTable(callNode.getLastChild(), globalName);
      } else if (requiredModule.isEs6Module()) {
        // const alias = goog.require('es6');
        // const {alias} = goog.require('es6');

        // ES6 stored in a const or destructured - keep the variables and just replace the call.

        // const alias = module$es6;
        // const {alias} = module$es6;
        checkState(NodeUtil.isNameDeclaration(parentNode.getParent()));
        callNode.replaceWith(NodeUtil.newQName(compiler, globalName).srcrefTree(callNode));
        maybeAddToSymbolTable(callNode.getLastChild(), globalName);
      } else if (parentNode.isName()) {
        // const alias = goog.require('closure');
        // use(alias);

        // Closure file stored in a var or const without destructuring. Inline the variable.

        // use(closure-global-name);
        String name = parentNode.getString();

        // Get the variable from the scope. Scope creation is lazy, and we want to snapshot the
        // scope BEFORE we detach any nodes.
        Node fromScope = t.getScope().getVar(parentNode.getString()).getNameNode();
        checkState(fromScope == parentNode);
        variablesToInline.put(name, fromScope, globalName);
        typesToInline.put(parentNode.getString(), parentNode, globalName);
        statementNode.detach();

        maybeAddAliasToSymbolTable(statementNode.getFirstChild(), currentModule);
      } else {
        // const {alias} = goog.require('closure');
        // use(alias);

        // Closure file stored in a var or const with destructuring. Inline the variables.

        // use(closure-global-name.alias);
        checkState(parentNode.isDestructuringLhs());

        for (Node importSpec : parentNode.getFirstChild().children()) {
          checkState(importSpec.hasChildren(), importSpec);
          String importedProperty = importSpec.getString();
          Node aliasNode = importSpec.getFirstChild();
          String aliasName = aliasNode.getString();
          String fullName = globalName + "." + importedProperty;

          // Get the variable from the scope. Scope creation is lazy, and we want to snapshot the
          // scope BEFORE we detach any nodes.
          Node fromScope = t.getScope().getVar(aliasNode.getString()).getNameNode();
          checkState(fromScope == aliasNode);
          variablesToInline.put(aliasName, aliasNode, fullName);
          typesToInline.put(aliasName, aliasNode, fullName);

          maybeAddAliasToSymbolTable(aliasNode, currentModule);
        }

        statementNode.detach();
      }

      compiler.reportChangeToChangeScope(changeScope);
    }

    private void visitRequire(
        NodeTraversal t, Node callNode, Node parentNode, ModuleMetadata currentModule) {
      String requiredNamespace = callNode.getLastChild().getString();
      ModuleMetadata requiredModule =
          moduleMetadataMap.getModulesByGoogNamespace().get(requiredNamespace);
      if (requiredModule == null) {
        requiredModule = getFallbackMetadataForNamespace(requiredNamespace);
      }
      rewriteImport(t, currentModule, requiredModule, requiredNamespace, callNode, parentNode);
    }

    private void rewriteGoogModuleGet(
        ModuleMetadata requiredModule, String requiredNamespace, NodeTraversal t, Node callNode) {
      Node maybeAssign = callNode.getParent();
      boolean isFillingAnAlias = maybeAssign.isAssign() && maybeAssign.getParent().isExprResult();

      if (isFillingAnAlias) {
        // Only valid to assign if the original declaration was a forward declare. Verified in the
        // CheckClosureImports pass. We can just detach this node as we will replace the
        // goog.forwardDeclare.
        // let x = goog.forwardDeclare('x');
        // x = goog.module.get('x');
        maybeAssign.getParent().detach();
      } else {
        callNode.replaceWith(
            NodeUtil.newQName(
                    compiler, ModuleRenaming.getGlobalName(requiredModule, requiredNamespace))
                .srcrefTree(callNode));
      }

      t.reportCodeChange();
    }

    private void visitGoogModuleGet(NodeTraversal t, Node callNode) {
      String requiredNamespace = callNode.getLastChild().getString();
      ModuleMetadata requiredModule =
          moduleMetadataMap.getModulesByGoogNamespace().get(requiredNamespace);
      if (requiredModule == null) {
        // Be fault tolerant. An error should've been reported in the checks.
        compiler.reportChangeToChangeScope(NodeUtil.getEnclosingChangeScopeRoot(callNode));
        NodeUtil.getEnclosingStatement(callNode).detach();
        return;
      }
      rewriteGoogModuleGet(requiredModule, requiredNamespace, t, callNode);
    }

    /** Process a goog.forwardDeclare() call and record the specified forward declaration. */
    private void visitForwardDeclare(
        NodeTraversal t, Node n, Node parent, ModuleMetadata currentModule) {
      CodingConvention convention = compiler.getCodingConvention();

      String typeDeclaration;
      try {
        typeDeclaration = Iterables.getOnlyElement(convention.identifyTypeDeclarationCall(n));
      } catch (NullPointerException | NoSuchElementException | IllegalArgumentException e) {
        compiler.report(
            JSError.make(
                n,
                INVALID_FORWARD_DECLARE,
                "A single type could not identified for the goog.forwardDeclare statement"));
        return;
      }

      if (typeDeclaration != null) {
        compiler.forwardDeclareType(typeDeclaration);
      }

      String requiredNamespace = n.getLastChild().getString();
      ModuleMetadata requiredModule =
          moduleMetadataMap.getModulesByGoogNamespace().get(requiredNamespace);

      // Assume anything not provided is a global, and that this isn't a module (we've checked this
      // in the checks pass).
      if (requiredModule == null) {
        checkState(parent.isExprResult());
        parent.detach();
        t.reportCodeChange();
      } else {
        rewriteImport(t, currentModule, requiredModule, requiredNamespace, n, parent);
      }
    }

    private void maybeInlineName(NodeTraversal t, Node n) {
      if (variablesToInline.isEmpty()) {
        return;
      }

      Var var = t.getScope().getVar(n.getString());

      // The scope hasn't updated, all aliases should be in scope still.
      if (var == null) {
        return;
      }

      String newName = variablesToInline.get(n.getString(), var.getNameNode());

      if (newName == null) {
        return;
      }

      if (n.getParent().isExportSpec() && n.getParent().getFirstChild() == n) {
        // We can't inline a name in an export spec. So split it out into its own export statement
        // first. Note that it should always be okay to make this new export a const export, which
        // differs from export specs, because we force the goog.require alias to be const.

        // const a = goog.require('some.provide');
        // let qux;
        // export {qux, a as b};
        // =======================================
        // const a = goog.require('some.provide');
        // let qux;
        // export {qux};
        // export const b = a;

        // After this rewriting we can then replace the new RHS `a` with `some.provide`.
        n = splitExportSpec(t, n, n.getParent());
      }

      if (!newName.contains(".")) {
        safeSetString(n, newName);
      } else {
        Node changeScope = NodeUtil.getEnclosingChangeScopeRoot(n);
        n.replaceWith(NodeUtil.newQName(compiler, newName).srcrefTree(n));
        if (changeScope != null) {
          compiler.reportChangeToChangeScope(changeScope);
        }
      }
    }

    /**
     * Splits the given name of an export spec into its own constant export statement.
     *
     * @return the right hand side of the new const node
     */
    private Node splitExportSpec(NodeTraversal t, Node nameNode, Node exportSpec) {
      Node oldExport = exportSpec.getGrandparent(); // export spec -> specs -> export
      Node newRHSNameNode = IR.name(nameNode.getString());
      Node newExport =
          IR.export(IR.constNode(IR.name(exportSpec.getSecondChild().getString()), newRHSNameNode))
              .srcrefTree(exportSpec);
      oldExport.getParent().addChildAfter(newExport, oldExport);
      exportSpec.detach();
      t.reportCodeChange();
      return newRHSNameNode;
    }

    private void safeSetString(Node n, String newString) {
      if (n.getString().equals(newString)) {
        return;
      }

      String originalName = n.getString();
      n.setString(newString);
      if (n.getOriginalName() == null) {
        n.setOriginalName(originalName);
      }
      // TODO(blickly): It would be better not to be renaming detached nodes
      Node changeScope = NodeUtil.getEnclosingChangeScopeRoot(n);
      if (changeScope != null) {
        compiler.reportChangeToChangeScope(changeScope);
      }
    }

    private void rewriteJsdoc(NodeTraversal t, JSDocInfo info, ModuleMetadata currentModule) {
      if (typesToInline.isEmpty() && nonAliasedNamespaces.isEmpty()) {
        return;
      }

      JsDocRefReplacer replacer = new JsDocRefReplacer(currentModule, t.getScope());

      for (Node typeNode : info.getTypeNodes()) {
        NodeUtil.visitPreOrder(typeNode, replacer);
      }
    }

    private final class JsDocRefReplacer implements NodeUtil.Visitor {
      private final ModuleMetadata currentModule;
      private final Scope scope;

      JsDocRefReplacer(ModuleMetadata currentModule, Scope scope) {
        this.currentModule = currentModule;
        this.scope = scope;
      }

      @Override
      public void visit(Node typeRefNode) {
        if (!typeRefNode.isString()) {
          return;
        }

        // A type name that might be simple like "Foo" or qualified like "foo.Bar".
        String typeName = typeRefNode.getString();

        // Tries to rename progressively shorter type prefixes like "foo.Bar.Baz", "foo.Bar",
        // "foo".
        String prefixTypeName = typeName;
        String suffix = "";
        do {
          // If the name is an alias for an imported namespace rewrite from
          // "{Foo}" to
          // "{module$exports$bar$Foo}" or
          // "{bar.Foo}"
          String globalName = null;

          // First see if this is an aliased variable created by an import.
          if (!prefixTypeName.contains(".")) {
            Var var = scope.getVar(prefixTypeName);

            if (var != null) {
              globalName = typesToInline.get(prefixTypeName, var.getNameNode());
            }
          }

          // If null it is not an aliased, local variable. See if there was a non-aliased import
          // for this namespace.
          if (globalName == null) {
            globalName = nonAliasedNamespaces.get(prefixTypeName);
          }

          if (globalName != null) {
            if (preprocessorSymbolTable != null) {
              // Jsdoc type node is a single STRING node that spans the whole type. For example
              // STRING node "bar.Foo". When rewriting modules potentially replace only "module"
              // part of the type: "bar.Foo" => "module$exports$bar$Foo". So we need to remember
              // that "bar" as alias. To do that we clone type node and make "bar" node from  it.
              Node moduleOnlyNode = typeRefNode.cloneNode();
              safeSetString(moduleOnlyNode, prefixTypeName);
              moduleOnlyNode.setLength(prefixTypeName.length());
              maybeAddAliasToSymbolTable(moduleOnlyNode, currentModule);
            }

            safeSetString(typeRefNode, globalName + suffix);
            return;
          }

          if (prefixTypeName.contains(".")) {
            prefixTypeName = prefixTypeName.substring(0, prefixTypeName.lastIndexOf('.'));
            suffix = typeName.substring(prefixTypeName.length());
          } else {
            return;
          }
        } while (true);
      }
    }

    /** Add the given qualified name node to the symbol table. */
    private void maybeAddToSymbolTable(Node n) {
      if (preprocessorSymbolTable != null) {
        preprocessorSymbolTable.addReference(n);
      }
    }

    private void maybeAddToSymbolTable(Node n, String name) {
      if (preprocessorSymbolTable != null) {
        preprocessorSymbolTable.addReference(n, name);
      }
    }

    /**
     * Add alias nodes to the symbol table as they going to be removed by rewriter. Example aliases:
     *
     * <pre>
     * const Foo = goog.require('my.project.Foo');
     * const bar = goog.require('my.project.baz');
     * const {baz} = goog.require('my.project.utils');
     * </pre>
     */
    private void maybeAddAliasToSymbolTable(Node n, ModuleMetadata currentModule) {
      if (preprocessorSymbolTable != null) {
        n.putBooleanProp(Node.MODULE_ALIAS, true);
        // Alias can be used in js types. Types have node type STRING and not NAME so we have to
        // use their name as string.
        String nodeName =
            n.isString() ? n.getString() : preprocessorSymbolTable.getQualifiedName(n);

        // We need to include module as part of the name because aliases are local to current
        // module. Aliases with the same name from different module should be completely different
        // entities.
        String module =
            ModuleRenaming.getGlobalName(
                currentModule, Iterables.getFirst(currentModule.googNamespaces(), null));

        String name = "alias_" + module + "_" + nodeName;
        maybeAddToSymbolTable(n, name);
      }
    }
  }
}
