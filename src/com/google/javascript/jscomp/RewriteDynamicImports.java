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

import com.google.javascript.jscomp.ModuleRenaming.GlobalizedModuleName;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.TemplatizedType;
import javax.annotation.Nullable;

/**
 * Rewrite dynamic import expressions to account for bundling and module rewriting.
 * Since dynamic imports cannot be fully polyfilled, optionally support replacing the expression
 * with a function call which indicates an external polyfill is utilized.
 *
 * If the import specifier is a string literal and the module resolver recognizes the target,
 * the pass retargets the specifier to the correct output chunk.
 *
 * Example from:
 * <pre>
 *   import('./input-module1.js');
 * </pre>
 *
 * To:
 * <pre>
 *   imprt_('./output-chunk0.js').then(function() { return module$output$chunk0; });
 * </pre>
 */
public class RewriteDynamicImports extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType UNABLE_TO_COMPUTE_RELATIVE_PATH = DiagnosticType.error(
      "JSC_UNABLE_TO_COMPUTE_RELATIVE_PATH",
      "Unable to compute relative import path from \"{0}\" to \"{1}\"");

  static final DiagnosticType DYNAMIC_IMPORT_ALIAS_MISSING = DiagnosticType.warning(
      "JSC_DYNAMIC_IMPORT_ALIAS_MISSING",
      "A dynamic import alias is specified as a qualified name but the definition " +
          "is missing: \"{0}\"");

  private final AbstractCompiler compiler;
  private final String alias;
  private boolean dynamicImportsRemoved = false;
  private boolean shouldInjectAliasExtern = false;

  /**
   *
   *
   * @param compiler The compiler
   */
  public RewriteDynamicImports(AbstractCompiler compiler, @Nullable  String alias) {
    this.compiler = compiler;
    this.alias = alias;
  }

  @Override
  public void process(Node externs, Node root) {
    dynamicImportsRemoved = false;
    shouldInjectAliasExtern = false;
    checkArgument(externs.isRoot(), externs);
    checkArgument(root.isRoot(), root);
    ScopeCreator scopeCreator = new TypedScopeCreator(compiler);
    NodeTraversal nodeTraversal = new NodeTraversal(compiler, this, scopeCreator);
    nodeTraversal.traverse(root.getParent());
    if (dynamicImportsRemoved) {
      compiler.setFeatureSet(compiler.getFeatureSet().without(Feature.DYNAMIC_IMPORT));
    }
    if (shouldInjectAliasExtern) {
      injectAliasAsExtern(externs);
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.getToken() != Token.DYNAMIC_IMPORT) {
      return;
    }

    // If the module specifier is a string, attempt to resolve the module
    ModuleMap moduleMap = compiler.getModuleMap();
    Node importSpecifier = n.getFirstChild();
    if (importSpecifier.isString() && moduleMap != null) {
      ModulePath targetPath = compiler.getModuleLoader().resolve(importSpecifier.getString());
      TypedVar targetModuleNS = getModuleNamespaceVar(t, compiler.getModuleMap().getModule(targetPath));
      if (targetModuleNS != null) {
        JSModule targetModule = targetModuleNS.getInput().getModule();
        // If the target module is bundled into the same output chunk, replace the import statement
        // with a promise that resolves to the module namespace.
        // No further rewriting occurs for this case.
        if (t.getModule() == targetModuleNS.getInput().getModule()) {
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
   * Before
   * <pre>
   *   import('./foo.js');
   * </pre>
   *
   * After
   * <pre>
   *   Promise.resolve(module$foo);
   * </pre>
   */
  private void replaceDynamicImportWithPromise(NodeTraversal t, Node dynamicImport, TypedVar targetModuleNS) {
    JSTypeRegistry registry = compiler.getTypeRegistry();
    Node promise = IR.name("Promise");
    promise.setJSType(registry.getGlobalType("Promise"));

    Node resolve = IR.string("resolve");
    resolve.setJSType(registry.getNativeType(JSTypeNative.STRING_TYPE));

    Node promiseResolve = IR.getprop(promise, resolve);
    Node promiseResolveCall = IR.call(promiseResolve);
    boolean isExpressionUsed = NodeUtil.isExpressionResultUsed(dynamicImport);
    if (isExpressionUsed) {
      JSType moduleNamespaceType;
      if (dynamicImport.getJSType() != null && dynamicImport.getJSType().isTemplatizedType()) {
        TemplatizedType templatizedType = (TemplatizedType) dynamicImport.getJSType();
        moduleNamespaceType = templatizedType.getTemplateTypes().asList().get(0);
      } else {
        moduleNamespaceType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }
      promiseResolve.setJSType(registry.createFunctionType(
          registry.createTemplatizedType(
              registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE),
              moduleNamespaceType),
          registry.getNativeType(JSTypeNative.ALL_TYPE)));
      promiseResolveCall.addChildToBack(getModuleNamespaceNode(targetModuleNS));
      promiseResolveCall.setJSType(dynamicImport.getJSType());
    } else {
      promiseResolve.setJSType(registry.createFunctionType(
          registry.createTemplatizedType(
              registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE),
              registry.getNativeType(JSTypeNative.VOID_TYPE)),
          registry.getNativeType(JSTypeNative.ALL_TYPE)));
      promiseResolveCall.setJSType(registry.createTemplatizedType(
          registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE),
          registry.getNativeType(JSTypeNative.VOID_TYPE)));
    }
    promiseResolveCall.useSourceInfoFromForTree(dynamicImport);
    Node parent = dynamicImport.getParent();
    parent.replaceChild(dynamicImport, promiseResolveCall);
    t.reportCodeChange(parent);
    dynamicImportsRemoved = true;
  }

  private static String getChunkFileName(JSModule chunk) {
    return chunk.getName() + ".js";
  }

  /**
   * Rewrite the dynamic import specifier to the output chunk path
   *
   * Before
   * <pre>
   *   import('./foo.js');
   * </pre>
   *
   * After
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
    Node newSpecifier = IR.string(retargetedSpecifier)
        .useSourceInfoFrom(dynamicImport.getFirstChild());
    newSpecifier.setJSType(dynamicImport.getFirstChild().getJSType());
    dynamicImport.getFirstChild().replaceWith(newSpecifier);
    t.reportCodeChange(dynamicImport.getFirstChild());
  }

  /**
   * Since a dynamic import expression is a promise resolving to the namespace export type,
   * add a ".then()" call after it and resolve to the rewritten module namespace.
   *
   * Before
   * <pre>
   *   import('./foo.js');
   * </pre>
   *
   * After
   * <pre>
   *   import('./foo.js').then(function() { return module$foo; });
   * </pre>
   */
  private void addChainedThen(NodeTraversal t, Node dynamicImport, TypedVar targetModuleNs) {
    JSTypeRegistry registry = compiler.getTypeRegistry();
    Node importParent = dynamicImport.getParent();
    Node placeholder = IR.string("");
    importParent.replaceChild(dynamicImport, placeholder);
    Node thenCallback = IR.function(
        IR.name(""),
        IR.paramList(),
        IR.block(
            IR.returnNode(
                getModuleNamespaceNode(targetModuleNs))));
    thenCallback.setJSType(registry.createFunctionType(dynamicImport.getJSType()));

    Node importThenCall = IR.call(
        IR.getprop(
            dynamicImport,
            IR.string("then")),
        thenCallback);
    importThenCall.getFirstChild().setJSType(registry.createFunctionType(
        registry.createTemplatizedType(
            registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE),
            registry.getNativeType(JSTypeNative.UNKNOWN_TYPE)),
        registry.createFunctionType(registry.getNativeType(JSTypeNative.ALL_TYPE))
    ));
    importThenCall.useSourceInfoIfMissingFromForTree(dynamicImport);
    if (dynamicImport.getJSType() != null) {
      importThenCall.setJSType(dynamicImport.getJSType());
    }
    importParent.replaceChild(placeholder, importThenCall);
    t.reportCodeChange(NodeUtil.getFunctionBody(thenCallback));
    t.reportCodeChange(importThenCall);
  }

  /**
   * Replace a dynamic import expression with a function call to the specified alias.
   *
   * Before
   * <pre>
   *   import('./foo.js');
   * </pre>
   *
   * After
   * <pre>
   *   aliasedName('./foo.js');
   * </pre>
   */
  private void aliasDynamicImport(NodeTraversal t, Node dynamicImport) {
    checkNotNull(this.alias);
    if (NodeUtil.isValidSimpleName(this.alias) && t.getTypedScope().getVar(this.alias) == null) {
      shouldInjectAliasExtern = true;
    } else if (t.getTypedScope().getVar(this.alias) == null) {
      compiler.report(
          JSError.make(
              dynamicImport,
              DYNAMIC_IMPORT_ALIAS_MISSING,
              this.alias));
    }

    Node importAliasCall = IR.call(
        NodeUtil.newQName(compiler, this.alias, dynamicImport, "import"),
        dynamicImport.getFirstChild().detach());
    if (NodeUtil.isValidSimpleName(this.alias)) {
      importAliasCall.putBooleanProp(Node.FREE_CALL, true);
    }
    importAliasCall.useSourceInfoIfMissingFromForTree(dynamicImport);
    if (dynamicImport.getJSType() != null) {
      importAliasCall.getFirstChild().setJSType(dynamicImport.getJSType());
    }
    dynamicImport.getParent().replaceChild(dynamicImport, importAliasCall);
    t.reportCodeChange(importAliasCall);
    dynamicImportsRemoved = true;
  }

  /**
   * For a given module, return a reference to the module namespace export
   */
  private Node getModuleNamespaceNode(TypedVar moduleVar) {
    Node moduleNamespace = moduleVar.getNode().cloneNode();
    moduleNamespace.setJSType(moduleVar.getNode().getJSType());
    return moduleNamespace;
  }

  /**
   * For a given module, return a reference to the module namespace export
   */
  private TypedVar getModuleNamespaceVar(NodeTraversal t, @Nullable Module module) {
    if (module == null) {
      return null;
    }
    GlobalizedModuleName globalModuleName =
        ModuleRenaming.GlobalizedModuleName.create(module.metadata(), null, null);

    return t.getTypedScope().getVar(globalModuleName.aliasName().join());
  }

  /**
   * Inject the alias definition for dynamic import expressions into the externs.
   */
  private void injectAliasAsExtern(Node externs) {
    checkState(alias != null && NodeUtil.isValidSimpleName(alias));

    Node importAliasFunction = IR.function(
        IR.name(alias), IR.paramList(IR.name("specifier")), IR.block());
    JSTypeRegistry registry = compiler.getTypeRegistry();
    importAliasFunction.setJSType(
        registry.createFunctionType(
            registry.createTemplatizedType(
                registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE),
                registry.getNativeType(JSTypeNative.UNKNOWN_TYPE)),
            registry.getNativeType(JSTypeNative.STRING_TYPE)));
    importAliasFunction.useSourceInfoFromForTree(externs);

    String externsSourceFile = compiler.getInput(externs.getFirstChild().getInputId()).getSourceFile().getName();
    JSDocInfo.Builder info = JSDocInfo.builder();
    info.recordParameter(
            "specifier",
            new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), externsSourceFile));
    info.recordReturnType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Promise<?>"), externsSourceFile));
    importAliasFunction.setJSDocInfo(info.build());

    externs.getFirstChild().addChildToFront(importAliasFunction);
    compiler.reportChangeToEnclosingScope(importAliasFunction);
  }
}
