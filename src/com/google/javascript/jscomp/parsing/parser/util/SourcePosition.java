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

package com.google.javascript.jscomp.parsing.parser.util;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.parsing.parser.SourceFile;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;

/**
 * A position in a source string - includes offset, line and column.
 */
public class SourcePosition {

  public final SourceFile source;
  public final int offset;
  public final int line;
  public final int column;

  public SourcePosition(SourceFile source, int offset, int line, int column) {
    this.source = source;
    this.offset = offset;
    this.line = line;
    this.column = column;
  }

  @Override
  public String toString() {
    return SimpleFormat.format("%s(%d, %d)", shortSourceName(), line + 1, column + 1);
  }

  private String shortSourceName() {
    if (source == null) {
      return "";
    }

    return Iterables.getLast(Splitter.on('/').split(source.name));
  }
}
