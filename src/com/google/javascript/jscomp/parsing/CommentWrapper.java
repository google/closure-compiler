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

import com.google.javascript.rhino.head.Token;

/**
 * Wrapper for the two comment types so that we can provide an implementation
 * of the Comment interface, regardless of which parser is used.
 */
public class CommentWrapper implements Comment {
  private boolean jsDoc;
  private int position;
  private int length;

  public CommentWrapper(com.google.javascript.jscomp.parsing.parser.trees.Comment comment) {
    jsDoc = comment.type ==
        com.google.javascript.jscomp.parsing.parser.trees.Comment.Type.JSDOC;
    position = comment.location.start.offset;
    length = comment.location.end.offset - position;
  }

  public CommentWrapper(com.google.javascript.rhino.head.ast.Comment comment) {
    jsDoc = comment.getCommentType() == Token.CommentType.JSDOC;
    position = comment.getAbsolutePosition();
    length = comment.getLength();
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
}

