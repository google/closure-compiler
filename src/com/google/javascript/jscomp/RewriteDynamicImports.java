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
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;
import static com.google.javascript.jscomp.ConvertChunksToESModules.DYNAMIC_IMPORT_CALLBACK_FN;
import static com.google.javascript.jscomp.ConvertChunksToESModules.UNABLE_TO_COMPUTE_RELATIVE_PATH;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
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
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplatizedType;
import java.util.Iterator;
import org.jspecify.annotations.Nullable;

/**
 * Rewrite dynamic import expressions to account for bundling and module rewriting. Since dynamic
 * imports cannot be fully polyfilled, optionally support replacing the expression with a function
 * call (alias function) which indicates an external polyfill is utilized.
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
 *
 * <p>Unlike other "rewrite" passes for which the DefaultPassConfig checks the output language level
 * using {@code options.needsTranspilationOf(feature)} before adding a pass, it does not check the
 * language level before adding this particular pass. Even when supported by the output level, we
 * still invoke this pass if {@code options.shouldAllowDynamicImport()} is set. The only difference
 * is that for {@code ES2020}, the dynamic imports syntax can pass through if aliasing is not
 * requested, while for {@code < ES2020} they must get rewritten using the alias call.
 *
 * <pre>
 * <p>{@code
 * options.shouldAllowDynamicImport() === false?
 *     ForbidDynamicImportUsage :
 *     (output >= ES2020) ?
 *         don't *really* rewrite it unless aliasing is requested :
 *         rewrite it using the alias call
 * }
 * </pre>
 *
 * <p>TODO: b/291319705 For the above reason, the pass should be better named as
 * MaybeRewriteDynamicImports.
 */
public class RewriteDynamicImports extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType DYNAMIC_IMPORT_ALIASING_REQUIRED =
      DiagnosticType.warning(
          "JSC_DYNAMIC_IMPORT_ALIASING_REQUIRED",
          "Dynamic import expressions should be aliased for language level. "
              + "Use the --dynamic_import_alias flag.");

  static final DiagnosticType DYNAMIC_IMPORT_INVALID_ALIAS =
      DiagnosticType.error(
          "JSC_DYNAMIC_IMPORT_INVALID_ALIAS", "Dynamic import alias is not a valid name");

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final @Nullable JSTypeRegistry registry;
  private final String alias;
  private final boolean requiresAliasing;
  private final boolean shouldWrapDynamicImportCallbacks;
  private boolean dynamicImportsRemoved = false;
  private boolean wrappedDynamicImportCallback = false;

  /** @param compiler The compiler */
  public RewriteDynamicImports(
      AbstractCompiler compiler, @Nullable String alias, ChunkOutputType chunkOutputType) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.registry = this.astFactory.isAddingTypes() ? compiler.getTypeRegistry() : null;
    this.alias = alias;
    this.requiresAliasing =
        !compiler.getOptions().getOutputFeatureSet().contains(Feature.DYNAMIC_IMPORT);
    this.shouldWrapDynamicImportCallbacks = chunkOutputType == ChunkOutputType.ES_MODULES;
  }

  private final boolean aliasIsValid() {
    return alias != null
        && (alias.equals("import")
            || NodeUtil.isValidQualifiedName(compiler.getAllowableFeatures(), alias));
  }

  @Override
  public void process(Node externs, Node root) {
    dynamicImportsRemoved = false;
    checkArgument(externs.isRoot(), externs);
    checkArgument(root.isRoot(), root);

    NodeTraversal.traverse(compiler, root, this);
    if (wrappedDynamicImportCallback) {
      injectWrappingFunctionExtern();
    }

    if (dynamicImportsRemoved) {
      compiler.markFeatureNotAllowed(Feature.DYNAMIC_IMPORT);

      // This pass removes dynamic import, but adds arrow functions.
      // It is unusual to call this NodeUtil method instead of the TranspilationPasses one. This
      // pass runs as a stage1 checks pass and does not have access to {@code TranspilationPasses}.
      // Adding that dep produces a cycle in the BUILD dep graph.
      // Regular transpiler passes must use the {@code
      // TranspilationPassses.maybeMarkFeaturesAsTranspiledAway}.
      NodeUtil.removeFeatureFromAllScripts(root, Feature.DYNAMIC_IMPORT, compiler);
      if (this.requiresAliasing && aliasIsValid()) {
        NodeTraversal.traverse(compiler, externs, new AliasInjectingTraversal());
      }
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
    if (importSpecifier.isStringLit() && moduleMap != null) {
      final ModulePath targetPath =
          t.getInput()
              .getPath()
              .resolveJsModule(
                  importSpecifier.getString(), n.getSourceFileName(), n.getLineno(), n.getCharno());
      final Module module =
          (targetPath == null) ? null : compiler.getModuleMap().getModule(targetPath);

      final String targetModuleVarName =
          (module == null)
              ? null
              : GlobalizedModuleName.create(module.metadata(), null, null).aliasName().join();
      final Var targetModuleNS =
          (targetModuleVarName == null) ? null : t.getScope().getVar(targetModuleVarName);

      if (targetModuleNS != null) {
        final JSChunk targetChunk = targetModuleNS.getInput().getChunk();
        // If the target module is bundled into the same output chunk, replace the import statement
        // with a promise that resolves to the module namespace.
        // No further rewriting occurs for this case.
        if (t.getChunk() == targetChunk) {
          replaceDynamicImportWithPromise(t, n, targetModuleNS);
          return;
        } else {
          // The target output chunk is recognized and different from the current chunk.
          // Retarget the import specifier path to the output chunk path and rewrite
          // the import to reference the rewritten global module namespace variable.
          retargetImportSpecifier(t, n, targetChunk);
          if (NodeUtil.isExpressionResultUsed(n)) {
            addChainedThen(t, n, targetModuleNS);
          }
        }
      }
    }
    if (aliasIsValid()) {
      aliasDynamicImport(t, n);
    } else if (this.alias != null) {
      t.report(n, DYNAMIC_IMPORT_INVALID_ALIAS);
    } else if (requiresAliasing) {
      t.report(n, DYNAMIC_IMPORT_ALIASING_REQUIRED);
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
    final Node promiseDotResolve =
        astFactory.createGetPropWithUnknownType(
            astFactory.createName(compiler.getTranspilationNamespace(), "Promise"), "resolve");
    final boolean isExpressionUsed = NodeUtil.isExpressionResultUsed(dynamicImport);
    final Node promiseResolveCall =
        isExpressionUsed
            ? astFactory.createCall(
                promiseDotResolve, type(dynamicImport), createModuleNamespaceNode(targetModuleNS))
            : astFactory.createCallWithUnknownType(promiseDotResolve);
    promiseResolveCall.srcrefTree(dynamicImport);
    Node parent = dynamicImport.getParent();
    dynamicImport.replaceWith(promiseResolveCall);
    t.reportCodeChange(parent);
    dynamicImportsRemoved = true;
  }

  private static String getChunkFileName(JSChunk chunk) {
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
  private void retargetImportSpecifier(NodeTraversal t, Node dynamicImport, JSChunk targetChunk) {
    String retargetedSpecifier;
    String importingChunkFilename = getChunkFileName(t.getInput().getChunk());
    String targetChunkFilename = getChunkFileName(targetChunk);
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
    Node newSpecifier = astFactory.createString(retargetedSpecifier).srcref(originalSpecifierNode);
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
   *   import('./foo.js').then(() => module$foo);
   * </pre>
   */
  private void addChainedThen(NodeTraversal t, Node dynamicImport, Var targetModuleNs) {
    final Node importParent = dynamicImport.getParent();
    final Node placeholder = IR.empty();
    dynamicImport.replaceWith(placeholder);
    final Node moduleNamespaceNode = createModuleNamespaceNode(targetModuleNs);
    final Node callbackFn = astFactory.createZeroArgArrowFunctionForExpression(moduleNamespaceNode);
    Node thenArgument = callbackFn;
    if (shouldWrapDynamicImportCallbacks) {
      AstFactory.Type wrappingFnType =
          registry != null
              ? type(registry.createFunctionType(callbackFn.getJSType(), callbackFn.getJSType()))
              : type(JSTypeNative.UNKNOWN_TYPE);
      Node wrappingFunction = astFactory.createName(DYNAMIC_IMPORT_CALLBACK_FN, wrappingFnType);
      thenArgument = astFactory.createCall(wrappingFunction, type(callbackFn), callbackFn);
      wrappedDynamicImportCallback = true;
    }
    final Node importThenCall =
        astFactory.createCall(
            astFactory.createGetPropWithUnknownType(dynamicImport, "then"),
            type(dynamicImport),
            thenArgument);
    importThenCall.srcrefTreeIfMissing(dynamicImport);
    placeholder.replaceWith(importThenCall);
    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.ARROW_FUNCTIONS, compiler);
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
    final Node moduleSpecifier = dynamicImport.removeFirstChild();
    Node importAliasCall =
        astFactory
            .createCall(aliasNode, type(dynamicImport), moduleSpecifier)
            .srcrefTreeIfMissing(dynamicImport);
    dynamicImport.replaceWith(importAliasCall);
    t.reportCodeChange(importAliasCall);
    dynamicImportsRemoved = true;
  }

  /** For a given module, return a reference to the module namespace export */
  private Node createModuleNamespaceNode(Var moduleVar) {
    final Node moduleVarNode = moduleVar.getNode();
    Node moduleNamespace = moduleVarNode.cloneNode();
    moduleNamespace.copyTypeFrom(moduleVarNode);
    return moduleNamespace;
  }

  /** For a given module, return a reference to the module namespace export */
  private void injectWrappingFunctionExtern() {
    AstFactory.Type wrappingFnType = type(JSTypeNative.UNKNOWN_TYPE);
    if (registry != null) {
      TemplateType templateT = registry.createTemplateType("T");
      wrappingFnType = type(registry.createFunctionType(templateT, templateT));
    }
    final Node wrappingFunctionDefinition =
        astFactory.createFunction(
            DYNAMIC_IMPORT_CALLBACK_FN,
            astFactory.createParamList("importCallback"),
            astFactory.createBlock(),
            wrappingFnType);

    Node externsRoot = compiler.getSynthesizedExternsInput().getAstRoot(compiler);
    wrappingFunctionDefinition.srcrefTree(externsRoot);
    externsRoot.addChildToBack(wrappingFunctionDefinition);
    compiler.reportChangeToEnclosingScope(wrappingFunctionDefinition);
  }

  /**
   * A shallow traversal class for the externs to inject the alias.
   *
   * <p>Later passes require the names to be defined. For simple name aliases first check for an
   * existing definition. Qualified name aliases check for an existing definition of the root name
   * only.
   */
  private class AliasInjectingTraversal extends NodeTraversal.AbstractPreOrderCallback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      checkState(aliasIsValid());

      // If the extern is a simple name and already declared, there is nothing to do
      if (NodeUtil.isValidSimpleName(alias) && t.getScope().getVar(alias) != null) {
        return false;
      }

      // Create the type of the import function if adding types
      AstFactory.Type importFnType = type(JSTypeNative.UNKNOWN_TYPE);
      if (registry != null) {
        JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
        JSType promiseType = registry.getNativeType(JSTypeNative.PROMISE_TYPE);
        TemplatizedType promiseTemplatizedType =
            registry.createTemplatizedType(
                promiseType.toObjectType(), ImmutableList.of(unknownType));
        JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
        importFnType = type(registry.createFunctionType(promiseTemplatizedType, stringType));
      }

      Node externsRoot = compiler.getSynthesizedExternsInput().getAstRoot(compiler);
      // "import" is not a valid JS name and will not parse. However we can manually create it
      // and inject it into the externs.
      if (alias.equals("import") || NodeUtil.isValidSimpleName(alias)) {
        Node aliasNode =
            astFactory.createFunction(
                alias,
                astFactory.createParamList("specifier"),
                astFactory.createBlock(),
                importFnType);
        aliasNode.srcrefTree(externsRoot);
        externsRoot.addChildToBack(aliasNode);
      } else {
        String aliasRootName = NodeUtil.getRootOfQualifiedName(alias);
        Var aliasVar = t.getScope().getVar(aliasRootName);
        // If the namespace root is defined, just assume the whole name is properly defined.
        // This may need revisited in the future, but checking if the full qualified name
        // is properly defined is difficult here. As long as the root of the qualified name is
        // defined, other passes seem be ok even with missing properties.
        if (aliasVar != null) {
          return false;
        }
        Node aliasRootNode =
            astFactory.createSingleVarNameDeclaration(aliasRootName, astFactory.createObjectLit());
        aliasRootNode.srcrefTree(externsRoot);
        externsRoot.addChildToBack(aliasRootNode);

        // Define the rest of the parts of the name
        Iterator<String> aliasNameParts = Splitter.on(".").split(alias).iterator();
        Node qName = aliasRootNode.getFirstChild().cloneNode();
        aliasNameParts.next(); // skip over root name
        while (aliasNameParts.hasNext()) {
          qName = astFactory.createGetPropWithUnknownType(qName.cloneTree(), aliasNameParts.next());
          Node assignedValue;
          if (!aliasNameParts.hasNext()) {
            assignedValue =
                astFactory.createFunction(
                    "",
                    astFactory.createParamList("specifier"),
                    astFactory.createBlock(),
                    importFnType);
          } else {
            assignedValue = astFactory.createObjectLit();
          }
          Node expr = astFactory.exprResult(astFactory.createAssign(qName, assignedValue));
          expr.srcrefTree(externsRoot);
          externsRoot.addChildToBack(expr);
        }
      }
      compiler.reportChangeToChangeScope(externsRoot);
      return false;
    }
  }
}
