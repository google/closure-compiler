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

/**
 * A Token in a javascript file.
 * Immutable.
 * A plain old data structure. Should contain data members and simple accessors only.
 */
public class Token {

  public final SourceRange location;
  public final TokenType type;

  public Token(TokenType type, SourceRange location) {
    this.type = type;
    this.location = location;
  }

  public SourcePosition getStart() {
    return location.start;
  }

  @Override
  public String toString() {
    return type.toString();
  }

  public IdentifierToken asIdentifier() { return (IdentifierToken) this; }
  public LiteralToken asLiteral() { return (LiteralToken) this; }
}
