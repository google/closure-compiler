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
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps a {@link JsonObject} to provide a V3 source map.
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
    try {
      JsonObject sourceMapRoot = new Gson().fromJson(contents, JsonObject.class);

      version = sourceMapRoot.get("version").getAsInt();
      file = getStringOrNull(sourceMapRoot, "file");
      lineCount = sourceMapRoot.has("lineCount")
          ? sourceMapRoot.get("lineCount").getAsInt() : -1;
      mappings = getStringOrNull(sourceMapRoot, "mappings");
      sourceRoot = getStringOrNull(sourceMapRoot, "sourceRoot");

      if (sourceMapRoot.has("sections")) {
        ImmutableList.Builder<SourceMapSection> builder = ImmutableList.builder();
        for (JsonElement each : sourceMapRoot.get("sections").getAsJsonArray()) {
          builder.add(buildSection(each.getAsJsonObject()));
        }
        sections = builder.build();
      } else {
        sections = null;
      }

      sources = getJavaStringArray(sourceMapRoot.get("sources"));
      names = getJavaStringArray(sourceMapRoot.get("names"));

      Map<String, Object> extensions = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> entry : sourceMapRoot.entrySet()) {
        if (entry.getKey().startsWith("x_")) {
          extensions.put(entry.getKey(), entry.getValue());
        }
      }
      this.extensions = Collections.unmodifiableMap(extensions);
    } catch (JsonParseException ex) {
      throw new SourceMapParseException("JSON parse exception: " + ex);
    }
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

  private static SourceMapSection buildSection(JsonObject section)
      throws JsonParseException, SourceMapParseException {
    JsonObject offset = section.get("offset").getAsJsonObject();
    int line = offset.get("line").getAsInt();
    int column = offset.get("column").getAsInt();

    if (section.has("map") && section.has("url")) {
      throw new SourceMapParseException(
          "Invalid map format: section may not have both 'map' and 'url'");
    } else if (section.has("url")) {
      return SourceMapSection.forURL(section.get("url").getAsString(), line, column);
    } else if (section.has("map")) {
      return SourceMapSection.forMap(section.get("map").toString(), line, column);
    }

    throw new SourceMapParseException(
        "Invalid map format: section must have either 'map' or 'url'");
  }

  private static String getStringOrNull(JsonObject object, String key) {
    return object.has(key) ? object.get(key).getAsString() : null;
  }

  private static String[] getJavaStringArray(JsonElement element) throws JsonParseException {
    if (element == null) {
      return null;
    }
    JsonArray array = element.getAsJsonArray();
    int len = array.size();
    String[] result = new String[len];
    for (int i = 0; i < len; i++) {
      result[i] = array.get(i).getAsString();
    }
    return result;
  }
}
