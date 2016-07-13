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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.Set;

public final class DefaultNameGeneratorTest extends TestCase {

  private static final Set<String> RESERVED_NAMES = ImmutableSet.of(
      "ba",
      "xba");

  private static String[] generate(
      DefaultNameGenerator ng, String prefix, int num) throws Exception {
    String[] result = new String[num];
    for (int i = 0; i < num; i++) {
      result[i] = ng.generateNextName();
      if (!result[i].startsWith(prefix)) {
        fail("Error: " + result[i]);
      }
    }
    return result;
  }

  public static void testNameGeneratorInvalidPrefixes() throws Exception {
    try {
      new DefaultNameGenerator(Collections.<String>emptySet(), "123abc", null);
      fail("Constructor should throw exception when the first char of prefix "
          + "is invalid");
    } catch (IllegalArgumentException ex) {
      // The error messages should contain meaningful information.
      assertThat(ex.getMessage()).contains("W, X, Y, Z, $]");
    }

    try {
      new DefaultNameGenerator(Collections.<String>emptySet(), "abc%", null);
      fail("Constructor should throw exception when one of prefix characters "
          + "is invalid");
    } catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).contains("W, X, Y, Z, _, 0, 1");
    }
  }

  public static void testGenerate() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(
        RESERVED_NAMES, "", null);
    String[] result = generate(ng, "", 106);
    assertEquals("a", result[0]);
    assertEquals("z", result[25]);
    assertEquals("A", result[26]);
    assertEquals("Z", result[51]);
    assertEquals("$", result[52]);
    assertEquals("aa", result[53]);
    // ba is reserved
    assertEquals("ca", result[54]);
    assertEquals("$a", result[104]);

    ng = new DefaultNameGenerator(RESERVED_NAMES, "x", null);
    result = generate(ng, "x", 132);

    // Expected: x, xa, ..., x$, xaa, ..., x$$
    assertEquals("x", result[0]);
    assertEquals("xa", result[1]);
    assertEquals("x$", result[64]);
    assertEquals("xaa", result[65]);
    // xba is reserved
    assertEquals("xca", result[66]);
  }

  public static void testReserve() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(
        RESERVED_NAMES, "", new char[] {'$'});
    String[] result = generate(ng, "", 106);
    assertEquals("a", result[0]);
    assertEquals("z", result[25]);
    assertEquals("A", result[26]);
    assertEquals("Z", result[51]);
    assertEquals("aa", result[52]);
    // ba is reserved
    assertEquals("ca", result[53]);
    assertEquals("ab", result[103]);
  }

  public static void testGenerateWithPriority1() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(
        RESERVED_NAMES, "", null);
    String[] result = generate(ng, "", 106);
    assertEquals("a", result[0]);
    assertEquals("z", result[25]);
    assertEquals("A", result[26]);
    assertEquals("Z", result[51]);
    assertEquals("$", result[52]);
    assertEquals("aa", result[53]);

    ng.favors("b");
    ng.reset(RESERVED_NAMES, "", null);
    result = generate(ng, "", 106);
    assertEquals("b", result[0]);
    assertEquals("a", result[1]);
    assertEquals("c", result[2]);
    assertEquals("d", result[3]);

    ng.favors("cc");
    ng.reset(RESERVED_NAMES, "", null);
    result = generate(ng, "", 106);
    assertEquals("c", result[0]);
    assertEquals("b", result[1]);
    assertEquals("a", result[2]);
    assertEquals("d", result[3]);
  }

  public static void testGenerateWithPriority2() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(
        RESERVED_NAMES, "", null);
    String[] result = generate(ng, "", 106);
    assertEquals("a", result[0]);
    assertEquals("z", result[25]);
    assertEquals("A", result[26]);
    assertEquals("Z", result[51]);
    assertEquals("$", result[52]);
    assertEquals("aa", result[53]);

    ng.favors("function");
    ng.favors("function");
    ng.favors("function");

    ng.reset(RESERVED_NAMES, "", null);
    result = generate(ng, "", 106);

    // All the letters of function should come first. In alphabetical order.
    assertEquals("n", result[0]);
    assertEquals("c", result[1]);
    assertEquals("f", result[2]);
    assertEquals("i", result[3]);
    assertEquals("o", result[4]);
    assertEquals("t", result[5]);
    assertEquals("u", result[6]);

    // Back to normal.
    assertEquals("a", result[7]);
    assertEquals("b", result[8]);

    // c has been prioritized.
    assertEquals("d", result[9]);
    assertEquals("e", result[10]);

    // This used to start with 'aa' but now n is prioritized over it.
    assertEquals("nn", result[53]);
    assertEquals("cn", result[54]);
  }

  public static void testGenerateWithPriority3() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(
        RESERVED_NAMES, "", null);
    String[] result = generate(ng, "", 106);
    ng.favors("???");
    ng.reset(RESERVED_NAMES, "", null);
    result = generate(ng, "", 106);
    assertEquals("a", result[0]);
  }
}
