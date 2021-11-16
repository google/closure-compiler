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

import com.google.common.collect.ImmutableMap;
import com.google.common.escape.ArrayBasedCharEscaper;
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
  private static final class JavaScriptEscaper extends ArrayBasedCharEscaper {
    private static final ImmutableMap<Character, String> JS_MAP =
        ImmutableMap.<Character, String>builder()
            .put('\'', "\\x27")
            .put('"', "\\x22")
            .put('<', "\\x3c")
            .put('=', "\\x3d")
            .put('>', "\\x3e")
            .put('&', "\\x26")
            .put('\b', "\\b")
            .put('\t', "\\t")
            .put('\n', "\\n")
            .put('\f', "\\f")
            .put('\r', "\\r")
            .put('\\', "\\\\")
            .buildOrThrow();

    JavaScriptEscaper() {
      super(JS_MAP, PRINTABLE_ASCII_MIN, PRINTABLE_ASCII_MAX);
    }

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

    /**
     * Append the escaped transformation of {@code c} to {@code to}.
     *
     * <p>{@link Escaper#escape(String)} has to build and return a new string, which is redundant if
     * we're just going to pass that string on to an {@link Appendable} anyway. This method just
     * appends the possibly-transformed characters one at a time to avoid the double memory
     * allocation.
     */
    void appendTo(CharSequence c, Appendable to) throws IOException {
      int l = c.length();
      for (int i = 0; i < l; i++) {
        char unescaped = c.charAt(i);
        // #escape(char) is protected so this must be done in a subclass.
        char[] escaped = escape(unescaped);
        if (escaped != null) {
          for (char value : escaped) {
            to.append(value);
          }
        } else {
          to.append(unescaped);
        }
      }
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
}
