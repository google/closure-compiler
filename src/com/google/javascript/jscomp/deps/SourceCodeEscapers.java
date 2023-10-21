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

import com.google.common.escape.Escaper;
import java.io.IOException;

/**
 * A factory for Escaper instances used to escape strings for safe use in various common programming
 * languages.
 *
 * <p>NOTE: This class is cribbed from the Guava libraries SourceCodeEscapers which is not part of
 * the current Guava release. https://github.com/google/guava/issues/1620
 */
public final class SourceCodeEscapers {

  private SourceCodeEscapers() {}

  // From: http://en.wikipedia.org/wiki/ASCII#ASCII_printable_characters
  private static final char PRINTABLE_ASCII_MIN = 0x20; // ' '
  private static final char PRINTABLE_ASCII_MAX = 0x7E; // '~'

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  private static final JavaScriptEscaper JAVASCRIPT_ESCAPER = new JavaScriptEscaper();

  /**
   * Returns an {@link Escaper} instance that replaces non-ASCII characters in a string with their
   * equivalent Javascript UTF-16 escape sequences "{@literal \}unnnn", "\xnn" or special
   * replacement sequences "\b", "\t", "\n", "\f", "\r" or "\\".
   *
   * <p><b>Warning:</b> This escaper is <b>not</b> suitable for JSON. JSON users may wish to use <a
   * href="http://code.google.com/p/google-gson/">GSON</a> or other high-level APIs when possible.
   */
  public static Escaper javascriptEscaper() {
    return JAVASCRIPT_ESCAPER;
  }

  /**
   * Uses {@link #javascriptEscaper()} to append the escaped transformation of {@code c} to {@code
   * to}. Using this method can be more memory efficient than calling {@link
   * Escaper#escape(String)}.
   */
  public static void appendWithJavascriptEscaper(CharSequence c, Appendable to) throws IOException {
    JAVASCRIPT_ESCAPER.appendTo(c, to);
  }

  /**
   * An Escaper for javascript strings. Turns all non-ASCII characters into ASCII javascript escape
   * sequences.
   */
  private static final class JavaScriptEscaper extends Escaper {
    private static final char[][] REPLACEMENT_CHARS;

    static {
      REPLACEMENT_CHARS = new char[0x100][];
      for (char i = 0; i < PRINTABLE_ASCII_MIN; i++) {
        char c = i;
        char[] r = new char[4];
        r[3] = HEX_DIGITS[c & 0xF];
        c = (char) (c >>> 4);
        r[2] = HEX_DIGITS[c & 0xF];
        r[1] = 'x';
        r[0] = '\\';
        REPLACEMENT_CHARS[i] = r;
      }
      for (char i = PRINTABLE_ASCII_MAX + 1; i < 0x100; i++) {
        char c = i;
        char[] r = new char[4];
        r[3] = HEX_DIGITS[c & 0xF];
        c = (char) (c >>> 4);
        r[2] = HEX_DIGITS[c & 0xF];
        r[1] = 'x';
        r[0] = '\\';
        REPLACEMENT_CHARS[i] = r;
      }
      REPLACEMENT_CHARS['\''] = "\\x27".toCharArray();
      REPLACEMENT_CHARS['"'] = "\\x22".toCharArray();
      REPLACEMENT_CHARS['<'] = "\\x3c".toCharArray();
      REPLACEMENT_CHARS['='] = "\\x3d".toCharArray();
      REPLACEMENT_CHARS['>'] = "\\x3e".toCharArray();
      REPLACEMENT_CHARS['&'] = "\\x26".toCharArray();
      REPLACEMENT_CHARS['\b'] = "\\b".toCharArray();
      REPLACEMENT_CHARS['\t'] = "\\t".toCharArray();
      REPLACEMENT_CHARS['\n'] = "\\n".toCharArray();
      REPLACEMENT_CHARS['\f'] = "\\f".toCharArray();
      REPLACEMENT_CHARS['\r'] = "\\r".toCharArray();
      REPLACEMENT_CHARS['\\'] = "\\\\".toCharArray();
    }

    void appendTo(CharSequence cs, Appendable to) throws IOException {
      int last = 0;
      int length = cs.length();
      for (int i = 0; i < length; i++) {
        char c = cs.charAt(i);
        char[] replacement;
        if (c < 0x100) {
          replacement = REPLACEMENT_CHARS[c];
          if (replacement == null) {
            continue;
          }
        } else {
          replacement = asUnicodeHexEscape(c);
        }
        if (last < i) {
          to.append(cs, last, i);
        }
        for (char r : replacement) {
          to.append(r);
        }
        last = i + 1;
      }
      if (last < length) {
        to.append(cs, last, length);
      }
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

    @Override
    public String escape(String string) {
      StringBuilder sb = new StringBuilder();
      try {
        this.appendTo(string, sb);
      } catch (IOException e) {
        throw new IllegalStateException(
            "This should never throw - StringBuilder.append doesn't actually throw IOException", e);
      }
      return sb.toString();
    }
  }
}
