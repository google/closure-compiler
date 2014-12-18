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

package com.google.javascript.jscomp.parsing.parser.trees;

import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

/** placeholder class */
public class Comment {
  public static enum Type {
    // /* comment */
    BLOCK,

    // // comment
    LINE,

    // /** comment */
    JSDOC,

    // #!/usr/bin/node
    // Only valid at the start of a file.
    SHEBANG
  }

  public final String value;
  public final SourceRange location;
  public final Type type;

  public Comment(String value, SourceRange location, Type type) {
    this.value = value;
    this.location = location;
    this.type = type;
  }

  public boolean isJsDoc() {
    return type == Type.JSDOC;
  }

  public int getAbsolutePosition() {
    return location.start.offset;
  }

  public int getLength() {
    return location.end.offset - location.start.offset;
  }
}
