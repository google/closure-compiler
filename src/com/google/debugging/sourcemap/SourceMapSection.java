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

/** A class representing a partial source map. */
public record SourceMapSection(SectionType type, String value, int line, int column) {

  public static enum SectionType {
    URL,
    MAP
  }

  public static SourceMapSection forMap(String value, int line, int column) {
    return new SourceMapSection(SectionType.MAP, value, line, column);
  }

  public static SourceMapSection forURL(String value, int line, int column) {
    return new SourceMapSection(SectionType.URL, value, line, column);
  }

  public SectionType getSectionType() {
    return type;
  }

  /**
   * Returns the value that represents the map for this section. This is either a URL for a valid
   * source map file that represents a section of a generate source file such as when multiple files
   * are concatenated together, or a string representing a JSON object that is a source map.
   */
  public String getSectionValue() {
    return value;
  }

  /** Returns the starting line for this section */
  public int getLine() {
    return line;
  }

  /** Returns the starting column for this section */
  public int getColumn() {
    return column;
  }
}
