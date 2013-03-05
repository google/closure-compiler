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

package com.google.debugging.sourcemap;

import java.io.IOException;

/**
 * We encode our variable length numbers as base64 encoded strings with
 * the least significant digit coming first.  Each base64 digit encodes
 * a 5-bit value (0-31) and a continuation bit.  Signed values can be
 * represented by using the least significant bit of the value as the
 * sign bit.
 *
 * @author johnlenz@google.com (John Lenz)
 */
final class Base64VLQ {
  // Utility class.
  private Base64VLQ() {}

  // A Base64 VLQ digit can represent 5 bits, so it is base-32.
  private static final int VLQ_BASE_SHIFT = 5;
  private static final int VLQ_BASE = 1 << VLQ_BASE_SHIFT;

  // A mask of bits for a VLQ digit (11111), 31 decimal.
  private static final int VLQ_BASE_MASK = VLQ_BASE-1;

  // The continuation bit is the 6th bit.
  private static final int VLQ_CONTINUATION_BIT = VLQ_BASE;

  /**
   * Converts from a two-complement value to a value where the sign bit is
   * is placed in the least significant bit.  For example, as decimals:
   *   1 becomes 2 (10 binary), -1 becomes 3 (11 binary)
   *   2 becomes 4 (100 binary), -2 becomes 5 (101 binary)
   */
  private static int toVLQSigned(int value) {
    if (value < 0) {
      return ((-value) << 1) + 1;
    } else {
      return (value << 1) + 0;
    }
  }

  /**
   * Converts to a two-complement value from a value where the sign bit is
   * is placed in the least significant bit.  For example, as decimals:
   *   2 (10 binary) becomes 1, 3 (11 binary) becomes -1
   *   4 (100 binary) becomes 2, 5 (101 binary) becomes -2
   */
  private static int fromVLQSigned(int value) {
    boolean negate = (value & 1) == 1;
    value = value >> 1;
    return negate ? -value : value;
  }

  /**
   * Writes a VLQ encoded value to the provide appendable.
   * @throws IOException
   */
  public static void encode(Appendable out, int value)
      throws IOException {
    value = toVLQSigned(value);
    do {
      int digit = value & VLQ_BASE_MASK;
      value >>>= VLQ_BASE_SHIFT;
      if (value > 0) {
        digit |= VLQ_CONTINUATION_BIT;
      }
      out.append(Base64.toBase64(digit));
    } while (value > 0);
  }

  /**
   * A simple interface for advancing through a sequence of characters, that
   * communicates that advance back to the source.
   */
  interface CharIterator {
    boolean hasNext();
    char next();
  }

  /**
   * Decodes the next VLQValue from the provided CharIterator.
   */
  public static int decode(CharIterator in) {
    int result = 0;
    boolean continuation;
    int shift = 0;
    do {
      char c = in.next();
      int digit = Base64.fromBase64(c);
      continuation = (digit & VLQ_CONTINUATION_BIT) != 0;
      digit &= VLQ_BASE_MASK;
      result = result + (digit << shift);
      shift = shift + VLQ_BASE_SHIFT;
    } while (continuation);

    return fromVLQSigned(result);
  }
}
