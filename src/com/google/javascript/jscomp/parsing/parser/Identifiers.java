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

/** JS identifier parsing utilities. */
public final class Identifiers {

  @SuppressWarnings("ShortCircuitBoolean") // Intentional to minimize branches in this code
  public static boolean isIdentifierStart(char ch) {
    // Most code is written in pure ASCII, so create a fast path here.
    if (ch <= 127) {
      // Intentionally avoiding short circuiting behavior of "||" and "&&".
      // This minimizes branches in this code which minimizes branch prediction misses.
      return ((ch >= 'A' & ch <= 'Z') | (ch >= 'a' & ch <= 'z') | (ch == '_' | ch == '$'));
    }

    // Handle non-ASCII characters.
    // TODO(tjgq): This should include all characters with the ID_Start property.
    return Character.isLetter(ch);
  }

  @SuppressWarnings("ShortCircuitBoolean") // Intentional to minimize branches in this code
  public static boolean isIdentifierPart(char ch) {
    // Most code is written in pure ASCII, so create a fast path here.
    if (ch <= 127) {
      return ((ch >= 'A' & ch <= 'Z')
          | (ch >= 'a' & ch <= 'z')
          | (ch >= '0' & ch <= '9')
          | (ch == '_' | ch == '$'));
    }

    // Handle non-ASCII characters.
    // TODO(tjgq): This should include all characters with the ID_Continue property, plus
    // Zero Width Non-Joiner and Zero Width Joiner.
    return isIdentifierStart(ch) || Character.isDigit(ch);
  }

  private Identifiers() {}
}
