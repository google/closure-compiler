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

package com.google.javascript.jscomp.parsing.parser;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;
import java.util.Objects;

/** Utility for finding line and column offsets within a source file. */
final class LineNumberScanner {

  private final SourceFile sourceFile;
  private final String contents;
  private final int sourceLength;
  private int lastLine = -1;
  private int lastLineStart = -1;
  private int nextLineStart = 0;

  LineNumberScanner(SourceFile sourceFile) {
    this.sourceFile = sourceFile;
    this.contents = sourceFile.contents;
    this.sourceLength = contents.length();
  }

  /**
   * Returns the source position of character offset {@code offset}. This class expects this method
   * to be called with increasing values of {@code offset}, more or less. {@link #rewindTo} must be
   * called before backing up to a previous line.
   */
  SourcePosition getSourcePosition(int offset) {
    Preconditions.checkArgument(
        offset >= lastLineStart,
        "Must call rewindTo before calling getSourcePosition for an earlier line (%s < %s)",
        offset,
        lastLineStart);
    while (offset >= nextLineStart) {
      advanceLine();
    }
    return new SourcePosition(sourceFile, offset, lastLine, offset - lastLineStart);
  }

  SourceRange getSourceRange(int startOffset, int endOffset) {
    return new SourceRange(getSourcePosition(startOffset), getSourcePosition(endOffset));
  }

  /**
   * Call this method to rewind the scanner to an earlier position in the source file. This is
   * necessary if backing up to a previous line.
   */
  void rewindTo(SourcePosition position) {
    Preconditions.checkArgument(Objects.equals(position.source, sourceFile));
    if (position.offset < lastLineStart) {
      lastLine = position.line - 1;
      nextLineStart = position.offset - position.column;
      advanceLine();
    }
  }

  private void advanceLine() {
    lastLine++;

    lastLineStart = nextLineStart;
    for (int index = lastLineStart; index < sourceLength; index++) {
      char ch = contents.charAt(index);
      if (isLineTerminator(ch)) {
        if (ch == '\r' && index + 1 < sourceLength && contents.charAt(index + 1) == '\n') {
          index++;
        }
        nextLineStart = index + 1;
        return;
      }
    }
    nextLineStart = Integer.MAX_VALUE;
  }

  private static boolean isLineTerminator(char ch) {
    switch (ch) {
      case '\n': // Line Feed
      case '\r': // Carriage Return
      case '\u2028': // Line Separator
      case '\u2029': // Paragraph Separator
        return true;
      default:
        return false;
    }
  }
}
