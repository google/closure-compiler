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

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import com.google.errorprone.annotations.Immutable;
import java.util.Comparator;

/** Class that represents a set of changes to make to the code. */
@AutoValue
@CopyAnnotations
@Immutable
public abstract class CodeReplacement implements Comparable<CodeReplacement> {

  static CodeReplacement create(int startPosition, int length, String newContent) {
    return create(startPosition, length, newContent, "");
  }

  static CodeReplacement create(int startPosition, int length, String newContent, String sortKey) {
    return new AutoValue_CodeReplacement(startPosition, length, newContent, sortKey);
  }

  /**
   * Returns the start position within the file that the modification should be applied starting at.
   */
  public abstract int getStartPosition();

  /** Returns how many characters the new content should replace in the original content. */
  public abstract int getLength();

  /**
   * Returns the end position within the file that the modification
   * should be applied starting at.
   */
  public int getEndPosition() {
    return getStartPosition() + getLength();
  }

  /** Returns the new content that should be inserted into the file. */
  public abstract String getNewContent();

  /**
   * Returns an additional String key that can be used to sort replacements using lexical ordering.
   */
  abstract String getSortKey();

  @Override
  public final int compareTo(CodeReplacement x) {
    return APPLICATION_ORDER.compare(this, x);
  }

  // Remember to compare every field so that it's consistent with equals.
  private static final Comparator<CodeReplacement> APPLICATION_ORDER =
      Comparator.comparingInt(CodeReplacement::getStartPosition)
          .thenComparingInt(CodeReplacement::getLength)
          .thenComparing(CodeReplacement::getSortKey)
          .thenComparing(CodeReplacement::getNewContent);
}
