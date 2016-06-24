/*
 * Copyright 2016 The Closure Compiler Authors.
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
 * A simple utility for shortening identifiers in a stable way. Generates
 * short substitution strings deterministically, using a compact
 * (1 to 6 characters in length) repesentation of a 32-bit hash of the key.
 * The string is suitable to be used as a JavaScript or CSS identifier.
 * Collisions are possible but unlikely, depending on the underlying hash algorithm used.
 *
 * This substitution scheme uses case-sensitive names for maximum
 * compression. Digits are also allowed in all but the first character of a
 * class name. There are a few characters allowed by the CSS grammar that we
 * choose not to use (e.g. the underscore and hyphen), to keep names simple.
 *
 * Xid should maintain as minimal dependencies as possible to ease its
 * integration with other tools, such as server side HTML generators.
 */
public class Xid {

  /** Possible first chars in an identifier */
  private static final String START_CHARS =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

  /** Possible non-first chars in an identifier */
  private static final String CHARS = START_CHARS + "0123456789";

  private static final int START_RADIX = START_CHARS.length();
  private static final int RADIX = CHARS.length();

  private final HashFunction hasher;

  /**
   * Strategy for selecting the underlying hash code function to be used by Xid.
   */
  public interface HashFunction {
    int hashCode(String value);
  }

  private static final HashFunction DEFAULT = new HashFunction() {
    @Override public int hashCode(String value) {
      return value.hashCode();
    }
  };

  public Xid() {
    this.hasher = DEFAULT;
  }

  public Xid(HashFunction hasher) {
    this.hasher = hasher;
  }

  /**
   * Gets the string that should be substituted for {@code key}. The same value will be
   * consistently returned for any particular {@code key}.
   *
   * @param key  the text to be replaced (never null)
   * @return the value to substitute for {@code key}
   */
  public String get(String key) {
    return toString(getAsInt(key));
  }

  /**
   * Returns the underlying integer representation of the given key.
   */
  public int getAsInt(String key) {
    return this.hasher.hashCode(key);
  }

  /**
   * Converts a 32-bit integer to a unique short string whose first character
   * is in {@link #START_CHARS} and whose subsequent characters, if any, are
   * in {@link #CHARS}. The result is 1-6 characters in length.
   */
  static String toString(int i) {
    char[] buf = new char[6];
    int len = 0;

    long l = i - (long) Integer.MIN_VALUE;
    buf[len++] = START_CHARS.charAt((int) (l % START_RADIX));
    i = (int) (l / START_RADIX);

    while (i > 0) {
      buf[len++] = CHARS.charAt(i % RADIX);
      i /= RADIX;
    }

    return new String(buf, 0, len);
  }
}
