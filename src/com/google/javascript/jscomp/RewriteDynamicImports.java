/*
 * Copyright 2021 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.ModuleRenaming.GlobalizedModuleName;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.TemplatizedType;
import javax.annotation.Nullable;

/**
 * Rewrite dynamic import expressions to account for bundling and module rewriting. Since dynamic
 * imports cannot be fully polyfilled, optionally support replacing the expression with a function
 * call which indicates an external polyfill is utilized.
 *
 * <p>If the import specifier is a string literal and the module resolver recognizes the target, the
 * pass retargets the specifier to the correct output chunk.
 *
 * <p>Example from:
 *
 * <pre>
 *   import('./input-module1.js');
 * </pre>
 *
 * To:
 *
 * <pre>
 *   imprt_('./output-chunk0.js').then(function() { return module$output$chunk0; });
 * </pre>
 */
public class RewriteDynamicImports extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType UNABLE_TO_COMPUTE_RELATIVE_PATH =
      DiagnosticType.error(
          "JSC_UNABLE_TO_COMPUTE_RELATIVE_PATH",
          "Unable to compute relative import path from \"{0}\" to \"{1}\"");

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final String alias;
  private boolean dynamicImportsRemoved = false;

  /** @param compiler The compiler */
  public RewriteDynamicImports(AbstractCompiler compiler, @Nullable String alias) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.alias = alias;
  }

  @Override
  public void process(Node externs, Node root) {
    dynamicImportsRemoved = false;
    checkArgument(externs.isRoot(), externs);
    checkArgument(root.isRoot(), root);
    NodeTraversal.traverse(compiler, root, this);
    if (dynamicImportsRemoved) {
      // This pass removes dynamic import, but adds arrow functions.
      compiler.setFeatureSet(
          compiler.getFeatureSet().without(Feature.DYNAMIC_IMPORT).with(Feature.ARROW_FUNCTIONS));
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.getToken() != Token.DYNAMIC_IMPORT) {
      return;
    }

    // If the module specifier is a string, attempt to resolve the module
    final ModuleMap moduleMap = compiler.getModuleMap();
    final Node importSpecifier = n.getFirstChild();
    if (importSpecifier.isString() && moduleMap != null) {
      final ModulePath targetPath = compiler.getModuleLoader().resolve(importSpecifier.getString());
      final Module module = compiler.getModuleMap().getModule(targetPath);
      final String targetModuleVarName =
          (module == null)
              ? null
              : GlobalizedModuleName.create(module.metadata(), null, null).aliasName().join();
      final Var targetModuleNS =
          (targetModuleVarName == null) ? null : t.getScope().getVar(targetModuleVarName);

      if (targetModuleNS != null) {
        final JSModule targetModule = targetModuleNS.getInput().getModule();
        // If the target module is bundled into the same output chunk, replace the import statement
        // with a promise that resolves to the module namespace.
        // No further rewriting occurs for this case.
        if (t.getModule() == targetModule) {
          replaceDynamicImportWithPromise(t, n, targetModuleNS);
          return;
        } else {
          // The target output chunk is recognized and different from the current chunk.
          // Retarget the import specifier path to the output chunk path and rewrite
          // the import to reference the rewritten global module namespace variable.
          retargetImportSpecifier(t, n, targetModule);
          if (NodeUtil.isExpressionResultUsed(n)) {
            addChainedThen(t, n, targetModuleNS);
          }
        }
      }
    }
    if (this.alias != null) {
      aliasDynamicImport(t, n);
    }
  }

  /**
   * Replace a dynamic import expression with a promise resolving to the rewritten module namespace.
   *
   * <p>Before
   *
   * <pre>
   *   import('./foo.js');
   * </pre>
   *
   * After
   *
   * <pre>
   *   Promise.resolve(module$foo);
   * </pre>
   */
  private void replaceDynamicImportWithPromise(
      NodeTraversal t, Node dynamicImport, Var targetModuleNS) {
    final Scope scope = t.getScope();
    final JSTypeRegistry registry = compiler.getTypeRegistry();
    final Node promiseDotResolve =
        astFactory.createQName(scope.getGlobalScope(), "Promise.resolve");
    final Node promiseResolveCall = astFactory.createCall(promiseDotResolve);
    final boolean isExpressionUsed = NodeUtil.isExpressionResultUsed(dynamicImport);
    if (isExpressionUsed) {
      JSType moduleNamespaceType;
      if (dynamicImport.getJSType() != null && dynamicImport.getJSType().isTemplatizedType()) {
        TemplatizedType templatizedType = (TemplatizedType) dynamicImport.getJSType();
        moduleNamespaceType = templatizedType.getTemplateTypes().asList().get(0);
      } else {
        moduleNamespaceType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }
      promiseDotResolve.setJSType(
          registry.createFunctionType(
              registry.createTemplatizedType(
                  registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE), moduleNamespaceType),
              registry.getNativeType(JSTypeNative.ALL_TYPE)));
      promiseResolveCall.addChildToBack(createModuleNamespaceNode(targetModuleNS));
      promiseResolveCall.setJSType(dynamicImport.getJSType());
    } else {
      promiseDotResolve.setJSType(
          registry.createFunctionType(
              registry.createTemplatizedType(
                  registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE),
                  registry.getNativeType(JSTypeNative.VOID_TYPE)),
              registry.getNativeType(JSTypeNative.ALL_TYPE)));
      promiseResolveCall.setJSType(
          registry.createTemplatizedType(
              registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE),
              registry.getNativeType(JSTypeNative.VOID_TYPE)));
    }
    promiseResolveCall.srcrefTree(dynamicImport);
    Node parent = dynamicImport.getParent();
    dynamicImport.replaceWith(promiseResolveCall);
    t.reportCodeChange(parent);
    dynamicImportsRemoved = true;
  }

  private static String getChunkFileName(JSModule chunk) {
    return chunk.getName() + ".js";
  }

  /**
   * Rewrite the dynamic import specifier to the output chunk path
   *
   * <p>Before
   *
   * <pre>
   *   import('./foo.js');
   * </pre>
   *
   * After
   *
   * <pre>
   *   import('./chunk0.js');
   * </pre>
   */
  private void retargetImportSpecifier(NodeTraversal t, Node dynamicImport, JSModule targetModule) {
    String retargetedSpecifier;
    String importingChunkFilename = getChunkFileName(t.getInput().getModule());
    String targetChunkFilename = getChunkFileName(targetModule);
    try {
      retargetedSpecifier =
          ModuleLoader.relativePathFrom(importingChunkFilename, targetChunkFilename);
    } catch (IllegalArgumentException e) {
      compiler.report(
          JSError.make(
              dynamicImport,
              UNABLE_TO_COMPUTE_RELATIVE_PATH,
              importingChunkFilename,
              targetChunkFilename));
      return;
    }
    final Node originalSpecifierNode = dynamicImport.getFirstChild();
    Node newSpecifier =
        astFactory.createString(retargetedSpecifier).srcref(originalSpecifierNode);
    originalSpecifierNode.replaceWith(newSpecifier);
    t.reportCodeChange(newSpecifier);
  }

  /**
   * Since a dynamic import expression is a promise resolving to the namespace export type, add a
   * ".then()" call after it and resolve to the rewritten module namespace.
   *
   * <p>Before
   *
   * <pre>
   *   import('./foo.js');
   * </pre>
   *
   * After
   *
   * <pre>
   *   import('./foo.js').then(function() { return module$foo; });
   * </pre>
   */
  private void addChainedThen(NodeTraversal t, Node dynamicImport, Var targetModuleNs) {
    JSTypeRegistry registry = compiler.getTypeRegistry();
    final Node importParent = dynamicImport.getParent();
    final Node placeholder = IR.empty();
    dynamicImport.replaceWith(placeholder);
    final Node moduleNamespaceNode = createModuleNamespaceNode(targetModuleNs);
    final Node callbackFn = astFactory.createZeroArgArrowFunctionForExpression(moduleNamespaceNode);
    final Node importThenCall =
        astFactory.createCall(astFactory.createGetProp(dynamicImport, "then"), callbackFn);
    importThenCall
        .getFirstChild()
        .setJSType(
            registry.createFunctionType(
                registry.createTemplatizedType(
                    registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE),
                    registry.getNativeType(JSTypeNative.UNKNOWN_TYPE)),
                registry.createFunctionType(registry.getNativeType(JSTypeNative.ALL_TYPE))));
    importThenCall.srcrefTreeIfMissing(dynamicImport);
    if (dynamicImport.getJSType() != null) {
      importThenCall.setJSType(dynamicImport.getJSType());
    }
    placeholder.replaceWith(importThenCall);
    compiler.reportChangeToChangeScope(callbackFn);
    compiler.reportChangeToEnclosingScope(importParent);
  }

  /**
   * Replace a dynamic import expression with a function call to the specified alias.
   *
   * <p>Before
   *
   * <pre>
   *   import('./foo.js');
   * </pre>
   *
   * After
   *
   * <pre>
   *   aliasedName('./foo.js');
   * </pre>
   */
  private void aliasDynamicImport(NodeTraversal t, Node dynamicImport) {
    checkNotNull(this.alias);
    final Node aliasNode = astFactory.createQNameWithUnknownType(this.alias);
    aliasNode.setOriginalName("import");
    final Node moduleSpecifier = dynamicImport.getFirstChild().detach();
    Node importAliasCall =
        astFactory
            .createCall(aliasNode, moduleSpecifier)
            .srcrefTreeIfMissing(dynamicImport);
    if (dynamicImport.getJSType() != null) {
      importAliasCall.setJSType(dynamicImport.getJSType());
    }
    dynamicImport.replaceWith(importAliasCall);
    t.reportCodeChange(importAliasCall);
    dynamicImportsRemoved = true;
  }

  /** For a given module, return a reference to the module namespace export */
  private Node createModuleNamespaceNode(Var moduleVar) {
    final Node moduleVarNode = moduleVar.getNode();
    Node moduleNamespace = moduleVarNode.cloneNode();
    moduleNamespace.setJSType(moduleVarNode.getJSType());
    return moduleNamespace;
  }
}
