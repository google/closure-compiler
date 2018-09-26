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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.ClosureCheckModule.DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_REQUIRE_NAMESPACE;

import com.google.common.collect.LinkedHashMultiset;
import com.google.javascript.jscomp.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.ModuleMetadataMap.ModuleType;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Gathers metadata around modules that is useful for checking imports / requires and creates a
 * {@link ModuleMetadataMap}.
 */
public final class GatherModuleMetadata implements CompilerPass {
  static final DiagnosticType MIXED_MODULE_TYPE =
      DiagnosticType.error("JSC_MIXED_MODULE_TYPE", "A file cannot be both {0} and {1}.");

  static final DiagnosticType INVALID_DECLARE_MODULE_ID_CALL =
      DiagnosticType.error(
          "JSC_INVALID_DECLARE_NAMESPACE_CALL",
          "goog.declareModuleId parameter must be a string literal.");

  static final DiagnosticType DECLARE_MODULE_ID_OUTSIDE_ES6_MODULE =
      DiagnosticType.error(
          "JSC_DECLARE_MODULE_NAMESPACE_OUTSIDE_ES6_MODULE",
          "goog.declareModuleId can only be called within ES6 modules.");

  static final DiagnosticType MULTIPLE_DECLARE_MODULE_NAMESPACE =
      DiagnosticType.error(
          "JSC_MULTIPLE_DECLARE_MODULE_NAMESPACE",
          "goog.declareModuleId can only be called once per ES6 module.");

  static final DiagnosticType INVALID_REQUIRE_TYPE =
      DiagnosticType.error(
          "JSC_INVALID_REQUIRE_TYPE", "Argument to goog.requireType must be a string.");

  static final DiagnosticType INVALID_SET_TEST_ONLY =
      DiagnosticType.error(
          "JSC_INVALID_SET_TEST_ONLY",
          "Optional, single argument to goog.setTestOnly must be a string.");

  private static final Node GOOG_PROVIDE = IR.getprop(IR.name("goog"), IR.string("provide"));
  private static final Node GOOG_MODULE = IR.getprop(IR.name("goog"), IR.string("module"));
  private static final Node GOOG_REQUIRE = IR.getprop(IR.name("goog"), IR.string("require"));
  private static final Node GOOG_REQUIRE_TYPE =
      IR.getprop(IR.name("goog"), IR.string("requireType"));
  private static final Node GOOG_SET_TEST_ONLY =
      IR.getprop(IR.name("goog"), IR.string("setTestOnly"));
  private static final Node GOOG_MODULE_DECLARELEGACYNAMESPACE =
      IR.getprop(GOOG_MODULE.cloneTree(), IR.string("declareLegacyNamespace"));
  private static final Node GOOG_DECLARE_MODULE_ID =
      IR.getprop(IR.name("goog"), IR.string("declareModuleId"));

  // TODO(johnplaisted): Remove once clients have migrated to declareModuleId
  private static final Node GOOG_MODULE_DECLARNAMESPACE =
      IR.getprop(GOOG_MODULE.cloneTree(), IR.string("declareNamespace"));

  /**
   * Map from module path to module. These modules represent files and thus will contain all goog
   * namespaces that are in the file. These are not the same modules in modulesByGoogNamespace.
   */
  private final Map<String, ModuleMetadata> modulesByPath = new HashMap<>();

  /**
   * Map from Closure namespace to module. These modules represent just the single namespace and
   * thus each module has only one goog namespace in its {@link ModuleMetadata#googNamespaces()}.
   * These are not the same modules in modulesByPath.
   */
  private final Map<String, ModuleMetadata> modulesByGoogNamespace = new HashMap<>();

  /** The current module being traversed. */
  private ModuleMetadataBuilder currentModule;

  /**
   * The module currentModule is nested under, if any. Modules are expected to be at most two deep
   * (a script and then a goog.loadModule call).
   */
  private ModuleMetadataBuilder parentModule;

  /** The call to goog.loadModule we are traversing. */
  private Node loadModuleCall;

  private final AbstractCompiler compiler;
  private final boolean processCommonJsModules;
  private final ResolutionMode moduleResolutionMode;

  public GatherModuleMetadata(
      AbstractCompiler compiler,
      boolean processCommonJsModules,
      ResolutionMode moduleResolutionMode) {
    this.compiler = compiler;
    this.processCommonJsModules = processCommonJsModules;
    this.moduleResolutionMode = moduleResolutionMode;
  }

  private class ModuleMetadataBuilder {
    private boolean ambiguous;
    private Node declaredModuleId;
    private Node declaresLegacyNamespace;
    private final Node rootNode;
    final ModuleMetadata.Builder metadataBuilder;
    LinkedHashMultiset<String> googNamespaces = LinkedHashMultiset.create();

    ModuleMetadataBuilder(Node rootNode, @Nullable ModulePath path) {
      this.metadataBuilder = ModuleMetadata.builder();
      this.rootNode = rootNode;
      metadataBuilder.path(path).moduleType(ModuleType.SCRIPT).usesClosure(false).isTestOnly(false);
    }

    void moduleType(ModuleType type, NodeTraversal t, Node n) {
      checkNotNull(type);

      if (metadataBuilder.moduleType() == type) {
        return;
      }

      if (metadataBuilder.moduleType() == ModuleType.SCRIPT) {
        metadataBuilder.moduleType(type);
        return;
      }

      ambiguous = true;
      t.report(n, MIXED_MODULE_TYPE, metadataBuilder.moduleType().description, type.description);
    }

    void recordDeclareModuleId(Node declaredModuleId) {
      this.declaredModuleId = declaredModuleId;
    }

    void recordDeclareLegacyNamespace(Node declaresLegacyNamespace) {
      this.declaresLegacyNamespace = declaresLegacyNamespace;
    }

    boolean isScript() {
      return metadataBuilder.moduleType() == ModuleType.SCRIPT;
    }

    ModuleMetadata build() {
      metadataBuilder.googNamespacesBuilder().addAll(googNamespaces);
      if (!ambiguous) {
        if (declaredModuleId != null && metadataBuilder.moduleType() != ModuleType.ES6_MODULE) {
          compiler.report(JSError.make(declaredModuleId, DECLARE_MODULE_ID_OUTSIDE_ES6_MODULE));
        }

        if (declaresLegacyNamespace != null) {
          if (metadataBuilder.moduleType() == ModuleType.GOOG_MODULE) {
            metadataBuilder.moduleType(ModuleType.LEGACY_GOOG_MODULE);
          } else {
            compiler.report(
                JSError.make(declaresLegacyNamespace, DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE));
          }
        }
      }

      return metadataBuilder.build();
    }
  }

  /** Traverses the AST and build a sets of {@link ModuleMetadata}s. */
  private final class Finder implements Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case SCRIPT:
          enterModule(n, t.getInput().getPath());
          break;
        case IMPORT:
        case EXPORT:
          visitImportOrExport(t, n);
          break;
        case CALL:
          if (n.isCall() && n.getFirstChild().matchesQualifiedName("goog.loadModule")) {
            loadModuleCall = n;
            enterModule(n, null);
          }
          break;
        default:
          break;
      }

      return true;
    }

    private void visitImportOrExport(NodeTraversal t, Node importOrExport) {
      checkNotNull(currentModule);
      currentModule.moduleType(ModuleType.ES6_MODULE, t, importOrExport);
      if (importOrExport.isImport()
          // export from
          || (importOrExport.hasTwoChildren() && importOrExport.getLastChild().isString())) {
        currentModule
            .metadataBuilder
            .es6ImportSpecifiersBuilder()
            .add(importOrExport.getLastChild().getString());
      }
    }

    private void enterModule(Node n, @Nullable ModulePath path) {
      ModuleMetadataBuilder newModule = new ModuleMetadataBuilder(n, path);
      if (currentModule != null) {
        checkState(parentModule == null, "Expected modules to be nested at most 2 deep.");
        parentModule = currentModule;
      }
      currentModule = newModule;
    }

    private void leaveModule() {
      checkNotNull(currentModule);
      ModuleMetadata module = currentModule.build();
      if (module.path() != null) {
        modulesByPath.put(module.path().toString(), module);
      }
      for (String namespace : module.googNamespaces()) {
        modulesByGoogNamespace.put(namespace, module);
      }
      if (parentModule != null) {
        parentModule.metadataBuilder.nestedModulesBuilder().add(module);
      }
      currentModule = parentModule;
      parentModule = null;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (processCommonJsModules && currentModule != null && currentModule.isScript()) {
        if (ProcessCommonJSModules.isCommonJsExport(t, n, moduleResolutionMode)
            || ProcessCommonJSModules.isCommonJsImport(n, moduleResolutionMode)) {
          currentModule.moduleType(ModuleType.COMMON_JS, t, n);
          return;
        }
      }

      switch (n.getToken()) {
        case SCRIPT:
          leaveModule();
          break;
        case NAME:
          visitName(t, n);
          break;
        case CALL:
          if (loadModuleCall == n) {
            leaveModule();
            loadModuleCall = null;
          } else {
            visitGoogCall(t, n);
          }
          break;
        default:
          break;
      }
    }

    private boolean isFromGoogImport(Var goog) {
      Node nameNode = goog.getNameNode();

      // Because other tools are regex based we force importing this file as "import * as goog".
      return nameNode != null
          && nameNode.isImportStar()
          && nameNode.getString().equals("goog")
          && nameNode.getParent().getFirstChild().isEmpty()
          && nameNode.getParent().getLastChild().getString().endsWith("/goog.js");
    }

    private void visitName(NodeTraversal t, Node n) {
      if (!"goog".equals(n.getString())) {
        return;
      }

      Var root = t.getScope().getVar("goog");
      if (root != null && !isFromGoogImport(root)) {
        return;
      }

      currentModule.metadataBuilder.usesClosure(true);
    }

    private void visitGoogCall(NodeTraversal t, Node n) {
      if (!n.hasChildren()
          || !n.getFirstChild().isGetProp()
          || !n.getFirstChild().isQualifiedName()) {
        return;
      }

      Node getprop = n.getFirstChild();

      Node firstProp = n.getFirstChild();

      while (firstProp.isGetProp()) {
        firstProp = firstProp.getFirstChild();
      }

      if (!firstProp.isName() || !firstProp.getString().equals("goog")) {
        return;
      }

      Var root = t.getScope().getVar("goog");
      if (root != null && root.input == t.getInput() && !isFromGoogImport(root)) {
        return;
      }

      currentModule.metadataBuilder.usesClosure(true);

      if (getprop.matchesQualifiedName(GOOG_PROVIDE)) {
        currentModule.moduleType(ModuleType.GOOG_PROVIDE, t, n);
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          String namespace = n.getLastChild().getString();
          addNamespace(currentModule, namespace, t, n);
        } else {
          t.report(n, ClosureRewriteModule.INVALID_PROVIDE_NAMESPACE);
        }
      } else if (getprop.matchesQualifiedName(GOOG_MODULE)) {
        currentModule.moduleType(ModuleType.GOOG_MODULE, t, n);
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          String namespace = n.getLastChild().getString();
          addNamespace(currentModule, namespace, t, n);
        } else {
          t.report(n, ClosureRewriteModule.INVALID_MODULE_NAMESPACE);
        }
      } else if (getprop.matchesQualifiedName(GOOG_MODULE_DECLARELEGACYNAMESPACE)) {
        currentModule.recordDeclareLegacyNamespace(n);
      } else if (getprop.matchesQualifiedName(GOOG_DECLARE_MODULE_ID)
          || getprop.matchesQualifiedName(GOOG_MODULE_DECLARNAMESPACE)) {
        if (currentModule.declaredModuleId != null) {
          t.report(n, MULTIPLE_DECLARE_MODULE_NAMESPACE);
        }
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          currentModule.recordDeclareModuleId(n);
          String namespace = n.getLastChild().getString();
          addNamespace(currentModule, namespace, t, n);
        } else {
          t.report(n, INVALID_DECLARE_MODULE_ID_CALL);
        }
      } else if (getprop.matchesQualifiedName(GOOG_REQUIRE)) {
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          currentModule
              .metadataBuilder
              .requiredGoogNamespacesBuilder()
              .add(n.getLastChild().getString());
        } else {
          t.report(n, INVALID_REQUIRE_NAMESPACE);
        }
      } else if (getprop.matchesQualifiedName(GOOG_REQUIRE_TYPE)) {
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          currentModule.metadataBuilder.requiredTypesBuilder().add(n.getLastChild().getString());
        } else {
          t.report(n, INVALID_REQUIRE_TYPE);
        }
      } else if (getprop.matchesQualifiedName(GOOG_SET_TEST_ONLY)) {
        if (n.hasOneChild() || (n.hasTwoChildren() && n.getLastChild().isString())) {
          currentModule.metadataBuilder.isTestOnly(true);
        } else {
          t.report(n, INVALID_SET_TEST_ONLY);
        }
      }
    }

    /**
     * Adds the namespaces to the module and checks if the given Closure namespace is a duplicate or
     * not.
     */
    private void addNamespace(
        ModuleMetadataBuilder module, String namespace, NodeTraversal t, Node n) {
      ModuleType existingType = null;
      if (module.googNamespaces.contains(namespace)) {
        existingType = module.metadataBuilder.moduleType();
      } else {
        ModuleMetadata existingModule = modulesByGoogNamespace.get(namespace);
        if (existingModule != null) {
          existingType = existingModule.moduleType();
        }
      }
      currentModule.googNamespaces.add(namespace);
      if (existingType != null) {
        switch (existingType) {
          case ES6_MODULE:
          case GOOG_MODULE:
          case LEGACY_GOOG_MODULE:
            t.report(n, ClosureRewriteModule.DUPLICATE_MODULE, namespace);
            return;
          case GOOG_PROVIDE:
            t.report(n, ClosureRewriteModule.DUPLICATE_NAMESPACE, namespace);
            return;
          case COMMON_JS:
          case SCRIPT:
            // Fall through, error
        }
        throw new IllegalStateException("Unexpected module type: " + existingType);
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, new Finder());
    NodeTraversal.traverse(compiler, root, new Finder());
    compiler.setModuleMetadataMap(new ModuleMetadataMap(modulesByPath, modulesByGoogNamespace));
  }
}