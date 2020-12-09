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
import elemental2.core.JSONType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;
import jsinterop.base.Js;
import jsinterop.base.JsPropertyMap;

/**
 * ClientSide implementation of the source map parser using jsinterop.
 *
 * <p>Note: This is invoked through super sourcing of SourceMapObjectParser.
 */
public class SourceMapObjectParser {

  public static SourceMapObject parse(String contents) throws SourceMapParseException {
    JsonMap sourceMap = Js.uncheckedCast(getJSON().parse(contents));
    SourceMapObject.Builder builder = SourceMapObject.builder();

    builder.setVersion(sourceMap.version);
    builder.setFile(sourceMap.file);
    // Line count is no longer part of the source map spec. -1 is the magic "not provided" value
    builder.setLineCount(-1);
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
    sourceMap.forEach(
        (key) -> {
          if (key.startsWith("x_")) {
            extensions.put(key, sourceMap.get(key));
          }
        });
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

  @JsProperty(namespace = JsPackage.GLOBAL)
  private static native JSONType getJSON();

  @JsType(
      isNative = true,
      namespace = JsPackage.GLOBAL,
      name = "com_google_debugging_sourcemap_SourceMapObjectParserJs$JsonMap")
  private abstract static class JsonMap implements JsPropertyMap<Object> {
    int version;
    String file;
    String mappings;
    String sourceRoot;
    Section[] sections;
    String[] sources;
    String[] names;
  }

  @JsType(
      isNative = true,
      namespace = JsPackage.GLOBAL,
      name = "com_google_debugging_sourcemap_SourceMapObjectParserJs$Section")
  private abstract static class Section {
    Offset offset;
    String url;
    String map;
  }

  @JsType(
      isNative = true,
      namespace = JsPackage.GLOBAL,
      name = "com_google_debugging_sourcemap_SourceMapObjectParserJs$Offset")
  private abstract static class Offset {
    int line;
    int column;
  }
}
