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

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Static methods related to module names.
 */
public class ModuleNames {
  private ModuleNames() {} // Static methods only; do not instantiate.

  /** According to the spec, the forward slash should be the delimiter on all platforms. */
  static final String MODULE_SLASH = "/";

  /** Returns a module name for an absolute path, with no resolution or checking. */
  public static String fileToModuleName(String path) {
    return ModuleNames.toModuleName(ModuleNames.escapeUri(path));
  }

  /** Returns a module name for an absolute path, with no resolution or checking. */
  public static String fileToJsIdentifier(String path) {
    return ModuleNames.toJSIdentifier(ModuleNames.escapeUri(path));
  }

  /** Creates a URI for the given input path. */
  static URI escapeUri(String input) {
    // Handle special characters
    String encodedInput = input.replace(':', '-')
        .replace('\\', '/')
        .replace(" ", "%20")
        .replace("[", "%5B")
        .replace("]", "%5D")
        .replace("<", "%3C")
        .replace(">", "%3E");

    return URI.create(encodedInput).normalize();
  }

  static String toJSIdentifier(URI uri) {
    return stripJsExtension(uri.toString())
        .replaceAll("^\\." + Pattern.quote(MODULE_SLASH), "")
        .replace(MODULE_SLASH, "$")
        .replace('\\', '$')
        .replace('@', '$')
        .replace('-', '_')
        .replace(':', '_')
        .replace('.', '_')
        .replace("%20", "_");
  }

  static String toModuleName(URI uri) {
    return "module$" + toJSIdentifier(uri);
  }

  private static String stripJsExtension(String fileName) {
    if (fileName.endsWith(".js")) {
      return fileName.substring(0, fileName.length() - ".js".length());
    }
    return fileName;
  }
}
