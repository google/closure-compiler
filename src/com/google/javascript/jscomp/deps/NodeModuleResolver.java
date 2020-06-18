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
import com.google.common.collect.ImmutableSortedSet;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.deps.ModuleLoader.ModuleResolverFactory;
import com.google.javascript.jscomp.deps.ModuleLoader.PathEscaper;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * Resolution algorithm for NodeJS. See https://nodejs.org/api/modules.html#modules_all_together
 *
 * <p>Unambiguous paths are file paths resolved from the current script. Ambiguous paths are located
 * within the nearest node_modules folder ancestor.
 */
public class NodeModuleResolver extends ModuleResolver {
  private static final String[] FILE_EXTENSIONS_TO_SEARCH = {
    "",
    ".js",
    ".json",
    // .i.js is typed interfaces. See ConvertToTypedInterface.java
    ".i.js",
    ".js.i.js"
  };
  private static final String[] FILES_TO_SEARCH = {
    ModuleLoader.MODULE_SLASH + "package.json",
    ModuleLoader.MODULE_SLASH + "index.js",
    ModuleLoader.MODULE_SLASH + "index.json"
  };

  /** Named modules found in node_modules folders */
  private final ImmutableMap<String, String> packageJsonMainEntries;

  /** Named modules found in node_modules folders */
  private final ImmutableSortedSet<String> nodeModulesFolders;

  /**
   * Build a list of node module paths. Given the following path:
   *
   * <p>/foo/node_modules/bar/node_modules/baz/foo_bar_baz.js
   *
   * <p>Return a set containing:
   *
   * <p>/foo/ /foo/node_modules/bar/
   *
   * @param modulePaths Set of all module paths where the key is the module path normalized to have
   *     a leading slash
   * @param moduleRootPaths Possibly empty list of root paths that should be ignored when processing
   *     module paths.
   * @return A sorted set with the longest paths first where each entry is the folder containing a
   *     node_modules sub-folder.
   */
  private static ImmutableSortedSet<String> buildNodeModulesFoldersRegistry(
      Iterable<String> modulePaths, Iterable<String> moduleRootPaths) {
    SortedSet<String> registry =
        new TreeSet<>(
            // TODO(b/28382956): Take better advantage of Java8 comparing() to simplify this
            (a, b) -> {
              // Order longest path first
              int comparison = Integer.compare(b.length(), a.length());
              if (comparison != 0) {
                return comparison;
              }

              return a.compareTo(b);
            });

    // For each modulePath, find all the node_modules folders
    // There might be more than one:
    //    /foo/node_modules/bar/node_modules/baz/foo_bar_baz.js
    // Should add:
    //   /foo/ -> bar/node_modules/baz/foo_bar_baz.js
    //   /foo/node_modules/bar/ -> baz/foo_bar_baz.js
    //
    // If there are root paths provided - they should be ignored from paths. For example if
    // root paths contains "/generated" root then the following module path should produce exact
    // same result as above:
    //    /generated/foo/node_modules/bar/node_modules/baz/foo_bar_baz.js
    for (String modulePath : modulePaths) {
      // Strip root path from the beginning if it matches any.
      for (String moduleRootPath : moduleRootPaths) {
        if (modulePath.startsWith(moduleRootPath)) {
          modulePath = modulePath.substring(moduleRootPath.length());
          break;
        }
      }
      String[] nodeModulesDirs = modulePath.split("/node_modules/");
      String parentPath = "";

      for (int i = 0; i < nodeModulesDirs.length - 1; i++) {
        if (i + 1 < nodeModulesDirs.length) {
          parentPath += nodeModulesDirs[i] + "/";
        }

        registry.add(parentPath);
        parentPath += "node_modules/";
      }
    }

    return ImmutableSortedSet.copyOfSorted(registry);
  }

  /** Factory for {@link NodeModuleResolver}. */
  public static final class Factory implements ModuleResolverFactory {
    private final Map<String, String> packageJsonMainEntries;

    public Factory() {
      this(/* packageJsonMainEntries= */ null);
    }

    public Factory(@Nullable Map<String, String> packageJsonMainEntries) {
      this.packageJsonMainEntries = packageJsonMainEntries;
    }

    @Override
    public ModuleResolver create(
        ImmutableSet<String> modulePaths,
        ImmutableList<String> moduleRootPaths,
        ErrorHandler errorHandler,
        PathEscaper pathEscaper) {
      return new NodeModuleResolver(
          modulePaths, moduleRootPaths, packageJsonMainEntries, errorHandler, pathEscaper);
    }
  }

  public NodeModuleResolver(
      ImmutableSet<String> modulePaths,
      ImmutableList<String> moduleRootPaths,
      Map<String, String> packageJsonMainEntries,
      ErrorHandler errorHandler,
      PathEscaper pathEscaper) {
    super(modulePaths, moduleRootPaths, errorHandler, pathEscaper);
    this.nodeModulesFolders = buildNodeModulesFoldersRegistry(modulePaths, moduleRootPaths);

    if (packageJsonMainEntries == null) {
      this.packageJsonMainEntries = ImmutableMap.of();
    } else {
      this.packageJsonMainEntries = buildPackageJsonMainEntries(packageJsonMainEntries);
    }
  }

  /**
   * @param packageJsonMainEntries a map with keys that are package.json file paths and values which
   *     are the "main" entry from the package.json. "main" entries are absolute paths rooted from
   *     the folder containing the package.json file.
   */
  private ImmutableMap<String, String> buildPackageJsonMainEntries(
      Map<String, String> packageJsonMainEntries) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (Map.Entry<String, String> packageJsonMainEntry : packageJsonMainEntries.entrySet()) {
      String entryKey = packageJsonMainEntry.getKey();
      if (ModuleLoader.isAmbiguousIdentifier(entryKey)) {
        entryKey = ModuleLoader.MODULE_SLASH + entryKey;
      }

      builder.put(entryKey, packageJsonMainEntry.getValue());
    }

    return builder.build();
  }

  @Override
  Map<String, String> getPackageJsonMainEntries() {
    return this.packageJsonMainEntries;
  }

  @Override
  @Nullable
  public String resolveJsModule(
      String scriptAddress, String moduleAddress, String sourcename, int lineno, int colno) {
    String loadAddress;
    if (ModuleLoader.isAbsoluteIdentifier(moduleAddress)
        || ModuleLoader.isRelativeIdentifier(moduleAddress)) {
      loadAddress = resolveJsModuleNodeFileOrDirectory(scriptAddress, moduleAddress);
    } else {
      loadAddress = resolveJsModuleFromRegistry(scriptAddress, moduleAddress);
    }

    if (loadAddress == null) {
      errorHandler.report(
          CheckLevel.WARNING,
          JSError.make(sourcename, lineno, colno, ModuleLoader.LOAD_WARNING, moduleAddress));
    }
    return loadAddress;
  }

  public String resolveJsModuleFile(String scriptAddress, String moduleAddress) {
    for (String extension : FILE_EXTENSIONS_TO_SEARCH) {
      String moduleAddressCandidate = moduleAddress + extension;
      String canonicalizedCandidatePath = canonicalizePath(scriptAddress, moduleAddressCandidate);

      // Also look for mappings in packageJsonMainEntries because browser field
      // advanced usage allows to override / skip specific files, including
      // the main entry.
      if (packageJsonMainEntries.containsKey(canonicalizedCandidatePath)) {
        moduleAddressCandidate = packageJsonMainEntries.get(canonicalizedCandidatePath);

        if (ModuleLoader.JSC_BROWSER_SKIPLISTED_MARKER.equals(moduleAddressCandidate)) {
          return null;
        }
      }

      String loadAddress = locate(scriptAddress, moduleAddressCandidate);
      if (loadAddress != null) {
        return loadAddress;
      }
    }

    return null;
  }

  @Nullable
  private String resolveJsModuleNodeFileOrDirectory(String scriptAddress, String moduleAddress) {
    String loadAddress;
    loadAddress = resolveJsModuleFile(scriptAddress, moduleAddress);
    if (loadAddress == null) {
      loadAddress = resolveJsModuleNodeDirectory(scriptAddress, moduleAddress);
    }
    return loadAddress;
  }

  @Nullable
  private String resolveJsModuleNodeDirectory(String scriptAddress, String moduleAddress) {
    if (moduleAddress.endsWith(ModuleLoader.MODULE_SLASH)) {
      moduleAddress = moduleAddress.substring(0, moduleAddress.length() - 1);
    }

    // Load as a file
    for (int i = 0; i < FILES_TO_SEARCH.length; i++) {
      String loadAddress = locate(scriptAddress, moduleAddress + FILES_TO_SEARCH[i]);
      if (loadAddress != null) {
        if (FILES_TO_SEARCH[i].equals(ModuleLoader.MODULE_SLASH + "package.json")) {
          if (packageJsonMainEntries.containsKey(loadAddress)) {
            return resolveJsModuleFile(scriptAddress, packageJsonMainEntries.get(loadAddress));
          }
        } else {
          return loadAddress;
        }
      }
    }

    return null;
  }

  @Nullable
  private String resolveJsModuleFromRegistry(String scriptAddress, String moduleAddress) {
    String normalizedScriptAddress =
        (ModuleLoader.isAmbiguousIdentifier(scriptAddress) ? ModuleLoader.MODULE_SLASH : "")
            + scriptAddress;
    for (String nodeModulesFolder : nodeModulesFolders) {

      if (!normalizedScriptAddress.startsWith(nodeModulesFolder)) {
        continue;
      }

      // Load as a file
      String fullModulePath = nodeModulesFolder + "node_modules/" + moduleAddress;
      String loadAddress = resolveJsModuleFile(scriptAddress, fullModulePath);
      if (loadAddress == null) {
        // Load as a directory
        loadAddress = resolveJsModuleNodeDirectory(scriptAddress, fullModulePath);
      }

      if (loadAddress != null) {
        return loadAddress;
      }
    }

    return null;
  }
}
