/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Provides compile-time locate semantics for ES6 and CommonJS modules.
 *
 * @see "Section 26.3.3.18.2 of the ES6 spec"
 * @see "http://wiki.commonjs.org/wiki/Modules/1.1"
 */
public final class ES6ModuleLoader {
  /** According to the spec, the forward slash should be the delimiter on all platforms. */
  static final String MODULE_SLASH = "/";
  /** The default module root, the current directory. */
  public static final String DEFAULT_FILENAME_PREFIX = "." + MODULE_SLASH;

  static final DiagnosticType LOAD_ERROR = DiagnosticType.error(
      "JSC_ES6_MODULE_LOAD_ERROR",
      "Failed to load module \"{0}\"");

  /** The root URIs that modules are resolved against. */
  private final List<URI> moduleRootUris;
  /** The set of all known input module URIs (including trailing .js), after normalization. */
  private final Set<URI> moduleUris;

  /**
   * Creates an instance of the module loader which can be used to locate ES6 and CommonJS modules.
   *
   * @param moduleRoots The root directories to locate modules in.
   * @param inputs All inputs to the compilation process.
   */
  public ES6ModuleLoader(List<String> moduleRoots, Iterable<CompilerInput> inputs) {
    this.moduleRootUris =
        Lists.transform(
            moduleRoots,
            new Function<String, URI>() {
              @Override
              public URI apply(String path) {
                return createUri(path);
              }
            });
    this.moduleUris = new HashSet<>();
    for (CompilerInput input : inputs) {
      if (!moduleUris.add(normalizeInputAddress(input))) {
        // Having root URIs "a" and "b" and source files "a/f.js" and "b/f.js" is ambiguous.
        throw new IllegalArgumentException(
            "Duplicate module URI after resolving: " + input.getName());
      }
    }
  }

  /**
   * Find a CommonJS module {@code requireName} relative to {@code context}.
   * @return The normalized module URI, or {@code null} if not found.
   */
  URI locateCommonJsModule(String requireName, CompilerInput context) {
    // * the immediate name require'd
    URI loadAddress = locate(requireName, context);
    if (loadAddress == null) {
      // * the require'd name + /index.js
      loadAddress = locate(requireName + MODULE_SLASH + "index.js", context);
    }
    if (loadAddress == null) {
      // * the require'd name with a potential trailing ".js"
      loadAddress = locate(requireName + ".js", context);
    }
    return loadAddress; // could be null.
  }

  /**
   * Find an ES6 module {@code moduleName} relative to {@code context}.
   * @return The normalized module URI, or {@code null} if not found.
   */
  URI locateEs6Module(String moduleName, CompilerInput context) {
    return locate(moduleName + ".js", context);
  }

  private URI locate(String name, CompilerInput referrer) {
    URI uri = createUri(name);
    if (isRelativeIdentifier(name)) {
      URI referrerUri = normalizeInputAddress(referrer);
      uri = referrerUri.resolve(uri);
    }
    URI normalized = normalizeAddress(uri);
    if (moduleUris.contains(normalized)) {
      return normalized;
    }
    return null;
  }

  /**
   * Normalizes the address of {@code input} and resolves it against the module roots.
   */
  URI normalizeInputAddress(CompilerInput input) {
    String name = input.getName();
    return normalizeAddress(createUri(name));
  }

  /**
   * Normalizes the URI for the given {@code uri} by resolving it against the known
   * {@link #moduleRootUris}.
   */
  private URI normalizeAddress(URI uri) {
    // Find a moduleRoot that this URI is under. If none, use as is.
    for (URI moduleRoot : moduleRootUris) {
      if (uri.toString().startsWith(moduleRoot.toString())) {
        return moduleRoot.relativize(uri);
      }
    }
    // Not underneath any of the roots.
    return uri;
  }

  static URI createUri(String input) {
    // Colons might cause URI.create() to fail
    String forwardSlashes =
        input.replace(':', '-').replace("\\", MODULE_SLASH).replace(" ", "%20");
    return URI.create(forwardSlashes).normalize();
  }

  private static String stripJsExtension(String fileName) {
    if (fileName.endsWith(".js")) {
      return fileName.substring(0, fileName.length() - ".js".length());
    }
    return fileName;
  }

  /** Whether this is relative to the current file, or a top-level identifier. */
  static boolean isRelativeIdentifier(String name) {
    return name.startsWith("." + MODULE_SLASH) || name.startsWith(".." + MODULE_SLASH);
  }

  /** Whether this is absolute to the compilation. */
  static boolean isAbsoluteIdentifier(String name) {
    return name.startsWith(MODULE_SLASH);
  }

  /**
   * Turns a filename into a JS identifier that is used for moduleNames in
   * rewritten code. Removes leading ./, replaces / with $, removes trailing .js
   * and replaces - with _. All moduleNames get a "module$" prefix.
   */
  public static String toModuleName(URI filename) {
    String moduleName =
        stripJsExtension(filename.toString())
            .replaceAll("^\\." + Pattern.quote(MODULE_SLASH), "")
            .replace(MODULE_SLASH, "$")
            .replace('\\', '$')
            .replace('-', '_')
            .replace(':', '_')
            .replace('.', '_')
            .replace("%20", "_");
    return "module$" + moduleName;
  }
}
