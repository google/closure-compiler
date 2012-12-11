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

import java.util.Arrays;

/**
 * A utility class for working with Base64 values.
 * @author johnlenz@google.com (John Lenz)
 */
public final class Base64 {

  // This is a utility class
  private Base64() {}

  /**
   *  A map used to convert integer values in the range 0-63 to their base64
   *  values.
   */
  private static final String BASE64_MAP =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
      "abcdefghijklmnopqrstuvwxyz" +
      "0123456789+/";

  /**
   * A map used to convert base64 character into integer values.
   */
  private static final int[] BASE64_DECODE_MAP = new int[256];
  static {
      Arrays.fill(BASE64_DECODE_MAP, -1);
      for (int i = 0; i < BASE64_MAP.length(); i++) {
        BASE64_DECODE_MAP[BASE64_MAP.charAt(i)] = i;
      }
  }

  /**
   * @param value A value in the range of 0-63.
   * @return a base64 digit.
   */
  public static char toBase64(int value) {
    assert (value <= 63 && value >= 0) : "value out of range:" + value;
    return BASE64_MAP.charAt(value);
  }

  /**
   * @param c A base64 digit.
   * @return A value in the range of 0-63.
   */
  public static int fromBase64(char c) {
    int result = BASE64_DECODE_MAP[c];
    assert (result != -1) : "invalid char";
    return BASE64_DECODE_MAP[c];
  }

  /**
   * @param value an integer to base64 encode.
   * @return the six digit long base64 encoded value of the integer.
   */
  public static String base64EncodeInt(int value) {
    char[] c = new char[6];
    for (int i = 0; i < 5; i++) {
      c[i] = Base64.toBase64((value >> (26 - i * 6)) & 0x3f);
    }
    c[5] = Base64.toBase64((value << 4) & 0x3f);
    return new String(c);
  }
}
