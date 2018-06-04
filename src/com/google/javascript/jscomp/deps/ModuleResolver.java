/*
 * Copyright 2017 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.deps.ModuleLoader.PathEscaper;
import java.util.Map;
import javax.annotation.Nullable;

/** Base class for algorithms that resolve JavaScript module references to input files. */
public abstract class ModuleResolver {
  /** The set of all known input module URIs (including trailing .js), after normalization. */
  protected final ImmutableSet<String> modulePaths;

  /** Root URIs to match module roots against. */
  protected final ImmutableList<String> moduleRootPaths;

  protected ErrorHandler errorHandler;
  private final PathEscaper pathEscaper;

  public ModuleResolver(
      ImmutableSet<String> modulePaths,
      ImmutableList<String> moduleRootPaths,
      ErrorHandler errorHandler,
      ModuleLoader.PathEscaper pathEscaper) {
    this.modulePaths = modulePaths;
    this.moduleRootPaths = moduleRootPaths;
    this.errorHandler = errorHandler;
    this.pathEscaper = pathEscaper;
  }

  Map<String, String> getPackageJsonMainEntries() {
    return ImmutableMap.of();
  }

  @Nullable
  public abstract String resolveJsModule(
      String scriptAddress, String moduleAddress, String sourcename, int lineno, int colno);

  public String resolveModuleAsPath(String scriptAddress, String moduleAddress) {
    if (!moduleAddress.endsWith(".js")) {
      moduleAddress += ".js";
    }
    String path = pathEscaper.escape(moduleAddress);
    if (ModuleLoader.isRelativeIdentifier(moduleAddress)) {
      String ourPath = scriptAddress;
      int lastIndex = ourPath.lastIndexOf(ModuleLoader.MODULE_SLASH);
      path =
          ModuleNames.canonicalizePath(
              ourPath.substring(0, lastIndex + ModuleLoader.MODULE_SLASH.length()) + path);
    }
    return ModuleLoader.normalize(path, moduleRootPaths);
  }

  /**
   * Locates the module with the given name, but returns null if there is no JS file in the expected
   * location.
   */
  @Nullable
  protected String locate(String scriptAddress, String name) {
    String canonicalizedPath = canonicalizePath(scriptAddress, name);

    String normalizedPath = canonicalizedPath;
    if (ModuleLoader.isAmbiguousIdentifier(canonicalizedPath)) {
      normalizedPath = ModuleLoader.MODULE_SLASH + canonicalizedPath;
    }

    // First check to see if the module is known with it's provided path
    if (modulePaths.contains(normalizedPath)) {
      return canonicalizedPath;
    }

    // Check for the module beneath each of the module roots
    for (String rootPath : moduleRootPaths) {
      String modulePath = rootPath + normalizedPath;

      // Since there might be code that relying on whether the path has a leading slash or not,
      // honor the state it was provided in. In an ideal world this would always be normalized
      // to contain a leading slash.
      if (modulePaths.contains(modulePath)) {
        return canonicalizedPath;
      }
    }

    return null;
  }

  /**
   * Normalizes a module path reference. Includes escaping special characters and converting
   * relative paths to absolute references.
   */
  protected String canonicalizePath(String scriptAddress, String moduleAddress) {
    String path = pathEscaper.escape(moduleAddress);
    if (ModuleLoader.isRelativeIdentifier(moduleAddress)) {
      String ourPath = scriptAddress;
      int lastIndex = ourPath.lastIndexOf(ModuleLoader.MODULE_SLASH);
      path =
          ModuleNames.canonicalizePath(
              ourPath.substring(0, lastIndex + ModuleLoader.MODULE_SLASH.length()) + path);
    }
    return path;
  }

  public void setErrorHandler(ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
  }
}
