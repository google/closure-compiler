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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.JSError;
import java.nio.file.Paths;
import java.util.HashSet;
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
  }

  public ModuleLoader(@Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots, Iterable<? extends DependencyInfo> inputs) {
    this(errorHandler, moduleRoots, inputs, PathResolver.RELATIVE);
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
     * Find a CommonJS module {@code requireName} relative to {@code context}.
     * @return The normalized module URI, or {@code null} if not found.
     */
    public ModulePath resolveCommonJsModule(String requireName) {
      // * the immediate name require'd
      String loadAddress = locate(requireName);
      if (loadAddress == null) {
        // * the require'd name + /index.js
        loadAddress = locate(requireName + MODULE_SLASH + "index.js");
      }
      if (loadAddress == null) {
        // * the require'd name with a potential trailing ".js"
        loadAddress = locate(requireName + ".js");
      }
      return loadAddress != null ? new ModulePath(loadAddress) : null; // could be null.
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
