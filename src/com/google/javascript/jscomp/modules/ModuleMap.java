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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.deps.ModuleLoader;
import org.jspecify.nullness.Nullable;

/**
 * A map containing information about all modules in the compilation.
 *
 * <p>This is currently used for ES modules and other types of module are not processed in detail.
 */
public final class ModuleMap {
  private final ImmutableMap<String, Module> resolvedModules;
  private final ImmutableMap<String, Module> resolvedClosureModules;

  public ModuleMap(
      ImmutableMap<String, Module> resolvedModules,
      ImmutableMap<String, Module> resolvedClosureModules) {
    this.resolvedModules = resolvedModules;
    this.resolvedClosureModules = resolvedClosureModules;
  }

  public @Nullable Module getModule(String moduleName) {
    return resolvedModules.get(moduleName);
  }

  public @Nullable Module getModule(ModuleLoader.ModulePath path) {
    return getModule(path.toModuleName());
  }

  public ImmutableMap<String, Module> getModulesByPath() {
    return resolvedModules;
  }

  public ImmutableMap<String, Module> getModulesByClosureNamespace() {
    return resolvedClosureModules;
  }

  public @Nullable Module getClosureModule(String namespace) {
    return resolvedClosureModules.get(namespace);
  }

  public static ModuleMap emptyForTesting() {
    return new ModuleMap(ImmutableMap.of(), ImmutableMap.of());
  }
}
