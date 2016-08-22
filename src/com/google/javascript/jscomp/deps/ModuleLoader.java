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
import java.net.URI;
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
  private final ImmutableList<URI> moduleRootUris;
  /** The set of all known input module URIs (including trailing .js), after normalization. */
  private final ImmutableSet<URI> moduleUris;

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
    this.moduleRootUris = createRootUris(moduleRoots, pathResolver);
    this.moduleUris =
        resolveUris(
            Iterables.transform(Iterables.transform(inputs, UNWRAP_DEPENDENCY_INFO), pathResolver),
            moduleRootUris);
  }

  public ModuleLoader(@Nullable ErrorHandler errorHandler,
      Iterable<String> moduleRoots, Iterable<? extends DependencyInfo> inputs) {
    this(errorHandler, moduleRoots, inputs, PathResolver.RELATIVE);
  }


  /**
   * A URI for a module.  Provides access to the module's closurized name
   * and a way to resolve relative paths.
   */
  public class ModuleUri {
    private final URI uri;

    private ModuleUri(URI uri) {
      this.uri = uri;
    }

    @Override
    public String toString() {
      return uri.toString();
    }

    /**
     * Turns a filename into a JS identifier that can be used in rewritten code.
     * Removes leading ./, replaces / with $, removes trailing .js
     * and replaces - with _.
     */
    public String toJSIdentifier() {
      return ModuleNames.toJSIdentifier(uri);
    }

    /**
     * Turns a filename into a JS identifier that is used for moduleNames in
     * rewritten code. Removes leading ./, replaces / with $, removes trailing .js
     * and replaces - with _. All moduleNames get a "module$" prefix.
     */
    public String toModuleName() {
      return ModuleNames.toModuleName(uri);
    }

    /**
     * Find a CommonJS module {@code requireName} relative to {@code context}.
     * @return The normalized module URI, or {@code null} if not found.
     */
    public ModuleUri resolveCommonJsModule(String requireName) {
      // * the immediate name require'd
      URI loadAddress = locate(requireName);
      if (loadAddress == null) {
        // * the require'd name + /index.js
        loadAddress = locate(requireName + MODULE_SLASH + "index.js");
      }
      if (loadAddress == null) {
        // * the require'd name with a potential trailing ".js"
        loadAddress = locate(requireName + ".js");
      }
      return loadAddress != null ? new ModuleUri(loadAddress) : null; // could be null.
    }

    /**
     * Find an ES6 module {@code moduleName} relative to {@code context}.
     * @return The normalized module URI, or {@code null} if not found.
     */
    public ModuleUri resolveEs6Module(String moduleName) {
      // Allow module names with or without the ".js" extension.
      if (!moduleName.endsWith(".js")) {
        moduleName += ".js";
      }
      URI resolved = locateNoCheck(moduleName);
      if (!moduleUris.contains(resolved) && errorHandler != null) {
        errorHandler.report(CheckLevel.WARNING, JSError.make(LOAD_WARNING, moduleName));
      }
      return new ModuleUri(resolved);
    }

    /**
     * Locates the module with the given name, but returns successfully even if
     * there is no JS file corresponding to the returned URI.
     */
    private URI locateNoCheck(String name) {
      URI nameUri = ModuleNames.escapeUri(name);
      if (isRelativeIdentifier(name)) {
        nameUri = uri.resolve(nameUri);
      }
      return normalize(nameUri, moduleRootUris);
    }

    /**
     * Locates the module with the given name, but returns null if there is no JS
     * file in the expected location.
     */
    @Nullable
    private URI locate(String name) {
      URI uri = locateNoCheck(name);
      if (moduleUris.contains(uri)) {
        return uri;
      }
      return null;
    }
  }

  /** Resolves a path into a {@link ModuleUri}. */
  public ModuleUri resolve(String path) {
    return new ModuleUri(
        normalize(ModuleNames.escapeUri(pathResolver.apply(path)), moduleRootUris));
  }

  /** Whether this is relative to the current file, or a top-level identifier. */
  public static boolean isRelativeIdentifier(String name) {
    return name.startsWith("." + MODULE_SLASH) || name.startsWith(".." + MODULE_SLASH);
  }

  /** Whether this is absolute to the compilation. */
  public static boolean isAbsoluteIdentifier(String name) {
    return name.startsWith(MODULE_SLASH);
  }

  private static ImmutableList<URI> createRootUris(Iterable<String> roots, PathResolver resolver) {
    ImmutableList.Builder<URI> builder = ImmutableList.builder();
    for (String root : roots) {
      builder.add(ModuleNames.escapeUri(resolver.apply(root)));
    }
    return builder.build();
  }

  private static ImmutableSet<URI> resolveUris(Iterable<String> names, ImmutableList<URI> roots) {
    HashSet<URI> resolved = new HashSet<>();
    for (String name : names) {
      if (!resolved.add(normalize(ModuleNames.escapeUri(name), roots))) {
        // Having root URIs "a" and "b" and source files "a/f.js" and "b/f.js" is ambiguous.
        throw new IllegalArgumentException(
            "Duplicate module URI after resolving: " + name);
      }
    }
    return ImmutableSet.copyOf(resolved);
  }

  /**
   * Normalizes the name and resolves it against the module roots.
   */
  private static URI normalize(URI uri, Iterable<URI> moduleRootUris) {
    // Find a moduleRoot that this URI is under. If none, use as is.
    for (URI moduleRoot : moduleRootUris) {
      if (uri.toString().startsWith(moduleRoot.toString())) {
        return moduleRoot.relativize(uri);
      }
    }
    // Not underneath any of the roots.
    return uri;
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
