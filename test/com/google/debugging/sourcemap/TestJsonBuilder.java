/*
 * Copyright 2011 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableMap;

/**
 * A minimal builder for a {@link Map} representing a source map JSON file.
 *
 * <p>This builder is designed and intended for tests. It does not check any invariants. It has only
 * the bare minimum type checks. It does not support round-tripping of Java types.
 */
final class TestJsonBuilder {

  private final ImmutableMap.Builder<String, Object> internal = ImmutableMap.builder();

  private final ImmutableList.Builder<ImmutableMap<String, Object>> sections =
      ImmutableList.builder();

  static TestJsonBuilder create() {
    return new TestJsonBuilder();
  }

  TestJsonBuilder setVersion(int version) {
    internal.put("version", version);
    return this;
  }

  TestJsonBuilder setFile(String file) {
    internal.put("file", file);
    return this;
  }

  TestJsonBuilder setLineCount(int count) {
    internal.put("lineCount", count);
    return this;
  }

  TestJsonBuilder setMappings(String mappings) {
    internal.put("mappings", mappings);
    return this;
  }

  TestJsonBuilder setSourceRoot(String root) {
    internal.put("sourceRoot", root);
    return this;
  }

  TestJsonBuilder setSources(String... sources) {
    internal.put("sources", sources);
    return this;
  }

  TestJsonBuilder setSourcesContent(String... contents) {
    internal.put("sourcesContent", contents);
    return this;
  }

  TestJsonBuilder setNames(String... names) {
    internal.put("names", names);
    return this;
  }

  TestJsonBuilder addSection(int line, int column, TestJsonBuilder map) {
    sections.add(
        ImmutableMap.<String, Object>builder()
            .put("offset", ImmutableMap.of("line", 1, "column", 2))
            .put("map", map.build())
            .buildOrThrow());
    internal.put("sections", sections.build());
    return this;
  }

  TestJsonBuilder setCustomProperty(String name, Object value) {
    internal.put(name, value);
    return this;
  }

  ImmutableMap<String, ?> build() {
    return internal.buildOrThrow();
  }

  private TestJsonBuilder() {}
}
