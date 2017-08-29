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
import java.util.Collection;
import java.util.Map;

/**
 * GWT-compatible helper for dealing with Java .properties files. The format is probably not fully
 * parsed by this code, but is suitable for simple use-cases inside Closure.
 */
public class GwtProperties {
  private final Map<String, String> contents;

  private GwtProperties(Map<String, String> contents) {
    this.contents = contents;
  }

  /**
   * @param key Property key to retrieve.
   * @return The string value of this key.
   */
  public String getProperty(String key) {
    return contents.get(key);
  }

  /** @return The collection of property names. */
  public Collection<String> propertyNames() {
    return contents.keySet();
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
    if (line.contains(":") || line.contains("=")) {
      if (line.indexOf(':') == -1) {
        return line.indexOf('=');
      }
      if (line.indexOf('=') == -1) {
        return line.indexOf(':');
      }
      // Both delimeters exist!
      return Math.min(line.indexOf('='), line.indexOf(':'));
    }
    // If no : or =, delimiter is first whitespace.
    return line.indexOf(' ');
  }
  /**
   * Constructs a new {@link GwtProperties} from the given source string.
   *
   * @param source To load from.
   * @return The {@link GwtProperties} object from the source.
   */
  public static GwtProperties load(String source) {
    String[] lines = source.split("\r?\n");
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

    for (int i = 0; i < lines.length; ++i) {
      String line = lines[i];
      if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
        continue; // skip if empty or starts with # or !
      }

      String data = "";

      int delimeterIndex = findDelimiter(line);
      if (delimeterIndex == -1) {
        continue;
      }
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

    return new GwtProperties(builder.build());
    }

  }
