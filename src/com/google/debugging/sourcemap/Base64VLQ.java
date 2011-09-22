/*
 * Copyright 2011 The Closure Compiler Authors. All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of Google Inc. nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
