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
import javax.annotation.Nullable;

/**
 * A token representing a javascript template literal substring.
 *
 * <p>The value of the Token is the raw string. The token also stores whether this token contains
 * any error messages that should be passed to the parser due to invalid escapes.
 */
public class TemplateLiteralToken extends LiteralToken {
  @Nullable public final String errorMessage;
  public final SourcePosition errorPosition;

  public TemplateLiteralToken(
      TokenType type,
      String value,
      String errorMsg,
      SourcePosition position,
      SourceRange location) {
    super(type, value, location);
    this.errorMessage = errorMsg;
    this.errorPosition = position;
  }

  @Override
  public String toString() {
    return value;
  }

  public boolean hasError() {
    return errorMessage != null;
  }
}
