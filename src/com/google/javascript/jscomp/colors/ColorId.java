/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.colors;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.errorprone.annotations.Immutable;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Set;

/**
 * A probablistically unique ID for a Color.
 *
 * <p>This is a semantic wrapper around UUID bytes to provide better typing when passing those bytes
 * around. It also hosts some utility methods to ensure consistent byte handling and help with
 * testing/debugging.
 *
 * <p>If two Colors have the same ID, they should be considered to represent the same "logical"
 * Color, perhaps inferred in different libraries. Ideally, those instances would be reconciled
 * before either Color is instantiated.
 *
 * <p>IDs have an intentionally unspecified width so that it may expand if needed in the future. For
 * the purposes of comparison, serialization, and display, leading 0s are ignored.
 */
@Immutable
public final class ColorId implements Serializable {

  // Assume for now that we have at most 8 bytes.
  private final long rightAligned;

  @VisibleForTesting
  public static ColorId fromAscii(String str) {
    final int length = str.length();
    checkState(length <= 8, length);

    long rightAligned = 0;
    for (int i = 0; i < length; i++) {
      int c = str.codePointAt(i);
      checkState(0 <= c && c < 128, c);
      rightAligned <<= 8;
      rightAligned |= c;
    }

    return new ColorId(rightAligned);
  }

  /**
   * Defines how IDs for unions are combined.
   *
   * <p>Ideally, this function would preserve the algebraic properties of the union operation.
   * However, doing so is difficult (impossible?) while also providing good hashing. Those
   * properties are summarized below.
   *
   * <p>This method must never be passed IDs of existing unions. Lack of associativity means the
   * result will not be the same as unioning all the IDs simultaneously.
   *
   * <pre>
   * | name          | has   | example                       | note                        |
   * |---------------|-------|-------------------------------|-----------------------------|
   * | associativity | false | f(x, f(y, z)) = f(f(x, y), z) |                             |
   * | commutativity | true  |       f(x, y) = f(y, x)       |                             |
   * | idempotence   | true  |    f(x, x, y) = f(x, y)       | `ids` is a Set              |
   * | identity      | true  |          f(x) = x             | singleton unions are banned |
   * </pre>
   */
  public static ColorId union(Set<ColorId> ids) {
    checkState(ids.size() > 1, ids);

    int index = 0;
    long[] sorted = new long[ids.size()];
    for (ColorId id : ids) {
      sorted[index++] = id.rightAligned;
    }
    Arrays.sort(sorted);

    Hasher hasher = FARM_64.newHasher();
    for (int i = 0; i < sorted.length; i++) {
      hasher.putLong(sorted[i]);
    }

    return new ColorId(hasher.hash().asLong());
  }

  static ColorId forNative(NativeColorId nat) {
    checkState(nat.getId() == null);
    // TODO(b/185519307): Do some kind of hashing here so that native IDs are spread over more bits.
    return new ColorId(nat.ordinal());
  }

  private ColorId(long rightAligned) {
    this.rightAligned = rightAligned;
  }

  @Override
  public boolean equals(Object x) {
    if (!(x instanceof ColorId)) {
      return false;
    }

    ColorId that = (ColorId) x;
    return this.rightAligned == that.rightAligned;
  }

  @Override
  public int hashCode() {
    return (int) this.rightAligned;
  }

  @Override
  public String toString() {
    // Use hex encoding so there are no ambiguous trailing chars like Base64 would have.
    return Long.toHexString(this.rightAligned);
  }

  private static final HashFunction FARM_64 = Hashing.farmHashFingerprint64();
}
