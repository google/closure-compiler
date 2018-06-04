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

package com.google.javascript.jscomp.deps;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.deps.ModuleLoader.ModuleResolverFactory;
import com.google.javascript.jscomp.deps.ModuleLoader.PathEscaper;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/**
 * Resolution algorithm for Webpack. Modules are located by a map of numeric ids to module paths.
 *
 * <p>As the compiler normally locates modules by path string, webpack numeric ids are converted to
 * strings.
 */
public class WebpackModuleResolver extends NodeModuleResolver {
  private final ImmutableMap<String, String> modulesById;

  /**
   * Uses a lookup map provided by webpack to locate modules from a numeric id used during import
   */
  public static final class Factory implements ModuleResolverFactory {
    private final Map<String, String> lookupMap;

    public Factory(Map<String, String> lookupMap) {
      this.lookupMap = lookupMap;
    }

    @Override
    public ModuleResolver create(
        ImmutableSet<String> modulePaths,
        ImmutableList<String> moduleRootPaths,
        ErrorHandler errorHandler,
        PathEscaper pathEscaper) {
      Map<String, String> normalizedPathsById = new HashMap<>();
      for (Entry<String, String> moduleEntry : lookupMap.entrySet()) {
        String canonicalizedPath =
            ModuleLoader.normalize(pathEscaper.escape(moduleEntry.getValue()), moduleRootPaths);
        if (ModuleLoader.isAmbiguousIdentifier(canonicalizedPath)) {
          canonicalizedPath = ModuleLoader.MODULE_SLASH + canonicalizedPath;
        }
        normalizedPathsById.put(moduleEntry.getKey(), canonicalizedPath);
      }
      return new WebpackModuleResolver(
          modulePaths, moduleRootPaths, normalizedPathsById, errorHandler, pathEscaper);
    }
  }

  public WebpackModuleResolver(
      ImmutableSet<String> modulePaths,
      ImmutableList<String> moduleRootPaths,
      Map<String, String> modulesById,
      ErrorHandler errorHandler,
      PathEscaper pathEscaper) {
    super(
        modulePaths,
        moduleRootPaths,
        /* packageJsonMainEntries= */ null,
        errorHandler,
        pathEscaper);

    this.modulesById = ImmutableMap.copyOf(modulesById);
  }

  @Override
  @Nullable
  public String resolveJsModule(
      String scriptAddress, String moduleAddress, String sourcename, int lineno, int colno) {
    String loadAddress = modulesById.get(moduleAddress);
    if (loadAddress == null) {
      // Module paths may still be used in type nodes so we need to fall back
      // to node module resolution for those.
      return super.resolveJsModule(scriptAddress, moduleAddress, sourcename, lineno, colno);
    }
    return loadAddress;
  }
}
