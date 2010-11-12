/*
 * Copyright 2009 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

/**
 * Strategies for how to do naming of anonymous functions that occur as
 * r-values in assignments and variable declarations.
 */
public enum AnonymousFunctionNamingPolicy {

  /** Don't give anonymous functions names */
  OFF(null),

  /**
   * Generates names that are based on the left-hand side of the assignment.
   * Runs after variable and property renaming, so that the generated names
   * will be short and obfuscated.
   * @see NameAnonymousFunctions
   */
  UNMAPPED(new char[] { NameAnonymousFunctions.DELIMITER }),

  /**
   * Generates short unique names and provides a mapping from them back to a
   * more meaningful name that's based on the left-hand side of the
   * assignment.
   * @see NameAnonymousFunctionsMapped
   */
  MAPPED(new char[] { NameAnonymousFunctionsMapped.PREFIX }),
  ;

  private final char[] reservedCharacters;

  AnonymousFunctionNamingPolicy(char[] reservedCharacters) {
    this.reservedCharacters = reservedCharacters;
  }

  /**
   * Gets characters that are reserved for use in anonymous function names and
   * can't be used in variable or property names.
   * @return reserved characters or null if no characters are reserved
   */
  public char[] getReservedCharacters() {
    // TODO(user) - for MAPPED, only the first character is reserved which
    // can be used to further optimize
    return reservedCharacters;
  }
}
