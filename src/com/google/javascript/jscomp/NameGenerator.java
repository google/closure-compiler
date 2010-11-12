/*
 * Copyright 2005 The Closure Compiler Authors.
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

import com.google.javascript.rhino.TokenStream;
import javax.annotation.Nullable;
import com.google.common.collect.Sets;
import com.google.common.primitives.Chars;

import java.util.*;

/**
 * A simple class for generating unique JavaScript variable/property names.
 *
 * <p>This class is not thread safe.
 *
 */
final class NameGenerator {
  /** Generate short name with this first character */
  static final char[] FIRST_CHAR =
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ$".toCharArray();

  /** These appear after after the first character */
  static final char[] NONFIRST_CHAR =
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789$"
        .toCharArray();

  private final Set<String> reservedNames;
  private final String prefix;
  private int nameCount;

  private final char[] firstChars;
  private final char[] nonFirstChars;

  /**
   * Creates a NameGenerator.
   *
   * @param reservedNames set of names that are reserved; generated names will
   *   not include these names. This set is referenced rather than copied,
   *   so changes to the set will be reflected in how names are generated.
   * @param prefix all generated names begin with this prefix.
   * @param reservedCharacters If specified these characters won't be used in
   *   generated names
   */
  NameGenerator(Set<String> reservedNames, String prefix,
      @Nullable char[] reservedCharacters) {
    this.reservedNames = reservedNames;
    this.prefix = prefix;

    // build the character arrays to use
    this.firstChars = reserveCharacters(FIRST_CHAR, reservedCharacters);
    this.nonFirstChars = reserveCharacters(NONFIRST_CHAR, reservedCharacters);

    checkPrefix(prefix);
  }

  /**
   * Provides the array of available characters based on the specified arrays.
   * @param chars The list of characters that are legal
   * @param reservedCharacters The characters that should not be used
   * @return An array of characters to use. Will return the chars array if
   *    reservedCharacters is null or empty, otherwise creates a new array.
   */
  static char[] reserveCharacters(char[] chars, char[] reservedCharacters) {
    if (reservedCharacters == null || reservedCharacters.length == 0) {
      return chars;
    }
    Set<Character> charSet = Sets.newLinkedHashSet(Chars.asList(chars));
    for (char reservedCharacter : reservedCharacters) {
      charSet.remove(reservedCharacter);
    }
    return Chars.toArray(charSet);
  }

  /** Validates a name prefix. */
  private void checkPrefix(String prefix) {
    if (prefix.length() > 0) {
      // Make sure that prefix starts with a legal character.
      if (!contains(firstChars, prefix.charAt(0))) {
        throw new IllegalArgumentException("prefix must start with one of: " +
                                           Arrays.toString(firstChars));
      }
      for (int pos = 1; pos < prefix.length(); ++pos) {
        if (!contains(nonFirstChars, prefix.charAt(pos))) {
          throw new IllegalArgumentException("prefix has invalid characters, " +
                                             "must be one of: " +
                                             Arrays.toString(nonFirstChars));
        }
      }
    }
  }

  private boolean contains(char[] arr, char c) {
    for (int i = 0; i < arr.length; i++) {
      if (arr[i] == c) {
        return true;
      }
    }
    return false;
  }

  /**
   * Generates the next short name.
   */
  String generateNextName() {
    while (true) {
      String name = prefix;

      int i = nameCount;

      if (name.isEmpty()) {
        int pos = i % firstChars.length;
        name += firstChars[pos];
        i /= firstChars.length;
      }

      while (i > 0) {
        i--;
        int pos = i % nonFirstChars.length;
        name += nonFirstChars[pos];
        i /= nonFirstChars.length;
      }

      nameCount++;

      // Make sure it's not a JS keyword or reserved name.
      if (TokenStream.isKeyword(name) || reservedNames.contains(name)) {
        continue;
      }

      return name;
    }
  }
}
