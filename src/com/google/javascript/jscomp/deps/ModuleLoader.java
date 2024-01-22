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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static com.google.common.collect.Streams.stream;
import static java.util.Comparator.naturalOrder;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.JSError;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import org.jspecify.nullness.Nullable;

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
  /** Used to canonicalize paths before resolution. */
  private final PathResolver pathResolver;

  private final PathEscaper pathEscaper;

  private final ModuleResolver moduleResolver;

  public static Builder builder() {
    return new Builder();
  }

  /** Builder */
  public static final class Builder {
    private ErrorHandler errorHandler = NOOP_ERROR_HANDER;
    private Iterable<String> moduleRoots;
    private Iterable<? extends DependencyInfo> inputs;
    private ModuleResolverFactory factory;
    private PathResolver pathResolver = PathResolver.RELATIVE;
    private PathEscaper pathEscaper = PathEscaper.ESCAPE;

    private Builder() {}

    @CanIgnoreReturnValue
    public Builder setErrorHandler(ErrorHandler x) {
      if (x != null) {
        this.errorHandler = x;
      }
      return this;
    }

    /** Path prefixes to strip from module paths */
    @CanIgnoreReturnValue
    public Builder setModuleRoots(Iterable<String> x) {
      this.moduleRoots = x;
      return this;
    }

    /**
     * All inputs to the compilation process.
     *
     * <p>Used to ensure that resolved paths references a valid input.
     */
    @CanIgnoreReturnValue
    public Builder setInputs(Iterable<? extends DependencyInfo> x) {
      this.inputs = x;
      return this;
    }

    /** Creates a module resolver, which determines how module identifiers are resolved */
    @CanIgnoreReturnValue
    public Builder setFactory(ModuleResolverFactory x) {
      this.factory = x;
      return this;
    }

    /** Determines how to sanitize paths before resolving */
    @CanIgnoreReturnValue
    public Builder setPathResolver(PathResolver x) {
      this.pathResolver = x;
      return this;
    }

    /** Determines if / how paths should be escaped */
    @CanIgnoreReturnValue
    public Builder setPathEscaper(PathEscaper x) {
      this.pathEscaper = x;
      return this;
    }

    public ModuleLoader build() {
      return new ModuleLoader(this);
    }
  }

  private ModuleLoader(Builder builder) {
    this.errorHandler = checkNotNull(builder.errorHandler);
    this.pathResolver = checkNotNull(builder.pathResolver);
    this.pathEscaper = checkNotNull(builder.pathEscaper);
    this.moduleRootPaths = this.createRootPaths(checkNotNull(builder.moduleRoots));

    ImmutableSet<String> modulePaths = this.resolvePaths(checkNotNull(builder.inputs));
    this.moduleResolver =
        builder.factory.create(
            modulePaths, this.moduleRootPaths, this.errorHandler, this.pathEscaper);
  }

  @VisibleForTesting
  public Map<String, String> getPackageJsonMainEntries() {
    return this.moduleResolver.getPackageJsonMainEntries();
  }

  /**
   * A path to a module. Provides access to the module's closurized name and a way to resolve
   * relative paths.
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
     * Turns a filename into a JS identifier that is used for moduleNames in rewritten code. Removes
     * leading /, replaces / with $, removes trailing .js and replaces - with _. All moduleNames get
     * a "module$" prefix.
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
    public @Nullable ModulePath resolveJsModule(
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
  private ImmutableList<String> createRootPaths(Iterable<String> roots) {
    return stream(roots)
        .map(this.pathResolver)
        .map(this.pathEscaper::escape)
        .map((n) -> isAmbiguousIdentifier(n) ? (MODULE_SLASH + n) : n)
        .collect(toImmutableSortedSet(BEST_MATCH_PATH_ORDERING))
        .asList();
  }

  /** @return List of normalized modules which always have a leading slash */
  private ImmutableSet<String> resolvePaths(Iterable<? extends DependencyInfo> inputs) {
    ImmutableMultiset<String> dupeModulePaths =
        stream(inputs)
            .map(DependencyInfo::getName)
            .map(this.pathResolver)
            .map(this.pathEscaper::escape)
            .map((p) -> normalize(p, this.moduleRootPaths))
            .map((n) -> isAmbiguousIdentifier(n) ? (MODULE_SLASH + n) : n)
            .sorted(BEST_MATCH_PATH_ORDERING) // Sort so that this errors are easier to read.
            .collect(toImmutableMultiset());

    checkState(
        dupeModulePaths.size() == dupeModulePaths.elementSet().size(),
        "Duplicate module paths after resolving: %s",
        dupeModulePaths);

    return dupeModulePaths.elementSet();
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

  public static String relativePathFrom(String fromUriPath, String toUriPath) {
    Path fromPath = Path.of(fromUriPath);
    Path toPath = Path.of(toUriPath);
    Path fromFolder = fromPath.getParent();

    // if the from URIs are simple names without paths, they are in the same folder
    // example: m0.js
    if (fromFolder == null && toPath.getParent() == null) {
      return "./" + toUriPath;
    } else if (fromFolder == null
        && (toUriPath.startsWith(".")
            || toPath.toString().startsWith("/")
            || toPath.toString().startsWith("\\"))) {
      return toUriPath;
    } else if (fromFolder == null) {
      throw new IllegalArgumentException("Relative path between URIs cannot be calculated");
    }

    String calculatedPath = fromFolder.relativize(toPath).toString();
    if (calculatedPath.startsWith(".") || calculatedPath.startsWith("/")) {
      return calculatedPath;
    }
    return "./" + calculatedPath;
  }

  public void setErrorHandler(ErrorHandler errorHandler) {
    if (errorHandler == null) {
      this.errorHandler = NOOP_ERROR_HANDER;
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
        return Path.of(path).toAbsolutePath().toString();
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

  // Sort longest length to shortest so that paths are applied most specific to least.
  private static final Comparator<String> BEST_MATCH_PATH_ORDERING =
      Comparator.comparingInt(String::length).reversed().thenComparing(naturalOrder());

  private static final ErrorHandler NOOP_ERROR_HANDER = (CheckLevel level, JSError error) -> {};

  /** A trivial module loader with no roots. */
  public static final ModuleLoader EMPTY =
      ModuleLoader.builder()
          .setModuleRoots(ImmutableList.of())
          .setInputs(ImmutableList.of())
          .setFactory(BrowserModuleResolver.FACTORY)
          .build();

  /** Standard path base resolution algorithms that are accepted as a command line flag. */
  public enum ResolutionMode {
    /**
     * Mimics the behavior of MS Edge.
     *
     * <p>Modules must begin with a "." or "/" character. Modules must include the file extension MS
     * Edge was the only browser to define a module resolution behavior at the time of this writing.
     */
    BROWSER,

    /**
     * A limited superset of BROWSER that transforms some path prefixes.
     *
     * <p>For example one could configure this so that "@root/" is replaced with
     * "/my/path/to/project/" within import paths.
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
}
