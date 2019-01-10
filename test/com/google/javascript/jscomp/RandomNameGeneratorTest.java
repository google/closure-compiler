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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Random;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RandomNameGeneratorTest {

  private static String[] generate(RandomNameGenerator ng, int count)
      throws Exception {
    String[] result = new String[count];
    for (int i = 0; i < count; i++) {
      result[i] = ng.generateNextName();
    }
    return result;
  }

  @Test
  public void testConstructorInvalidPrefixes() throws Exception {
    Random random = new Random(0);

    try {
      new RandomNameGenerator(ImmutableSet.of(), "123abc", null, random);
      assertWithMessage(
              "Constructor should throw exception when the first char of prefix is invalid")
          .fail();
    } catch (IllegalArgumentException ex) {
      // The error messages should contain meaningful information.
      assertThat(ex).hasMessageThat().contains("W, X, Y, Z, $");
    }

    try {
      new RandomNameGenerator(ImmutableSet.of(), "abc%", null, random);
      assertWithMessage(
              "Constructor should throw exception when one of prefix characters is invalid")
          .fail();
    } catch (IllegalArgumentException ex) {
      assertThat(ex).hasMessageThat().contains("W, X, Y, Z, _, 0, 1");
    }
  }

  @Test
  public void testGenerate() throws Exception {
    // Since there's a hash function involved, there's not much point in
    // mocking Random to get nicer values. Instead, let's just try to
    // verify the sanity of the results.
    Random random = new Random(0);
    Set<String> reservedNames = ImmutableSet.of();
    String prefix = "prefix";
    int prefixLen = prefix.length();
    // Add a prefix to avoid dropping JavaScript keywords.
    RandomNameGenerator ng = new RandomNameGenerator(
        reservedNames, prefix, null, random);
    // Generate all 1- and 2-character names.
    // alphabet length, 1st digit
    int len1 = RandomNameGenerator.NONFIRST_CHAR.size();
    // alphabet length, 2nd digit
    int len2 = RandomNameGenerator.NONFIRST_CHAR.size();
    // len1 == len2 because we have a prefix
    int count = len1 * (1 + len2);
    String[] result = generate(ng, count);
    Set<String> resultSet = Sets.newHashSet(result);

    // We got as many names as we asked for, and all are different.
    assertThat(resultSet).hasSize(count);

    // First come names with length 1, then 2. No names are longer.
    for (int i = 0; i < len1; ++i) {
      assertThat(result[i].length()).isEqualTo(prefixLen + 1);
    }
    for (int i = len1; i < count; ++i) {
      assertThat(result[i].length()).isEqualTo(prefixLen + 2);
    }

    // We don't just have an alphabet for the first character and one for the
    // second, i.e. if we just had the valid characters A,B,C,D,E and the
    // shuffle was such that we started with C,D,A,B,E as names, the following
    // names would not just be CC,DC,AC,BC,EC,CD,DD,AD,BD,ED,...
    // Of course it could randomly happen some time, which is why we check
    // for all occurrences.

    // Verify that we don't get C,D,A,B,E,_C,_C,_C,_C,_C,_D,_D,_D,_D,_D_,...
    int countPass = 0;
    int countTest = 0;
    for (int i1 = 0; i1 < len1; ++i1) {
      for (int i2 = 0; i2 < len2; ++i2, ++countTest) {
        if (result[i1].charAt(prefixLen)
            != result[len1 + i1 * len2 + i2].charAt(prefixLen + 1)) {
          countPass++;
        }
      }
    }
    assertThat(100.0 * countPass / countTest).isGreaterThan(80.0); // arbitrary threshold

    // Names are not sorted (some might be, by chance)
    countPass = 0;
    countTest = 0;
    for (int i = 0; i < count - 1; ++i) {
      if (result[i].compareTo(result[i + 1]) > 0) {
        countPass++;
      }
    }
    assertThat(100.0 * countPass / countTest).isGreaterThan(25.0); // arbitrary threshold
  }

  @Test
  public void testFirstCharAlphabet() throws Exception {
    Random random = new Random(0);
    Set<String> reservedNames = ImmutableSet.of();
    RandomNameGenerator ng = new RandomNameGenerator(
        reservedNames, "", null, random);
    // Generate all 1- and 2-character names.
    int len1 = RandomNameGenerator.FIRST_CHAR.size();
    int len2 = RandomNameGenerator.NONFIRST_CHAR.size();
    int count = len1 * (1 + len2);
    String[] result = generate(ng, count);
    Set<String> resultSet = Sets.newHashSet(result);

    // We got as many names as we asked for, and all are different.
    assertThat(resultSet).hasSize(count);

    // First come len1 names with length 1, then 2.
    for (int i = 0; i < len1; ++i) {
      assertThat(result[i].length()).isEqualTo(1);
    }
    // Because there's no prefix, we use the "first characters" alphabet
    // for the first character, so there will be only len1 length-1 names,
    // not len2 as in testGenerate.

    for (int i = len1; i < count; ++i) {
      // Because we don't have a prefix, some generated names will be
      // JavaScript keywords and will be skipped, so we'll get a few
      // length-3 names.
      assertThat(result[i].length()).isAtLeast(2);
    }
  }

  @Test
  public void testPrefix() throws Exception {
    Random random = new Random(0);
    Set<String> reservedNames = ImmutableSet.of();
    String prefix = "prefix";
    RandomNameGenerator ng = new RandomNameGenerator(
        reservedNames, prefix, null, random);
    // Generate all 1- and 2-character names.
    int len1 = RandomNameGenerator.FIRST_CHAR.size();
    int len2 = RandomNameGenerator.NONFIRST_CHAR.size();
    int count = len1 * (1 + len2);
    String[] result = generate(ng, count);

    for (int i = 0; i < count; ++i) {
      assertThat(result[i]).startsWith(prefix);
      assertThat(result[i].length()).isGreaterThan(prefix.length());
    }
  }

  @Test
  public void testSeeds() throws Exception {
    // Using different seeds should return different names.
    Random random0 = new Random(0);
    Random random1 = new Random(1);
    Set<String> reservedNames = ImmutableSet.of();
    RandomNameGenerator ng0 = new RandomNameGenerator(
        reservedNames, "", null, random0);
    RandomNameGenerator ng1 = new RandomNameGenerator(
        reservedNames, "", null, random1);

    int count = 1000;
    String[] results0 = generate(ng0, count);
    String[] results1 = generate(ng1, count);

    int countPass = 0;
    int countTest = 0;
    for (int i = 0; i < count; ++i, ++countTest) {
      if (!results0[i].equals(results1[i])) {
        countPass++;
      }
    }
    assertThat(100.0 * countPass / countTest).isGreaterThan(90.0); // arbitrary threshold
  }

  @Test
  public void testReservedNames() throws Exception {
    Random random = new Random(0);
    Set<String> reservedNames = ImmutableSet.of("x", "ba");
    RandomNameGenerator ng = new RandomNameGenerator(
        reservedNames, "", null, random);
    // Generate all 1- and 2-character names (and a couple 3-character names,
    // because "x" and "ba", and keywords, shouldn't be used).
    int count =
        RandomNameGenerator.FIRST_CHAR.size() * (RandomNameGenerator.NONFIRST_CHAR.size() + 1);
    Set<String> result = Sets.newHashSet(generate(ng, count));

    assertThat(result).doesNotContain("x");
    assertThat(result).doesNotContain("ba");

    // Even though we skipped "x" and "ba", we still got 'count' different
    // names. We know they are different because 'result' is a Set.
    assertThat(result).hasSize(count);
  }

  @Test
  public void testReservedCharacters() throws Exception {
    Random random = new Random(0);
    Set<String> reservedNames = ImmutableSet.of();
    RandomNameGenerator ng = new RandomNameGenerator(
        reservedNames, "", new char[]{'a', 'b'}, random);
    // Generate all 1- and 2-character names (and also many 3-character names,
    // because "a" and "b" shouldn't be used).
    int count =
        RandomNameGenerator.FIRST_CHAR.size() * (RandomNameGenerator.NONFIRST_CHAR.size() + 1);
    Set<String> result = Sets.newHashSet(generate(ng, count));

    assertThat(result).doesNotContain("a");
    assertThat(result).doesNotContain("b");
    assertThat(result).contains("x");
    assertThat(result).doesNotContain("ax");
    assertThat(result).doesNotContain("bx");
    assertThat(result).doesNotContain("xa");
    assertThat(result).doesNotContain("xb");
    assertThat(result).contains("xx");

    // Even though we skipped a few names with reserved characters, we still
    // got 'count' different names. We know they are different because 'result'
    // is a Set.
    assertThat(result).hasSize(count);
  }
}
