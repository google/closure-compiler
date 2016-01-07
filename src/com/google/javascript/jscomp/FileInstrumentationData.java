/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.UnsignedBytes;

import java.util.BitSet;

/**
 * Holds instrumentation details related to a file, namely, the filename,
 * the array name used in instrumentation, and the lines which were
 * instrumented (in encoded form).
 * @author praveenk@google.com (Praveen Kumashi)
 */
@GwtIncompatible("com.google.common.primitives.UnsignedBytes")
class FileInstrumentationData {
  private final BitSet instrumentedBits;
  private final String arrayName;
  private final String fileName;

  FileInstrumentationData(String fileName, String arrayName) {
    this.fileName = fileName;
    this.arrayName = arrayName;
    instrumentedBits = new BitSet();
  }

  String getArrayName() {
    return arrayName;
  }

  String getFileName() {
    return fileName;
  }

  /**
   * Returns a byte-wise hex string representation of the BitField from
   * MSB (Most Significant Byte) to LSB (Least Significant Byte).
   * Eg. Single byte: a setting of "0001 1111", returns "1f"
   * Eg. Multiple bytes: a setting of "0000 0010 0001 1111", returns "1f02"
   *
   * @return string representation of bits set
   */
  String getInstrumentedLinesAsHexString() {
    StringBuilder builder = new StringBuilder();

    // Build the hex string.
    for (byte byteEntry : instrumentedBits.toByteArray()) {
      // Java bytes are signed, but we want the value as if it were unsigned.
      int value = UnsignedBytes.toInt(byteEntry);
      String hexString = Integer.toHexString(value);

      // Pad string to be two characters (if it isn't already).
      hexString = Strings.padStart(hexString, 2, '0');

      builder.append(hexString);
    }

    return builder.toString();
  }


  /**
   * Mark given 1-based line number as instrumented. Zero, Negative numbers
   * are not allowed.
   * @param lineNumber the line number which was instrumented
   */
  void setLineAsInstrumented(int lineNumber) {
    Preconditions.checkArgument(
        lineNumber > 0, "Expected non-zero positive integer as line number: %s", lineNumber);

    // Map the 1-based line number to 0-based bit position
    instrumentedBits.set(lineNumber - 1);
  }
}
