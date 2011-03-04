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
   * A url for a valid source map file that represents a section of a generate
   * source file such as when multiple files are concatenated together.
   */
  private final String sectionUrl;
  private final long representedLength;

  /**
   * @param sectionUrl The url for the partial sourcemap
   * @param length The number of character represented by the source
   * map section.
   */
  public SourceMapSection(String sectionUrl, long length) {
    this.sectionUrl = sectionUrl;
    this.representedLength = length;
  }

  /**
   * @return the name
   */
  public String getSectionUrl() {
    return sectionUrl;
  }

  /**
   * @return the length
   */
  public long getLength() {
    return representedLength;
  }
}
