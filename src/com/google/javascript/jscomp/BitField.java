/*
 * Copyright 2004 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;

import java.util.Arrays;

/**
 * Implements a few functionalities of a bit field.
 * Copied (and re-written) in parts from 'com.google.testing.coverage.BitField'
 *
 */
class BitField {

  private byte[] bytes;

  public BitField() {
    this.bytes = new byte[0];
  }

  /**
   * Sets or clears a bit at the given 0-based index.
   *
   * @param index 0-based bit position.
   */
  public void setBit(int index) {
    if (index < 0) {
      return;
    }

    int byteIndex = index / 8;
    int newByteSize = byteIndex + 1;

    if (bytes.length < newByteSize) {
      bytes = Arrays.copyOf(bytes, newByteSize);
    }

    int bitIndex = index % 8;
    int mask = 1 << bitIndex;
    bytes[byteIndex] |= mask;
  }

  /**
   * Checks whether a bit at the given 0-based index is set.
   *
   * @param index 0-based bit position
   * @return true if set, false otherwise
   */
  public boolean isBitSet(int index) {
    int byteIndex = index / 8;

    if (byteIndex >= bytes.length) {
      return false;
    }

    int bitIndex = index % 8;
    int mask = 1 << bitIndex;
    return (bytes[byteIndex] & mask) != 0;
  }

  /**
   * Returns a byte-wise hex string representation of the BitField from
   * MSB (Most Significant Byte) to LSB (Least Significant Byte).
   * Eg. Single byte: a setting of "0001 1111", returns "1f"
   * Eg. Multiple bytes: a setting of "0000 0010 0001 1111", returns "1f02"
   *
   * @return string representation of bits set
   */
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) {
      int byteValue = 0;
      for (int j = i * 8 + 7; j >= i * 8; j--) {
        byteValue = 2 * byteValue + (isBitSet(j) ? 1 : 0);
      }
      result.append(getHexPair(byteValue));
    }
    return result.toString();
  }

  /**
   * Returns a hex pair string representation of the given int value.
   *
   * @param byteValue int value
   * @return string representation of hex pair corresponding to the int value
   */
  public static String getHexPair(int byteValue) {
    Preconditions.checkArgument((byteValue >= 0) && (byteValue < 256));
    int firstHex = (int) Math.floor(byteValue / 16);
    int secondHex = byteValue % 16;
    return Integer.toHexString(firstHex) + Integer.toHexString(secondHex);
  }
}
