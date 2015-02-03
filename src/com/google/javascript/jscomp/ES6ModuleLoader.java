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

import com.google.common.collect.Maps;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Provides compile-time locate semantics for ES6 and CommonJS modules.
 *
 * @see Section 26.3.3.18.2 of the ES6 spec
 * @see http://wiki.commonjs.org/wiki/Modules/1.1
 */
class ES6ModuleLoader {
  /**
   * According to the spec, the forward slash should be the delimiter on all
   * platforms.
   */
  static final String MODULE_SLASH = "/";

  /**
   * When a module resolves to a directory, this index file is checked for.
   */
  static final String INDEX_FILE = "index.js";

  /**
   * Whether this is relative to the current file, or a top-level identifier.
   */
  static boolean isRelativeIdentifier(String name) {
    return name.startsWith("." + MODULE_SLASH) ||
        name.startsWith(".." + MODULE_SLASH);
  }

  static final DiagnosticType LOAD_ERROR = DiagnosticType.error(
      "JSC_ES6_MODULE_LOAD_ERROR",
      "Failed to load module \"{0}\"");

  private final Map<String, CompilerInput> inputsByAddress = Maps.newHashMap();
  private final String moduleRoot;
  private final URI moduleRootURI;

  ES6ModuleLoader(AbstractCompiler compiler, String moduleRoot) {
    this.moduleRoot = moduleRoot;
    this.moduleRootURI = createUri(moduleRoot);

    // Precompute the module name of each source file.
    for (CompilerInput input : compiler.getInputsInOrder()) {
      inputsByAddress.put(getLoadAddress(input), input);
    }
  }

  /**
   * Error thrown when a load fails.
   */
  static class LoadFailedException extends Exception {}

  /**
   * The normalize hook creates a global qualified name for a module, and then
   * the locate hook creates an address. Meant to mimic the behavior of these
   * two hooks.
   * @param name The name passed to the require() call
   * @param referrer The file where we're calling from
   * @return A globally unique address.
   */
  String locate(String name, CompilerInput referrer) {
    URI base = isRelativeIdentifier(name) ? createUri(referrer) : moduleRootURI;

    return convertSourceUriToModuleAddress(base.resolve(createUri(name)));
  }

  /**
   * Locates a compiler input by ES6 module address.
   *
   * If the input doesn't exist, the implementation can decide whether to create
   * an input, or fail softly (by returning null), or throw an error.
   */
  CompilerInput load(String name) throws LoadFailedException {
    return inputsByAddress.get(name);
  }

  /**
   * Gets the ES6 module address for an input.
   */
  String getLoadAddress(CompilerInput input) {
    return convertSourceUriToModuleAddress(createUri(input));
  }

  private static URI createUri(CompilerInput input) {
    return createUri(input.getName().replace("\\", MODULE_SLASH));
  }

  // TODO(nicksantos): Figure out a better way to deal with
  // URI syntax errors.
  private static URI createUri(String uri) {
    try {
      return new URI(uri);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private String resolveInFileSystem(String filename) {
    File f = new File(filename);

    // Resolve index.js files within directories.
    if (f.exists() && f.isDirectory()) {
      File index = new File(f, INDEX_FILE);
      if (index.exists()) {
        return moduleRootURI.relativize(index.toURI()).getPath();
      }
    }

    return filename;
  }

  private String convertSourceUriToModuleAddress(URI uri) {
    String filename = resolveInFileSystem(uri.normalize().toString());

    // The DOS command shell will normalize "/" to "\", so we have to
    // wrestle it back to conform the the module standard.
    filename = filename.replace("\\", MODULE_SLASH);

    // TODO(nicksantos): It's not totally clear to me what
    // should happen if a file is not under the given module root.
    // Maybe this should be an error, or resolved differently.
    if (!moduleRoot.isEmpty() && filename.indexOf(moduleRoot) == 0) {
      filename = filename.substring(moduleRoot.length());
    }

    return filename;
  }
}
