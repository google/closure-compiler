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



/**
 * A source file.
 *
 * Immutable.
 */
public final class SourceFile {
  public final String name;
  public final String contents;
  public final LineNumberTable lineNumberTable;

  public SourceFile(String name, String contents) {
    this.name = name;
    this.contents = contents;
    this.lineNumberTable = new LineNumberTable(this);
  }

  public String getSnippet(SourcePosition position) {
    int lineStart = lineNumberTable.offsetOfLine(position.line);
    int lineEnd = lineNumberTable.offsetOfLine(position.line + 1);

    lineStart = Math.min(lineStart, contents.length() - 1);
    lineEnd = Math.min(lineEnd, contents.length() - 1);
    return contents.substring(lineStart, lineEnd);
  }
}
