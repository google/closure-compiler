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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Contains metadata around modules that is useful for checking imports / requires.
 *
 * <p>TODO(johnplaisted): There's an opportunity for reuse here in ClosureRewriteModules, which
 * would involve putting this in some common location. Currently this is only used as a helper class
 * for Es6RewriteModules. CompilerInput already has some (not all) of this information but it is not
 * always populated. It may also be ideal to include CommonJS here too as ES6 modules can import
 * them. That would allow decoupling of how these modules are written; right now Es6RewriteModule
 * only checks this for goog.requires and goog: imports, not for ES6 path imports.
 */
public final class ModuleMetadataMap {
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

  public ModuleMetadataMap(
      Map<String, ModuleMetadata> modulesByPath,
      Map<String, ModuleMetadata> modulesByGoogNamespace) {
    this.modulesByPath = ImmutableMap.copyOf(modulesByPath);
    this.modulesByGoogNamespace = ImmutableMap.copyOf(modulesByGoogNamespace);
  }

  /** Struct containing basic information about a module including its type and goog namespaces. */
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
     * and goog.declareModuleId.
     */
    public abstract ImmutableMultiset<String> googNamespaces();

    /** Closure namespaces this file requires. e.g. all arguments to goog.require calls. */
    public abstract ImmutableMultiset<String> requiredGoogNamespaces();

    /**
     * Closure namespaces this file has weak dependencies on. e.g. all arguments to goog.requireType
     * calls.
     */
    public abstract ImmutableMultiset<String> requiredTypes();

    /** Raw text of all ES6 import specifiers (includes "export from" as well). */
    public abstract ImmutableMultiset<String> es6ImportSpecifiers();

    abstract ImmutableList<ModuleMetadata> nestedModules();

    @Nullable
    public abstract ModulePath path();

    public static Builder builder() {
      return new AutoValue_ModuleMetadataMap_ModuleMetadata.Builder();
    }

    /** Builder for {@link ModuleMetadata}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract ModuleMetadata build();

      public abstract ImmutableMultiset.Builder<String> googNamespacesBuilder();

      public abstract ImmutableMultiset.Builder<String> requiredGoogNamespacesBuilder();

      public abstract ImmutableMultiset.Builder<String> requiredTypesBuilder();

      public abstract ImmutableMultiset.Builder<String> es6ImportSpecifiersBuilder();

      public abstract ImmutableList.Builder<ModuleMetadata> nestedModulesBuilder();

      public abstract Builder path(@Nullable ModulePath value);

      public abstract Builder usesClosure(boolean value);

      public abstract Builder isTestOnly(boolean value);

      public abstract ModuleType moduleType();

      public abstract Builder moduleType(ModuleType value);
    }
  }

  /**
   * @return map from module path to module. These modules represent files and thus {@link
   *     ModuleMetadata#googNamespaces()} contains all Closure namespaces in the file. These are not
   *     the same modules from {@link ModuleMetadataMap#getModulesByGoogNamespace()}. It is not
   *     valid to call {@link ModuleRenaming#getGlobalName(ModuleMetadata, String)} on {@link
   *     ModuleType#GOOG_PROVIDE} modules from this map that have more than one Closure namespace as
   *     it is ambiguous.
   */
  public ImmutableMap<String, ModuleMetadata> getModulesByPath() {
    return modulesByPath;
  }

  /**
   * @return map from Closure namespace to module. These modules represent the Closure namespace and
   *     thus {@link ModuleMetadata#googNamespaces()} will have size 1. As a result, it is valid to
   *     call {@link ModuleRenaming#getGlobalName(ModuleMetadata, String)} on these modules. These
   *     are not the same modules from {@link ModuleMetadataMap#getModulesByPath()}.
   */
  public ImmutableMap<String, ModuleMetadata> getModulesByGoogNamespace() {
    return modulesByGoogNamespace;
  }
}
