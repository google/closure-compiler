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
    if (Character.isLetter(ch)) {
      return true;
    }

    // Workaround for b/36459436.
    // When running under GWT/J2CL, Character.isLetter only handles ASCII.
    // Angular relies heavily on Latin Small Letter Barred O and Greek Capital Letter Delta.
    // Greek letters are occasionally found in math code.
    // Latin letters are found in our own tests.
    return (ch >= 0x00C0 & ch <= 0x00D6) // Latin letters
        // 0x00D7 = multiplication sign, not a letter
        | (ch >= 0x00D8 & ch <= 0x00F6) // Latin letters
        // 0x00F7 = division sign, not a letter
        | (ch >= 0x00F8 & ch <= 0x00FF) // Latin letters
        | ch == 0x0275 // Latin Barred O
        | (ch >= 0x0391 & ch <= 0x03A1) // Greek uppercase letters
        // 0x03A2 = unassigned
        | (ch >= 0x03A3 & ch <= 0x03A9) // Remaining Greek uppercase letters
        | (ch >= 0x03B1 & ch <= 0x03C9); // Greek lowercase letters
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
