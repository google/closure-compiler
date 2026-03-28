/*
 * Copyright 2026 The Closure Compiler Authors.
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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;

/**
 * A JSON lexer optimized for large string extraction. By using String.substring() on the source
 * contents, it avoids the massive GC churn caused by JsonReader's internal StringBuilder resizing
 * when parsing large Base64 VLQ mapping strings.
 */
final class SourceMapJsonLexer {
  final String json;
  int pos = 0;
  final int length;

  SourceMapJsonLexer(String json) {
    this.json = json;
    this.length = json.length();
  }

  void skipWhitespace() {
    while (pos < length) {
      char c = json.charAt(pos);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        pos++;
      } else {
        break;
      }
    }
  }

  boolean hasNext() {
    skipWhitespace();
    if (pos >= length) {
      return false;
    }
    char c = json.charAt(pos);
    return c != ']' && c != '}';
  }

  void beginObject() throws SourceMapParseException {
    skipWhitespace();
    if (pos < length && json.charAt(pos) == '{') {
      pos++;
    } else {
      throw new SourceMapParseException("Expected '{'");
    }
  }

  void endObject() throws SourceMapParseException {
    skipWhitespace();
    if (pos < length && json.charAt(pos) == '}') {
      pos++;
    } else {
      throw new SourceMapParseException("Expected '}'");
    }
  }

  void beginArray() throws SourceMapParseException {
    skipWhitespace();
    if (pos < length && json.charAt(pos) == '[') {
      pos++;
    } else {
      throw new SourceMapParseException("Expected '['");
    }
  }

  void endArray() throws SourceMapParseException {
    skipWhitespace();
    if (pos < length && json.charAt(pos) == ']') {
      pos++;
    } else {
      throw new SourceMapParseException("Expected ']'");
    }
  }

  @CanIgnoreReturnValue
  String nextName() throws SourceMapParseException {
    String name = nextString();
    skipWhitespace();
    if (pos < length && json.charAt(pos) == ':') {
      pos++;
    } else {
      throw new SourceMapParseException("Expected ':'");
    }
    return name;
  }

  @Nullable String nextStringOrNull() throws SourceMapParseException {
    skipWhitespace();
    if (pos < length && json.startsWith("null", pos)) {
      pos += 4;
      return null;
    }
    return nextString();
  }

  @CanIgnoreReturnValue
  String nextString() throws SourceMapParseException {
    skipWhitespace();
    if (pos >= length || json.charAt(pos) != '"') {
      throw new SourceMapParseException("Expected string");
    }
    pos++; // skip quote
    int start = pos;
    boolean hasEscape = false;
    while (pos < length) {
      char c = json.charAt(pos);
      if (c == '"') {
        break;
      }
      if (c == '\\') {
        hasEscape = true;
        pos++;
      }
      pos++;
    }
    if (pos >= length) {
      throw new SourceMapParseException("Unterminated string");
    }
    String result;
    if (!hasEscape) {
      result = json.substring(start, pos);
    } else {
      result = unescape(json, start, pos);
    }
    pos++; // skip quote
    return result;
  }

  int nextInt() throws SourceMapParseException {
    skipWhitespace();
    int start = pos;
    if (pos < length && json.charAt(pos) == '-') {
      pos++;
    }
    while (pos < length && json.charAt(pos) >= '0' && json.charAt(pos) <= '9') {
      pos++;
    }
    if (start == pos) {
      throw new SourceMapParseException("Expected integer");
    }
    try {
      return Integer.parseInt(json, start, pos, 10);
    } catch (NumberFormatException e) {
      throw new SourceMapParseException("Invalid integer: " + json.substring(start, pos));
    }
  }

  String nextRawValue() throws SourceMapParseException {
    skipWhitespace();
    int start = pos;
    skipValue();
    return json.substring(start, pos);
  }

  void skipValue() throws SourceMapParseException {
    skipWhitespace();
    if (pos >= length) {
      throw new SourceMapParseException("Unexpected end of input");
    }
    char c = json.charAt(pos);
    if (c == '{') {
      pos++;
      while (hasNext()) {
        nextName();
        skipValue();
        checkComma();
      }
      endObject();
    } else if (c == '[') {
      pos++;
      while (hasNext()) {
        skipValue();
        checkComma();
      }
      endArray();
    } else if (c == '"') {
      nextString();
    } else if (c == 't' && json.startsWith("true", pos)) {
      pos += 4;
    } else if (c == 'f' && json.startsWith("false", pos)) {
      pos += 5;
    } else if (c == 'n' && json.startsWith("null", pos)) {
      pos += 4;
    } else if (c == '-' || (c >= '0' && c <= '9')) {
      if (c == '-') {
        pos++;
      }
      while (pos < length
          && ((json.charAt(pos) >= '0' && json.charAt(pos) <= '9')
              || json.charAt(pos) == '.'
              || json.charAt(pos) == 'e'
              || json.charAt(pos) == 'E'
              || json.charAt(pos) == '+'
              || json.charAt(pos) == '-')) {
        pos++;
      }
    } else {
      throw new SourceMapParseException("Unexpected character: " + c);
    }
  }

  void checkComma() throws SourceMapParseException {
    boolean hasComma = tryConsumeComma();
    if (hasComma && !hasNext()) {
      throw new SourceMapParseException("Unexpected trailing comma");
    }
    if (!hasComma && hasNext()) {
      throw new SourceMapParseException("Expected ','");
    }
  }

  @CanIgnoreReturnValue
  boolean tryConsumeComma() {
    skipWhitespace();
    if (pos < length && json.charAt(pos) == ',') {
      pos++;
      return true;
    }
    return false;
  }

  private static String unescape(String s, int start, int end) throws SourceMapParseException {
    StringBuilder sb = new StringBuilder(end - start);
    for (int i = start; i < end; i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < end) {
        i++;
        char next = s.charAt(i);
        switch (next) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (i + 4 >= end) {
              throw new SourceMapParseException("Invalid unicode escape sequence");
            }
            try {
              sb.append((char) Integer.parseInt(s, i + 1, i + 5, 16));
              i += 4;
            } catch (NumberFormatException e) {
              throw new SourceMapParseException(
                  "Invalid unicode escape: " + s.substring(i + 1, i + 5));
            }
          }
          default -> sb.append(next);
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
