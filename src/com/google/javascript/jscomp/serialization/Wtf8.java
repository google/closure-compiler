/*
 * Copyright 2021 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.serialization;

import com.google.errorprone.annotations.NoAllocation;
import com.google.protobuf.ByteString;

/** Encoder/decoder into https://simonsapin.github.io/wtf-8/ */
final class Wtf8 {

  private Wtf8() {}

  private static final byte CONTINUATION_MASK = 0x3F;

  static ByteString encodeToWtf8(String s) {
    int length = s.length();
    ByteString.Output output = ByteString.newOutput(length);
    for (int i = 0; i < length; i++) {
      int codepoint = s.codePointAt(i);

      if (codepoint < 0x80) {
        output.write(codepoint);
      } else if (codepoint < 0x800) {
        output.write(0xC0 | (0x1f & (codepoint >>> 6)));
        output.write(0x80 | (CONTINUATION_MASK & codepoint));
      } else if (codepoint < 0x10000) {
        output.write(0xE0 | (0xf & (codepoint >>> 12)));
        output.write(0x80 | (CONTINUATION_MASK & (codepoint >>> 6)));
        output.write(0x80 | (CONTINUATION_MASK & codepoint));
      } else {
        // This codepoints takes two UTF-16 code units, so we need an extra increment.
        i++;
        output.write(0xF0 | (0x7 & (codepoint >>> 18)));
        output.write(0x80 | (CONTINUATION_MASK & (codepoint >>> 12)));
        output.write(0x80 | (CONTINUATION_MASK & (codepoint >>> 6)));
        output.write(0x80 | (CONTINUATION_MASK & codepoint));
      }
    }
    return output.toByteString();
  }

  static Decoder decoder(int maxLength) {
    return new Decoder(maxLength);
  }

  /**
   * Decodes strings from WTF8 bytes.
   *
   * <p>This class is not threadsafe! In order to minimize allocations, and maximize performance, it
   * reuses buffers between decodings.
   */
  static class Decoder {
    private final int[] codepointBuffer;

    private Decoder(int maxLength) {
      this.codepointBuffer = new int[maxLength];
    }

    @NoAllocation
    String decode(ByteString encoded) {
      final int encodedByteCount = encoded.size();
      int codepointCount = 0;

      for (int i = 0; i < encodedByteCount; i++) {
        final byte b = encoded.byteAt(i);
        final int codepoint;

        if ((b & 0x80) == 0) {
          // 0xxx xxxx: 1 byte
          codepoint = b;
        } else if ((b & 0xE0) == 0xC0) {
          // 110x xxxx: 2 bytes
          int firstByte = 0x1F & b;
          int secondByte = encoded.byteAt(++i) & CONTINUATION_MASK;
          codepoint = (firstByte << 6) | secondByte;
        } else if ((b & 0xF0) == 0xE0) {
          // 1110 xxxx: 3 bytes
          int firstByte = 0xF & b;
          int secondByte = encoded.byteAt(++i) & CONTINUATION_MASK;
          int thirdByte = encoded.byteAt(++i) & CONTINUATION_MASK;
          codepoint = (firstByte << 12) | (secondByte << 6) | thirdByte;
        } else if ((b & 0xF8) == 0xF0) {
          // 1111 0xxx: 4 bytes
          int firstByte = 0x7 & b;
          int secondByte = encoded.byteAt(++i) & CONTINUATION_MASK;
          int thirdByte = encoded.byteAt(++i) & CONTINUATION_MASK;
          int fourthByte = encoded.byteAt(++i) & CONTINUATION_MASK;
          codepoint = (firstByte << 18) | (secondByte << 12) | (thirdByte << 6) | fourthByte;
        } else {
          throw new AssertionError();
        }

        this.codepointBuffer[codepointCount++] = codepoint;
      }

      return new String(this.codepointBuffer, 0, codepointCount);
    }
  }
}
