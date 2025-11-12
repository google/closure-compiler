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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DefaultNameGeneratorTest {

  private static final ImmutableSet<String> RESERVED_NAMES = ImmutableSet.of("ba", "xba");

  private static String[] generate(DefaultNameGenerator ng, String prefix, int num)
      throws Exception {
    String[] result = new String[num];
    for (int i = 0; i < num; i++) {
      result[i] = ng.generateNextName();
      if (!result[i].startsWith(prefix)) {
        assertWithMessage("Error: %s", result[i]).fail();
      }
    }
    return result;
  }

  @Test
  public void testNameGeneratorInvalidPrefixes() throws Exception {
    try {
      new DefaultNameGenerator(ImmutableSet.of(), "123abc", ImmutableSet.of());
      assertWithMessage(
              "Constructor should throw exception when the first char of prefix is invalid")
          .fail();
    } catch (IllegalArgumentException ex) {
      // The error messages should contain meaningful information.
      assertThat(ex).hasMessageThat().contains("W, X, Y, Z, $]");
    }

    try {
      new DefaultNameGenerator(ImmutableSet.of(), "abc%", ImmutableSet.of());
      assertWithMessage(
              "Constructor should throw exception when one of prefix characters is invalid")
          .fail();
    } catch (IllegalArgumentException ex) {
      assertThat(ex).hasMessageThat().contains("W, X, Y, Z, _, 0, 1");
    }
  }

  @Test
  public void testGenerate() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(RESERVED_NAMES, "", ImmutableSet.of());
    String[] result = generate(ng, "", 106);
    assertThat(result[0]).isEqualTo("a");
    assertThat(result[25]).isEqualTo("z");
    assertThat(result[26]).isEqualTo("A");
    assertThat(result[51]).isEqualTo("Z");
    assertThat(result[52]).isEqualTo("$");
    assertThat(result[53]).isEqualTo("aa");
    // ba is reserved
    assertThat(result[54]).isEqualTo("ca");
    assertThat(result[104]).isEqualTo("$a");

    ng = new DefaultNameGenerator(RESERVED_NAMES, "x", ImmutableSet.of());
    result = generate(ng, "x", 132);

    // Expected: x, xa, ..., x$, xaa, ..., x$$
    assertThat(result[0]).isEqualTo("x");
    assertThat(result[1]).isEqualTo("xa");
    assertThat(result[64]).isEqualTo("x$");
    assertThat(result[65]).isEqualTo("xaa");
    // xba is reserved
    assertThat(result[66]).isEqualTo("xca");
  }

  @Test
  public void testReserve() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(RESERVED_NAMES, "", ImmutableSet.of('$'));
    String[] result = generate(ng, "", 106);
    assertThat(result[0]).isEqualTo("a");
    assertThat(result[25]).isEqualTo("z");
    assertThat(result[26]).isEqualTo("A");
    assertThat(result[51]).isEqualTo("Z");
    assertThat(result[52]).isEqualTo("aa");
    // ba is reserved
    assertThat(result[53]).isEqualTo("ca");
    assertThat(result[103]).isEqualTo("ab");
  }

  @Test
  public void testES6KeywordsNotGenerated() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(RESERVED_NAMES, "le", ImmutableSet.of('$'));
    String[] result = generate(ng, "le", 106);
    assertThat(result[19]).isEqualTo("les");
    assertThat(result[20]).isEqualTo("leu"); // "let" keyword skipped
    assertThat(result[45]).isEqualTo("leT"); // "leT" not skipped

    ng = new DefaultNameGenerator(RESERVED_NAMES, "awai", ImmutableSet.of('$'));
    result = generate(ng, "awai", 106);
    assertThat(result[19]).isEqualTo("awais");
    assertThat(result[20]).isEqualTo("awaiu"); // "await" keyword skipped
    assertThat(result[45]).isEqualTo("awaiT"); // "awaiT" not skipped
  }

  @Test
  public void testGenerateWithPriority1() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(RESERVED_NAMES, "", ImmutableSet.of());
    String[] result = generate(ng, "", 106);
    assertThat(result[0]).isEqualTo("a");
    assertThat(result[25]).isEqualTo("z");
    assertThat(result[26]).isEqualTo("A");
    assertThat(result[51]).isEqualTo("Z");
    assertThat(result[52]).isEqualTo("$");
    assertThat(result[53]).isEqualTo("aa");

    ng.favors("b");
    ng.reset(RESERVED_NAMES, "", ImmutableSet.of());
    result = generate(ng, "", 106);
    assertThat(result[0]).isEqualTo("b");
    assertThat(result[1]).isEqualTo("a");
    assertThat(result[2]).isEqualTo("c");
    assertThat(result[3]).isEqualTo("d");

    ng.favors("cc");
    ng.reset(RESERVED_NAMES, "", ImmutableSet.of());
    result = generate(ng, "", 106);
    assertThat(result[0]).isEqualTo("c");
    assertThat(result[1]).isEqualTo("b");
    assertThat(result[2]).isEqualTo("a");
    assertThat(result[3]).isEqualTo("d");
  }

  @Test
  public void testGenerateWithPriority2() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(RESERVED_NAMES, "", ImmutableSet.of());
    String[] result = generate(ng, "", 106);
    assertThat(result[0]).isEqualTo("a");
    assertThat(result[25]).isEqualTo("z");
    assertThat(result[26]).isEqualTo("A");
    assertThat(result[51]).isEqualTo("Z");
    assertThat(result[52]).isEqualTo("$");
    assertThat(result[53]).isEqualTo("aa");

    ng.favors("function");
    ng.favors("function");
    ng.favors("function");

    ng.reset(RESERVED_NAMES, "", ImmutableSet.of());
    result = generate(ng, "", 106);

    // All the letters of function should come first. In alphabetical order.
    assertThat(result[0]).isEqualTo("n");
    assertThat(result[1]).isEqualTo("c");
    assertThat(result[2]).isEqualTo("f");
    assertThat(result[3]).isEqualTo("i");
    assertThat(result[4]).isEqualTo("o");
    assertThat(result[5]).isEqualTo("t");
    assertThat(result[6]).isEqualTo("u");

    // Back to normal.
    assertThat(result[7]).isEqualTo("a");
    assertThat(result[8]).isEqualTo("b");

    // c has been prioritized.
    assertThat(result[9]).isEqualTo("d");
    assertThat(result[10]).isEqualTo("e");

    // This used to start with 'aa' but now n is prioritized over it.
    assertThat(result[53]).isEqualTo("nn");
    assertThat(result[54]).isEqualTo("cn");
  }

  @Test
  public void testGenerateWithPriority3() throws Exception {
    DefaultNameGenerator ng = new DefaultNameGenerator(RESERVED_NAMES, "", ImmutableSet.of());
    String[] result = generate(ng, "", 106);
    ng.favors("???");
    ng.reset(RESERVED_NAMES, "", ImmutableSet.of());
    result = generate(ng, "", 106);
    assertThat(result[0]).isEqualTo("a");
  }
}
