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

package com.google.javascript.jscomp.modules;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.rhino.Node;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Contains metadata around modules (or scripts) that is useful for checking imports / requires.
 *
 * <p>TODO(johnplaisted): There's an opportunity for reuse here in ClosureRewriteModules, which
 * would involve putting this in some common location. Currently this is only used as a helper class
 * for Es6RewriteModules. CompilerInput already has some (not all) of this information but it is not
 * always populated. It may also be ideal to include CommonJS here too as ES6 modules can import
 * them. That would allow decoupling of how these modules are written; right now Es6RewriteModule
 * only checks this for goog.requires and goog: imports, not for ES6 path imports.
 */
public final class ModuleMetadataMap {
  /** Various types of Javascript modules and scripts that can be found in the JS Compiler. */
  public enum ModuleType {
    ES6_MODULE("an ES6 module"),
    /** A goog.module that does not declare a legacy namespace. */
    GOOG_MODULE("a goog.module"),
    /** A goog.module that declares a legacy namespace with goog.module.declareLegacyNamespace. */
    LEGACY_GOOG_MODULE("a goog.module"),
    COMMON_JS("a CommonJS module"),
    // The following two cases are not actually modules, but are useful to include in the map.
    GOOG_PROVIDE("a script file that contains at least one goog.provide"),
    SCRIPT("a script file that does not contain a goog.provide");

    public final String description;

    ModuleType(String description) {
      this.description = description;
    }
  }

  /**
   * How a goog.provide or goog.module namespace is seen from TypeScript (via Clutz): as a set of
   * named exports, as a single default export, or omitted because of {@code @nodts}.
   *
   * <p>For example, {@code exports = {A, B};} is NAMED, {@code exports = class Foo {};} is DEFAULT,
   * and {@code /** @nodts *}{@code / exports = class Foo {};} is NO_DTS.
   */
  public enum TsExportType {
    /** The module exports a single default value. */
    DEFAULT,
    /** The module exports multiple named values. */
    NAMED,
    /** Clutz omits the namespace from the .d.ts (and import mappings) because of {@code @nodts}. */
    NO_DTS
  }

  /**
   * Map from module path to module. These modules represent files and thus will contain all goog
   * namespaces that are in the file. These are not the same modules in modulesByGoogNamespace.
   */
  private final ImmutableMap<String, ModuleMetadata> modulesByPath;

  /**
   * Map from Closure namespace to module. These modules represent just the single namespace and
   * thus each module has only one goog namespace in its {@link ModuleMetadata#googNamespaces()}.
   * These are not the same modules in modulesByPath.
   */
  private final ImmutableMap<String, ModuleMetadata> modulesByGoogNamespace;

  private final ImmutableSet<ModuleMetadata> moduleMetadata;

  /**
   * Whether the modules' TypeScript export type facts were computed (see {@link
   * com.google.javascript.jscomp.CompilerOptions#setClassifyTsExportTypes}).
   */
  private final boolean tsExportTypesClassified;

  public ModuleMetadataMap(
      Map<String, ModuleMetadata> modulesByPath,
      Map<String, ModuleMetadata> modulesByGoogNamespace) {
    this(modulesByPath, modulesByGoogNamespace, /* tsExportTypesClassified= */ false);
  }

  public ModuleMetadataMap(
      Map<String, ModuleMetadata> modulesByPath,
      Map<String, ModuleMetadata> modulesByGoogNamespace,
      boolean tsExportTypesClassified) {
    this.modulesByPath = ImmutableMap.copyOf(modulesByPath);
    this.modulesByGoogNamespace = ImmutableMap.copyOf(modulesByGoogNamespace);
    this.moduleMetadata =
        ImmutableSet.<ModuleMetadata>builder()
            .addAll(modulesByPath.values())
            .addAll(modulesByGoogNamespace.values())
            .build();
    this.tsExportTypesClassified = tsExportTypesClassified;
  }

  /**
   * Struct containing basic information about a module/script including its type and goog
   * namespaces.
   */
  @AutoValue
  public abstract static class ModuleMetadata {
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

    public boolean hasLegacyGoogNamespaces() {
      return isGoogProvide() || isLegacyGoogModule();
    }

    public boolean isCommonJs() {
      return moduleType() == ModuleType.COMMON_JS;
    }

    public boolean isNonProvideScript() {
      return moduleType() == ModuleType.SCRIPT;
    }

    /** Whether this is a module (with it's own local scope). */
    public boolean isModule() {
      return switch (moduleType()) {
        case GOOG_PROVIDE, SCRIPT -> false;
        case COMMON_JS, ES6_MODULE, GOOG_MODULE, LEGACY_GOOG_MODULE -> true;
      };
    }

    /**
     * AST node that represents the root of this module.
     *
     * <p>May be null if this is a synthetic piece of metadata - e.g. in a test, or something used
     * as a fallback.
     */
    public abstract @Nullable Node rootNode();

    /**
     * Whether this file uses Closure Library at all. Note that a file could use Closure Library
     * even without calling goog.provide/module/require - there are some primitives in base.js that
     * can be used without being required like goog.isArray.
     *
     * <p>If this is true this indicates the base.js is needed and is not part of this script - it
     * is an EXTERNAL dependencym otherwise false. If this is also false if Closure Library is part
     * of this script - e.g. a bundle with base.js. So something could be a "goog.provide'd file",
     * but not use Closure if the bundle already contains Closure.
     */
    public abstract boolean usesClosure();

    /** Whether goog.setTestOnly was called. */
    public abstract boolean isTestOnly();

    /**
     * Closure namespaces that this file is associated with. Created by goog.provide, goog.module,
     * and goog.declareModuleId.
     *
     * <p>This is a multiset as it does not warn on duplicate namespaces, but will still encapsulate
     * that information with this multiset.
     */
    public abstract ImmutableMultiset<String> googNamespaces();

    /**
     * Closure namespaces this file strongly requires, i.e., arguments to goog.require calls.
     *
     * <p>This is a multiset as it does not warn on duplicate namespaces, but will still encapsulate
     * that information with this multiset.
     */
    public abstract ImmutableMultiset<String> stronglyRequiredGoogNamespaces();

    /**
     * Closure namespaces this file dynamically require, i.e., arguments to goog.requireDynamic()
     * calls.
     *
     * <p>This is a multiset as it does not warn on duplicate namespaces, but will still encapsulate
     * that information with this multiset.
     */
    public abstract ImmutableMultiset<String> dynamicallyRequiredGoogNamespaces();

    /**
     * Closure namespaces this file "maybe" require, i.e., arguments to
     * goog.maybeRequireFrameworkInternalOnlyDoNotCallOrElse() calls.
     *
     * <p>This is a multiset as it does not warn on duplicate namespaces, but will still encapsulate
     * that information with this multiset.
     */
    public abstract ImmutableMultiset<String> maybeRequiredGoogNamespaces();

    /**
     * Closure namespaces this file weakly requires, i.e., arguments to goog.requireType calls.
     *
     * <p>This is a multiset as it does not warn on duplicate namespaces, but will still encapsulate
     * that information with this multiset.
     */
    public abstract ImmutableMultiset<String> weaklyRequiredGoogNamespaces();

    /** Raw text of all ES6 import specifiers (includes "export from" as well). */
    public abstract ImmutableMultiset<String> es6ImportSpecifiers();

    public abstract ImmutableList<ModuleMetadata> nestedModules();

    /**
     * Arguments to goog.readToggleInternalDoNotCallDirectly() calls.
     *
     * <p>This is a multiset as it does not warn on duplicate toggles, but will still encapsulate
     * that information with this multiset.
     */
    public abstract ImmutableMultiset<String> readToggles();

    /**
     * How TypeScript (via Clutz) sees each namespace in {@link #googNamespaces()}.
     *
     * <p>Only populated for goog.provide and goog.module files when {@link
     * com.google.javascript.jscomp.CompilerOptions#setClassifyTsExportTypes} is enabled; see {@link
     * ModuleMetadataMap#getTsExportType}.
     */
    public abstract ImmutableMap<String, TsExportType> tsExportTypes();

    public abstract @Nullable ModulePath path();

    public static Builder builder() {
      return new AutoValue_ModuleMetadataMap_ModuleMetadata.Builder()
          .tsExportTypes(ImmutableMap.of());
    }

    // Use reference equality to prevent bad HashSet<ModuleMetadata> performance on GWT.
    // GatherModuleMetadata is guaranteed to create exactly one ModuleMetadata instance for each
    // input module.
    // NOTE(user): consider removing this override now that GWT & J2CL builds are gone.
    @Override
    public final boolean equals(Object other) {
      return super.equals(other);
    }

    @Override
    public final int hashCode() {
      return super.hashCode();
    }

    /** Builder for {@link ModuleMetadata}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract ModuleMetadata build();

      public abstract ImmutableMultiset.Builder<String> googNamespacesBuilder();

      @CanIgnoreReturnValue
      public Builder addGoogNamespace(String namespace) {
        googNamespacesBuilder().add(namespace);
        return this;
      }

      public abstract ImmutableMultiset.Builder<String> stronglyRequiredGoogNamespacesBuilder();

      public abstract ImmutableMultiset.Builder<String> dynamicallyRequiredGoogNamespacesBuilder();

      public abstract ImmutableMultiset.Builder<String> maybeRequiredGoogNamespacesBuilder();

      public abstract ImmutableMultiset.Builder<String> weaklyRequiredGoogNamespacesBuilder();

      public abstract ImmutableMultiset.Builder<String> es6ImportSpecifiersBuilder();

      public abstract ImmutableList.Builder<ModuleMetadata> nestedModulesBuilder();

      public abstract ImmutableMultiset.Builder<String> readTogglesBuilder();

      public abstract Builder tsExportTypes(Map<String, TsExportType> value);

      public abstract Builder path(@Nullable ModulePath value);

      public abstract Builder usesClosure(boolean value);

      public abstract Builder isTestOnly(boolean value);

      public abstract ModuleType moduleType();

      public abstract Builder moduleType(ModuleType value);

      public abstract Builder rootNode(@Nullable Node root);
    }
  }

  /**
   * @return map from module path to module. These modules represent files and thus {@link
   *     ModuleMetadata#googNamespaces()} contains all Closure namespaces in the file. These are not
   *     the same modules from ModuleMetadataMap#getModulesByGoogNamespace(). It is not valid to
   *     call ModuleRenaming#getGlobalName(ModuleMetadata, String) on {@link
   *     ModuleType#GOOG_PROVIDE} modules from this map that have more than one Closure namespace as
   *     it is ambiguous.
   */
  public ImmutableMap<String, ModuleMetadata> getModulesByPath() {
    return modulesByPath;
  }

  /**
   * @return map from Closure namespace to module. These modules represent the Closure namespace and
   *     thus {@link ModuleMetadata#googNamespaces()} will have size 1. As a result, it is valid to
   *     call ModuleRenaming#getGlobalName(ModuleMetadata, String) on these modules. These are not
   *     the same modules from {@link ModuleMetadataMap#getModulesByPath()}.
   */
  public ImmutableMap<String, ModuleMetadata> getModulesByGoogNamespace() {
    return modulesByGoogNamespace;
  }

  /**
   * The set of all modules across both maps.
   *
   * <p>{@code goog.loadModule} calls have no associated path, and non-Closure modules have no
   * namespaces.
   */
  public ImmutableSet<ModuleMetadata> getAllModuleMetadata() {
    return moduleMetadata;
  }

  /**
   * Returns whether TypeScript sees the given goog namespace as a set of named exports, as a single
   * default export, or as omitted because of {@code @nodts}.
   *
   * <p>This is a purely syntactic approximation of Clutz's type-based classification, so that it
   * can be computed without typechecking (e.g. when extracting JsTrimmer summaries).
   *
   * <p>Returns DEFAULT for namespaces not in this map, and for namespaces that aren't from a
   * goog.provide or goog.module, e.g. from goog.declareModuleId, which aren't classified.
   *
   * @throws IllegalStateException unless this map was built with {@link
   *     com.google.javascript.jscomp.CompilerOptions#setClassifyTsExportTypes} enabled
   */
  public TsExportType getTsExportType(String googNamespace) {
    checkState(
        this.tsExportTypesClassified,
        "TypeScript export types require CompilerOptions#setClassifyTsExportTypes");
    ModuleMetadata module = modulesByGoogNamespace.get(googNamespace);
    return module != null
        ? module.tsExportTypes().getOrDefault(googNamespace, TsExportType.DEFAULT)
        : TsExportType.DEFAULT;
  }

  public static ModuleMetadataMap emptyForTesting() {
    return new ModuleMetadataMap(ImmutableMap.of(), ImmutableMap.of());
  }
}
