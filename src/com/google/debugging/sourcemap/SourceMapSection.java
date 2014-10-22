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

/**
 * A class representing a partial source map.
 * @author johnlenz@google.com (John Lenz)
 */
public class SourceMapSection {

  /**
   * A URL for a valid source map file that represents a section of a generate
   * source file such as when multiple files are concatenated together.
   */
  private final String value;
  private final int line;
  private final int column;
  private final SectionType type;

  public static enum SectionType {
    URL,
    MAP
  }

  /**
   * @param sectionUrl The URL for the partial source map
   * @param line The number of lines into the file where the represented section
   *    starts.
   * @param column The number of characters into the line where the represented
   *    section starts.
   * @deprecated
   */
  @Deprecated
  public SourceMapSection(String sectionUrl, int line, int column) {
    this.type = SectionType.URL;
    this.value = sectionUrl;
    this.line = line;
    this.column = column;
  }

  private SourceMapSection(
      SectionType type, String value, int line, int column) {
    this.type = type;
    this.value = value;
    this.line = line;
    this.column = column;
  }

  public static SourceMapSection forMap(String value, int line, int column) {
    return new SourceMapSection(SectionType.MAP, value, line, column);
  }

  public static SourceMapSection forURL(String value, int line, int column) {
    return new SourceMapSection(SectionType.URL, value, line, column);
  }

  public SectionType getSectionType() {
    return this.type;
  }

  /**
   * @return the value that represents the map for this section.
   */
  public String getSectionValue() {
    return value;
  }

  /**
   * @return the starting line for this section
   */
  public int getLine() {
    return line;
  }

  /**
   * @return the column for this section
   */
  public int getColumn() {
    return column;
  }
}
