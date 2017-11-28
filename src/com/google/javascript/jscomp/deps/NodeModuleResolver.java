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
import java.util.Comparator;
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
  private static final String[] FILE_EXTENSIONS_TO_SEARCH = {"", ".js", ".json"};
  private static final String[] FILES_TO_SEARCH = {
    ModuleLoader.MODULE_SLASH + "package.json",
    ModuleLoader.MODULE_SLASH + "index.js",
    ModuleLoader.MODULE_SLASH + "index.json"
  };

  /** Named modules found in node_modules folders */
  private final ImmutableMap<String, String> packageJsonMainEntries;

  /** Aliased files found in package.json alias entries */
  private final ImmutableMap<String, String> packageJsonAliasedEntries;

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
   * @return A sorted set with the longest paths first where each entry is the folder containing a
   *     node_modules sub-folder.
   */
  private static ImmutableSortedSet<String> buildNodeModulesFoldersRegistry(
      Iterable<String> modulePaths) {
    SortedSet<String> registry =
        new TreeSet<>(
            new Comparator<String>() {
              @Override
              public int compare(String a, String b) {
                // Order longest path first
                int comparison = Integer.compare(b.length(), a.length());
                if (comparison != 0) {
                  return comparison;
                }

                return a.compareTo(b);
              }
            });

    // For each modulePath, find all the node_modules folders
    // There might be more than one:
    //    /foo/node_modules/bar/node_modules/baz/foo_bar_baz.js
    // Should add:
    //   /foo/ -> bar/node_modules/baz/foo_bar_baz.js
    //   /foo/node_modules/bar/ -> baz/foo_bar_baz.js
    for (String modulePath : modulePaths) {
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

  public NodeModuleResolver(
      ImmutableSet<String> modulePaths,
      ImmutableList<String> moduleRootPaths,
      Map<String, String> packageJsonMainEntries,
      Map<String, String> packageJsonAliasedEntries,
      ErrorHandler errorHandler) {
    super(modulePaths, moduleRootPaths, errorHandler);
    this.nodeModulesFolders = buildNodeModulesFoldersRegistry(modulePaths);

    if (packageJsonMainEntries == null) {
      this.packageJsonMainEntries = ImmutableMap.of();
    } else {
      this.packageJsonMainEntries = buildPackageJsonEntries(packageJsonMainEntries);
    }

    if (packageJsonAliasedEntries == null) {
      this.packageJsonAliasedEntries = ImmutableMap.of();
    } else {
      this.packageJsonAliasedEntries = buildPackageJsonEntries(packageJsonAliasedEntries);
    }
  }

  /**
   * @param packageJsonEntries a map with keys that are either package.json file paths or paths
   *     to alias, and values which are either the main entry from the package.json or the
   *     replacement path, respectively. Main entries are absolute paths rooted from
   *     the folder containing the package.json file.
   */
  private ImmutableMap<String, String> buildPackageJsonEntries(
      Map<String, String> packageJsonEntries) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (Map.Entry<String, String> jsonEntry : packageJsonEntries.entrySet()) {
      String entryKey = jsonEntry.getKey();
      if (ModuleLoader.isAmbiguousIdentifier(entryKey)) {
        entryKey = ModuleLoader.MODULE_SLASH + entryKey;
      }

      builder.put(entryKey, jsonEntry.getValue());
    }

    return builder.build();
  }

  @Override
  Map<String, String> getPackageJsonMainEntries() {
    return this.packageJsonMainEntries;
  }

  @Override
  Map<String, String> getPackageJsonAliasedEntries() {
    return this.packageJsonAliasedEntries;
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

      if (packageJsonAliasedEntries.containsKey(canonicalizedCandidatePath)) {
        moduleAddressCandidate = packageJsonAliasedEntries.get(canonicalizedCandidatePath);

        if (ModuleLoader.JSC_ALIAS_BLACKLISTED_MARKER.equals(moduleAddressCandidate)) {
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
    for (String nodeModulesFolder : nodeModulesFolders) {
      String normalizedScriptAddress =
          (ModuleLoader.isAmbiguousIdentifier(scriptAddress) ? ModuleLoader.MODULE_SLASH : "")
              + scriptAddress;

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
