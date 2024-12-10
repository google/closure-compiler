/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino;

import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Stream;

/**
 * An interning pool for strings used by the Rhino package.
 *
 * <p>As of 2021-03-16 and again as of 2024-12-09, this custom pool is measurably more performant
 * than `String::intern`.
 */
public final class RhinoStringPool {

  /**
   * The threadsafe datastructure that backs this pool.
   *
   * <p>We use weak-refs, rather than strong-refs, to prevent a memory leak in server-like
   * applications.
   */
  private static final Interner<String> INTERNER = Interners.newWeakInterner();

  /**
   * Check if two strings are the same according to interning.
   *
   * <p>The caller is responsible for ensuring that strings passed to this method are actually
   * interned. This method mainly exists to highlight where equality checks depend on interning for
   * correctness.
   *
   * <p>Ideally we could use something like a branded-type, for interned strings, to verify correct
   * usage. However, Java doesn't support type-brands, and using like wrapper objects would
   * undermine performance. This also needs to be ergonomic enough that callers don't resort to
   * using `==` directly.
   */
  public static boolean uncheckedEquals(String a, String b) {
    return identical(a, b);
  }

  public static String addOrGet(String s) {
    return INTERNER.intern(s);
  }

  private RhinoStringPool() {}

  /**
   * A list of strings that is lazily interned into a RhinoStringPool as they are accessed.
   *
   * <p>The only reason to use this class is for performance optimizations - the {@link
   * LazyInternedStringList#newStringNode} method skips re-interning the string. This is a
   * performance win if creating a lot of string nodes with the same string.
   */
  public static final class LazyInternedStringList {
    private final ArrayList<String> pool;

    private final WriteOnlyBitset isInterned;

    public LazyInternedStringList(List<String> pool) {
      this.pool = new ArrayList<>(pool);
      this.isInterned = new WriteOnlyBitset(pool.size());
    }

    // if Java supported type-brands we would want to use one here to indicate that the returned
    // string is always interned but that's not possible without an expensive wrapper type - see
    // also the comment on uncheckedEquals().
    public String get(int offset) {
      if (!this.isInterned.get(offset)) {
        String uninterned = this.pool.get(offset);
        String interned = RhinoStringPool.addOrGet(uninterned);
        this.pool.set(offset, interned);
        this.isInterned.set(offset); // must happen after this.pool.set() to avoid a race condition
        return interned;
      }
      return this.pool.get(offset);
    }

    public Stream<String> stream() {
      return this.pool.stream();
    }
  }

  /**
   * Bitset implementation that only supports setting bits from false => true, not vice versa, and
   * is not resizable once created.
   *
   * <p>This class was originally copied from the JDK BitSet implementation, but has been heavily
   * modified to be thread-safe and remove unnecessary functionality.
   */
  @VisibleForTesting
  static final class WriteOnlyBitset {
    /*
     * BitSets are packed into arrays of "words."  Currently a word is
     * an int, which consists of 32 bits, requiring 5 address bits. The choice of word size comes
     * from the convenience of using AtomicIntegerArray.
     */
    private static final int ADDRESS_BITS_PER_WORD = 5;
    private final AtomicIntegerArray bits;

    WriteOnlyBitset(int size) {
      this.bits = new AtomicIntegerArray(wordIndex(size - 1) + 1);
    }

    void set(int offset) {
      int wordIndex = wordIndex(offset);
      this.bits.updateAndGet(wordIndex, (prevValue) -> prevValue | (1 << offset));
    }

    boolean get(int offset) {
      int wordIndex = wordIndex(offset);
      return ((bits.get(wordIndex) & (1 << offset)) != 0);
    }

    /** Given a bit index, return word index containing it. */
    private static int wordIndex(int bitIndex) {
      return bitIndex >> ADDRESS_BITS_PER_WORD;
    }
  }
}
