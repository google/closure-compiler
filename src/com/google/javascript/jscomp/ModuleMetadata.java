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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
final class ModuleMetadata {
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

  private static final Node GOOG_PROVIDE = IR.getprop(IR.name("goog"), IR.string("provide"));
  private static final Node GOOG_MODULE = IR.getprop(IR.name("goog"), IR.string("module"));
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
   * thus each module has only one goog namespace in its {@link Module#getGoogNamespaces()}. These
   * are not the same modules in modulesByPath.
   */
  private final Map<String, Module> modulesByGoogNamespace = new HashMap<>();

  /** Modules by AST node. */
  private final Map<Node, Module> modulesByNode = new HashMap<>();

  /** The current module being traversed. */
  private ModuleBuilder currentModule;

  /**
   * The module currentModule is nested under, if any. Modules are expected to be at most two deep
   * (a script and then a goog.loadModule call).
   */
  private ModuleBuilder parentModule;

  /** The call to goog.loadModule we are traversing. */
  private Node loadModuleCall;

  private final AbstractCompiler compiler;
  private final boolean processCommonJsModules;
  private final ResolutionMode moduleResolutionMode;
  private Finder finder;

  ModuleMetadata(
      AbstractCompiler compiler,
      boolean processCommonJsModules,
      ResolutionMode moduleResolutionMode) {
    this.compiler = compiler;
    this.processCommonJsModules = processCommonJsModules;
    this.moduleResolutionMode = moduleResolutionMode;
    this.finder = new Finder();
  }

  /** Struct containing basic information about a module including its type and goog namespaces. */
  static final class Module {
    private final ModuleType moduleType;
    private final ModulePath path;
    private final ImmutableList<Module> nestedModules;

    /**
     * Closure namespaces that this file is associated with. Created by goog.provide, goog.module,
     * and goog.module.declareNamespace.
     */
    private final ImmutableSet<String> googNamespaces;

    private Module(
        @Nullable ModulePath path,
        ModuleType moduleType,
        Set<String> googNamespaces,
        List<Module> nestedModules) {
      this.path = path;
      this.moduleType = moduleType;
      this.googNamespaces = ImmutableSet.copyOf(googNamespaces);
      this.nestedModules = ImmutableList.copyOf(nestedModules);
    }

    public ModuleType getModuleType() {
      return moduleType;
    }

    public boolean isEs6Module() {
      return moduleType == ModuleType.ES6_MODULE;
    }

    public boolean isGoogModule() {
      return isNonLegacyGoogModule() || isLegacyGoogModule();
    }

    public boolean isNonLegacyGoogModule() {
      return moduleType == ModuleType.GOOG_MODULE;
    }

    public boolean isLegacyGoogModule() {
      return moduleType == ModuleType.LEGACY_GOOG_MODULE;
    }

    public boolean isGoogProvide() {
      return moduleType == ModuleType.GOOG_PROVIDE;
    }

    public boolean isCommonJs() {
      return moduleType == ModuleType.COMMON_JS;
    }

    public boolean isScript() {
      return moduleType == ModuleType.SCRIPT;
    }

    public ImmutableSet<String> getGoogNamespaces() {
      return googNamespaces;
    }

    @Nullable
    public ModulePath getPath() {
      return path;
    }

    /** @return the global, qualified name to rewrite any references to this module to */
    public String getGlobalName() {
      return getGlobalName(null);
    }

    /** @return the global, qualified name to rewrite any references to this module to */
    public String getGlobalName(@Nullable String googNamespace) {
      checkState(googNamespace == null || googNamespaces.contains(googNamespace));
      switch (moduleType) {
        case GOOG_MODULE:
          return ClosureRewriteModule.getBinaryModuleNamespace(googNamespace);
        case GOOG_PROVIDE:
        case LEGACY_GOOG_MODULE:
          return googNamespace;
        case ES6_MODULE:
        case COMMON_JS:
          return path.toModuleName();
        case SCRIPT:
          // fall through, throw an error
      }
      throw new IllegalStateException("Unexpected module type: " + moduleType);
    }
  }

  private final class ModuleBuilder {
    final Node rootNode;
    @Nullable final ModulePath path;
    final Set<String> googNamespaces;
    final List<Module> nestedModules;
    ModuleType moduleType;
    Node declaresNamespace;
    Node declaresLegacyNamespace;
    boolean ambiguous;

    ModuleBuilder(Node rootNode, @Nullable ModulePath path) {
      this.rootNode = rootNode;
      this.path = path;
      googNamespaces = new HashSet<>();
      nestedModules = new ArrayList<>();
      moduleType = ModuleType.SCRIPT;
      ambiguous = false;
    }

    Module build() {
      if (!ambiguous) {
        if (declaresNamespace != null && moduleType != ModuleType.ES6_MODULE) {
          compiler.report(
              JSError.make(declaresNamespace, DECLARE_MODULE_NAMESPACE_OUTSIDE_ES6_MODULE));
        }

        if (declaresLegacyNamespace != null) {
          if (moduleType == ModuleType.GOOG_MODULE) {
            moduleType = ModuleType.LEGACY_GOOG_MODULE;
          } else {
            compiler.report(
                JSError.make(
                    declaresLegacyNamespace, DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE));
          }
        }
      }

      return new Module(path, moduleType, googNamespaces, nestedModules);
    }

    void setModuleType(ModuleType type, NodeTraversal t, Node n) {
      checkNotNull(type);

      if (moduleType == type) {
        return;
      }

      if (moduleType == ModuleType.SCRIPT) {
        moduleType = type;
        return;
      }

      ambiguous = true;
      t.report(n, MIXED_MODULE_TYPE, moduleType.description, type.description);
    }

    void addGoogNamespace(String namespace) {
      googNamespaces.add(namespace);
    }

    void recordDeclareNamespace(Node declaresNamespace) {
      this.declaresNamespace = declaresNamespace;
    }

    void recordDeclareLegacyNamespace(Node declaresLegacyNamespace) {
      this.declaresLegacyNamespace = declaresLegacyNamespace;
    }

    public boolean isScript() {
      return moduleType == ModuleType.SCRIPT;
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
          checkNotNull(currentModule);
          currentModule.setModuleType(ModuleType.ES6_MODULE, t, n);
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

    private void enterModule(Node n, @Nullable ModulePath path) {
      ModuleBuilder newModule = new ModuleBuilder(n, path);
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
      if (module.path != null) {
        modulesByPath.put(module.path.toString(), module);
      }
      for (String namespace : module.getGoogNamespaces()) {
        modulesByGoogNamespace.put(namespace, module);
      }
      if (parentModule != null) {
        parentModule.nestedModules.add(module);
      }
      currentModule = parentModule;
      parentModule = null;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (processCommonJsModules && currentModule != null && currentModule.isScript()) {
        if (ProcessCommonJSModules.isCommonJsExport(t, n, moduleResolutionMode)
            || ProcessCommonJSModules.isCommonJsImport(n, moduleResolutionMode)) {
          currentModule.setModuleType(ModuleType.COMMON_JS, t, n);
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

    private void visitGoogCall(NodeTraversal t, Node n) {
      if (!n.hasChildren()
          || !n.getFirstChild().isGetProp()
          || !n.getFirstChild().isQualifiedName()) {
        return;
      }

      Node getprop = n.getFirstChild();

      if (getprop.matchesQualifiedName(GOOG_PROVIDE)) {
        currentModule.setModuleType(ModuleType.GOOG_PROVIDE, t, n);
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          String namespace = n.getLastChild().getString();
          currentModule.addGoogNamespace(namespace);
          checkDuplicates(namespace, t, n);
        } else {
          t.report(n, ClosureRewriteModule.INVALID_PROVIDE_NAMESPACE);
        }
      } else if (getprop.matchesQualifiedName(GOOG_MODULE)) {
        currentModule.setModuleType(ModuleType.GOOG_MODULE, t, n);
        if (n.hasTwoChildren() && n.getLastChild().isString()) {
          String namespace = n.getLastChild().getString();
          currentModule.addGoogNamespace(namespace);
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
          currentModule.addGoogNamespace(namespace);
          checkDuplicates(namespace, t, n);
        } else {
          t.report(n, INVALID_DECLARE_NAMESPACE_CALL);
        }
      }
    }

    /** Checks if the given Closure namespace is a duplicate or not. */
    private void checkDuplicates(String namespace, NodeTraversal t, Node n) {
      Module existing = modulesByGoogNamespace.get(namespace);
      if (existing != null) {
        switch (existing.moduleType) {
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
        throw new IllegalStateException("Unexpected module type: " + existing.moduleType);
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
      for (String symbol : module.getGoogNamespaces()) {
        modulesByGoogNamespace.remove(symbol);
      }
      if (module.path != null) {
        modulesByPath.remove(module.path.toString());
      }
      for (Module nested : module.nestedModules) {
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
   *     Module#getGoogNamespaces()} contains all Closure namespaces in the file. These are not the
   *     same modules from {@link ModuleMetadata#getModulesByGoogNamespace()}. It is not valid to
   *     call {@link Module#getGlobalName()} on {@link ModuleType#GOOG_PROVIDE} modules from this
   *     map that have more than one Closure namespace as it is ambiguous.
   */
  Map<String, Module> getModulesByPath() {
    return Collections.unmodifiableMap(modulesByPath);
  }

  /**
   * @return map from Closure namespace to module. These modules represent the Closure namespace and
   *     thus {@link Module#getGoogNamespaces()} will have size 1. As a result, it is valid to call
   *     {@link Module#getGlobalName()} on these modules. These are not the same modules from {@link
   *     ModuleMetadata#getModulesByPath()}.
   */
  Map<String, Module> getModulesByGoogNamespace() {
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
