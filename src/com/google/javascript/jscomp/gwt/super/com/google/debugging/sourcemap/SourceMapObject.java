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

package com.google.debugging.sourcemap;

import com.google.common.collect.ImmutableList;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses external JSON within GWT to provide a V3 source map. Replaces the Closure-internal
 * version which uses GSON.
 */
public final class SourceMapObject {
  private final int version;
  private final int lineCount;
  private final String sourceRoot;
  private final String file;
  private final String mappings;
  private final String[] sources;
  private final String[] names;
  private final List<SourceMapSection> sections;
  private final Map<String, Object> extensions;

  /**
   * Construct a new {@link SourceMapObject} from the source JSON.
   */
  public SourceMapObject(String contents) throws SourceMapParseException {
    JSONValue value = JSONParser.parseStrict(contents);
    JSONObject sourceMapRoot = value != null ? value.isObject() : null;
    if (sourceMapRoot == null) {
      throw new SourceMapParseException("couldn't parseStrict contents of source map");
    }

    version = getNumber(sourceMapRoot.get("version"), 0);
    file = getStringOrNull(sourceMapRoot.get("file"));
    lineCount = getNumber(sourceMapRoot.get("lineCount"), -1);
    mappings = getStringOrNull(sourceMapRoot.get("mappings"));
    sourceRoot = getStringOrNull(sourceMapRoot.get("sourceRoot"));

    if (sourceMapRoot.containsKey("sections")) {
      ImmutableList.Builder<SourceMapSection> builder = ImmutableList.builder();
      JSONArray array = sourceMapRoot.get("sections").isArray();
      if (array != null) {
        for (int i = 0, count = array.size(); i < count; ++i) {
          builder.add(buildSection(array.get(i).isObject()));
        }
      }
      sections = builder.build();
    } else {
      sections = null;
    }

    sources = getJavaStringArray(sourceMapRoot.get("sources"));
    names = getJavaStringArray(sourceMapRoot.get("names"));

    Map<String, Object> extensions = new LinkedHashMap<>();
    for (String key : sourceMapRoot.keySet()) {
      if (key.startsWith("x_")) {
        extensions.put(key, sourceMapRoot.get(key));
      }
    }
    this.extensions = Collections.unmodifiableMap(extensions);
  }

  public int getVersion() {
    return version;
  }

  public int getLineCount() {
    return lineCount;
  }

  public String getSourceRoot() {
    return sourceRoot;
  }

  public String getFile() {
    return file;
  }

  public String getMappings() {
    return mappings;
  }

  public String[] getSources() {
    return sources;
  }

  public String[] getNames() {
    return names;
  }

  public List<SourceMapSection> getSections() {
    return sections;
  }

  public Map<String, Object> getExtensions() {
    return extensions;
  }

  private static SourceMapSection buildSection(JSONObject section)
      throws SourceMapParseException {
    JSONObject offset = section.get("offset").isObject();
    if (offset == null || !offset.containsKey("line") || !offset.containsKey("column")) {
      throw new SourceMapParseException("Invalid map format: missing offset");
    }
    int line = getNumber(offset.get("line"), -1);
    int column = getNumber(offset.get("column"), -1);

    if (section.containsKey("map") && section.containsKey("url")) {
      throw new SourceMapParseException(
          "Invalid map format: section may not have both 'map' and 'url'");
    } else if (section.containsKey("url")) {
      return SourceMapSection.forURL(getStringOrNull(section.get("url")), line, column);
    } else if (section.containsKey("map")) {
      return SourceMapSection.forMap(section.get("map").toString(), line, column);
    }

    throw new SourceMapParseException(
        "Invalid map format: section must have either 'map' or 'url'");
  }

  private static String[] getJavaStringArray(JSONValue value) {
    if (value == null) {
      return null;
    }
    JSONArray array = value.isArray();
    if (array == null) {
      return null;
    }
    int count = array.size();
    String[] result = new String[count];
    for (int i = 0; i < count; ++i) {
      result[i] = getStringOrNull(array.get(i));
    }
    return result;
  }

  private static String getStringOrNull(JSONValue value) {
    if (value == null) {
      return null;
    }
    JSONString string = value.isString();
    if (string == null) {
      return null;
    }
    return string.stringValue();
  }

  private static int getNumber(JSONValue value, int def) {
    if (value == null) {
      return def;
    }
    JSONNumber number = value.isNumber();
    if (number == null) {
      return def;
    }
    return (int) number.doubleValue();
  }
}
