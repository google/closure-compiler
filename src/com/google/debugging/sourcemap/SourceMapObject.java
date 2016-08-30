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

import java.util.List;
import java.util.Map;

/** Wraps a JsonObject to provide a V3 source map. */
public class SourceMapObject {
  private final int version;
  private final int lineCount;
  private final String sourceRoot;
  private final String file;
  private final String mappings;
  private final String[] sources;
  private final String[] names;
  private final List<SourceMapSection> sections;
  private final Map<String, Object> extensions;

  private SourceMapObject(
      int version,
      int lineCount,
      String sourceRoot,
      String file,
      String mappings,
      String[] sources,
      String[] names,
      List<SourceMapSection> sections,
      Map<String, Object> extensions) {
    this.version = version;
    this.lineCount = lineCount;
    this.sourceRoot = sourceRoot;
    this.file = file;
    this.mappings = mappings;
    this.sources = sources;
    this.names = names;
    this.sections = sections;
    this.extensions = extensions;
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

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    private int version;
    private int lineCount;
    private String sourceRoot;
    private String file;
    private String mappings;
    private String[] sources;
    private String[] names;
    private List<SourceMapSection> sections;
    private Map<String, Object> extensions;

    public Builder setVersion(int version) {
      this.version = version;
      return this;
    }

    public Builder setLineCount(int lineCount) {
      this.lineCount = lineCount;
      return this;
    }

    public Builder setSourceRoot(String sourceRoot) {
      this.sourceRoot = sourceRoot;
      return this;
    }

    public Builder setFile(String file) {
      this.file = file;
      return this;
    }

    public Builder setMappings(String mappings) {
      this.mappings = mappings;
      return this;
    }

    public Builder setSources(String[] sources) {
      this.sources = sources;
      return this;
    }

    public Builder setNames(String[] names) {
      this.names = names;
      return this;
    }

    public Builder setSections(List<SourceMapSection> sections) {
      this.sections = sections;
      return this;
    }

    public Builder setExtensions(Map<String, Object> extensions) {
      this.extensions = extensions;
      return this;
    }

    public SourceMapObject build() {
      return new SourceMapObject(
          version, lineCount, sourceRoot, file, mappings, sources, names, sections, extensions);
    }
  }
}
