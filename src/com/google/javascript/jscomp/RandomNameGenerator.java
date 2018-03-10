/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Chars;
import com.google.javascript.rhino.TokenStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A class for generating unique, randomized JavaScript variable/property
 * names.
 *
 * <p>Unlike NameGenerator, names do not follow a predictable sequence such as
 *   a, b, ... z, aa, ab, ..., az, ba, ...
 * but instead they are random, based on an external random seed. We do
 * partially compromise for efficiency in that
 * <ul>
 * <li>Generated names will have the same length as they would with
 * NameGenerator
 * <li>We don't use a completely different alphabet for each name prefix, but
 * instead choose among a few with a predictable formula.
 * </ul>
 *
 * <p>More precisely:
 * <ul>
 * <li>We compute a random shuffle of the alphabet for "first characters", and
 * a small number of random shuffles of the alphabet for "non-first
 * characters". Then we do a typical number-to-text conversion of a name's
 * "index", where the alphabet for digits is not just 0 to 9. The least
 * significant digit comes first.
 * <li>We represent each digit using an appropriate alphabet. If it's not the
 * first character of the name (i.e. not the least significant one, or there
 * is a constant prefix) then we have several appropriate alphabets to choose
 * from; we choose one based a hash of the previous digits of this name.
 * </ul>
 *
 * <p>This class is not thread safe.
 */
@GwtIncompatible(
    "java.util.Collections.shuffle, "
    + "com.google.common.hash.Hasher, "
    + "com.google.common.hash.Hashing")
public final class RandomNameGenerator implements NameGenerator {

  /** Generate random names with this first character. */
  static final ImmutableSet<Character> FIRST_CHAR = asSet(DefaultNameGenerator.FIRST_CHAR);

  /** These appear after after the first character */
  static final ImmutableSet<Character> NONFIRST_CHAR = asSet(DefaultNameGenerator.NONFIRST_CHAR);

  /** The possible first characters, after reserved characters are removed */
  private ImmutableSet<Character> firstChars;

  /** Possible non-first characters, after reserved characters are removed */
  private ImmutableSet<Character> nonFirstChars;

  /** Source of randomness */
  private final Random random;

  /** List of reserved names; these are not returned by generateNextName */
  private ImmutableSet<String> reservedNames;

  /** Prefix added to all generated names */
  private String prefix;

  /** How many names have we issued so far (includes names that cannot be used
   * because they are reserved through 'reservedNames' or JavaScript
   * keywords) */
  private int nameCount;

  /** How many shuffles of nonFirstChars to generate */
  private static final int NUM_SHUFFLES = 16;

  /** Randomly-shuffled version of firstChars */
  private String shuffledFirst;
  /** Randomly-shuffled versions of nonFirstChars (there are NUM_SHUFFLES of them) */
  private ImmutableList<String> shuffledNonFirst;

  public RandomNameGenerator(Random random) {
    this.random = random;
    reset(ImmutableSet.of(), "", null);
  }

  RandomNameGenerator(
      Set<String> reservedNames,
      String prefix,
      @Nullable char[] reservedCharacters,
      Random random) {
    this.random = random;
    reset(reservedNames, prefix, reservedCharacters);
  }

  /**
   * Creates a RandomNameGenerator.
   *
   * @param reservedNames set of names that are reserved; generated names will not include these
   *     names. This set is referenced rather than copied, so changes to the set will be reflected
   *     in how names are generated
   * @param prefix all generated names begin with this prefix (a name consisting of only this
   *     prefix, with no suffix, will not be generated)
   * @param reservedFirstCharacters if specified these characters won't be used in generated names
   *     for the first character
   * @param reservedNonFirstCharacters if specified these characters won't be used in generated
   *     names for characters after the first
   * @param random source of randomness when generating random names
   */
  RandomNameGenerator(
      Set<String> reservedNames,
      String prefix,
      @Nullable char[] reservedFirstCharacters,
      @Nullable char[] reservedNonFirstCharacters,
      Random random) {
    this.random = random;
    reset(reservedNames, prefix, reservedFirstCharacters, reservedNonFirstCharacters);
  }

  @Override
  public void reset(
      Set<String> reservedNames,
      String prefix,
      @Nullable char[] reservedCharacters) {
    reset(reservedNames, prefix, reservedCharacters, reservedCharacters);
  }

  @Override
  public void reset(
      Set<String> reservedNames,
      String prefix,
      @Nullable char[] reservedFirstCharacters,
      @Nullable char[] reservedNonFirstCharacters) {
    this.reservedNames = ImmutableSet.copyOf(reservedNames);
    this.prefix = prefix;
    nameCount = 0;

    // Build the character arrays to use
    firstChars = Sets.difference(FIRST_CHAR, asSet(reservedFirstCharacters)).immutableCopy();
    nonFirstChars =
        Sets.difference(NONFIRST_CHAR, asSet(reservedNonFirstCharacters)).immutableCopy();

    checkPrefix(prefix);
    shuffleAlphabets();
  }

  @Override
  public NameGenerator clone(
      Set<String> reservedNames,
      String prefix,
      @Nullable char[] reservedCharacters) {
    return new RandomNameGenerator(
        reservedNames, prefix, reservedCharacters, random);
  }

  private static ImmutableSet<Character> asSet(@Nullable char[] chars) {
    return chars == null ? ImmutableSet.of() : ImmutableSet.copyOf(Chars.asList(chars));
  }

  /**
   * Validates a name prefix.
   */
  private void checkPrefix(String prefix) {
    if (prefix.length() > 0) {
      // Make sure that prefix starts with a legal character.
      if (!firstChars.contains(prefix.charAt(0))) {
        throw new IllegalArgumentException(
            "prefix must start with one of: "
            + Joiner.on(", ").join(firstChars));
      }
      for (int pos = 1; pos < prefix.length(); ++pos) {
        if (!nonFirstChars.contains(prefix.charAt(pos))) {
          throw new IllegalArgumentException(
              "prefix has invalid characters, must be one of: "
              + Joiner.on(", ").join(nonFirstChars));
        }
      }
    }
  }

  private static String shuffleAndCopyAlphabet(Set<Character> input, Random random) {
    List<Character> shuffled = new ArrayList<>(input);
    Collections.shuffle(shuffled, random);
    return new String(Chars.toArray(shuffled));
  }

  /** Generates random shuffles of the alphabets. */
  private void shuffleAlphabets() {
    shuffledFirst = shuffleAndCopyAlphabet(firstChars, random);
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (int i = 0; i < NUM_SHUFFLES; ++i) {
      builder.add(shuffleAndCopyAlphabet(nonFirstChars, random));
    }
    shuffledNonFirst = builder.build();
  }

  /**
   * Computes the length (in digits) for a name suffix.
   */
  private int getNameLength(int position, int nameIdx) {
    int length = 0;
    nameIdx++;
    do {
      nameIdx--;
      int alphabetSize = position == 0
          ? firstChars.size() : nonFirstChars.size();
      nameIdx /= alphabetSize;
      position++;
      length++;
    } while (nameIdx > 0);
    return length;
  }

  /**
   * Returns the {@code nameIdx}-th short name. This might be a reserved name.
   * A user-requested prefix is not included, but the first returned character
   * is supposed to go at position {@code position} in the final name
   */
  private String generateSuffix(int position, int nameIdx) {
    StringBuilder name = new StringBuilder();
    int length = getNameLength(position, nameIdx);
    nameIdx++;
    do {
      nameIdx--;
      String alphabet;
      if (position == 0) {
        alphabet = shuffledFirst;
      } else {
        Hasher hasher = Hashing.murmur3_128().newHasher();
        hasher.putInt(length);
        hasher.putUnencodedChars(name);
        int alphabetIdx = (hasher.hash().asInt() & 0x7fffffff) % NUM_SHUFFLES;
        alphabet = shuffledNonFirst.get(alphabetIdx);
      }
      int alphabetSize = alphabet.length();
      char character = alphabet.charAt(nameIdx % alphabetSize);
      name.append(character);

      nameIdx /= alphabetSize;
      position++;
    } while (nameIdx > 0);
    return name.toString();
  }

  /**
   * Generates the next short name.
   *
   * <p>This generates names of increasing length. To minimize output size,
   * therefore, it's good to call it for the most used symbols first.
   */
  @Override
  public String generateNextName() {
    while (true) {
      String name = prefix + generateSuffix(prefix.length(), nameCount++);

      // Make sure it's not a JS keyword or reserved name.
      if (TokenStream.isKeyword(name) || reservedNames.contains(name)) {
        continue;
      }

      return name;
    }
  }
}
