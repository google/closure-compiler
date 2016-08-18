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

package com.google.javascript.jscomp;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GWT-compatible helper for dealing with Java .properties files. The format is probably not
 * fully parsed by this code, but is suitable for simple use-cases inside Closure.
 */
public class GwtProperties {
  // Matches the first part of a property, e.g. "foo.bar.baz = "
  private static final Pattern PROP_DEF = Pattern.compile("^(\\w+(\\.\\w+)*)\\s*[:= ]");

  // Matches a value part of a property, e.g. "  value\" (continuation) or "value"
  private static final Pattern PROP_LINE = Pattern.compile("^\\s*(.*?)(\\\\?)$");  // literal "\"

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

  /**
   * @return The collection of property names.
   */
  public Collection<String> propertyNames() {
    return contents.keySet();
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
        continue;  // skip if empty or starts with # or !
      }

      Matcher m = PROP_DEF.matcher(line);
      if (!m.find()) {
        continue;
      }
      String key = m.group(1);
      String data = "";

      line = line.substring(m.group(0).length());  // remove matched part
      for (;;) {
        Matcher lineMatch = PROP_LINE.matcher(line);
        if (!lineMatch.matches()) {
          // Should never happen, since PROP_LINE contains .* and no hard requirements.
          throw new RuntimeException("Properties parser failed on line: " + line);
        }
        data += lineMatch.group(1);  // add content found

        // If the line ends with "/", then consume another line if possible.
        boolean isLastLine = lineMatch.group(2).isEmpty();
        if (isLastLine || i + 1 == lines.length) {
          break;
        }
        line = lines[++i];
      }

      builder.put(key, data);
    }

    return new GwtProperties(builder.build());
  }

}
