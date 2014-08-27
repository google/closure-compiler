/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import java.util.Objects;

/**
 * Class that represents a set of changes to make to the code.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
public final class CodeReplacement {

  private final int startPosition;
  private final int length;
  private final String newContent;

  CodeReplacement(int startPosition, int length, String newContent) {
    this.startPosition = startPosition;
    this.length = length;
    this.newContent = newContent;
  }

  /**
   * Returns the start position within the file that the modification
   * should be applied starting at.
   */
  public int getStartPosition() {
    return startPosition;
  }

  /**
   * Returns how many bytes the new content should replace in the
   * original content.
   */
  public int getLength() {
    return length;
  }

  /**
   * Returns the new content that should be inserted into the file.
   */
  public String getNewContent() {
    return newContent;
  }

  @Override public String toString() {
    return String.format(
        "Start position: %d\nLength: %d\nNew Content: \"%s\"", startPosition, length, newContent);
  }

  @Override public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CodeReplacement)) {
      return false;
    }
    CodeReplacement other = (CodeReplacement) o;
    return startPosition == other.startPosition
        && length == other.length
        && newContent.equals(other.newContent);
  }

  @Override public int hashCode() {
    return Objects.hash(startPosition, length, newContent);
  }
}
