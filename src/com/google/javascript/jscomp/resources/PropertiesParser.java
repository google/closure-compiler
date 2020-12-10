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

package com.google.javascript.jscomp.resources;

import com.google.common.collect.ImmutableMap;

/**
 * Parses a Java properties file in a a way that can be transpiled into JS.
 *
 * <p>The format is probably not fully parsed by this code, but is suitable for simple use-cases
 * inside Closure.
 */
final class PropertiesParser {

  @SuppressWarnings("StringSplitter") // Can't use Splitter.onPattern in J2CL.
  static ImmutableMap<String, String> parse(String source) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

    String[] lines = source.split("\r?\n");
    for (int i = 0; i < lines.length; ++i) {
      String line = lines[i];
      if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
        continue; // skip if empty or starts with # or !
      }

      int delimeterIndex = findDelimiter(line);
      if (delimeterIndex == -1) {
        continue;
      }

      String data = "";
      // Remove whitespace on both sides of key.
      String key = line.substring(0, delimeterIndex).trim();
      // Remove whitespace only on left side of data value. Trailing white space is data.
      line = trimLeft(line.substring(delimeterIndex + 1));
      while (true) {
        if (line.endsWith("\\")) {
          data += line.substring(0, line.length() - 1);
          if (i + 1 == lines.length) {
            break;
          }
          line = trimLeft(lines[++i]);
        } else {
          data += line;
          break;
        }
      }

      builder.put(key, data);
    }

    return builder.build();
  }

  private static String trimLeft(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) != ' ') {
        return str.substring(i);
      }
    }
    return str;
  }

  private static int findDelimiter(String line) {
    for (int i = 0; i < line.length(); i++) {
      switch (line.charAt(i)) {
        case ':':
        case '=':
          return i;
        default:
          break;
      }
    }

    // If no : or =, delimiter is first whitespace.
    return line.indexOf(' ');
  }

  private PropertiesParser() {}
}
