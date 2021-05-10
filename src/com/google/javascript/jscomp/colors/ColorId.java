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
import com.google.common.collect.Iterables;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
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

  public static ColorId fromUnsigned(long x) {
    return new ColorId(x);
  }

  public static ColorId fromUnsigned(int x) {
    return new ColorId((long) x & 0xFFFFFFFFL);
  }

  public static ColorId fromUnsigned(byte x) {
    return new ColorId((long) x & 0xFFL);
  }

  public static ColorId fromBytes(ByteString bytes) {
    return fromBytesAt(bytes, bytes.size(), BYTESTRING_AT);
  }

  public static ColorId fromBytes(byte[] bytes) {
    return fromBytesAt(bytes, bytes.length, BYTEARRAY_AT);
  }

  @VisibleForTesting
  public static ColorId fromAscii(String str) {
    return fromBytesAt(str, str.length(), ASCII_AT);
  }

  private static <T> ColorId fromBytesAt(T source, int length, ByteAt<T> byteAt) {
    checkState(length <= 8, length);

    long rightAligned = 0;
    for (int i = 0; i < length; i++) {
      rightAligned <<= 8;
      rightAligned |= ((long) byteAt.get(source, i)) & 0xFF;
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
    if (ids.size() <= 1) {
      return Iterables.getOnlyElement(ids);
    }

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

  public ByteString asByteString() {
    long copy = this.rightAligned;
    byte[] out = new byte[8];
    for (int i = 7; i >= 0; i--) {
      out[i] = (byte) copy;
      copy >>>= 8;
    }
    return ByteString.copyFrom(out);
  }

  @Override
  public String toString() {
    // Use hex encoding so there are no ambiguous trailing chars like Base64 would have.
    return Long.toHexString(this.rightAligned);
  }

  private static final HashFunction FARM_64 = Hashing.farmHashFingerprint64();

  private interface ByteAt<T> {
    byte get(T source, int index);
  }

  private static final ByteAt<ByteString> BYTESTRING_AT = (s, i) -> s.byteAt(i);

  private static final ByteAt<byte[]> BYTEARRAY_AT = (s, i) -> s[i];

  private static final ByteAt<String> ASCII_AT =
      (s, i) -> {
        int c = s.codePointAt(i);
        checkState(0 <= c && c < 128, c);
        return (byte) c;
      };
}
