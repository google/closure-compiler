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
package com.google.javascript.jscomp.parsing;

/**
 * This class was created as a wrapper for the two comment types
 * (from the Rhino parser and the new parser) so that we could
 * provide an implementation of the Comment interface, regardless
 * of which parser is used. Now there is only one parser so:
 * TODO(tbreisacher): Consider remove this class and letting
 * com.google.javascript.jscomp.parsing.parser.trees.Comment
 * implement the Comment interface directly, as long as doing so
 * doesn't leak the intermediate AST.
 */
public class CommentWrapper implements Comment {
  private final boolean jsDoc;
  private final int line;
  private final int position;
  private final int length;
  private final String text;

  public CommentWrapper(com.google.javascript.jscomp.parsing.parser.trees.Comment comment) {
    jsDoc = comment.type ==
        com.google.javascript.jscomp.parsing.parser.trees.Comment.Type.JSDOC;
    position = comment.location.start.offset;
    length = comment.location.end.offset - position;
    text = comment.value;
    line = comment.location.start.line;
  }

  @Override
  public boolean isJsDoc() {
    return jsDoc;
  }

  @Override
  public int getAbsolutePosition() {
    return position;
  }

  @Override
  public int getLength() {
    return length;
  }

  @Override
  public String getText() {
    return text;
  }

  @Override
  public int getLine() {
    return line;
  }
}

