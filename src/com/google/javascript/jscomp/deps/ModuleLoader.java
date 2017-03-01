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
import com.google.common.collect.ImmutableSortedSet;
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
import java.util.SortedSet;
import java.util.TreeSet;
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

  public static final DiagnosticType LOAD_WARNING =
      DiagnosticType.warning("JSC_JS_MODULE_LOAD_WARNING", "Failed to load module \"{0}\"");

  private static final String[] NODE_FILE_EXTENSIONS_TO_SEARCH = {"", ".js", ".json"};
  private static final String[] EMPTY_FILE_EXTENSIONS_TO_SEARCH = {""};
  private static final String[] NODE_FILES_TO_SEARCH = {
    MODULE_SLASH + "package.json", MODULE_SLASH + "index.js", MODULE_SLASH + "index.json"
  };

  @Nullable private final ErrorHandler errorHandler;

  /** Root URIs to match module roots against. */
  private final ImmutableList<String> moduleRootPaths;
  /** The set of all known input module URIs (including trailing .js), after normalization. */
  private final ImmutableSet<String> modulePaths;

  /** Named modules found in node_modules folders */
  private final ImmutableSortedSet<String> nodeModulesFolders;

  /** Named modules found in node_modules folders */
  private ImmutableMap<String, String> packageJsonMainEntries;

  /** Used to canonicalize paths before resolution. */
  private final PathResolver pathResolver;

  private final ResolutionMode resolutionMode;

  /**
   * Creates an instance of the module loader which can be used to locate ES6 and CommonJS modules.
   *
   * @param inputs All inputs to the compilation process.
   */
  public ModuleLoader(
      @Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots,
      Iterable<? extends DependencyInfo> inputs,
      PathResolver pathResolver,
      ResolutionMode resolutionMode) {
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

    this.nodeModulesFolders = buildNodeModulesFoldersRegistry(this.modulePaths);

    this.resolutionMode = resolutionMode;
  }

  public ModuleLoader(
      @Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots,
      Iterable<? extends DependencyInfo> inputs,
      ResolutionMode resolutionMode) {
    this(errorHandler, moduleRoots, inputs, PathResolver.RELATIVE, resolutionMode);
  }

  public ModuleLoader(
      @Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots,
      Iterable<? extends DependencyInfo> inputs,
      PathResolver pathResolver) {
    this(errorHandler, moduleRoots, inputs, pathResolver, ResolutionMode.LEGACY);
  }

  public ModuleLoader(
      @Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots,
      Iterable<? extends DependencyInfo> inputs) {
    this(errorHandler, moduleRoots, inputs, PathResolver.RELATIVE, ResolutionMode.LEGACY);
  }

  public Map<String, String> getPackageJsonMainEntries() {
    return this.packageJsonMainEntries;
  }

  /**
   * @param packageJsonMainEntries a map with keys that are package.json file paths and values which
   *     are the "main" entry from the package.json. "main" entries are absolute paths rooted from
   *     the folder containing the package.json file.
   */
  public void setPackageJsonMainEntries(Map<String, String> packageJsonMainEntries) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (Map.Entry<String, String> packageJsonMainEntry : packageJsonMainEntries.entrySet()) {
      String entryKey = packageJsonMainEntry.getKey();
      builder.put(toAbsoluteIdentifier(entryKey), packageJsonMainEntry.getValue());
    }

    this.packageJsonMainEntries = builder.build();
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
     * Find a JS module {@code requireName}. See
     * https://nodejs.org/api/modules.html#modules_all_together
     *
     * @return The normalized module URI, or {@code null} if not found.
     */
    @Nullable
    public ModulePath resolveJsModule(String moduleAddress) {
      return resolveJsModule(moduleAddress, null, -1, -1);
    }

    /**
     * Find a JS module {@code requireName}. See
     * https://nodejs.org/api/modules.html#modules_all_together
     *
     * @return The normalized module URI, or {@code null} if not found.
     */
    @Nullable
    public ModulePath resolveJsModule(
        String moduleAddress, String sourcename, int lineno, int colno) {
      String loadAddress = null;

      // * the immediate name require'd
      switch (resolutionMode) {
        case LEGACY:
          loadAddress = resolveJsModuleLegacy(moduleAddress, sourcename, lineno, colno);
          break;

        case BROWSER:
          if (isAbsoluteIdentifier(moduleAddress) || isRelativeIdentifier(moduleAddress)) {
            // Edge is the only browser supporting modules currently and requires
            // a file extension. This may be loosened as more browsers support
            // ES2015 modules natively.
            loadAddress = resolveJsModuleFile(moduleAddress, EMPTY_FILE_EXTENSIONS_TO_SEARCH);
          }
          break;

        case NODE:
          if (isAbsoluteIdentifier(moduleAddress) || isRelativeIdentifier(moduleAddress)) {
            loadAddress = resolveJsModuleNodeFileOrDirectory(moduleAddress);
          } else {
            loadAddress = resolveJsModuleFromRegistry(moduleAddress);
          }
          break;
      }

      if (loadAddress != null) {
        return new ModulePath(loadAddress);
      }

      if (errorHandler != null) {
        errorHandler.report(
            CheckLevel.WARNING,
            JSError.make(sourcename, lineno, colno, LOAD_WARNING, moduleAddress));
      }

      return null;
    }

    @Nullable
    private String resolveJsModuleFile(String moduleAddress, String[] fileExtensionsToSearch) {
      // Load node module as a file
      for (int i = 0; i < fileExtensionsToSearch.length; i++) {
        String loadAddress = locate(moduleAddress + fileExtensionsToSearch[i]);
        if (loadAddress != null) {
          return loadAddress;
        }
      }

      return null;
    }

    @Nullable
    private String resolveJsModuleNodeFileOrDirectory(String moduleAddress) {
      String loadAddress = resolveJsModuleFile(moduleAddress, NODE_FILE_EXTENSIONS_TO_SEARCH);
      if (loadAddress == null) {
        loadAddress = resolveJsModuleNodeDirectory(moduleAddress);
      }
      return loadAddress;
    }

    @Nullable
    private String resolveJsModuleNodeDirectory(String moduleAddress) {
      // Load as a file
      for (int i = 0; i < NODE_FILES_TO_SEARCH.length; i++) {
        String loadAddress = locate(moduleAddress + NODE_FILES_TO_SEARCH[i]);
        if (loadAddress != null) {
          if (i == 0) {
            if (packageJsonMainEntries.containsKey(loadAddress)) {
              return resolveJsModuleFile(
                  packageJsonMainEntries.get(loadAddress), NODE_FILE_EXTENSIONS_TO_SEARCH);
            }
          } else {
            return loadAddress;
          }
        }
      }

      return null;
    }

    @Nullable
    private String resolveJsModuleFromRegistry(String moduleAddress) {
      for (String nodeModulesFolder : nodeModulesFolders) {
        if (!toAbsoluteIdentifier(this.path).startsWith(nodeModulesFolder)) {
          continue;
        }

        // Load as a file
        String fullModulePath = nodeModulesFolder + "node_modules/" + moduleAddress;
        String loadAddress = resolveJsModuleFile(fullModulePath, NODE_FILE_EXTENSIONS_TO_SEARCH);
        if (loadAddress == null) {
          // Load as a directory
          loadAddress = resolveJsModuleNodeDirectory(fullModulePath);
        }

        if (loadAddress != null) {
          return loadAddress;
        }
      }

      return null;
    }

    /**
     * Find a module using the old LEGACY method.
     *
     * @return The normalized module path.
     */
    private String resolveJsModuleLegacy(
        String moduleName, String sourcename, int lineno, int colno) {
      // Allow module names with or without the ".js" extension.
      if (!moduleName.endsWith(".js")) {
        moduleName += ".js";
      }
      String resolved = resolveJsModuleFile(moduleName, EMPTY_FILE_EXTENSIONS_TO_SEARCH);
      if (resolved == null) {
        if (errorHandler != null) {
          errorHandler.report(
              CheckLevel.WARNING,
              JSError.make(sourcename, lineno, colno, LOAD_WARNING, moduleName));
        }
        return canonicalizePath(moduleName);
      }
      return resolved;
    }

    /**
     * Normalizes a module path reference. Includes escaping special characters and converting
     * relative paths to absolute references.
     */
    private String canonicalizePath(String name) {
      String path = ModuleNames.escapePath(name);
      if (isRelativeIdentifier(name)) {
        String ourPath = this.path;
        int lastIndex = ourPath.lastIndexOf('/');
        path = ModuleNames.canonicalizePath(ourPath.substring(0, lastIndex + 1) + path);
      }
      return path;
    }

    /**
     * Locates the module with the given name, but returns null if there is no JS file in the
     * expected location.
     */
    @Nullable
    private String locate(String name) {
      String canonicalizedPath = canonicalizePath(name);

      // First check to see if the module is known with it's provided path
      if (modulePaths.contains(toAbsoluteIdentifier(canonicalizedPath))) {
        return canonicalizedPath;
      }

      // Check for the module beneath each of the module roots
      for (String rootPath : moduleRootPaths) {
        String modulePath = rootPath + toAbsoluteIdentifier(canonicalizedPath);

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
     * Treats the module address as a path and returns the name of that module. Does not verify that
     * there is actually a JS file at the provided URI.
     *
     * <p>Primarily used for per-file ES6 module transpilation
     */
    public ModulePath resolveModuleAsPath(String moduleAddress) {
      if (!moduleAddress.endsWith(".js")) {
        moduleAddress += ".js";
      }
      String path = ModuleNames.escapePath(moduleAddress);
      if (isRelativeIdentifier(moduleAddress)) {
        String ourPath = this.path;
        int lastIndex = ourPath.lastIndexOf('/');
        path = ModuleNames.canonicalizePath(ourPath.substring(0, lastIndex + 1) + path);
      }
      return new ModulePath(normalize(path, moduleRootPaths));
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

  /** Whether name is a path-based identifier (has a '/' character) */
  public static boolean isPathIdentifier(String name) {
    return name.contains(MODULE_SLASH);
  }

  /** Removes the leading slash from absolute identifiers */
  public static String removeAbsoluteIdentifierIndicator(String name) {
    if (isAbsoluteIdentifier(name)) {
      return name.substring(1);
    }
    return name;
  }

  /** Converts paths which don't start with either a '.' or '/' to absolute paths */
  public static String toAbsoluteIdentifier(String name) {
    if (name.length() == 0 || isAbsoluteIdentifier(name) || isRelativeIdentifier(name)) {
      return name;
    }
    return MODULE_SLASH + name;
  }

  /**
   * @param roots List of module root paths. This path prefix will be removed from module paths when
   *     resolved.
   */
  private static ImmutableList<String> createRootPaths(
      Iterable<String> roots, PathResolver resolver) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (String root : roots) {
      String rootModuleName = ModuleNames.escapePath(resolver.apply(root));
      builder.add(toAbsoluteIdentifier(rootModuleName));
    }
    return builder.build();
  }

  /**
   * @param modulePaths List of modules. Modules can be relative to the compilation root or absolute
   *     file system paths (or even absolute paths from the compilation root).
   * @param roots List of module roots which anchor absolute path references.
   * @return List of normalized modules which always have a leading slash
   */
  private static ImmutableSet<String> resolvePaths(
      Iterable<String> modulePaths, Iterable<String> roots) {
    ImmutableSet.Builder<String> resolved = ImmutableSet.builder();
    Set<String> knownPaths = new HashSet<String>();
    for (String name : modulePaths) {
      String canonicalizedPath = ModuleNames.escapePath(name);
      if (!knownPaths.add(normalize(canonicalizedPath, roots))) {
        // Having root paths "a" and "b" and source files "a/f.js" and "b/f.js" is ambiguous.
        throw new IllegalArgumentException(
            "Duplicate module path after resolving: " + name);
      }
      resolved.add(toAbsoluteIdentifier(canonicalizedPath));
    }
    return resolved.build();
  }

  /** Normalizes the name and resolves it against the module roots. */
  private static String normalize(String path, Iterable<String> moduleRootPaths) {
    // Find a moduleRoot that this URI is under. If none, use as is.
    for (String moduleRoot : moduleRootPaths) {
      if (toAbsoluteIdentifier(path).startsWith(moduleRoot)) {
        // Make sure that e.g. path "foobar/test.js" is not matched by module "foo", by checking for
        // a leading slash.
        String trailing = toAbsoluteIdentifier(path).substring(moduleRoot.length());
        if (trailing.startsWith(MODULE_SLASH)) {
          return trailing.substring(MODULE_SLASH.length());
        }
      }
    }
    // Not underneath any of the roots.
    return path;
  }

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

        if (!registry.contains(parentPath)) {
          registry.add(parentPath);
        }
        parentPath += "node_modules/";
      }
    }
    
    return ImmutableSortedSet.copyOfSorted(registry);
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
      new ModuleLoader(
          null,
          ImmutableList.<String>of(),
          ImmutableList.<DependencyInfo>of(),
          ResolutionMode.LEGACY);

  /** An enum used to specify what algorithm to use to locate non path-based modules */
  public enum ResolutionMode {
    /**
     * Mimics the behavior of MS Edge.
     *
     * Modules must begin with a "." or "/" character.
     * Modules must include the file extension
     * MS Edge was the only browser to define a module resolution behavior at the time of this
     * writing.
     */
    BROWSER,

    /**
     * Keeps the same behavior the compiler has used historically.
     *
     * Modules which do not begin with a "." or "/" character are assumed to be relative to the
     * compilation root.
     * ".js" file extensions are added if the import omits them.
     */
    LEGACY,

    /**
     * Uses the node module resolution algorithm.
     *
     * Modules which do not begin with a "." or "/" character are looked up from the appropriate
     * node_modules folder.
     * Includes the ability to require directories and JSON files.
     * Exact match, then ".js", then ".json" file extensions are searched.
     */
    NODE
  }
}
