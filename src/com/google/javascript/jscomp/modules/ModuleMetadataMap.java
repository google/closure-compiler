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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.rhino.Node;
import java.util.Map;
import org.jspecify.nullness.Nullable;

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

  public ModuleMetadataMap(
      Map<String, ModuleMetadata> modulesByPath,
      Map<String, ModuleMetadata> modulesByGoogNamespace) {
    this.modulesByPath = ImmutableMap.copyOf(modulesByPath);
    this.modulesByGoogNamespace = ImmutableMap.copyOf(modulesByGoogNamespace);
    this.moduleMetadata =
        ImmutableSet.<ModuleMetadata>builder()
            .addAll(modulesByPath.values())
            .addAll(modulesByGoogNamespace.values())
            .build();
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

    public boolean isCommonJs() {
      return moduleType() == ModuleType.COMMON_JS;
    }

    public boolean isNonProvideScript() {
      return moduleType() == ModuleType.SCRIPT;
    }

    /** Whether this is a module (with it's own local scope). */
    public boolean isModule() {
      switch (moduleType()) {
        case GOOG_PROVIDE:
        case SCRIPT:
          return false;
        case COMMON_JS:
        case ES6_MODULE:
        case GOOG_MODULE:
        case LEGACY_GOOG_MODULE:
          return true;
      }
      throw new AssertionError(moduleType());
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

    public abstract @Nullable ModulePath path();

    public static Builder builder() {
      return new AutoValue_ModuleMetadataMap_ModuleMetadata.Builder();
    }

    // Use reference equality to prevent bad HashSet<ModuleMetadata> performance on GWT.
    // GatherModuleMetadata is guaranteed to create exactly one ModuleMetadata instance for each
    // input module.
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

  public static ModuleMetadataMap emptyForTesting() {
    return new ModuleMetadataMap(ImmutableMap.of(), ImmutableMap.of());
  }
}
