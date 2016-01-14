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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Chars;
import com.google.javascript.rhino.TokenStream;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * A simple class for generating unique JavaScript variable/property names.
 *
 * <p>This class is not thread safe.
 *
 */
final class DefaultNameGenerator implements NameGenerator {

  /**
   * Represents a char that can be used in renaming as well as how often
   * that char appears in the generated code.
   */
  private static final class CharPriority implements Comparable<CharPriority>{
    final char name;
    int occurrence;

    // This is a tie-breaker when two chars occurrence count is the same.
    // When that happens, the 'natural' order prevails.
    final int order;

    CharPriority(char name, int order) {
      this.name = name;
      this.order = order;
      this.occurrence = 0;
    }

    // @Override removed for GWT compatibility
    public CharPriority clone() {
      CharPriority result = new CharPriority(name, order);
      result.occurrence = occurrence;
      return result;
    }

    @Override
    public int compareTo(CharPriority other) {
      // Start out by putting the element with more occurrence first.
      int result = other.occurrence - this.occurrence;
      if (result != 0) {
        return result;
      }
      // If there is a tie, follow the order of FIRST_CHAR and NONFIRST_CHAR.
      result = this.order - other.order;
      return result;
    }
  }

  // TODO(user): Maybe we don't need a HashMap to look up.
  // I started writing a skip-list like data-structure that would let us
  // have O(1) favors() and O(1) reset() but the code gotten very messy.
  // Lets start with a logical implementation first until performance becomes
  // a problem.
  private Map<Character, CharPriority> priorityLookupMap;

  // It is important that the ordering of FIRST_CHAR is as close to NONFIRT_CHAR
  // as possible. Using the ASCII ordering is not a good idea. The reason
  // is that we cannot use numbers as FIRST_CHAR yet the ACSII value of numbers
  // is very small. If we picked numbers first in NONFIRST_CHAR, we would
  // end up balancing the huffman tree and result is bad compression.
  /** Generate short name with this first character */
  static final char[] FIRST_CHAR =
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ$".toCharArray();

  /** These appear after after the first character */
  static final char[] NONFIRST_CHAR =
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789$"
        .toCharArray();

  private Set<String> reservedNames;
  private String prefix;
  private int nameCount;

  private CharPriority[] firstChars;
  private CharPriority[] nonFirstChars;

  public DefaultNameGenerator() {
    buildPriorityLookupMap();
    Set<String> reservedNames = Sets.newHashSetWithExpectedSize(0);
    reset(reservedNames, "", null);
  }

  /**
   * Creates a DefaultNameGenerator.
   *
   * @param reservedNames set of names that are reserved; generated names will
   *   not include these names. This set is referenced rather than copied,
   *   so changes to the set will be reflected in how names are generated.
   * @param prefix all generated names begin with this prefix.
   * @param reservedCharacters If specified these characters won't be used in
   *   generated names
   */
  public DefaultNameGenerator(Set<String> reservedNames, String prefix,
      @Nullable char[] reservedCharacters) {
    buildPriorityLookupMap();
    reset(reservedNames, prefix, reservedCharacters);
  }

  private DefaultNameGenerator(Set<String> reservedNames, String prefix,
      @Nullable char[] reservedCharacters,
      Map<Character, CharPriority> priorityLookupMap) {
    // Clone the priorityLookupMap to preserve information about how often
    // characters are used.
    this.priorityLookupMap = Maps.newHashMapWithExpectedSize(
        NONFIRST_CHAR.length);
    for (Map.Entry<Character, CharPriority> entry :
      priorityLookupMap.entrySet()) {
      this.priorityLookupMap.put(entry.getKey(), entry.getValue().clone());
    }

    reset(reservedNames, prefix, reservedCharacters);
  }

  private void buildPriorityLookupMap() {
    priorityLookupMap = Maps.newHashMapWithExpectedSize(NONFIRST_CHAR.length);
    int order = 0;
    for (char c : NONFIRST_CHAR) {
      priorityLookupMap.put(c, new CharPriority(c, order));
      order++;
    }
  }

  /**
   * Note that the history of what characters are most used in the program
   * (set through calls to 'favor') is not deleted. Upon 'reset', that history
   * is taken into account for the names that will be generated later: it
   * re-calculates how characters are prioritized based on how often the they
   * appear in the final output.
   */
  @Override
  public void reset(
      Set<String> reservedNames,
      String prefix,
      @Nullable char[] reservedCharacters) {

    this.reservedNames = reservedNames;
    this.prefix = prefix;
    this.nameCount = 0;

    // build the character arrays to use
    this.firstChars = reserveCharacters(FIRST_CHAR, reservedCharacters);
    this.nonFirstChars = reserveCharacters(NONFIRST_CHAR, reservedCharacters);
    Arrays.sort(firstChars);
    Arrays.sort(nonFirstChars);

    checkPrefix(prefix);
  }

  @Override
  public NameGenerator clone(
      Set<String> reservedNames,
      String prefix,
      @Nullable char[] reservedCharacters) {
    return new DefaultNameGenerator(reservedNames, prefix, reservedCharacters,
        priorityLookupMap);
  }

  /**
   * Increase the prioritization of all the chars in a String. This information
   * is not used until {@link #reset} is called. A compiler would be
   * able to generate names while changing the prioritization of the name
   * generator for the <b>next</b> pass.
   */
  void favors(CharSequence sequence) {
    for (int i = 0; i < sequence.length(); i++) {
      CharPriority c = priorityLookupMap.get(sequence.charAt(i));
      if (c != null) {
        c.occurrence++;
      }
    }
  }

  /**
   * Provides the array of available characters based on the specified arrays.
   * @param chars The list of characters that are legal
   * @param reservedCharacters The characters that should not be used
   * @return An array of characters to use. Will return the chars array if
   *    reservedCharacters is null or empty, otherwise creates a new array.
   */
  CharPriority[] reserveCharacters(char[] chars, char[] reservedCharacters) {
    if (reservedCharacters == null || reservedCharacters.length == 0) {
      CharPriority[] result = new CharPriority[chars.length];
      for (int i = 0; i < chars.length; i++) {
        result[i] = priorityLookupMap.get(chars[i]);
      }
      return result;
    }
    Set<Character> charSet = new LinkedHashSet<>(Chars.asList(chars));
    for (char reservedCharacter : reservedCharacters) {
      charSet.remove(reservedCharacter);
    }

    CharPriority[] result = new CharPriority[charSet.size()];
    int index = 0;
    for (char c : charSet) {
      result[index++] = priorityLookupMap.get(c);
    }
    return result;
  }

  /** Validates a name prefix. */
  private void checkPrefix(String prefix) {
    if (prefix.length() > 0) {
      // Make sure that prefix starts with a legal character.
      if (!contains(firstChars, prefix.charAt(0))) {
        char[] chars = new char[firstChars.length];
        for (int i = 0; i < chars.length; i++) {
          chars[i] = firstChars[i].name;
        }
        throw new IllegalArgumentException(
            "prefix must start with one of: " + Arrays.toString(chars));
      }
      for (int pos = 1; pos < prefix.length(); ++pos) {
        char[] chars = new char[nonFirstChars.length];
        for (int i = 0; i < chars.length; i++) {
          chars[i] = nonFirstChars[i].name;
        }
        if (!contains(nonFirstChars, prefix.charAt(pos))) {
          throw new IllegalArgumentException(
              "prefix has invalid characters, must be one of: "
              + Arrays.toString(chars));
        }
      }
    }
  }

  private static boolean contains(CharPriority[] arr, char c) {
    for (int i = 0; i < arr.length; i++) {
      if (arr[i].name == c) {
        return true;
      }
    }
    return false;
  }

  /**
   * Generates the next short name.
   */
  @Override
  public String generateNextName() {
    while (true) {
      String name = prefix;

      int i = nameCount;

      if (name.isEmpty()) {
        int pos = i % firstChars.length;
        name += firstChars[pos].name;
        i /= firstChars.length;
      }

      while (i > 0) {
        i--;
        int pos = i % nonFirstChars.length;
        name += nonFirstChars[pos].name;
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
