/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Class for parsing the line maps in SourceMap v2.
 *
 * @author johnlenz@google.com (John Lenz)
 * @author jschorr@google.com (Joseph Schorr)
 */
class SourceMapLineDecoder {

  /**
   * Decodes a line in a character map into a list of mapping IDs.
   */
  static List<Integer> decodeLine(String lineSource) {
    return decodeLine(new StringParser(lineSource));
  }

  private SourceMapLineDecoder() {}

  static LineEntry decodeLineEntry(String in, int lastId) {
    return decodeLineEntry(new StringParser(in), lastId);
  }

  private static LineEntry decodeLineEntry(StringParser reader, int lastId) {
    int repDigits = 0;

    // Determine the number of digits used for the repetition count.
    // Each "!" indicates another base64 digit.
    for (char peek = reader.peek(); peek == '!'; peek = reader.peek()) {
      repDigits++;
      reader.next(); // consume the "!"
    }

    int idDigits = 0;
    int reps = 0;
    if (repDigits == 0) {
      // No repetition digit escapes, so the next character represents the
      // number of digits in the id (bottom 2 bits) and the number of
      // repetitions (top 4 digits).
      char digit = reader.next();
      int value = addBase64Digit(digit, 0);
      reps = (value >> 2);
      idDigits = (value & 3);
    } else {
      char digit = reader.next();
      idDigits = addBase64Digit(digit, 0);

      int value = 0;
      for (int i = 0; i < repDigits; i++) {
        digit = reader.next();
        value = addBase64Digit(digit, value);
      }
      reps = value;
    }

    // Adjust for 1 offset encoding.
    reps += 1;
    idDigits += 1;

    // Decode the id token.
    int value = 0;
    for (int i = 0; i < idDigits; i++) {
      char digit = reader.next();
      value = addBase64Digit(digit, value);
    }
    int mappingId = getIdFromRelativeId(value, idDigits, lastId);
    return new LineEntry(mappingId, reps);
  }

  private static List<Integer> decodeLine(StringParser reader) {
    List<Integer> result = Lists.newArrayListWithCapacity(512);
    int lastId = 0;
    while (reader.hasNext()) {
      LineEntry entry = decodeLineEntry(reader, lastId);
      lastId = entry.id;

      for (int i=0; i < entry.reps; i++) {
        result.add(entry.id);
      }
    }

    return result;
  }

  /**
   * Build base64 number a digit at a time, most significant digit first.
   */
  private static int addBase64Digit(char digit, int previousValue) {
    return (previousValue * 64) + Base64.fromBase64(digit);
  }

  /**
   * @return the id from the relative id.
   */
  static int getIdFromRelativeId(int rawId, int digits, int lastId) {
    // The value range depends on the number of digits
    int base = 1 << (digits * 6);
    return ((rawId >= base/2) ? rawId - base : rawId) + lastId;
  }

  /**
   * Simple class for tracking a single entry in a line map.
   */
  static class LineEntry {
    final int id;
    final int reps;
    public LineEntry(int id, int reps) {
      this.id = id;
      this.reps = reps;
    }
  }

  /**
   * A simple class for maintaining the current location
   * in the input.
   */
  static class StringParser {
    final String content;
    int current = 0;

    StringParser(String content) {
      this.content = content;
    }

    char next() {
      return content.charAt(current++);
    }

    char peek() {
      return content.charAt(current);
    }

    boolean hasNext() {
      return current < content.length() -1;
    }
  }
}
