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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Gathers metadata around modules that is useful for checking imports / requires.
 *
 * <p>TODO(johnplaisted): There's an opportunity for reuse here in ClosureRewriteModules, which
 * would involve putting this in some common location. Currently this is only used as a helper class
 * for Es6RewriteModules. CompilerInput already has some (not all) of this information but it is not
 * always populated. It may also be ideal to include CommonJS here too as ES6 modules can import
 * them. That would allow decoupling of how these modules are written; right now Es6RewriteModule
 * only checks this for goog.requires and goog: imports, not for ES6 path imports.
 */
public final class ModuleMetadata {
  /** Various types of Javascript "modules" that can be found in the JS Compiler. */
  public enum ModuleType {
    ES6_MODULE("an ES6 module"),
    GOOG_PROVIDE("a goog.provide'd file"),
    /** A goog.module that does not declare a legacy namespace. */
    GOOG_MODULE("a goog.module"),
    /** A goog.module that declares a legacy namespace with goog.module.declareLegacyNamespace. */
    LEGACY_GOOG_MODULE("a goog.module"),
    COMMON_JS("a CommonJS module"),
    SCRIPT("a script");

    private final String description;

    ModuleType(String description) {
      this.description = description;
    }
  }

  static final DiagnosticType MIXED_MODULE_TYPE =
      DiagnosticType.error("JSC_MIXED_MODULE_TYPE", "A file cannot be both {0} and {1}.");

  static final DiagnosticType INVALID_DECLARE_NAMESPACE_CALL =
      DiagnosticType.error(
          "JSC_INVALID_DECLARE_NAMESPACE_CALL",
          "goog.module.declareNamespace parameter must be a string literal.");

  static final DiagnosticType DECLARE_MODULE_NAMESPACE_OUTSIDE_ES6_MODULE =
      DiagnosticType.error(
          "JSC_DECLARE_MODULE_NAMESPACE_OUTSIDE_ES6_MODULE",
          "goog.module.declareNamespace can only be called within ES6 modules.");

  static final DiagnosticType MULTIPLE_DECLARE_MODULE_NAMESPACE =
      DiagnosticType.error(
          "JSC_MULTIPLE_DECLARE_MODULE_NAMESPACE",
          "goog.module.declareNamespace can only be called once per ES6 module.");

  static final DiagnosticType INVALID_REQUIRE_TYPE =
      DiagnosticType.error(
          "JSC_INVALID_REQUIRE_TYPE", "Argument to goog.requireType must be a string.");

  private static final Node GOOG_PROVIDE = IR.getprop(IR.name("goog"), IR.string("provide"));
  private static final Node GOOG_MODULE = IR.getprop(IR.name("goog"), IR.string("module"));
  private static final Node GOOG_REQUIRE = IR.getprop(IR.name("goog"), IR.string("require"));
  private static final Node GOOG_REQUIRE_TYPE =
      IR.getprop(IR.name("goog"), IR.string("requireType"));
  private static final Node GOOG_SET_TEST_ONLY =
      IR.getprop(IR.name("goog"), IR.string("setTestOnly"));
  private static final Node GOOG_MODULE_DECLARELEGACYNAMESPACE =
      IR.getprop(GOOG_MODULE.cloneTree(), IR.string("declareLegacyNamespace"));
  private static final Node GOOG_MODULE_DECLARNAMESPACE =
      IR.getprop(GOOG_MODULE.cloneTree(), IR.string("declareNamespace"));

  /**
   * Map from module path to module. These modules represent files and thus will contain all goog
   * namespaces that are in the file. These are not the same modules in modulesByGoogNamespace.
   */
  private final Map<String, Module> modulesByPath = new HashMap<>();

  /**
   * Map from Closure namespace to module. These modules represent just the single namespace and
   * thus each module has only one goog namespace in its {@link Module#googNamespaces()}. These
   * are not the same modules in modulesByPath.
   */
  private final Map<String, Module> modulesByGoogNamespace = new HashMap<>();

  /** Modules by AST node. */
  private final Map<Node, Module> modulesByNode = new HashMap<>();

  /** The current module being traversed. */
  private Module.Builder currentModule;

  /**
   * The module currentModule is nested under, if any. Modules are expected to be at most two deep
   * (a script and then a goog.loadModule call).
   */
  private Module.Builder parentModule;

  /** The call to goog.loadModule we are traversing. */
  private Node loadModuleCall;

  private final AbstractCompiler compiler;
  private final boolean processCommonJsModules;
  private final ResolutionMode moduleResolutionMode;
  private Finder finder;

  public ModuleMetadata(AbstractCompiler compiler) {
    this(compiler, false, ResolutionMode.BROWSER);
  }

  public ModuleMetadata(
      AbstractCompiler compiler,
      boolean processCommonJsModules,
      ResolutionMode moduleResolutionMode) {
    this.compiler = compiler;
    this.processCommonJsModules = processCommonJsModules;
    this.moduleResolutionMode = moduleResolutionMode;
    this.finder = new Finder();
  }

  /** Struct containing basic information about a module including its type and goog namespaces. */
  @AutoValue
  public abstract static class Module {
    public abstract ModuleType moduleType();

    public boolean isEs6Module() {
      return moduleType() == ModuleType.ES6_MODULE;
    }

    public boolean isGoogModule() {
      return isNonLegacyGoogModule() || isLegacyGoogModule();
    }

    public boolean isNonLegacyGoogModule() {
      return moduleType() == ModuleType.GOOG_MODULE;
    }

    public boolean isLegacyGoogModule() {
      return moduleType() == ModuleType.LEGACY_GOOG_MODULE;
    }

    public boolean isGoogProvide() {
      return moduleType() == ModuleType.GOOG_PROVIDE;
    }

    public boolean isCommonJs() {
      return moduleType() == ModuleType.COMMON_JS;
    }

    public boolean isScript() {
      return moduleType() == ModuleType.SCRIPT;
    }

    /**
     * Whether this file uses Closure Library at all. Note that a file could use Closure Library
     * even without calling goog.provide/module/require - there are some primitives in base.js that
     * can be used without being required like goog.isArray.
     */
    public abstract boolean usesClosure();

    /** Whether goog.setTestOnly was called. */
    public abstract boolean isTestOnly();

    /**
     * Closure namespaces that this file is associated with. Created by goog.provide, goog.module,
     * and goog.module.declareNamespace.
     */
    public abstract ImmutableSet<String> googNamespaces();

    /** Closure namespaces this file requires. e.g. all arguments to goog.require calls. */
    public abstract ImmutableSet<String> requiredGoogNamespaces();

    /**
     * Closure namespaces this file has weak dependencies on. e.g. all arguments to goog.requireType
     * calls.
     */
    public abstract ImmutableSet<String> requiredTypes();

    /** Raw text of all ES6 import specifiers (includes "export from" as well). */
    public abstract ImmutableSet<String> es6ImportSpecifiers();

    abstract ImmutableList<Module> nestedModules();

    @Nullable
    public abstract ModulePath path();

    /** @return the global, qualified name to rewrite any references to this module to */
    public String getGlobalName() {
      return getGlobalName(null);
    }

    /** @return the global, qualified name to rewrite any references to this module to */
    public String getGlobalName(@Nullable String googNamespace) {
      checkState(googNamespace == null || googNamespaces().contains(googNamespace));
      switch (moduleType()) {
        case GOOG_MODULE:
          return ClosureRewriteModule.getBinaryModuleNamespace(googNamespace);
        case GOOG_PROVIDE:
        case LEGACY_GOOG_MODULE:
          return googNamespace;
        case ES6_MODULE:
        case COMMON_JS:
          return path().toModuleName();
        case SCRIPT:
          // fall through, throw an error
      }
      throw new IllegalStateException("Unexpected module type: " + moduleType());
    }

    private static Builder builder(
        AbstractCompiler compiler, Node rootNode, @Nullable ModulePath path) {
      Builder builder = new AutoValue_ModuleMetadata_Module.Builder();
      builder.compiler = compiler;
      builder.rootNode = rootNode;
      return builder.path(path).moduleType(ModuleType.SCRIPT).usesClosure(false).isTestOnly(false);
    }

    @AutoValue.Builder
    abstract static class Builder {
      private boolean ambiguous;
      private Node declaresNamespace;
      private Node declaresLegacyNamespace;
      private Node rootNode;
      private AbstractCompiler compiler;

      abstract Module buildInternal();
      abstract ImmutableSet.Builder<String> googNamespacesBuilder();
      abstract ImmutableSet.Builder<String> requiredGoogNamespacesBuilder();
      abstract ImmutableSet.Builder<String> requiredTypesBuilder();
      abstract ImmutableSet.Builder<String> es6ImportSpecifiersBuilder();
      abstract ImmutableList.Builder<Module> nestedModulesBuilder();
      abstract Builder path(@Nullable ModulePath value);
      abstract Builder usesClosure(boolean value);
      abstract Builder isTestOnly(boolean value);

      abstract ModuleType moduleType();
      abstract Builder moduleType(ModuleType value);
      void moduleType(ModuleType type, NodeTraversal t, Node n) {
        checkNotNull(type);

        if (moduleType() == type) {
          return;
        }

        if (moduleType() == ModuleType.SCRIPT) {
          moduleType(type);
          return;
        }

        ambiguous = true;
        t.report(n, MIXED_MODULE_TYPE, moduleType().description, type.description);
      }

      void recordDeclareNamespace(Node declaresNamespace) {
        this.declaresNamespace = declaresNamespace;
      }

      void recordDeclareLegacyNamespace(Node declaresLegacyNamespace) {
        this.declaresLegacyNamespace = declaresLegacyNamespace;
      }

      boolean isScript() {
        return moduleType() == ModuleType.SCRIPT;
      }

      Module build() {
        if (!ambiguous) {
          if (declaresNamespace != null && moduleType() != ModuleType.ES6_MODULE) {
            compiler.report(
                JSError.make(declaresNamespace, DECLARE_MODULE_NAMESPACE_OUTSIDE_ES6_MODULE));
          }

          if (declaresLegacyNamespace != null) {
            if (moduleType() == ModuleType.GOOG_MODULE) {
              moduleType(ModuleType.LEGACY_GOOG_MODULE);
            } else {
              compiler.report(
                  JSError.make(declaresLegacyNamespace, DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE));
            }
          }
        }

        return buildInternal();
      }
    }
  }

  /** Traverses the AST and build a sets of {@link Module}s. */
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
        currentModule.es6ImportSpecifiersBuilder().add(importOrExport.getLastChild().getString());
      }
    }

    private void enterModule(Node n, @Nullable ModulePath path) {
      Module.Builder newModule = Module.builder(compiler, n, path);
      if (currentModule != null) {
        checkState(parentModule == null, "Expected modules to be nested at most 2 deep.");
        parentModule = currentModule;
      }
      currentModule = newModule;
    }

    private void leaveModule() {
      checkNotNull(currentModule);
      Module module = currentModule.build();
      modulesByNode.put(currentModule.rootNode, module);
      if (module.path() != null) {
        modulesByPath.put(module.path().toString(), module);
      }
      for (String namespace : module.googNamespaces()) {
        modulesByGoogNamespace.put(namespace, module);
      }
      if (parentModule != null) {
        parentModule.nestedModulesBuilder().add(module);
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
      if (root != null && !isFromGoogImport(root)) {
        return;
      }

      currentModule.usesClosure(true);

      if (getprop.matchesQualifiedName(GOOG_PROVIDE)) {
        currentModule.moduleType(ModuleType.GOOG_PROVIDE, t, n);
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          String namespace = n.getLastChild().getString();
          currentModule.googNamespacesBuilder().add(namespace);
          checkDuplicates(namespace, t, n);
        } else {
          t.report(n, ClosureRewriteModule.INVALID_PROVIDE_NAMESPACE);
        }
      } else if (getprop.matchesQualifiedName(GOOG_MODULE)) {
        currentModule.moduleType(ModuleType.GOOG_MODULE, t, n);
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          String namespace = n.getLastChild().getString();
          currentModule.googNamespacesBuilder().add(namespace);
          checkDuplicates(namespace, t, n);
        } else {
          t.report(n, ClosureRewriteModule.INVALID_MODULE_NAMESPACE);
        }
      } else if (getprop.matchesQualifiedName(GOOG_MODULE_DECLARELEGACYNAMESPACE)) {
        currentModule.recordDeclareLegacyNamespace(n);
      } else if (getprop.matchesQualifiedName(GOOG_MODULE_DECLARNAMESPACE)) {
        if (currentModule.declaresNamespace != null) {
          t.report(n, MULTIPLE_DECLARE_MODULE_NAMESPACE);
        }
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          currentModule.recordDeclareNamespace(n);
          String namespace = n.getLastChild().getString();
          currentModule.googNamespacesBuilder().add(namespace);
          checkDuplicates(namespace, t, n);
        } else {
          t.report(n, INVALID_DECLARE_NAMESPACE_CALL);
        }
      } else if (getprop.matchesQualifiedName(GOOG_REQUIRE)) {
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          currentModule.requiredGoogNamespacesBuilder().add(n.getLastChild().getString());
        } else {
          t.report(n, ClosureRewriteModule.INVALID_REQUIRE_NAMESPACE);
        }
      } else if (getprop.matchesQualifiedName(GOOG_REQUIRE_TYPE)) {
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          currentModule.requiredTypesBuilder().add(n.getLastChild().getString());
        } else {
          t.report(n, INVALID_REQUIRE_TYPE);
        }
      } else if (getprop.matchesQualifiedName(GOOG_SET_TEST_ONLY)) {
        currentModule.isTestOnly(true);
      }
    }

    /** Checks if the given Closure namespace is a duplicate or not. */
    private void checkDuplicates(String namespace, NodeTraversal t, Node n) {
      Module existing = modulesByGoogNamespace.get(namespace);
      if (existing != null) {
        switch (existing.moduleType()) {
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
        throw new IllegalStateException("Unexpected module type: " + existing.moduleType());
      }
    }
  }

  public void process(Node externs, Node root) {
    finder = new Finder();
    NodeTraversal.traverse(compiler, externs, finder);
    NodeTraversal.traverse(compiler, root, finder);
  }

  private void remove(Module module) {
    if (module != null) {
      for (String symbol : module.googNamespaces()) {
        modulesByGoogNamespace.remove(symbol);
      }
      if (module.path() != null) {
        modulesByPath.remove(module.path().toString());
      }
      for (Module nested : module.nestedModules()) {
        remove(nested);
      }
    }
  }

  public void hotSwapScript(Node scriptRoot) {
    Module existing =
        modulesByPath.get(compiler.getInput(scriptRoot.getInputId()).getPath().toString());
    remove(existing);
    NodeTraversal.traverse(compiler, scriptRoot, finder);
  }

  /**
   * @return map from module path to module. These modules represent files and thus {@link
   *     Module#googNamespaces()} contains all Closure namespaces in the file. These are not the
   *     same modules from {@link ModuleMetadata#getModulesByGoogNamespace()}. It is not valid to
   *     call {@link Module#getGlobalName()} on {@link ModuleType#GOOG_PROVIDE} modules from this
   *     map that have more than one Closure namespace as it is ambiguous.
   */
  public Map<String, Module> getModulesByPath() {
    return Collections.unmodifiableMap(modulesByPath);
  }

  /**
   * @return map from Closure namespace to module. These modules represent the Closure namespace and
   *     thus {@link Module#googNamespaces()} will have size 1. As a result, it is valid to call
   *     {@link Module#getGlobalName()} on these modules. These are not the same modules from {@link
   *     ModuleMetadata#getModulesByPath()}.
   */
  public Map<String, Module> getModulesByGoogNamespace() {
    return Collections.unmodifiableMap(modulesByGoogNamespace);
  }

  /** @return the {@link Module} that contains the given AST node */
  @Nullable
  Module getContainingModule(Node n) {
    if (finder == null) {
      return null;
    }
    Module m = null;
    while (m == null && n != null) {
      m = modulesByNode.get(n);
      n = n.getParent();
    }
    return m;
  }
}
