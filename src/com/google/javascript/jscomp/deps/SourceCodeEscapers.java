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
package com.google.javascript.jscomp.deps;

import com.google.common.escape.ArrayBasedCharEscaper;
import com.google.common.escape.Escaper;

import java.util.HashMap;
import java.util.Map;

/**
 * A factory for Escaper instances used to escape strings for safe use in
 * various common programming languages.
 *
 * NOTE: This class is cribbed from the Guava libraries SourceCodeEscapers which
 * is not part of the current Guava release.
 */
public final class SourceCodeEscapers {
  private SourceCodeEscapers() {}

  // From: http://en.wikipedia.org/wiki/ASCII#ASCII_printable_characters
  private static final char PRINTABLE_ASCII_MIN = 0x20;  // ' '
  private static final char PRINTABLE_ASCII_MAX = 0x7E;  // '~'

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  /**
   * Returns an {@link Escaper} instance that replaces non-ASCII characters
   * in a string with their equivalent Javascript UTF-16 escape sequences
   * "{@literal \}unnnn", "\xnn" or special replacement sequences "\b", "\t",
   * "\n", "\f", "\r" or "\\".
   *
   * <p><b>Warning:</b> This escaper is <b>not</b> suitable for JSON. JSON users
   * may wish to use <a href="http://code.google.com/p/google-gson/">GSON</a> or
   * other high-level APIs when possible.
   */
  public static Escaper javascriptEscaper() {
    return JAVASCRIPT_ESCAPER;
  }

  /**
   * An Escaper for javascript strings. Turns all non-ASCII characters into
   * ASCII javascript escape sequences.
   */
  private static final Escaper JAVASCRIPT_ESCAPER;
  static {
    Map<Character, String> jsMap = new HashMap<>();
    jsMap.put('\'', "\\x27");
    jsMap.put('"',  "\\x22");
    jsMap.put('<',  "\\x3c");
    jsMap.put('=',  "\\x3d");
    jsMap.put('>',  "\\x3e");
    jsMap.put('&',  "\\x26");
    jsMap.put('\b', "\\b");
    jsMap.put('\t', "\\t");
    jsMap.put('\n', "\\n");
    jsMap.put('\f', "\\f");
    jsMap.put('\r', "\\r");
    jsMap.put('\\', "\\\\");
    JAVASCRIPT_ESCAPER = new ArrayBasedCharEscaper(
        jsMap, PRINTABLE_ASCII_MIN, PRINTABLE_ASCII_MAX) {
          @Override
          protected char[] escapeUnsafe(char c) {
            // Do two digit hex escape for value less than 0x100.
            if (c < 0x100) {
              char[] r = new char[4];
              r[3] = HEX_DIGITS[c & 0xF];
              c = (char) (c >>> 4);
              r[2] = HEX_DIGITS[c & 0xF];
              r[1] = 'x';
              r[0] = '\\';
              return r;
            }
            return asUnicodeHexEscape(c);
          }
    };
  }

  // Helper for common case of escaping a single char.
  private static char[] asUnicodeHexEscape(char c) {
    // Equivalent to String.format("\\u%04x", (int) c);
    char[] r = new char[6];
    r[0] = '\\';
    r[1] = 'u';
    r[5] = HEX_DIGITS[c & 0xF];
    c = (char) (c >>> 4);
    r[4] = HEX_DIGITS[c & 0xF];
    c = (char) (c >>> 4);
    r[3] = HEX_DIGITS[c & 0xF];
    c = (char) (c >>> 4);
    r[2] = HEX_DIGITS[c & 0xF];
    return r;
  }
}
