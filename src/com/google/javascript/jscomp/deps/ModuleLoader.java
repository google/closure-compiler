/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.JSError;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * Provides compile-time locate semantics for ES6 and CommonJS modules.
 *
 * @see "https://tc39.github.io/ecma262/#sec-module-semantics"
 * @see "http://wiki.commonjs.org/wiki/Modules/1.1"
 */
public final class ModuleLoader {

  public static final DiagnosticType MODULE_CONFLICT = DiagnosticType.warning(
      "JSC_MODULE_CONFLICT", "File has both goog.module and ES6 modules: {0}");

  /** According to the spec, the forward slash should be the delimiter on all platforms. */
  public static final String MODULE_SLASH = ModuleNames.MODULE_SLASH;

  /** The default module root, the current directory. */
  public static final String DEFAULT_FILENAME_PREFIX = "." + MODULE_SLASH;

  public static final DiagnosticType LOAD_WARNING = DiagnosticType.warning(
      "JSC_ES6_MODULE_LOAD_WARNING",
      "Failed to load module \"{0}\"");

  @Nullable private final ErrorHandler errorHandler;

  /** Root URIs to match module roots against. */
  private final ImmutableList<String> moduleRootPaths;
  /** The set of all known input module URIs (including trailing .js), after normalization. */
  private final ImmutableSet<String> modulePaths;

  /** Named modules found in node_modules folders */
  private final ImmutableSortedMap<String, ImmutableSet<String>> nodeModulesRegistry;

  /** Named modules found in node_modules folders */
  private ImmutableMap<String, String> packageJsonMainEntries;

  /** Used to canonicalize paths before resolution. */
  private final PathResolver pathResolver;

  /**
   * Creates an instance of the module loader which can be used to locate ES6 and CommonJS modules.
   *
   * @param inputs All inputs to the compilation process.
   */
  public ModuleLoader(
      @Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots,
      Iterable<? extends DependencyInfo> inputs,
      PathResolver pathResolver) {
    checkNotNull(moduleRoots);
    checkNotNull(inputs);
    checkNotNull(pathResolver);
    this.pathResolver = pathResolver;
    this.errorHandler = errorHandler;
    this.moduleRootPaths = createRootPaths(moduleRoots, pathResolver);
    this.modulePaths =
        resolvePaths(
            Iterables.transform(Iterables.transform(inputs, UNWRAP_DEPENDENCY_INFO), pathResolver),
            moduleRootPaths);

    this.packageJsonMainEntries = ImmutableMap.of();

    this.nodeModulesRegistry = buildRegistry(this.modulePaths);
  }

  public ModuleLoader(@Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots, Iterable<? extends DependencyInfo> inputs) {
    this(errorHandler, moduleRoots, inputs, PathResolver.RELATIVE);
  }

  public Map<String, String> getPackageJsonMainEntries() {
    return this.packageJsonMainEntries;
  }

  public void setPackageJsonMainEntries(Map<String, String> packageJsonMainEntries) {
    this.packageJsonMainEntries = ImmutableMap.copyOf(packageJsonMainEntries);
  }

  /**
   * A path to a module.  Provides access to the module's closurized name
   * and a way to resolve relative paths.
   */
  public class ModulePath {
    private final String path;

    private ModulePath(String path) {
      this.path = path;
    }

    @Override
    public String toString() {
      return path;
    }

    /**
     * Turns a filename into a JS identifier that can be used in rewritten code.
     * Removes leading ./, replaces / with $, removes trailing .js
     * and replaces - with _.
     */
    public String toJSIdentifier() {
      return ModuleNames.toJSIdentifier(path);
    }

    /**
     * Turns a filename into a JS identifier that is used for moduleNames in
     * rewritten code. Removes leading ./, replaces / with $, removes trailing .js
     * and replaces - with _. All moduleNames get a "module$" prefix.
     */
    public String toModuleName() {
      return ModuleNames.toModuleName(path);
    }

    /**
     * Find a CommonJS module {@code requireName}. See
     * https://nodejs.org/api/modules.html#modules_all_together
     *
     * @return The normalized module URI, or {@code null} if not found.
     */
    public ModulePath resolveCommonJsModule(String requireName) {
      String loadAddress;

      // * the immediate name require'd
      if (isAbsoluteIdentifier(requireName) || isRelativeIdentifier(requireName)) {
        loadAddress = resolveCommonJsModuleFileOrDirectory(requireName);
      } else {
        loadAddress = resolveCommonJsModuleFromRegistry(requireName);
      }
      if (loadAddress != null) {
        return new ModulePath(loadAddress);
      }
      return null;
    }

    private String resolveCommonJsModuleFile(String requireName) {
      String[] extensions = {"", ".js", ".json"};

      // Load as a file
      for (int i = 0; i < extensions.length; i++) {
        String loadAddress = locate(requireName + extensions[i]);
        if (loadAddress != null) {
          return loadAddress;
        }
      }

      return null;
    }

    private String resolveCommonJsModuleFileOrDirectory(String requireName) {
      String loadAddress = resolveCommonJsModuleFile(requireName);
      if (loadAddress == null) {
        loadAddress = resolveCommonJsModuleDirectory(requireName);
      }
      return loadAddress;
    }

    private String resolveCommonJsModuleDirectory(String requireName) {
      String[] extensions = {
        MODULE_SLASH + "package.json", MODULE_SLASH + "index.js", MODULE_SLASH + "index.json"
      };

      // Load as a file
      for (int i = 0; i < extensions.length; i++) {
        String loadAddress = locate(requireName + extensions[i]);
        if (loadAddress != null) {
          if (i == 0) {
            if (packageJsonMainEntries.containsKey(loadAddress)) {
              return resolveCommonJsModuleFile(packageJsonMainEntries.get(loadAddress));
            }
          } else {
            return loadAddress;
          }
        }
      }

      return null;
    }

    private String resolveCommonJsModuleFromRegistry(String requireName) {
      for (Map.Entry<String, ImmutableSet<String>> nodeModulesFolder :
          nodeModulesRegistry.entrySet()) {
        if (!this.path.startsWith(nodeModulesFolder.getKey())) {
          continue;
        }

        // Load as a file
        String fullModulePath = nodeModulesFolder.getKey() + "node_modules/" + requireName;
        String loadAddress = resolveCommonJsModuleFile(fullModulePath);
        if (loadAddress != null) {
          return loadAddress;
        }

        // Load as a directory
        loadAddress = resolveCommonJsModuleDirectory(fullModulePath);
        if (loadAddress != null) {
          return loadAddress;
        }
      }

      return null;
    }

    /**
     * Find an ES6 module {@code moduleName} relative to {@code context}.
     * @return The normalized module URI, or {@code null} if not found.
     */
    public ModulePath resolveEs6Module(String moduleName) {
      // Allow module names with or without the ".js" extension.
      if (!moduleName.endsWith(".js")) {
        moduleName += ".js";
      }
      String resolved = locateNoCheck(moduleName);
      if (!modulePaths.contains(resolved) && errorHandler != null) {
        errorHandler.report(CheckLevel.WARNING, JSError.make(LOAD_WARNING, moduleName));
      }
      return new ModulePath(resolved);
    }

    /**
     * Locates the module with the given name, but returns successfully even if
     * there is no JS file corresponding to the returned URI.
     */
    private String locateNoCheck(String name) {
      String path = ModuleNames.escapePath(name);
      if (isRelativeIdentifier(name)) {
        String ourPath = this.path;
        int lastIndex = ourPath.lastIndexOf('/');
        path = ModuleNames.canonicalizePath(ourPath.substring(0, lastIndex + 1) + path);
      }
      return normalize(path, moduleRootPaths);
    }

    /**
     * Locates the module with the given name, but returns null if there is no JS
     * file in the expected location.
     */
    @Nullable
    private String locate(String name) {
      String path = locateNoCheck(name);
      if (modulePaths.contains(path)) {
        return path;
      }
      return null;
    }
  }

  /** Resolves a path into a {@link ModulePath}. */
  public ModulePath resolve(String path) {
    return new ModulePath(
        normalize(ModuleNames.escapePath(pathResolver.apply(path)), moduleRootPaths));
  }

  /** Whether this is relative to the current file, or a top-level identifier. */
  public static boolean isRelativeIdentifier(String name) {
    return name.startsWith("." + MODULE_SLASH) || name.startsWith(".." + MODULE_SLASH);
  }

  /** Whether this is absolute to the compilation. */
  public static boolean isAbsoluteIdentifier(String name) {
    return name.startsWith(MODULE_SLASH);
  }

  private static ImmutableList<String> createRootPaths(
      Iterable<String> roots, PathResolver resolver) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (String root : roots) {
      builder.add(ModuleNames.escapePath(resolver.apply(root)));
    }
    return builder.build();
  }

  private static ImmutableSet<String> resolvePaths(
      Iterable<String> names, ImmutableList<String> roots) {
    HashSet<String> resolved = new HashSet<>();
    for (String name : names) {
      if (!resolved.add(normalize(ModuleNames.escapePath(name), roots))) {
        // Having root paths "a" and "b" and source files "a/f.js" and "b/f.js" is ambiguous.
        throw new IllegalArgumentException(
            "Duplicate module path after resolving: " + name);
      }
    }
    return ImmutableSet.copyOf(resolved);
  }

  /**
   * Normalizes the name and resolves it against the module roots.
   */
  private static String normalize(String path, Iterable<String> moduleRootPaths) {
    // Find a moduleRoot that this URI is under. If none, use as is.
    for (String moduleRoot : moduleRootPaths) {
      if (path.startsWith(moduleRoot)) {
        // Make sure that e.g. path "foobar/test.js" is not matched by module "foo", by checking for
        // a leading slash.
        String trailing = path.substring(moduleRoot.length());
        if (trailing.startsWith(MODULE_SLASH)) {
          return trailing.substring(MODULE_SLASH.length());
        }
      }
    }
    // Not underneath any of the roots.
    return path;
  }

  /** Build the module registry from the set of module paths */
  private static ImmutableSortedMap<String, ImmutableSet<String>> buildRegistry(
      ImmutableSet<String> modulePaths) {
    SortedMap<String, Set<String>> registry =
        new TreeMap<>(
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
        String subPath = modulePath.substring(parentPath.length() + "node_modules/".length());

        if (!registry.containsKey(parentPath)) {
          registry.put(parentPath, new HashSet<String>());
        }
        registry.get(parentPath).add(subPath);

        parentPath += "node_modules/";
      }
    }

    SortedMap<String, ImmutableSet<String>> immutableRegistry =
        new TreeMap<>(
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
    for (Map.Entry<String, Set<String>> entry : registry.entrySet()) {
      immutableRegistry.put(entry.getKey(), ImmutableSet.copyOf(entry.getValue()));
    }
    return ImmutableSortedMap.copyOfSorted(immutableRegistry);
  }

  /** An enum indicating whether to absolutize paths. */
  public enum PathResolver implements Function<String, String> {
    RELATIVE {
      @Override
      public String apply(String path) {
        return path;
      }
    },

    @GwtIncompatible("Paths.get, Path.toAbsolutePath")
    ABSOLUTE {
      @Override
      public String apply(String path) {
        return Paths.get(path).toAbsolutePath().toString();
      }
    };
  }

  private static final Function<DependencyInfo, String> UNWRAP_DEPENDENCY_INFO =
      new Function<DependencyInfo, String>() {
        @Override
        public String apply(DependencyInfo info) {
          return info.getName();
        }
      };

  /** A trivial module loader with no roots. */
  public static final ModuleLoader EMPTY =
      new ModuleLoader(null, ImmutableList.<String>of(), ImmutableList.<DependencyInfo>of());
}
