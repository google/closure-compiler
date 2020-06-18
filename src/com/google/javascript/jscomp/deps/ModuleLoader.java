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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * Provides compile-time locate semantics for ES6 and CommonJS modules.
 *
 * @see "https://tc39.github.io/ecma262/#sec-module-semantics"
 * @see "http://wiki.commonjs.org/wiki/Modules/1.1"
 */
public final class ModuleLoader {

  public static final DiagnosticType MODULE_CONFLICT =
      DiagnosticType.warning(
          "JSC_MODULE_CONFLICT",
          "File cannot be a combination of goog.provide, goog.module, and/or ES6 module: {0}");

  /** According to the spec, the forward slash should be the delimiter on all platforms. */
  public static final String MODULE_SLASH = ModuleNames.MODULE_SLASH;

  /** The default module root, the current directory. */
  public static final String DEFAULT_FILENAME_PREFIX = "." + MODULE_SLASH;

  public static final String JSC_BROWSER_SKIPLISTED_MARKER = "$jscomp$browser$skiplisted";

  public static final DiagnosticType LOAD_WARNING =
      DiagnosticType.error("JSC_JS_MODULE_LOAD_WARNING", "Failed to load module \"{0}\"");

  public static final DiagnosticType INVALID_MODULE_PATH =
      DiagnosticType.error(
          "JSC_INVALID_MODULE_PATH", "Invalid module path \"{0}\" for resolution mode \"{1}\"");

  private ErrorHandler errorHandler;

  /** Root URIs to match module roots against. */
  private final ImmutableList<String> moduleRootPaths;
  /** The set of all known input module URIs (including trailing .js), after normalization. */
  private final ImmutableSet<String> modulePaths;

  /** Used to canonicalize paths before resolution. */
  private final PathResolver pathResolver;

  private final PathEscaper pathEscaper;

  private final ModuleResolver moduleResolver;

  /**
   * Creates an instance of the module loader which can be used to locate ES6 and CommonJS modules.
   *
   * @param moduleRoots path prefixes to strip from module paths
   * @param inputs all inputs to the compilation process. Used to ensure that resolved paths
   *     references an valid input.
   * @param factory creates a module resolver, which determines how module identifiers are resolved
   * @param pathResolver determines how to sanitize paths before resolving
   * @param pathEscaper determines if / how paths should be escaped
   */
  public ModuleLoader(
      @Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots,
      Iterable<? extends DependencyInfo> inputs,
      ModuleResolverFactory factory,
      PathResolver pathResolver,
      PathEscaper pathEscaper) {
    checkNotNull(moduleRoots);
    checkNotNull(inputs);
    checkNotNull(pathResolver);
    checkNotNull(pathEscaper);
    this.pathResolver = pathResolver;
    this.pathEscaper = pathEscaper;
    this.errorHandler = errorHandler == null ? new NoopErrorHandler() : errorHandler;
    this.moduleRootPaths = createRootPaths(moduleRoots, pathResolver, pathEscaper);
    this.modulePaths =
        resolvePaths(
            Iterables.transform(Iterables.transform(inputs, DependencyInfo::getName), pathResolver),
            moduleRootPaths,
            pathEscaper);
    this.moduleResolver =
        factory.create(this.modulePaths, this.moduleRootPaths, this.errorHandler, this.pathEscaper);
  }

  public ModuleLoader(
      @Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots,
      Iterable<? extends DependencyInfo> inputs,
      ModuleResolverFactory factory,
      PathResolver pathResolver) {
    this(errorHandler, moduleRoots, inputs, factory, pathResolver, PathEscaper.ESCAPE);
  }

  public ModuleLoader(
      @Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots,
      Iterable<? extends DependencyInfo> inputs,
      ModuleResolverFactory factory) {
    this(errorHandler, moduleRoots, inputs, factory, PathResolver.RELATIVE, PathEscaper.ESCAPE);
  }

  @VisibleForTesting
  public Map<String, String> getPackageJsonMainEntries() {
    return this.moduleResolver.getPackageJsonMainEntries();
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
     * Determines if this path is the same as another path, ignoring any potential leading slashes
     * on both.
     */
    public boolean equalsIgnoreLeadingSlash(ModulePath other) {
      return other != null && toModuleName().equals(other.toModuleName());
    }

    /**
     * Turns a filename into a JS identifier that is used for moduleNames in
     * rewritten code. Removes leading /, replaces / with $, removes trailing .js
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
      String loadAddress =
          moduleResolver.resolveJsModule(this.path, moduleAddress, sourcename, lineno, colno);

      if (loadAddress != null) {
        return new ModulePath(loadAddress);
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
      return new ModulePath(moduleResolver.resolveModuleAsPath(this.path, moduleAddress));
    }
  }

  /** Resolves a path into a {@link ModulePath}. */
  public ModulePath resolve(String path) {
    return new ModulePath(normalize(pathEscaper.escape(pathResolver.apply(path)), moduleRootPaths));
  }

  /** Whether this is relative to the current file, or a top-level identifier. */
  public static boolean isRelativeIdentifier(String name) {
    return name.startsWith("." + MODULE_SLASH) || name.startsWith(".." + MODULE_SLASH);
  }

  /** Whether this is absolute to the compilation. */
  public static boolean isAbsoluteIdentifier(String name) {
    return name.startsWith(MODULE_SLASH);
  }

  /** Whether this is neither absolute or relative. */
  public static boolean isAmbiguousIdentifier(String name) {
    return !isAbsoluteIdentifier(name) && !isRelativeIdentifier(name);
  }

  /** Whether name is a path-based identifier (has a '/' character) */
  public static boolean isPathIdentifier(String name) {
    return name.contains(MODULE_SLASH);
  }

  /**
   * Normalizes the given root paths, which are path prefixes to be removed from a module path when
   * resolved.
   */
  private static ImmutableList<String> createRootPaths(
      Iterable<String> roots, PathResolver resolver, PathEscaper escaper) {
    // Sort longest length to shortest so that paths are applied most specific to least.
    Set<String> builder =
        new TreeSet<>(
            Comparator.comparingInt(String::length)
                .thenComparing(Comparator.naturalOrder())
                .reversed());
    for (String root : roots) {
      String rootModuleName = escaper.escape(resolver.apply(root));
      if (isAmbiguousIdentifier(rootModuleName)) {
        rootModuleName = MODULE_SLASH + rootModuleName;
      }
      builder.add(rootModuleName);
    }
    return ImmutableList.copyOf(builder);
  }

  /**
   * @param modulePaths List of modules. Modules can be relative to the compilation root or absolute
   *     file system paths (or even absolute paths from the compilation root).
   * @param roots List of module roots which anchor absolute path references.
   * @return List of normalized modules which always have a leading slash
   */
  private static ImmutableSet<String> resolvePaths(
      Iterable<String> modulePaths, Iterable<String> roots, PathEscaper escaper) {
    ImmutableSet.Builder<String> resolved = ImmutableSet.builder();
    Set<String> knownPaths = new HashSet<>();
    for (String name : modulePaths) {
      String canonicalizedPath = escaper.escape(name);
      if (!knownPaths.add(normalize(canonicalizedPath, roots))) {
        // Having root paths "a" and "b" and source files "a/f.js" and "b/f.js" is ambiguous.
        throw new IllegalArgumentException(
            "Duplicate module path after resolving: " + name);
      }
      if (isAmbiguousIdentifier(canonicalizedPath)) {
        canonicalizedPath = MODULE_SLASH + canonicalizedPath;
      }
      resolved.add(canonicalizedPath);
    }
    return resolved.build();
  }

  /** Normalizes the name and resolves it against the module roots. */
  static String normalize(String path, Iterable<String> moduleRootPaths) {
    String normalizedPath = path;
    if (isAmbiguousIdentifier(normalizedPath)) {
      normalizedPath = MODULE_SLASH + normalizedPath;
    }

    // Find a moduleRoot that this URI is under. If none, use as is.
    for (String moduleRoot : moduleRootPaths) {
      if (normalizedPath.startsWith(moduleRoot)) {
        // Make sure that e.g. path "foobar/test.js" is not matched by module "foo", by checking for
        // a leading slash.
        String trailing = normalizedPath.substring(moduleRoot.length());
        if (trailing.startsWith(MODULE_SLASH)) {
          return trailing.substring(MODULE_SLASH.length());
        }
      }
    }
    // Not underneath any of the roots.
    return path;
  }

  public void setErrorHandler(ErrorHandler errorHandler) {
    if (errorHandler == null) {
      this.errorHandler = new NoopErrorHandler();
    } else {
      this.errorHandler = errorHandler;
    }
    this.moduleResolver.setErrorHandler(this.errorHandler);
  }

  public ErrorHandler getErrorHandler() {
    return this.errorHandler;
  }

  /** Indicates whether to escape characters in paths. */
  public enum PathEscaper {
    /**
     * Escapes characters in paths according to {@link ModuleNames#escapePath(String)} and then
     * canonicalizes it.
     */
    ESCAPE {
      @Override
      public String escape(String path) {
        return ModuleNames.escapePath(path);
      }
    },

    /**
     * Does not escaped characters in paths, but does canonicalize it according to {@link
     * ModuleNames#canonicalizePath(String)}.
     */
    CANONICALIZE_ONLY {
      @Override
      public String escape(String path) {
        return ModuleNames.canonicalizePath(path);
      }
    };

    public abstract String escape(String path);
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

  /** An enum used to specify what algorithm to use to locate non path-based modules */
  @FunctionalInterface
  public interface ModuleResolverFactory {
    ModuleResolver create(
        ImmutableSet<String> modulePaths,
        ImmutableList<String> moduleRootPaths,
        ErrorHandler errorHandler,
        PathEscaper pathEscaper);
  }

  /** A trivial module loader with no roots. */
  public static final ModuleLoader EMPTY =
      new ModuleLoader(
          /** errorReporter= */ null,
          ImmutableList.of(),
          ImmutableList.of(),
          BrowserModuleResolver.FACTORY);

  /** Standard path base resolution algorithms that are accepted as a command line flag. */
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
     * A limited superset of BROWSER that transforms some path prefixes.
     *
     * <p>For example one could configure this so that "@root/" is replaced with
     * "/my/path/to/project/" within import paths.</p>
     */
    BROWSER_WITH_TRANSFORMED_PREFIXES,

    /**
     * Uses the node module resolution algorithm.
     *
     * <p>Modules which do not begin with a "." or "/" character are looked up from the appropriate
     * node_modules folder. Includes the ability to require directories and JSON files. Exact match,
     * then ".js", then ".json" file extensions are searched.
     */
    NODE,

    /**
     * Uses a lookup map provided by webpack to locate modules from a numeric id used during import
     */
    WEBPACK,
  }

  private static final class NoopErrorHandler implements ErrorHandler {
    @Override
    public void report(CheckLevel level, JSError error) {}
  }
}
