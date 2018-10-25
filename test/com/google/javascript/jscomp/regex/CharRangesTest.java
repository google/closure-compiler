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

package com.google.javascript.jscomp.regex;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.testing.EqualsTester;
import java.util.BitSet;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CharRangesTest {

  static final long SEED = Long.parseLong(System.getProperty(
      "junit.random.seed", "" + System.currentTimeMillis()));

  @Test
  public void testAgainstRegularImplementation() {
    Random rnd = new Random(SEED);

    for (int run = 10; --run >= 0;) {
      // Fill with bits in the range [0x1000, 0x3000).
      BitSet bs = new BitSet();
      for (int i = 0x1000; --i >= 0;) {
        bs.set(0x1000 + rnd.nextInt(0x3000));
      }

      // Create an equivalent sparse bit set
      int[] members = new int[bs.cardinality()];
      for (int i = -1, k = 0; k < members.length; ++k) {
        members[k] = i = bs.nextSetBit(i + 1);
      }
      CharRanges sbs = CharRanges.withMembers(members);

      // Check all bits including past the min/max bit
      for (int i = 0; i < 0x5000; ++i) {
        if (bs.get(i) != sbs.contains(i)) {
          assertWithMessage("sbs=" + sbs + ", bs=" + bs + ", difference at bit " + i).fail();
        }
      }
    }
  }

  @Test
  public void testEmptyCharRanges() {
    CharRanges sbs = CharRanges.EMPTY;
    for (int i = -1000; i < 1000; ++i) {
      assertThat(sbs.contains(i)).isFalse();
    }
    assertThat(sbs.toString()).isEqualTo("[]");
  }

  @Test
  public void testCharRangesFactories() {
    CharRanges isbs = CharRanges.withMembers(0, 1, 4, 9);
    CharRanges isbs2 = CharRanges.withMembers(0, 1, 4, 9);
    assertThat(isbs.toString()).isEqualTo("[0x0-0x1 0x4 0x9]");

    CharRanges esbs = CharRanges.withMembers();

    assertThat(isbs2).isEqualTo(isbs);
    assertThat(isbs).isNotEqualTo(esbs);
    assertThat(isbs).isNotEqualTo(null);
    assertThat(isbs).isNotEqualTo(new Object());

    assertThat(isbs2.hashCode()).isEqualTo(isbs.hashCode());
    assertThat(isbs.hashCode()).isNotEqualTo(esbs.hashCode());
  }

  @Test
  public void testRangeConstructor() {
    try {
      CharRanges.withRanges(1);
      assertWithMessage("Mismatched ranges").fail();
    } catch (IllegalArgumentException ex) {
      // pass
    }

    try {
      CharRanges.withRanges(1, 4, 4, 5);
      assertWithMessage("Discontiguous ranges").fail();
    } catch (IllegalArgumentException ex) {
      // pass
    }

    try {
      CharRanges.withRanges(4, 5, 1, 3);
      assertWithMessage("Misordered ranges").fail();
    } catch (IllegalArgumentException ex) {
      // pass
    }

    try {
      CharRanges.withRanges(0, 0);
      assertWithMessage("Empty range").fail();
    } catch (IllegalArgumentException ex) {
      // pass
    }
  }

  @Test
  public void testDupeMembers() {
    CharRanges sbs1 = CharRanges.withMembers(0, 1, 4, 9);
    assertWithMessage(sbs1.toString()).that(sbs1.toString()).isEqualTo("[0x0-0x1 0x4 0x9]");

    CharRanges sbs2 = CharRanges.withMembers(9, 1, 4, 1, 0);
    assertWithMessage(sbs2.toString()).that(sbs2.toString()).isEqualTo("[0x0-0x1 0x4 0x9]");

    new EqualsTester().addEqualityGroup(sbs1, sbs2).testEquals();

    for (int i = -10; i < 20; ++i) {
      assertWithMessage("" + i).that(sbs2.contains(i)).isEqualTo(sbs1.contains(i));
    }
  }

  @Test
  public void testDifference() {
    //                     1               2               3
    //     0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0
    // b-a  DD         DD DDD        D      DDD
    // a      AAAAAAAAA      A A A A   A AAA   AAA A A
    // b    BBB  BBB  BBB BBB        B B    BBB
    // a-b     DD   DD       D D D D     DDD   DDD D D
    CharRanges a = CharRanges.withRanges(0x03, 0x0C, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19,
        0x1C, 0x1D, 0x1E, 0x21, 0x24, 0x27, 0x28, 0x29, 0x2A, 0x2B);
    CharRanges b = CharRanges.withRanges(0x01, 0x04, 0x06, 0x09, 0x0B, 0x0E, 0x0F, 0x12, 0x1A, 0x1B,
        0x1C, 0x1D, 0x21, 0x24);
    CharRanges empty = CharRanges.withMembers();

    assertThat(empty.union(empty)).isEqualTo(empty);
    assertThat(a.union(empty)).isEqualTo(a);
    assertThat(empty.union(b)).isEqualTo(b);

    CharRanges aSb = a.difference(b);
    assertThat(aSb.toString())
        .isEqualTo("[0x4-0x5 0x9-0xa 0x12 0x14 0x16 0x18 0x1e-0x20 0x24-0x26 0x28 0x2a]");
    assertThat(a.containsAll(aSb)).isTrue();
    assertThat(aSb.containsAll(a)).isFalse();
    assertThat(aSb.containsAll(b)).isFalse();

    CharRanges bSa = b.difference(a);
    assertThat(bSa.toString()).isEqualTo("[0x1-0x2 0xc-0xd 0xf-0x11 0x1a 0x21-0x23]");
    assertThat(b.containsAll(bSa)).isTrue();
    assertThat(bSa.containsAll(a)).isFalse();
    assertThat(bSa.containsAll(b)).isFalse();

    // Check that a and b not changed by operation
    assertThat(a.toString())
        .isEqualTo("[0x3-0xb 0x12 0x14 0x16 0x18 0x1c 0x1e-0x20 0x24-0x26 0x28 0x2a]");
    assertThat(b.toString()).isEqualTo("[0x1-0x3 0x6-0x8 0xb-0xd 0xf-0x11 0x1a 0x1c 0x21-0x23]");

    //    0 1 2 3 4 5 6 7 8 9 a b c d e f
    // m: * * * *     *     * *       * *
    // s:     *     * * *     * *   * *
    // d: * *   *           *           *
    CharRanges m = CharRanges.withMembers(0, 1, 2, 3, 6, 9, 0xa, 0xe, 0xf);
    CharRanges s = CharRanges.withMembers(2, 5, 6, 7, 0xa, 0xb, 0xd, 0xe);
    CharRanges d = m.difference(s);
    assertThat(d.toString()).isEqualTo("[0x0-0x1 0x3 0x9 0xf]");
    assertThat(m.containsAll(d)).isTrue();
    assertThat(d.containsAll(m)).isFalse();
    assertThat(d.containsAll(s)).isFalse();
    assertThat(s.containsAll(d)).isFalse();
    assertThat(d.containsAll(d)).isTrue();
  }

  @Test
  public void testUnion() {
    //                 1               2               3
    // 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0
    //    AAAAAAAAA      A A A A   A AAA   AAA A A
    //  BBB  BBB  BBB BBB        B B    BBB
    //  UUUUUUUUUUUUU UUUU U U U U U UUUUUUUUU U U
    CharRanges a = CharRanges.withRanges(0x03, 0x0C, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19,
        0x1C, 0x1D, 0x1E, 0x21, 0x24, 0x27, 0x28, 0x29, 0x2A, 0x2B);
    CharRanges b = CharRanges.withRanges(0x01, 0x04, 0x06, 0x09, 0x0B, 0x0E, 0x0F, 0x12, 0x1A, 0x1B,
        0x1C, 0x1D, 0x21, 0x24);
    CharRanges empty = CharRanges.withMembers();

    assertThat(empty.union(empty)).isEqualTo(empty);
    assertThat(a.union(empty)).isEqualTo(a);
    assertThat(empty.union(b)).isEqualTo(b);

    CharRanges aUb = a.union(b);
    assertThat(aUb.toString())
        .isEqualTo("[0x1-0xd 0xf-0x12 0x14 0x16 0x18 0x1a 0x1c 0x1e-0x26 0x28 0x2a]");
    assertThat(b.union(a)).isEqualTo(aUb);
    assertThat(aUb.containsAll(a)).isTrue();
    assertThat(aUb.containsAll(b)).isTrue();
    assertThat(a.containsAll(b)).isFalse();
    assertThat(b.containsAll(a)).isFalse();
    assertThat(a.containsAll(a)).isTrue();
    assertThat(b.containsAll(b)).isTrue();
    assertThat(aUb.containsAll(aUb)).isTrue();

    // Check that a and b not changed by operation
    assertThat(a.toString())
        .isEqualTo("[0x3-0xb 0x12 0x14 0x16 0x18 0x1c 0x1e-0x20 0x24-0x26 0x28 0x2a]");
    assertThat(b.toString()).isEqualTo("[0x1-0x3 0x6-0x8 0xb-0xd 0xf-0x11 0x1a 0x1c 0x21-0x23]");
  }
}
