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

import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Maps offsets into a source string into line/column positions.
 *
 * Immutable.
 */
public class LineNumberTable {

  private final SourceFile sourceFile;
  private final int[] lineStartOffsets;

  public LineNumberTable(SourceFile sourceFile) {
    this.sourceFile = sourceFile;
    this.lineStartOffsets = computeLineStartOffsets(sourceFile.contents);
  }

  private static int[] computeLineStartOffsets(String source) {
    // TODO(johnlenz): do this more efficiently.
    ArrayList<Integer> lineStartOffsets = new ArrayList<>();
    lineStartOffsets.add(0);
    for (int index = 0; index < source.length(); index++) {
      char ch = source.charAt(index);
      if (isLineTerminator(ch)) {
        if (index + 1 < source.length() && ch == '\r'
            && source.charAt(index + 1) == '\n') {
          index++;
        }
        lineStartOffsets.add(index + 1);
      }
    }
    lineStartOffsets.add(Integer.MAX_VALUE);
    return toIntArray(lineStartOffsets);
  }

  public static int[] toIntArray(ArrayList<Integer> integers) {
      int[] ret = new int[integers.size()];
      for (int i = 0; i < ret.length; i++) {
          ret[i] = integers.get(i).intValue();
      }
      return ret;
  }

  private static boolean isLineTerminator(char ch) {
    switch (ch) {
    case '\n': // Line Feed
    case '\r':  // Carriage Return
    case '\u2028':  // Line Separator
    case '\u2029':  // Paragraph Separator
      return true;
    default:
      return false;
    }
  }

  public SourcePosition getSourcePosition(int offset) {
    int line = getLine(offset);
    return new SourcePosition(sourceFile, offset, line, getColumn(line, offset));
  }

  public int getLine(int offset) {
    int index = Arrays.binarySearch(lineStartOffsets, offset);
    // start of line
    if (index >= 0) {
      return index;
    }
    return -index - 2;
  }

  public int offsetOfLine(int line) {
    return lineStartOffsets[line];
  }

  private int getColumn(int line, int offset) {
    return offset - offsetOfLine(line);
  }

  public SourceRange getSourceRange(int startOffset, int endOffset) {
    return new SourceRange(getSourcePosition(startOffset), getSourcePosition(endOffset));
  }
}
