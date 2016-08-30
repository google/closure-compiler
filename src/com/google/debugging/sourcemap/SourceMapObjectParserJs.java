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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;

/**
 * ClientSide implementation of the source map parser using jsinterop.
 *
 * Note: This is invoked through super sourcing of SourceMapObjectParser.
 */
public class SourceMapObjectParserJs {

  // TODO(dankurka): Use elemental2 here once available
  @JsMethod(name = "parse", namespace = "JSON")
  private static native Object parseJson(String json);

  // TODO(dankurka): Use elemental2 here once available
  @JsMethod(name = "keys", namespace = "Object")
  private static native String[] keys(Object o);

  // TODO(dankurka): Switch to our general util once available
  // JsMethod to prevent mangling
  @JsMethod
  public static native Object get(Object o, String key) /*-{
    return o[key];
  }-*/;

  @JsType(isNative = true, name = "Object", namespace = JsPackage.GLOBAL)
  private static class JsonMap {
    int version;
    String file;
    int lineCount;
    String mappings;
    String sourceRoot;
    Section[] sections;
    String[] sources;
    String[] names;
    @JsMethod
    native Object getLineCount();
  }

  @JsType(isNative = true, name = "Object", namespace = JsPackage.GLOBAL)
  private static class Section {
    Offset offset;
    String url;
    String map;
  }

  @JsType(isNative = true, name = "Object", namespace = JsPackage.GLOBAL)
  private static class Offset {
    int line;
    int column;
  }

  public static SourceMapObject parse(String contents) throws SourceMapParseException {

    SourceMapObject.Builder builder = SourceMapObject.builder();

    Object jsonInstance = null;

    try {
      jsonInstance = parseJson(contents);
    } catch (Exception ex) {
      throw new SourceMapParseException("JSON parse exception: " + ex);
    }

    JsonMap sourceMap = (JsonMap) jsonInstance;

    builder.setVersion(sourceMap.version);
    builder.setFile(sourceMap.file);
    builder.setLineCount(sourceMap.getLineCount() != null ? sourceMap.lineCount : -1);
    builder.setMappings(sourceMap.mappings);
    builder.setSourceRoot(sourceMap.sourceRoot);

    if (sourceMap.sections != null) {
      ImmutableList.Builder<SourceMapSection> listBuilder = ImmutableList.builder();
      for (Section section : sourceMap.sections) {
        listBuilder.add(buildSection(section));
      }
      builder.setSections(listBuilder.build());
    } else {
      builder.setSections(null);
    }

    builder.setSources(sourceMap.sources);
    builder.setNames(sourceMap.names);

    Map<String, Object> extensions = new LinkedHashMap<>();
    String[] keys = keys(sourceMap);

    for (String key : keys) {
      if (key.startsWith("x_")) {
        extensions.put(key, get(sourceMap, key));
      }
    }

    builder.setExtensions(Collections.unmodifiableMap(extensions));
    return builder.build();
  }

  private static SourceMapSection buildSection(Section section) throws SourceMapParseException {
    int line = section.offset.line;
    int column = section.offset.column;

    if (section.map != null && section.url != null) {
      throw new SourceMapParseException(
          "Invalid map format: section may not have both 'map' and 'url'");
    } else if (section.url != null) {
      return SourceMapSection.forURL(section.url, line, column);
    } else if (section.map != null) {
      return SourceMapSection.forMap(String.valueOf(section.map), line, column);
    }

    throw new SourceMapParseException(
        "Invalid map format: section must have either 'map' or 'url'");
  }
}
