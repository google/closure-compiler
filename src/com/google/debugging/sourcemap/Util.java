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
package com.google.debugging.sourcemap;

import java.io.IOException;

public class Util {

  private static final char[] HEX_CHARS
      = { '0', '1', '2', '3', '4', '5', '6', '7',
          '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

  /**
   * Escapes the given string to a double quoted (") JavaScript/JSON string
   */
  static String escapeString(String s) {
    return escapeString(s, '"',  "\\\"", "\'", "\\\\");
  }

  /** Helper to escape JavaScript string as well as regular expression */
  private static String escapeString(String s, char quote,
                          String doublequoteEscape,
                          String singlequoteEscape,
                          String backslashEscape) {
    StringBuilder sb = new StringBuilder(s.length() + 2);
    sb.append(quote);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        case '\\': sb.append(backslashEscape); break;
        case '\"': sb.append(doublequoteEscape); break;
        case '\'': sb.append(singlequoteEscape); break;
        case '>':
          // Unicode-escape the '>' in '-->' and ']]>'
          if (i >= 2
              && ((s.charAt(i - 1) == '-' && s.charAt(i - 2) == '-')
                  || (s.charAt(i - 1) == ']' && s.charAt(i - 2) == ']'))) {
            sb.append("\\u003e");
          } else {
            sb.append(c);
          }
          break;
        case '<':
          // Unicode-escape the '<' in '</script' and '<!--'
          final String END_SCRIPT = "/script";
          final String START_COMMENT = "!--";

          if (s.regionMatches(true, i + 1, END_SCRIPT, 0,
                              END_SCRIPT.length())) {
            sb.append("\\u003c");
          } else if (s.regionMatches(false, i + 1, START_COMMENT, 0,
                                     START_COMMENT.length())) {
            sb.append("\\u003c");
          } else {
            sb.append(c);
          }
          break;
        default:
          // No charsetEncoder provided - pass straight Latin characters
          // through, and escape the rest.  Doing the explicit character
          // check is measurably faster than using the CharsetEncoder.
          if (c > 0x1f && c <= 0x7f) {
            sb.append(c);
          } else {
            // Other characters can be misinterpreted by some JS parsers,
            // or perhaps mangled by proxies along the way,
            // so we play it safe and Unicode escape them.
            appendHexJavaScriptRepresentation(sb, c);
          }
      }
    }
    sb.append(quote);
    return sb.toString();
  }

  /** @see #appendHexJavaScriptRepresentation(int, Appendable) */
  public static void appendHexJavaScriptRepresentation(StringBuilder sb, char c) {
    try {
      appendHexJavaScriptRepresentation(c, sb);
    } catch (IOException ex) {
      // StringBuilder does not throw IOException.
      throw new RuntimeException(ex);
    }
  }

  /**
   * Returns a JavaScript representation of the character in a hex escaped
   * format.
   * @param codePoint The code point to append.
   * @param out The buffer to which the hex representation should be appended.
   */
  private static void appendHexJavaScriptRepresentation(
      int codePoint, Appendable out)
      throws IOException {
    if (Character.isSupplementaryCodePoint(codePoint)) {
      // Handle supplementary Unicode values which are not representable in
      // JavaScript.  We deal with these by escaping them as two 4B sequences
      // so that they will round-trip properly when sent from Java to JavaScript
      // and back.
      char[] surrogates = Character.toChars(codePoint);
      appendHexJavaScriptRepresentation(surrogates[0], out);
      appendHexJavaScriptRepresentation(surrogates[1], out);
      return;
    }
    out.append("\\u")
        .append(HEX_CHARS[(codePoint >>> 12) & 0xf])
        .append(HEX_CHARS[(codePoint >>> 8) & 0xf])
        .append(HEX_CHARS[(codePoint >>> 4) & 0xf])
        .append(HEX_CHARS[codePoint & 0xf]);
  }

  private Util() {}
}
