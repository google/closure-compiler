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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

/** A token representing an identifier. */
public class IdentifierToken extends Token {
  private final String value;
  private final boolean privateIdentifier;

  public IdentifierToken(SourceRange location, String value) {
    super(TokenType.IDENTIFIER, location);
    this.value = value;
    privateIdentifier = value.startsWith("#");
  }

  @Override
  public String toString() {
    return value;
  }

  public boolean isKeyword() {
    return Keywords.isKeyword(value);
  }

  public boolean valueEquals(String str) {
    return value.equals(str);
  }

  /**
   * Gets the value of the identifier assuring that it is not a private identifier.
   *
   * <p>You must verify privateIdentifier is false (and presumably error if it is true) before
   * calling this method.
   *
   * <p>Prefer calling {@link #isKeyword()} or {@link #valueEquals(String)} if those methods meet
   * your needs.
   */
  public String getValue() {
    checkState(!privateIdentifier);
    return value;
  }

  /** Gets the value of the identifier, allowing it to be a private identifier. */
  public String getMaybePrivateValue() {
    return value;
  }

  /** Whether the value starts with a #. */
  public boolean isPrivateIdentifier() {
    return privateIdentifier;
  }
}
