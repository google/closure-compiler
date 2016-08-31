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

import com.google.common.base.Joiner;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Static methods related to module names.
 */
public class ModuleNames {
  private ModuleNames() {} // Static methods only; do not instantiate.

  /** According to the spec, the forward slash should be the delimiter on all platforms. */
  static final String MODULE_SLASH = "/";

  /** To join together normalized module names. */
  private static final Joiner MODULE_JOINER = Joiner.on(MODULE_SLASH);

  /** Returns a module name for an absolute path, with no resolution or checking. */
  public static String fileToModuleName(String path) {
    return toModuleName(escapePath(path));
  }

  /** Returns a module name for an absolute path, with no resolution or checking. */
  public static String fileToJsIdentifier(String path) {
    return toJSIdentifier(escapePath(path));
  }

  /** Escapes the given input path. */
  static String escapePath(String input) {
    // Handle special characters
    String encodedInput = input.replace(':', '-')
        .replace('\\', '/')
        .replace(" ", "%20")
        .replace("[", "%5B")
        .replace("]", "%5D")
        .replace("<", "%3C")
        .replace(">", "%3E");

    return canonicalizePath(encodedInput);
  }

  static String toJSIdentifier(String path) {
    return stripJsExtension(path)
        .replaceAll("^\\." + Pattern.quote(MODULE_SLASH), "")
        .replace(MODULE_SLASH, "$")
        .replace('\\', '$')
        .replace('@', '$')
        .replace('-', '_')
        .replace(':', '_')
        .replace('.', '_')
        .replace("%20", "_");
  }

  static String toModuleName(String path) {
    return "module$" + toJSIdentifier(path);
  }

  private static String stripJsExtension(String fileName) {
    if (fileName.endsWith(".js")) {
      return fileName.substring(0, fileName.length() - ".js".length());
    }
    return fileName;
  }

  /**
   * Canonicalize a given path, removing segments containing "." and consuming segments for "..".
   *
   * If no segment could be consumed for "..", retains the segment.
   */
  static String canonicalizePath(String path) {
    String[] parts = path.split(Pattern.quote(MODULE_SLASH));
    String[] buffer = new String[parts.length];
    int position = 0;
    int available = 0;

    boolean absolutePath = (parts.length > 1 && parts[0].isEmpty());
    if (absolutePath) {
      // If the path starts with "/" (so the left side, index zero, is empty), then the path will
      // always remain absolute. Make the first segment unavailable to touch.
      --available;
    }

    for (String part : parts) {
      if (part.equals(".")) {
        continue;
      }

      if (part.equals("..")) {
        if (available > 0) {
          // Consume the previous segment.
          --position;
          --available;
          buffer[position] = null;
        } else if (!absolutePath) {
          // If this is a relative path, retain "..", as it can't be consumed on the left.
          buffer[position] = part;
          ++position;
        }
        continue;
      }

      buffer[position] = part;
      ++position;
      ++available;
    }

    if (absolutePath && position == 1) {
      return MODULE_SLASH;  // special-case single absolute segment as joining [""] doesn't work
    }
    return MODULE_JOINER.join(Arrays.copyOf(buffer, position));
  }
}
