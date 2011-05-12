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

import java.util.BitSet;
import java.util.Random;

import com.google.javascript.jscomp.regex.CharRanges;

import junit.framework.TestCase;

public class CharRangesTest extends TestCase {

  static final long SEED = Long.parseLong(System.getProperty(
      "junit.random.seed", "" + System.currentTimeMillis()));

  public final void testAgainstRegularImplementation() {
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
          fail("sbs=" + sbs + ", bs=" + bs + ", difference at bit " + i);
        }
      }
    }
  }

  public final void testEmptyCharRanges() {
    CharRanges sbs = CharRanges.EMPTY;
    for (int i = -1000; i < 1000; ++i) {
      assertFalse(sbs.contains(i));
    }
    assertEquals("[]", sbs.toString());
  }

  public final void testCharRangesFactories() {
    CharRanges isbs = CharRanges.withMembers(new int[] { 0, 1, 4, 9 });
    CharRanges isbs2 = CharRanges.withMembers(new int[] { 0, 1, 4, 9 });
    assertEquals("[0x0-0x1 0x4 0x9]", isbs.toString());

    CharRanges esbs = CharRanges.withMembers(new int[0]);

    assertEquals(isbs, isbs);
    assertEquals(isbs, isbs2);
    assertFalse(isbs.equals(esbs));
    assertFalse(isbs.equals(null));
    assertFalse(isbs.equals(new Object()));

    assertEquals(isbs.hashCode(), isbs2.hashCode());
    assertFalse(isbs.hashCode() == esbs.hashCode());
  }

  public final void testRangeConstructor() {
    try {
      CharRanges.withRanges(new int[] { 1 });
      fail("Mismatched ranges");
    } catch (IllegalArgumentException ex) {
      // pass
    }

    try {
      CharRanges.withRanges(new int[] { 1, 4, 4, 5 });
      fail("Discontiguous ranges");
    } catch (IllegalArgumentException ex) {
      // pass
    }

    try {
      CharRanges.withRanges(new int[] { 4, 5, 1, 3 });
      fail("Misordered ranges");
    } catch (IllegalArgumentException ex) {
      // pass
    }

    try {
      CharRanges.withRanges(new int[] { 0, 0 });
      fail("Empty range");
    } catch (IllegalArgumentException ex) {
      // pass
    }
  }

  public final void testDupeMembers() {
    CharRanges sbs1 = CharRanges.withMembers(new int[] { 0, 1, 4, 9 });
    assertEquals(sbs1.toString(), "[0x0-0x1 0x4 0x9]", sbs1.toString());

    CharRanges sbs2 = CharRanges.withMembers(new int[] { 9, 1, 4, 1, 0 });
    assertEquals(sbs2.toString(), "[0x0-0x1 0x4 0x9]", sbs2.toString());

    assertEquals(sbs1, sbs2);
    assertEquals(sbs1.hashCode(), sbs2.hashCode());

    for (int i = -10; i < 20; ++i) {
      assertEquals("" + i, sbs1.contains(i), sbs2.contains(i));
    }
  }

  public final void testDifference() {
    //                     1               2               3
    //     0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0
    // b-a  DD         DD DDD        D      DDD
    // a      AAAAAAAAA      A A A A   A AAA   AAA A A
    // b    BBB  BBB  BBB BBB        B B    BBB
    // a-b     DD   DD       D D D D     DDD   DDD D D
    CharRanges a = CharRanges.withRanges(new int[] {
        0x03, 0x0C, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19,
        0x1C, 0x1D, 0x1E, 0x21, 0x24, 0x27, 0x28, 0x29, 0x2A, 0x2B });
    CharRanges b = CharRanges.withRanges(new int[] {
        0x01, 0x04, 0x06, 0x09, 0x0B, 0x0E, 0x0F, 0x12, 0x1A, 0x1B,
        0x1C, 0x1D, 0x21, 0x24 });
    CharRanges empty = CharRanges.withMembers(new int[0]);

    assertEquals(empty, empty.union(empty));
    assertEquals(a, a.union(empty));
    assertEquals(b, empty.union(b));

    CharRanges aSb = a.difference(b);
    assertEquals(
        "[0x4-0x5 0x9-0xa 0x12 0x14 0x16 0x18 0x1e-0x20 0x24-0x26 0x28 0x2a]",
        aSb.toString());
    assertTrue(a.containsAll(aSb));
    assertFalse(aSb.containsAll(a));
    assertFalse(aSb.containsAll(b));

    CharRanges bSa = b.difference(a);
    assertEquals(
        "[0x1-0x2 0xc-0xd 0xf-0x11 0x1a 0x21-0x23]",
        bSa.toString());
    assertTrue(b.containsAll(bSa));
    assertFalse(bSa.containsAll(a));
    assertFalse(bSa.containsAll(b));

    // Check that a and b not changed by operation
    assertEquals(
        "[0x3-0xb 0x12 0x14 0x16 0x18 0x1c 0x1e-0x20 0x24-0x26 0x28 0x2a]",
        a.toString());
    assertEquals(
        "[0x1-0x3 0x6-0x8 0xb-0xd 0xf-0x11 0x1a 0x1c 0x21-0x23]",
        b.toString());

    //    0 1 2 3 4 5 6 7 8 9 a b c d e f
    // m: * * * *     *     * *       * *
    // s:     *     * * *     * *   * *
    // d: * *   *           *           *
    CharRanges m = CharRanges.withMembers(0, 1, 2, 3, 6, 9, 0xa, 0xe, 0xf);
    CharRanges s = CharRanges.withMembers(2, 5, 6, 7, 0xa, 0xb, 0xd, 0xe);
    CharRanges d = m.difference(s);
    assertEquals("[0x0-0x1 0x3 0x9 0xf]", d.toString());
    assertTrue(m.containsAll(d));
    assertFalse(d.containsAll(m));
    assertFalse(d.containsAll(s));
    assertFalse(s.containsAll(d));
    assertTrue(d.containsAll(d));
  }

  public final void testUnion() {
    //                 1               2               3
    // 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0
    //    AAAAAAAAA      A A A A   A AAA   AAA A A
    //  BBB  BBB  BBB BBB        B B    BBB
    //  UUUUUUUUUUUUU UUUU U U U U U UUUUUUUUU U U
    CharRanges a = CharRanges.withRanges(new int[] {
        0x03, 0x0C, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19,
        0x1C, 0x1D, 0x1E, 0x21, 0x24, 0x27, 0x28, 0x29, 0x2A, 0x2B });
    CharRanges b = CharRanges.withRanges(new int[] {
        0x01, 0x04, 0x06, 0x09, 0x0B, 0x0E, 0x0F, 0x12, 0x1A, 0x1B,
        0x1C, 0x1D, 0x21, 0x24 });
    CharRanges empty = CharRanges.withMembers(new int[0]);

    assertEquals(empty, empty.union(empty));
    assertEquals(a, a.union(empty));
    assertEquals(b, empty.union(b));

    CharRanges aUb = a.union(b);
    assertEquals(
        "[0x1-0xd 0xf-0x12 0x14 0x16 0x18 0x1a 0x1c 0x1e-0x26 0x28 0x2a]",
        aUb.toString());
    assertEquals(aUb, b.union(a));
    assertTrue(aUb.containsAll(a));
    assertTrue(aUb.containsAll(b));
    assertFalse(a.containsAll(b));
    assertFalse(b.containsAll(a));
    assertTrue(a.containsAll(a));
    assertTrue(b.containsAll(b));
    assertTrue(aUb.containsAll(aUb));

    // Check that a and b not changed by operation
    assertEquals(
        "[0x3-0xb 0x12 0x14 0x16 0x18 0x1c 0x1e-0x20 0x24-0x26 0x28 0x2a]",
        a.toString());
    assertEquals(
        "[0x1-0x3 0x6-0x8 0xb-0xd 0xf-0x11 0x1a 0x1c 0x21-0x23]",
        b.toString());
  }
}
