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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java implementation of the source map parser.
 */
public class SourceMapObjectParser {
  public static SourceMapObject parse(String contents) throws SourceMapParseException {

    SourceMapObject.Builder builder = SourceMapObject.builder();

    try {
      JsonObject sourceMapRoot = new Gson().fromJson(contents, JsonObject.class);

      builder.setVersion(sourceMapRoot.get("version").getAsInt());
      builder.setFile(getStringOrNull(sourceMapRoot, "file"));
      builder.setLineCount(
          sourceMapRoot.has("lineCount") ? sourceMapRoot.get("lineCount").getAsInt() : -1);
      builder.setMappings(getStringOrNull(sourceMapRoot, "mappings"));
      builder.setSourceRoot(getStringOrNull(sourceMapRoot, "sourceRoot"));

      if (sourceMapRoot.has("sections")) {
        ImmutableList.Builder<SourceMapSection> listBuilder = ImmutableList.builder();
        for (JsonElement each : sourceMapRoot.get("sections").getAsJsonArray()) {
          listBuilder.add(buildSection(each.getAsJsonObject()));
        }
        builder.setSections(listBuilder.build());
      }

      builder.setSources(getJavaStringArray(sourceMapRoot.get("sources")));
      builder.setNames(getJavaStringArray(sourceMapRoot.get("names")));

      Map<String, Object> extensions = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> entry : sourceMapRoot.entrySet()) {
        if (entry.getKey().startsWith("x_")) {
          extensions.put(entry.getKey(), entry.getValue());
        }
      }
      builder.setExtensions(Collections.unmodifiableMap(extensions));

    } catch (JsonParseException ex) {
      throw new SourceMapParseException("JSON parse exception: " + ex);
    }

    return builder.build();
  }

  private static SourceMapSection buildSection(JsonObject section) throws SourceMapParseException {
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

  private static String[] getJavaStringArray(JsonElement element) {
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

  private SourceMapObjectParser() {}
}
