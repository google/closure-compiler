/*
 * Copyright 2018 The Closure Compiler Authors.
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
import org.jspecify.nullness.Nullable;

/**
 * A token representing a javascript template literal substring.
 *
 * <p>The value of the Token is the raw string. The token also stores whether this token contains
 * any error messages that should be passed to the parser due to invalid escapes or unnecessary
 * escapes. The parser, not the scanner, reports these errors because the errors are suppressed in
 * tagged template literals. The scanner does not know if it's in a tagged or untagged template lit.
 */
public class TemplateLiteralToken extends LiteralToken {
  public final @Nullable String errorMessage;
  public final SourcePosition errorPosition;
  public final ErrorLevel errorLevel;

  enum ErrorLevel {
    WARNING,
    ERROR
  }

  public TemplateLiteralToken(
      TokenType type,
      String value,
      String errorMsg,
      ErrorLevel errorLevel,
      SourcePosition position,
      SourceRange location) {
    super(type, value, location);
    this.errorMessage = errorMsg;
    this.errorLevel = errorLevel;
    this.errorPosition = position;
  }

  @Override
  public String toString() {
    return value;
  }

  public boolean hasError() {
    return ErrorLevel.ERROR.equals(errorLevel);
  }
}
